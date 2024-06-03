/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.sessions;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.TaskQueue;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.ConnectInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestAdapter;
import com.newrelic.agent.android.harvest.HarvestConfiguration;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.harvest.MachineMeasurements;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricStore;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.stats.TicToc;
import com.newrelic.agent.android.util.Constants;

import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@RunWith(JUnit4.class)
public class ActiveSessionsTests {
    private final static String APP_TOKEN = "AA51de4fdd7863d52ee088f2abb5d891495b0f2073";
    private final static InetSocketAddress COLLECTOR_ADDRESS = new InetSocketAddress("staging-mobile-collector.newrelic.com", 80);
    private final static CharsetDecoder utfDecoder = Charset.forName("UTF-8").newDecoder();
    private final static AgentLog log = new ConsoleAgentLog();

    @BeforeClass
    public static void setLogging() {
        log.setLevel(AgentLog.DEBUG);
        AgentLogManager.setAgentLog(log);
    }

    @Before
    public void setUp() throws Exception {
        ApplicationStateMonitor.setInstance(null);
        ApplicationStateMonitor.getInstance().activityStarted();
    }

    @Test
    public void testSessionDurationMetricCreatedOnBackground() {
        final Harvest testHarvest = new Harvest();

        Harvest.setInstance(testHarvest);
        Harvest.initialize(new AgentConfiguration());

        Harvest.addHarvestListener(new HarvestAdapter() {
            @Override
            public void onHarvestBefore() {
                // Force StatsEngine to produce its Metrics, which should contain Session/Duration.
                StatsEngine.get().onHarvest();

                // StatsEngine queues its metrics. Run the queue to ensure they have been processed.
                TaskQueue.synchronousDequeue();
            }
        });

        Harvest.start();

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }

        // Called when going into background/final data send.
        Harvest.harvestNow(true, true);
        Harvest.stop();

        MachineMeasurements metrics = testHarvest.getHarvestData().getMetrics();
        MetricStore store = metrics.getMetrics();
        Metric metric = store.get("Session/Duration");
        Assert.assertNotNull(metric);

        Assert.assertNotNull("Should contain duration metric", metric);
        Assert.assertNotEquals("Duration should be non-zero", 0, metric.getTotal(), 0);

    }

    @Test
    public void testInvalidSessionDuration() {
        final Harvest testHarvest = new Harvest();

        Harvest.setInstance(testHarvest);
        Harvest.initialize(new AgentConfiguration());

        Assert.assertEquals("Should return invalid session duration before agent starts", Harvest.INVALID_SESSION_DURATION, Harvest.getMillisSinceStart());
        Harvest.start();
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
        }
        Assert.assertNotEquals("Should return valid session duration while agent is running", Harvest.INVALID_SESSION_DURATION, Harvest.getMillisSinceStart());
        Harvest.stop();

        Assert.assertEquals("Should return invalid session duration after agent stops", Harvest.INVALID_SESSION_DURATION, Harvest.getMillisSinceStart());
    }

    //// Not a test we want to run on Jenkins/regularly.  @Test
    public void testLotsOfSessions() {
        UuidPool uuidPool = new UuidPool(20000);

        Session connectSession = new Session(uuidPool);
        connectSession.reset();
        connectSession.createConnectPayload();
        connectSession.setBlocking(true);
        connectSession.connect();

        while (!connectSession.isConnected()) ;

        connectSession.sendConnect();

        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        while (!connectSession.didReadConnectResponse())
            connectSession.readConnectResponse();

        HarvestConfiguration configuration = connectSession.getConfiguration();
        log.debug("Using HarvestConfiguration: " + configuration.toString());


        SessionPool pool = new SessionPool(connectSession, uuidPool, 200);

        log.debug("Sending requests");
        ArrayList<Future> futures = new ArrayList<Future>();
        futures.add(pool.start());
        waitForFutures(futures);
    }

    private void waitForFutures(Collection<Future> futures) {
        while (true) {
            boolean allDone = true;
            for (Future future : futures) {
                if (!future.isDone()) {
                    allDone = false;
                    break;
                } else {
                    log.info("Future reported as done.");
                }
            }

            if (allDone)
                break;

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class UuidPool {
        private AtomicInteger uuidIndex = new AtomicInteger(0);
        private ArrayList<String> uuids = new ArrayList<String>();

        public UuidPool(int numUuids) {
            for (int i = 0; i < numUuids; i++) {
                uuids.add(UUID.randomUUID().toString());
            }
        }

        public synchronized String nextUuid() {
            if (uuidIndex.intValue() >= uuids.size())
                uuidIndex = new AtomicInteger(0);
            int index = uuidIndex.getAndIncrement();
            return uuids.get(index);
        }
    }

    class SessionPool implements Runnable {
        private Collection<Session> sessions = new CopyOnWriteArrayList<Session>();

        public SessionPool(Session connectedSession, UuidPool uuidPool, int numSessions) {
            for (int i = 0; i < numSessions; i++) {
                Session session = new Session(uuidPool);
                session.setSkipSendConnect(true);
                session.setConfiguration(connectedSession.getConfiguration());
                session.setDeviceInformation(connectedSession.getDeviceInformation());
                session.reset();
                addSession(session);
            }
        }

        public Future start() {
            ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
            return executor.scheduleAtFixedRate(this, 0, 1, TimeUnit.SECONDS);
        }

        @Override
        public void run() {
            TicToc ticToc = new TicToc();
            ticToc.tic();
            while (!allDone()) {
                execute();
            }
            resetAll();
            log.info("Sent " + count() + " in " + ticToc.toc() + " ms");
        }

        public void addSession(Session session) {
            sessions.add(session);
        }

        public int count() {
            return sessions.size();
        }


        public void execute() {
            for (Session session : sessions) {
                if (!session.didConnect()) {
                    session.connect();
                    continue;
                }

                if (session.isConnected()) {
                    if (!session.sentConnect()) {
                        session.sendConnect();
                        continue;
                    }

                    if (!session.didReadConnectResponse()) {
                        session.readConnectResponse();
                        continue;
                    } else {
                        if (!session.sentData()) {
                            session.sendData();
                            continue;
                        }
                        session.close();
                    }
                }
            }
        }

        public boolean allDone() {
            for (Session session : sessions) {
                if (!session.isDone())
                    return false;
            }
            return true;
        }

        public void resetAll() {
            for (Session session : sessions) {
                session.reset();
            }
        }
    }

    class Session {
        private SocketChannel channel;
        private ByteBuffer connectPayload;
        private ByteBuffer dataPayload;
        private boolean sentConnect;
        private boolean didConnect;
        private boolean readConnectResponse;
        private boolean sentData;
        private boolean blocking;
        private boolean skipSendConnect;

        private String uuid;
        private HarvestConfiguration configuration;
        private DeviceInformation deviceInformation;
        private UuidPool uuidPool;

        public Session(UuidPool uuidPool) {
            this.uuidPool = uuidPool;
        }

        public void reset() {
            setUuid(uuidPool.nextUuid());
            createChannel();

            didConnect = sentData = false;
            if (skipSendConnect) {
                createDataPayload();
                return;
            }
            configuration = null;
            createConnectPayload();
            sentConnect = readConnectResponse = false;
        }

        public void sendConnect() {
            try {
                channel.write(connectPayload);
                sentConnect = true;
            } catch (IOException e) {
                e.printStackTrace();
                reset();
            }
        }

        public void sendData() {
            try {
                createDataPayload();
                channel.write(dataPayload);
                sentData = true;
            } catch (IOException e) {
                e.printStackTrace();
                reset();
            }
        }

        public boolean sentConnect() {
            return sentConnect;
        }

        public boolean sentData() {
            return sentData;
        }

        public void connect() {
            try {
                channel.connect(COLLECTOR_ADDRESS);
                didConnect = true;
            } catch (IOException e) {
                e.printStackTrace();
                reset();
                return;
            }
        }

        public void readConnectResponse() {
            ByteBuffer response = ByteBuffer.allocate(1024);
            try {
                int read = channel.read(response);
                if (read > 0) {
                    response.flip();
                    String responseString = utfDecoder.decode(response).toString();
                    if (responseString.isEmpty()) {
                        log.error("Empty response");
                        reset();
                        return;
                    }
                    configuration = parseHarvesterConfiguration(responseString);
                    if (configuration == null) {
                        reset();
                        return;
                    }
                    readConnectResponse = true;
                    didConnect = false;
                    close();
                    createChannel();
                }
            } catch (IOException e) {
                e.printStackTrace();
                reset();
            }
        }

        public void skipSendConnect() {
            sentConnect = readConnectResponse = true;
            sentData = false;
        }

        public boolean isDone() {
            return didConnect && sentConnect && readConnectResponse && sentData && !isConnected();
        }

        public boolean didConnect() {
            return didConnect;
        }

        public boolean didReadConnectResponse() {
            return readConnectResponse;
        }

        public boolean isConnected() {
            try {
                if (channel.isConnectionPending())
                    return channel.finishConnect();
                else
                    return channel.isConnected();
            } catch (IOException e) {
                e.printStackTrace();
                reset();
                return false;
            }
        }

        public void close() {
            try {
                channel.close();
            } catch (IOException e) {
                e.printStackTrace();
                reset();
            }
        }

        private void createChannel() {
            try {
                channel = SelectorProvider.provider().openSocketChannel();
                channel.configureBlocking(blocking);
            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        }

        private void createConnectPayload() {
            ApplicationInformation applicationInformation = new ApplicationInformation();
            applicationInformation.setAppName("Active Sessions Test");
            applicationInformation.setAppVersion("1.0");
            applicationInformation.setPackageId("com.newrelic.agent.android.sessions");

            deviceInformation = new DeviceInformation();
            deviceInformation.setAgentName("Fake Agent");
            deviceInformation.setAgentVersion("0.0");
            deviceInformation.setManufacturer("fake");
            deviceInformation.setModel("fake");
            deviceInformation.setOsName("Android");
            deviceInformation.setOsVersion("4.2");
            deviceInformation.setDeviceId(uuid);

            ConnectInformation connectInformation = new ConnectInformation(applicationInformation, deviceInformation);

            int postBufferSize = 512;
            ByteBuffer postBuffer = ByteBuffer.allocate(postBufferSize);

            postBuffer.put("POST /mobile/v3/connect HTTP/1.1\r\n".getBytes());
            postBuffer.put((Constants.Network.CONTENT_TYPE_HEADER + ": " + Constants.Network.ContentType.JSON + "\r\n").getBytes());
            postBuffer.put((Constants.Network.CONTENT_ENCODING_HEADER + ": identity\r\n").getBytes());
            postBuffer.put((Constants.Network.USER_AGENT_HEADER + ":\r\n").getBytes());
            postBuffer.put((Constants.Network.APPLICATION_LICENSE_HEADER + ": " + APP_TOKEN + "\r\n").getBytes());
            postBuffer.put((Constants.Network.HOST_HEADER + ": staging-mobile-collector.newrelic.com\r\n").getBytes());

            String json = connectInformation.asJson().toString();

            postBuffer.put((Constants.Network.CONTENT_LENGTH_HEADER + ": " + json.length() + "\r\n\r\n").getBytes());
            postBuffer.put(json.getBytes());

            postBuffer.flip();

            connectPayload = postBuffer;
        }

        private void createDataPayload() {
            HarvestData data = new HarvestData();
            data.setDataToken(configuration.getDataToken());

            deviceInformation.setDeviceId(uuid);
            data.setDeviceInformation(deviceInformation);

            String json = data.asJson().toString();

            int dataBufferSize = 1024;
            ByteBuffer dataBuffer = ByteBuffer.allocate(dataBufferSize);

            dataBuffer.put("POST /mobile/v3/data HTTP/1.1\r\n".getBytes());
            dataBuffer.put((Constants.Network.CONTENT_TYPE_HEADER + ": application/json\r\n").getBytes());
            dataBuffer.put((Constants.Network.CONTENT_ENCODING_HEADER + ": identity\r\n").getBytes());
            dataBuffer.put((Constants.Network.USER_AGENT_HEADER + ":\r\n").getBytes());
            dataBuffer.put((Constants.Network.APPLICATION_LICENSE_HEADER + ": " + APP_TOKEN + "\r\n").getBytes());
            dataBuffer.put((Constants.Network.HOST_HEADER + ": staging-mobile-collector.newrelic.com\r\n").getBytes());
            dataBuffer.put((Constants.Network.CONTENT_LENGTH_HEADER + ": " + json.length() + "\r\n\r\n").getBytes());
            dataBuffer.put(json.getBytes());
            dataBuffer.flip();

            dataPayload = dataBuffer;
        }

        String getUuid() {
            return uuid;
        }

        void setUuid(String uuid) {
            this.uuid = uuid;
        }

        boolean isBlocking() {
            return blocking;
        }

        void setBlocking(boolean blocking) {
            this.blocking = blocking;
        }

        HarvestConfiguration getConfiguration() {
            return configuration;
        }

        void setConfiguration(HarvestConfiguration configuration) {
            this.configuration = configuration;
        }

        DeviceInformation getDeviceInformation() {
            return deviceInformation;
        }

        void setDeviceInformation(DeviceInformation deviceInformation) {
            this.deviceInformation = deviceInformation;
        }

        private HarvestConfiguration parseHarvesterConfiguration(String response) {
            Gson gson = new Gson();
            HarvestConfiguration config = null;
            int jsonStart = response.indexOf("{");
            if (jsonStart < 0) {
                log.error("Can't find JSON in: " + response);
                return null;
            }
            response = response.substring(jsonStart);
            try {
                config = gson.fromJson(response, HarvestConfiguration.class);
            } catch (JsonSyntaxException e) {
                log.error("Unable to parse collector configuration: " + e.getMessage());
                log.error(response);
            }
            return config;
        }

        void setSkipSendConnect(boolean skipSendConnect) {
            this.skipSendConnect = skipSendConnect;
            if (skipSendConnect)
                skipSendConnect();
        }
    }
}

/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.agentdata;

import com.google.gson.JsonIOException;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.newrelic.agent.android.AgentConfiguration;
import com.newrelic.agent.android.FeatureFlag;
import com.newrelic.agent.android.agentdata.builder.AgentDataBuilder;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;
import com.newrelic.agent.android.measurement.MeasurementException;
import com.newrelic.agent.android.payload.Payload;
import com.newrelic.agent.android.payload.PayloadStore;
import com.newrelic.agent.android.test.mock.AgentDataReporterSpy;
import com.newrelic.agent.android.test.stub.StubAnalyticsAttributeStore;
import com.newrelic.mobile.fbs.HexAgentData;
import com.newrelic.mobile.fbs.HexAgentDataBundle;

import org.ietf.jgss.GSSException;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.CharConversionException;
import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.ObjectStreamException;
import java.io.SyncFailedException;
import java.io.UTFDataFormatException;
import java.io.UnsupportedEncodingException;
import java.lang.instrument.IllegalClassFormatException;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.UnknownServiceException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileLockInterruptionException;
import java.nio.charset.CharacterCodingException;
import java.security.GeneralSecurityException;
import java.security.PrivilegedActionException;
import java.security.cert.CertificateException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.TooManyListenersException;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.zip.DataFormatException;
import java.util.zip.ZipException;

import javax.management.BadAttributeValueExpException;
import javax.management.BadStringOperationException;
import javax.management.InvalidApplicationException;
import javax.management.JMException;
import javax.management.modelmbean.InvalidTargetObjectTypeException;
import javax.management.modelmbean.XMLParseException;
import javax.naming.NamingException;
import javax.net.ssl.SSLException;
import javax.print.PrintException;
import javax.script.ScriptException;
import javax.security.auth.DestroyFailedException;
import javax.security.auth.RefreshFailedException;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.UnsupportedLookAndFeelException;
import javax.transaction.xa.XAException;
import javax.xml.crypto.KeySelectorException;
import javax.xml.crypto.URIReferenceException;
import javax.xml.crypto.dsig.TransformException;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.TransformerException;
import javax.xml.xpath.XPathException;

public class HandledExceptionsTest {
    private static AgentLog log;
    private static AgentConfiguration agentConfiguration;
    private static Map<String, Object> sessionAttributes;
    private static PayloadStore<Payload> payloadStore;
    private static String exceptionMessage = "The tea is too hot";

    @BeforeClass
    public static void setUpClass() throws Exception {
        FeatureFlag.enableFeature(FeatureFlag.HandledExceptions);

        AgentLogManager.setAgentLog(new ConsoleAgentLog());
        log = AgentLogManager.getAgentLog();

        payloadStore = new PayloadStore<Payload>() {
            ArrayList<Payload> cache = new ArrayList<>();

            @Override
            public boolean store(Payload data) {
                cache.add(data);
                return true;
            }

            @Override
            public List<Payload> fetchAll() {
                return cache;
            }

            @Override
            public int count() {
                return cache.size();
            }

            @Override
            public void clear() {
                cache.clear();
            }

            @Override
            public void delete(Payload data) {
                cache.remove(data);
            }
        };

        agentConfiguration = new AgentConfiguration();
        agentConfiguration.setApplicationToken(AgentDataSender.class.getSimpleName());
        agentConfiguration.setEnableAnalyticsEvents(true);
        agentConfiguration.setReportCrashes(true);
        agentConfiguration.setReportHandledExceptions(true);
        agentConfiguration.setAnalyticsAttributeStore(new StubAnalyticsAttributeStore());
        agentConfiguration.setUseSsl(false);
        agentConfiguration.setPayloadStore(payloadStore);

        sessionAttributes = new HashMap<String, Object>() {{
            put("a string", "hello");
            put("dbl", 666.6666666);
            put("lng", 12323435456463233L);
            put("yes", true);
            put("int", 3);
        }};

        AgentDataReporterSpy.initialize(agentConfiguration);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        AgentDataReporterSpy.shutdown();
    }

    @Before
    public void setUp() throws Exception {
        payloadStore.clear();
    }

    @After
    public void tearDown() throws Exception {
        payloadStore.clear();
    }

    private void testException(Throwable throwable) {
        Boolean result = false;
        try {
            result = AgentDataController.sendAgentData(throwable, sessionAttributes);
            Assert.assertTrue("Should submit throwable", result);
            log.info("Test [" + throwable.getClass().getSimpleName() + "]: " + result.toString());

        } catch (Exception e) {
            log.error("Test [" + throwable.getClass().getSimpleName() + "]: " + result.toString() + ". " + e);
        }
    }

    @Test
    public void testAllSupportedJavaExceptions() throws Exception {
        testException(new Exception(exceptionMessage));
        testException(new RuntimeException(exceptionMessage));
        testException(new ArithmeticException(exceptionMessage));
        testException(new ArrayIndexOutOfBoundsException(exceptionMessage));
        testException(new BatchUpdateException());
        testException(new BrokenBarrierException(exceptionMessage));
        testException(new CertificateException(exceptionMessage));
        testException(new CharConversionException(exceptionMessage));
        testException(new CharacterCodingException());
        testException(new ClassCastException(exceptionMessage));
        testException(new CloneNotSupportedException(exceptionMessage));
        testException(new ClosedChannelException());
        testException(new ConcurrentModificationException(exceptionMessage));
        testException(new DataFormatException(exceptionMessage));
        testException(new DatatypeConfigurationException(exceptionMessage));
        testException(new DestroyFailedException(exceptionMessage));
        testException(new EOFException(exceptionMessage));
        testException(new Exception(exceptionMessage));
        testException(new FileLockInterruptionException());
        testException(new FileNotFoundException(exceptionMessage));
        testException(new GeneralSecurityException(exceptionMessage));
        testException(new IOException(exceptionMessage));
        testException(new IllegalAccessException(exceptionMessage));
        testException(new IllegalArgumentException(exceptionMessage));
        testException(new IllegalClassFormatException(exceptionMessage));
        testException(new IllegalStateException(exceptionMessage));
        testException(new IndexOutOfBoundsException(exceptionMessage));
        testException(new InputMismatchException(exceptionMessage));
        testException(new InstantiationException(exceptionMessage));
        testException(new InterruptedException(exceptionMessage));
        testException(new InterruptedIOException(exceptionMessage));
        testException(new InvalidMidiDataException(exceptionMessage));
        testException(new InvalidTargetObjectTypeException(exceptionMessage));
        testException(new JMException(exceptionMessage));
        testException(new KeySelectorException(exceptionMessage));
        testException(new LineUnavailableException(exceptionMessage));
        testException(new MalformedURLException(exceptionMessage));
        testException(new MidiUnavailableException(exceptionMessage));
        testException(new NamingException(exceptionMessage));
        testException(new NegativeArraySizeException(exceptionMessage));
        testException(new NoSuchElementException(exceptionMessage));
        testException(new NoSuchFieldException(exceptionMessage));
        testException(new NoSuchMethodException(exceptionMessage));
        testException(new NullPointerException(exceptionMessage));
        testException(new NumberFormatException(exceptionMessage));
        testException(new ParserConfigurationException(exceptionMessage));
        testException(new PrintException(exceptionMessage));
        testException(new ProtocolException(exceptionMessage));
        testException(new RefreshFailedException(exceptionMessage));
        testException(new RuntimeException(exceptionMessage));
        testException(new SAXException(exceptionMessage));
        testException(new SQLException(exceptionMessage));
        testException(new SQLWarning(exceptionMessage));
        testException(new SocketException(exceptionMessage));
        testException(new TimeoutException(exceptionMessage));
        testException(new TooManyListenersException(exceptionMessage));
        testException(new URIReferenceException(exceptionMessage));
        testException(new UTFDataFormatException(exceptionMessage));
        testException(new UnknownHostException(exceptionMessage));
        testException(new UnknownServiceException(exceptionMessage));
        testException(new UnmodifiableClassException(exceptionMessage));
        testException(new UnsupportedAudioFileException(exceptionMessage));
        testException(new UnsupportedEncodingException(exceptionMessage));
        testException(new UnsupportedOperationException(exceptionMessage));
        testException(new XAException(exceptionMessage));
        testException(new XMLParseException(exceptionMessage));
        testException(new XMLSignatureException(exceptionMessage));
        testException(new XMLStreamException(exceptionMessage));
        testException(new ZipException(exceptionMessage));

        testException(new BadAttributeValueExpException(exceptionMessage));
        testException(new BadStringOperationException(exceptionMessage));
        testException(new ClassNotFoundException(exceptionMessage));
        testException(new ExecutionException(exceptionMessage, new Throwable("bleh")));
        testException(new GSSException(1));
        testException(new InvalidApplicationException(exceptionMessage));
        testException(new InvocationTargetException(new Throwable("InvocationTargetException")));
        testException(new ObjectStreamException(exceptionMessage) {
        });
        testException(new ParseException(exceptionMessage, 69));
        testException(new PrivilegedActionException(new RuntimeException("PrivilegedActionException")));
        testException(new SSLException(exceptionMessage));
        testException(new ScriptException(exceptionMessage));
        testException(new SyncFailedException(exceptionMessage));
        testException(new TransformException(exceptionMessage));
        testException(new TransformerException(exceptionMessage));
        testException(new URISyntaxException(exceptionMessage, "URISyntaxException"));
        testException(new UnsupportedCallbackException(new Callback() {
        }));
        testException(new UnsupportedLookAndFeelException(exceptionMessage));
        testException(new XPathException(exceptionMessage));

        Assert.assertEquals(88, payloadStore.count());
    }

    @Test
    public void testAllSupportedAgentExceptions() throws Exception {
        testException(new JsonIOException(exceptionMessage));
        testException(new JsonParseException(exceptionMessage));
        testException(new JsonSyntaxException(exceptionMessage));
        testException(new MeasurementException(exceptionMessage));

        Assert.assertEquals(4, payloadStore.count());
    }

    @Test
    public void testAllSupportedAppExceptions() throws Exception {
        testException(new JsonIOException(exceptionMessage));
        testException(new JsonParseException(exceptionMessage));
        testException(new InvalidPreferencesFormatException(exceptionMessage));
        testException(new BackingStoreException(exceptionMessage));

        Assert.assertEquals(4, payloadStore.count());
    }

    @Test
    public void testThrowable() {
        class ThrowableHex extends Throwable {
            @Override
            public String getMessage() {
                return exceptionMessage;
            }
        }

        ThrowableHex throwableHex = new ThrowableHex();
        StackTraceElement[] backTrace = Thread.currentThread().getStackTrace();
        throwableHex.setStackTrace(backTrace);

        testException(throwableHex);

        List<Payload> list = payloadStore.fetchAll();
        Assert.assertEquals(1, list.size());

        HexAgentDataBundle agentDataBundle = HexAgentDataBundle.getRootAsHexAgentDataBundle(ByteBuffer.wrap(list.get(0).getBytes()));
        HexAgentData agentData = agentDataBundle.hexAgentData(0);

        Map<String, Object> hexAttrMap = AgentDataBuilder.attributesMapFromAgentData(agentData);
        Assert.assertEquals(hexAttrMap.get("name"), throwableHex.getClass().toString());
        Assert.assertEquals(hexAttrMap.get("cause"), throwableHex.getClass().getName() + ": " + exceptionMessage);
        Assert.assertEquals(hexAttrMap.get("message"), exceptionMessage);

        Assert.assertNotNull(hexAttrMap.get("thread 0"));
        @SuppressWarnings("unchecked")
        HashMap<String, Object> stackTrace = (HashMap<String, Object>) hexAttrMap.get("thread 0");
        @SuppressWarnings("unchecked")
        HashMap<String, Object> stackFrame = (HashMap<String, Object>) stackTrace.get("frame 1");
        Assert.assertEquals(stackFrame.get("className"), this.getClass().getName());
        Assert.assertEquals(stackFrame.get("methodName"), "testThrowable");
    }
}

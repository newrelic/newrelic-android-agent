/**
 * Copyright 2021-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.StatFs;
import android.text.TextUtils;

import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.analytics.AnalyticsControllerImpl;
import com.newrelic.agent.android.analytics.AnalyticsEvent;
import com.newrelic.agent.android.analytics.EventManager;
import com.newrelic.agent.android.api.common.TransactionData;
import com.newrelic.agent.android.api.v1.ConnectionEvent;
import com.newrelic.agent.android.api.v1.ConnectionListener;
import com.newrelic.agent.android.api.v1.DeviceForm;
import com.newrelic.agent.android.api.v2.TraceMachineInterface;
import com.newrelic.agent.android.background.ApplicationStateEvent;
import com.newrelic.agent.android.background.ApplicationStateListener;
import com.newrelic.agent.android.background.ApplicationStateMonitor;
import com.newrelic.agent.android.distributedtracing.UserActionFacade;
import com.newrelic.agent.android.distributedtracing.UserActionType;
import com.newrelic.agent.android.harvest.AgentHealth;
import com.newrelic.agent.android.harvest.ApplicationInformation;
import com.newrelic.agent.android.harvest.ConnectInformation;
import com.newrelic.agent.android.harvest.DeviceInformation;
import com.newrelic.agent.android.harvest.EnvironmentInformation;
import com.newrelic.agent.android.harvest.Harvest;
import com.newrelic.agent.android.harvest.HarvestData;
import com.newrelic.agent.android.instrumentation.MetricCategory;
import com.newrelic.agent.android.logging.AgentLog;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.metric.Metric;
import com.newrelic.agent.android.metric.MetricNames;
import com.newrelic.agent.android.metric.MetricUnit;
import com.newrelic.agent.android.ndk.NativeReporting;
import com.newrelic.agent.android.payload.PayloadController;
import com.newrelic.agent.android.sample.MachineMeasurementConsumer;
import com.newrelic.agent.android.sample.Sampler;
import com.newrelic.agent.android.stats.StatsEngine;
import com.newrelic.agent.android.stores.SharedPrefsAnalyticsAttributeStore;
import com.newrelic.agent.android.stores.SharedPrefsCrashStore;
import com.newrelic.agent.android.stores.SharedPrefsEventStore;
import com.newrelic.agent.android.stores.SharedPrefsPayloadStore;
import com.newrelic.agent.android.tracing.TraceMachine;
import com.newrelic.agent.android.util.ActivityLifecycleBackgroundListener;
import com.newrelic.agent.android.util.AndroidEncoder;
import com.newrelic.agent.android.util.Connectivity;
import com.newrelic.agent.android.util.Encoder;
import com.newrelic.agent.android.util.OfflineStorage;
import com.newrelic.agent.android.util.PersistentUUID;
import com.newrelic.agent.android.util.Reachability;
import com.newrelic.agent.android.util.UiBackgroundListener;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AndroidAgentImpl implements
        AgentImpl,
        ConnectionListener,
        ApplicationStateListener,
        TraceMachineInterface {

    private static final AgentLog log = AgentLogManager.getAgentLog();

    private final Context context;
    private SavedState savedState;

    private final Lock lock = new ReentrantLock();

    private final Encoder encoder = new AndroidEncoder();

    // Cached application and device information
    DeviceInformation deviceInformation;
    private ApplicationInformation applicationInformation;

    private final AgentConfiguration agentConfiguration;

    // Producers and consumers that are tightly coupled to Android implementations
    private MachineMeasurementConsumer machineMeasurementConsumer;
    private OfflineStorage offlineStorageInstance;

    public AndroidAgentImpl(final Context context, final AgentConfiguration agentConfiguration) throws AgentInitializationException {
        // We want an Application context, not an Activity context.
        this.context = appContext(context);
        this.agentConfiguration = agentConfiguration;
        this.savedState = new SavedState(this.context);

        if (isDisabled()) {
            throw new AgentInitializationException("This version of the agent has been disabled");
        }

        initApplicationInformation();

        // Register ourselves with the TraceMachine
        TraceMachine.setTraceMachineInterface(this);

        agentConfiguration.setCrashStore(new SharedPrefsCrashStore(context));
        agentConfiguration.setPayloadStore(new SharedPrefsPayloadStore(context));
        agentConfiguration.setAnalyticsAttributeStore(new SharedPrefsAnalyticsAttributeStore(context));
        agentConfiguration.setEventStore(new SharedPrefsEventStore(context));
        offlineStorageInstance = new OfflineStorage(context);

        ApplicationStateMonitor.getInstance().addApplicationStateListener(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            // used to determine when app backgrounds
            final UiBackgroundListener backgroundListener;
            if (Agent.getMonoInstrumentationFlag().equals("YES")) {
                backgroundListener = new ActivityLifecycleBackgroundListener();
                if (backgroundListener instanceof Application.ActivityLifecycleCallbacks) {
                    try {
                        if (context.getApplicationContext() instanceof Application) {
                            Application application = (Application) context.getApplicationContext();
                            application.registerActivityLifecycleCallbacks((Application.ActivityLifecycleCallbacks) backgroundListener);
                            if (agentConfiguration.getApplicationFramework() == ApplicationFramework.Xamarin || agentConfiguration.getApplicationFramework() == ApplicationFramework.MAUI) {
                                ApplicationStateMonitor.getInstance().activityStarted();
                            }
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                }
            } else {
                backgroundListener = new UiBackgroundListener();
            }

            context.registerComponentCallbacks(backgroundListener);
        }

        setupSession();
    }

    protected void initialize() {
        // init this session's data
        setupSession();

        // Agent init now emits metrics and attributes,
        // so the analytics engine must be initialized first
        AnalyticsControllerImpl.initialize(agentConfiguration, this);

        Harvest.addHarvestListener(savedState);
        Harvest.initialize(agentConfiguration);
        Harvest.setHarvestConfiguration(savedState.getHarvestConfiguration());
        Harvest.setHarvestConnectInformation(savedState.getConnectInformation());

        Measurements.initialize();
        log.info(MessageFormat.format("New Relic Agent v{0}", Agent.getVersion()));
        log.verbose(MessageFormat.format("Application token: {0}", agentConfiguration.getApplicationToken()));

        machineMeasurementConsumer = new MachineMeasurementConsumer();
        Measurements.addMeasurementConsumer(machineMeasurementConsumer);

        StatsEngine.get().inc(MetricNames.SUPPORTABILITY_CRASH_UNCAUGHT_HANDLER
                .replace(MetricNames.TAG_NAME, getUnhandledExceptionHandlerName()));
        PayloadController.initialize(agentConfiguration);

        // Set up the sampler
        Sampler.init(context);

        if (isInstantApp()) {
            log.info("This appears to be an Instant App");
            final AnalyticsAttribute attribute = new AnalyticsAttribute(AnalyticsAttribute.INSTANT_APP_ATTRIBUTE, true);
            AnalyticsControllerImpl.getInstance().addAttributeUnchecked(attribute, false);
        }

        if (FeatureFlag.featureEnabled(FeatureFlag.NativeReporting)) {
            try {
                NativeReporting.initialize(context, agentConfiguration);
            } catch (NoClassDefFoundError e) {
                log.error("NativeReporting feature is enabled, but agent-ndk was not found (probably missing as a dependency).");
                log.error("Native reporting will not be enabled");
            }
        }
    }

    protected void setupSession() {
        //Don't preserve the previous session state!
        TraceMachine.clearActivityHistory();
        agentConfiguration.provideSessionId();
    }

    protected void finalizeSession() {
        // no-op at present
    }

    @Override
    // Clear and re-save the savedState if it's out of date.
    // Returns true if an update was performed
    public boolean updateSavedConnectInformation() {
        final ConnectInformation savedConnectInformation = savedState.getConnectInformation();
        final ConnectInformation newConnectInformation = new ConnectInformation(getApplicationInformation(), getDeviceInformation());

        if (!(newConnectInformation.equals(savedConnectInformation) && savedState.hasConnectionToken(agentConfiguration.getApplicationToken()))) {
            // saved state changed.  clear and re-save.  Harvester will re-save harvestConfiguration

            // check versionCode for app update
            if (newConnectInformation.getApplicationInformation().isAppUpgrade(savedConnectInformation.getApplicationInformation())) {
                StatsEngine.get().inc(MetricNames.MOBILE_APP_UPGRADE);

                final AnalyticsAttribute attribute = new AnalyticsAttribute(AnalyticsAttribute.APP_UPGRADE_ATTRIBUTE,
                        savedConnectInformation.getApplicationInformation().getAppVersion());
                AnalyticsControllerImpl.getInstance().addAttributeUnchecked(attribute, false);
            }

            savedState.clear();
            savedState.saveConnectInformation(newConnectInformation);
            savedState.saveConnectionToken(agentConfiguration.getApplicationToken());

            return true;
        } else {
            return false;
        }
    }

    @Override
    public DeviceInformation getDeviceInformation() {
        if (deviceInformation == null) {
            DeviceInformation info = new DeviceInformation();

            info.setOsName("Android");
            info.setOsVersion(android.os.Build.VERSION.RELEASE);
            info.setOsBuild(android.os.Build.VERSION.INCREMENTAL);
            info.setModel(android.os.Build.MODEL);
            info.setAgentName("AndroidAgent");
            info.setAgentVersion(Agent.getVersion());
            info.setManufacturer(android.os.Build.MANUFACTURER);
            info.setDeviceId(getUUID());
            info.setArchitecture(System.getProperty("os.arch"));
            info.setRunTime(System.getProperty("java.vm.version"));
            info.setSize(deviceForm(context).name().toLowerCase(Locale.getDefault()));
            info.setApplicationFramework(agentConfiguration.getApplicationFramework());
            info.setApplicationFrameworkVersion(agentConfiguration.getApplicationFrameworkVersion());

            deviceInformation = info;
        }

        return deviceInformation;
    }

    @Override
    @SuppressWarnings("deprecation")
    public EnvironmentInformation getEnvironmentInformation() {
        final EnvironmentInformation envInfo = new EnvironmentInformation();
        final ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);

        final long free[] = new long[2];
        try {
            final StatFs rootStatFs = new StatFs(Environment.getRootDirectory().getAbsolutePath());
            final StatFs externalStatFs = new StatFs(Environment.getExternalStorageDirectory().getAbsolutePath());

            if (Build.VERSION.SDK_INT >= 18) {
                free[0] = rootStatFs.getAvailableBlocksLong() * rootStatFs.getBlockSizeLong();
                free[1] = externalStatFs.getAvailableBlocksLong() * rootStatFs.getBlockSizeLong();
            } else {
                free[0] = rootStatFs.getAvailableBlocks() * rootStatFs.getBlockSize();
                free[1] = externalStatFs.getAvailableBlocks() * externalStatFs.getBlockSize();
            }
        } catch (Exception e) {
            AgentHealth.noticeException(e);
        } finally {
            // JSON serializer always expects some value for diskAvailable. Ensure they aren't negative.
            if (free[0] < 0)
                free[0] = 0;
            if (free[1] < 0)
                free[1] = 0;

            envInfo.setDiskAvailable(free);
        }
        envInfo.setMemoryUsage(Sampler.sampleMemory(activityManager).getSampleValue().asLong());
        envInfo.setOrientation(context.getResources().getConfiguration().orientation);
        envInfo.setNetworkStatus(getNetworkCarrier());
        envInfo.setNetworkWanType(getNetworkWanType());

        return envInfo;
    }

    @SuppressWarnings("deprecation")
    public void initApplicationInformation() throws AgentInitializationException {
        if (applicationInformation != null) {
            log.debug("attempted to reinitialize ApplicationInformation.");
            return;
        }

        final String packageName = context.getPackageName();
        final PackageManager packageManager = context.getPackageManager();

        PackageInfo packageInfo = null;
        try {
            packageInfo = packageManager.getPackageInfo(packageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new AgentInitializationException("Could not determine package version: " + e.getMessage());
        }

        String appVersion = agentConfiguration.getCustomApplicationVersion();
        if (TextUtils.isEmpty(appVersion)) {
            if (packageInfo != null && packageInfo.versionName != null && packageInfo.versionName.length() > 0) {
                appVersion = packageInfo.versionName;
            } else {
                throw new AgentInitializationException("Your app doesn't appear to have a version defined. Ensure you have defined 'versionName' in your manifest.");
            }

        }
        log.debug("Using application version " + appVersion);

        String appName;
        try {
            final ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            if (info != null) {
                appName = packageManager.getApplicationLabel(info).toString();
            } else {
                appName = packageName;
            }
        } catch (PackageManager.NameNotFoundException e) {
            log.warn(e.toString());
            appName = packageName;
        } catch (SecurityException e) {
            log.warn(e.toString());
            appName = packageName;
        }
        log.debug("Using application name " + appName);


        int versionCode;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            versionCode = (int) packageInfo.getLongVersionCode();
        } else {
            versionCode = packageInfo.versionCode;
        }
        String build = agentConfiguration.getCustomBuildIdentifier();
        if (TextUtils.isEmpty(build)) {
            if (packageInfo != null) {
                // set the versionCode as the build by default
                build = String.valueOf(versionCode);
            } else {
                build = "";
                log.warn("Your app doesn't appear to have a version code defined. Ensure you have defined 'versionCode' in your manifest.");
            }
        }
        log.debug("Using build " + build);

        applicationInformation = new ApplicationInformation(appName, appVersion, packageName, build);
        applicationInformation.setVersionCode(versionCode);
    }

    @Override
    public ApplicationInformation getApplicationInformation() {
        return applicationInformation;
    }

    @Override
    public long getSessionDurationMillis() {
        return Harvest.getMillisSinceStart();
    }

    private static DeviceForm deviceForm(final Context context) {
        final int deviceSize = context.getResources().getConfiguration().screenLayout & android.content.res.Configuration.SCREENLAYOUT_SIZE_MASK;
        switch (deviceSize) {
            case android.content.res.Configuration.SCREENLAYOUT_SIZE_SMALL: {
                return DeviceForm.SMALL;
            }
            case android.content.res.Configuration.SCREENLAYOUT_SIZE_NORMAL: {
                return DeviceForm.NORMAL;
            }
            case android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE: {
                return DeviceForm.LARGE;
            }
            default: {
                //
                // Android 2.2 doesn't have the XLARGE constant.
                //
                if (deviceSize > android.content.res.Configuration.SCREENLAYOUT_SIZE_LARGE) {
                    return DeviceForm.XLARGE;
                } else {
                    return DeviceForm.UNKNOWN;
                }
            }
        }
    }

    private static Context appContext(final Context context) {
        if (!(context instanceof Application)) {
            return context.getApplicationContext();
        } else {
            return context;
        }
    }

    // never called
    @Deprecated
    @Override
    public void addTransactionData(TransactionData transactionData) {
    }

    // never called
    @Deprecated
    @Override
    public void mergeTransactionData(List<TransactionData> transactionDataList) {
    }

    // never called
    @Deprecated
    @Override
    public List<TransactionData> getAndClearTransactionData() {
        return null;
    }

    @Override
    public String getCrossProcessId() {
        lock.lock();
        try {
            return savedState.getCrossProcessId();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getStackTraceLimit() {
        lock.lock();
        try {
            return savedState.getStackTraceLimit();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getResponseBodyLimit() {
        lock.lock();
        try {
            return savedState.getHarvestConfiguration().getResponse_body_limit();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void start() {
        if (!isDisabled()) {
            initialize();
            Harvest.start();

            if (FeatureFlag.featureEnabled(FeatureFlag.NativeReporting)) {
                try {
                    if (NativeReporting.isInitialized()) {
                        NativeReporting.getInstance().start();
                    }
                } catch (NoClassDefFoundError e) {
                    log.error("Native reporting is not enabled");
                }
            }

            if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
                // assume a user action caused the agent to start or return to foreground
                UserActionFacade.getInstance().recordUserAction(UserActionType.AppLaunch);
            }
        } else {
            stop(false);
        }
    }

    @Override
    public void stop() {
        stop(true);
    }

    void stop(boolean finalSendData) {
        if (FeatureFlag.featureEnabled(FeatureFlag.DistributedTracing)) {
            // assume some user action caused the agent to go to background
            UserActionFacade.getInstance().recordUserAction(UserActionType.AppBackground);
        }

        // clean up session data
        finalizeSession();

        Sampler.shutdown();
        TraceMachine.haltTracing();

        final AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
        final EventManager eventManager = analyticsController.getEventManager();

        if (!NewRelic.isShutdown) {
            // Emit a supportability metric that records the number of events recorded versus ejected
            // during this session
            int eventsRecorded = eventManager.getEventsRecorded();
            int eventsEjected = eventManager.getEventsEjected();

            Measurements.addCustomMetric(MetricNames.SUPPORTABILITY_EVENTS + "Recorded", MetricCategory.NONE.name(),
                    eventsRecorded, eventsEjected, eventsEjected, MetricUnit.OPERATIONS, MetricUnit.OPERATIONS);
        }

        if (finalSendData) {
            if (isUIThread()) {
                StatsEngine.get().inc(MetricNames.SUPPORTABILITY_HARVEST_ON_MAIN_THREAD);
            }

            try {
                //clear all existing data during shutdown process
                if (NewRelic.isStarted() && NewRelic.isShutdown) {
                    clearExistingData();

                    //make sure to add shutdown supportability metrics
                    for (ConcurrentHashMap.Entry<String, Metric> entry : StatsEngine.notice().getStatsMap().entrySet()) {
                        Metric metric = entry.getValue();
                        if (Harvest.getInstance().getHarvestData() != null && Harvest.getInstance().getHarvestData().getMetrics() != null) {
                            Harvest.getInstance().getHarvestData().getMetrics().addMetric(metric);
                        }
                    }
                }
            } catch (Exception ex) {
                log.error("There is an error during shutdown process: " + ex.getLocalizedMessage());
            }

            Harvest.harvestNow(true);

            HarvestData harvestData = Harvest.getInstance().getHarvestData();
            log.debug("EventManager: recorded[" + eventManager.getEventsRecorded() + "] ejected[" + eventManager.getEventsEjected() + "]");
            if (harvestData != null && harvestData.isValid()) {
                Collection<AnalyticsEvent> events = harvestData.getAnalyticsEvents();
                if (!events.isEmpty()) {
                    log.warn("Agent stopped with " + events.size() + " events dropped from failed harvest.");
                }
                if (0 < eventManager.size()) {
                    log.warn("Agent stopped with " + eventManager.size() + " events left in event pool.");
                }
            }
        }

        if (FeatureFlag.featureEnabled(FeatureFlag.NativeReporting)) {
            try {
                NativeReporting.shutdown();
            } catch (NoClassDefFoundError e) {
                // ignored
            }
        }

        AnalyticsControllerImpl.shutdown();
        TraceMachine.clearActivityHistory();
        Harvest.shutdown();
        Measurements.shutdown();
        PayloadController.shutdown();
    }

    @Override
    public void disable() {
        log.warn("PERMANENTLY DISABLING AGENT v" + Agent.getVersion());
        try {
            savedState.saveDisabledVersion(Agent.getVersion());
        } finally {
            try {
                stop(false);
            } finally {
                Agent.setImpl(NullAgentImpl.instance);
            }
        }
    }

    public boolean isDisabled() {
        return Agent.getVersion().equals(savedState.getDisabledVersion());
    }

    public String getNetworkCarrier() {
        return Connectivity.carrierNameFromContext(context);
    }

    public String getNetworkWanType() {
        return Connectivity.wanType(context);
    }

    /**
     * Initialize & start the New Relic agent.
     *
     * @param context
     * @param agentConfiguration
     */
    public static void init(final Context context, final AgentConfiguration agentConfiguration) {
        try {
            Agent.setImpl(new AndroidAgentImpl(context, agentConfiguration));
            Agent.start();
        } catch (AgentInitializationException e) {
            log.error("Failed to initialize the agent: " + e.toString());
            return;
        }
    }

    /*
     * (non-Javadoc)
     * @see com.newrelic.agent.android.api.v1.ConnectionListener#connected(com.newrelic.agent.android.api.v1.ConnectionEvent)
     */
    // never called
    @Deprecated
    @Override
    public void connected(ConnectionEvent e) {
        log.error("AndroidAgentImpl: connected ");
    }

    /*
     * (non-Javadoc)
     * @see com.newrelic.agent.android.api.v1.ConnectionListener#disconnected(com.newrelic.agent.android.api.v1.ConnectionEvent)
     */
    // never called
    @Deprecated
    @Override
    public void disconnected(ConnectionEvent e) {
        savedState.clear();
    }


    @Override
    public void applicationForegrounded(ApplicationStateEvent e) {
        log.info("AndroidAgentImpl: application foregrounded");
        if (!NewRelic.isShutdown) {
            start();
        }
    }

    @Override
    public void applicationBackgrounded(ApplicationStateEvent e) {
        log.info("AndroidAgentImpl: application backgrounded");
        stop();
    }

    @Override
    public void setLocation(String countryCode, String adminRegion) {
        if (countryCode == null || adminRegion == null) {
            throw new IllegalArgumentException("Country code and administrative region are required.");
        }
        // api.setLocation(new com.newrelic.agent.android.instrumentation.Location(countryCode, adminRegion));
    }


    /**
     * Android specific interface to set Location. Uses Geocoder to resolve
     * a Location to a country and region code.
     *
     * @param location An android.location.Location
     */
    public void setLocation(Location location) {
        if (location == null) {
            throw new IllegalArgumentException("Location must not be null.");
        }

        final Geocoder coder = new Geocoder(context);
        List<Address> addresses = null;
        try {
            addresses = coder.getFromLocation(location.getLatitude(), location.getLongitude(), 1);
        } catch (IOException e) {
            log.error("Unable to geocode location: " + e.toString());
        }

        if (addresses == null || addresses.size() == 0) {
            // No addresses returned.
            return;
        }

        final Address address = addresses.get(0);
        if (address == null) {
            // Address list entry is null.
            return;
        }

        final String countryCode = address.getCountryCode();
        final String adminArea = address.getAdminArea();

        if (countryCode != null && adminArea != null) {
            setLocation(countryCode, adminArea);
        }
    }

    /**
     * Used to sort transactions in descending order by timestamp.
     */
    private static final Comparator<TransactionData> cmp = new Comparator<TransactionData>() {
        @Override
        public int compare(TransactionData lhs, TransactionData rhs) {
            if (lhs.getTimestamp() > rhs.getTimestamp()) {
                return -1;
            } else if (lhs.getTimestamp() < rhs.getTimestamp()) {
                return 1;
            } else {
                return 0;
            }
        }
    };

    /**
     * Get a UUID for the current device.
     *
     * @return A random UUID. This UUID is generated once and then stored in SharedPreferences.
     * This will be overridden by any value injected into the agent configuration.
     */
    String getUUID() {
        String uuid = savedState.getConnectInformation().getDeviceInformation().getDeviceId();

        if (TextUtils.isEmpty(uuid)) {
            PersistentUUID persistentUUID = new PersistentUUID(context);
            uuid = persistentUUID.getPersistentUUID();
            savedState.saveDeviceId(uuid);
            StatsEngine.get().inc(MetricNames.METRIC_UUID_CREATED);
        }

        // override the the UUID with any non-null present in configuration
        String configuredUUID = agentConfiguration.getDeviceID();
        if (configuredUUID != null) {
            uuid = configuredUUID;
            StatsEngine.get().inc(MetricNames.METRIC_UUID_OVERRIDDEN);
        }

        return uuid;
    }

    private String getUnhandledExceptionHandlerName() {
        try {
            return Thread.getDefaultUncaughtExceptionHandler().getClass().getName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private void clearExistingData() {
        try {
            //clear harvestData
            if (Harvest.getInstance() != null && Harvest.getInstance().getHarvestData() != null) {
                HarvestData harvestData = Harvest.getInstance().getHarvestData();
                harvestData.reset();
            }

            //clear activity traces
            TraceMachine.clearActivityHistory();

            //clear analytics
            AnalyticsControllerImpl analyticsController = AnalyticsControllerImpl.getInstance();
            if (analyticsController != null) {
                analyticsController.clear();
            }

            //clear measurementEngine
            MeasurementEngine measurementEngine = new MeasurementEngine();
            if (measurementEngine != null) {
                measurementEngine.clear();
            }
        } catch (Exception ex) {
            log.error("There is an error while clean data during shutdown process: " + ex.getLocalizedMessage());
        }
    }

    public Encoder getEncoder() {
        return encoder;
    }

    // TraceMachineInterface methods
    @Override
    public long getCurrentThreadId() {
        return Thread.currentThread().getId();
    }

    @Override
    public boolean isUIThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    @Override
    public String getCurrentThreadName() {
        return Thread.currentThread().getName();
    }

    protected SavedState getSavedState() {
        return savedState;
    }

    protected void setSavedState(SavedState savedState) {
        this.savedState = savedState;
    }

    public boolean hasReachableNetworkConnection(String reachableHost) {
        return Reachability.hasReachableNetworkConnection(context, reachableHost);
    }

    @Override
    public boolean isInstantApp() {
        return InstantApps.isInstantApp(context);
    }

    @Override
    public void persistHarvestDataToDisk(String data) {
        offlineStorageInstance.persistHarvestDataToDisk(data);
    }

    @Override
    public Map<String, String> getAllOfflineData() {
        return offlineStorageInstance.getAllOfflineData();
    }
}

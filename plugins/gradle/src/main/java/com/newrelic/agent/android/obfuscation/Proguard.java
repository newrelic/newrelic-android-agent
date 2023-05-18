/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.obfuscation;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.newrelic.agent.util.BuildId;
import com.newrelic.agent.util.Streams;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.slf4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Proguard {
    public static final String NR_PROPERTIES = "newrelic.properties";
    public static final String MAPPING_FILENAME = "mapping.txt";
    public static final String MAPPING_FILE_KEY = "com.newrelic.mapping.txt";
    public static final String MAPPING_PROVIDER_KEY = "com.newrelic.mapping.provider";
    public static final String VARIANT_KEY = "com.newrelic.mapping.variant";
    public static final String LOGLEVEL_KEY = "com.newrelic.loglevel";
    public static final String PROJECT_ROOT_KEY = "com.newrelic.projectroot";
    public static final String NR_MAP_PREFIX = "# NR_BUILD_ID -> ";

    private static final String PROP_NR_APP_TOKEN = "com.newrelic.application_token";
    private static final String PROP_UPLOADING_ENABLED = "com.newrelic.enable_proguard_upload";
    private static final String PROP_MAPPING_API_HOST = "com.newrelic.mapping_upload_host";
    private static final String PROP_COMPRESSED_UPLOADS = "com.newrelic.compressed_uploads";
    private static final String PROP_SSL_CONNECTION = "com.newrelic.ssl_connection";
    private static final String DEFAULT_MAPPING_API_HOST = "mobile-symbol-upload.newrelic.com";
    private static final String DEFAULT_REGION_MAPPING_API_HOST = "mobile-symbol-upload.%s.nr-data.net";
    private static final String MAPPING_API_PATH = "/symbol";
    private static final String NR_COMPILER_PREFIX = "# compiler: ";
    private static final String NR_COMPILER_VERSION_PREFIX = "# compiler_version: ";
    private static final String NEWLN = System.getProperty("line.separator", "\r\n");

    static final class Network {
        public static final String APPLICATION_LICENSE_HEADER = "X-App-License-Key";
        public static final String REQUEST_DEBUG_HEADER = "X-APP-REQUEST-DEBUG";
        public static final String CONTENT_LENGTH_HEADER = "Content-Length";

        static final class ContentType {
            public static final String HEADER = "Content-Type";
            public static final String URL_ENCODED = "application/x-www-form-urlencoded";
            public static final String MULTIPART_FORM_DATA = "multipart/form-data";
        }
    }

    static public class Provider {
        public static final String PROGUARD_603 = "proguard:6.0.3";
        public static final String DEXGUARD = "dexguard";
        public static final String R8 = "r8";
        public static final String DEFAULT = R8;

        public static String decompose(String mapProvider) {
            String[] parts = mapProvider.split("[:]");
            String header = NR_COMPILER_PREFIX + mapProvider + NEWLN;

            if (parts.length > 0) {
                header = NR_COMPILER_PREFIX + parts[0] + NEWLN;
                if (parts.length > 1) {
                    header += NR_COMPILER_VERSION_PREFIX + parts[1] + NEWLN;
                }
            }

            return header;
        }
    }

    private final Logger log;

    private String projectRoot;
    private String licenseKey = null;
    private boolean uploadingEnabled = true;
    private String mappingApiHost = DEFAULT_MAPPING_API_HOST;
    private boolean compressedUploads = true;
    private boolean sslConnection = true;

    private static Map<String, String> agentOptions = Collections.emptyMap();
    private static String buildId = BuildId.getBuildId(BuildId.DEFAULT_VARIANT);

    private Properties newRelicProps;

    public Proguard(final Logger log, final Map<String, String> agentOptions) {
        this.log = log;
        Proguard.agentOptions = agentOptions;
    }

    public void findAndSendMapFile() {
        if (getProjectRoot() != null) {
            if (!fetchConfiguration()) {
                return;
            }

            final File projectRoot = new File(getProjectRoot());

            Collection<File> files = new ArrayList<>();

            // when launched for a Gradle task, the map location *should** be specified in the config
            if (agentOptions.containsKey(Proguard.MAPPING_FILE_KEY)) {
                final File mappingFile = new File(agentOptions.get(Proguard.MAPPING_FILE_KEY));
                if (mappingFile.exists()) {
                    files.add(mappingFile);
                } else {
                    log.warn("Mapping file [" + mappingFile.getAbsolutePath() + "] doesn't exist");
                }

            } else {
                // but when launched from legacy instrumentation, we'll still have to locate the map
                final IOFileFilter fileFilter = FileFilterUtils.nameFileFilter(MAPPING_FILENAME);
                files = FileUtils.listFiles(projectRoot, fileFilter, TrueFileFilter.INSTANCE);
            }

            if (files.isEmpty()) {
                log.error("While evidence of ProGuard/DexGuard was detected, New Relic failed to find 'mapping.txt' files.");
                logRecourse();
                return;
            }

            for (final File file : files) {
                try {
                    if (shouldUploadMapFile(file)) {
                        if (uploadingEnabled) {
                            sendMapping(file);
                        } else {
                            log.error("Map uploads are disabled!");
                        }
                    }
                } catch (IOException e) {
                    log.error("Unable to open ProGuard/DexGuard 'mapping.txt' file: " + e.getLocalizedMessage());
                    logRecourse();
                }
            }
        }
    }

    @SuppressWarnings("deprecation")
    boolean shouldUploadMapFile(File file) throws IOException {

        if (file.exists()) {
            try (ReversedLinesFileReader revReader = new ReversedLinesFileReader(file)) {
                final String lastLine = revReader.readLine();
                revReader.close();

                if (lastLine == null || lastLine.isEmpty()) {
                    log.warn("Map [" + file.getAbsolutePath() + "] is empty!");
                } else if (lastLine.startsWith(NR_MAP_PREFIX)) {
                    // replace build id with parsed value
                    buildId = lastLine.substring(NR_MAP_PREFIX.length());
                    log.info("Map [" + file.getAbsolutePath() + "] has already been tagged with buildID [" + buildId + "] - resending.");
                    return true;        // send it again
                } else {
                    String variant = agentOptions.get(VARIANT_KEY);
                    buildId = BuildId.getBuildId(variant);
                    log.info("Tagging map [" + file.getAbsolutePath() + "] with buildID [" + buildId + "]");
                    // Write the build ID to the file so the user can uploaded it manually later.
                    try (final FileWriter fileWriter = new FileWriter(file, true)) {
                        fileWriter.write(NR_MAP_PREFIX + buildId + NEWLN);
                        fileWriter.close();
                        return true;    // send it
                    }
                }
            }
        }
        return false;
    }

    String getProjectRoot() {
        if (projectRoot == null) {
            final String encodedProjectRoot = agentOptions.get(PROJECT_ROOT_KEY);
            if (encodedProjectRoot == null) {
                // Fall back to the CWD
                log.info("Unable to determine project root, falling back to CWD.");
                projectRoot = System.getProperty("user.dir");
            } else {
                projectRoot = new String(BaseEncoding.base64().decode(encodedProjectRoot));
            }
        }

        return projectRoot;
    }

    private boolean fetchConfiguration() {
        try (final Reader propsReader = new BufferedReader(new FileReader(getProjectRoot() + File.separator + NR_PROPERTIES))) {
            newRelicProps = new Properties();

            newRelicProps.load(propsReader);

            uploadingEnabled = newRelicProps.getProperty(PROP_UPLOADING_ENABLED, "true").equals("true");
            compressedUploads = newRelicProps.getProperty(PROP_COMPRESSED_UPLOADS, "true").equals("true");
            sslConnection = newRelicProps.getProperty(PROP_SSL_CONNECTION, "true").equals("true");

            licenseKey = newRelicProps.getProperty(PROP_NR_APP_TOKEN, null);
            if (licenseKey == null) {
                log.error("Unable to find a value for " + PROP_NR_APP_TOKEN + " in 'newrelic.properties'");
                logRecourse();
                return false;
            }

            mappingApiHost = newRelicProps.getProperty(PROP_MAPPING_API_HOST, null);
            if (mappingApiHost == null) {
                String region = parseRegionFromApplicationToken(licenseKey);
                if (region != null) {
                    mappingApiHost = String.format(Locale.getDefault(), Proguard.DEFAULT_REGION_MAPPING_API_HOST, region);
                }
            }

        } catch (FileNotFoundException e) {
            log.error("Unable to find 'newrelic.properties' in the project root (" + getProjectRoot() + "): " + e.getLocalizedMessage());
            logRecourse();
            return false;

        } catch (IOException e) {
            log.error("Unable to read 'newrelic.properties' in the project root (" + getProjectRoot() + "): " + e.getLocalizedMessage());
            logRecourse();
            return false;

        }

        return true;
    }

    private void sendMapping(File mapFile) throws IOException {
        final int USEFUL_BUFFER_SIZE = 0x10000;     // 64k

        if (mapFile.length() <= 0) {
            log.error("Tried to send a zero-length map file!");
            return;
        }

        HttpURLConnection connection = null;

        try {
            String host = DEFAULT_MAPPING_API_HOST;

            if (mappingApiHost != null) {
                host = mappingApiHost;
            }

            if (!host.startsWith("http")) {
                host = (sslConnection ? "https://" : "http://") + host;
            }

            final URL url = new URL(host + MAPPING_API_PATH);

            connection = (HttpURLConnection) url.openConnection();

            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");
            connection.setRequestProperty(Network.APPLICATION_LICENSE_HEADER, licenseKey);

            // use the blocking API call if debug enabled
            if (agentOptions.containsKey(LOGLEVEL_KEY) && agentOptions.get(LOGLEVEL_KEY).equalsIgnoreCase("debug")) {
                log.debug("Map upload request is synchronous");
                connection.setRequestProperty(Network.REQUEST_DEBUG_HEADER, "NRMA");
            }

            if (compressedUploads) {
                // used if compressedUploads is true
                final File zipFile = new File(mapFile.getAbsolutePath() + ".zip");

                try (final FileInputStream fis = new FileInputStream(mapFile);
                     final FileOutputStream fos = new FileOutputStream(zipFile);
                     final ZipOutputStream zos = new ZipOutputStream(fos)) {
                    ZipEntry zipEntry = new ZipEntry(mapFile.getName());

                    zos.putNextEntry(zipEntry);
                    Streams.copy(fis, zos, USEFUL_BUFFER_SIZE);
                    zos.finish();

                    connection.setRequestProperty(Network.ContentType.HEADER, Network.ContentType.MULTIPART_FORM_DATA
                            + "; boundary=" + MultipartFormWriter.boundary);
                    connection.setRequestProperty(Network.CONTENT_LENGTH_HEADER, String.valueOf(zipFile.length()));

                    mapFile = zipFile;  // the input is now the *zipped* map
                }
            } else {
                connection.setRequestProperty(Network.ContentType.HEADER, Network.ContentType.URL_ENCODED);
            }

            final OutputStream outputStrm = connection.getOutputStream();
            String providerHeader = null;

            // insert encoding compiler at head of file (like R8 does)
            if (agentOptions.containsKey(Proguard.MAPPING_PROVIDER_KEY)) {
                String mapProvider = agentOptions.get(Proguard.MAPPING_PROVIDER_KEY);
                if (!Strings.isNullOrEmpty(mapProvider)) {
                    if (!mapProvider.toLowerCase().startsWith(Provider.R8)) {
                        providerHeader = Provider.decompose(mapProvider);
                    }
                }
            }

            // the request body:
            try (final FileInputStream fis = new FileInputStream(mapFile);
                 final DataOutputStream dos = new DataOutputStream(outputStrm)) {

                if (compressedUploads) {
                    // multipart form-data
                    MultipartFormWriter formWriter = new MultipartFormWriter(dos, USEFUL_BUFFER_SIZE);
                    formWriter.writeFilePart("zip", mapFile, fis);
                    formWriter.finish();

                } else {
                    // url-encoded byte string
                    dos.writeBytes("proguard=");

                    if (!Strings.isNullOrEmpty(providerHeader)) {
                        dos.writeBytes(providerHeader);
                    }

                    try (final BufferedInputStream bis = new BufferedInputStream(fis)) {
                        final byte[] cbuf = new byte[USEFUL_BUFFER_SIZE];
                        while (bis.read(cbuf, 0, USEFUL_BUFFER_SIZE) != -1) {
                            if (cbuf.length > 0) {
                                dos.writeBytes(URLEncoder.encode(new String(cbuf), "UTF-8"));
                            }
                        }
                    }
                }

                dos.writeBytes("&buildId=" + buildId);
                dos.flush();
                log.debug("sendMapping writing [" + dos.size() + "] bytes" + (compressedUploads ? " (compressed)" : ""));

            } finally {
                outputStrm.close();
            }

            final int responseCode = connection.getResponseCode();
            log.debug("Mapping.txt upload returns [" + responseCode + "]");

            switch (responseCode) {
                case HttpURLConnection.HTTP_OK:
                    log.info("Mapping.txt updated.");
                    break;

                case HttpURLConnection.HTTP_CREATED:
                    log.info("Successfully sent ProGuard/DexGuard 'mapping.txt' to New Relic.");
                    break;

                case HttpURLConnection.HTTP_ACCEPTED:
                    log.info("Successfully sent ProGuard/DexGuard 'mapping.txt' to New Relic for background processing.");
                    break;

                case HttpURLConnection.HTTP_BAD_REQUEST:
                    try (InputStream inputStream = connection.getErrorStream()) {
                        String response = Streams.slurp(inputStream, "UTF-8");
                        if (Strings.isNullOrEmpty(response)) {
                            response = connection.getResponseMessage();
                        }
                        log.error("Unable to send ProGuard/DexGuard 'mapping.txt' to New Relic: " + response);
                        logRecourse();
                    }
                    break;

                case HttpURLConnection.HTTP_CONFLICT:
                    log.info("A ProGuard/DexGuard 'mapping.txt' tagged with build ID [" + buildId + "] has already been stored.");
                    break;

                default:
                    if (responseCode > HttpURLConnection.HTTP_BAD_REQUEST) {
                        try (InputStream inputStream = connection.getErrorStream()) {
                            String response = Streams.slurp(inputStream, "UTF-8");
                            if (Strings.isNullOrEmpty(response)) {
                                response = connection.getResponseMessage();
                            }
                            log.error("Unable to send ProGuard/DexGuard 'mapping.txt' to New Relic - received status " + responseCode + ": " + response);
                            logRecourse();
                        }
                    } else {
                        log.error("ProGuard/DexGuard 'mapping.txt' upload return [" + responseCode + "]");
                    }
                    break;
            }

        } catch (Exception e) {
            log.error("An error occurred uploading ProGuard/DexGuard 'mapping.txt' to New Relic: " + e.getLocalizedMessage());
            logRecourse();

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void logRecourse() {
        log.error("To de-obfuscate crashes, upload the build's ProGuard/DexGuard 'mapping.txt' manually,");
        log.error("or run the 'newRelicMapUpload<Variant>' or 'newRelicProguardScanTask' Gradle tasks.");
        log.error("For more help, see 'https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile-android/install-configure/android-agent-crash-reporting'");
    }

    /**
     * The first six bytes of the Mobile license key MUST match the regex pattern ^(.+?x).
     * If the regex does not match, the hostname MUST default to mobile-collector.newrelic.com.
     * If the regex matches, agents MUST strip trailing x characters from the matched identifier and
     * insert the result between mobile-collector. and .newrelic.com.
     * <p>
     * This is a duplicate of a method defined in agent-core:AgentConfiguration; linking to the agent
     * jar from the class rewriter is problematic.
     *
     * @param applicationToken
     * @return Normalized region specifier, or null if region not detected
     */
    String parseRegionFromApplicationToken(String applicationToken) {

        if (null == applicationToken || "".equals(applicationToken)) {
            return null;
        }

        // spec says: [a-z]{2,3}[0-9]{2}x{1,2}
        final Pattern pattern = Pattern.compile("^(.+?)x{1,2}.*");
        final Matcher matcher = pattern.matcher(applicationToken);

        if (matcher.matches()) {
            try {
                final String prefix = matcher.group(1);
                if (prefix == null || "".equals(prefix)) {
                    log.warn("Region prefix empty");
                } else {
                    return prefix;
                }

            } catch (Exception e) {
                log.error("getRegionalCollectorFromLicenseKey: " + e);
            }
        }

        return null;
    }

    static class MultipartFormWriter {
        final static String boundary = "===" + System.currentTimeMillis() + "===";
        final static String newLn = System.getProperty("line.separator", "\r\n");

        final OutputStream os;
        final int bufferSz;

        private void writeString(final String string) throws IOException {
            os.write(string.getBytes());
        }

        public MultipartFormWriter(DataOutputStream dos, int bufferSz) {
            this.os = dos;
            this.bufferSz = bufferSz;
        }

        void writeFilePart(final String partName, final File filePart, final InputStream is) throws IOException {
            writeString("--" + boundary + newLn);
            writeString("Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + filePart.getName() + "\"" + newLn);
            writeString("Content-Type: " + URLConnection.guessContentTypeFromName(filePart.getName()) + newLn);
            writeString("Content-Transfer-Encoding: binary" + newLn + newLn);
            Streams.copy(is, os, bufferSz);            // the file itself
        }

        public void finish() throws IOException {
            writeString(newLn + "--" + boundary + "--" + newLn);
        }
    }

}

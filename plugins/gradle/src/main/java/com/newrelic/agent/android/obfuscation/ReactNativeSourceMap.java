/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.obfuscation;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.newrelic.agent.util.Streams;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles React Native source map uploads to New Relic Symbol Ingest API.
 */
public class ReactNativeSourceMap {

    public static final String NR_PROPERTIES = "newrelic.properties";

    public static final String SOURCE_MAP_FILENAME = "index.android.bundle.map";
    public static final String PROJECT_ROOT_KEY = "com.newrelic.projectroot";

    static final String PROP_NR_APP_TOKEN = "com.newrelic.application_token";
    static final String PROP_NR_API_KEY = "com.newrelic.api_key";
    static final String PROP_UPLOADING_ENABLED = "com.newrelic.react_native_sourcemap_upload";
    static final String PROP_SOURCEMAP_API_HOST = "com.newrelic.react_native_sourcemap_upload_host";
    static final String PROP_SSL_CONNECTION = "com.newrelic.ssl_connection";
    static final String PROP_COMPRESSED_UPLOADS = "com.newrelic.compressed_uploads";

    static final String DEFAULT_SOURCEMAP_API_HOST = "symbol-ingest-api-service.newrelic.com";
    static final String DEFAULT_REGION_SOURCEMAP_API_HOST = "symbol-ingest-api.%s.nr-data.net";
    static final String DEFAULT_SOURCEMAP_API_PATH = "/v1/react-native/sourcemaps";

    static final int USEFUL_BUFFER_SIZE = 0x10000;  // 64k

    static final class Network {
        public static final String APPLICATION_LICENSE_HEADER = "X-APP-LICENSE-KEY";
        public static final String API_KEY_HEADER = "Api-Key";
        public static final String CONTENT_LENGTH_HEADER = "Content-Length";
        public static final String AGENT_VERSION_HEADER = "X-NewRelic-Agent-Version";
        public static final String AGENT_OSNAME_HEADER = "X-NewRelic-OS-Name";
        public static final String AGENT_PLATFORM_HEADER = "X-NewRelic-Platform";

        static final class ContentType {
            public static final String HEADER = "Content-Type";
            public static final String MULTIPART_FORM_DATA = "multipart/form-data";
        }
    }

    private final Logger log;

    String projectRoot;
    String licenseKey = null;
    String apiKey = null;
    boolean uploadingEnabled = true;
    boolean compressedUploads = true;
    String sourceMapApiHost = DEFAULT_SOURCEMAP_API_HOST;
    boolean sslConnection = true;

    private static Map<String, String> agentOptions = Collections.emptyMap();

    private String buildId;
    private String appVersionId;
    private Properties newRelicProps;

    public ReactNativeSourceMap(final Logger log, final Map<String, String> agentOptions) {
        this.log = log;
        ReactNativeSourceMap.agentOptions = agentOptions;
    }

    public void uploadSourceMap(File sourceMapFile, String buildId, String appVersionId) {
        this.buildId = buildId;
        this.appVersionId = appVersionId;

        if (getProjectRoot() != null) {
            if (!fetchConfiguration()) {
                return;
            }

            if (!uploadingEnabled) {
                log.info("React Native source map uploads are disabled.");
                return;
            }

            if (sourceMapFile == null || !sourceMapFile.exists()) {
                log.warn("React Native source map file not found: " + (sourceMapFile != null ? sourceMapFile.getAbsolutePath() : "null"));
                return;
            }

            if (sourceMapFile.length() <= 0) {
                log.error("React Native source map file is empty: " + sourceMapFile.getAbsolutePath());
                return;
            }

            try {
                sendSourceMap(sourceMapFile);
            } catch (IOException e) {
                log.error("Unable to upload React Native source map: " + e.getLocalizedMessage());
                logRecourse();
            }
        }
    }

    String getProjectRoot() {
        if (projectRoot == null) {
            final String encodedProjectRoot = agentOptions.get(PROJECT_ROOT_KEY);
            if (encodedProjectRoot == null) {
                log.info("Unable to determine project root, falling back to CWD.");
                projectRoot = System.getProperty("user.dir");
            } else {
                projectRoot = new String(BaseEncoding.base64().decode(encodedProjectRoot));
            }
        }

        return projectRoot;
    }

    boolean fetchConfiguration() {
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

            apiKey = newRelicProps.getProperty(PROP_NR_API_KEY, null);
            if (apiKey == null) {
                log.error("Unable to find a value for " + PROP_NR_API_KEY + " in 'newrelic.properties'");
                log.error("The Api-Key is required for React Native source map uploads.");
                logRecourse();
                return false;
            }

            sourceMapApiHost = newRelicProps.getProperty(PROP_SOURCEMAP_API_HOST, null);
            if (sourceMapApiHost == null) {
                String region = parseRegionFromApplicationToken(licenseKey);
                if (region != null) {
                    sourceMapApiHost = String.format(Locale.getDefault(), DEFAULT_REGION_SOURCEMAP_API_HOST, region);
                } else {
                    sourceMapApiHost = DEFAULT_SOURCEMAP_API_HOST;
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

    protected void sendSourceMap(File sourceMapFile) throws IOException {
        HttpURLConnection connection = getHttpURLConnection();

        try {
            connection.setRequestProperty(Network.ContentType.HEADER, Network.ContentType.MULTIPART_FORM_DATA
                    + "; boundary=" + MultipartFormWriter.boundary);


            final OutputStream outputStrm = connection.getOutputStream();

            // Prepare file data - optionally compress
            byte[] fileData;
            String fileName;
            long originalSize = sourceMapFile.length();

            try (FileInputStream fis = new FileInputStream(sourceMapFile)) {
                if (compressedUploads) {
                    // Zip compress the source map (server accepts .zip extension)
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                        ZipEntry zipEntry = new ZipEntry(sourceMapFile.getName());
                        zos.putNextEntry(zipEntry);
                        Streams.copy(fis, zos, USEFUL_BUFFER_SIZE);
                        zos.closeEntry();
                    }
                    fileData = baos.toByteArray();
                    fileName = sourceMapFile.getName() + ".zip";
                    log.info("Compressed source map from " + originalSize + " to " + fileData.length + " bytes (" +
                            String.format("%.1f", (100.0 - (fileData.length * 100.0 / originalSize))) + "% reduction)");
                } else {
                    // Read uncompressed
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    Streams.copy(fis, baos, USEFUL_BUFFER_SIZE);
                    fileData = baos.toByteArray();
                    fileName = sourceMapFile.getName();
                }
            }

            try (ByteArrayInputStream bis = new ByteArrayInputStream(fileData);
                 DataOutputStream dos = new DataOutputStream(outputStrm)) {

                MultipartFormWriter formWriter = new MultipartFormWriter(dos, USEFUL_BUFFER_SIZE);

                // Write form fields first (matching curl order)
                // Write the appVersionId
                formWriter.writeFieldPart("appVersionId", appVersionId);

                // Write the jsBundleId
                formWriter.writeFieldPart("jsBundleId", appVersionId);

                // Write the sourcemapName (original filename without .gz)
                formWriter.writeFieldPart("sourcemapName", sourceMapFile.getName());

                // Write the source map file last
                formWriter.writeFilePart("sourcemap", fileName, bis, compressedUploads);

                formWriter.finish();

                dos.flush();
                log.debug("sendSourceMap writing [" + dos.size() + "] bytes" + (compressedUploads ? " (zip compressed)" : ""));

            } finally {
                outputStrm.close();
            }

            final int responseCode = connection.getResponseCode();
            log.debug("React Native source map upload returns [" + responseCode + "]");

            switch (responseCode) {
                case HttpURLConnection.HTTP_OK:
                    log.debug("React Native source map updated.");
                    break;

                case HttpURLConnection.HTTP_CREATED:
                    log.info("Successfully sent React Native source map to New Relic.");
                    break;

                case HttpURLConnection.HTTP_ACCEPTED:
                    log.info("Successfully sent React Native source map to New Relic for background processing.");
                    break;

                case HttpURLConnection.HTTP_BAD_REQUEST:
                    try (InputStream inputStream = connection.getErrorStream()) {
                        String response = Streams.slurp(inputStream, "UTF-8");
                        if (Strings.isNullOrEmpty(response)) {
                            response = connection.getResponseMessage();
                        }
                        log.error("Unable to send React Native source map to New Relic: " + response);
                        logRecourse();
                    }
                    break;

                case HttpURLConnection.HTTP_CONFLICT:
                    log.info("A React Native source map with build ID [" + buildId + "] has already been stored.");
                    break;

                default:
                    if (responseCode > HttpURLConnection.HTTP_BAD_REQUEST) {
                        try (InputStream inputStream = connection.getErrorStream()) {
                            String response = Streams.slurp(inputStream, "UTF-8");
                            if (Strings.isNullOrEmpty(response)) {
                                response = connection.getResponseMessage();
                            }
                            log.error("Unable to send React Native source map to New Relic - received status " + responseCode + ": " + response);
                            logRecourse();
                        }
                    } else {
                        log.error("React Native source map upload returned [" + responseCode + "]");
                    }
                    break;
            }

        } catch (Exception e) {
            log.error("An error occurred uploading React Native source map to New Relic: " + e.getLocalizedMessage());
            logRecourse();

        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    HttpURLConnection getHttpURLConnection() throws IOException {
        String host = sourceMapApiHost;

        if (!host.startsWith("http")) {
            host = (sslConnection ? "https://" : "http://") + host;
        }

        final URL url = new URL(host + DEFAULT_SOURCEMAP_API_PATH);

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        connection.setUseCaches(false);
        connection.setDoOutput(true);
        connection.setRequestMethod("POST");

        // Set authentication headers
        connection.setRequestProperty(Network.APPLICATION_LICENSE_HEADER, licenseKey);
        connection.setRequestProperty(Network.API_KEY_HEADER, apiKey);

        return connection;
    }

    /**
     * Mask sensitive values for logging (show first 4 and last 4 characters).
     */
    private String maskSensitiveValue(String value) {
        if (value == null || value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
    }

    protected void logRecourse() {
        log.error("To symbolicate JavaScript errors, upload the build's source map manually,");
        log.error("or run the 'newrelicReactNativeSourceMapUpload<Variant>' Gradle task.");
        log.error("For more help, see 'https://docs.newrelic.com/docs/mobile-monitoring/new-relic-mobile/mobile-sdk/record-errors/'");
    }

    /**
     * Parse region from application token for regional endpoint support.
     *
     * @param applicationToken The New Relic application token
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

    /**
     * Multipart form writer for HTTP uploads.
     */
    static class MultipartFormWriter {
        final static String boundary = "----------------------" + System.currentTimeMillis();
        final static String newLn = "\r\n";

        final OutputStream os;
        final int bufferSz;

        void writeString(final String string) throws IOException {
            os.write(string.getBytes());
        }

        public MultipartFormWriter(OutputStream os, int bufferSz) {
            this.os = os;
            this.bufferSz = bufferSz;
        }

        void writeFilePart(final String partName, final String fileName, final InputStream is,
                           final boolean isZipped) throws IOException {
            writeString("--" + boundary + newLn);
            writeString("Content-Disposition: form-data; name=\"" + partName + "\"; filename=\"" + fileName + "\"" + newLn);
            if (isZipped) {
                writeString("Content-Type: application/zip" + newLn);
            } else {
                writeString("Content-Type: application/octet-stream" + newLn);
            }
            writeString(newLn);
            Streams.copy(is, os, bufferSz);
            writeString(newLn);  // newline after file content before next boundary
        }

        void writeFieldPart(final String fieldName, final String fieldValue) throws IOException {
            writeString("--" + boundary + newLn);
            writeString("Content-Disposition: form-data; name=\"" + fieldName + "\"" + newLn);
            writeString(newLn);
            writeString(fieldValue);
            writeString(newLn);
        }

        public void finish() throws IOException {
            writeString("--" + boundary + "--" + newLn);
        }
    }
}
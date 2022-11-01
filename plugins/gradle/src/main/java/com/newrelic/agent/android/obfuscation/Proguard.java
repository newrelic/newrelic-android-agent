/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.obfuscation;

import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import com.newrelic.agent.InstrumentationAgent;
import com.newrelic.agent.compile.Log;
import com.newrelic.agent.util.BuildId;
import com.newrelic.agent.util.Streams;

import org.gradle.internal.impldep.org.apache.commons.io.Charsets;
import org.gradle.internal.impldep.org.apache.commons.io.FileUtils;
import org.gradle.internal.impldep.org.apache.commons.io.filefilter.FileFilterUtils;
import org.gradle.internal.impldep.org.apache.commons.io.filefilter.IOFileFilter;
import org.gradle.internal.impldep.org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.Closeable;
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
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
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

    private static final String PROP_NR_APP_TOKEN = "com.newrelic.application_token";
    private static final String PROP_UPLOADING_ENABLED = "com.newrelic.enable_proguard_upload";
    private static final String PROP_MAPPING_API_HOST = "com.newrelic.mapping_upload_host";
    private static final String PROP_COMPRESSED_UPLOADS = "com.newrelic.compressed_uploads";
    private static final String PROP_SSL_CONNECTION = "com.newrelic.ssl_connection";
    private static final String DEFAULT_MAPPING_API_HOST = "mobile-symbol-upload.newrelic.com";
    private static final String DEFAULT_REGION_MAPPING_API_HOST = "mobile-symbol-upload.%s.nr-data.net";
    private static final String MAPPING_API_PATH = "/symbol";
    private static final String NR_MAP_PREFIX = "# NR_BUILD_ID -> ";
    private static final String NR_COMPILER_PREFIX = "# compiler: ";
    private static final String NR_COMPILER_VERSION_PREFIX = "# compiler_version: ";

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
            String header = NR_COMPILER_PREFIX + mapProvider + newLn;

            if (parts.length > 0) {
                header = NR_COMPILER_PREFIX + parts[0] + newLn;
                if (parts.length > 1) {
                    header += NR_COMPILER_VERSION_PREFIX + parts[1] + newLn;
                }
            }

            return header;
        }
    }

    private final Log log;

    private String projectRoot;
    private String licenseKey = null;
    private boolean uploadingEnabled = true;
    private String mappingApiHost = DEFAULT_MAPPING_API_HOST;
    private boolean compressedUploads = true;
    private boolean sslConnection = true;

    private static Map<String, String> agentOptions = Collections.emptyMap();
    private static String newLn = System.getProperty("line.separator", "\r\n");
    private static String buildId = BuildId.getBuildId(BuildId.DEFAULT_VARIANT);

    private Properties newRelicProps;

    public Proguard(final Log log, final Map<String, String> agentOptions) {
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
                    log.warning("Mapping file [" + mappingFile.getAbsolutePath() + "] doesn't exist");
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

    boolean shouldUploadMapFile(File file) throws IOException {

        if (file.exists()) {
            try (ReversedLinesFileReader revReader = new ReversedLinesFileReader(file)) {
                final String lastLine = revReader.readLine();
                revReader.close();

                if (lastLine == null || lastLine.isEmpty()) {
                    log.warning("Map [" + file.getAbsolutePath() + "] is empty!");
                } else if (lastLine.startsWith(NR_MAP_PREFIX)) {
                    // replace build id with parsed value
                    buildId = lastLine.substring(NR_MAP_PREFIX.length());
                    log.debug("Map [" + file.getAbsolutePath() + "] has already been tagged with buildID [" + buildId + "] - resending.");
                    return true;        // send it again
                } else {
                    String variant = agentOptions.get(VARIANT_KEY);
                    buildId = BuildId.getBuildId(variant);
                    log.info("Tagging map [" + file.getAbsolutePath() + "] with buildID [" + buildId + "]");
                    // Write the build ID to the file so the user can uploaded it manually later.
                    try (final FileWriter fileWriter = new FileWriter(file, true)) {
                        fileWriter.write(NR_MAP_PREFIX + buildId + newLn);
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
                    log.warning("Region prefix empty");
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

    static class ReversedLinesFileReader implements Closeable {
        private final int blockSize;
        private final Charset encoding;
        private final RandomAccessFile randomAccessFile;
        private final long totalByteLength;
        private final long totalBlockCount;
        private final byte[][] newLineSequences;
        private final int avoidNewlineSplitBufferSize;
        private final int byteDecrement;
        private ReversedLinesFileReader.FilePart currentFilePart;
        private boolean trailingNewlineOfFileSkipped;

        /**
         * @deprecated
         */
        @Deprecated
        public ReversedLinesFileReader(File file) throws IOException {
            this(file, 4096, (Charset) Charset.defaultCharset());
        }

        public ReversedLinesFileReader(File file, Charset charset) throws IOException {
            this(file, 4096, (Charset) charset);
        }

        public ReversedLinesFileReader(File file, int blockSize, Charset encoding) throws IOException {
            this.trailingNewlineOfFileSkipped = false;
            this.blockSize = blockSize;
            this.encoding = encoding;
            Charset charset = Charsets.toCharset(encoding);
            CharsetEncoder charsetEncoder = charset.newEncoder();
            float maxBytesPerChar = charsetEncoder.maxBytesPerChar();
            if (maxBytesPerChar == 1.0F) {
                this.byteDecrement = 1;
            } else if (charset == StandardCharsets.UTF_8) {
                this.byteDecrement = 1;
            } else if (charset != Charset.forName("Shift_JIS") && charset != Charset.forName("windows-31j") && charset != Charset.forName("x-windows-949") && charset != Charset.forName("gbk") && charset != Charset.forName("x-windows-950")) {
                if (charset != StandardCharsets.UTF_16BE && charset != StandardCharsets.UTF_16LE) {
                    if (charset == StandardCharsets.UTF_16) {
                        throw new UnsupportedEncodingException("For UTF-16, you need to specify the byte order (use UTF-16BE or UTF-16LE)");
                    }

                    throw new UnsupportedEncodingException("Encoding " + encoding + " is not supported yet (feel free to submit a patch)");
                }

                this.byteDecrement = 2;
            } else {
                this.byteDecrement = 1;
            }

            this.newLineSequences = new byte[][]{"\r\n".getBytes(encoding), "\n".getBytes(encoding), "\r".getBytes(encoding)};
            this.avoidNewlineSplitBufferSize = this.newLineSequences[0].length;
            this.randomAccessFile = new RandomAccessFile(file, "r");
            this.totalByteLength = this.randomAccessFile.length();
            int lastBlockLength = (int) (this.totalByteLength % (long) blockSize);
            if (lastBlockLength > 0) {
                this.totalBlockCount = this.totalByteLength / (long) blockSize + 1L;
            } else {
                this.totalBlockCount = this.totalByteLength / (long) blockSize;
                if (this.totalByteLength > 0L) {
                    lastBlockLength = blockSize;
                }
            }

            this.currentFilePart = new ReversedLinesFileReader.FilePart(this.totalBlockCount, lastBlockLength, (byte[]) null);
        }

        public ReversedLinesFileReader(File file, int blockSize, String encoding) throws IOException {
            this(file, blockSize, Charsets.toCharset(encoding));
        }

        public String readLine() throws IOException {
            String line;
            for (line = this.currentFilePart.readLine(); line == null; line = this.currentFilePart.readLine()) {
                this.currentFilePart = this.currentFilePart.rollOver();
                if (this.currentFilePart == null) {
                    break;
                }
            }

            if ("".equals(line) && !this.trailingNewlineOfFileSkipped) {
                this.trailingNewlineOfFileSkipped = true;
                line = this.readLine();
            }

            return line;
        }

        public void close() throws IOException {
            this.randomAccessFile.close();
        }

        private class FilePart {
            private final long no;
            private final byte[] data;
            private byte[] leftOver;
            private int currentLastBytePos;

            private FilePart(long no, int length, byte[] leftOverOfLastFilePart) throws IOException {
                this.no = no;
                int dataLength = length + (leftOverOfLastFilePart != null ? leftOverOfLastFilePart.length : 0);
                this.data = new byte[dataLength];
                long off = (no - 1L) * (long) ReversedLinesFileReader.this.blockSize;
                if (no > 0L) {
                    ReversedLinesFileReader.this.randomAccessFile.seek(off);
                    int countRead = ReversedLinesFileReader.this.randomAccessFile.read(this.data, 0, length);
                    if (countRead != length) {
                        throw new IllegalStateException("Count of requested bytes and actually read bytes don't match");
                    }
                }

                if (leftOverOfLastFilePart != null) {
                    System.arraycopy(leftOverOfLastFilePart, 0, this.data, length, leftOverOfLastFilePart.length);
                }

                this.currentLastBytePos = this.data.length - 1;
                this.leftOver = null;
            }

            private ReversedLinesFileReader.FilePart rollOver() throws IOException {
                if (this.currentLastBytePos > -1) {
                    throw new IllegalStateException("Current currentLastCharPos unexpectedly positive... last readLine() should have returned something! currentLastCharPos=" + this.currentLastBytePos);
                } else if (this.no > 1L) {
                    return ReversedLinesFileReader.this.new FilePart(this.no - 1L, ReversedLinesFileReader.this.blockSize, this.leftOver);
                } else if (this.leftOver != null) {
                    throw new IllegalStateException("Unexpected leftover of the last block: leftOverOfThisFilePart=" + new String(this.leftOver, ReversedLinesFileReader.this.encoding));
                } else {
                    return null;
                }
            }

            private String readLine() throws IOException {
                String line = null;
                boolean isLastFilePart = this.no == 1L;
                int i = this.currentLastBytePos;

                while (i > -1) {
                    if (!isLastFilePart && i < ReversedLinesFileReader.this.avoidNewlineSplitBufferSize) {
                        this.createLeftOver();
                        break;
                    }

                    int newLineMatchByteCount;
                    if ((newLineMatchByteCount = this.getNewLineMatchByteCount(this.data, i)) > 0) {
                        int lineStart = i + 1;
                        int lineLengthBytes = this.currentLastBytePos - lineStart + 1;
                        if (lineLengthBytes < 0) {
                            throw new IllegalStateException("Unexpected negative line length=" + lineLengthBytes);
                        }

                        byte[] lineData = new byte[lineLengthBytes];
                        System.arraycopy(this.data, lineStart, lineData, 0, lineLengthBytes);
                        line = new String(lineData, ReversedLinesFileReader.this.encoding);
                        this.currentLastBytePos = i - newLineMatchByteCount;
                        break;
                    }

                    i -= ReversedLinesFileReader.this.byteDecrement;
                    if (i < 0) {
                        this.createLeftOver();
                        break;
                    }
                }

                if (isLastFilePart && this.leftOver != null) {
                    line = new String(this.leftOver, ReversedLinesFileReader.this.encoding);
                    this.leftOver = null;
                }

                return line;
            }

            private void createLeftOver() {
                int lineLengthBytes = this.currentLastBytePos + 1;
                if (lineLengthBytes > 0) {
                    this.leftOver = new byte[lineLengthBytes];
                    System.arraycopy(this.data, 0, this.leftOver, 0, lineLengthBytes);
                } else {
                    this.leftOver = null;
                }

                this.currentLastBytePos = -1;
            }

            private int getNewLineMatchByteCount(byte[] data, int i) {
                byte[][] var3 = ReversedLinesFileReader.this.newLineSequences;
                int var4 = var3.length;

                for (int var5 = 0; var5 < var4; ++var5) {
                    byte[] newLineSequence = var3[var5];
                    boolean match = true;

                    for (int j = newLineSequence.length - 1; j >= 0; --j) {
                        int k = i + j - (newLineSequence.length - 1);
                        match &= k >= 0 && data[k] == newLineSequence[j];
                    }

                    if (match) {
                        return newLineSequence.length;
                    }
                }

                return 0;
            }
        }
    }

}

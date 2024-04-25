/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.obfuscation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.never;

import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import com.newrelic.agent.util.BuildId;
import com.newrelic.agent.util.Streams;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RunWith(JUnit4.class)
public class ProguardTest {

    static private String appToken = "<APP_TOKEN>";
    private static Logger logger = LoggerFactory.getLogger("newrelic");

    private Map<String, String> agentOptions;
    private TemporaryFolder projectRoot;
    private Proguard proguard;
    private String buildId = BuildId.getBuildId(BuildId.DEFAULT_VARIANT);

    private File mappingTxt;
    private File properties;
    private Properties props;

    @Before
    public void setUp() throws IOException {
        projectRoot = TemporaryFolder.builder().assureDeletion().build();
        projectRoot.create();

        mappingTxt = projectRoot.newFile(Proguard.MAPPING_FILENAME);
        Files.write(Streams.slurpBytes(getClass().getResourceAsStream("/mapping.txt")), mappingTxt);

        properties = projectRoot.newFile(Proguard.NR_PROPERTIES);
        Files.write(Streams.slurpBytes(getClass().getResourceAsStream("/newrelic.properties")), properties);

        props = new Properties();
        props.load(getClass().getResourceAsStream("/newrelic.properties"));
        props.put(Proguard.PROP_NR_APP_TOKEN, appToken);
        props.put(Proguard.MAPPING_FILE_KEY, mappingTxt.getAbsolutePath());
        props.put(Proguard.PROP_UPLOADING_ENABLED, "false");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        agentOptions = Mockito.spy(new HashMap<>());
        agentOptions.put(Proguard.PROJECT_ROOT_KEY, BaseEncoding.base64().encode(projectRoot.getRoot().getAbsolutePath().getBytes()));
        agentOptions.put(Proguard.MAPPING_FILE_KEY, mappingTxt.getAbsolutePath());
        agentOptions.put(Proguard.MAPPING_PROVIDER_KEY, Proguard.Provider.DEFAULT);
        agentOptions.put(Proguard.PROP_NR_APP_TOKEN, appToken);
        agentOptions.put(BuildId.BUILD_ID_KEY, buildId);

        proguard = Mockito.spy(new Proguard(logger, agentOptions));
    }

    @After
    public void tearDown() {
        mappingTxt.delete();
        properties.delete();
        projectRoot.delete();
    }

    @Test
    public void getProjectRoot() {
        Assert.assertEquals(projectRoot.getRoot().getAbsolutePath(), proguard.getProjectRoot());
    }

    @Test
    public void getNullProjectRoot() {
        proguard.projectRoot = null;

        agentOptions = Mockito.spy(new HashMap<>());
        agentOptions.put(Proguard.MAPPING_FILE_KEY, mappingTxt.getAbsolutePath());
        agentOptions.put(Proguard.MAPPING_PROVIDER_KEY, "R8");
        agentOptions.put(Proguard.PROP_NR_APP_TOKEN, appToken);
        agentOptions.put(BuildId.BUILD_ID_KEY, buildId);

        proguard = Mockito.spy(new Proguard(logger, agentOptions));

        Assert.assertEquals(System.getProperty("user.dir"), proguard.getProjectRoot());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testMapFileHasCorrectBuildId() {
        String buildId = BuildId.getBuildId(BuildId.DEFAULT_VARIANT);

        try {
            Assert.assertTrue(mappingTxt.exists());
            Assert.assertTrue(proguard.shouldUploadMapFile(mappingTxt));

            try (ReversedLinesFileReader reader = new ReversedLinesFileReader(mappingTxt)) {
                String line = reader.readLine();
                Assert.assertEquals(Proguard.NR_MAP_PREFIX + buildId, line);
            }

        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void fetchConfiguration() throws IOException {
        Assert.assertTrue(proguard.fetchConfiguration());

        props.clear();
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }
        Assert.assertFalse(proguard.fetchConfiguration());

        props.put(Proguard.PROP_NR_APP_TOKEN, appToken);
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }
        Assert.assertTrue(proguard.fetchConfiguration());
    }

    @Test
    public void fetchConfigurationWithEncryption() throws IOException {
        props.put(Proguard.PROP_SSL_CONNECTION, "false");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        Assert.assertTrue(proguard.fetchConfiguration());
        Assert.assertFalse(proguard.sslConnection);
        URL url = proguard.getHttpURLConnection().getURL();
        Assert.assertEquals("http", url.getProtocol());
    }

    @Test
    public void fetchConfigurationWithOverrides() throws IOException {
        props.clear();
        props.put(Proguard.PROP_NR_APP_TOKEN, appToken);
        props.put(Proguard.PROP_SSL_CONNECTION, "false");
        props.put(Proguard.PROP_MAPPING_API_HOST, "my.house.net");
        props.put(Proguard.PROP_MAPPING_API_PATH, "/maps/");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }
        Assert.assertTrue(proguard.fetchConfiguration());
        Assert.assertFalse(proguard.sslConnection);

        URL url = proguard.getHttpURLConnection().getURL();
        Assert.assertEquals("http://my.house.net/maps", url.toString());
        Assert.assertEquals("my.house.net", url.getAuthority());
        Assert.assertEquals("/maps", url.getPath());
    }

    @Test
    public void findAndSendMapFile() throws IOException {
        proguard.findAndSendMapFile();
        Mockito.verify(proguard, never()).sendMapping(mappingTxt);
    }

    @Test
    public void shouldUploadMapFile() throws IOException {
        Assert.assertTrue(mappingTxt.exists());

        // Tags a blank map with build ID
        Assert.assertTrue(proguard.shouldUploadMapFile(mappingTxt));

        // Already tagged so ready for upload
        Assert.assertTrue(proguard.shouldUploadMapFile(mappingTxt));

        mappingTxt.delete();
        Assert.assertFalse(proguard.shouldUploadMapFile(mappingTxt));
    }

    @Test
    public void getHttpUrlConnection() throws IOException {
        proguard.fetchConfiguration();
        URL url = proguard.getHttpURLConnection().getURL();
        Assert.assertEquals("https", url.getProtocol());
        Assert.assertEquals(proguard.mappingApiHost, url.getAuthority());
        Assert.assertEquals(proguard.mappingApiPath, url.getPath());
        Assert.assertEquals("https://mobile-symbol-upload.newrelic.com/symbol", url.toString());
    }

    @Test
    public void parseRegionFromApplicationToken() {
        Assert.assertNull(proguard.parseRegionFromApplicationToken("<APP-TOKEN>"));
        Assert.assertEquals("eu01", proguard.parseRegionFromApplicationToken("eu01xx544ebfee1f547c425d885ff1ddfc4e82acd2"));
    }

    @Test
    public void sendMappingUncompressed() throws IOException {
        props.put(Proguard.PROP_UPLOADING_ENABLED, "true");
        props.put(Proguard.PROP_COMPRESSED_UPLOADS, "false");
        props.put(Proguard.PROP_UPLOAD_POST_KEY, "=r8=");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        HttpURLConnection connection = Mockito.spy(proguard.getHttpURLConnection());
        Mockito.doReturn(HttpURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        Mockito.doNothing().when(connection).connect();
        Mockito.doNothing().when(connection).disconnect();
        Mockito.doReturn(connection).when(proguard).getHttpURLConnection();

        File osFile = new File(mappingTxt.getAbsolutePath() + ".out");
        try (FileOutputStream fos = new FileOutputStream(osFile)) {
            Mockito.doReturn(fos).when(connection).getOutputStream();

            proguard.fetchConfiguration();
            proguard.sendMapping(mappingTxt);

            Assert.assertTrue(connection.getRequestProperties().containsKey(Proguard.Network.ContentType.HEADER));
            Assert.assertEquals(Proguard.Network.ContentType.URL_ENCODED, connection.getRequestProperties().get(Proguard.Network.ContentType.HEADER).get(0));
            fos.close();

            String outBytes;
            try (FileInputStream fis = new FileInputStream(osFile)) {
                outBytes = Streams.slurp(fis, "UTF-8");
            }

            String postKey = props.getProperty(Proguard.PROP_UPLOAD_POST_KEY, "proguard")
                    .replace("/", "")
                    .replace("=", "");
            Assert.assertTrue(outBytes.startsWith(postKey + "="));
            Assert.assertTrue(outBytes.endsWith("&buildId=" + buildId));
        }
    }

    @Test
    public void sendMappingCompressed() throws IOException {
        props.put(Proguard.PROP_UPLOADING_ENABLED, "true");
        props.put(Proguard.PROP_COMPRESSED_UPLOADS, "true");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        HttpURLConnection connection = Mockito.spy(proguard.getHttpURLConnection());
        Mockito.doReturn(HttpURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        Mockito.doNothing().when(connection).connect();
        Mockito.doNothing().when(connection).disconnect();
        Mockito.doReturn(connection).when(proguard).getHttpURLConnection();

        File osFile = new File(mappingTxt.getAbsolutePath() + ".out");
        try (FileOutputStream fos = new FileOutputStream(osFile)) {
            Mockito.doReturn(fos).when(connection).getOutputStream();

            proguard.fetchConfiguration();
            proguard.sendMapping(mappingTxt);

            Assert.assertTrue(connection.getRequestProperties().containsKey(Proguard.Network.ContentType.HEADER));
            Assert.assertTrue(connection.getRequestProperties().get(Proguard.Network.ContentType.HEADER).get(0).startsWith(Proguard.Network.ContentType.MULTIPART_FORM_DATA));
            Assert.assertTrue(new File(mappingTxt.getAbsolutePath() + ".zip").exists());
            fos.close();

            String outBytes;
            try (FileInputStream fis = new FileInputStream(osFile)) {
                outBytes = Streams.slurp(fis, "UTF-8");
            }

            Assert.assertTrue(outBytes.endsWith("&buildId=" + buildId));
        }
    }

    @Test
    public void testCustomPostKey() throws IOException {
        props.put(Proguard.PROP_UPLOADING_ENABLED, "true");
        props.put(Proguard.PROP_COMPRESSED_UPLOADS, "false");
        props.put(Proguard.PROP_UPLOAD_POST_KEY, "=compiler/");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        HttpURLConnection connection = Mockito.spy(proguard.getHttpURLConnection());
        Mockito.doReturn(HttpURLConnection.HTTP_CREATED).when(connection).getResponseCode();
        Mockito.doNothing().when(connection).connect();
        Mockito.doNothing().when(connection).disconnect();
        Mockito.doReturn(connection).when(proguard).getHttpURLConnection();

        File osFile = new File(mappingTxt.getAbsolutePath() + ".out");
        try (FileOutputStream fos = new FileOutputStream(osFile)) {
            Mockito.doReturn(fos).when(connection).getOutputStream();

            proguard.fetchConfiguration();
            proguard.sendMapping(mappingTxt);

            fos.close();

            String outBytes;
            try (FileInputStream fis = new FileInputStream(osFile)) {
                outBytes = Streams.slurp(fis, "UTF-8");
            }

            Assert.assertTrue(outBytes.matches("^compiler=.*"));
            Assert.assertTrue(outBytes.endsWith("&buildId=" + buildId));
        }
    }

    @Test
    public void testDefaultEndpoint() throws IOException {
        proguard.fetchConfiguration();
        URL url = proguard.getHttpURLConnection().getURL();
        Assert.assertEquals("https", url.getProtocol());
        Assert.assertEquals(Proguard.DEFAULT_MAPPING_API_HOST, url.getAuthority());
        Assert.assertEquals(Proguard.DEFAULT_MAPPING_API_PATH, url.getPath());
        Assert.assertEquals("https://mobile-symbol-upload.newrelic.com/symbol", url.toString());
    }

    @Test
    public void testRegionEndpoints() throws IOException {
        props.put(Proguard.PROP_NR_APP_TOKEN, "eu01xx544ebfee1f547c425d885ff1ddfc4e82acd2");
        props.put(Proguard.PROP_MAPPING_API_PATH, "euSymbol");
        props.remove(Proguard.PROP_MAPPING_API_HOST);
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }
        proguard.fetchConfiguration();
        URL url = proguard.getHttpURLConnection().getURL();
        Assert.assertEquals("https", url.getProtocol());
        Assert.assertEquals(proguard.mappingApiHost, url.getAuthority());
        Assert.assertEquals(proguard.mappingApiPath, url.getPath());
        Assert.assertTrue(proguard.mappingApiHost.matches(Proguard.DEFAULT_REGION_MAPPING_API_HOST.replace("%s", ".*")));
        Assert.assertEquals("https://mobile-symbol-upload.eu01.nr-data.net/euSymbol", url.toString());
    }

    @Test
    public void testWithMissingMap() throws IOException {
        // test agentConf for missing entry, but finds map in directory tree
        props.remove(Proguard.MAPPING_FILE_KEY);
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        agentOptions.remove(Proguard.MAPPING_FILE_KEY);
        proguard.findAndSendMapFile();
        Mockito.verify(proguard, never()).logRecourse();

        // test with empty or missing map file
        mappingTxt.delete();
        proguard.findAndSendMapFile();
        Mockito.verify(proguard, atMostOnce()).logRecourse();
    }

    @Test
    public void recordMapUploadException() throws IOException {
        props.put(Proguard.PROP_UPLOADING_ENABLED, "true");
        props.remove(Proguard.MAPPING_FILE_KEY);
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        // test agentConf for missing entry
        agentOptions.remove(Proguard.MAPPING_FILE_KEY);
        mappingTxt.delete();

        Mockito.doThrow(new IOException()).when(proguard).sendMapping(any(File.class));
        Mockito.doReturn(true).when(proguard).shouldUploadMapFile(any(File.class));

        proguard.findAndSendMapFile();
        Mockito.verify(proguard, atMostOnce()).logRecourse();

    }

    @Test
    public void testMultiPartFileWriter() throws IOException {
        File osFile = new File(mappingTxt.getAbsolutePath() + ".out");
        try (FileInputStream fis = new FileInputStream(mappingTxt); FileOutputStream fos = new FileOutputStream(osFile)) {
            Proguard.MultipartFormWriter formWriter = new Proguard.MultipartFormWriter(fos, Proguard.USEFUL_BUFFER_SIZE);
            formWriter.writeFilePart("part1", mappingTxt, fis);
            formWriter.writeString("A");
            formWriter.writeString("B");
            formWriter.writeString("C");
        }

        String outBytes;
        try (FileInputStream fis = new FileInputStream(osFile)) {
            outBytes = Streams.slurp(fis, "UTF-8");
        }
        Assert.assertTrue(outBytes.contains("Content-Disposition: form-data; name=\"part1\"; filename=\"mapping.txt\""));
        Assert.assertTrue(outBytes.endsWith("ABC"));
    }

    @Test
    public void testProviderDecompose() {
        String header = Proguard.Provider.decompose("r8:1.2.3");
        Assert.assertEquals("# compiler: r8\r\n# compiler_version: 1.2.3\r\n", header);
    }
}
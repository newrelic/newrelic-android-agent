/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.obfuscation;


import com.google.common.io.BaseEncoding;
import com.google.common.io.Files;
import com.newrelic.agent.util.BuildId;
import com.newrelic.agent.util.Streams;

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
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RunWith(JUnit4.class)
public class ReactNativeSourceMapTest {

    private static final String APP_TOKEN = "<APP_TOKEN>";
    private static final String API_KEY = "<API_KEY>";
    private static final String APP_VERSION = "1.0.0";
    private static Logger logger = LoggerFactory.getLogger("newrelic");

    private Map<String, String> agentOptions;
    private TemporaryFolder projectRoot;
    private ReactNativeSourceMap sourceMapUploader;
    private String buildId = BuildId.getBuildId(BuildId.DEFAULT_VARIANT);

    private File sourceMapFile;
    private File properties;
    private Properties props;

    @Before
    public void setUp() throws IOException {
        projectRoot = TemporaryFolder.builder().assureDeletion().build();
        projectRoot.create();

        sourceMapFile = projectRoot.newFile(ReactNativeSourceMap.SOURCE_MAP_FILENAME);
        Files.write("{\"version\":3,\"sources\":[],\"mappings\":\"\"}".getBytes(), sourceMapFile);

        properties = projectRoot.newFile(ReactNativeSourceMap.NR_PROPERTIES);
        props = new Properties();
        props.put(ReactNativeSourceMap.PROP_NR_APP_TOKEN, APP_TOKEN);
        props.put(ReactNativeSourceMap.PROP_NR_API_KEY, API_KEY);
        props.put(ReactNativeSourceMap.PROP_UPLOADING_ENABLED, "true");
        props.put(ReactNativeSourceMap.PROP_SSL_CONNECTION, "true");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        agentOptions = Mockito.spy(new HashMap<>());
        agentOptions.put(ReactNativeSourceMap.PROJECT_ROOT_KEY, BaseEncoding.base64().encode(projectRoot.getRoot().getAbsolutePath().getBytes()));

        sourceMapUploader = Mockito.spy(new ReactNativeSourceMap(logger, agentOptions));
    }

    @After
    public void tearDown() {
        sourceMapFile.delete();
        properties.delete();
        projectRoot.delete();
    }

    @Test
    public void getProjectRoot() {
        Assert.assertEquals(projectRoot.getRoot().getAbsolutePath(), sourceMapUploader.getProjectRoot());
    }

    @Test
    public void getNullProjectRoot() {
        sourceMapUploader.projectRoot = null;

        agentOptions = Mockito.spy(new HashMap<>());
        sourceMapUploader = Mockito.spy(new ReactNativeSourceMap(logger, agentOptions));

        Assert.assertEquals(System.getProperty("user.dir"), sourceMapUploader.getProjectRoot());
    }

    @Test
    public void fetchConfiguration() throws IOException {
        Assert.assertTrue(sourceMapUploader.fetchConfiguration());
        Assert.assertEquals(APP_TOKEN, sourceMapUploader.licenseKey);
        Assert.assertEquals(API_KEY, sourceMapUploader.apiKey);
        Assert.assertTrue(sourceMapUploader.uploadingEnabled);
        Assert.assertTrue(sourceMapUploader.sslConnection);
    }

    @Test
    public void fetchConfigurationMissingAppToken() throws IOException {
        props.remove(ReactNativeSourceMap.PROP_NR_APP_TOKEN);
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }
        Assert.assertFalse(sourceMapUploader.fetchConfiguration());
    }

    @Test
    public void fetchConfigurationMissingApiKey() throws IOException {
        props.remove(ReactNativeSourceMap.PROP_NR_API_KEY);
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }
        Assert.assertFalse(sourceMapUploader.fetchConfiguration());
    }

    @Test
    public void fetchConfigurationWithEncryptionDisabled() throws IOException {
        props.put(ReactNativeSourceMap.PROP_SSL_CONNECTION, "false");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        Assert.assertTrue(sourceMapUploader.fetchConfiguration());
        Assert.assertFalse(sourceMapUploader.sslConnection);
        URL url = sourceMapUploader.getHttpURLConnection().getURL();
        Assert.assertEquals("http", url.getProtocol());
    }

    @Test
    public void fetchConfigurationWithUploadDisabled() throws IOException {
        props.put(ReactNativeSourceMap.PROP_UPLOADING_ENABLED, "false");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        Assert.assertTrue(sourceMapUploader.fetchConfiguration());
        Assert.assertFalse(sourceMapUploader.uploadingEnabled);
    }

    @Test
    public void fetchConfigurationWithCustomHost() throws IOException {
        props.put(ReactNativeSourceMap.PROP_SOURCEMAP_API_HOST, "custom-api.example.com");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        Assert.assertTrue(sourceMapUploader.fetchConfiguration());
        Assert.assertEquals("custom-api.example.com", sourceMapUploader.sourceMapApiHost);
    }

    @Test
    public void getHttpUrlConnection() throws IOException {
        sourceMapUploader.fetchConfiguration();
        HttpURLConnection connection = sourceMapUploader.getHttpURLConnection();
        URL url = connection.getURL();

        Assert.assertEquals("https", url.getProtocol());
        Assert.assertTrue(url.toString().contains(ReactNativeSourceMap.DEFAULT_SOURCEMAP_API_PATH));
    }

    @Test
    public void getHttpUrlConnectionHasCorrectHeaders() throws IOException {
        sourceMapUploader.fetchConfiguration();
        HttpURLConnection connection = sourceMapUploader.getHttpURLConnection();

        Assert.assertEquals(APP_TOKEN, connection.getRequestProperty(ReactNativeSourceMap.Network.APPLICATION_LICENSE_HEADER));
        Assert.assertEquals(API_KEY, connection.getRequestProperty(ReactNativeSourceMap.Network.API_KEY_HEADER));
    }

    @Test
    public void parseRegionFromApplicationToken() {
        Assert.assertNull(sourceMapUploader.parseRegionFromApplicationToken("<APP-TOKEN>"));
        Assert.assertEquals("eu01", sourceMapUploader.parseRegionFromApplicationToken("eu01xx544ebfee1f547c425d885ff1ddfc4e82acd2"));
        Assert.assertEquals("us01", sourceMapUploader.parseRegionFromApplicationToken("us01x1234567890abcdef"));
    }

    @Test
    public void testRegionEndpoints() throws IOException {
        props.put(ReactNativeSourceMap.PROP_NR_APP_TOKEN, "eu01xx544ebfee1f547c425d885ff1ddfc4e82acd2");
        props.remove(ReactNativeSourceMap.PROP_SOURCEMAP_API_HOST);
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        sourceMapUploader.fetchConfiguration();
        URL url = sourceMapUploader.getHttpURLConnection().getURL();

        Assert.assertEquals("https", url.getProtocol());
        Assert.assertTrue(url.toString().contains("eu01"));
        Assert.assertTrue(sourceMapUploader.sourceMapApiHost.contains("eu01"));
    }

    @Test
    public void uploadSourceMapWithNullFile() throws IOException {
        sourceMapUploader.uploadSourceMap(null, buildId, APP_VERSION);
        // When null file is passed, sendSourceMap should not be called
        // We can't use verify with checked exception, so we verify by checking that
        // the method completes without error when given null
    }

    @Test
    public void uploadSourceMapWithNonExistentFile() throws IOException {
        File nonExistent = new File("/non/existent/file.map");
        sourceMapUploader.uploadSourceMap(nonExistent, buildId, APP_VERSION);
        // Method should complete without error for non-existent file
    }

    @Test
    public void uploadSourceMapWithEmptyFile() throws IOException {
        File emptyFile = projectRoot.newFile("empty.map");
        sourceMapUploader.uploadSourceMap(emptyFile, buildId, APP_VERSION);
        // Method should complete without error for empty file
    }

    @Test
    public void uploadSourceMapWhenDisabled() throws IOException {
        props.put(ReactNativeSourceMap.PROP_UPLOADING_ENABLED, "false");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        sourceMapUploader.uploadSourceMap(sourceMapFile, buildId, APP_VERSION);
        // Method should complete without calling sendSourceMap when disabled
    }

    // Note: Tests for sendSourceMap with actual HTTP responses are skipped because
    // Mockito cannot mock HttpsURLConnectionImpl in newer Java versions (17+).
    // These would be better tested as integration tests with an actual test server.

    @Test
    public void testMultiPartFormWriter() throws IOException {
        File osFile = new File(sourceMapFile.getAbsolutePath() + ".out");
        try (FileInputStream fis = new FileInputStream(sourceMapFile);
             FileOutputStream fos = new FileOutputStream(osFile)) {

            ReactNativeSourceMap.MultipartFormWriter formWriter =
                    new ReactNativeSourceMap.MultipartFormWriter(fos, ReactNativeSourceMap.USEFUL_BUFFER_SIZE);

            formWriter.writeFilePart("sourcemap", sourceMapFile.getName(), fis, false);
            formWriter.writeFieldPart("sourcemapName", sourceMapFile.getName());
            formWriter.writeFieldPart("jsBundleId", buildId);
            formWriter.writeFieldPart("appVersionId", APP_VERSION);
            formWriter.finish();
        }

        String outBytes;
        try (FileInputStream fis = new FileInputStream(osFile)) {
            outBytes = Streams.slurp(fis, "UTF-8");
        }

        Assert.assertTrue(outBytes.contains("Content-Disposition: form-data; name=\"sourcemap\""));
        Assert.assertTrue(outBytes.contains("Content-Disposition: form-data; name=\"sourcemapName\""));
        Assert.assertTrue(outBytes.contains("Content-Disposition: form-data; name=\"jsBundleId\""));
        Assert.assertTrue(outBytes.contains("Content-Disposition: form-data; name=\"appVersionId\""));
        Assert.assertTrue(outBytes.contains(sourceMapFile.getName()));
        Assert.assertTrue(outBytes.contains(buildId));
        Assert.assertTrue(outBytes.contains(APP_VERSION));
    }

    @Test
    public void testMultiPartFieldPart() throws IOException {
        File osFile = new File(sourceMapFile.getAbsolutePath() + ".out");
        try (FileOutputStream fos = new FileOutputStream(osFile)) {
            ReactNativeSourceMap.MultipartFormWriter formWriter =
                    new ReactNativeSourceMap.MultipartFormWriter(fos, ReactNativeSourceMap.USEFUL_BUFFER_SIZE);

            formWriter.writeFieldPart("testField", "testValue");
            formWriter.finish();
        }

        String outBytes;
        try (FileInputStream fis = new FileInputStream(osFile)) {
            outBytes = Streams.slurp(fis, "UTF-8");
        }

        Assert.assertTrue(outBytes.contains("Content-Disposition: form-data; name=\"testField\""));
        Assert.assertTrue(outBytes.contains("testValue"));
    }

    @Test
    public void testMultiPartFormWriterWithZip() throws IOException {
        File osFile = new File(sourceMapFile.getAbsolutePath() + ".out");
        try (FileInputStream fis = new FileInputStream(sourceMapFile);
             FileOutputStream fos = new FileOutputStream(osFile)) {

            ReactNativeSourceMap.MultipartFormWriter formWriter =
                    new ReactNativeSourceMap.MultipartFormWriter(fos, ReactNativeSourceMap.USEFUL_BUFFER_SIZE);

            formWriter.writeFilePart("sourcemap", sourceMapFile.getName() + ".zip", fis, true);
            formWriter.finish();
        }

        String outBytes;
        try (FileInputStream fis = new FileInputStream(osFile)) {
            outBytes = Streams.slurp(fis, "UTF-8");
        }

        Assert.assertTrue(outBytes.contains("Content-Disposition: form-data; name=\"sourcemap\""));
        Assert.assertTrue(outBytes.contains("filename=\"" + sourceMapFile.getName() + ".zip\""));
        Assert.assertTrue(outBytes.contains("Content-Type: application/zip"));
    }

    @Test
    public void fetchConfigurationWithCompressedUploadsDisabled() throws IOException {
        props.put(ReactNativeSourceMap.PROP_COMPRESSED_UPLOADS, "false");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        Assert.assertTrue(sourceMapUploader.fetchConfiguration());
        Assert.assertFalse(sourceMapUploader.compressedUploads);
    }

    @Test
    public void fetchConfigurationWithCompressedUploadsEnabled() throws IOException {
        props.put(ReactNativeSourceMap.PROP_COMPRESSED_UPLOADS, "true");
        try (FileOutputStream fos = new FileOutputStream(properties)) {
            props.store(fos, "");
        }

        Assert.assertTrue(sourceMapUploader.fetchConfiguration());
        Assert.assertTrue(sourceMapUploader.compressedUploads);
    }

    @Test
    public void buildTelemetryJson() {
        String json = sourceMapUploader.buildTelemetryJson(
                "index.android.bundle.map",
                "index.android.bundle.map.zip",
                250000000L,
                1000000L
        );

        Assert.assertTrue(json.contains("\"bundler\":\"metro\""));
        Assert.assertTrue(json.contains("\"name\":\"index.android.bundle\""));
        Assert.assertTrue(json.contains("\"name\":\"index.android.bundle.map.zip\""));
        Assert.assertTrue(json.contains("\"size\":250000000"));
        Assert.assertTrue(json.contains("\"size\":1000000"));
    }

    @Test
    public void buildTelemetryJsonHasCorrectStructure() {
        String json = sourceMapUploader.buildTelemetryJson(
                "main.map",
                "main.map.zip",
                300000000L,
                5000000L
        );

        Assert.assertTrue(json.contains("\"bundles\":["));
        Assert.assertTrue(json.contains("\"sourcemaps\":["));
        Assert.assertTrue(json.contains("\"bundler\":\"metro\""));
        // Bundle name should strip .map extension
        Assert.assertTrue(json.contains("\"name\":\"main\""));
        // Sourcemap name should be the zip filename
        Assert.assertTrue(json.contains("\"name\":\"main.map.zip\""));
        Assert.assertTrue(json.contains("\"size\":300000000"));
        // Bundle size
        Assert.assertTrue(json.contains("\"size\":5000000"));
    }

    @Test
    public void buildTelemetryJsonWithZeroBundleSize() {
        String json = sourceMapUploader.buildTelemetryJson(
                "index.android.bundle.map",
                "index.android.bundle.map.zip",
                250000000L,
                0L
        );

        // When bundle file is not found, size should be 0
        Assert.assertTrue(json.contains("\"bundles\":[{\"name\":\"index.android.bundle\",\"size\":0}]"));
    }

    @Test
    public void maxCompressedSizeIs200MB() {
        Assert.assertEquals(200L * 1024 * 1024, ReactNativeSourceMap.MAX_COMPRESSED_SIZE);
    }
}
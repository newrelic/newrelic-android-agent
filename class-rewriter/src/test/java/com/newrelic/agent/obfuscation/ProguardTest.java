package com.newrelic.agent.obfuscation;

import com.google.common.io.BaseEncoding;
import com.newrelic.agent.compile.Log;
import com.newrelic.agent.util.BuildId;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ProguardTest {

    static private String projectRoot = ".";
    static private String appToken = "<APP_TOKEN>";
    static private String fileName = Proguard.MAPPING_FILENAME;
    private Map<String, String> agentOptions;

    @Before
    public void setUp() {
        agentOptions = new HashMap<>();
        agentOptions.put(Proguard.PROJECT_ROOT_KEY, BaseEncoding.base64().encode(projectRoot.getBytes()));
        agentOptions.put("com.newrelic.application_token", appToken);
    }

    @Test
    public void testGetProjectRoot() {
        Proguard p = new Proguard(Log.LOGGER, agentOptions);
        assertEquals(projectRoot, p.getProjectRoot());

    }

    @Test
    public void testMapFileHasCorrectBuildId() {

        String buildId = BuildId.getBuildId(BuildId.DEFAULT_VARIANT);

        Proguard p = new Proguard(Log.LOGGER, agentOptions);

        File file = new File(fileName);
        try {
            assertTrue(file.createNewFile());
            FileWriter writer = new FileWriter(file);
            writer.write("hello, world\n");
            writer.close();

            p.shouldUploadMapFile(file);

            ReversedLinesFileReader reader = new ReversedLinesFileReader(file);

            String line = reader.readLine();
            assertEquals("# NR_BUILD_ID -> " + buildId, line);
            reader.close();
        } catch (IOException e) {
            fail(e.getMessage());
        } finally {
            assertTrue(file.delete());
        }
    }

    @After
    public void tearDown() {
    }
}
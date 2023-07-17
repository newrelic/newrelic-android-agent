/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.obfuscation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.io.BaseEncoding;
import com.newrelic.agent.util.BuildId;

import org.apache.commons.io.input.ReversedLinesFileReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RunWith(JUnit4.class)
public class ProguardTest {

    static private String projectRoot = ".";
    static private String appToken = "<APP_TOKEN>";
    static private String fileName = Proguard.MAPPING_FILENAME;
    private static Logger logger = LoggerFactory.getLogger("newrelic");

    private Map<String, String> agentOptions;

    @Before
    public void setUp() {
        agentOptions = new HashMap<>();
        agentOptions.put(Proguard.PROJECT_ROOT_KEY, BaseEncoding.base64().encode(projectRoot.getBytes()));
        agentOptions.put("com.newrelic.application_token", appToken);
    }

    @Test
    public void testGetProjectRoot() {
        Proguard p = new Proguard(logger, agentOptions);
        assertEquals(projectRoot, p.getProjectRoot());

    }

    @SuppressWarnings("deprecation")
    @Test
    public void testMapFileHasCorrectBuildId() {

        String buildId = BuildId.getBuildId(BuildId.DEFAULT_VARIANT);
        Proguard p = new Proguard(logger, agentOptions);
        File file = new File(fileName);

        try {
            assertTrue(file.createNewFile());
            FileWriter writer = new FileWriter(file);
            writer.write("hello, world\n");
            writer.close();

            p.shouldUploadMapFile(file);

            try (ReversedLinesFileReader reader = new ReversedLinesFileReader(file)) {
                String line = reader.readLine();
                assertEquals(Proguard.NR_MAP_PREFIX + buildId, line);
            }

        } catch (IOException e) {
            fail(e.getMessage());
        } finally {
            assertTrue(file.delete());
        }
    }

}
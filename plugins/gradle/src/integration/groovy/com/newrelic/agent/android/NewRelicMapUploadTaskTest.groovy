/*
 * Copyright (c) 2023. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.junit.Assert
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class NewRelicMapUploadTaskTest extends PluginTest {
    def provider

    NewRelicMapUploadTaskTest() {
        super(true);
    }

    @BeforeEach
    void setup() {
        provider = plugin.buildHelper.variantAdapter.getMapUploadProvider("release").get()
    }

    @Test
    void getVariantName() {
        Assert.assertEquals("release", provider.getVariantName().get())
    }

    @Test
    void getProjectRoot() {
        Assert.assertEquals(project.layout.projectDirectory, provider.getProjectRoot().get())
    }

    @Test
    void getBuildId() {
        def buildId = provider.getBuildId().get()
        Assert.assertFalse(buildId.isEmpty())
        Assert.assertFalse(UUID.fromString(buildId).toString().isEmpty())
    }

    @Test
    void getMapProvider() {
        Assert.assertEquals("r8", provider.getMapProvider().get().toLowerCase())
    }

    @Test
    void getMappingFile() {
        def f = provider.getMappingFile().asFile
        Assert.assertFalse(f.get().absolutePath.isEmpty())
        def m = plugin.buildHelper.variantAdapter.getMappingFileProvider("release")
        Assert.assertEquals(f.get(), m.get().asFile)
    }

    @Test
    void getLogger() {
        Assert.assertEquals(provider.getLogger(), NewRelicGradlePlugin.LOGGER)
    }

    @Test
    void getTaggedMappingFile() {
        def taggedFile = provider.getTaggedMappingFile()
        def expectedPath = project.layout.buildDirectory.file("outputs/newrelic/release/mapping.txt").get().asFile

        Assert.assertEquals(expectedPath.absolutePath, taggedFile.absolutePath)
        Assert.assertTrue("Tagged file path should contain variant name",
                          taggedFile.absolutePath.contains("release"))
        Assert.assertTrue("Tagged file should be separate from input",
                          taggedFile != provider.getMappingFile().asFile.get())
    }

    @Test
    void getTaggedMappingFileWithCompoundVariantName() {
        // Test compound variant names (flavor + build type) like "googleQa", "amazonRelease", etc.
        def compoundProvider = plugin.buildHelper.variantAdapter.registerOrNamed(
                "testMapUploadGoogleQa", NewRelicMapUploadTask) { task ->
            task.variantName.set("googleQa")
            task.buildId.set("test-build-id")
            task.mapProvider.set("r8")
            task.projectRoot.set(project.layout.projectDirectory)
        }.get()

        def taggedFile = compoundProvider.getTaggedMappingFile()
        def expectedPath = project.layout.buildDirectory.file("outputs/newrelic/googleQa/mapping.txt").get().asFile

        Assert.assertEquals("Compound variant names should work correctly",
                          expectedPath.absolutePath, taggedFile.absolutePath)
        Assert.assertTrue("Tagged file path should contain compound variant name",
                          taggedFile.absolutePath.contains("googleQa"))
        Assert.assertTrue("Output path should be isolated per variant",
                          !taggedFile.absolutePath.contains("release"))
    }

    @Test
    void taskExecutionConditionsWithRegeneratedInput() {
        // Test the critical scenario: existing tagged output + regenerated input should trigger execution
        def testProvider = plugin.buildHelper.variantAdapter.registerOrNamed(
                "testMapUploadFreshness", NewRelicMapUploadTask) { task ->
            task.variantName.set("testVariant")
            task.buildId.set("test-build-id-123")
            task.mapProvider.set("r8")
            task.projectRoot.set(project.layout.projectDirectory)
        }.get()

        // Create mock input mapping file
        def mockInputFile = new File(project.buildDir, "outputs/mapping/testVariant/mapping.txt")
        mockInputFile.parentFile.mkdirs()
        mockInputFile.text = """
# Original mapping content
com.example.Test -> a:
    void method() -> a
"""

        // Create mock tagged output with correct tag (simulates previous run)
        def taggedFile = testProvider.getTaggedMappingFile()
        taggedFile.parentFile.mkdirs()
        taggedFile.text = """
# Original mapping content
com.example.Test -> a:
    void method() -> a

# NR_MAP_PREFIX=test-build-id-123
"""

        // Initially, tagged output is newer (task should be up-to-date)
        Thread.sleep(100) // Ensure time difference
        def initialInputTime = mockInputFile.lastModified()
        def initialTaggedTime = taggedFile.lastModified()

        Assert.assertTrue("Initially tagged file should be newer",
                         initialTaggedTime >= initialInputTime)

        // Now simulate input regeneration (R8/ProGuard runs again)
        Thread.sleep(100) // Ensure time difference
        mockInputFile.text = """
# Regenerated mapping content (different obfuscation)
com.example.Test -> b:
    void method() -> b
"""

        def regeneratedInputTime = mockInputFile.lastModified()
        Assert.assertTrue("Input should now be newer after regeneration",
                         regeneratedInputTime > initialTaggedTime)

        // Test onlyIf condition - should return true because input is newer
        testProvider.configure { task ->
            task.mappingFile.set(mockInputFile)
        }

        // Simulate the onlyIf logic
        def tag = "# NR_MAP_PREFIX=test-build-id-123"
        def inputExists = mockInputFile.exists()
        def taggedExists = taggedFile.exists()
        def taggedContainsTag = taggedExists && taggedFile.text.contains(tag)
        def inputNewer = inputExists && taggedExists && (mockInputFile.lastModified() > taggedFile.lastModified())
        def shouldExecute = inputExists && (!taggedContainsTag || inputNewer)

        Assert.assertTrue("Input file should exist", inputExists)
        Assert.assertTrue("Tagged file should exist", taggedExists)
        Assert.assertTrue("Tagged file should contain tag", taggedContainsTag)
        Assert.assertTrue("Input should be newer than tagged output", inputNewer)
        Assert.assertTrue("Task should execute because input is newer", shouldExecute)

        // Test upToDateWhen condition - should return false because input is newer
        def isUpToDate = inputExists && taggedExists && taggedContainsTag && !inputNewer
        Assert.assertFalse("Task should NOT be up-to-date when input is newer", isUpToDate)
    }
}
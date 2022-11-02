package com.newrelic.agent.util;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.UUID;

public class BuildIdTest {

    @Before
    public void setUp() throws Exception {
        BuildId.invalidate();
        BuildId.setVariantMapsEnabled(true);
    }

    @Test
    public void invalidate() {
        String oldBuildId = BuildId.getBuildId("variant");
        Assert.assertNotNull(oldBuildId);

        BuildId.invalidate();

        String newBuildId = BuildId.getBuildId("variant");
        Assert.assertNotNull(oldBuildId);
        Assert.assertNotEquals(oldBuildId, newBuildId);
    }

    @Test
    public void getDefaultBuildId() {
        String buildId = BuildId.getDefaultBuildId();
        Assert.assertNotNull(buildId);
        Assert.assertEquals(buildId, BuildId.getBuildId(BuildId.DEFAULT_VARIANT));
    }

    @Test
    public void getBuildId() {
        String buildId = BuildId.getBuildId("test");
        Assert.assertNotNull(buildId);
    }

    @Test
    public void getVariantBuildIds() {
        Map<String, String> buildIds = BuildId.getVariantBuildIds();
        Assert.assertNotNull(buildIds);
        Assert.assertNotNull(BuildId.getBuildId(BuildId.DEFAULT_VARIANT));
        Assert.assertNotNull(BuildId.getDefaultBuildId());

        Assert.assertNotNull(BuildId.getBuildId("ricky"));
        Assert.assertNotNull(BuildId.getBuildId("julien"));
        Assert.assertNotNull(BuildId.getBuildId("bubbles"));

        buildIds = BuildId.getVariantBuildIds();
        Assert.assertEquals(4, buildIds.size());
    }

    @Test
    public void autoBuildId() {
        String buildId = BuildId.autoBuildId();
        Assert.assertEquals(buildId, UUID.fromString(buildId).toString());
    }

    @Test
    public void disableVariantIds() {
        String buildId = BuildId.getDefaultBuildId();
        String variantBuildId = BuildId.getBuildId("variant");
        Assert.assertNotEquals(buildId, variantBuildId);

        BuildId.invalidate();
        BuildId.setVariantMapsEnabled(false);
        buildId = BuildId.getDefaultBuildId();
        variantBuildId = BuildId.getBuildId("variant");
        Assert.assertEquals(buildId, variantBuildId);
    }
}
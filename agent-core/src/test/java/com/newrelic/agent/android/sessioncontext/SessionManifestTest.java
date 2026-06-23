/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */
package com.newrelic.agent.android.sessioncontext;

import com.google.gson.JsonObject;
import com.newrelic.agent.android.analytics.AnalyticsAttribute;
import com.newrelic.agent.android.logging.AgentLogManager;
import com.newrelic.agent.android.logging.ConsoleAgentLog;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SessionManifestTest {

    @BeforeClass
    public static void beforeClass() {
        AgentLogManager.setAgentLog(new ConsoleAgentLog());
    }

    private static Map<String, AnalyticsAttribute> byName(Set<AnalyticsAttribute> attrs) {
        Map<String, AnalyticsAttribute> m = new HashMap<>();
        for (AnalyticsAttribute a : attrs) {
            m.put(a.getName(), a);
        }
        return m;
    }

    @Test
    public void roundTripsAllAttributeTypes() {
        Set<AnalyticsAttribute> attrs = new HashSet<>();
        attrs.add(new AnalyticsAttribute("checkout_step", "review"));
        attrs.add(new AnalyticsAttribute("cartSize", 3.0));
        attrs.add(new AnalyticsAttribute("premium", true));

        SessionManifest original = new SessionManifest("S_A", 1234, 1000L, 2000L, attrs);
        JsonObject json = original.toJson();
        SessionManifest restored = SessionManifest.fromJson(json);

        Assert.assertEquals("S_A", restored.getSessionId());
        Assert.assertEquals(1234, restored.getRealAgentId());
        Assert.assertEquals(1000L, restored.getSessionStartMs());
        Assert.assertEquals(2000L, restored.getLastUpdateMs());

        Map<String, AnalyticsAttribute> restoredAttrs = byName(restored.getAttributes());
        Assert.assertEquals(3, restoredAttrs.size());
        Assert.assertEquals("review", restoredAttrs.get("checkout_step").getStringValue());
        Assert.assertEquals(3.0, restoredAttrs.get("cartSize").getDoubleValue(), 0.0);
        Assert.assertTrue(restoredAttrs.get("premium").getBooleanValue());
    }

    @Test
    public void legacyJsonWithoutOptionalFieldsDeserializesToDefaults() {
        JsonObject legacy = new JsonObject();
        legacy.addProperty("sessionId", "S_legacy");
        legacy.addProperty("realAgentId", 7);

        SessionManifest restored = SessionManifest.fromJson(legacy);

        Assert.assertEquals("S_legacy", restored.getSessionId());
        Assert.assertEquals(7, restored.getRealAgentId());
        Assert.assertEquals(0L, restored.getSessionStartMs());
        Assert.assertEquals(0L, restored.getLastUpdateMs());
        Assert.assertTrue(restored.getAttributes().isEmpty());
    }
}
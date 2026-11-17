/*
 * Copyright (c) 2022-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android.mobileview;

import com.newrelic.agent.android.FeatureFlag;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MobileViewTimingTests {
    private final MobileViewTiming timing = MobileViewTiming.getInstance();

    @Before
    public void setUp() {
        timing.clear();
    }

    @After
    public void tearDown() {
        timing.clear();
        FeatureFlag.resetFeatures();
    }

    @Test
    public void enabledWhenEitherMobileViewsFlagIsOn() {
        FeatureFlag.disableFeature(FeatureFlag.AutomaticMobileViewTracing);
        FeatureFlag.disableFeature(FeatureFlag.ManualMobileViewTracing);
        Assert.assertFalse("Both flags off should disable timing.", timing.isEnabled());

        FeatureFlag.enableFeature(FeatureFlag.AutomaticMobileViewTracing);
        Assert.assertTrue(timing.isEnabled());

        FeatureFlag.disableFeature(FeatureFlag.AutomaticMobileViewTracing);
        FeatureFlag.enableFeature(FeatureFlag.ManualMobileViewTracing);
        Assert.assertTrue(timing.isEnabled());
    }

    @Test
    public void admitsAValidCustomerTiming() {
        Assert.assertTrue(timing.admit("timeToFullDisplay", 900.0, "instance-1", false));
        Assert.assertTrue("Zero is a valid duration.", timing.admit("loadMore", 0.0, "instance-1", false));
        Assert.assertTrue("The limit itself is valid.", timing.admit("loadMore", MobileViewTiming.MAX_MILLISECONDS, "instance-1", false));
    }

    @Test
    public void rejectsEmptyOrNullName() {
        Assert.assertFalse(timing.admit(null, 1.0, "instance-1", false));
        Assert.assertFalse(timing.admit("", 1.0, "instance-1", false));
        Assert.assertFalse("Agent-owned timings need a name too.", timing.admit("", 1.0, "instance-1", true));
    }

    @Test
    public void rejectsNamesLongerThanTheLimit() {
        Assert.assertTrue(timing.admit(repeat('a', MobileViewTiming.MAX_NAME_LENGTH), 1.0, "instance-1", false));
        Assert.assertFalse(timing.admit(repeat('a', MobileViewTiming.MAX_NAME_LENGTH + 1), 1.0, "instance-1", false));
    }

    @Test
    public void reservedNameIsRejectedForCustomersButAcceptedForTheAgent() {
        Assert.assertFalse(timing.admit(MobileViewTiming.TIME_TO_INITIAL_DISPLAY, 1.0, "instance-1", false));
        Assert.assertTrue(timing.admit(MobileViewTiming.TIME_TO_INITIAL_DISPLAY, 1.0, "instance-1", true));
    }

    @Test
    public void rejectsNonFiniteNegativeAndOversizedDurations() {
        Assert.assertFalse(timing.admit("t", Double.NaN, "instance-1", false));
        Assert.assertFalse(timing.admit("t", Double.POSITIVE_INFINITY, "instance-1", false));
        Assert.assertFalse(timing.admit("t", Double.NEGATIVE_INFINITY, "instance-1", false));
        Assert.assertFalse(timing.admit("t", -0.001, "instance-1", false));
        Assert.assertFalse(timing.admit("t", MobileViewTiming.MAX_MILLISECONDS + 0.001, "instance-1", false));
        Assert.assertFalse("The range applies to the agent's own timing too.",
                timing.admit(MobileViewTiming.TIME_TO_INITIAL_DISPLAY, MobileViewTiming.MAX_MILLISECONDS + 1, "instance-1", true));
    }

    @Test
    public void rejectedTimingsDoNotUseUpTheCap() {
        for (int i = 0; i < 100; i++) {
            timing.admit("t", Double.NaN, "instance-1", false);
        }
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE; i++) {
            Assert.assertTrue("Timing " + i + " should be admitted.", timing.admit("t", 1.0, "instance-1", false));
        }
    }

    @Test
    public void capsCustomerTimingsPerViewInstance() {
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE; i++) {
            Assert.assertTrue(timing.admit("t", 1.0, "instance-1", false));
        }
        Assert.assertFalse("The 17th timing should be rejected.", timing.admit("t", 1.0, "instance-1", false));
        Assert.assertFalse(timing.admit("t", 1.0, "instance-1", false));

        Assert.assertTrue("Another view instance has its own bucket.", timing.admit("t", 1.0, "instance-2", false));
    }

    @Test
    public void agentOwnedTimingsAreNotCappedAndDoNotCount() {
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE + 5; i++) {
            Assert.assertTrue(timing.admit(MobileViewTiming.TIME_TO_INITIAL_DISPLAY, 1.0, "instance-1", true));
        }
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE; i++) {
            Assert.assertTrue("Customer timings keep their full budget.", timing.admit("t", 1.0, "instance-1", false));
        }

        // And a full customer bucket does not block the baseline.
        Assert.assertFalse(timing.admit("t", 1.0, "instance-1", false));
        Assert.assertTrue(timing.admit(MobileViewTiming.TIME_TO_INITIAL_DISPLAY, 1.0, "instance-1", true));
    }

    @Test
    public void timingsWithNoViewShareOneBucket() {
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE / 2; i++) {
            Assert.assertTrue(timing.admit("t", 1.0, null, false));
        }
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE / 2; i++) {
            Assert.assertTrue(timing.admit("t", 1.0, "", false));
        }
        Assert.assertFalse("Null and empty identity should both count against the shared bucket.",
                timing.admit("t", 1.0, null, false));
    }

    @Test
    public void sessionResetEmptiesOnlyTheUnattributedBucket() {
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE; i++) {
            timing.admit("t", 1.0, null, false);
            timing.admit("t", 1.0, "instance-1", false);
        }
        Assert.assertFalse(timing.admit("t", 1.0, null, false));

        timing.resetUnattributedCapForNewSession();

        Assert.assertTrue("The unattributed bucket should be empty again.", timing.admit("t", 1.0, null, false));
        Assert.assertFalse("A view instance's bucket is untouched by the reset.", timing.admit("t", 1.0, "instance-1", false));
    }

    @Test
    public void evictsOldestViewBucketBeyondTheTrackingLimit() {
        // Fill the oldest bucket completely, then push it out by tracking MAX_TRACKED_BUCKETS newer ones.
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE; i++) {
            timing.admit("t", 1.0, "oldest", false);
        }
        Assert.assertFalse(timing.admit("t", 1.0, "oldest", false));

        for (int i = 0; i < MobileViewTiming.MAX_TRACKED_BUCKETS; i++) {
            timing.admit("t", 1.0, "newer-" + i, false);
        }

        Assert.assertTrue("The evicted bucket starts over: it belongs to a view long since left.",
                timing.admit("t", 1.0, "oldest", false));
    }

    @Test
    public void unattributedBucketIsNeverEvicted() {
        for (int i = 0; i < MobileViewTiming.MAX_TIMINGS_PER_VIEW_INSTANCE; i++) {
            timing.admit("t", 1.0, null, false);
        }

        for (int i = 0; i < MobileViewTiming.MAX_TRACKED_BUCKETS * 2; i++) {
            timing.admit("t", 1.0, "view-" + i, false);
        }

        Assert.assertFalse("The shared bucket must survive view-bucket churn.", timing.admit("t", 1.0, null, false));
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}

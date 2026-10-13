/*
 * Copyright (c) 2026-present New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowApplication

@RunWith(RobolectricTestRunner::class)
class AndroidAgentImplProcessGuardTest {

    private lateinit var spyContext: SpyContext
    private lateinit var agentConfiguration: AgentConfiguration

    @Before
    fun setUp() {
        Agent.setImpl(null)
        spyContext = SpyContext()
        agentConfiguration = AgentConfiguration().apply {
            applicationToken = "ab20dfe5-96d2-4c6d-b975-3fe9d8778dfc"
        }
    }

    @After
    fun tearDown() {
        Agent.stop()
        Agent.setImpl(null)
    }

    @Test
    fun initInstallsAgentImplInMainProcess() {
        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        assertTrue(Agent.getImpl() is AndroidAgentImpl)
    }

    @Test
    fun initDoesNothingInColonSuffixedSecondaryProcess() {
        val nullImpl = Agent.getImpl()
        ShadowApplication.setProcessName("${spyContext.context.packageName}:remote")

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        assertSame(nullImpl, Agent.getImpl())
    }

    @Test
    fun initDoesNothingInColonlessSecondaryProcess() {
        val nullImpl = Agent.getImpl()
        ShadowApplication.setProcessName("com.example.worker")

        AndroidAgentImpl.init(spyContext.context, agentConfiguration)

        assertSame(nullImpl, Agent.getImpl())
    }
}

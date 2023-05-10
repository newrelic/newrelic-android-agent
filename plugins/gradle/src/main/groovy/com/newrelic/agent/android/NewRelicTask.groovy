/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import com.newrelic.agent.InstrumentationAgent
import com.sun.tools.attach.VirtualMachine
import org.gradle.api.DefaultTask

import java.lang.management.ManagementFactory

abstract class NewRelicTask extends DefaultTask {
    def getPid() {
        final String nameOfRunningVM = ManagementFactory.getRuntimeMXBean().getName()
        final int p = nameOfRunningVM.indexOf('@')
        return nameOfRunningVM.substring(0, p)
    }

    def getJarFilePath() {
        try {
            String jarFilePath = InstrumentationAgent.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()

            // This last step is necessary as windows expects a windowsesque path
            // (for example C:\path\to\file).  A normal javaesque path would be in
            // the form /C:/path/to/file.
            jarFilePath = new File(jarFilePath).getCanonicalPath()

            logger.debug("Found New Relic instrumentation jar: " + jarFilePath)

            return jarFilePath
        } catch (URISyntaxException e) {
            logger.error("Unable to find New Relic instrumentation jar")
            throw new RuntimeException(e)
        } catch (IOException e) {
            logger.error("Unable to find New Relic instrumentation jar")
            throw new RuntimeException(e)
        }
    }

    def injectAgent(String agentArgs) {
        final VirtualMachine vm = VirtualMachine.attach(getPid())
        vm.loadAgent(getJarFilePath(), agentArgs)
        vm.detach()
    }
}

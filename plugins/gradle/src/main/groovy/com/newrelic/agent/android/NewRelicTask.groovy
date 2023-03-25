/*
 * Copyright (c) 2022 - present. New Relic Corporation. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.newrelic.agent.android

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

class NewRelicTask extends DefaultTask {
    /**
     * Create and register with Gradle an instance of this task type
     *
     * @return
     */
    TaskProvider register(Project project, def variant) { project.tasks.named(getName()) }

    /**
     * Configure tha task
     *
     * @return The provider of this task
     */
    TaskProvider configure(Project project, def variant) { project.tasks.named(getName()) }

    /**
     * Perform final task fixups prior to DSL lock. Should also try to lock any task data
     *
     * @return The provider of this task
     */
    TaskProvider finalize(Project project, def variant) { project.tasks.named(getName()) }

    /**
     * Sanity check the task, including data and dependencies
     *
     * @return true is verification was successful
     */
    Boolean verify(Project project, def variant) { project.tasks.named(getName()) }
}

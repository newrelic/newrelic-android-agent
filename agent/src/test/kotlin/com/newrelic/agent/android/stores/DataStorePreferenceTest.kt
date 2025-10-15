package com.newrelic.agent.android.stores

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class DataStorePreferenceTest {
    private lateinit var context: Context

    // Define a list of filenames that might be created by tests for cleanup
    private val testDataStoreFiles = mutableListOf(
        USER_PREFERENCES_FILE_NAME,
        "dynamic_store_A",
        "dynamic_store_B",
        "another_dynamic_store"
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Crucial: Clean up any DataStore files from previous runs before each test
        clearAllTestDataStoreFiles()
    }

    @After
    fun tearDown() {
        // Crucial: Clean up DataStore files created during the test
        clearAllTestDataStoreFiles()
    }

    private fun clearAllTestDataStoreFiles() {
        val datastoreDir = File(context.filesDir, "datastore")
        if (datastoreDir.exists() && datastoreDir.isDirectory) {
            testDataStoreFiles.forEach { fileName ->
                File(datastoreDir, "$fileName.preferences_pb").delete()
                File(datastoreDir, "$fileName.preferences_pb.tmp").delete() // Just in case
            }
        }
    }

    // --- Tests for default dataStorePreference ---
    @Test
    fun `dataStorePreference returns non-null instance`() {
        val store = context.dataStorePreference
        assertNotNull("Default DataStore instance should not be null", store)
    }

    @Test
    fun `dataStorePreference returns singleton instance on multiple accesses`() {
        val store1 = context.dataStorePreference
        val store2 = context.dataStorePreference
        assertSame("Multiple accesses to dataStorePreference should yield the same instance", store1, store2)
    }

    // --- Tests for getNamedDataStorePreference ---
    @Test
    fun `getNamedDataStorePreference returns non-null instance`() {
        val store = context.getNamedDataStorePreference("my_custom_store_1")
        assertNotNull("Named DataStore instance should not be null", store)
        testDataStoreFiles.add("my_custom_store_1") // Add to list for cleanup
    }

    @Test
    fun `getNamedDataStorePreference returns same instance for same name`() {
        val name = "dynamic_store_A"
        val store1 = context.getNamedDataStorePreference(name)
        val store2 = context.getNamedDataStorePreference(name)
        assertSame("Accessing with same dynamic name should yield same instance", store1, store2)
    }

    @Test
    fun `getNamedDataStorePreference returns different instances for different names`() {
        val name1 = "dynamic_store_A"
        val name2 = "dynamic_store_B"
        val store1 = context.getNamedDataStorePreference(name1)
        val store2 = context.getNamedDataStorePreference(name2)
        assertNotSame("Accessing with different dynamic names should yield different instances", store1, store2)
    }

    @Test
    fun `getNamedDataStorePreference with default name returns default dataStorePreference instance`() {
        val defaultNamedStore = context.getNamedDataStorePreference(USER_PREFERENCES_FILE_NAME)
        val actualDefaultStore = context.dataStorePreference
        assertSame(
            "Getting DataStore by default name should return the default dataStorePreference instance",
            actualDefaultStore,
            defaultNamedStore
        )
    }


    // --- Tests for createSupervisorJob ---
    @Test
    fun `createSupervisorJob returns non-null CompletableJob`() {
        val job = createSupervisorJob()
        assertNotNull("createSupervisorJob should return a non-null job", job)
    }

    @Test
    fun `createSupervisorJob returns an active SupervisorJob`() {
        val job : CompletableJob = createSupervisorJob() // Explicit type for clarity
        assertTrue("Job should be active upon creation", job.isActive)
        assertFalse("Job should not be completed upon creation", job.isCompleted)
        assertFalse("Job should not be cancelled upon creation", job.isCancelled)
    }

    @Test
    fun `SupervisorJob created by createSupervisorJob isolates child failures`() {
        val supervisor = createSupervisorJob() // Cast for specific SupervisorJob behavior

        val childJob1 = Job(supervisor) // childJob1 is a child of supervisor
        val childJob2 = Job(supervisor) // childJob2 is a child of supervisor

        // Fail childJob1
        childJob1.cancel(kotlinx.coroutines.CancellationException("Child 1 failed"))

        assertTrue("SupervisorJob should remain active after a child fails", supervisor.isActive)
        assertFalse("SupervisorJob should not be cancelled when a child fails", supervisor.isCancelled)
        assertTrue("ChildJob2 should remain active as sibling", childJob2.isActive)

        supervisor.cancel() // Clean up the supervisor job itself
    }
}


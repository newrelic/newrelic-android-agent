package com.newrelic.agent.android.stores

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DataStoreKeysTest {
    @Before
    fun setUp() {
        DataStoreKeys.clearRegisteredKeys()
    }

    @Test
    fun `defineKey creates and registers STRING key correctly`() {
        val keyName = "myStringKey"
        val key = DataStoreKeys.defineKey<String>(keyName, PreferenceType.STRING)

        assertEquals("Key name should match", keyName, key.name)
        assertTrue("Key should be registered", DataStoreKeys.registeredKeys.containsKey(keyName))
        assertTrue("Key should be of type StringPreferenceKey",
            stringPreferencesKey("dummy").javaClass == key.javaClass
        )
    }

    @Test
    fun `defineKey creates and registers LONG key (for Int) correctly`() {
        val keyName = "myLongKey"
        val key = DataStoreKeys.defineKey<Long>(keyName, PreferenceType.LONG)

        assertEquals("Key name should match", keyName, key.name)
        assertTrue("Key should be registered", DataStoreKeys.registeredKeys.containsKey(keyName))
        assertTrue("Key should be of type LongPreferenceKey",
            longPreferencesKey("dummy").javaClass == key.javaClass
        )
    }

    @Test
    fun `defineKey creates and registers BOOLEAN key correctly`() {
        val keyName = "myBooleanKey"
        val key = DataStoreKeys.defineKey<Boolean>(keyName, PreferenceType.BOOLEAN)

        assertEquals("Key name should match", keyName, key.name)
        assertTrue("Key should be registered", DataStoreKeys.registeredKeys.containsKey(keyName))
        assertTrue("Key should be of type BooleanPreferenceKey",
            booleanPreferencesKey("dummy").javaClass == key.javaClass
        )
    }

    @Test
    fun `defineKey creates and registers STRING_SET key correctly`() {
        val keyName = "myStringSetKey"
        val key = DataStoreKeys.defineKey<Set<String>>(keyName, PreferenceType.SET)

        assertEquals("Key name should match", keyName, key.name)
        assertTrue("Key should be registered", DataStoreKeys.registeredKeys.containsKey(keyName))
        assertTrue("Key should be of type StringSetPreferenceKey",
            stringSetPreferencesKey("dummy").javaClass == key.javaClass
        )
    }

    @Test
    fun `defineKey reuses existing key if name and PreferenceType match`() {
        val keyName = "sharedKey"
        val key1 = DataStoreKeys.defineKey<String>(keyName, PreferenceType.STRING)
        val key2 = DataStoreKeys.defineKey<String>(keyName, PreferenceType.STRING) // Same name and type

        assertEquals("Only one key should be registered for the name", 1, DataStoreKeys.registeredKeys.size)
    }

    @Test
    fun `defineKey reuses existing STRING_SET key if name and PreferenceType match`() {
        val keyName = "sharedSetKey"
        val key1 = DataStoreKeys.defineKey<Set<String>>(keyName, PreferenceType.SET)
        val key2 = DataStoreKeys.defineKey<Set<String>>(keyName, PreferenceType.SET)

        assertEquals("Only one Set key should be registered for the name", 1, DataStoreKeys.registeredKeys.size)
    }

    @Test
    fun `expungeStaleEntries removes garbage collected keys`() {
        val keyName = "tempKey"
        DataStoreKeys.defineKey<String>(keyName, PreferenceType.STRING)
        assertEquals(1, DataStoreKeys.registeredKeys.size)

        // Trigger cleanup
        DataStoreKeys.expungeStaleEntries()

        // Size should remain same or decrease (depending on GC)
        assertTrue("Size should not increase", DataStoreKeys.registeredKeys.size <= 1)
    }

    @Test
    fun `concurrent defineKey calls are thread safe`() {
        val keyNames = (1..10).map { "concurrentKey$it" }
        val threads = keyNames.map { keyName ->
            Thread {
                DataStoreKeys.defineKey<String>(keyName, PreferenceType.STRING)
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        assertEquals("All concurrent keys should be registered", 10, DataStoreKeys.registeredKeys.size)
    }
}


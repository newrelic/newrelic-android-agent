package com.newrelic.agent.android.stores

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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
        assertSame("Registered key should be the same instance", key, DataStoreKeys.registeredKeys[keyName])
        assertTrue("Key should be of type StringPreferenceKey",
            stringPreferencesKey("dummy").javaClass == key.javaClass
        )
    }

    @Test
    fun `defineKey creates and registers LONG key (for Int) correctly`() {
        val keyName = "myIntKey"
        val key = DataStoreKeys.defineKey<Int>(keyName, PreferenceType.LONG)

        assertEquals("Key name should match", keyName, key.name)
        assertTrue("Key should be registered", DataStoreKeys.registeredKeys.containsKey(keyName))
        assertSame("Registered key should be the same instance", key, DataStoreKeys.registeredKeys[keyName])
        assertTrue("Key should be of type IntPreferenceKey",
            intPreferencesKey("dummy").javaClass == key.javaClass
        )
    }

    @Test
    fun `defineKey creates and registers BOOLEAN key correctly`() {
        val keyName = "myBooleanKey"
        val key = DataStoreKeys.defineKey<Boolean>(keyName, PreferenceType.BOOLEAN)

        assertEquals("Key name should match", keyName, key.name)
        assertTrue("Key should be registered", DataStoreKeys.registeredKeys.containsKey(keyName))
        assertSame("Registered key should be the same instance", key, DataStoreKeys.registeredKeys[keyName])
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
        assertSame("Registered key should be the same instance", key, DataStoreKeys.registeredKeys[keyName])
        assertTrue("Key should be of type StringSetPreferenceKey",
            stringSetPreferencesKey("dummy").javaClass == key.javaClass
        )
    }

    @Test
    fun `defineKey reuses existing key if name and PreferenceType match`() {
        val keyName = "sharedKey"
        val key1 = DataStoreKeys.defineKey<String>(keyName, PreferenceType.STRING)
        val key2 = DataStoreKeys.defineKey<String>(keyName, PreferenceType.STRING) // Same name and type

        assertSame("Should return the same instance for identical requests", key1, key2)
        assertEquals("Only one key should be registered for the name", 1, DataStoreKeys.registeredKeys.size)
    }

    @Test
    fun `defineKey reuses existing STRING_SET key if name and PreferenceType match`() {
        val keyName = "sharedSetKey"
        val key1 = DataStoreKeys.defineKey<Set<String>>(keyName, PreferenceType.SET)
        val key2 = DataStoreKeys.defineKey<Set<String>>(keyName, PreferenceType.SET)

        assertSame("Should return the same instance for identical Set requests", key1, key2)
        assertEquals("Only one Set key should be registered for the name", 1, DataStoreKeys.registeredKeys.size)
    }

    @Test
    fun `defineKey overwrites and registers new key if PreferenceType differs for same name (warns)`() {
        val keyName = "conflictingTypeKey"
        val stringKey = DataStoreKeys.defineKey<String>(keyName, PreferenceType.STRING)
        val intKey = DataStoreKeys.defineKey<Int>(keyName, PreferenceType.LONG)

        assertSame("The new (Int) key should now be the one registered", intKey, DataStoreKeys.registeredKeys[keyName])
        assertEquals("Only one key should be registered, the latest one", 1, DataStoreKeys.registeredKeys.size)
        assertTrue("The registered key should be of type IntPreferenceKey",
            intPreferencesKey("dummy").javaClass == intKey.javaClass
        )
    }

    @Test
    fun `defineKey handles case where existing key type matches PreferenceType but generic T is incompatible (warns and recreates)`() {
        val keyName = "genericMismatchButTypeOkayKey"
        val originalStringKey = DataStoreKeys.defineKey<String>(keyName, PreferenceType.STRING)
        val newKeyAttempt = DataStoreKeys.defineKey<Int>(keyName, PreferenceType.STRING)

        val registered = DataStoreKeys.registeredKeys[keyName]
        assertTrue("Registered key should still be a StringPreferenceKey type because PreferenceType.STRING was used for recreation",
            stringPreferencesKey("dummy").javaClass == registered!!.javaClass)
    }
}


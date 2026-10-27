package com.pawtillery.game

import com.badlogic.gdx.Preferences

/** In-memory libGDX Preferences, so Settings can be tested without a running app. */
class MemoryPreferences : Preferences {
    private val map = HashMap<String, Any>()

    override fun putBoolean(key: String, value: Boolean) = apply { map[key] = value }
    override fun putInteger(key: String, value: Int) = apply { map[key] = value }
    override fun putLong(key: String, value: Long) = apply { map[key] = value }
    override fun putFloat(key: String, value: Float) = apply { map[key] = value }
    override fun putString(key: String, value: String) = apply { map[key] = value }
    override fun put(vals: MutableMap<String, *>) = apply { vals.forEach { (k, v) -> if (v != null) map[k] = v } }
    override fun getBoolean(key: String) = getBoolean(key, false)
    override fun getInteger(key: String) = getInteger(key, 0)
    override fun getLong(key: String) = getLong(key, 0L)
    override fun getFloat(key: String) = getFloat(key, 0f)
    override fun getString(key: String) = getString(key, "")
    override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
    override fun getInteger(key: String, defValue: Int) = map[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = map[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = map[key] as? Float ?: defValue
    override fun getString(key: String, defValue: String) = map[key] as? String ?: defValue
    override fun get(): MutableMap<String, *> = HashMap(map)
    override fun contains(key: String) = map.containsKey(key)
    override fun clear() = map.clear()
    override fun remove(key: String) {
        map.remove(key)
    }
    override fun flush() = Unit
}

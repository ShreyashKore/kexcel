package com.gyanoba.kxcel.utils

public class FastList<K>() {

    private var map: MutableMap<K, Int> = mutableMapOf()
    private var index: Int = 0

    public constructor(other: FastList<K>) : this() {
        this.map = other.map.toMutableMap()
        this.index = other.index
    }

    public fun add(key: K) {
        if (!map.containsKey(key)) {
            map[key] = index
            index += 1
        }
    }

    public fun contains(key: K): Boolean {
        return map.containsKey(key)
    }

    public fun remove(key: K) {
        map.remove(key)
    }

    public fun clear() {
        index = 0
        map.clear()
    }

    public val keys: List<K>
        get() = map.keys.toList()

    public val isNotEmpty: Boolean
        get() = map.isNotEmpty()
}
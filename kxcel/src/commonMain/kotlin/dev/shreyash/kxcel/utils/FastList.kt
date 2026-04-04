package dev.shreyash.kxcel.utils

class FastList<K>() {

    private var map: MutableMap<K, Int> = mutableMapOf()
    private var index: Int = 0

    constructor(other: FastList<K>) : this() {
        this.map = other.map.toMutableMap()
        this.index = other.index
    }

    fun add(key: K) {
        if (!map.containsKey(key)) {
            map[key] = index
            index += 1
        }
    }

    fun contains(key: K): Boolean {
        return map.containsKey(key)
    }

    fun remove(key: K) {
        map.remove(key)
    }

    fun clear() {
        index = 0
        map.clear()
    }

    val keys: List<K>
        get() = map.keys.toList()

    val isNotEmpty: Boolean
        get() = map.isNotEmpty()
}
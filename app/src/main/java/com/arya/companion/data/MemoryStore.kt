package com.arya.companion.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

val MEMORY_CATEGORIES = listOf("identity", "preference", "goal", "project", "relationship", "emotional", "behavior")

data class Memory(val id: String, val category: String, val text: String, val createdAt: Long)

/** Things Arya remembers about the user. Stored only on the device and shown (and deletable) in Settings. */
class MemoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("arya_memories", Context.MODE_PRIVATE)
    private val state = MutableStateFlow(load())

    val memories: StateFlow<List<Memory>> = state.asStateFlow()

    @Synchronized
    fun add(category: String, text: String): Boolean {
        val clean = text.replace(Regex("\\s+"), " ").trim().take(300)
        if (clean.isEmpty() || category !in MEMORY_CATEGORIES) return false
        val current = state.value
        if (current.any { it.text.equals(clean, ignoreCase = true) }) return true
        val entry = Memory(
            id = System.currentTimeMillis().toString(36) + (0..999).random().toString(36),
            category = category,
            text = clean,
            createdAt = System.currentTimeMillis(),
        )
        commit((current + entry).takeLast(MAX_MEMORIES))
        return true
    }

    @Synchronized
    fun delete(id: String) = commit(state.value.filterNot { it.id == id })

    @Synchronized
    fun clear() = commit(emptyList())

    private fun commit(list: List<Memory>) {
        state.value = list
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("category", it.category)
                    .put("text", it.text)
                    .put("createdAt", it.createdAt),
            )
        }
        prefs.edit().putString("list", array.toString()).apply()
    }

    private fun load(): List<Memory> {
        val raw = prefs.getString("list", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val o = array.optJSONObject(i) ?: return@mapNotNull null
                val text = o.optString("text")
                if (text.isEmpty()) null else Memory(o.optString("id"), o.optString("category"), text, o.optLong("createdAt"))
            }
        }.getOrDefault(emptyList())
    }

    private companion object {
        const val MAX_MEMORIES = 80
    }
}

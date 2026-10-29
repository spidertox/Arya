package com.arya.companion.live

import android.content.Context
import com.arya.companion.Graph
import com.arya.companion.data.MEMORY_CATEGORIES
import com.arya.companion.device.DeviceActions
import com.arya.companion.ui.Themes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Function declarations sent to Gemini in the Live setup message (OpenAPI-style schema, upper-case types). */
object ToolSpecs {
    private fun prop(type: String, description: String, enum: List<String>? = null): JSONObject =
        JSONObject().put("type", type).put("description", description).also { if (enum != null) it.put("enum", JSONArray(enum)) }

    private fun fn(name: String, description: String, props: Map<String, JSONObject> = emptyMap(), required: List<String> = emptyList()): JSONObject {
        val declaration = JSONObject().put("name", name).put("description", description)
        if (props.isNotEmpty()) {
            val properties = JSONObject()
            props.forEach { (key, value) -> properties.put(key, value) }
            val parameters = JSONObject().put("type", "OBJECT").put("properties", properties)
            if (required.isNotEmpty()) parameters.put("required", JSONArray(required))
            declaration.put("parameters", parameters)
        }
        return declaration
    }

    fun declarations(): JSONArray = JSONArray().apply {
        put(
            fn(
                "changeBackground",
                "Changes the colour theme of the Arya app interface.",
                mapOf("color" to prop("STRING", "Theme colour: ${Themes.names.joinToString(", ")}.")),
                listOf("color"),
            ),
        )
        put(
            fn(
                "saveCustomMemory",
                "Saves one durable fact about the user to Arya's persistent memory.",
                mapOf(
                    "category" to prop("STRING", "The memory category.", MEMORY_CATEGORIES),
                    "text" to prop("STRING", "A precise third-person statement about the user."),
                ),
                listOf("category", "text"),
            ),
        )
        put(fn("getCurrentTime", "Returns the current local date, time and time zone of the user's phone."))
        put(fn("openApp", "Opens an installed app on the phone by its name, for example WhatsApp, YouTube, Camera or Settings.", mapOf("name" to prop("STRING", "App name as shown on the phone.")), listOf("name")))
        put(
            fn(
                "setAlarm",
                "Sets an alarm in the phone's clock app at a given time of day (24-hour clock).",
                mapOf(
                    "hour" to prop("INTEGER", "Hour, 0-23."),
                    "minute" to prop("INTEGER", "Minute, 0-59."),
                    "label" to prop("STRING", "Optional alarm label."),
                ),
                listOf("hour", "minute"),
            ),
        )
        put(
            fn(
                "setTimer",
                "Starts a countdown timer in the phone's clock app.",
                mapOf("seconds" to prop("INTEGER", "Timer length in seconds."), "label" to prop("STRING", "Optional timer label.")),
                listOf("seconds"),
            ),
        )
        put(fn("dialNumber", "Opens the phone dialer with a number filled in. The user presses the call button themselves.", mapOf("number" to prop("STRING", "Phone number.")), listOf("number")))
        put(fn("dialContact", "Looks up a saved contact by name and opens the dialer with their number. The user presses the call button themselves.", mapOf("name" to prop("STRING", "Contact name.")), listOf("name")))
        put(fn("webSearch", "Searches the web for a phrase in the phone's browser.", mapOf("query" to prop("STRING", "What to search for.")), listOf("query")))
        put(fn("openWebsite", "Opens a web address (http or https) in the phone's browser.", mapOf("url" to prop("STRING", "The web address.")), listOf("url")))
        put(fn("setFlashlight", "Turns the phone's flashlight on or off.", mapOf("on" to prop("BOOLEAN", "true to turn on, false to turn off.")), listOf("on")))
        put(fn("setVolume", "Sets the phone's media volume as a percentage.", mapOf("percent" to prop("INTEGER", "Volume, 0-100.")), listOf("percent")))
    }
}

/** Runs a function call from Gemini. Never throws: failures come back as `{ success: false, error }`. */
class ToolRunner(context: Context) {
    private val device = DeviceActions(context)

    private fun ok() = JSONObject().put("success", true)
    private fun fail(error: String) = JSONObject().put("success", false).put("error", error)

    suspend fun run(name: String, args: JSONObject): JSONObject = try {
        when (name) {
            "changeBackground" -> {
                val theme = Themes.resolve(args.optString("color"))
                if (theme == null) {
                    fail("Unknown colour. Use ${Themes.names.joinToString(", ")}.")
                } else {
                    Graph.settings.update { it.copy(theme = theme) }
                    ok().put("color", theme)
                }
            }
            "saveCustomMemory" -> {
                val saved = Graph.memories.add(args.optString("category"), args.optString("text"))
                if (saved) ok() else fail("Invalid category or empty text.")
            }
            "getCurrentTime" -> withContext(Dispatchers.Default) {
                val now = ZonedDateTime.now()
                JSONObject()
                    .put("iso", now.toOffsetDateTime().toString())
                    .put("local", now.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.FULL)))
                    .put("timeZone", ZoneId.systemDefault().id)
            }
            in DeviceActions.toolNames -> device.run(name, args)
            else -> fail("Unknown tool: $name")
        }
    } catch (e: Exception) {
        fail(e.message ?: "The tool failed.")
    }
}

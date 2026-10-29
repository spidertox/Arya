package com.arya.companion.device

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.arya.companion.AppVisibility
import com.arya.companion.MainActivity
import com.arya.companion.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Phone commands Arya can run through Gemini function calls. Everything uses standard Android intents:
 * calls only open the dialer, and alarms and timers go through the clock app.
 *
 * Android blocks starting activities from the background, so when the app is not on screen the intent
 * is handed to the user as a notification they can tap instead.
 */
class DeviceActions(private val context: Context) {

    companion object {
        const val CHANNEL_ACTION = "arya_actions"
        private var nextRequestCode = 100

        val toolNames = setOf(
            "openApp", "setAlarm", "setTimer", "dialNumber", "dialContact",
            "webSearch", "openWebsite", "setFlashlight", "setVolume",
        )

        fun hasContactsPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        private fun ok() = JSONObject().put("success", true)
        private fun fail(error: String) = JSONObject().put("success", false).put("error", error)
    }

    suspend fun run(name: String, args: JSONObject): JSONObject = when (name) {
        "openApp" -> onMain { openApp(args.optString("name")) }
        "setAlarm" -> onMain { setAlarm(args.optInt("hour", -1), args.optInt("minute", 0), args.optString("label")) }
        "setTimer" -> onMain { setTimer(args.optInt("seconds", -1), args.optString("label")) }
        "dialNumber" -> onMain { dialNumber(args.optString("number")) }
        "dialContact" -> dialContact(args.optString("name"))
        "webSearch" -> onMain { webSearch(args.optString("query")) }
        "openWebsite" -> onMain { openWebsite(args.optString("url")) }
        "setFlashlight" -> onMain { setFlashlight(args.optBoolean("on", true)) }
        "setVolume" -> onMain { setVolume(args.optInt("percent", -1)) }
        else -> fail("Unknown tool: $name")
    }

    private suspend fun <T> onMain(block: () -> T): T = withContext(Dispatchers.Main) { block() }

    // ---- launching ---------------------------------------------------------------------------

    private fun launch(intent: Intent, what: String): JSONObject {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (AppVisibility.visible.value) {
            return try {
                context.startActivity(intent)
                ok()
            } catch (e: ActivityNotFoundException) {
                fail("No app on this phone can do that.")
            }
        }
        // Background: Android blocks the launch, so ask the user to tap a notification.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return fail("The phone is locked or Arya is in the background, and notifications are off, so I cannot do that now. Ask the user to open Arya and try again.")
        }
        postTapNotification(intent, what)
        return ok().put("note", "Arya is in the background, so a notification was posted. Tell the user to tap it to $what.")
    }

    private fun postTapNotification(intent: Intent, what: String) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ACTION,
                context.getString(R.string.channel_action_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_action_desc) },
        )
        val pending = PendingIntent.getActivity(
            context,
            nextRequestCode++,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ACTION)
            .setSmallIcon(R.drawable.ic_stat_arya)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText("Tap to $what")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setTimeoutAfter(60_000)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        manager.notify(nextRequestCode, notification)
    }

    // ---- apps --------------------------------------------------------------------------------

    private fun normalize(value: String): String = value.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]"), "")

    private fun openApp(name: String): JSONObject {
        val wanted = normalize(name)
        if (wanted.length < 2) return fail("No app name was given.")
        return try {
            val pm = context.packageManager
            val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            var bestPackage: String? = null
            var bestLabel: String? = null
            var bestScore = 0
            for (info in pm.queryIntentActivities(query, 0)) {
                val label = info.loadLabel(pm).toString()
                val norm = normalize(label)
                if (norm.isEmpty()) continue
                val score = when {
                    norm == wanted -> 100
                    norm.startsWith(wanted) || (wanted.startsWith(norm) && norm.length >= 3) -> 70
                    norm.contains(wanted) -> 50
                    wanted.contains(norm) && norm.length >= 4 -> 40
                    else -> 0
                }
                if (score > bestScore) {
                    bestScore = score
                    bestPackage = info.activityInfo.packageName
                    bestLabel = label
                }
            }
            val pkg = bestPackage ?: return fail("No installed app matches \"$name\".")
            val intent = pm.getLaunchIntentForPackage(pkg) ?: return fail("That app cannot be opened.")
            val result = launch(intent, "open $bestLabel")
            if (result.optBoolean("success")) result.put("opened", bestLabel)
            result
        } catch (e: Exception) {
            fail("Could not open the app: ${e.message}")
        }
    }

    // ---- alarm & timer -----------------------------------------------------------------------

    private fun setAlarm(hour: Int, minute: Int, label: String): JSONObject {
        if (hour !in 0..23 || minute !in 0..59) return fail("The alarm time is not valid.")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Arya" })
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return launch(intent, "set the alarm for %02d:%02d".format(hour, minute))
    }

    private fun setTimer(seconds: Int, label: String): JSONObject {
        if (seconds !in 1..86_400) return fail("The timer length is not valid.")
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, label.ifBlank { "Arya" })
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        return launch(intent, "start the timer")
    }

    // ---- calls (dialer only) -----------------------------------------------------------------

    private fun dial(number: String, what: String): JSONObject =
        launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number))), what)

    private fun dialNumber(number: String): JSONObject {
        val cleaned = number.replace(Regex("[^0-9+*#]"), "")
        if (cleaned.length < 3) return fail("That phone number is not valid.")
        return dial(cleaned, "open the dialer")
    }

    private suspend fun dialContact(name: String): JSONObject {
        val wanted = name.trim()
        if (wanted.length < 2) return fail("No contact name was given.")
        if (!hasContactsPermission(context)) {
            return fail("Contacts access is off. Ask the user to allow it in Arya's settings, or give a phone number instead.")
        }
        val found = withContext(Dispatchers.IO) { lookupContacts(wanted) }
        if (found.isEmpty()) return fail("No contact matches \"$name\".")

        val chosen = found.keys.firstOrNull { it.equals(wanted, ignoreCase = true) }
            ?: found.keys.singleOrNull()
        if (chosen == null) {
            return fail("More than one contact matches. Ask which one.").put("candidates", JSONArray(found.keys.toList()))
        }
        val result = onMain { dial(found.getValue(chosen), "open the dialer for $chosen") }
        if (result.optBoolean("success")) result.put("contact", chosen)
        return result
    }

    private fun lookupContacts(name: String): Map<String, String> {
        val found = LinkedHashMap<String, String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?",
            arrayOf("%$name%"),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC",
        )?.use { cursor ->
            while (cursor.moveToNext() && found.size < 6) {
                val contactName = cursor.getString(0)
                val number = cursor.getString(1)
                if (contactName != null && number != null && !found.containsKey(contactName)) found[contactName] = number
            }
        }
        return found
    }

    // ---- web ---------------------------------------------------------------------------------

    private fun webSearch(query: String): JSONObject {
        val text = query.trim()
        if (text.isEmpty()) return fail("No search text was given.")
        val intent = Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, text)
        val result = launch(intent, "see the search results")
        if (result.optBoolean("success") || result.has("note")) return result
        // No app handles WEB_SEARCH: fall back to a plain browser URL.
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(text))), "see the search results")
    }

    private fun openWebsite(url: String): JSONObject {
        var address = url.trim()
        if (address.isEmpty()) return fail("No address was given.")
        if (!Regex("(?i)^[a-z][a-z0-9+.-]*:.*").matches(address)) address = "https://$address"
        val lower = address.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return fail("Only http and https addresses can be opened.")
        return launch(Intent(Intent.ACTION_VIEW, Uri.parse(address)), "open the website")
    }

    // ---- hardware ----------------------------------------------------------------------------

    private fun setFlashlight(on: Boolean): JSONObject {
        return try {
            val manager = context.getSystemService(CameraManager::class.java)
            for (id in manager.cameraIdList) {
                val hasFlash = manager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE)
                if (hasFlash == true) {
                    manager.setTorchMode(id, on)
                    return ok()
                }
            }
            fail("This phone has no flashlight.")
        } catch (e: Exception) {
            fail("Could not switch the flashlight: ${e.message}")
        }
    }

    private fun setVolume(percent: Int): JSONObject {
        if (percent !in 0..100) return fail("The volume must be between 0 and 100.")
        return try {
            val audio = context.getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(max * percent / 100f), 0)
            ok()
        } catch (e: Exception) {
            fail("Could not change the volume: ${e.message}")
        }
    }
}

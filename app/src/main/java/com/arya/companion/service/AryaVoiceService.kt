package com.arya.companion.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.arya.companion.Graph
import com.arya.companion.MainActivity
import com.arya.companion.R
import com.arya.companion.data.WakeMode
import com.arya.companion.live.AryaEngine
import com.arya.companion.live.LiveState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service (type: microphone) that hosts a conversation, and in "Always" wake mode also the
 * "Hey Arya" listener. Android requires microphone use in the background to run inside a visible
 * foreground service, which is why this exists. It is only ever started from the app screen (or from its own
 * notification), never at boot, and it always shows a notification with a Stop button.
 */
class AryaVoiceService : Service() {

    private enum class Mode { None, Conversation, Wake }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var mode = Mode.None
    private var wake: WakeWordListener? = null
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                AryaEngine.stop()
                shutdown()
            }
            ACTION_CONVERSATION -> {
                if (!enterForeground(getString(R.string.notif_talking))) return START_NOT_STICKY
                beginConversation()
            }
            ACTION_WAKE -> {
                // Never interrupt a conversation that is already running.
                val text = if (mode == Mode.Conversation) R.string.notif_talking else R.string.notif_listening
                if (!enterForeground(getString(text))) return START_NOT_STICKY
                if (mode == Mode.None) beginWake()
            }
            else -> stopSelf() // restarted by the system without a command: never listen silently
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wake?.stop()
        wake = null
        stateJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // ---- modes -------------------------------------------------------------------------------

    private fun beginConversation() {
        wake?.stop()
        wake = null
        mode = Mode.Conversation
        updateNotification(getString(R.string.notif_talking))
        AryaEngine.start(withMicrophone = true)

        // When the conversation ends go back to listening for the wake word, or stop.
        stateJob?.cancel()
        stateJob = scope.launch {
            var sawActive = false
            AryaEngine.state.collect { state ->
                if (state != LiveState.Disconnected) {
                    sawActive = true
                } else if (sawActive) {
                    stateJob?.cancel()
                    conversationEnded()
                }
            }
        }
    }

    private fun conversationEnded() {
        if (Graph.settings.settings.value.wakeMode == WakeMode.Always) beginWake() else shutdown()
    }

    private fun beginWake() {
        mode = Mode.Wake
        updateNotification(getString(R.string.notif_listening))
        val listener = WakeWordListener(
            context = this,
            onWake = { onWakeWordHeard() },
            onProblem = { message ->
                AryaEngine.reportProblem(message)
                shutdown()
            },
        )
        wake = listener
        listener.start()
    }

    private fun onWakeWordHeard() {
        wake = null
        chime()
        beginConversation()
    }

    /** A short tone so the user knows Arya heard them, even with the screen off. */
    private fun chime() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone.startTone(ToneGenerator.TONE_PROP_ACK, 150)
            scope.launch {
                kotlinx.coroutines.delay(400)
                tone.release()
            }
        } catch (e: Exception) {
            // optional
        }
    }

    private fun shutdown() {
        mode = Mode.None
        wake?.stop()
        wake = null
        stateJob?.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ---- notification ------------------------------------------------------------------------

    /** Returns false when Android refuses to start the foreground service (for example a missing permission). */
    private fun enterForeground(text: String): Boolean {
        return try {
            createChannel(this)
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            true
        } catch (e: Exception) {
            AryaEngine.reportProblem("Android would not start Arya's voice service. Check the microphone permission and try again.")
            stopSelf()
            false
        }
    }

    private fun buildNotification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, AryaVoiceService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_arya)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(text)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_stop), stop)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(text))
        } catch (e: Exception) {
            // notifications may be blocked
        }
    }

    companion object {
        const val ACTION_CONVERSATION = "com.arya.companion.action.CONVERSATION"
        const val ACTION_WAKE = "com.arya.companion.action.WAKE"
        const val ACTION_STOP = "com.arya.companion.action.STOP"
        private const val CHANNEL_ID = "arya_voice"
        private const val NOTIFICATION_ID = 4101

        private fun createChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.channel_voice_name), NotificationManager.IMPORTANCE_LOW)
                    .apply { description = context.getString(R.string.channel_voice_desc) },
            )
        }

        /** Starts a voice conversation. Call only while the app is on screen. */
        fun startConversation(context: Context) = start(context, ACTION_CONVERSATION)

        /** Starts the always-on "Hey Arya" listener. Call only while the app is on screen. */
        fun startWake(context: Context) = start(context, ACTION_WAKE)

        fun stopWake(context: Context) {
            if (AryaEngine.state.value == LiveState.Disconnected) {
                context.stopService(Intent(context, AryaVoiceService::class.java))
            }
        }

        private fun start(context: Context, action: String) {
            ContextCompat.startForegroundService(context, Intent(context, AryaVoiceService::class.java).setAction(action))
        }
    }
}

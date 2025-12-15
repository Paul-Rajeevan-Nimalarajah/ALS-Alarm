package com.sldevelopers.alsalarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat

class AlarmService : Service() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private lateinit var audioManager: AudioManager
    private var originalAlarmVolume: Int = -1

    override fun onCreate() {
        super.onCreate()
        Log.d("AlarmService", "Service onCreate")
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d("AlarmService", "Service onStartCommand")

        val channelId = "alarm_channel"
        val channelName = "Alarm Channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH)
            channel.setBypassDnd(true)
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, AlarmScreenActivity::class.java)
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Alarm")
            .setContentText("Your alarm is ringing.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()

        startSoundAndVibration()
        startForeground(1, notification)

        return START_STICKY
    }

    private fun startSoundAndVibration() {
        val sharedPrefs = getSharedPreferences("AlarmSettings", Context.MODE_PRIVATE)
        val ringtoneUriString = sharedPrefs.getString("ringtone_uri", null)
        val volumePercentage = sharedPrefs.getInt("volume", 80)

        val alarmUri = if (ringtoneUriString != null) {
            Uri.parse(ringtoneUriString)
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }
        
        try {
            // Force the system's alarm volume to the desired level
            originalAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val desiredVolume = (volumePercentage / 100f * maxVolume).toInt()
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, desiredVolume, 0)

            // Start Ringtone with MediaPlayer
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alarmUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
            Log.d("AlarmService", "Ringtone started with system volume forced to: $desiredVolume")

            // Start Vibration
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 1000, 500), 0))
            Log.d("AlarmService", "Vibration started.")

        } catch (e: Exception) {
            Log.e("AlarmService", "Error starting sound or vibration", e)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        vibrator?.cancel()

        // Restore the original alarm volume
        if (originalAlarmVolume != -1) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalAlarmVolume, 0)
            Log.d("AlarmService", "Original alarm volume restored.")
        }
        Log.d("AlarmService", "Service destroyed. Alarm stopped.")
    }
}

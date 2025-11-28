package com.example.flashcard

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Calendar

// ----------------------------------------
// 1. The Receiver (Shows the Notification)
// ----------------------------------------
class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "Study Reminder"
        val message = intent.getStringExtra("message") ?: "Time to study!"
        val notificationId = intent.getIntExtra("id", 0)

        Log.d("NotificationReceiver", "Received event: $title (ID: $notificationId)")

        // Create an intent to open the app when clicking the notification
        val activityIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            activityIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "scheduler_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
    }
}

// ----------------------------------------
// 2. The Scheduler Object (Helper)
// ----------------------------------------
object NotificationScheduler {

    /**
     * Schedules a system notification for a specific time.
     * @param dateType Must be "today" or "tomorrow".
     */
    fun scheduleEvent(
        context: Context,
        title: String,
        description: String,
        dateType: String,
        hour: Int,
        minute: Int
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val calendar = Calendar.getInstance()

        // If "tomorrow", add 1 day
        if (dateType.lowercase() == "tomorrow") {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // Set time components
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        val targetTime = calendar.timeInMillis
        val currentTime = System.currentTimeMillis()

        // 1. Check if the scheduled time is in the past.
        if (targetTime <= currentTime) {
            // If the time is in the past on the current day, it must be for the next day.
            // This prevents the alarm from failing immediately if the scheduling took too long
            // or if the event was set for an earlier time on the current day.
            if (dateType.lowercase() == "today") {
                Log.w("NotificationScheduler", "Attempted to schedule 'today' event in the past. Scheduling for tomorrow instead.")
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            } else {
                // If it's "tomorrow" and it's still somehow in the past, something is wrong, skip.
                return
            }
        }

        val notificationId = (calendar.timeInMillis / 1000).toInt() // Unique ID based on time in seconds

        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("title", title)
            putExtra("message", description)
            putExtra("id", notificationId)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        try {
            // Schedule the alarm using setExact
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                } else {
                    // Fallback if permission is missing (though Manifest requests it)
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }

            Log.d("NotificationScheduler", "Scheduled notification: $title at ${calendar.time}")

        } catch (e: SecurityException) {
            e.printStackTrace()
            // In a real app, this should be handled gracefully in UI
        }
    }
}
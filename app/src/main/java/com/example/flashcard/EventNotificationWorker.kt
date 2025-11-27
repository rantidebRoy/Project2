package com.example.flashcard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.util.*

class EventNotificationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val db = FirebaseFirestore.getInstance()
    private val userId = FirebaseAuth.getInstance().currentUser?.uid

    override suspend fun doWork(): Result {
        if (userId == null) return Result.failure()

        try {
            // Fetch all events of today
            val snapshot = db.collection("users")
                .document(userId)
                .collection("events")
                .whereEqualTo("date", "today") // or use dynamic date if needed
                .get()
                .await()

            val allEvents = snapshot.documents.mapNotNull { doc ->
                val data = doc.data ?: return@mapNotNull null
                data + ("docId" to doc.id)
            }

            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            val currentMinute = calendar.get(Calendar.MINUTE)

            createNotificationChannel()

            allEvents.forEach { event ->
                val hour = (event["hour"] as? Long ?: 0L).toInt()
                val minute = (event["minute"] as? Long ?: 0L).toInt()
                val docId = event["docId"] as? String ?: UUID.randomUUID().toString()
                val title = event["title"] as? String ?: "Event"

                // Check if current time matches event time
                if (currentHour == hour && currentMinute == minute) {
                    val builder = NotificationCompat.Builder(applicationContext, "EVENT_CHANNEL")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Event Reminder")
                        .setContentText(title)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)

                    // Make sure notificationId is positive Int
                    val notificationId = docId.hashCode().let { if (it < 0) -it else it }
                    NotificationManagerCompat.from(applicationContext).notify(notificationId, builder.build())
                }
            }

            return Result.success()

        } catch (e: Exception) {
            e.printStackTrace()
            return Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "EVENT_CHANNEL",
                "Event Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Channel for Event Reminders"
            }
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

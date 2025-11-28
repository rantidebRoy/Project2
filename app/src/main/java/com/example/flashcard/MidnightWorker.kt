package com.example.flashcard

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class MidnightWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val db = FirebaseFirestore.getInstance()
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        // If no user is logged in, we can't update their database
        if (userId == null) return Result.success()

        val eventsRef = db.collection("users").document(userId).collection("events")
        val appContext = applicationContext // Use application context for scheduling

        return try {
            val batch = db.batch()
            val eventsToSchedule = mutableListOf<Map<String, Any>>()

            // 1. --- Query for "today" (To Delete) ---
            val todaySnapshot = eventsRef.whereEqualTo("date", "today").get().await()
            for (doc in todaySnapshot.documents) {
                batch.delete(doc.reference)
            }

            // 2. --- Query for "tomorrow" (To Update and Schedule) ---
            val tomorrowSnapshot = eventsRef.whereEqualTo("date", "tomorrow").get().await()
            for (doc in tomorrowSnapshot.documents) {
                // Update tag in batch
                batch.update(doc.reference, "date", "today")

                // Extract details needed for scheduling
                doc.data?.let { data ->
                    eventsToSchedule.add(data)
                }
            }

            // 3. Commit database changes
            batch.commit().await()

            // 4. Schedule notifications for the newly "today" events
            for (event in eventsToSchedule) {
                NotificationScheduler.scheduleEvent(
                    context = appContext,
                    title = event["title"] as? String ?: "New Today Event",
                    description = event["description"] as? String ?: "Check your schedule.",
                    dateType = "today", // It is now guaranteed to be 'today'
                    hour = (event["hour"] as? Long)?.toInt() ?: 9,
                    minute = (event["minute"] as? Long)?.toInt() ?: 0
                )
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            // Log the failure and retry later if possible
            Result.retry()
        }
    }
}
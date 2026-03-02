package com.example.flashcard

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

//data class Flashcard(
//    val id: String = "",
//    val question: String = "",
//    val answer: String = ""
//)

object FirestoreRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun userId(): String = auth.currentUser?.uid ?: "guest"

    suspend fun getTopics(): List<String> {
        val snapshot = db.collection("users").document(userId())
            .collection("topics")
            .get()
            .await()

        return snapshot.documents.mapNotNull { it.id }
    }

    suspend fun addTopic(topicName: String) {
        val topicRef = db.collection("users").document(userId())
            .collection("topics").document(topicName)
        topicRef.set(mapOf("name" to topicName)).await()
    }

    suspend fun getFlashcards(topic: String): List<Flashcard> {
        val snapshot = db.collection("users").document(userId())
            .collection("topics").document(topic)
            .collection("flashcards")
            .get()
            .await()

        return snapshot.documents.map {
            Flashcard(
                id = it.id,
                question = it.getString("question") ?: "",
                answer = it.getString("answer") ?: ""
            )
        }
    }

    suspend fun addFlashcard(topic: String, question: String, answer: String) {
        val ref = db.collection("users").document(userId())
            .collection("topics").document(topic)
            .collection("flashcards").document()
        ref.set(mapOf("question" to question, "answer" to answer)).await()
    }

    suspend fun deleteFlashcard(topic: String, id: String) {
        db.collection("users").document(userId())
            .collection("topics").document(topic)
            .collection("flashcards").document(id)
            .delete()
            .await()
    }
}



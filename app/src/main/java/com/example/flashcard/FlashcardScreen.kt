package com.example.flashcard

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlin.random.Random
import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cloudinary.android.MediaManager
import com.cloudinary.android.callback.ErrorInfo
import com.cloudinary.android.callback.UploadCallback
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.Close
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow

// --- Data Models ---
data class Topic(
    val id: String = "",
    val name: String = ""
)

data class Flashcard(
    val id: String = "",
    val question: String = "",
    val answer: String = "",
    val questionImageUrl: String? = null,
    val answerImageUrl: String? = null,
    val timestamp: Long = 0
)

// --- ViewModel ---
class FlashcardViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    var topics by mutableStateOf<List<Topic>>(emptyList())
        private set

    var flashcards by mutableStateOf<List<Flashcard>>(emptyList())
        private set

    var currentTopic by mutableStateOf<Topic?>(null)
        private set

    // ---------- Topics ----------
    fun loadTopics() {
        val uid = auth.currentUser?.uid ?: return
        db.collection("users").document(uid).collection("topics")
            .get()
            .addOnSuccessListener { result ->
                topics = result.documents.map { doc ->
                    val fieldId = doc.get("id")?.toString()
                    Topic(
                        id = fieldId ?: "",
                        name = doc.getString("name") ?: ""
                    )
                }.sortedWith(Comparator { t1, t2 ->
                    // Extract numeric suffix for correct "Old to New" sorting (e.g. f2 < f10)
                    val n1 = t1.id.substringAfterLast("f").toIntOrNull()
                    val n2 = t2.id.substringAfterLast("f").toIntOrNull()
                    if (n1 != null && n2 != null) {
                        n1.compareTo(n2)
                    } else {
                        t1.id.compareTo(t2.id)
                    }
                })
            }
    }

    // --- Add Topic With Incremental ID Based on User's Own ID Field ---
    fun addTopic(name: String, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        val topicsRef = userRef.collection("topics")

        userRef.get().addOnSuccessListener { userDoc ->
            if (!userDoc.exists()) {
                onFailure(Exception("User profile not found in database."))
                return@addOnSuccessListener
            }

            val userId = userDoc.get("id")?.toString()
            if (userId == null) {
                onFailure(Exception("User ID (numeric) is missing from your profile."))
                return@addOnSuccessListener
            }

            val existingCounter = userDoc.getLong("topicCounter")

            if (existingCounter == null) {
                // --- MIGRATION LOGIC FOR OLD ACCOUNTS ---
                // No Toast here to avoid flickering, but we do the work
                topicsRef.get().addOnSuccessListener { result ->
                    val existingNumbers = result.documents.mapNotNull { doc ->
                        val fieldId = doc.get("id")?.toString() ?: doc.id
                        if (fieldId.startsWith("${userId}f")) {
                            fieldId.substringAfter("f").toIntOrNull()
                        } else null
                    }
                    val nextNum = (existingNumbers.maxOrNull() ?: 0) + 1L
                    
                    // Initialize the counter in the DB first
                    userRef.update("topicCounter", nextNum).addOnSuccessListener {
                        executeAddTopicTransaction(name, onDone, onFailure)
                    }.addOnFailureListener { e -> onFailure(e) }
                }.addOnFailureListener { e -> onFailure(e) }
            } else {
                // --- STANDARD LOGIC ---
                executeAddTopicTransaction(name, onDone, onFailure)
            }
        }.addOnFailureListener { e -> onFailure(e) }
    }

    private fun executeAddTopicTransaction(name: String, onDone: () -> Unit, onFailure: (Exception) -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        val topicsRef = userRef.collection("topics")

        db.runTransaction { transaction ->
            val userDoc = transaction.get(userRef)
            val userId = userDoc.get("id")?.toString() ?: throw Exception("Internal Error: User ID is null")
            val nextNumber = userDoc.getLong("topicCounter") ?: 1L

            val newTopicId = "${userId}f$nextNumber"
            transaction.update(userRef, "topicCounter", nextNumber + 1)

            val topicData = hashMapOf(
                "id" to newTopicId,
                "name" to name
            )
            transaction.set(topicsRef.document(), topicData)
            null
        }.addOnSuccessListener {
            loadTopics()
            onDone()
        }.addOnFailureListener { e -> onFailure(e) }
    }

    fun deleteTopic(topicId: String, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return

        val topicsRef = db.collection("users").document(uid).collection("topics")

        topicsRef.whereEqualTo("id", topicId).get()
            .addOnSuccessListener { result ->
                for (doc in result.documents) {
                    doc.reference.delete()
                }
                loadTopics()
                onDone()
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ---------- Flashcards ----------
    fun loadFlashcards(topic: Topic) {
        val uid = auth.currentUser?.uid ?: return
        currentTopic = topic

        db.collection("users").document(uid)
            .collection("topics")
            .whereEqualTo("id", topic.id)
            .get()
            .addOnSuccessListener { topicDocs ->
                val topicDoc = topicDocs.documents.firstOrNull() ?: return@addOnSuccessListener

                topicDoc.reference.collection("flashcards")
                    .get()
                    .addOnSuccessListener { result ->
                        flashcards = result.documents.map { doc ->
                            Flashcard(
                                id = doc.id,
                                question = doc.get("question")?.toString() ?: "",
                                answer = doc.get("answer")?.toString() ?: "",
                                questionImageUrl = doc.get("questionImageUrl")?.toString(),
                                answerImageUrl = doc.get("answerImageUrl")?.toString(),
                                timestamp = doc.getLong("timestamp") ?: 0L
                            )
                        }.sortedByDescending { it.timestamp }
                    }
            }
    }

    fun addFlashcard(question: String, answer: String, questionImageUrl: String?, answerImageUrl: String?, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return

        db.collection("users").document(uid)
            .collection("topics").whereEqualTo("id", topic.id)
            .get()
            .addOnSuccessListener { topicDocs ->
                val topicDoc = topicDocs.documents.firstOrNull() ?: return@addOnSuccessListener

                val flashcard = hashMapOf(
                    "question" to question,
                    "answer" to answer,
                    "questionImageUrl" to questionImageUrl,
                    "answerImageUrl" to answerImageUrl,
                    "timestamp" to System.currentTimeMillis()
                )

                topicDoc.reference.collection("flashcards")
                    .add(flashcard)
                    .addOnSuccessListener {
                        loadFlashcards(topic)
                        onDone()
                    }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    fun deleteFlashcard(flashcardId: String, onDone: () -> Unit = {}, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return

        db.collection("users").document(uid)
            .collection("topics").whereEqualTo("id", topic.id)
            .get()
            .addOnSuccessListener { topicDocs ->
                val topicDoc = topicDocs.documents.firstOrNull() ?: return@addOnSuccessListener

                topicDoc.reference.collection("flashcards")
                    .document(flashcardId)
                    .delete()
                    .addOnSuccessListener {
                        loadFlashcards(topic)
                        onDone()
                    }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ---------- Share Topic ----------
    fun shareTopic(onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val topic = currentTopic ?: return

        val userTopicsRef = db.collection("users").document(uid).collection("topics")

        userTopicsRef.whereEqualTo("id", topic.id).get()
            .addOnSuccessListener { topicDocs ->
                val topicDoc = topicDocs.documents.firstOrNull()
                if (topicDoc == null) {
                    onDone()
                    return@addOnSuccessListener
                }

                val globalRef = db.collection("global_flashcards").document(topic.id)

                // delete existing global flashcards & topic doc if any
                globalRef.collection("flashcards").get()
                    .addOnSuccessListener { oldFlashcards ->
                        val batchDelete = db.batch()
                        for (doc in oldFlashcards.documents) {
                            batchDelete.delete(doc.reference)
                        }
                        batchDelete.delete(globalRef)

                        batchDelete.commit()
                            .addOnSuccessListener {
                                topicDoc.reference.collection("flashcards").get()
                                    .addOnSuccessListener { newFlashcards ->
                                        val newTopicData = mapOf(
                                            "id" to topic.id,
                                            "name" to topic.name,
                                            "name_lowercase" to topic.name.lowercase().trim(),
                                            "sharedBy" to uid,
                                            "timestamp" to System.currentTimeMillis()
                                        )

                                        globalRef.set(newTopicData)
                                            .addOnSuccessListener {
                                                val batchAdd = db.batch()
                                                for (fDoc in newFlashcards.documents) {
                                                    val data = mapOf(
                                                        "question" to (fDoc.get("question")?.toString() ?: ""),
                                                        "answer" to (fDoc.get("answer")?.toString() ?: ""),
                                                        "questionImageUrl" to (fDoc.get("questionImageUrl")?.toString()),
                                                        "answerImageUrl" to (fDoc.get("answerImageUrl")?.toString()),
                                                        "timestamp" to (fDoc.getLong("timestamp") ?: 0L)
                                                    )
                                                    val newFlashRef = globalRef.collection("flashcards").document()
                                                    batchAdd.set(newFlashRef, data)
                                                }

                                                batchAdd.commit()
                                                    .addOnSuccessListener {
                                                        // Write root-level topic_tag name only, avoid duplicates
                                                        val tagData = mapOf(
                                                            "name" to topic.name,
                                                            "name_lowercase" to topic.name.lowercase().trim()
                                                        )
                                                        db.collection("topic_tag")
                                                            .whereEqualTo("name_lowercase", topic.name.lowercase().trim())
                                                            .get()
                                                            .addOnSuccessListener { existing ->
                                                                if (existing.isEmpty) {
                                                                    db.collection("topic_tag").document()
                                                                        .set(tagData)
                                                                        .addOnSuccessListener { onDone() }
                                                                        .addOnFailureListener { e -> onFailure(e) }
                                                                } else {
                                                                    onDone()
                                                                }
                                                            }
                                                            .addOnFailureListener { e -> onFailure(e) }
                                                    }
                                                    .addOnFailureListener { e -> onFailure(e) }
                                            }
                                            .addOnFailureListener { e -> onFailure(e) }
                                    }
                                    .addOnFailureListener { e -> onFailure(e) }
                            }
                            .addOnFailureListener { e -> onFailure(e) }
                    }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }

    // ---------- Importing logic ----------
    fun importGlobalTopicIntoUser(globalTopicId: String, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val uid = auth.currentUser?.uid ?: return
        val userRef = db.collection("users").document(uid)
        val topicsRef = userRef.collection("topics")
        val globalRef = db.collection("global_flashcards").document(globalTopicId)

        globalRef.get().addOnSuccessListener { gDoc ->
            if (!gDoc.exists()) {
                onDone()
                return@addOnSuccessListener
            }
            val globalName = gDoc.get("name")?.toString() ?: ""
            globalRef.collection("flashcards").get()
                .addOnSuccessListener { flashDocs ->
                    userRef.get().addOnSuccessListener { userDoc ->
                        val userId = userDoc.get("id")?.toString() ?: return@addOnSuccessListener

                        topicsRef.get().addOnSuccessListener { existingTopics ->
                            val existingNumbers = existingTopics.documents.mapNotNull { doc ->
                                val fieldId = doc.get("id")?.toString() ?: doc.id
                                if (fieldId.startsWith("${userId}f")) {
                                    fieldId.substringAfter("f").toIntOrNull()
                                } else null
                            }
                            val nextNumber = (existingNumbers.maxOrNull() ?: 0) + 1
                            val newTopicId = "${userId}f$nextNumber"

                            val topicData = mapOf("id" to newTopicId, "name" to globalName)

                            topicsRef.document().set(topicData)
                                .addOnSuccessListener {
                                    // locate created document
                                    topicsRef.whereEqualTo("id", newTopicId).get()
                                        .addOnSuccessListener { newTopicDocs ->
                                            val newTopicDoc = newTopicDocs.documents.firstOrNull()
                                            if (newTopicDoc == null) {
                                                onFailure(Exception("Failed to locate newly created topic document"))
                                                return@addOnSuccessListener
                                            }

                                            val batch = db.batch()
                                            for (fDoc in flashDocs.documents) {
                                                val data = mapOf(
                                                    "question" to (fDoc.get("question")?.toString() ?: ""),
                                                    "answer" to (fDoc.get("answer")?.toString() ?: ""),
                                                    "questionImageUrl" to (fDoc.get("questionImageUrl")?.toString()),
                                                    "answerImageUrl" to (fDoc.get("answerImageUrl")?.toString()),
                                                    "timestamp" to (fDoc.getLong("timestamp") ?: 0L)
                                                )
                                                val newFlashRef = newTopicDoc.reference.collection("flashcards").document()
                                                batch.set(newFlashRef, data)
                                            }

                                            batch.commit()
                                                .addOnSuccessListener {
                                                    loadTopics()
                                                    onDone()
                                                }
                                                .addOnFailureListener { e -> onFailure(e) }
                                        }
                                        .addOnFailureListener { e -> onFailure(e) }
                                }
                                .addOnFailureListener { e -> onFailure(e) }
                        }.addOnFailureListener { e -> onFailure(e) }
                    }.addOnFailureListener { e -> onFailure(e) }
                }
                .addOnFailureListener { e -> onFailure(e) }
        }.addOnFailureListener { e -> onFailure(e) }
    }

    // ---------- Delete global topic (helper) ----------
    fun deleteGlobalTopic(globalTopicId: String, onDone: () -> Unit, onFailure: (Exception) -> Unit = {}) {
        val db = FirebaseFirestore.getInstance()
        val ref = db.collection("global_flashcards").document(globalTopicId)
        ref.collection("flashcards").get()
            .addOnSuccessListener { fs ->
                val batch = db.batch()
                for (fd in fs.documents) batch.delete(fd.reference)
                batch.delete(ref)
                batch.commit()
                    .addOnSuccessListener { onDone() }
                    .addOnFailureListener { e -> onFailure(e) }
            }
            .addOnFailureListener { e -> onFailure(e) }
    }
}

// ----------------------------------------------------------------------------------
// UI
// ----------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel, onBack: () -> Unit) {
    var currentView by remember { mutableStateOf("topics") }
    var shuffledFlashcards by remember { mutableStateOf<List<Flashcard>>(emptyList()) }
    var currentShuffleIndex by remember { mutableStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }

    var correctCount by remember { mutableIntStateOf(0) }
    var wrongCount by remember { mutableIntStateOf(0) }

    var selectedFlashcard by remember { mutableStateOf<Flashcard?>(null) } // New state for detail view

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentView) {
                            "topics" -> "Flashcards"
                            "addTopic" -> "Add Topic"
                            "topicOptions" -> viewModel.currentTopic?.name ?: ""
                            "shuffle" -> viewModel.currentTopic?.name ?: ""
                            "results" -> "Session Results"
                            "list" -> viewModel.currentTopic?.name ?: ""
                            "addFlashcard" -> "Add Flashcard"
                            "import" -> "Import Topics"
                            "detail" -> "Flashcard Detail"
                            else -> ""
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (currentView) {
                            "topics" -> onBack()
                            "addTopic" -> currentView = "topics"
                            "topicOptions" -> currentView = "topics"
                            "topicOptions" -> currentView = "topics"
                            "shuffle" -> currentView = "results"
                            "results" -> currentView = "topics"
                            "list" -> currentView = "topicOptions"
                            "addFlashcard" -> currentView = "topicOptions"
                            "import" -> currentView = "topics"
                            "detail" -> currentView = "topicOptions"
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Show icons only for topic-related screens
                    when (currentView) {
                        "topicOptions", "list" -> {
                            // ADD icon (new)
                            IconButton(onClick = {
                                currentView = "addFlashcard"
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Flashcard")
                            }

                            // Share icon
                            IconButton(onClick = {
                                viewModel.shareTopic(
                                    onDone = {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Topic shared")
                                        }
                                    },
                                    onFailure = { e ->
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Share failed: ${e.message}")
                                        }
                                    }
                                )
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Share Topic")
                            }

                            // Delete icon (topic delete)
                            IconButton(onClick = {
                                viewModel.currentTopic?.id?.let { id ->
                                    viewModel.deleteTopic(id,
                                        onDone = {
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Topic deleted")
                                            }
                                            currentView = "topics"
                                        },
                                        onFailure = { e ->
                                            coroutineScope.launch {
                                                snackbarHostState.showSnackbar("Delete failed: ${e.message}")
                                            }
                                        }
                                    )
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Topic")
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    )  { padding ->
        Box(Modifier.padding(padding)) {
            when (currentView) {
                // --- Topic List ---
                "topics" -> TopicListScreen(
                    viewModel = viewModel,
                    onTopicSelected = {
                        viewModel.loadFlashcards(it)
                        currentView = "topicOptions"
                    },
                    onAddTopic = { currentView = "addTopic" },
                    onImportTopics = { currentView = "import" }
                )

                // --- Add Topic ---
                "addTopic" -> AddTopicScreen(
                    onAdd = { name ->
                        viewModel.addTopic(name,
                            onDone = {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Topic '$name' added")
                                }
                                currentView = "topics"
                            },
                            onFailure = { e ->
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Failed to add topic: ${e.message}")
                                }
                            }
                        )
                    },
                    onCancel = { currentView = "topics" }
                )

                // --- NEW: TopicOptionsScreen (your list + resume design) ---
                "topicOptions" -> TopicOptionsScreen(
                    viewModel = viewModel,
                    onSelectShuffle = {
                        shuffledFlashcards = viewModel.flashcards.shuffled(Random(System.currentTimeMillis()))
                        currentShuffleIndex = 0
                        showAnswer = false
                        correctCount = 0
                        wrongCount = 0
                        currentView = "shuffle"
                    },
                    onFlashcardClick = { card ->
                        selectedFlashcard = card
                        currentView = "detail"
                    },
                    modifier = Modifier
                )

                // --- Flashcard Detail ---
                "detail" -> {
                    selectedFlashcard?.let { flashcard ->
                        FlashcardDetailView(
                            flashcard = flashcard,
                            onBack = { currentView = "topicOptions" }
                        )
                    }
                }


                // --- Add Flashcard (stays the same) ---
                "addFlashcard" -> AddFlashcardScreen(
                    viewModel = viewModel,
                    onDone = {
                        // CRITICAL: Go back to topicOptionsScreen, NOT "list"
                        currentView = "topicOptions"
                    },
                    onCancel = { currentView = "topicOptions" }  // Changed from "list"
                )

                // --- Shuffle ---
                "shuffle" -> {
                    val currentFlashcard = shuffledFlashcards.getOrNull(currentShuffleIndex)
                    if (currentFlashcard != null) {
                        ShuffleFlashcardScreen(
                            flashcard = currentFlashcard,
                            showAnswer = showAnswer,
                            onShowAnswer = { showAnswer = true },
                            onCorrect = {
                                correctCount++
                                if (currentShuffleIndex < shuffledFlashcards.size - 1) {
                                    currentShuffleIndex++
                                    showAnswer = false
                                } else {
                                    currentView = "results"
                                }
                            },
                            onWrong = {
                                wrongCount++
                                if (currentShuffleIndex < shuffledFlashcards.size - 1) {
                                    currentShuffleIndex++
                                    showAnswer = false
                                } else {
                                    currentView = "results"
                                }
                            },
                            onEndSession = { currentView = "results" }
                        )
                    } else {
                        // Should technically not happen unless list empty, but safe fallback
                        currentView = "topicOptions"
                    }
                }

                // --- Results ---
                "results" -> {
                    SessionResultsScreen(
                        correct = correctCount,
                        wrong = wrongCount,
                        total = correctCount + wrongCount,
                        onOk = { currentView = "topicOptions" }
                    )
                }

                // --- List View ---
                "list" -> FlashcardListWithDeleteScreen(
                    viewModel = viewModel,
                    onBack = { currentView = "topicOptions" },
                    onAddFlashcard = { currentView = "addFlashcard" }
                )

                // --- Add Flashcard ---
                "addFlashcard" -> AddFlashcardScreen(
                    viewModel = viewModel,
                    onDone = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Flashcard added")
                        }
                        currentView = "list"
                    },
                    onCancel = { currentView = "list" }
                )

                // --- Import Topics ---
                "import" -> ImportTopicsScreen(
                    viewModel = viewModel,
                    onBack = { currentView = "topics" },
                    onImportSuccess = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Topic imported")
                        }
                        viewModel.loadTopics()
                    },
                    onGlobalDeleteSuccess = {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Global topic deleted")
                        }
                    }
                )
            }
        }
    }
}

// --- Topic List Screen (scrollable list + fixed bottom buttons) ---
@Composable
fun TopicListScreen(
    viewModel: FlashcardViewModel,
    onTopicSelected: (Topic) -> Unit,
    onAddTopic: () -> Unit,
    onImportTopics: () -> Unit
) {
    LaunchedEffect(Unit) { viewModel.loadTopics() }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "Your Topics",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 24.dp, top = 24.dp)
        )

        Spacer(Modifier.height(12.dp))

        // Scrollable list area, takes available space
        Box(modifier = Modifier.weight(1f).padding(horizontal = 24.dp)) {
            if (viewModel.topics.isEmpty()) {
                Text("No topics yet.", modifier = Modifier.align(Alignment.TopStart))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(viewModel.topics) { topic ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clickable { onTopicSelected(topic) },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Box(Modifier.padding(16.dp)) {
                                Text(topic.name, fontSize = 20.sp)
                            }
                        }
                    }
                }
            }
        }

        // Fixed bottom buttons
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Button(onClick = onAddTopic, modifier = Modifier.fillMaxWidth()) {
                Text("Add New Topic")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onImportTopics, modifier = Modifier.fillMaxWidth()) {
                Text("Import Topics")
            }
        }
    }
}

// --- Add Topic Screen ---
@Composable
fun AddTopicScreen(onAdd: (String) -> Unit, onCancel: () -> Unit) {
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Add New Topic", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Topic Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { onAdd(name) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save Topic") }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

// --- Import Topics Screen ---
@Composable
fun ImportTopicsScreen(
    viewModel: FlashcardViewModel,
    onBack: () -> Unit,
    onImportSuccess: () -> Unit,
    onGlobalDeleteSuccess: () -> Unit
) {
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) } // names
    var results by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) } // Pair(name, globalDocId)
    var showSuggestions by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

    // realtime prefix search for suggestions (max 3)
    LaunchedEffect(query.text) {
        if (!showSuggestions || query.text.isBlank()) { 
            if (query.text.isBlank()) suggestions = emptyList()
            return@LaunchedEffect 
        }
        val termLower = query.text.lowercase().trim()
        val start = termLower
        val end = termLower + '\uf8ff'
        db.collection("topic_tag")
            .whereGreaterThanOrEqualTo("name_lowercase", start)
            .whereLessThanOrEqualTo("name_lowercase", end)
            .limit(10)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { it.get("name")?.toString() }
                suggestions = list.distinct()
            }
            .addOnFailureListener { suggestions = emptyList() }
    }

    // Helper to run search
    fun searchTopics(searchText: String) {
        showSuggestions = false
        if (searchText.isBlank()) { results = emptyList(); return }
        loading = true
        val termLower = searchText.trim().lowercase()
        val start = termLower
        val end = termLower + '\uf8ff'

        db.collection("topic_tag")
            .whereGreaterThanOrEqualTo("name_lowercase", start)
            .whereLessThanOrEqualTo("name_lowercase", end)
            .get()
            .addOnSuccessListener { snap ->
                val namesLower = snap.documents.mapNotNull { it.get("name_lowercase")?.toString() }.distinct()
                if (namesLower.isEmpty()) {
                    results = emptyList(); loading = false; return@addOnSuccessListener
                }
                val chunks = namesLower.chunked(10)
                val tmpResults = mutableListOf<Pair<String, String>>()
                var processed = 0
                for (chunk in chunks) {
                    db.collection("global_flashcards")
                        .whereIn("name_lowercase", chunk)
                        .get()
                        .addOnSuccessListener { gSnap ->
                            for (gDoc in gSnap.documents) {
                                val gname = gDoc.get("name")?.toString() ?: ""
                                val gid = gDoc.id
                                tmpResults.add(Pair(gname, gid))
                            }
                            processed++
                            if (processed == chunks.size) {
                                results = tmpResults
                                loading = false
                            }
                        }
                        .addOnFailureListener {
                            processed++
                            if (processed == chunks.size) {
                                results = tmpResults
                                loading = false
                            }
                        }
                }
            }
            .addOnFailureListener {
                results = emptyList(); loading = false
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { 
                query = it
                showSuggestions = true 
            },
            label = { Text("Search topics") },
            modifier = Modifier.fillMaxWidth()
        )

        if (showSuggestions && suggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 144.dp)
                ) {
                    items(suggestions) { name ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .clickable {
                                    val newText = "$name "
                                    query = TextFieldValue(newText, TextRange(newText.length))
                                    showSuggestions = false
                                    suggestions = emptyList()
                                    // Trigger search immediately on the selected name
                                    searchTopics(name)
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(name)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Button(onClick = {
            searchTopics(query.text)
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Search")
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Text("Loading...", modifier = Modifier.padding(8.dp))
        }

        // Results scrollable area
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(results) { (name, gid) ->
                var count by remember { mutableStateOf<Int?>(null) }
                var sharedBy by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(gid) {
                    val docRef = db.collection("global_flashcards").document(gid)
                    docRef.get()
                        .addOnSuccessListener { doc -> sharedBy = doc.get("sharedBy")?.toString() }
                        .addOnFailureListener { sharedBy = null }
                    docRef.collection("flashcards").get()
                        .addOnSuccessListener { fSnap -> count = fSnap.size() }
                        .addOnFailureListener { count = 0 }
                }

                Card(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                    elevation = CardDefaults.cardElevation(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(4.dp))
                            Text("ID: $gid", fontSize = 12.sp)
                            Spacer(Modifier.height(4.dp))
                            Text("Flashcards: ${count ?: "..."}", fontSize = 12.sp)
                        }

                        // If this topic was shared by the current user: show both delete and add
                        if (sharedBy != null && sharedBy == currentUid) {
                            IconButton(onClick = {
                                viewModel.deleteGlobalTopic(gid, onDone = {
                                    // locally remove from results
                                    results = results.filterNot { it.second == gid }
                                    onGlobalDeleteSuccess()
                                }, onFailure = {
                                    // ignore or show error snackbar (hosted by caller)
                                })
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete global topic")
                            }
                            IconButton(onClick = {
                                viewModel.importGlobalTopicIntoUser(gid, onDone = {
                                    onImportSuccess()
                                }, onFailure = {
                                    // optional error handling
                                })
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Import")
                            }
                        } else {
                            IconButton(onClick = {
                                viewModel.importGlobalTopicIntoUser(gid, onDone = {
                                    onImportSuccess()
                                }, onFailure = {
                                    // optional error
                                })
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Import")
                            }
                        }
                    }
                }
            }
        }
    }
}

// --- Topic Options ---
@Composable
fun TopicOptionsScreen(
    viewModel: FlashcardViewModel,
    onSelectShuffle: () -> Unit,
    onFlashcardClick: (Flashcard) -> Unit,
    modifier: Modifier = Modifier
) {
    val flashcards = viewModel.flashcards

    Column(
        modifier = modifier
            .fillMaxSize()          // comes from Box(padding) in Scaffold
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),     // LEFT–RIGHT padding here
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(flashcards) { card ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onFlashcardClick(card) },
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Q: ${card.question}",
                                fontWeight = FontWeight.Bold,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Answer removed from list view
                        }
                        IconButton(onClick = { viewModel.deleteFlashcard(card.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Flashcard",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Button(
            onClick = onSelectShuffle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp)  // LEFT–RIGHT padding for button
        ) {
            Text("Shuffle")
        }
    }
}



// --- Shuffle Flashcard Screen ---
// --- Shuffle Flashcard Screen ---
// --- Shuffle Flashcard Screen ---
@Composable
fun ShuffleFlashcardScreen(
    flashcard: Flashcard,
    showAnswer: Boolean,
    onShowAnswer: () -> Unit,
    onCorrect: () -> Unit,
    onWrong: () -> Unit,
    onEndSession: () -> Unit
) {
    val questionScroll = rememberScrollState()
    val answerScroll = rememberScrollState()
    var viewingImage by remember { mutableStateOf<String?>(null) }

    if (viewingImage != null) {
        Dialog(onDismissRequest = { viewingImage = null }) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            val state = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceAtLeast(1f)
                offset += panChange
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { viewingImage = null }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .transformable(state = state)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = viewingImage,
                        contentDescription = "Full Image",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
                IconButton(
                    onClick = { viewingImage = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text("Question:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // Give weight to question/answer areas
                .border(1.dp, MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium)
                .padding(8.dp)
                .verticalScroll(questionScroll)
        ) {
            Text(
                text = flashcard.question,
                style = MaterialTheme.typography.bodyLarge
            )
        }

        if (!flashcard.questionImageUrl.isNullOrBlank()) {
            Spacer(Modifier.height(4.dp))
            Button(onClick = { viewingImage = flashcard.questionImageUrl }, modifier = Modifier.fillMaxWidth(0.5f)) {
                Text("View Image")
            }
        }

        Spacer(Modifier.height(16.dp))

        if (showAnswer) {
            Text("Answer:", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(6.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .border(1.dp, MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.medium)
                    .padding(8.dp)
                    .verticalScroll(answerScroll)
            ) {
                Text(
                    text = flashcard.answer,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            if (!flashcard.answerImageUrl.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Button(onClick = { viewingImage = flashcard.answerImageUrl }, modifier = Modifier.fillMaxWidth(0.5f)) {
                    Text("View Image")
                }
            }
        } else {
             // Placeholder logic to keep layout stable or just empty space
             Spacer(modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(24.dp))

        if (!showAnswer) {
            Button(onClick = onShowAnswer, modifier = Modifier.fillMaxWidth()) {
                Text("Show Answer")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) { Text("End Session") }
        } else {
            // Buttons: X (Red) and Check (Green)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onWrong,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Wrong", tint = Color.White)
                }

                Button(
                    onClick = onCorrect,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.Check, contentDescription = "Correct", tint = Color.White)
                }
            }
            Spacer(Modifier.height(8.dp))
            // "End Session" during answer phase
            OutlinedButton(onClick = onEndSession, modifier = Modifier.fillMaxWidth()) { Text("End Session") }
        }
    }
}

@Composable
fun SessionResultsScreen(
    correct: Int,
    wrong: Int,
    total: Int,
    onOk: () -> Unit
) {
    val accuracy = if (total > 0) (correct.toFloat() / total.toFloat()) * 100 else 0f

    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Session Complete!", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                ResultRow(label = "Attempted", value = "$total")
                ResultRow(label = "Correct", value = "$correct", color = Color(0xFF4CAF50)) // Green
                ResultRow(label = "Wrong", value = "$wrong", color = Color(0xFFF44336))   // Red
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Accuracy", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "%.1f%%".format(accuracy),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onOk,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("OK", fontSize = 18.sp)
        }
    }
}

@Composable
fun ResultRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 18.sp)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
    }
}



// --- List View with Delete ---
@Composable
fun FlashcardListWithDeleteScreen(
    viewModel: FlashcardViewModel,
    onBack: () -> Unit,
    onAddFlashcard: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp)
    ) {
        Text(
            "Flashcards in ${viewModel.currentTopic?.name}",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(viewModel.flashcards) { card ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {

                        // Text column takes remaining space, avoiding overlap
                        Column(
                            modifier = Modifier
                                .weight(1f)               // ← ensures text expands but avoids icon area
                        ) {
                            Text(
                                "Q: ${card.question}",
                                fontWeight = FontWeight.Bold
                            )
                            Text("A: ${card.answer}")
                        }

                        // Delete button stays on the right side with spacing
                        IconButton(onClick = { viewModel.deleteFlashcard(card.id) }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Flashcard",
                                tint = MaterialTheme.colorScheme.primary

                            )
                        }
                    }
                }
            }
        }


        Spacer(Modifier.height(16.dp))

        Button(onClick = onAddFlashcard, modifier = Modifier.fillMaxWidth()) { Text("Add New Flashcard") }
        Spacer(Modifier.height(8.dp))
    }
}

// --- Add Flashcard Screen ---
// --- Cloudinary Helper Object ---
object CloudinaryHelper {
    private var isInit = false
    fun init(context: Context) {
        if (isInit) return
        try {
            MediaManager.get()
            isInit = true
        } catch (e: Exception) {
            // REPLACE WITH YOUR CREDENTIALS
            val config = HashMap<String, Any>()
            config["cloud_name"] = "daq8i0mep" // e.g. "demo"
            config["secure"] = true
            MediaManager.init(context, config)
            isInit = true
        }
    }

    fun upload(uri: Uri, context: Context, onSuccess: (String) -> Unit, onError: (String) -> Unit) {
        init(context)
        MediaManager.get().upload(uri)
            .unsigned("ml_default") // e.g. "unsigned_preset"
            .callback(object : UploadCallback {
                override fun onStart(requestId: String?) {}
                override fun onProgress(requestId: String?, bytes: Long, totalBytes: Long) {}
                override fun onSuccess(requestId: String?, resultData: MutableMap<Any?, Any?>?) {
                    val url = resultData?.get("secure_url")?.toString() ?: ""
                    onSuccess(url)
                }
                override fun onError(requestId: String?, error: ErrorInfo?) {
                    onError(error?.description ?: "Upload Error")
                }
                override fun onReschedule(requestId: String?, error: ErrorInfo?) {}
            })
            .dispatch()
    }
}

// --- Add Flashcard Screen ---
@Composable
fun AddFlashcardScreen(viewModel: FlashcardViewModel, onDone: () -> Unit, onCancel: () -> Unit) {
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var questionImageUri by remember { mutableStateOf<Uri?>(null) }
    var answerImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Image Pickers
    val questionImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> questionImageUri = uri }

    val answerImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> answerImageUri = uri }

    Scaffold(
        bottomBar = {
            // Fixed bottom buttons
            Column(modifier = Modifier.padding(16.dp)) {
                // Show "Uploading images..." ONLY if we are uploading and have images selected
                if (isUploading && (questionImageUri != null || answerImageUri != null)) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Uploading images...", modifier = Modifier.align(Alignment.CenterHorizontally))
                    Spacer(Modifier.height(8.dp))
                } else if (isUploading) {
                     // Just saving text without images, optional: "Saving..."
                     LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                     Spacer(Modifier.height(8.dp))
                     Text("Saving...", modifier = Modifier.align(Alignment.CenterHorizontally))
                     Spacer(Modifier.height(8.dp))
                }
                
                Button(
                    onClick = {
                        isUploading = true
                        var qUrl: String? = null
                        var aUrl: String? = null
                        var uploadCount = 0
                        val totalUploads = (if (questionImageUri != null) 1 else 0) + (if (answerImageUri != null) 1 else 0)

                        fun checkDone() {
                            if (uploadCount >= totalUploads) {
                                viewModel.addFlashcard(question, answer, qUrl, aUrl, onDone = {
                                    isUploading = false
                                    onDone()
                                }, onFailure = {
                                    isUploading = false
                                })
                            }
                        }

                        if (totalUploads == 0) {
                            checkDone()
                        } else {
                            if (questionImageUri != null) {
                                CloudinaryHelper.upload(questionImageUri!!, context, onSuccess = { url ->
                                    qUrl = url
                                    uploadCount++
                                    checkDone()
                                }, onError = {
                                    uploadCount++ // skip on error or handle?
                                    checkDone()
                                })
                            }
                            if (answerImageUri != null) {
                                CloudinaryHelper.upload(answerImageUri!!, context, onSuccess = { url ->
                                    aUrl = url
                                    uploadCount++
                                    checkDone()
                                }, onError = {
                                    uploadCount++
                                    checkDone()
                                })
                            }
                        }
                    },
                    enabled = question.isNotBlank() && answer.isNotBlank() && !isUploading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Save Flashcard") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Add Flashcard", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // QUESTION INPUT
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Question") },
                modifier = Modifier.fillMaxWidth().height(120.dp), // Fixed height ~3-4 lines
                maxLines = 10, // Allow scrolling internally if needed, but height is fixed
                singleLine = false
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { 
                questionImageLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                ) 
            }) {
                Text(if (questionImageUri == null) "Add Question Image" else "Change Question Image")
            }
            if (questionImageUri != null) {
                Text("Image selected", fontSize = 12.sp, color = Color.Gray)
            }

            Spacer(Modifier.height(24.dp))

            // ANSWER INPUT
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it },
                label = { Text("Answer") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 10,
                singleLine = false
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = { 
                answerImageLauncher.launch(
                    androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                ) 
            }) {
                Text(if (answerImageUri == null) "Add Answer Image" else "Change Answer Image")
            }
            if (answerImageUri != null) {
                Text("Image selected", fontSize = 12.sp, color = Color.Gray)
            }
            
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
fun FlashcardDetailView(
    flashcard: Flashcard,
    onBack: () -> Unit
) {
    var viewingImage by remember { mutableStateOf<String?>(null) }

    // Image Viewer Dialog
    if (viewingImage != null) {
        Dialog(onDismissRequest = { viewingImage = null }) {
            var scale by remember { mutableStateOf(1f) }
            var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            val state = rememberTransformableState { zoomChange, panChange, _ ->
                scale = (scale * zoomChange).coerceAtLeast(1f)
                offset += panChange
            }

            Box(
                Modifier
                    .fillMaxSize()
                    .clickable { viewingImage = null }
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .transformable(state = state)
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = viewingImage,
                        contentDescription = "Full Image",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.Fit
                    )
                }
                IconButton(
                    onClick = { viewingImage = null },
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Question", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .border(1.dp, MaterialTheme.colorScheme.primary, shape = MaterialTheme.shapes.medium)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(flashcard.question, fontSize = 18.sp)
        }

        if (!flashcard.questionImageUrl.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewingImage = flashcard.questionImageUrl }) {
                Text("View Image")
            }
        }

        Spacer(Modifier.height(24.dp))

        Text("Answer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .border(1.dp, MaterialTheme.colorScheme.secondary, shape = MaterialTheme.shapes.medium)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(flashcard.answer, fontSize = 18.sp)
        }

        if (!flashcard.answerImageUrl.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = { viewingImage = flashcard.answerImageUrl }) {
                Text("View Image")
            }
        }

        Spacer(Modifier.height(32.dp))

        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to List")
        }
    }
}

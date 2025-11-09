package com.example.flashcard

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// --- Routes ---
private const val LOGIN_ROUTE = "login_screen"
private const val SIGNUP_ROUTE = "signup_screen"
private const val MAIN_ROUTE = "main_app"
private const val TIMER_ROUTE = "timer_screen"
private const val FLASHCARD_ROUTE = "flashcard_screen"
private const val PROFILE_ROUTE = "profile_screen"
private const val SCHEDULER_ROUTE = "scheduler_screen"
private const val QNA_ROUTE = "qna_screen" // Add with other routes
public const val PUBLISH_QUESTION_ROUTE = "publish_question_screen"


// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                AppNavigation()
            }
        }
    }
}

// --- ViewModel Factory ---
class LoginViewModelFactory(private val sessionManager: SessionManager) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(sessionManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- Navigation ---
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    val loginViewModel: LoginViewModel =
        viewModel(factory = LoginViewModelFactory(sessionManager))
    val flashcardViewModel: FlashcardViewModel = viewModel()
    val timerModel: TimerModel = viewModel()
    val startDestination = if (sessionManager.isLoggedIn()) MAIN_ROUTE else LOGIN_ROUTE

    NavHost(navController = navController, startDestination = startDestination) {

        // --- Login Screen ---
        composable(LOGIN_ROUTE) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    sessionManager.saveSession(FirebaseAuth.getInstance().currentUser?.uid ?: "")
                    navController.navigate(MAIN_ROUTE) {
                        popUpTo(LOGIN_ROUTE) { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    navController.navigate(SIGNUP_ROUTE)
                }
            )
        }

        // --- Signup Screen ---
        composable(SIGNUP_ROUTE) {
            SignupScreenIntegrated { name, email, password ->
                registerUser(context, name, email, password) {
                    sessionManager.saveSession(FirebaseAuth.getInstance().currentUser?.uid ?: "")
                    navController.navigate(MAIN_ROUTE) {
                        popUpTo(SIGNUP_ROUTE) { inclusive = true }
                    }
                }
            }
        }

        // --- Main Screen ---
        composable(MAIN_ROUTE) {
            MainScreen(
                onNavigateToTimer = { navController.navigate(TIMER_ROUTE) },
                onNavigateToFlashcard = { navController.navigate(FLASHCARD_ROUTE) },
                onNavigateToProfile = { navController.navigate(PROFILE_ROUTE) },
                onNavigateToScheduler = { navController.navigate(SCHEDULER_ROUTE) },
                onNavigateToQnA = { navController.navigate(QNA_ROUTE) },
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    sessionManager.clearSession()
                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                    navController.navigate(LOGIN_ROUTE) {
                        popUpTo(MAIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // --- Timer Screen ---
        composable(TIMER_ROUTE) {
            TimerScreen(
                viewModel = timerModel,
                onBack = { navController.popBackStack() },
                context = context
            )
        }

        // --- Flashcard Screen ---
        composable(FLASHCARD_ROUTE) {
            FlashcardScreen(viewModel = flashcardViewModel, onBack = { navController.popBackStack() })
        }
        // --- Flashcard Topic Flow ---
        composable("topicDetail/{topic}") { backStackEntry ->
            val topic = backStackEntry.arguments?.getString("topic") ?: ""
            TopicDetailScreen(navController, topic)
        }

        composable("listView/{topic}") { backStackEntry ->
            val topic = backStackEntry.arguments?.getString("topic") ?: ""
            ListViewScreen(navController, topic)
        }

//        composable("flashcardDetail/{topic}/{id}") { backStackEntry ->
//            val topic = backStackEntry.arguments?.getString("topic") ?: ""
//            val id = backStackEntry.arguments?.getString("id") ?: ""
//            FlashcardDetailScreen(navController, topic, id)
//        }

        composable("flashcardDetail/{topicId}/{flashcardId}") { backStackEntry ->
            val topicId = backStackEntry.arguments?.getString("topicId") ?: ""
            val flashcardId = backStackEntry.arguments?.getString("flashcardId") ?: ""
            FlashcardDetailScreen(navController, topicId, flashcardId)
        }

        // --- Profile Screen ---
        composable(PROFILE_ROUTE) {
            ProfileScreen(onBack = { navController.popBackStack() })
        }

        // --- Scheduler Screen ---
        composable(SCHEDULER_ROUTE) {
            SchedulerScreen(onBack = { navController.popBackStack() })
        }
        composable(QNA_ROUTE) {
            QnAScreen(parentNavController = navController)
        }


        composable(PUBLISH_QUESTION_ROUTE) {
            PublishQuestionScreen(onBack = { navController.popBackStack() })
        }

    }
}

// --- MainScreen with DND Permission Prompt ---
@Composable
fun MainScreen(
    onNavigateToTimer: () -> Unit,
    onNavigateToFlashcard: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToScheduler: () -> Unit,
    onNavigateToQnA: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    // DND Permission Check
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    var showDndDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showDndDialog = true
        }
    }

    // Show AlertDialog to request DND permission
    if (showDndDialog) {
        AlertDialog(
            onDismissRequest = { showDndDialog = false },
            title = { Text("Permission Required") },
            text = { Text("To enable Focus timer, please allow access to Do Not Disturb mode.") },
            confirmButton = {
                TextButton(onClick = {
                    showDndDialog = false
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    context.startActivity(intent)
                }) {
                    Text("Grant Access")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDndDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Main Menu", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        Button(onClick = onNavigateToProfile, modifier = Modifier.fillMaxWidth()) {
            Text("View Profile")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNavigateToTimer, modifier = Modifier.fillMaxWidth()) {
            Text("Timer")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNavigateToFlashcard, modifier = Modifier.fillMaxWidth()) {
            Text("Flashcards")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNavigateToScheduler, modifier = Modifier.fillMaxWidth()) {
            Text("Scheduler")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNavigateToQnA, modifier = Modifier.fillMaxWidth()) {
            Text("QnA")
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }
    }
}

// --- Profile Screen ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val userId = FirebaseAuth.getInstance().currentUser?.uid
    var username by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf<String?>(null) }
    var id by remember { mutableStateOf<Number?>(null) }

    LaunchedEffect(userId) {
        if (userId != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(userId).get()
                .addOnSuccessListener { snapshot ->
                    username = snapshot.getString("name")
                    email = snapshot.getString("email")
                    id = snapshot.getLong("id")

                }
                .addOnFailureListener {
                    Toast.makeText(context, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        TopAppBar(
            title = { Text("User Profile") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Text("Name: ${username ?: "Loading..."}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text("Email: ${email ?: "Loading..."}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(8.dp))
            Text("ID: ${id ?: "Loading..."}", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(20.dp))
        }
    }
}


// --- Signup Screen Integrated ---
@Composable
fun SignupScreenIntegrated(onSignup: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = {
                if (name.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                    onSignup(name.trim(), email.trim(), password.trim())
                } else {
                    Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Sign Up")
        }
    }
}

// --- Firebase Signup Function ---
fun registerUser(
    context: Context,
    name: String,
    email: String,
    password: String,
    onSuccess: () -> Unit
) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    auth.createUserWithEmailAndPassword(email, password)
        .addOnSuccessListener { result ->
            val firebaseUser = result.user ?: run {
                Toast.makeText(context, "Firebase user is null", Toast.LENGTH_LONG).show()
                return@addOnSuccessListener
            }

            val counterRef = db.collection("counters").document("users")
            val userRef = db.collection("users").document(firebaseUser.uid)

            db.runTransaction { transaction ->
                val snapshot = transaction.get(counterRef)
                val currentCount = snapshot.getLong("userCount") ?: 9999
                val newId = currentCount + 1

                transaction.set(counterRef, mapOf("userCount" to newId))
                transaction.set(userRef, mapOf("id" to newId, "name" to name, "email" to email))
                newId
            }.addOnSuccessListener {
                Toast.makeText(context, "Signup success!", Toast.LENGTH_LONG).show()
                onSuccess()
            }.addOnFailureListener { e ->
                Toast.makeText(context, "Transaction failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
}

package com.example.flashcard

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import androidx.compose.material.icons.filled.*
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.ui.graphics.vector.ImageVector

// --------------------------
//        ROUTES
// --------------------------
private const val SPLASH_ROUTE = "splash"
private const val INTRO_ROUTE = "intro"

private const val LOGIN_ROUTE = "login_screen"
private const val SIGNUP_ROUTE = "signup_screen"
private const val MAIN_ROUTE = "main_app"
private const val TIMER_ROUTE = "timer_screen"
private const val FLASHCARD_ROUTE = "flashcard_screen"
private const val PROFILE_ROUTE = "profile_screen"
private const val SCHEDULER_ROUTE = "scheduler_screen"
private const val QNA_ROUTE = "qna_screen"
const val PUBLISH_QUESTION_ROUTE = "publish_question_screen"

// --------------------------
//       MainActivity
// --------------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        createNotificationChannel()

        setContent {
            MaterialTheme {
                AppNavigationWithNotifications()
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "scheduler_channel",
                "Scheduler Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

// --------------------------
//   ViewModel Factory
// --------------------------
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

// --------------------------
// Navigation Graph
// --------------------------
@Composable
fun AppNavigationWithNotifications() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val sessionManager = remember { SessionManager(context) }

    val loginViewModel: LoginViewModel =
        viewModel(factory = LoginViewModelFactory(sessionManager))
    val flashcardViewModel: FlashcardViewModel = viewModel()
    val timerModel: TimerModel = viewModel()

    val startDestination = SPLASH_ROUTE

    // Background Notification Checker
    LaunchedEffect(Unit) { startNotificationChecker(context) }

    NavHost(navController = navController, startDestination = startDestination) {

        // --- Splash ---
        composable(SPLASH_ROUTE) {
            SplashScreen(navController = navController, sessionManager = sessionManager)
        }

        // --- Intro ---
        composable(INTRO_ROUTE) {
            IntroScreen(navController)
        }

        // --- Login ---
        composable(LOGIN_ROUTE) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    sessionManager.saveSession(FirebaseAuth.getInstance().currentUser?.uid ?: "")
                    navController.navigate(MAIN_ROUTE) {
                        popUpTo(LOGIN_ROUTE) { inclusive = true }
                    }
                },
                onNavigateToSignup = { navController.navigate(SIGNUP_ROUTE) }
            )
        }

        composable(SIGNUP_ROUTE) {
            SignupScreen(
                onSignup = { name, email, password ->
                    registerUser(context, name, email, password) {
                        sessionManager.saveSession(FirebaseAuth.getInstance().currentUser?.uid ?: "")
                        navController.navigate(MAIN_ROUTE) {
                            popUpTo(SIGNUP_ROUTE) { inclusive = true }
                        }
                    }
                },
                onNavigateToLogin = {
                    navController.navigate(LOGIN_ROUTE) {
                        popUpTo(SIGNUP_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // --- Main App Screen ---
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

        // --- Timer ---
        composable(TIMER_ROUTE) {
            TimerScreen(
                viewModel = timerModel,
                onBack = { navController.popBackStack() },
                context = context
            )
        }

        // --- Flashcard ---
        composable(FLASHCARD_ROUTE) {
            FlashcardScreen(viewModel = flashcardViewModel, onBack = { navController.popBackStack() })
        }

        composable("topicDetail/{topic}") { entry ->
            TopicDetailScreen(navController, entry.arguments?.getString("topic") ?: "")
        }
        composable("listView/{topic}") { entry ->
            ListViewScreen(navController, entry.arguments?.getString("topic") ?: "")
        }
        composable("flashcardDetail/{topicId}/{flashcardId}") { entry ->
            FlashcardDetailScreen(
                navController,
                entry.arguments?.getString("topicId") ?: "",
                entry.arguments?.getString("flashcardId") ?: ""
            )
        }

        // --- Profile, Scheduler, QnA ---
        composable(PROFILE_ROUTE) { ProfileScreen(onBack = { navController.popBackStack() }) }
        composable(SCHEDULER_ROUTE) { SchedulerScreen(onBack = { navController.popBackStack() }) }
        composable(QNA_ROUTE) { QnAScreen(parentNavController = navController) }
        composable(PUBLISH_QUESTION_ROUTE) { PublishQuestionScreen(onBack = { navController.popBackStack() }) }
    }
}

// --------------------------
//     Splash Screen
// --------------------------
@Composable
fun SplashScreen(navController: NavController, sessionManager: SessionManager) {

    LaunchedEffect(Unit) {
        delay(2000)

        if (sessionManager.isLoggedIn()) {
            navController.navigate(MAIN_ROUTE) {
                popUpTo(SPLASH_ROUTE) { inclusive = true }
            }
        } else {
            navController.navigate(INTRO_ROUTE) {
                popUpTo(SPLASH_ROUTE) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "App Logo",
            modifier = Modifier.size(250.dp)
        )
    }
}

// --------------------------
//     Intro Screen
// --------------------------

@Composable
fun IntroScreen(navController: NavController) {

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // App Name
        Text(
            text = "Study Buddy",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Description
        Text(
            text = "Learn Smarter. Study Better. Achieve More.\n\n" +
                    "Features include:\n" +
                    "• Flashcards: Memorize and revise concepts.\n" +
                    "• Scheduler: Plan your study sessions.\n" +
                    "• Timer: Focus with Pomodoro-style timers.\n" +
                    "• Q&A: Ask questions and get answers.",

            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Sign Up Button (same color as Log In)
        Button(
            onClick = { navController.navigate(SIGNUP_ROUTE) },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text(
                text = "Sign Up",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Log In Button (same color, only pressed effect)
        Button(
            onClick = { navController.navigate(LOGIN_ROUTE) },
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Text(
                text = "Log In",
                style = MaterialTheme.typography.titleMedium
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}



// -------------------------------------------------------------
// Existing Screens — unchanged except routing updated
// -------------------------------------------------------------



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
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    var showDndDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            showDndDialog = true
        }
    }

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
                }) { Text("Grant Access") }
            },
            dismissButton = {
                TextButton(onClick = { showDndDialog = false }) { Text("Cancel") }
            }
        )
    }

    val buttonModifier = Modifier
        .fillMaxWidth()
        .height(55.dp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        // App Title
        Text(
            text = "Study Buddy",
            style = MaterialTheme.typography.displaySmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Your Personal Learning Companion",
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        )
        Spacer(Modifier.height(32.dp))

        // Main buttons with icons
        MainButton("View Profile", Icons.Default.Person, onNavigateToProfile, buttonModifier)
        Spacer(Modifier.height(12.dp))
        MainButton("Timer", Icons.Default.Timer, onNavigateToTimer, buttonModifier)
        Spacer(Modifier.height(12.dp))
        MainButton("Flashcards", Icons.Default.MenuBook, onNavigateToFlashcard, buttonModifier)
        Spacer(Modifier.height(12.dp))
        MainButton("Scheduler", Icons.Default.CalendarToday, onNavigateToScheduler, buttonModifier)
        Spacer(Modifier.height(12.dp))
        MainButton("Q&A", Icons.Default.QuestionAnswer, onNavigateToQnA, buttonModifier)
        Spacer(Modifier.height(24.dp))

        // Logout button
        OutlinedButton(
            onClick = onLogout,
            modifier = buttonModifier,
            shape = MaterialTheme.shapes.extraLarge,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            border = ButtonDefaults.outlinedButtonBorder
        ) {
            Icon(Icons.Default.Logout, contentDescription = "Logout", modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Text("Logout")
        }
    }
}

@Composable
fun MainButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Icon(icon, contentDescription = text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}




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
            FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .get()
                .addOnSuccessListener { snapshot ->
                    username = snapshot.getString("name")
                    email = snapshot.getString("email")
                    id = snapshot.getLong("id")
                }.addOnFailureListener {
                    Toast.makeText(context, "Failed to fetch user data", Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        TopAppBar(
            title = { Text("User Profile") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        )

        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Text("Name: ${username ?: "Loading..."}")
            Spacer(Modifier.height(8.dp))
            Text("Email: ${email ?: "Loading..."}")
            Spacer(Modifier.height(8.dp))
            Text("ID: ${id ?: "Loading..."}")
            Spacer(Modifier.height(20.dp))
        }
    }
}
// ------------------------------
// Firebase Registration
// ------------------------------
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

            val firebaseUser = result.user ?: return@addOnSuccessListener

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
            }.addOnFailureListener {
                Toast.makeText(context, "Transaction failed", Toast.LENGTH_LONG).show()
            }

        }.addOnFailureListener { e ->
            Toast.makeText(context, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
}

// ------------------------------
// Background Notification Checker
// ------------------------------
fun startNotificationChecker(context: Context) {
    val db = FirebaseFirestore.getInstance()
    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            val calendar = Calendar.getInstance()
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)
            val todayStr = "today"

            try {
                val snapshot = db.collection("users")
                    .document(userId).collection("events")
                    .whereEqualTo("date", todayStr)
                    .get().await()

                snapshot.documents.forEach { doc ->
                    val data = doc.data ?: return@forEach
                    val eventHour = (data["hour"] as? Long)?.toInt() ?: 0
                    val eventMinute = (data["minute"] as? Long)?.toInt() ?: 0
                    val title = data["title"] as? String ?: "Event"
                    val desc = data["description"] as? String ?: ""

                    if (eventHour == hour && eventMinute == minute) {

                        val builder = androidx.core.app.NotificationCompat.Builder(
                            context, "scheduler_channel"
                        )
                            .setSmallIcon(android.R.drawable.ic_dialog_info)
                            .setContentTitle(title)
                            .setContentText(desc)
                            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                            .setAutoCancel(true)

                        notificationManager.notify(doc.id.hashCode(), builder.build())
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            delay(60000)
        }
    }
}

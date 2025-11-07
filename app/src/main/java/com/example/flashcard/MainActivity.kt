package com.example.flashcard

import android.content.Context
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
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
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// --- Routes ---
private const val WELCOME_ROUTE = "welcome_screen"
private const val LOGIN_ROUTE = "login_screen"
private const val SIGNUP_ROUTE = "signup_screen"
private const val MAIN_ROUTE = "main_app"
private const val TIMER_ROUTE = "timer_screen"
private const val FLASHCARD_ROUTE = "flashcard_screen"

// --- MainActivity ---
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
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

    val startDestination = if (sessionManager.isLoggedIn()) MAIN_ROUTE else WELCOME_ROUTE

    NavHost(navController = navController, startDestination = startDestination) {

        // Welcome Screen
        composable(WELCOME_ROUTE) {
            WelcomeScreenNew(
                onNavigateToLogin = { navController.navigate(LOGIN_ROUTE) },
                onNavigateToSignup = { navController.navigate(SIGNUP_ROUTE) }
            )
        }

        // Login Screen
        composable(LOGIN_ROUTE) {
            LoginScreen(
                viewModel = loginViewModel,
                onLoginSuccess = {
                    navController.navigate(MAIN_ROUTE) {
                        popUpTo(WELCOME_ROUTE) { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    navController.navigate(SIGNUP_ROUTE) {
                        popUpTo(LOGIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // Signup Screen
        composable(SIGNUP_ROUTE) {
            SignupScreenIntegrated { name, email, password ->
                registerUser(context, name, email, password) {
                    navController.navigate(MAIN_ROUTE) {
                        popUpTo(WELCOME_ROUTE) { inclusive = true }
                    }
                }
            }
        }

        // Main App Screen
        composable(MAIN_ROUTE) {
            MainScreen(
                onNavigateToTimer = { navController.navigate(TIMER_ROUTE) },
                onNavigateToFlashcard = { navController.navigate(FLASHCARD_ROUTE) },
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                    navController.navigate(WELCOME_ROUTE) {
                        popUpTo(MAIN_ROUTE) { inclusive = true }
                    }
                }
            )
        }

        // Timer Screen
        composable(TIMER_ROUTE) {
            TimerScreen(viewModel = timerModel, onBack = { navController.popBackStack() })
        }

        // Flashcard Screen
        composable(FLASHCARD_ROUTE) {
            FlashcardScreen(viewModel = flashcardViewModel, onBack = { navController.popBackStack() })
        }
    }
}

// --- Main App Screen ---
@Composable
fun MainScreen(
    onNavigateToTimer: () -> Unit,
    onNavigateToFlashcard: () -> Unit,
    onLogout: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("You are logged in!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = onNavigateToTimer, modifier = Modifier.fillMaxWidth()) {
            Text("Go to Timer")
        }
        Spacer(Modifier.height(12.dp))
        Button(onClick = onNavigateToFlashcard, modifier = Modifier.fillMaxWidth()) {
            Text("Go to Flashcards")
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }
    }
}

// --- Welcome Screen (renamed) ---
@Composable
fun WelcomeScreenNew(onNavigateToLogin: () -> Unit, onNavigateToSignup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Welcome to Study Buddy!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onNavigateToLogin, modifier = Modifier.fillMaxWidth()) {
            Text("Login")
        }
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onNavigateToSignup, modifier = Modifier.fillMaxWidth()) {
            Text("Sign Up")
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
fun registerUser(context: Context, name: String, email: String, password: String, onSuccess: () -> Unit) {
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()
    val TAG = "SignupDebug"

    auth.createUserWithEmailAndPassword(email, password)
        .addOnSuccessListener { result ->
            val firebaseUser = result.user
            if (firebaseUser == null) {
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
            }.addOnSuccessListener { newId ->
                Toast.makeText(context, "Signup success! ID = $newId", Toast.LENGTH_LONG).show()
                onSuccess()
            }.addOnFailureListener { e ->
                Toast.makeText(context, "Transaction failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
        .addOnFailureListener { e ->
            Toast.makeText(context, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
}

package com.example.flashcard

import android.os.Bundle
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
import androidx.navigation.compose.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

// ------------------- Routes -------------------
private const val WELCOME_ROUTE = "welcome"
private const val LOGIN_ROUTE = "login"
private const val SIGNUP_ROUTE = "signup"
private const val MAIN_ROUTE = "main"

// ------------------- MainActivity -------------------
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
}

// ------------------- Navigation -------------------
@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController, startDestination = WELCOME_ROUTE) {
        composable(WELCOME_ROUTE) {
            WelcomeScreenNew(
                onNavigateToLogin = { navController.navigate(LOGIN_ROUTE) },
                onNavigateToSignup = { navController.navigate(SIGNUP_ROUTE) }
            )
        }

        composable(LOGIN_ROUTE) {
            LoginScreen(onLoginSuccess = {
                navController.navigate(MAIN_ROUTE) { popUpTo(WELCOME_ROUTE) { inclusive = true } }
            })
        }

        composable(SIGNUP_ROUTE) {
            SignupScreen(onSignupSuccess = {
                navController.navigate(MAIN_ROUTE) { popUpTo(WELCOME_ROUTE) { inclusive = true } }
            })
        }

        composable(MAIN_ROUTE) {
            MainScreen()
        }
    }
}

// ------------------- Welcome Screen -------------------
@Composable
fun WelcomeScreenNew(onNavigateToLogin: () -> Unit, onNavigateToSignup: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome!", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onNavigateToLogin) { Text("Login") }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = onNavigateToSignup) { Text("Sign Up") }
    }
}

// ------------------- Login Screen -------------------
@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (email.isNotBlank() && password.isNotBlank()) {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        Toast.makeText(context, "Login Success!", Toast.LENGTH_SHORT).show()
                        onLoginSuccess()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Login Failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Login") }
    }
}

// ------------------- Signup Screen -------------------
@Composable
fun SignupScreen(onSignupSuccess: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val db = FirebaseFirestore.getInstance()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text("Email") })
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (name.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { result ->
                        val user = result.user ?: return@addOnSuccessListener
                        val counterRef = db.collection("counters").document("users")
                        val userRef = db.collection("users").document(user.uid)

                        db.runTransaction { tx ->
                            val snapshot = tx.get(counterRef)
                            val currentCount = snapshot.getLong("userCount") ?: 9999
                            val newId = currentCount + 1

                            // Update counter
                            tx.set(counterRef, mapOf("userCount" to newId))
                            // Add user
                            tx.set(userRef, mapOf("id" to newId, "name" to name, "email" to email))
                        }.addOnSuccessListener {
                            Toast.makeText(context, "Signup Success!", Toast.LENGTH_SHORT).show()
                            onSignupSuccess()
                        }.addOnFailureListener { e ->
                            Toast.makeText(context, "Transaction failed: ${e.message}", Toast.LENGTH_LONG).show()
                        }

                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(context, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            } else {
                Toast.makeText(context, "Fill all fields", Toast.LENGTH_SHORT).show()
            }
        }) { Text("Sign Up") }
    }
}

// ------------------- Main Screen -------------------
@Composable
fun MainScreen() {
    val context = LocalContext.current
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("You are logged in!", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            FirebaseAuth.getInstance().signOut()
            Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
        }) { Text("Logout") }
    }
}

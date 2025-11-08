package com.example.flashcard

import android.os.Bundle
import android.util.Log
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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class SignupActivity : ComponentActivity() {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SignupScreen { name, email, password ->
                registerUser(name, email, password)
            }
        }
    }

    private fun registerUser(name: String, email: String, password: String) {
        val TAG = "signupDebug"
        val testRef = db.collection("users").document("test_user")
        testRef.set(mapOf("name" to "Test", "email" to "test@test.com"))
            .addOnSuccessListener { Log.d("SignupDebug", "Test write success") }
            .addOnFailureListener { e -> Log.e("SignupDebug", "Test write failed: ${e.message}") }

        android.util.Log.d(TAG, "registerUser() called with name=$name, email=$email")

        auth.createUserWithEmailAndPassword(email, password)
            .addOnSuccessListener { result ->
                val firebaseUser = result.user
                if (firebaseUser == null) {
                    android.util.Log.e(TAG, "Firebase user is null after signup")
                    Toast.makeText(this, "Error: Firebase user is null", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                val counterRef = db.collection("counters").document("users")
                val userRef = db.collection("users").document(firebaseUser.uid)

                android.util.Log.d(TAG, "Starting Firestore transaction...")

                db.runTransaction { t ->
                    val snapshot = t.get(counterRef)
                    val currentCount = snapshot.getLong("userCount") ?: 9999
                    val newId = currentCount + 1

                    android.util.Log.d(TAG, "Current count: $currentCount → New ID: $newId")

                    // Update counter document (create if missing)
                    t.set(counterRef, mapOf("userCount" to newId))

                    // Create user document
                    val userData = mapOf(
                        "id" to newId,
                        "name" to name,
                        "email" to email
                    )
                    t.set(userRef, userData)

                    newId
                }
                    .addOnSuccessListener { newId ->
                        android.util.Log.d(TAG, "Transaction success: newId=$newId")
                        Toast.makeText(this, "Signup success! Your user ID = $newId", Toast.LENGTH_LONG).show()
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e(TAG, "Transaction failed: ${e.message}", e)
                        Toast.makeText(this, "Transaction failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }

            }
            .addOnFailureListener { e ->
                android.util.Log.e(TAG, "Signup failed: ${e.message}", e)
                Toast.makeText(this, "Signup failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignupScreen(onSignup: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val context = LocalContext.current // ✅ Only used inside Composable

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") }
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") }
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            visualTransformation = PasswordVisualTransformation()
        )
        Spacer(Modifier.height(20.dp))

        val context = LocalContext.current

        Button(
            onClick = {
                android.util.Log.d("SignupDebug", "Sign Up button pressed")
                Toast.makeText(context, "Sign Up pressed", Toast.LENGTH_SHORT).show()

                if (name.isNotBlank() && email.isNotBlank() && password.isNotBlank()) {
                    onSignup(name.trim(), email.trim(), password.trim())
                } else {
                    Toast.makeText(context, "Please fill all fields", Toast.LENGTH_SHORT).show()
                }
            }
        ) {
            Text("Sign Up")
        }

    }
}

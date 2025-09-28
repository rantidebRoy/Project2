package com.example.flashcard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Authentication states for UI
 */
enum class AuthState {
    IDLE,
    LOADING,
    SUCCESS,
    ERROR
}

/**
 * ViewModel for handling authentication logic (Login, Sign Up, Logout)
 */
class LoginViewModel(private val sessionManager: SessionManager) : ViewModel() {

    // Firebase Auth instance
    private val auth: FirebaseAuth = Firebase.auth

    // --- UI State Variables ---
    var email by mutableStateOf("")
    var password by mutableStateOf("")

    var authState by mutableStateOf(AuthState.IDLE)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Button state derived properties
    val isLoading: Boolean
        get() = authState == AuthState.LOADING

    val isInputValid: Boolean
        get() = email.isNotBlank() && password.isNotBlank()

    // --- Authentication Functions ---

    fun login() {
        if (!isInputValid) {
            errorMessage = "Email and password cannot be empty."
            return
        }

        authState = AuthState.LOADING
        errorMessage = null

        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(email, password).await()
                result.user?.uid?.let { userId ->
                    sessionManager.saveSession(userId)
                }
                authState = AuthState.SUCCESS
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Login failed. Please check your credentials."
                authState = AuthState.ERROR
            }
        }
    }

    fun signUp() {
        if (!isInputValid) {
            errorMessage = "Email and password cannot be empty."
            return
        }

        authState = AuthState.LOADING
        errorMessage = null

        viewModelScope.launch {
            try {
                val result = auth.createUserWithEmailAndPassword(email, password).await()
                result.user?.uid?.let { userId ->
                    sessionManager.saveSession(userId)
                }
                authState = AuthState.SUCCESS
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Sign up failed. Please try again."
                authState = AuthState.ERROR
            }
        }
    }

    fun logout() {
        auth.signOut()
        sessionManager.clearSession()
        resetState()
    }

    fun resetState() {
        authState = AuthState.IDLE
        errorMessage = null
    }
}

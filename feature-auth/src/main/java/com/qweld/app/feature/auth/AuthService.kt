package com.qweld.app.feature.auth

import kotlinx.coroutines.flow.Flow

interface AuthService {
  data class User(
    val uid: String,
    val email: String?,
    val isAnonymous: Boolean,
  )

  val currentUser: Flow<User?>

  suspend fun signInAnonymously(): User

  suspend fun signInWithGoogle(idToken: String): User

  suspend fun signInWithEmail(email: String, password: String): User

  suspend fun linkAnonymousToGoogle(idToken: String): User
}

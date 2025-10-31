package com.qweld.app.feature.auth.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.qweld.app.data.analytics.Analytics
import com.qweld.app.data.analytics.logAuthLink
import com.qweld.app.data.analytics.logAuthSignIn
import com.qweld.app.data.analytics.logAuthSignOut
import com.qweld.app.feature.auth.AuthService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import timber.log.Timber

class FirebaseAuthService(
  private val firebaseAuth: FirebaseAuth,
  private val analytics: Analytics,
  private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AuthService {

  override val currentUser: Flow<AuthService.User?> =
    callbackFlow {
      val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser?.toUser()).isSuccess
      }
      firebaseAuth.addAuthStateListener(listener)
      trySend(firebaseAuth.currentUser?.toUser()).isSuccess
      awaitClose { firebaseAuth.removeAuthStateListener(listener) }
    }.distinctUntilChanged()

  override suspend fun signInAnonymously(): AuthService.User =
    performSignIn(provider = "anonymous") {
      firebaseAuth.signInAnonymously().await().user.requireUser()
    }

  override suspend fun signInWithGoogle(idToken: String): AuthService.User =
    performSignIn(provider = "google") {
      val credential = GoogleAuthProvider.getCredential(idToken, null)
      firebaseAuth.signInWithCredential(credential).await().user.requireUser()
    }

  override suspend fun signInWithEmail(email: String, password: String): AuthService.User =
    performSignIn(provider = "password") {
      firebaseAuth.signInWithEmailAndPassword(email, password).await().user.requireUser()
    }

  override suspend fun linkAnonymousToGoogle(idToken: String): AuthService.User {
    val current = firebaseAuth.currentUser ?: error("No authenticated user to link.")
    if (!current.isAnonymous) {
      return current.toUser()
    }
    val credential = GoogleAuthProvider.getCredential(idToken, null)
    return performLink(method = "google") {
      current.linkWithCredential(credential).await().user.requireUser()
    }
  }

  override suspend fun signOut() {
    withContext(dispatcher) {
      val previous = firebaseAuth.currentUser
      firebaseAuth.signOut()
      val uidLabel = previous?.uid ?: "anon"
      Timber.i("[auth_signout] ok=true uid=%s", uidLabel)
      analytics.logAuthSignOut("app_menu")
    }
  }

  private suspend fun performSignIn(
    provider: String,
    block: suspend () -> FirebaseUser,
  ): AuthService.User =
    withContext(dispatcher) {
      try {
        val user = block()
        Timber.i("[auth_signin] provider=%s uid=%s", provider, user.uid)
        analytics.logAuthSignIn(provider)
        user.toUser()
      } catch (exception: Exception) {
        logError(exception)
        throw exception
      }
    }

  private suspend fun performLink(
    method: String,
    block: suspend () -> FirebaseUser,
  ): AuthService.User =
    withContext(dispatcher) {
      try {
        val user = block()
        Timber.i("[auth_link] anon->uid=%s", user.uid)
        analytics.logAuthLink(method)
        user.toUser()
      } catch (exception: Exception) {
        logError(exception)
        throw exception
      }
    }

  private fun FirebaseUser?.requireUser(): FirebaseUser =
    this ?: error("Firebase returned null user.")

  private fun FirebaseUser.toUser(): AuthService.User =
    AuthService.User(
      uid = uid,
      email = email,
      isAnonymous = isAnonymous,
    )

  private fun logError(throwable: Exception) {
    val code =
      when (throwable) {
        is FirebaseAuthException -> throwable.errorCode
        else -> throwable.javaClass.simpleName
      }
    Timber.e(throwable, "[auth_error] code=%s message=%s", code, throwable.message)
  }
}

package com.qweld.app.feature.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeAuthServiceTest {
  private val service = FakeAuthService()

  @Test
  fun guest_sign_in_emits_anonymous_user() = runTest {
    val user = service.signInAnonymously()

    assertTrue(user.isAnonymous)
    assertEquals(user, service.currentUser.filterNotNull().first())
  }

  @Test
  fun google_and_email_sign_in_replace_user() = runTest {
    service.signInAnonymously()

    val googleUser = service.signInWithGoogle("token123")
    assertFalse(googleUser.isAnonymous)
    assertEquals("token123@google.test", googleUser.email)

    val emailUser = service.signInWithEmail("welder@qweld.app", "secret")
    assertFalse(emailUser.isAnonymous)
    assertEquals("welder@qweld.app", emailUser.email)
  }

  @Test
  fun link_anonymous_to_google_promotes_account() = runTest {
    val anonymous = service.signInAnonymously()
    assertTrue(anonymous.isAnonymous)

    val linked = service.linkAnonymousToGoogle("linkedToken")

    assertFalse(linked.isAnonymous)
    assertEquals("linkedToken@google.test", linked.email)
    assertEquals(linked, service.currentUser.filterNotNull().first())
  }

  @Test
  fun sign_out_clears_current_user() = runTest {
    service.signInAnonymously()

    service.signOut()

    assertEquals(null, service.currentUser.first())
  }
}

private class FakeAuthService : AuthService {
  private var nextId = 0
  private val state = MutableStateFlow<AuthService.User?>(null)

  override val currentUser: MutableStateFlow<AuthService.User?> = state

  override suspend fun signInAnonymously(): AuthService.User {
    val user = AuthService.User(uid = "guest-${nextId++}", email = null, isAnonymous = true)
    state.value = user
    return user
  }

  override suspend fun signInWithGoogle(idToken: String): AuthService.User {
    val user = AuthService.User(uid = "google-${nextId++}", email = "$idToken@google.test", isAnonymous = false)
    state.value = user
    return user
  }

  override suspend fun signInWithEmail(email: String, password: String): AuthService.User {
    val user = AuthService.User(uid = "email-${nextId++}", email = email, isAnonymous = false)
    state.value = user
    return user
  }

  override suspend fun linkAnonymousToGoogle(idToken: String): AuthService.User {
    val user = AuthService.User(uid = "google-${nextId++}", email = "$idToken@google.test", isAnonymous = false)
    state.value = user
    return user
  }

  override suspend fun signOut() {
    state.value = null
  }
}

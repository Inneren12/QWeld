package com.qweld.app.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qweld.app.data.db.QWELD_DB_VERSION
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptStats
import com.qweld.app.data.repo.AttemptsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AdminDashboardViewModel(
  private val attemptsRepository: AttemptsRepository,
  private val answersRepository: AnswersRepository,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AdminDashboardUiState())
  val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    viewModelScope.launch {
      _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
      try {
        val data = withContext(ioDispatcher) { loadData() }
        _uiState.value = AdminDashboardUiState(data = data, isLoading = false, errorMessage = null)
      } catch (exception: Exception) {
        Timber.e(exception, "[admin_dashboard_load_error]")
        _uiState.value = _uiState.value.copy(
          isLoading = false,
          errorMessage = exception.localizedMessage ?: "Unable to load admin data",
        )
      }
    }
  }

  private suspend fun loadData(): AdminDashboardData {
    val attemptStats = attemptsRepository.getStats()
    val answerCount = answersRepository.countAll()
    val userVersion = attemptsRepository.getUserVersion()

    return AdminDashboardData(
      attemptStats = attemptStats,
      answerCount = answerCount,
      dbHealth = DbHealth(
        userVersion = userVersion,
        expectedVersion = QWELD_DB_VERSION,
      ),
    )
  }
}

data class DbHealth(
  val userVersion: Int,
  val expectedVersion: Int,
) {
  val isAtExpectedVersion: Boolean
    get() = userVersion == expectedVersion
}

data class AdminDashboardData(
  val attemptStats: AttemptStats,
  val answerCount: Int,
  val dbHealth: DbHealth,
)

data class AdminDashboardUiState(
  val data: AdminDashboardData? = null,
  val isLoading: Boolean = true,
  val errorMessage: String? = null,
)

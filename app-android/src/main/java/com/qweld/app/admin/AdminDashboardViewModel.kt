package com.qweld.app.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qweld.app.data.db.QWELD_DB_VERSION
import com.qweld.app.data.repo.AnswersRepository
import com.qweld.app.data.repo.AttemptStats
import com.qweld.app.data.repo.AttemptsRepository
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.data.reports.QuestionReportSummary
import com.qweld.app.data.reports.QuestionReportWithId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

class AdminDashboardViewModel(
  private val attemptsRepository: AttemptsRepository,
  private val answersRepository: AnswersRepository,
  private val questionReportRepository: QuestionReportRepository,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val _uiState = MutableStateFlow(AdminDashboardUiState())
  val uiState: StateFlow<AdminDashboardUiState> = _uiState.asStateFlow()

  init {
    refresh()
  }

  fun refresh() {
    loadDashboardStats()
    loadReportSummaries()
  }

  private fun loadDashboardStats() {
    viewModelScope.launch {
      _uiState.update { it.copy(isLoading = true, errorMessage = null) }
      try {
        val data = withContext(ioDispatcher) { loadData() }
        _uiState.update { it.copy(data = data, isLoading = false, errorMessage = null) }
      } catch (exception: Exception) {
        Timber.e(exception, "[admin_dashboard_load_error]")
        _uiState.update {
          it.copy(
            isLoading = false,
            errorMessage = exception.localizedMessage ?: "Unable to load admin data",
          )
        }
      }
    }
  }

  fun loadReportSummaries() {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          reports = it.reports.copy(
            isLoading = true,
            errorMessage = null,
          ),
        )
      }
      try {
        val summaries = withContext(ioDispatcher) { questionReportRepository.listReportSummaries() }
        _uiState.update {
          val shouldKeepSelection = it.reports.selectedQuestionId?.let { id -> summaries.any { summary -> summary.questionId == id } } ?: false
          it.copy(
            reports = it.reports.copy(
              isLoading = false,
              summaries = summaries,
              errorMessage = null,
              selectedQuestionId = if (shouldKeepSelection) it.reports.selectedQuestionId else null,
              selectedReports = if (shouldKeepSelection) it.reports.selectedReports else emptyList(),
              detailError = null,
            ),
          )
        }
      } catch (exception: Exception) {
        Timber.e(exception, "[admin_dashboard_reports_error]")
        _uiState.update {
          it.copy(
            reports = it.reports.copy(
              isLoading = false,
              errorMessage = exception.localizedMessage ?: "Unable to load question reports",
            ),
          )
        }
      }
    }
  }

  fun loadReportsForQuestion(questionId: String) {
    viewModelScope.launch {
      _uiState.update {
        it.copy(
          reports = it.reports.copy(
            selectedQuestionId = questionId,
            isDetailLoading = true,
            detailError = null,
            selectedReports = emptyList(),
          ),
        )
      }
      try {
        val reports = withContext(ioDispatcher) {
          questionReportRepository.listReportsForQuestion(questionId = questionId)
        }
        _uiState.update {
          it.copy(
            reports = it.reports.copy(
              selectedQuestionId = questionId,
              isDetailLoading = false,
              selectedReports = reports,
              detailError = null,
            ),
          )
        }
      } catch (exception: Exception) {
        Timber.e(exception, "[admin_dashboard_reports_detail_error] question=$questionId")
        _uiState.update {
          it.copy(
            reports = it.reports.copy(
              selectedQuestionId = questionId,
              isDetailLoading = false,
              detailError = exception.localizedMessage ?: "Failed to load report details",
            ),
          )
        }
      }
    }
  }

  fun clearSelectedQuestion() {
    _uiState.update {
      it.copy(
        reports = it.reports.copy(
          selectedQuestionId = null,
          selectedReports = emptyList(),
          isDetailLoading = false,
          detailError = null,
        ),
      )
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

data class QuestionReportsDashboardState(
  val summaries: List<QuestionReportSummary> = emptyList(),
  val isLoading: Boolean = false,
  val errorMessage: String? = null,
  val selectedQuestionId: String? = null,
  val selectedReports: List<QuestionReportWithId> = emptyList(),
  val isDetailLoading: Boolean = false,
  val detailError: String? = null,
)

data class AdminDashboardUiState(
  val data: AdminDashboardData? = null,
  val isLoading: Boolean = true,
  val errorMessage: String? = null,
  val reports: QuestionReportsDashboardState = QuestionReportsDashboardState(),
)

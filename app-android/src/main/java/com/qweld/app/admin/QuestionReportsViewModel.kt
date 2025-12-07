package com.qweld.app.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.qweld.app.data.reports.QuestionReportRepository
import com.qweld.app.data.reports.QuestionReportWithId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the admin question reports list screen.
 * Handles loading, filtering, and displaying question reports.
 */
class QuestionReportsViewModel(
  private val repository: QuestionReportRepository
) : ViewModel() {

  private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
  val uiState: StateFlow<UiState> = _uiState.asStateFlow()

  private val _selectedStatusFilter = MutableStateFlow<String?>(null)
  val selectedStatusFilter: StateFlow<String?> = _selectedStatusFilter.asStateFlow()

  init {
    loadReports()
  }

  fun loadReports(statusFilter: String? = _selectedStatusFilter.value) {
    viewModelScope.launch {
      try {
        _uiState.value = UiState.Loading
        val reports = repository.listReports(status = statusFilter, limit = 100)
        _uiState.value = if (reports.isEmpty()) {
          UiState.Empty
        } else {
          UiState.Success(reports)
        }
        Timber.d("[admin_reports] loaded=${reports.size} filter=$statusFilter")
      } catch (e: Exception) {
        Timber.e(e, "[admin_reports_error] filter=$statusFilter")
        _uiState.value = UiState.Error(e.message ?: "Failed to load reports")
      }
    }
  }

  fun setStatusFilter(status: String?) {
    _selectedStatusFilter.value = status
    loadReports(status)
  }

  fun refresh() {
    loadReports()
  }

  sealed class UiState {
    object Loading : UiState()
    object Empty : UiState()
    data class Success(val reports: List<QuestionReportWithId>) : UiState()
    data class Error(val message: String) : UiState()
  }
}

/**
 * ViewModel for the admin question report detail screen.
 * Handles loading a single report and updating its status.
 */
class QuestionReportDetailViewModel(
  private val repository: QuestionReportRepository,
  private val reportId: String
) : ViewModel() {

  private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
  val uiState: StateFlow<UiState> = _uiState.asStateFlow()

  init {
    loadReport()
  }

  fun loadReport() {
    viewModelScope.launch {
      try {
        _uiState.value = UiState.Loading
        val report = repository.getReportById(reportId)
        _uiState.value = if (report != null) {
          UiState.Success(report)
        } else {
          UiState.Error("Report not found")
        }
      } catch (e: Exception) {
        Timber.e(e, "[admin_report_detail_error] id=$reportId")
        _uiState.value = UiState.Error(e.message ?: "Failed to load report")
      }
    }
  }

  fun updateStatus(newStatus: String, resolutionCode: String?, resolutionComment: String?) {
    viewModelScope.launch {
      try {
        val currentState = _uiState.value
        if (currentState !is UiState.Success) return@launch

        val review = buildReviewMap(resolutionCode, resolutionComment)
        repository.updateReportStatus(reportId, newStatus, review)

        Timber.d("[admin_report_update] id=$reportId status=$newStatus")

        // Reload to get updated data
        loadReport()
      } catch (e: Exception) {
        Timber.e(e, "[admin_report_update_error] id=$reportId")
        _uiState.value = UiState.Error(e.message ?: "Failed to update status")
      }
    }
  }

  private fun buildReviewMap(resolutionCode: String?, resolutionComment: String?): Map<String, Any?>? {
    // Return null if both are blank (no review data to update)
    if (resolutionCode.isNullOrBlank() && resolutionComment.isNullOrBlank()) {
      return null
    }

    val reviewMap = mutableMapOf<String, Any?>()

    if (!resolutionCode.isNullOrBlank()) {
      reviewMap["resolutionCode"] = resolutionCode
    }

    if (!resolutionComment.isNullOrBlank()) {
      reviewMap["resolutionComment"] = resolutionComment
    }

    // Note: resolvedAt is automatically set by the repository when status is RESOLVED or WONT_FIX
    return reviewMap
  }

  sealed class UiState {
    object Loading : UiState()
    data class Success(val report: QuestionReportWithId) : UiState()
    data class Error(val message: String) : UiState()
  }
}

/**
 * Formats a Firestore Timestamp to a readable date/time string.
 */
fun Timestamp?.formatDateTime(): String {
  if (this == null) return "N/A"
  val date = this.toDate()
  val formatter = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
  return formatter.format(date)
}

/**
 * Formats a Firestore Timestamp to a short date string.
 */
fun Timestamp?.formatDate(): String {
  if (this == null) return "N/A"
  val date = this.toDate()
  val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
  return formatter.format(date)
}

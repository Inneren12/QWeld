package com.qweld.app.feature.exam.vm

import dagger.hilt.android.scopes.ActivityRetainedScoped
import javax.inject.Inject

@ActivityRetainedScoped
class ExamResultHolder @Inject constructor() {

  private var latestResult: ExamViewModel.ExamResultData? = null

  fun update(result: ExamViewModel.ExamResultData) {
    latestResult = result
  }

  fun clear() {
    latestResult = null
  }

  fun requireLatest(): ExamViewModel.ExamResultData {
    return checkNotNull(latestResult) { "Result is not available" }
  }
}

package com.qweld.app.feature.exam.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi

object Io {
  @OptIn(ExperimentalCoroutinesApi::class)
  val pool = Dispatchers.IO.limitedParallelism(2)
}

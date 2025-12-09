package com.qweld.app.feature.exam.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.qweld.app.domain.exam.TimerController
import com.qweld.app.feature.exam.R
import com.qweld.app.feature.exam.model.ExamUiState
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExamTimerLifecycleTest {
  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  private val dispatcher = StandardTestDispatcher()
  private lateinit var viewModel: TimerHarnessViewModel
  private lateinit var updateFactory: (TimerHarnessFactory) -> Unit
  private lateinit var updateKey: (Int) -> Unit
  private var currentKey = 0

  @Before
  fun setUp() {
    Dispatchers.setMain(dispatcher)
    composeTestRule.setContent {
      MaterialTheme(colorScheme = lightColorScheme()) {
        var factory by remember { mutableStateOf(defaultFactory()) }
        var key by remember { mutableStateOf(0) }
        updateFactory = { factory = it }
        updateKey = { key = it }
        viewModel = viewModel(key = "timer-$key", factory = factory)
        TimerHarnessScreen(state = viewModel.uiState)
      }
    }
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun timerContinuesWhenBackgrounded() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    val resumedLabel = context.getString(R.string.exam_timer_label, "04:00:00")
    composeTestRule.onNodeWithText(resumedLabel).assertIsDisplayed()

    composeTestRule.runOnIdle { viewModel.advance(Duration.ofSeconds(5)) }
    composeTestRule.onNodeWithText(context.getString(R.string.exam_timer_label, "03:59:55"))
      .assertIsDisplayed()

    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
    composeTestRule.runOnIdle { viewModel.advance(Duration.ofSeconds(5)) }
    composeTestRule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

    composeTestRule.onNodeWithText(context.getString(R.string.exam_timer_label, "03:59:50"))
      .assertIsDisplayed()
  }

  @Test
  fun timerSurvivesConfigurationChange() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    composeTestRule.runOnIdle { viewModel.advance(Duration.ofSeconds(3)) }
    composeTestRule.onNodeWithText(context.getString(R.string.exam_timer_label, "03:59:57"))
      .assertIsDisplayed()

    composeTestRule.activityRule.scenario.recreate()

    composeTestRule.onNodeWithText(context.getString(R.string.exam_timer_label, "03:59:57"))
      .assertIsDisplayed()

    composeTestRule.runOnIdle { viewModel.advance(Duration.ofSeconds(3)) }
    composeTestRule.onNodeWithText(context.getString(R.string.exam_timer_label, "03:59:54"))
      .assertIsDisplayed()
  }

  @Test
  fun timerRestoresFromSavedRemaining() {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    composeTestRule.runOnIdle { viewModel.advance(Duration.ofSeconds(120)) }
    val remaining = composeTestRule.runOnIdle { viewModel.currentRemaining() }

    composeTestRule.runOnIdle {
      currentKey += 1
      updateFactory.invoke(defaultFactory(initialRemaining = remaining))
      updateKey.invoke(currentKey)
    }

    composeTestRule.onNodeWithText(context.getString(R.string.exam_timer_label, "03:58:00"))
      .assertIsDisplayed()

    composeTestRule.runOnIdle { viewModel.advance(Duration.ofSeconds(5)) }
    composeTestRule.onNodeWithText(context.getString(R.string.exam_timer_label, "03:57:55"))
      .assertIsDisplayed()
  }

  private fun defaultFactory(initialRemaining: Duration? = null): TimerHarnessFactory {
    return TimerHarnessFactory(dispatcher, MutableClock(), initialRemaining)
  }
}

private class TimerHarnessFactory(
  private val dispatcher: TestDispatcher,
  private val clock: MutableClock,
  private val initialRemaining: Duration? = null,
) : ViewModelProvider.Factory {
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    return TimerHarnessViewModel(dispatcher, clock, initialRemaining) as T
  }
}

private class TimerHarnessViewModel(
  private val dispatcher: TestDispatcher,
  private val clock: MutableClock,
  private val initialRemaining: Duration?,
) : ViewModel() {
  private val timerController = TimerController(clock) { }
  private val _uiState = MutableStateFlow(ExamUiState(timerLabel = null))
  val uiState: StateFlow<ExamUiState> = _uiState

  private var manualRemaining: Duration? = null

  init {
    if (initialRemaining != null) {
      resumeTimer(initialRemaining)
    } else {
      startTimer()
    }
  }

  fun advance(duration: Duration) {
    clock.advance(duration)
    dispatcher.scheduler.advanceTimeBy(duration.toMillis())
  }

  fun currentRemaining(): Duration {
    return manualRemaining ?: timerController.remaining()
  }

  private fun startTimer() {
    timerController.start()
    updateLabel(TimerController.formatDuration(TimerController.EXAM_DURATION))
    launchTicker { timerController.remaining() }
  }

  private fun resumeTimer(initialRemaining: Duration) {
    manualRemaining = initialRemaining
    updateLabel(TimerController.formatDuration(initialRemaining))
    launchTicker {
      checkNotNull(manualRemaining) { "manualRemaining should be set for resumed timer" }
    }
  }

  private fun launchTicker(remainingProvider: () -> Duration): Job {
    return viewModelScope.launch(dispatcher) {
      var remaining = remainingProvider()
      while (true) {
        delay(1_000)
        remaining =
          manualRemaining?.minusSeconds(1)
            ?: remainingProvider().coerceAtLeast(Duration.ZERO)
        if (remaining.isNegative) remaining = Duration.ZERO
        manualRemaining = if (manualRemaining != null) remaining else null
        updateLabel(TimerController.formatDuration(remaining))
        if (remaining.isZero) break
      }
    }
  }

  private fun updateLabel(label: String) {
    _uiState.value = _uiState.value.copy(timerLabel = label)
  }
}

@Composable
private fun TimerHarnessScreen(state: StateFlow<ExamUiState>) {
  val uiState = state.value
  val context = LocalContext.current
  uiState.timerLabel?.let { label ->
    TimerLabel(text = context.getString(R.string.exam_timer_label, label))
  }
}

@Composable
private fun TimerLabel(text: String) {
  androidx.compose.material3.Text(text = text, modifier = Modifier.testTag("timerLabel"))
}

private class MutableClock(
  private var now: Instant = Instant.EPOCH,
  private val zoneId: ZoneId = ZoneId.systemDefault(),
) : Clock() {
  override fun getZone(): ZoneId = zoneId

  override fun withZone(zone: ZoneId): Clock = MutableClock(now, zone)

  override fun instant(): Instant = now

  fun advance(duration: Duration) {
    now = now.plus(duration)
  }
}

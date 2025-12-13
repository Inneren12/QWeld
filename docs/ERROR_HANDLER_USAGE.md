# Error Handler Usage Guide

## Overview

The centralized error handler provides a consistent way to handle non-fatal errors across the QWeld app. It routes errors to logging (Timber) and Crashlytics, with hooks for future UI integration.

## Architecture

### Core Components

- **`AppError`** - Sealed class representing different error categories
- **`ErrorContext`** - Contextual information about where/when errors occur
- **`AppErrorHandler`** - Interface for error handling
- **`AppErrorHandlerImpl`** - Implementation in `core-data`
- **`UiErrorEvent`** - Events for UI feedback (future dialogs/snackbars)

### Error Categories

1. **Unexpected** - Bugs or unforeseen issues
2. **Network** - Connectivity, timeouts, API failures
3. **Reporting** - Question/content reporting failures
4. **Persistence** - Room, DataStore errors
5. **ContentLoad** - Asset, blueprint, question loading failures
6. **Auth** - Authentication flow errors

## Basic Usage

### 1. Access the Error Handler

From a Context (Activity, Fragment, or Composable with LocalContext):

```kotlin
val errorHandler = context.findErrorHandler()
```

From a ViewModel or Repository (pass via constructor):

```kotlin
class MyViewModel(
    private val errorHandler: AppErrorHandler
) : ViewModel() {
    // ...
}
```

### 2. Handle Errors

Using the main handle() method:

```kotlin
try {
    // risky operation
} catch (e: Exception) {
    errorHandler?.handle(
        AppError.Network(
            cause = e,
            context = ErrorContext(
                screen = "ExamScreen",
                action = "load_questions",
                extra = mapOf("locale" to "en", "taskId" to "A-1")
            )
        )
    )
}
```

Using convenience extensions:

```kotlin
try {
    // risky operation
} catch (e: IOException) {
    errorHandler?.handleNetwork(
        cause = e,
        screen = "ExamScreen",
        action = "fetch_blueprint"
    )
}
```

## Best Practices

### 1. Choose the Right Error Category

- Use **Network** for connectivity/API errors
- Use **ContentLoad** for asset loading failures
- Use **Persistence** for database/DataStore errors
- Use **Unexpected** for bugs or unclassified errors

### 2. Provide Context

Always include:
- `screen` - Current screen/destination
- `action` - What operation failed
- `extra` - Additional debug info (avoid PII!)

```kotlin
errorHandler?.handleContentLoad(
    cause = AssetNotFoundException("Missing blueprint"),
    screen = "ExamSetup",
    action = "load_blueprint",
    extra = mapOf(
        "blueprintId" to "welder_ip_2024",
        "locale" to "en"
    )
)
```

### 3. Avoid PII in Context

DO NOT include:
- User names, emails, IDs
- Device serial numbers
- IP addresses
- Personal data

DO include:
- Screen names
- Operation types
- Generic identifiers (locale, taskId, etc.)

### 4. Handle Errors Close to Source

Handle errors where they occur, not at the UI layer:

```kotlin
// Repository example
class QuestionRepository(
    private val errorHandler: AppErrorHandler
) {
    suspend fun loadQuestions(taskId: String): Result<List<Question>> {
        return try {
            val questions = assetLoader.load(taskId)
            Result.success(questions)
        } catch (e: Exception) {
            errorHandler.handleContentLoad(
                cause = e,
                action = "load_questions",
                extra = mapOf("taskId" to taskId)
            )
            Result.failure(e)
        }
    }
}
```

## Debug vs Release Behavior

The error handler adapts based on build type:

### Debug Builds
- Verbose ERROR-level logs with full stack traces
- Logs include "Recorded to Crashlytics" messages
- Stack traces visible in Logcat

### Release Builds
- Minimal WARNING-level logs
- Only error class names (no stack traces)
- Reduced log noise for users

## Future Integration

### UI Error Events

The error handler emits `UiErrorEvent` for future UI integration:

```kotlin
// Future: Observe in a top-level ViewModel
val errorHandler = (application as QWeldApp).errorHandler
if (errorHandler is AppErrorHandlerImpl) {
    errorHandler.errorEvents.collect { event ->
        when (event) {
            is UiErrorEvent.NetworkError -> showNetworkSnackbar()
            is UiErrorEvent.GenericError -> showErrorDialog(event.message)
        }
    }
}
```

### Planned Features
- User-facing error report dialog
- Snackbar integration for transient errors
- Error analytics dashboard
- Custom retry strategies per error type

## Testing

When writing tests, you can:

1. Mock the error handler:
```kotlin
val mockErrorHandler = mockk<AppErrorHandler>(relaxed = true)
```

2. Verify error handling:
```kotlin
verify {
    mockErrorHandler.handle(
        match { it is AppError.Network }
    )
}
```

3. Use a test implementation:
```kotlin
class TestErrorHandler : AppErrorHandler {
    val errors = mutableListOf<AppError>()
    override fun handle(error: AppError) {
        errors.add(error)
    }
}
```

## Migration Guide

### Replacing Ad-Hoc Logging

Before:
```kotlin
try {
    loadData()
} catch (e: Exception) {
    Timber.e(e, "Failed to load data")
}
```

After:
```kotlin
try {
    loadData()
} catch (e: Exception) {
    errorHandler?.handleUnexpected(
        throwable = e,
        screen = "MyScreen",
        action = "load_data"
    )
}
```

### Benefits
- Consistent error reporting
- Automatic Crashlytics integration
- Context preservation
- Future UI integration
- Better debugging with structured context

## See Also

- `AppError.kt` - Error type definitions
- `AppErrorHandlerImpl.kt` - Implementation details
- `stage.md` - ERROR-2 status and roadmap

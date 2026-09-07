@file:OptIn(ExperimentalUuidApi::class)

package com.alpha.showcase.common.utils

import com.alpha.showcase.common.storage.getOrCreateDurableDeviceId
import getPlatform
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.concurrent.Volatile
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class UserFeedbackSender(
  private val isAnonymousUsageEnabled: () -> Boolean,
  private val getAuthenticatedUserId: () -> String?,
  private val prepareFeedbackInsert: suspend () -> Unit = {},
  private val insertFeedback: suspend (UserFeedback) -> Unit,
) {
  suspend fun send(feedbackContent: String, email: String): Result<Unit> {
    return try {
      check(isAnonymousUsageEnabled()) {
        "Enable anonymous usage data to send feedback"
      }
      prepareFeedbackInsert()
      check(isAnonymousUsageEnabled()) {
        "Anonymous usage data was disabled before feedback could be uploaded"
      }
      checkNotNull(getAuthenticatedUserId()) {
        "Feedback service is unavailable"
      }
      val normalizedContent = feedbackContent.trim()
      require(normalizedContent.length in 1..5_000) {
        "Feedback must contain between 1 and 5000 characters"
      }
      val normalizedEmail = email.trim().takeIf(String::isNotEmpty)
      require(normalizedEmail == null || normalizedEmail.length <= 320) {
        "Feedback email is too long"
      }
      currentCoroutineContext().ensureActive()
      insertFeedback(
        UserFeedback(
          feedbackType = "user_feedback",
          content = normalizedContent,
          contactEmail = normalizedEmail,
        )
      )
      Result.success(Unit)
    } catch (error: CancellationException) {
      throw error
    } catch (error: Exception) {
      Result.failure(error)
    }
  }
}

internal class UserFeedbackTaskManager(
  private val scope: CoroutineScope,
  private val sender: UserFeedbackSender,
  private val timeoutMillis: Long = USER_FEEDBACK_TIMEOUT_MILLIS,
  private val cleanupJoinTimeoutMillis: Long = USER_FEEDBACK_CLEANUP_JOIN_TIMEOUT_MILLIS,
) {
  suspend fun send(feedbackContent: String, email: String): Result<Unit> {
    val task = scope.async(start = CoroutineStart.LAZY) {
      sender.send(feedbackContent, email)
    }
    return try {
      withTimeoutOrNull(timeoutMillis) {
        task.await()
      } ?: Result.failure(UserFeedbackTimeoutException(timeoutMillis))
    } finally {
      if (!task.isCompleted) {
        task.cancel()
      }
      // Give cooperative transports a brief chance to finish cancellation. The join itself is
      // bounded so a broken transport cannot keep the feedback dialog disabled forever.
      withContext(NonCancellable) {
        withTimeoutOrNull(cleanupJoinTimeoutMillis) {
          task.join()
        }
      }
    }
  }

  fun cancelInFlight() {
    scope.coroutineContext.cancelChildren()
  }
}

internal const val USER_FEEDBACK_TIMEOUT_MILLIS = 15_000L
internal const val USER_FEEDBACK_CLEANUP_JOIN_TIMEOUT_MILLIS = 500L

internal class UserFeedbackTimeoutException(timeoutMillis: Long) :
  Exception("Feedback submission timed out after ${timeoutMillis}ms")

@OptIn(ExperimentalUuidApi::class)
class Analytics {

  val deviceId: String get() = getOrCreateDurableDeviceId()

  private val analyticsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val sessionId = Uuid.random().toString()
  private var userId: String? = null
  @Volatile
  private var anonymousUsageEnabled: Boolean = false
  private val userFeedbackSender = UserFeedbackSender(
    isAnonymousUsageEnabled = { anonymousUsageEnabled },
    getAuthenticatedUserId = SupabaseAuth::getUserId,
    prepareFeedbackInsert = {
      check(SupabaseAuth.ensureAuthenticated()) {
        "Feedback service is unavailable"
      }
    },
    insertFeedback = { feedback ->
      check(anonymousUsageEnabled) {
        "Anonymous usage data was disabled before feedback could be uploaded"
      }
      currentCoroutineContext().ensureActive()
      Supabase.insertFeedback(feedback)
    },
  )
  private val userFeedbackTaskManager = UserFeedbackTaskManager(
    scope = analyticsScope,
    sender = userFeedbackSender,
  )

  companion object {
    private var instance: Analytics? = null
    private val myLock = SynchronizedObject()

    fun initialize(anonymousUsage: Boolean = false): Analytics {
      val analytics = synchronized(myLock){
        if (instance == null) {
          instance = Analytics().apply {
            anonymousUsageEnabled = anonymousUsage
          }
        }
        instance!!
      }
      return analytics
    }

    fun getInstance(): Analytics {
      checkNotNull(instance) { "Analytics must be initialized before getting instance" }
      return instance!!
    }
  }

  fun setAnonymousUsage(enabled: Boolean) {
    anonymousUsageEnabled = enabled
    if (!enabled) {
      userFeedbackTaskManager.cancelInFlight()
    }
  }

  fun setUserId(userId: String) {
    this.userId = userId
  }

  fun clearUserId() {
    userId = null
  }

  fun logEvent(
    eventName: String,
    eventType: String = "event",
  ) {
    if (!anonymousUsageEnabled) return
    if (!eventName.matches(TELEMETRY_NAME_PATTERN) || eventName.length > 128) return
    if (!eventType.matches(TELEMETRY_NAME_PATTERN) || eventType.length > 64) return
    analyticsScope.launch {
      try {
        if (!SupabaseAuth.ensureAuthenticated()) return@launch
        if (SupabaseAuth.getUserId() == null) return@launch
        if (!anonymousUsageEnabled) return@launch
        val device = getPlatform().getDevice()
        val eventLog = EventLog(
          name = eventName,
          type = eventType,
          sid = sessionId,
          buildType = device.buildType,
        )
        Supabase.insertAnalyticsEvent(eventLog)
      } catch (e: Exception) {
        Log.w("Analytics", "Failed to submit analytics event")
      }
    }
  }

  suspend fun sendUserFeedback(feedbackContent: String, email: String): Result<Unit> =
    userFeedbackTaskManager.send(feedbackContent, email)
}

private val TELEMETRY_NAME_PATTERN = Regex("[A-Za-z0-9][A-Za-z0-9_.-]*")


@Serializable
data class Device(
  @SerialName("device_id")
  val id: String = getOrCreateDurableDeviceId(),
  @SerialName("name")
  val name: String,
  @SerialName("model")
  val model: String,
  @SerialName("oem_name")
  val oemName: String,
  @SerialName("os_name")
  val osName: String,
  @SerialName("os_version")
  val osVersion: String,
  @SerialName("locale")
  val locale: String,
  @SerialName("screen_size")
  val screenSize: String = "",
  @SerialName("app_version")
  val appVersion: String,
  @SerialName("app_namespace")
  val appNameSpace: String,
  @SerialName("app_build")
  val appBuild: String,
  @SerialName("carrier_name")
  val carrierName: String? = null,
  @SerialName("carrier_country")
  val carrierCountry: String? = null,
  @SerialName("build_type")
  val buildType: String,
  @SerialName("os_api")
  val osApi: String,
  @SerialName("build_id")
  val buildId: String,
  @SerialName("timezone_offset")
  val timezoneOffset: String,
  @SerialName("cpu_arch")
  val cpuArch: String?,
)


@Serializable
data class EventLog(
  @SerialName("event_name")
  val name: String,
  @SerialName("event_type")
  val type: String = "event",
  @SerialName("session_id")
  val sid: String? = null,
  @SerialName("build_type")
  val buildType: String,
)


@Serializable
data class UserFeedback(
  @SerialName("feedback_type")
  val feedbackType: String,
  @SerialName("content")
  val content: String,
  @SerialName("rating")
  val rating: Int? = null,
  @SerialName("contact_email")
  val contactEmail: String? = null,
)

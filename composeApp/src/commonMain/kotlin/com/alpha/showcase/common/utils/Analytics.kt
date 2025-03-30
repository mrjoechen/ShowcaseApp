@file:OptIn(ExperimentalUuidApi::class)

package com.alpha.showcase.common.utils

import androidx.compose.ui.text.intl.Locale
import com.alpha.showcase.common.storage.objectStoreOf
import com.alpha.showcase.common.versionHash
import com.alpha.showcase.common.versionName
import getPlatform
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class Analytics {

  private val analyticsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val sessionId = Uuid.random().toString()
  private var userId: String? = null
  private var device: Device? = null

  companion object {
    private var instance: Analytics? = null
    private const val PREF_NAME = "device_prefs"
    private const val DEVICE_ID_KEY = "device_id"
    private val myLock = SynchronizedObject()

    val store = objectStoreOf<String>(PREF_NAME)
    fun initialize(): Analytics {
      synchronized(myLock){
        if (instance == null) {
          instance = Analytics()
          instance?.initializeDevice()
        }
        return instance!!
      }

    }

    fun getInstance(): Analytics {
      checkNotNull(instance) { "Analytics must be initialized before getting instance" }
      return instance!!
    }
  }

  private fun getDeviceId(): String {
    return runBlocking {
      var deviceId = store.get()
      if (deviceId == null) {
        deviceId = Uuid.random().toString()
        store.set(deviceId)
      }
      deviceId
    }
  }

  fun initializeDevice() {

  }

  fun setUserId(userId: String) {
    this.userId = userId
  }

  fun sendUserFeedback(feedbackContent: String, email: String) {

    try {
      analyticsScope.launch {
        val feedback = UserFeedback(
          deviceId = device?.id ?: "",
          feedbackType = "user_feedback",
          content = feedbackContent,
          contactEmail = email
        )
        Supabase.insertValue("user_feedbacks", feedback)
      }
    } catch (e: Exception) {
        e.printStackTrace()
      ToastUtil.error("Failed to send feedback")
    }

  }
}

@Serializable
sealed class TypedProperty {
  abstract val name: String

  @Serializable
  data class StringProperty(override val name: String, val value: String) : TypedProperty()

  @Serializable
  data class LongProperty(override val name: String, val value: Long) : TypedProperty()

  @Serializable
  data class DoubleProperty(override val name: String, val value: Double) : TypedProperty()

  @Serializable
  data class BooleanProperty(override val name: String, val value: Boolean) : TypedProperty()

  @Serializable
  data class DateTimeProperty(override val name: String, val value: String) : TypedProperty()
}


@Serializable
data class Device(
  @SerialName("device_id")
  val id: String = Uuid.random().toString(),
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
  @SerialName("id")
  val id: String = Uuid.random().toString(),
  @SerialName("event_name")
  val name: String,
  @SerialName("event_type")
  val type: String = "event",
  @SerialName("session_id")
  val sid: String? = null,
  @SerialName("distribution_group_id")
  val distributionGroupId: String? = null,
  @SerialName("user_id")
  val userId: String? = null,
  @SerialName("device_id")
  val deviceId: String? = null,
  @SerialName("data_residency_region")
  val dataResidencyRegion: String? = null,
  @SerialName("properties")
  val properties: Map<String, String>? = null,
  @SerialName("typed_properties")
  val typedProperties: List<TypedProperty>? = null,
  @SerialName("build_type")
  val buildType: String = "",
  @SerialName("build_info")
  val buildInfo: String? = null
)


@Serializable
data class UserFeedback(
  @SerialName("device_id")
  val deviceId: String,
  @SerialName("feedback_type")
  val feedbackType: String,
  @SerialName("content")
  val content: String,
  @SerialName("rating")
  val rating: Int? = null,
  @SerialName("attachment_url")
  val attachmentUrl: String? = null,
  @SerialName("contact_email")
  val contactEmail: String? = null,
  @SerialName("contact_phone")
  val contactPhone: String? = null
)
package com.alpha.showcase.common.networkfile

import io.github.aakira.napier.Napier
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.core.toByteArray
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.adaptivity.xmlutil.ExperimentalXmlUtilApi
import nl.adaptivity.xmlutil.XmlDeclMode
import nl.adaptivity.xmlutil.core.XmlVersion
import nl.adaptivity.xmlutil.serialization.DefaultXmlSerializationPolicy
import nl.adaptivity.xmlutil.serialization.XML
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import nl.adaptivity.xmlutil.serialization.XmlValue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class WebDavClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String
) {

    private val client = HttpClient {
        expectSuccess = true
        install(Logging) {
            level = LogLevel.ALL
            logger = object : Logger {
                override fun log(message: String) {
                    Napier.i(message)
                }
            }
        }
    }

    suspend fun listFiles(directory: String): List<WebDavFile> {
        val url = "$baseUrl${if (directory.startsWith("/")) directory else "/$directory"}"
        val response: HttpResponse = client.request(url) {
            method = HttpMethod("PROPFIND")
            headers {
                append(HttpHeaders.Authorization, "Basic ${getAuthHeader()}")
                append(HttpHeaders.Depth, "1")
//                append(HttpHeaders.AccessControlAllowOrigin, "*")
            }
//            contentType(ContentType.Application.Xml)
        }
        val responseString = response.body<String>()
        return parseWebDavResponse(responseString)
    }

    suspend fun downloadFile(filePath: String): ByteArray {
        val url = "$baseUrl/$filePath"
        val response: HttpResponse = client.get(url) {
            headers {
                append(HttpHeaders.Authorization, "Basic ${getAuthHeader()}")
            }
        }
        return response.body<ByteArray>()
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun getAuthHeader(): String {
        val authString = "$username:$password"
        return Base64.encode(authString.toByteArray())
    }

    @OptIn(ExperimentalXmlUtilApi::class)
    val xml: XML by lazy {
        XML {
            xmlVersion = XmlVersion.XML10
            xmlDeclMode = XmlDeclMode.Auto
            indentString = "  "
            repairNamespaces = true
            autoPolymorphic = true
            policy = DefaultXmlSerializationPolicy(
                DefaultXmlSerializationPolicy.Builder().apply {
                    autoPolymorphic = false
                    pedantic = false
                    ignoreNamespaces()
                    ignoreUnknownChildren()
                }.build()
            )
        }
    }


    private fun parseWebDavResponse(responseBody: String): List<WebDavFile> {
        val multistatus = xml.decodeFromString(Multistatus.serializer(), responseBody)
        return multistatus.responses.map { response ->
            WebDavFile(
                name = response.propstat.prop.displayname?:(response.href.substringAfterLast('/').removeSuffix("/")),
                path = response.href,
                isDirectory = response.propstat.prop.resourcetype.collection != null,
                lastModified = response.propstat.prop.getlastmodified ?: "",
                creationDate = response.propstat.prop.creationdate ?: "",
                contentLength = response.propstat.prop.getcontentlength?.toLongOrNull() ?: 0,
                eTag = response.propstat.prop.getetag ?: "",
                contentType = response.propstat.prop.getcontenttype ?: ""
            )
        }
    }
}

@Serializable
@XmlSerialName("multistatus", "DAV:", "D")
data class Multistatus(
    @XmlElement(true)
    val responses: List<Response>
)

@Serializable
@XmlSerialName("response", "DAV:", "D")
data class Response(
    @XmlElement
    @XmlSerialName("href", "DAV:", "D")
    val href: String,
    @XmlSerialName("propstat", "DAV:", "D")
    val propstat: Propstat
)

@Serializable
@XmlSerialName("propstat", "DAV:", "D")
data class Propstat(
    @XmlSerialName("prop", "DAV:", "D")
    val prop: Prop,
    @XmlElement
    @XmlSerialName("status", "DAV:", "D")
    val status: String
)

@Serializable
@XmlSerialName("prop", "DAV:", "D")
data class Prop(
    @XmlElement
    @XmlSerialName("getcontenttype", "DAV:", "D")
    val getcontenttype: String? = null,
    @XmlElement
    @XmlSerialName("getcontentlength", "DAV:", "D")
    val getcontentlength: String? = null,
    @XmlElement
    @XmlSerialName("getetag", "DAV:", "D")
    val getetag: String? = null,
    @XmlElement
    @XmlSerialName("getlastmodified", "DAV:", "D")
    val getlastmodified: String? = null,
    @XmlElement
    @XmlSerialName("creationdate", "DAV:", "D")
    val creationdate: String? = null,
    @XmlSerialName("resourcetype", "DAV:", "D")
    val resourcetype: ResourceType,
    @XmlElement
    @XmlSerialName("displayname", "DAV:", "D")
    val displayname: String? = null,
    @XmlElement
    @XmlSerialName("supportedlock", "DAV:", "D")
    val supportedlock: SupportedLock? = null,
    @XmlElement(true)
    val otherProps: List<OtherProp> = emptyList()
)

@Serializable
@XmlSerialName("resourcetype", "DAV:", "D")
data class ResourceType(
    @XmlElement(false)
    @XmlSerialName("collection", "DAV:", "D")
    val collection: Unit? = null
)

@Serializable
@XmlSerialName("supportedlock", "DAV:", "D")
data class SupportedLock(
    @XmlElement(true)
    val lockentries: List<LockEntry> = emptyList()
)

@Serializable
@XmlSerialName("lockentry", "DAV:", "D")
data class LockEntry(
    @XmlSerialName("lockscope", "DAV:", "D")
    val lockscope: LockScope,
    @XmlSerialName("locktype", "DAV:", "D")
    val locktype: LockType
)

@Serializable
@XmlSerialName("lockscope", "DAV:", "D")
data class LockScope(
    @XmlElement(false)
    @XmlSerialName("exclusive", "DAV:", "D")
    val exclusive: Unit? = null,
    @XmlElement(false)
    @XmlSerialName("shared", "DAV:", "D")
    val shared: Unit? = null
)

@Serializable
@XmlSerialName("locktype", "DAV:", "D")
data class LockType(
    @XmlElement(false)
    @XmlSerialName("write", "DAV:", "D")
    val write: Unit? = null
)

@Serializable
data class OtherProp(
    @XmlSerialName("", "", "")
    val name: String,
    @XmlValue(true)
    val value: String
)
@Serializable
data class WebDavFile(
    @SerialName("name")
    val name: String,
    @SerialName("path")
    val path: String,
    @SerialName("isDirectory")
    val isDirectory: Boolean,
    @SerialName("lastModified")
    val lastModified: String,
    @SerialName("creationDate")
    val creationDate: String,
    @SerialName("contentLength")
    val contentLength: Long = 0,
    @SerialName("eTag")
    val eTag: String = "",
    @SerialName("contentType")
    val contentType: String = ""
)
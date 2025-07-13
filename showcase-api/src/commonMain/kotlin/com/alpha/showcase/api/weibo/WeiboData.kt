package com.alpha.showcase.api.weibo

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * https://m.weibo.cn/api/container/getIndex?type=uid&value=1906286443&containerid=1076031906286443
 */
@Serializable
data class WeiboUserResponse(
    @SerialName("ok")
    val ok: Int,
    @SerialName("data")
    val data: WeiboUserData?
)

@Serializable
data class WeiboUserData(
    @SerialName("userInfo")
    val userInfo: WeiboUser?,
    @SerialName("tabsInfo")
    val tabsInfo: WeiboTabsInfo?
)

@Serializable
data class WeiboUser(
    @SerialName("id")
    val id: Long,
    @SerialName("screen_name")
    val screenName: String,
    @SerialName("profile_image_url")
    val profileImageUrl: String?,
    @SerialName("avatar_hd")
    val avatarHd: String?,
    @SerialName("statuses_count")
    val statusesCount: Int = 0,
    @SerialName("followers_count")
    val followersCount: String = ""
)

@Serializable
data class WeiboTabsInfo(
    @SerialName("tabs")
    val tabs: List<WeiboTab>
)

@Serializable
data class WeiboTab(
    @SerialName("tabKey")
    val tabKey: String,
    @SerialName("tab_type")
    val tabType: String,
    @SerialName("title")
    val title: String,
    @SerialName("containerid")
    val containerid: String
)

@Serializable
data class WeiboPostsResponse(
    @SerialName("ok")
    val ok: Int,
    @SerialName("data")
    val data: WeiboPostsData?
)

@Serializable
data class WeiboPostsData(
    @SerialName("cards")
    val cards: List<WeiboCard>?,
    @SerialName("cardlistInfo")
    val cardlistInfo: WeiboCardListInfo?
)

@Serializable
data class WeiboCard(
    @SerialName("card_type")
    val cardType: Int,
    @SerialName("mblog")
    val mblog: WeiboPost?
)

@Serializable
data class WeiboCardListInfo(
    @SerialName("containerid")
    val containerid: String,
    @SerialName("since_id")
    val sinceId: Long?,
    @SerialName("page")
    val page: Int?
)

@Serializable
data class WeiboPost(
    @SerialName("id")
    val id: String,
    @SerialName("mid")
    val mid: String,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("text")
    val text: String,
    @SerialName("raw_text")
    val rawText: String?,
    @SerialName("source")
    val source: String?,
    @SerialName("pics")
    val pics: List<WeiboImage>?,
    @SerialName("user")
    val user: WeiboUser?,
    @SerialName("retweeted_status")
    val retweetedStatus: WeiboPost?,
    @SerialName("page_info")
    val pageInfo: WeiboPageInfo?
)

@Serializable
data class WeiboImage(
    @SerialName("pid")
    val pid: String,
    @SerialName("url")
    val url: String,
    @SerialName("size")
    val size: String?,
    @SerialName("geo")
    val geo: WeiboImageGeo?,
    @SerialName("large")
    val large: WeiboImageSize?,
    @SerialName("largest")
    val largest: WeiboImageSize?,
    @SerialName("type")
    val type: String?,
    @SerialName("duration")
    val duration: Float?,
    @SerialName("videoSrc")
    val videoSrc: String?
)

@Serializable
data class WeiboImageGeo(
    @SerialName("width")
    val width: Int,
    @SerialName("height")
    val height: Int,
    @SerialName("croped")
    val croped: Boolean?
)

@Serializable
data class WeiboImageSize(
    @SerialName("size")
    val size: String,
    @SerialName("url")
    val url: String,
    @SerialName("geo")
    val geo: WeiboImageGeo?
)

@Serializable
data class WeiboPageInfo(
    @SerialName("type")
    val type: String,
    @SerialName("page_pic")
    val page_pic: PagePic?,
    @SerialName("page_url")
    val pageUrl: String?,
    @SerialName("page_title")
    val pageTitle: String?,
    @SerialName("play_count")
    val playCount: String? = "",
    @SerialName("video_orientation")
    val videoOrientation: String? = null,
    @SerialName("media_info")
    val mediaInfo: WeiboMediaInfo? = null,
    @SerialName("urls")
    val urls: WeiboUrl? = null
)

@Serializable
data class PagePic(
    @SerialName("type")
    val type: String?,
    @SerialName("url")
    val url: String?,
    @SerialName("width")
    val width: String?,
    @SerialName("height")
    val height: String?
)

@Serializable
data class WeiboMediaInfo(
    @SerialName("stream_url")
    val streamUrl: String?,
    @SerialName("stream_url_hd")
    val streamUrlHd: String?,
    @SerialName("duration")
    val duration: Float?
)
@Serializable
data class WeiboUrl(
    @SerialName("mp4_720p_mp4")
    val mp4_720p_mp4: String?,
    @SerialName("mp4_ld_mp4")
    val mp4_ld_mp4: String?,
    @SerialName("mp4_hd_mp4")
    val mp4_hd_mp4: String?,
)

fun WeiboPageInfo.mediaUrl(): String? = if (type == "video") {
    (urls?.mp4_720p_mp4) ?: (urls?.mp4_hd_mp4) ?: (urls?.mp4_ld_mp4) ?: (mediaInfo?.streamUrlHd)?: (mediaInfo?.streamUrl)
} else {
    null
}
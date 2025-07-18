package com.alpha.showcase.common.repo

import com.alpha.showcase.api.weibo.WeiboApi
import com.alpha.showcase.api.weibo.WeiboImage
import com.alpha.showcase.api.weibo.WeiboPost
import com.alpha.showcase.api.weibo.mediaUrl
import com.alpha.showcase.common.networkfile.storage.remote.WeiboSource
import com.alpha.showcase.common.ui.play.DataWithType
import com.alpha.showcase.common.ui.play.removeQueryParameter
import com.alpha.showcase.common.utils.getExtension
import kotlinx.coroutines.delay

class WeiboSourceRepo : SourceRepository<WeiboSource, DataWithType> {

    private val weiboService by lazy {
        WeiboApi()
    }

    override suspend fun getItem(remoteApi: WeiboSource): Result<DataWithType> {
        return Result.failure(UnsupportedOperationException("Single item not supported for Weibo"))
    }

    override suspend fun getItems(
        remoteApi: WeiboSource,
        recursive: Boolean,
        filter: ((DataWithType) -> Boolean)?
    ): Result<List<DataWithType>> {
        return try {
            val result = mutableListOf<DataWithType>()

            val containRetweet = remoteApi.containRetweet
            val containOriginal = remoteApi.containOriginal

            // 第一步：获取用户信息和containerid
            val userResponse = weiboService.getUserInfo(uid = remoteApi.uid)
            if (userResponse.ok != 1 || userResponse.data?.tabsInfo?.tabs.isNullOrEmpty()) {
                return Result.failure(Exception("no valid user info found"))
            }

            // 查找包含微博内容的tab
            val weiboTab = userResponse.data?.tabsInfo?.tabs?.find {
                it.tabKey == "weibo" || it.containerid.contains("107603")
            } ?: userResponse.data?.tabsInfo?.tabs?.first() ?:
                return Result.failure(Exception("no valid weibo tab found"))

            val containerid = weiboTab.containerid
            val userName = userResponse.data?.userInfo?.screenName ?: "user-${remoteApi.uid}"

            // 第二步：分页获取用户微博
            var page = 1
            var sinceId: Long? = null
            var hasMore = true

            while (hasMore && page <= 6) { // 限制最多获取10页，避免过多请求
                try {
                    val postsResponse = weiboService.getUserPosts(
                        uid = remoteApi.uid,
                        containerid = containerid,
                        page = page,
                        sinceId = sinceId
                    )

                    if (postsResponse.ok != 1 || postsResponse.data?.cards.isNullOrEmpty()) {
                        hasMore = false
                        break
                    }

                    val cards = postsResponse.data?.cards
                    var foundImages = false

                    cards?.forEach { card ->
                        card.mblog?.let { post ->
                            val images = extractImagesFromPost(post, containOriginal, containRetweet,  userName)
                            result.addAll(images.map { DataWithType(data = it, type = it.removeQueryParameter().getExtension()) })
                            result.addAll(extractVideoFromPost(post, containOriginal, containRetweet, userName).map { DataWithType(data = it, type = it.removeQueryParameter().getExtension()) })
                            if (images.isNotEmpty()) {
                                foundImages = true
                            }
                        }
                    }

                    // 更新分页参数
                    val cardListInfo = postsResponse.data?.cardlistInfo
                    sinceId = cardListInfo?.sinceId
                    
                    // 如果没有更多数据或者sinceId相同，停止分页
                    if (sinceId == null || !foundImages) {
                        hasMore = false
                    } else {
                        page++
                        // 添加延迟避免请求过于频繁
                        delay(1000)
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                    break
                }
            }

            // 应用过滤器
            val filtered = filter?.let { result.filter(it) } ?: result
            
            Result.success(filtered)

        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    private fun extractImagesFromPost(post: WeiboPost, containOriginal: Boolean, containRetweet: Boolean, userName: String): List<String> {
        val images = mutableListOf<String>()

        if (containOriginal){
            // 提取原创微博的图片
            post.pics?.let { pics ->
                images.addAll(convertWeiboImagesToNetworkFiles(pics, post, userName))
            }
        }

        if (containRetweet){
            // 提取转发微博的图片
            post.retweetedStatus?.pics?.let { pics ->
                images.addAll(convertWeiboImagesToNetworkFiles(pics, post.retweetedStatus!!, userName))
            }
        }

        
        return images
    }

    private fun extractVideoFromPost(post: WeiboPost, containOriginal: Boolean, containRetweet: Boolean, userName: String): List<String> {
        val images = mutableListOf<String>()
        if (containOriginal){
            post.pics?:let { pics ->
                post.pageInfo?.mediaUrl()?.let { mediaUrl ->
                    images.add(mediaUrl)
                }

            }
        }


        if (containRetweet) {
            post.retweetedStatus?.pics ?: let { pics ->
                post.retweetedStatus?.pageInfo?.mediaUrl()?.let { mediaUrl ->
                    images.add(mediaUrl)
                }
            }
        }

        return images
    }

    private fun convertWeiboImagesToNetworkFiles(
        pics: List<WeiboImage>,
        post: WeiboPost,
        userName: String
    ): List<String> {
        return pics.mapIndexedNotNull { index, pic ->
            try {
                // 优先使用最大尺寸的图片
                val imageUrl = pic.videoSrc ?: pic.largest?.url ?: pic.large?.url ?: pic.url
                // 提取图片扩展名
//                val extension = extractImageExtension(imageUrl)
                // 生成文件名：时间戳_序号.扩展名
//                val timestamp = parseWeiboTime(post.createdAt)
//                val fileName = "${timestamp}_${index + 1}.$extension"
//                 清理微博文本作为描述
//                val description = cleanWeiboText(post.rawText ?: post.text)

                imageUrl
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun extractImageExtension(url: String): String {
        return when {
            url.contains(".jpg", ignoreCase = true) -> "jpg"
            url.contains(".jpeg", ignoreCase = true) -> "jpeg"
            url.contains(".png", ignoreCase = true) -> "png"
            url.contains(".gif", ignoreCase = true) -> "gif"
            url.contains(".webp", ignoreCase = true) -> "webp"
            else -> "jpg" // 默认为jpg
        }
    }

    private fun cleanWeiboText(text: String): String {
        return text
            .replace(Regex("<[^>]*>"), "") // 移除HTML标签
            .replace(Regex("http[s]?://\\S+"), "") // 移除链接
            .replace(Regex("#\\S+#"), "") // 移除话题标签
            .replace(Regex("@\\S+"), "") // 移除@用户
            .trim()
            .take(100) // 限制描述长度
    }

    private fun estimateImageSize(pic: WeiboImage): Long {
        // 根据图片尺寸估算大小
        val geo = pic.largest?.geo ?: pic.large?.geo ?: pic.geo
        return if (geo != null) {
            // 粗略估算：宽度 * 高度 * 3 (RGB) / 8 (压缩率估算)
            (geo.width * geo.height * 3L / 8).coerceAtLeast(50000L)
        } else {
            200000L // 默认200KB
        }
    }
}
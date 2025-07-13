package com.alpha.showcase.common.repo

import com.alpha.showcase.common.utils.Supabase

class AlbumSourceRepo {

    companion object {
        private var _music_api_url: String? = null
        private var _api_auth: String? = null
        suspend fun getMusicApiUrl(): String {
            return _music_api_url ?: Supabase.getConfigValue("music_api_baseurl")?.also {
                _music_api_url = it
            }?:""
        }

        suspend fun getApiAuth(): String? {
            return _api_auth ?: Supabase.getConfigValue("music_api_auth")?.also {
                _api_auth = it
            }
        }
    }
}
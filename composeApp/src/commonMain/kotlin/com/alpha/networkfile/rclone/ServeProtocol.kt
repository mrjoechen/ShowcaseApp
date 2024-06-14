package com.alpha.networkfile.rclone

sealed class SERVE_PROTOCOL(val name: String, val type: Int)

object SERVE_PROTOCOL_HTTP: SERVE_PROTOCOL("http", 1)
object SERVE_PROTOCOL_FTP: SERVE_PROTOCOL("ftp", 2)
object SERVE_PROTOCOL_DLNA: SERVE_PROTOCOL("dlna", 3)
object SERVE_PROTOCOL_WEBDAV: SERVE_PROTOCOL("webdav", 4)
object SERVE_PROTOCOL_REST: SERVE_PROTOCOL("restic", 5)
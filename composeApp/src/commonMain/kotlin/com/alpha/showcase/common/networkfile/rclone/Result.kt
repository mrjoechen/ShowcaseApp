package com.alpha.showcase.common.networkfile.rclone

//sealed interface Result<out R>{
//  data class Success<out T>(val data: T?): Result<T>
//  data class Error(val message: String?, val exception: Exception? = null): Result<Nothing>
//}
//
//val Result<*>.succeeded
//  get() = this is Result.Success && data != null
//
//val Result<*>.toString
//  get() = when (this) {
//    is Result.Success<*> -> "Success[data=$data]"
//    is Result.Error -> "Error[message=$message , exception=$exception]"
//  }
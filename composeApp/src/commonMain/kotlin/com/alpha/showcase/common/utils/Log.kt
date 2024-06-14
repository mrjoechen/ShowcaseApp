package com.alpha.showcase.common.utils

import io.github.aakira.napier.Napier

/**
 * Created by chenqiao on 2023/10/1.
 * e-mail : mrjctech@gmail.com
 */

object Log{

  fun v(msg: String) {
    Napier.v(message = msg)
  }

  fun i(msg: String) {
    Napier.i(message = msg)
  }

  fun d(msg: String) {
    Napier.d(message = msg)
  }

  fun w(msg: String) {
    Napier.w(message = msg)
  }

  fun e(msg: String) {
    Napier.e(message = msg)
  }

  fun wtf(msg: String) {
    Napier.wtf(message = msg)
  }

  fun v(tag: String, msg: String) {
    Napier.v(message = msg, tag = tag)
  }

  fun i(tag: String, msg: String) {
    Napier.i(message = msg, tag = tag)
  }

  fun d(tag: String, msg: String) {
    Napier.d(message = msg, tag = tag)
  }

  fun w(tag: String, msg: String) {
    Napier.w(message = msg, tag = tag)
  }

  fun e(tag: String, msg: String) {
    Napier.e(message = msg, tag = tag)
  }

  fun wtf(tag: String, msg: String) {
    Napier.wtf(message = msg, tag = tag)
  }
}
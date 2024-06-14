package com.alpha.networkfile.util

import kotlin.random.Random

fun getStringRandom(length: Int = 12): String? {
  var value: String? = ""
  //参数length，表示生成几位随机数
  for (i in 0 until length) {
    val charOrNum = if (Random.nextInt(2) % 2 == 0) "char" else "num"
    //输出字母还是数字
    if ("char".equals(charOrNum, ignoreCase = true)) {
      //输出是大写字母还是小写字母
      val temp = if (Random.nextInt(2) % 2 == 0) 65 else 97
      value += (Random.nextInt(26) + temp).toChar()
    } else if ("num".equals(charOrNum, ignoreCase = true)) {
      value += Random.nextInt(10)
    }
  }
  return value
}


//fun main() {
//  println("ABCDEFG1234567890😄你好👋🌞".encodeName())
//  println("MTIzQWI.".decodeName())
//}
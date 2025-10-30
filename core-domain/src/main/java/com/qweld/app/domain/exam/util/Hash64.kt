package com.qweld.app.domain.exam.util

object Hash64 {
  fun hash(seed: Long, salt: String): Long {
    var h = seed xor -0x340d631b2e0b8dcbL
    salt.forEach { ch ->
      h = h xor ch.code.toLong()
      h *= 1099511628211L
    }
    h = h xor (h ushr 32)
    h *= 0x62a9d9ed799705f5L
    h = h xor (h ushr 29)
    return h
  }
}

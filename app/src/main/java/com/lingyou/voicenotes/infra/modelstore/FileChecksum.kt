package com.lingyou.voicenotes.infra.modelstore

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

object FileChecksum {
    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(1024 * 256)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: File, expectedSha256: String, expectedBytes: Long? = null): Boolean {
        if (!file.exists()) return false
        if (expectedBytes != null && file.length() != expectedBytes) return false
        return sha256(file).equals(expectedSha256, ignoreCase = true)
    }
}

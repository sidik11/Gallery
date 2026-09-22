package com.sidik.msgallery.security

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec

/**
 * Bounded-memory Media3 reader for version-2 chunked vault containers.
 *
 * Each chunk is independently AES-GCM authenticated, allowing Media3 to seek
 * without materializing the complete decrypted video or writing a cache file.
 * Legacy version-1 vault files are intentionally not exposed for random access.
 */
class CryptoDataSource(
    private val file: java.io.File,
    private val key: javax.crypto.SecretKey
) : DataSource {
    private var raf: RandomAccessFile? = null
    private var opened = false
    private var position = 0L
    private var remaining = 0L
    private var totalLength = 0L
    private var chunkSize = 0
    private var chunkPlainStart = 0L
    private var chunkPlainLength = 0
    private var chunkData = ByteArray(0)

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        check(!opened) { "DataSource already opened" }
        val r = RandomAccessFile(file, "r")
        raf = r
        try {
            require(readAscii(r, 5) == CryptoEngine.MAGIC) { "Invalid vault file" }
            require(r.read() == CryptoEngine.VERSION_CHUNKED) {
                "Vault format does not support streaming playback"
            }
            chunkSize = r.readInt()
            require(chunkSize in 64 * 1024..8 * 1024 * 1024) { "Invalid chunk size" }
            totalLength = scanPlainLength(r, chunkSize)
            position = dataSpec.position.coerceAtLeast(0L).coerceAtMost(totalLength)
            remaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                totalLength - position
            } else {
                minOf(dataSpec.length.coerceAtLeast(0L), totalLength - position)
            }
            opened = true
            return remaining
        } catch (t: Throwable) {
            close()
            throw t
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened || length == 0) return if (length == 0) 0 else C.RESULT_END_OF_INPUT
        if (remaining <= 0L) return C.RESULT_END_OF_INPUT

        val target = minOf(length.toLong(), remaining).toInt()
        var copied = 0

        while (copied < target) {
            if (position < chunkPlainStart ||
                position >= chunkPlainStart + chunkPlainLength
            ) {
                loadChunkFor(position)
            }

            val inChunk = (position - chunkPlainStart).toInt()
            val available = chunkPlainLength - inChunk
            val count = minOf(target - copied, available)
            System.arraycopy(chunkData, inChunk, buffer, offset + copied, count)
            position += count
            remaining -= count
            copied += count
        }
        return copied
    }

    override fun getUri(): Uri = Uri.fromFile(file)

    override fun close() {
        chunkData.fill(0)
        chunkData = ByteArray(0)
        raf?.close()
        raf = null
        opened = false
        position = 0L
        remaining = 0L
        totalLength = 0L
        chunkPlainStart = 0L
        chunkPlainLength = 0
        chunkSize = 0
    }

    private fun loadChunkFor(targetPosition: Long) {
        val r = raf ?: error("DataSource is closed")
        r.seek(5L + 1L + 4L)

        var plainStart = 0L
        require(targetPosition < totalLength) { "Read position past end of vault" }
        while (true) {
            val header = ByteArray(4)
            if (r.read(header) != 4) error("Truncated vault chunk")
            val plainLength = ByteBuffer.wrap(header).int
            require(plainLength in 1..chunkSize) { "Invalid vault chunk length" }

            val iv = ByteArray(CryptoEngine.IV_SIZE)
            if (r.read(iv) != iv.size) error("Truncated vault IV")
            val encrypted = ByteArray(plainLength + 16)
            if (r.read(encrypted) != encrypted.size) error("Truncated vault chunk")

            val next = plainStart + plainLength
            require(next <= totalLength) { "Invalid vault chunk bounds" }
            if (targetPosition < next) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                    init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(CryptoEngine.TAG_BITS, iv))
                }
                val decrypted = cipher.doFinal(encrypted)
                chunkData.fill(0)
                chunkData = decrypted
                chunkPlainStart = plainStart
                chunkPlainLength = decrypted.size
                iv.fill(0)
                encrypted.fill(0)
                return
            }

            iv.fill(0)
            encrypted.fill(0)
            plainStart = next
        }
    }

    private fun scanPlainLength(r: RandomAccessFile, chunkSize: Int): Long {
        r.seek(5L + 1L + 4L)
        var total = 0L
        while (r.filePointer < r.length()) {
            val plain = r.readInt()
            require(plain in 1..chunkSize) { "Invalid vault chunk length" }
            val payloadBytes = CryptoEngine.IV_SIZE + plain + 16
            val nextPosition = r.filePointer + payloadBytes
            require(nextPosition <= r.length()) { "Truncated vault chunk" }
            r.seek(nextPosition)
            total += plain
        }
        return total
    }

    private fun readAscii(r: RandomAccessFile, count: Int): String {
        val bytes = ByteArray(count)
        if (r.read(bytes) != count) error("Truncated vault header")
        return String(bytes, Charsets.US_ASCII)
    }
}

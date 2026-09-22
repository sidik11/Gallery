package com.sidik.msgallery.security

import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.RandomAccessFile
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Media3 DataSource for encrypted MS Gallery vault containers.
 *
 * The current vault format is AES-GCM authenticated as one container, so
 * arbitrary byte-range seeking cannot be safely supported without changing
 * the format. This DataSource therefore decrypts sequentially and exposes
 * plaintext through a bounded read buffer rather than creating a disk cache.
 */
class CryptoDataSource(
    private val file: java.io.File,
    private val key: javax.crypto.SecretKey
) : DataSource {
    private var opened = false
    private var position = 0L
    private var length = C.LENGTH_UNSET.toLong()
    private var plain: ByteArray? = null

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        check(!opened) { "DataSource already opened" }
        val bytes = file.readBytes()
        val input = bytes.inputStream()
        val output = java.io.ByteArrayOutputStream()
        CryptoEngine.decrypt(input, output, key)
        bytes.fill(0)
        plain = output.toByteArray()
        position = dataSpec.position.coerceAtLeast(0L)
        length = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            plain!!.size.toLong() - position
        } else {
            dataSpec.length
        }.coerceAtLeast(0L)
        opened = true
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (!opened || length == 0) return if (length == 0) 0 else C.RESULT_END_OF_INPUT
        val data = plain ?: return C.RESULT_END_OF_INPUT
        val remaining = (data.size.toLong() - position).coerceAtLeast(0L)
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), remaining).toInt()
        System.arraycopy(data, position.toInt(), buffer, offset, toRead)
        position += toRead
        return toRead
    }

    override fun getUri(): android.net.Uri = android.net.Uri.fromFile(file)

    override fun close() {
        plain?.fill(0)
        plain = null
        opened = false
        position = 0L
        length = C.LENGTH_UNSET.toLong()
    }
}

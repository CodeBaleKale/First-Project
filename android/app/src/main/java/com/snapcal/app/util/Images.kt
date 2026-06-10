package com.snapcal.app.util

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.snapcal.app.net.ClaudeExtractor.EncodedImage
import java.io.ByteArrayOutputStream

/** Longest edge sent to the API — keeps image token cost reasonable. */
private const val MAX_DIMENSION = 1568

object Images {

    /** Decode a content Uri, downscale to [MAX_DIMENSION], re-encode as base64 JPEG. */
    fun encode(resolver: ContentResolver, uri: Uri): EncodedImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IllegalArgumentException("Could not read that image")
        }

        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= MAX_DIMENSION) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = resolver.openInputStream(uri).use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: throw IllegalArgumentException("Could not decode that image")

        val scaled = scaleDown(bitmap)
        val bytes = ByteArrayOutputStream().also {
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, it)
        }.toByteArray()
        if (scaled !== bitmap) bitmap.recycle()

        return EncodedImage("image/jpeg", Base64.encodeToString(bytes, Base64.NO_WRAP))
    }

    /** Small bitmap for UI thumbnails. */
    fun thumbnail(resolver: ContentResolver, uri: Uri, target: Int = 256): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sampleSize = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= target) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
    } catch (_: Exception) {
        null
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_DIMENSION) return bitmap
        val ratio = MAX_DIMENSION.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }
}

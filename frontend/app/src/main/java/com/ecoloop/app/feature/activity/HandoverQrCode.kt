package com.ecoloop.app.feature.activity

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/** Membuat bitmap QR dari token serah terima mentah (dibuat di perangkat, tidak dikirim ke mana pun). */
fun createQrBitmap(value: String, size: Int = 640): Bitmap {
    val matrix = MultiFormatWriter().encode(
        value,
        BarcodeFormat.QR_CODE,
        size,
        size,
        mapOf(EncodeHintType.MARGIN to 1),
    )
    val pixels = IntArray(size * size) { index ->
        if (matrix[index % size, index / size]) Color.BLACK else Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}

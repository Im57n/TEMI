package com.example.temiapp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix

class QrCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code)

        val ipTextView = findViewById<TextView>(R.id.ipTextView)
        val urlTextView = findViewById<TextView>(R.id.urlTextView)
        val qrImageView = findViewById<ImageView>(R.id.img_download_qr)
        val btnBack = findViewById<Button>(R.id.btn_back)

        btnBack.setOnClickListener { finish() }

        val currentIp = getLocalIpAddress()

        if (currentIp != null) {
            val downloadUrl = "http://$currentIp:8080/download"
            ipTextView.text = currentIp
            urlTextView.text = downloadUrl

            // 🌟 生成並顯示 QR Code
            val qrBitmap = generateQRCode(downloadUrl)
            if (qrBitmap != null) {
                qrImageView.setImageBitmap(qrBitmap)
            }
        } else {
            ipTextView.text = "⚠️ 請確認 Temi 已連上 Wi-Fi"
            urlTextView.text = "無法產生網址"
        }
    }

    // 🌟 核心：生成 QR Code Bitmap 的方法
    private fun generateQRCode(text: String): Bitmap? {
        return try {
            val width = 500
            val height = 500
            val matrix: BitMatrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, width, height)
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(x, y, if (matrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun getLocalIpAddress(): String? {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt == 0) return null
        return Formatter.formatIpAddress(ipInt)
    }
}
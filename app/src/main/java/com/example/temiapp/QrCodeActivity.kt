package com.example.temiapp // ⚠️ 確保這裡跟你的專案 package 一樣

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Bundle
import android.text.format.Formatter
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class QrCodeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_code)

        val ipTextView = findViewById<TextView>(R.id.ipTextView)
        val urlTextView = findViewById<TextView>(R.id.urlTextView) // 🌟 綁定新的網址 TextView
        val btnBack = findViewById<Button>(R.id.btn_back)

        // 設定返回按鈕的點擊事件
        btnBack.setOnClickListener {
            finish() // 關閉此頁面，返回原本的主畫面
        }

        // 取得裝置目前的區域網路 IP
        val currentIp = getLocalIpAddress()

        if (currentIp != null) {
            ipTextView.text = currentIp
            // 🌟 自動組合出正確的下載網址
            urlTextView.text = "http://$currentIp:8080/download"
        } else {
            ipTextView.text = "⚠️ 請確認 Temi 已連上 Wi-Fi"
            urlTextView.text = "無法產生網址"
        }
    }

    // 取得區域網路 IP 的方法
    @Suppress("DEPRECATION")
    private fun getLocalIpAddress(): String? {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt == 0) return null
        return Formatter.formatIpAddress(ipInt)
    }
}
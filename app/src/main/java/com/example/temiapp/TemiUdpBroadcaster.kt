package com.example.temiapp

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import android.util.Log
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Temi UDP 廣播器
 * 功能：每 2 秒在醫院內網廣播自己的 IP，讓護理師的手機 App 能自動抓到，免掃 QR Code！
 */
class TemiUdpBroadcaster(private val context: Context) {
    private var isRunning = false
    private var thread: Thread? = null

    fun start() {
        if (isRunning) return
        isRunning = true
        thread = Thread {
            try {
                // 開啟 UDP Socket 並允許廣播
                val socket = DatagramSocket()
                socket.broadcast = true

                while (isRunning) {
                    val ip = getLocalIpAddress()
                    if (ip != null) {
                        // 打包成 JSON 格式
                        val json = JSONObject().apply {
                            put("device", "temi")
                            put("ip", ip)
                        }.toString()

                        val data = json.toByteArray()
                        // 255.255.255.255 代表「發送給同一個 Wi-Fi 下的所有人」，Port 設定為 8888
                        val packet = DatagramPacket(data, data.size, InetAddress.getByName("255.255.255.255"), 8888)
                        socket.send(packet)
                        Log.d("TemiUDP", "廣播發送中: $json")
                    }
                    Thread.sleep(2000) // 每 2 秒喊一次
                }
                socket.close()
            } catch (e: Exception) {
                Log.e("TemiUDP", "UDP 廣播發生錯誤", e)
            }
        }
        thread?.start()
    }

    fun stop() {
        isRunning = false
        thread?.interrupt()
    }

    @Suppress("DEPRECATION")
    private fun getLocalIpAddress(): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ipInt = wifiManager.connectionInfo.ipAddress
        if (ipInt == 0) return null
        return Formatter.formatIpAddress(ipInt)
    }
}
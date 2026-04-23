package com.example.temiapp

import android.content.Context
import android.content.Intent
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.InputStream

class TemiWebServer(private val context: Context, port: Int) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // 🌟 CORS 敲門磚
        if (method == Method.OPTIONS) {
            val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
            response.addHeader("Access-Control-Allow-Origin", "*")
            response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
            response.addHeader("Access-Control-Allow-Headers", "Content-Type")
            return response
        }

        // 🌟 下載遙控 App 的通道 (讓護理機直接從 Temi 內網下載 APK)
        if (uri == "/download" && method == Method.GET) {
            return try {
                // 讀取 assets 資料夾中的 apk 檔案
                val inputStream: InputStream = context.assets.open("temi_remote.apk")
                val response = newChunkedResponse(Response.Status.OK, "application/vnd.android.package-archive", inputStream)
                // 強制瀏覽器下載檔案
                response.addHeader("Content-Disposition", "attachment; filename=\"temi_remote.apk\"")
                response
            } catch (e: Exception) {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "APK 檔案未找到，請確認已將 temi_remote.apk 放入 assets 資料夾中")
            }
        }

        // 🌟 狀態回報 API (讓手機 App 隨時掌握 Temi 狀態與最新版本)
        if (uri == "/api/status" && method == Method.GET) {
            val statusJson = JSONObject().apply {
                put("isBusy", AppStatus.isBusy)
                put("currentTask", AppStatus.currentTaskName)

                // 🌟 核心：宣告目前 Temi 肚子裡的 APK 是第 2 版！
                put("apkVersion", 4)
            }.toString()
            return createCorsResponse(Response.Status.OK, statusJson)
        }

        // 處理遠端控制 API 指令
        if (uri == "/api/command" && method == Method.POST) {
            try {
                val map = HashMap<String, String>()
                session.parseBody(map)

                // 修復亂碼：安全轉換 UTF-8
                var postData = map["postData"] ?: "{}"
                val contentType = session.headers["content-type"]?.lowercase() ?: ""

                if (postData.isNotEmpty() && !contentType.contains("utf-8")) {
                    postData = String(postData.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
                }

                val json = JSONObject(postData)
                val action = json.optString("action")

                // 🌟 強制中斷指令：直接呼叫 MainActivity 的中斷行為，並解除忙碌狀態
                if (action == "stop") {
                    val stopIntent = Intent(context, MainActivity::class.java).apply {
                        setAction("ACTION_STOP_TEMI")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(stopIntent)

                    AppStatus.isBusy = false
                    AppStatus.currentTaskName = "空閒"
                    return createCorsResponse(Response.Status.OK, """{"status": "success"}""")
                }

                // ⚠️ 忙碌檢查：若 Temi 正在執行任務，拒絕新指令
                if (AppStatus.isBusy) {
                    return createCorsResponse(Response.Status.OK, """{"status": "busy", "message": "${AppStatus.currentTaskName}"}""")
                }

                // 一收到正常指令，立刻全域鎖定
                AppStatus.isBusy = true

                // 🌟 加入 SINGLE_TOP 標籤，防止畫面重複摧毀重建導致的生命週期重疊 Bug
                val flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP

                when (action) {
                    "navigation" -> {
                        val target = json.optString("target")
                        AppStatus.currentTaskName = "前往 $target"

                        val intent = Intent(context, NavigationActivity::class.java).apply {
                            putExtra(NavigationActivity.EXTRA_TARGET_LOCATION, target)
                            putExtra(NavigationActivity.EXTRA_SOURCE_QUERY, "remote")
                            addFlags(flags)
                        }
                        context.startActivity(intent)
                    }

                    "full_tour" -> {
                        AppStatus.currentTaskName = "全區導覽"
                        val intent = Intent(context, NavigationActivity::class.java).apply {
                            putExtra("extra_is_full_tour", true)
                            addFlags(flags)
                        }
                        context.startActivity(intent)
                    }

                    "broadcast_patrol" -> {
                        val topic = json.optString("topic")
                        AppStatus.currentTaskName = "全區巡邏廣播"

                        val intent = Intent(context, BroadcastActivity::class.java).apply {
                            putExtra("extra_auto_start_text", topic)
                            addFlags(flags)
                        }
                        context.startActivity(intent)
                    }

                    "video_local" -> {
                        val key = json.optString("key")
                        AppStatus.currentTaskName = "原地衛教宣導"

                        val intent = Intent(context, VideoActivity::class.java).apply {
                            putExtra(VideoActivity.EXTRA_AUTOPLAY_KEY, key)
                            addFlags(flags)
                        }
                        context.startActivity(intent)
                    }

                    "ward_task" -> {
                        val room = json.optString("room")
                        val task = json.optString("task")
                        val content = json.optString("content")
                        AppStatus.currentTaskName = "前往 $room 執行 $task"

                        val intent = if (task == "video") {
                            Intent(context, NavigationActivity::class.java).apply {
                                putExtra(NavigationActivity.EXTRA_TARGET_LOCATION, room)
                                putExtra(NavigationActivity.EXTRA_START_VIDEO_ON_ARRIVAL, true)
                                putExtra(NavigationActivity.EXTRA_VIDEO_KEY, content)
                            }
                        } else {
                            Intent(context, BroadcastActivity::class.java).apply {
                                putExtra(BroadcastActivity.EXTRA_TARGET_ROOM, room)
                                putExtra("extra_auto_start_text", content)
                            }
                        }
                        intent.addFlags(flags)
                        context.startActivity(intent)
                    }
                }

                return createCorsResponse(Response.Status.OK, """{"status": "success"}""")

            } catch (e: Exception) {
                AppStatus.isBusy = false
                AppStatus.currentTaskName = "空閒"
                return createCorsResponse(Response.Status.INTERNAL_ERROR, """{"status": "error"}""")
            }
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found")
    }

    private fun createCorsResponse(status: Response.IStatus, jsonBody: String): Response {
        val response = newFixedLengthResponse(status, "application/json; charset=UTF-8", jsonBody)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }
}
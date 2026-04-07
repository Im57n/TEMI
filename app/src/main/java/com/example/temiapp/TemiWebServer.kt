package com.example.temiapp

import android.content.Context
import android.content.Intent
import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject
import java.io.InputStream

/**
 * Temi 內網 Web Server
 * - 提供手機端網頁 / App 下載
 * - 提供狀態查詢與遠端控制 API
 */
class TemiWebServer(
    private val context: Context,
    port: Int
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        // ===== CORS preflight =====
        if (method == Method.OPTIONS) {
            val response = newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "")
            addCorsHeaders(response)
            response.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS")
            response.addHeader("Access-Control-Allow-Headers", "Content-Type")
            return response
        }

        // ===== Web UI (assets/index.html) =====
        if ((uri == "/" || uri == "/index.html") && method == Method.GET) {
            return try {
                val inputStream: InputStream = context.assets.open("index.html")
                val response = newChunkedResponse(Response.Status.OK, "text/html; charset=UTF-8", inputStream)
                addCorsHeaders(response)
                response
            } catch (e: Exception) {
                createJsonCorsResponse(Response.Status.NOT_FOUND, """{"status":"error","message":"index.html not found in assets"}""")
            }
        }

        // ===== 下載遙控 App (assets/temi_remote.apk) =====
        if (uri == "/download" && method == Method.GET) {
            return try {
                val inputStream: InputStream = context.assets.open("temi_remote.apk")
                val response = newChunkedResponse(
                    Response.Status.OK,
                    "application/vnd.android.package-archive",
                    inputStream
                )
                response.addHeader("Content-Disposition", "attachment; filename=\"temi_remote.apk\"")
                addCorsHeaders(response)
                response
            } catch (e: Exception) {
                createJsonCorsResponse(
                    Response.Status.NOT_FOUND,
                    """{"status":"error","message":"temi_remote.apk not found in assets"}"""
                )
            }
        }

        // ===== 狀態回報 =====
        if (uri == "/api/status" && method == Method.GET) {
            val statusJson = JSONObject().apply {
                put("isBusy", AppStatus.isBusy)
                put("currentTask", AppStatus.currentTaskName)
            }.toString()
            return createJsonCorsResponse(Response.Status.OK, statusJson)
        }

        // ===== 遠端控制指令 =====
        if (uri == "/api/command" && method == Method.POST) {
            return handleCommand(session)
        }

        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404 Not Found")
    }

    private fun handleCommand(session: IHTTPSession): Response {
        return try {
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

            // ===== stop：強制中斷 =====
            if (action == "stop") {
                val stopIntent = Intent(context, MainActivity::class.java).apply {
                    this.action = ACTION_STOP_TEMI
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(stopIntent)
                AppStatus.setIdle()
                return createJsonCorsResponse(Response.Status.OK, """{"status":"success"}""")
            }

            // ===== Busy gate =====
            if (AppStatus.isBusy) {
                return createJsonCorsResponse(
                    Response.Status.OK,
                    """{"status":"busy","message":"${escapeJson(AppStatus.currentTaskName)}"}"""
                )
            }

            when (action) {
                "navigation" -> {
                    val target = json.optString("target")
                    // 相容 assets/index.html 舊版：只有 action 沒有 target 時，僅開啟導覽頁面
                    if (target.isBlank()) {
                        val intent = Intent(context, NavigationActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(intent)
                    } else {
                        AppStatus.setBusy("前往 $target")
                        val intent = Intent(context, NavigationActivity::class.java).apply {
                            putExtra(NavigationActivity.EXTRA_TARGET_LOCATION, target)
                            putExtra(NavigationActivity.EXTRA_SOURCE_QUERY, "remote")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        }
                        context.startActivity(intent)
                    }
                }

                // 相容 assets/index.html 舊版按鈕：只開啟頁面
                "broadcast" -> {
                    val intent = Intent(context, BroadcastActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                }

                "video" -> {
                    val intent = Intent(context, VideoActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                }

                "ward_guide" -> {
                    val intent = Intent(context, WardGuideActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                }

                "full_tour" -> {
                    AppStatus.setBusy("全區導覽")
                    val intent = Intent(context, NavigationActivity::class.java).apply {
                        putExtra(NavigationActivity.EXTRA_START_FULL_TOUR, true)
                        putExtra(NavigationActivity.EXTRA_SOURCE_QUERY, "remote")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                }

                "broadcast_patrol" -> {
                    val topic = json.optString("topic")
                    AppStatus.setBusy("全區巡邏廣播")
                    val intent = Intent(context, BroadcastActivity::class.java).apply {
                        putExtra(BroadcastActivity.EXTRA_AUTO_START_TEXT, topic)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                }

                "video_local" -> {
                    val key = json.optString("key")
                    AppStatus.setBusy("原地衛教宣導")
                    val intent = Intent(context, VideoActivity::class.java).apply {
                        putExtra(VideoActivity.EXTRA_AUTOPLAY_KEY, key)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                }

                "ward_task" -> {
                    val room = json.optString("room")
                    val task = json.optString("task")
                    val content = json.optString("content")
                    AppStatus.setBusy("前往 $room 執行 $task")

                    val intent = if (task == "video") {
                        Intent(context, NavigationActivity::class.java).apply {
                            putExtra(NavigationActivity.EXTRA_TARGET_LOCATION, room)
                            putExtra(NavigationActivity.EXTRA_SOURCE_QUERY, "remote")
                            putExtra(NavigationActivity.EXTRA_START_VIDEO_ON_ARRIVAL, true)
                            putExtra(NavigationActivity.EXTRA_VIDEO_KEY, content)
                        }
                    } else {
                        Intent(context, BroadcastActivity::class.java).apply {
                            putExtra(BroadcastActivity.EXTRA_TARGET_ROOM, room)
                            putExtra(BroadcastActivity.EXTRA_AUTO_START_TEXT, content)
                        }
                    }

                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    context.startActivity(intent)
                }

                else -> {
                    AppStatus.setIdle()
                    return createJsonCorsResponse(
                        Response.Status.BAD_REQUEST,
                        """{"status":"error","message":"unknown action"}"""
                    )
                }
            }

            createJsonCorsResponse(Response.Status.OK, """{"status":"success"}""")

        } catch (e: Exception) {
            AppStatus.setIdle()
            createJsonCorsResponse(Response.Status.INTERNAL_ERROR, """{"status":"error"}""")
        }
    }

    private fun createJsonCorsResponse(status: Response.IStatus, jsonBody: String): Response {
        val response = newFixedLengthResponse(status, "application/json; charset=UTF-8", jsonBody)
        addCorsHeaders(response)
        return response
    }

    private fun addCorsHeaders(response: Response) {
        response.addHeader("Access-Control-Allow-Origin", "*")
    }

    private fun escapeJson(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }

    companion object {
        const val ACTION_STOP_TEMI = "ACTION_STOP_TEMI"
    }
}

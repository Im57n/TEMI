package com.example.temiapp

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Window
import android.widget.Button
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest

class VideoActivity : AppCompatActivity() {

    companion object {
        const val MODE_HEALTH_EDU = "health_edu"

        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_ROOM = "extra_room"
        const val EXTRA_AUTOPLAY_KEY = "extra_autoplay_key"
        const val EXTRA_AFTER_ASK_AND_CHARGE = "extra_after_ask_and_charge"

        // 影片 key（你按鈕用這些 key）
        const val KEY_WARD_NOTICE = "ward_notice"
        const val KEY_ORAL_CLEAN = "oral_clean"
        const val KEY_CHEMO_TUBE = "chemo_tube"
    }

    private lateinit var robot: Robot

    private lateinit var videoView: VideoView
    private lateinit var btnPlayAll: Button
    private lateinit var btnVideo1: Button
    private lateinit var btnVideo2: Button
    private lateinit var btnStop: Button
    private lateinit var btnBack: Button

    private var mode: String = MODE_HEALTH_EDU
    private var room: String = ""
    private var autoplayKey: String = ""
    private var afterAskAndCharge: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        robot = Robot.getInstance()

        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_HEALTH_EDU
        room = intent.getStringExtra(EXTRA_ROOM).orEmpty().trim()
        autoplayKey = intent.getStringExtra(EXTRA_AUTOPLAY_KEY).orEmpty().trim()
        afterAskAndCharge = intent.getBooleanExtra(EXTRA_AFTER_ASK_AND_CHARGE, false)

        videoView = findViewById(R.id.video_view)
        btnPlayAll = findViewById(R.id.btn_play_all)
        btnVideo1 = findViewById(R.id.btn_video_1)
        btnVideo2 = findViewById(R.id.btn_video_2)
        btnStop = findViewById(R.id.btn_stop_video)
        btnBack = findViewById(R.id.btn_back)

        btnBack.setOnClickListener { finish() }

        videoView.setOnCompletionListener {
            if (afterAskAndCharge) showQuestionDialog()
        }

        btnStop.setOnClickListener {
            try { if (videoView.isPlaying) videoView.stopPlayback() } catch (_: Exception) {}
        }

        // ✅ 抵達後自動播放
        if (autoplayKey.isNotEmpty()) {
            playByKey(autoplayKey)
        }

        btnPlayAll.setOnClickListener { onPick(KEY_WARD_NOTICE) }
        btnVideo1.setOnClickListener { onPick(KEY_ORAL_CLEAN) }
        btnVideo2.setOnClickListener { onPick(KEY_CHEMO_TUBE) }
    }

    private fun onPick(key: String) {
        // ✅ 有 room 且目前不是自動播放狀態 => 「先選影片，再出發」
        if (room.isNotEmpty() && autoplayKey.isEmpty()) {
            val i = Intent(this, NavigationActivity::class.java).apply {
                putExtra(NavigationActivity.EXTRA_TARGET_LOCATION, room)
                putExtra(NavigationActivity.EXTRA_SOURCE_QUERY, "video_select")
                putExtra(NavigationActivity.EXTRA_START_VIDEO_ON_ARRIVAL, true)
                putExtra(NavigationActivity.EXTRA_VIDEO_MODE, mode)
                putExtra(NavigationActivity.EXTRA_VIDEO_KEY, key) // ✅ 把影片 key 帶去 Navigation
            }
            startActivity(i)
            finish()
        } else {
            // ✅ 只是單純在這頁播放
            playByKey(key)
        }
    }

    private fun playByKey(key: String) {
        // ✅ 這裡要對上你 res/raw 真的檔名（你截圖就是這三個）
        val rawName = when (key) {
            KEY_WARD_NOTICE -> "safety_guide"
            KEY_ORAL_CLEAN -> "oral_hygiene"
            KEY_CHEMO_TUBE -> "care"
            else -> ""
        }

        if (rawName.isBlank()) {
            Toast.makeText(this, "找不到影片 key：$key", Toast.LENGTH_SHORT).show()
            return
        }

        val resId = resources.getIdentifier(rawName, "raw", packageName)
        if (resId == 0) {
            Toast.makeText(this, "請確認 res/raw/$rawName.mp4 是否存在", Toast.LENGTH_LONG).show()
            return
        }

        val uri = Uri.parse("android.resource://$packageName/$resId")
        videoView.setVideoURI(uri)
        videoView.start()
    }

    private fun showQuestionDialog() {
        if (isFinishing || isDestroyed) return

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_question)
        dialog.setCancelable(false)

        val btnYes = dialog.findViewById<Button>(R.id.btn_yes)
        val btnNo = dialog.findViewById<Button>(R.id.btn_no)

        btnYes.setOnClickListener {
            robot.speak(TtsRequest.create("請問有什麼需要幫忙的呢？", false))
            dialog.dismiss()
            if (room.isNotEmpty()) {
                startActivity(Intent(this, RoomActionMenuActivity::class.java).apply {
                    putExtra(RoomActionMenuActivity.EXTRA_ROOM, room)
                })
            }
            finish()
        }

        btnNo.setOnClickListener {
            robot.speak(TtsRequest.create("好的，謝謝您，我現在回充電座。", false))
            try { robot.goTo("home base") } catch (_: Exception) {}
            dialog.dismiss()
            finish()
        }

        dialog.show()
        robot.speak(TtsRequest.create("請問是否還有其他問題？", false))
    }
}

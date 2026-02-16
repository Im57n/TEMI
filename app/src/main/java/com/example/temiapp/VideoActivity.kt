package com.example.temiapp

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.robotemi.sdk.Robot

class VideoActivity : AppCompatActivity() {

    private lateinit var videoView: VideoView
    private lateinit var robot: Robot

    // 1. 定義播放清單 (依序放入 R.raw.影片檔名)
    private val videoPlaylist = listOf(
        R.raw.water_dispenser,   // 第1部：飲水機
        R.raw.safety_guide,      // 第2部：逃生安全
        R.raw.ward_environment   // 第3部：病房環境
    )

    // 2. 記錄目前播到第幾部
    private var currentVideoIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video)

        robot = Robot.getInstance()
        videoView = findViewById(R.id.video_view)

        // 加入播放控制器
        val mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)

        // 3. 設定監聽器：當一部影片播完時，自動播下一部
        videoView.setOnCompletionListener {
            currentVideoIndex++ // 索引 +1
            playNextVideo()     // 播放下一部
        }

        setupButtons()
    }

    private fun setupButtons() {
        // --- 左邊按鈕：病房注意事項 (連續播放) ---
        findViewById<Button>(R.id.btn_play_all).setOnClickListener {
            currentVideoIndex = 0 // 重置從第一部開始
            playNextVideo()
            Toast.makeText(this, "開始連續播放衛教影片", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_video_1).setOnClickListener {
            val localVideoPath = "android.resource://" + packageName + "/" + R.raw.oral_hygiene
            playVideo(localVideoPath)
            Toast.makeText(this, "正在播放：口腔清潔", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_video_2).setOnClickListener {
            val localVideoPath = "android.resource://" + packageName + "/" + R.raw.care
            playVideo(localVideoPath)
            Toast.makeText(this, "正在播放：管路照護", Toast.LENGTH_SHORT).show()
        }

        // --- 右邊按鈕：停止播放 ---
        findViewById<Button>(R.id.btn_stop_video).setOnClickListener {
            if (videoView.isPlaying) {
                videoView.stopPlayback()
            }
            // 重置索引，下次按播放會重頭開始 (或視需求不重置)
            currentVideoIndex = 0
            Toast.makeText(this, "播放已停止", Toast.LENGTH_SHORT).show()
        }

        // 返回按鈕
        findViewById<Button>(R.id.btn_back).setOnClickListener {
            if (videoView.isPlaying) {
                videoView.stopPlayback()
            }
            finish()
        }
    }

    // 核心邏輯：播放清單中的下一部
    private fun playNextVideo() {
        // 檢查是否還有影片要播
        if (currentVideoIndex < videoPlaylist.size) {
            val resId = videoPlaylist[currentVideoIndex]
            val videoPath = "android.resource://" + packageName + "/" + resId
            playVideo(videoPath)

            // 顯示目前播放的進度提示 (可選)
            val currentNum = currentVideoIndex + 1
            val total = videoPlaylist.size
            // Toast.makeText(this, "播放第 $currentNum / $total 部影片", Toast.LENGTH_SHORT).show()
        } else {
            // 全部播完了
            Toast.makeText(this, "所有影片播放完畢", Toast.LENGTH_LONG).show()
            // 可以在這裡讓機器人說話，例如 robot.speak(...)
        }
    }

    private fun playVideo(url: String) {
        try {
            val uri = Uri.parse(url)
            videoView.setVideoURI(uri)
            videoView.start()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "影片播放失敗", Toast.LENGTH_SHORT).show()
        }
    }
}
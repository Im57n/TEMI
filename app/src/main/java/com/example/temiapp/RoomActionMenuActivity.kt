package com.example.temiapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class RoomActionMenuActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ROOM = "extra_room"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_room_action_menu)

        val room = intent.getStringExtra(EXTRA_ROOM).orEmpty()
        findViewById<TextView>(R.id.tv_room_title).text = "目前病房：$room"

        findViewById<Button>(R.id.btn_call_only).setOnClickListener {
            val i = Intent(this, NavigationActivity::class.java).apply {
                putExtra(NavigationActivity.EXTRA_TARGET_LOCATION, room)
                putExtra(NavigationActivity.EXTRA_SOURCE_QUERY, "call_only")
            }
            startActivity(i)
            finish() // 🌟 點擊後把自己關掉，確保任務結束後直接落在主畫面
        }

        findViewById<Button>(R.id.btn_health_edu).setOnClickListener {
            val i = Intent(this, VideoActivity::class.java).apply {
                putExtra(VideoActivity.EXTRA_MODE, VideoActivity.MODE_HEALTH_EDU)
                putExtra(VideoActivity.EXTRA_ROOM, room)
                putExtra(VideoActivity.EXTRA_AFTER_ASK_AND_CHARGE, true)
            }
            startActivity(i)
            finish() // 🌟 點擊後把自己關掉，確保任務結束後直接落在主畫面
        }

        findViewById<Button>(R.id.btn_policy).setOnClickListener {
            val i = Intent(this, BroadcastActivity::class.java).apply {
                putExtra(BroadcastActivity.EXTRA_TARGET_ROOM, room)
            }
            startActivity(i)
            finish() // 🌟 點擊後把自己關掉，確保任務結束後直接落在主畫面
        }

        findViewById<Button>(R.id.btn_back).setOnClickListener {
            finish()
        }
    }
}
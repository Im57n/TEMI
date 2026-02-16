package com.example.temiapp

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.robotemi.sdk.NlpResult
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener

class MainActivity : AppCompatActivity(),
    OnRobotReadyListener,
    Robot.NlpListener {

    private lateinit var robot: Robot

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        robot = Robot.getInstance()

        findViewById<Button>(R.id.btn_to_navigation).setOnClickListener {
            startActivity(Intent(this, NavigationActivity::class.java))
        }

        findViewById<Button>(R.id.btn_to_broadcast).setOnClickListener {
            startActivity(Intent(this, BroadcastActivity::class.java))
        }

        findViewById<Button>(R.id.btn_to_video).setOnClickListener {
            startActivity(Intent(this, VideoActivity::class.java))
        }

        findViewById<Button>(R.id.btn_to_ward_guide).setOnClickListener {
            startActivity(Intent(this, WardGuideActivity::class.java))
        }

        handleNlpIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNlpIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
        robot.addNlpListener(this)
    }

    override fun onStop() {
        robot.removeNlpListener(this)
        robot.removeOnRobotReadyListener(this)
        super.onStop()
    }

    override fun onRobotReady(isReady: Boolean) {
        if (isReady) robot.hideTopBar()
    }

    override fun onNlpCompleted(nlpResult: NlpResult) {
        val spoken = nlpResult.resolvedQuery?.trim().orEmpty()
        if (spoken.isBlank()) return

        val target = SpeechTargetParser.parseTargetLocation(spoken) ?: return

        startActivity(
            Intent(this, NavigationActivity::class.java).apply {
                putExtra(NavigationActivity.EXTRA_TARGET_LOCATION, target)
                putExtra(NavigationActivity.EXTRA_SOURCE_QUERY, spoken)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    private fun handleNlpIntent(intent: Intent?) {
        val extras = intent?.extras ?: return
        for (k in extras.keySet()) {
            val v = extras.get(k)
            if (v is NlpResult) {
                onNlpCompleted(v)
                return
            }
        }
    }
}

package com.example.temiapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.robotemi.sdk.Robot
import com.robotemi.sdk.Robot.WakeupWordListener
import com.robotemi.sdk.listeners.OnRobotReadyListener
import com.robotemi.sdk.voice.WakeupOrigin
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(),
    OnRobotReadyListener,
    WakeupWordListener {

    private lateinit var robot: Robot
    private lateinit var recorder: AudioRecorderHelper
    private val asrClient = HospitalAsrClient()
    private val ioExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var isCapturingVoiceCommand = false
    private var pendingAudioFile: File? = null
    private var stopRecordingRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        robot = Robot.getInstance()
        recorder = AudioRecorderHelper(this)

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
    }

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
        robot.addWakeupWordListener(this)
    }

    override fun onStop() {
        robot.removeWakeupWordListener(this)
        robot.removeOnRobotReadyListener(this)
        cancelVoiceCapture()
        super.onStop()
    }

    override fun onDestroy() {
        ioExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onRobotReady(isReady: Boolean) {
        if (isReady) robot.hideTopBar()
    }

    override fun onWakeupWord(wakeupWord: String, direction: Int, wakeupOrigin: WakeupOrigin) {
        Log.d(TAG, "onWakeupWord: wakeupWord=$wakeupWord, direction=$direction, origin=$wakeupOrigin")
        startHospitalAsrFlow()
    }

    private fun startHospitalAsrFlow() {
        if (isCapturingVoiceCommand) return

        if (!hasRecordAudioPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                REQUEST_RECORD_AUDIO
            )
            Toast.makeText(this, "請先允許錄音權限", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            pendingAudioFile = recorder.startRecording()
            isCapturingVoiceCommand = true
            Toast.makeText(this, "請說出目的地", Toast.LENGTH_SHORT).show()

            stopRecordingRunnable?.let { mainHandler.removeCallbacks(it) }
            stopRecordingRunnable = Runnable { stopAndSendToHospitalAsr() }
            mainHandler.postDelayed(
                stopRecordingRunnable!!,
                HospitalAsrConfig.RECORDING_MILLIS
            )
        } catch (e: Exception) {
            isCapturingVoiceCommand = false
            pendingAudioFile = null
            Log.e(TAG, "startHospitalAsrFlow failed", e)
            Toast.makeText(this, "無法開始錄音：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun stopAndSendToHospitalAsr() {
        stopRecordingRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRecordingRunnable = null

        val audioFile = recorder.stopRecording(deleteBrokenFile = true)
        isCapturingVoiceCommand = false
        pendingAudioFile = null

        if (audioFile == null || !audioFile.exists() || audioFile.length() <= 0L) {
            Toast.makeText(this, "錄音失敗，請再說一次", Toast.LENGTH_SHORT).show()
            return
        }

        ioExecutor.execute {
            try {
                val result = asrClient.transcribe(audioFile)
                val transcript = result.transcript
                Log.d(TAG, "ASR transcript=$transcript")
                runOnUiThread {
                    handleAsrTranscript(transcript)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Hospital ASR failed", e)
                runOnUiThread {
                    Toast.makeText(
                        this,
                        "ASR 失敗：${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                runCatching { audioFile.delete() }
            }
        }
    }

    private fun handleAsrTranscript(transcript: String) {
        val spoken = transcript.trim()
        if (spoken.isBlank()) {
            Toast.makeText(this, "沒有辨識到語音內容", Toast.LENGTH_SHORT).show()
            return
        }

        val target = SpeechTargetParser.parseTargetLocation(spoken)
        if (target == null) {
            Toast.makeText(this, "辨識結果：$spoken", Toast.LENGTH_LONG).show()
            return
        }

        startActivity(
            Intent(this, NavigationActivity::class.java).apply {
                putExtra(NavigationActivity.EXTRA_TARGET_LOCATION, target)
                putExtra(NavigationActivity.EXTRA_SOURCE_QUERY, spoken)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        )
    }

    private fun cancelVoiceCapture() {
        stopRecordingRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRecordingRunnable = null
        pendingAudioFile?.let { runCatching { it.delete() } }
        pendingAudioFile = null
        recorder.cancelRecording()
        isCapturingVoiceCommand = false
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO) {
            val granted = grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (granted) {
                startHospitalAsrFlow()
            } else {
                Toast.makeText(this, "未授權錄音，無法使用 hey temi 語音導覽", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_RECORD_AUDIO = 1001
    }
}
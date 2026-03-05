package com.example.temiapp

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.robotemi.sdk.Robot
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener

class BroadcastActivity : AppCompatActivity(),
    OnRobotReadyListener,
    OnGoToLocationStatusChangedListener,
    Robot.TtsListener {

    private lateinit var robot: Robot
    private lateinit var listView: ListView
    private var selectedText: String? = null

    private var isPatrolling = false

    // ==========================================
    //           修改重點：巡邏路線設定
    // ==========================================
    // 邏輯：起點(護理站) -> A -> B -> C -> D -> 終點(護理站)
    private val patrolRoute = listOf(
        "護理站",  // 第1步：先確保在起點 (若已在會直接跳下一步)
        "廣播a",   // 第2步
        "廣播b",   // 第3步
        "廣播c",   // 第4步
        "廣播d",   // 第5步
        "護理站"   // 第6步：回到護理站結束
    )

    // 目前走到路線的第幾個點 (索引)
    private var currentRouteIndex = 0

    // 廣播內容選項
    private val broadcastTopics = mapOf(
        "領口罩須知" to "提醒您，進入本院區請全程配戴口罩，保護您我安全。",
        "安全針具定義" to "安全針具定義為：醫療機構對於所屬醫事人員執行直接接觸病人體液或血液之醫療處置時，所使用之防護器具。",
        "狂犬病宣導" to "狂犬病是由病毒引起的神經性疾病，請務必定期為家中寵物施打疫苗。",
        "住院安寧廣播" to "現在時間晚上九點，請降低音量，給予病患安靜的休息空間。"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_broadcast)

        robot = Robot.getInstance()
        listView = findViewById(R.id.listview_broadcast_topics)


        targetRoom = intent.getStringExtra(EXTRA_TARGET_ROOM).orEmpty().trim()
        isRoomRouteBroadcast = targetRoom.isNotEmpty()

        setupListView()
        setupButtons()
    }

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
        robot.addOnGoToLocationStatusChangedListener(this)
        robot.addTtsListener(this)
    }

    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
        robot.removeOnGoToLocationStatusChangedListener(this)
        robot.removeTtsListener(this)
        stopBroadcast()
    }

    override fun onRobotReady(isReady: Boolean) { }

    private fun setupListView() {
        val adapter = ArrayAdapter(
            this,
            R.layout.list_item_large,
            broadcastTopics.keys.toList()
        )

        listView.adapter = adapter
        listView.choiceMode = ListView.CHOICE_MODE_SINGLE
        listView.setOnItemClickListener { _, _, position, _ ->
            val topicTitle = adapter.getItem(position)
            selectedText = broadcastTopics[topicTitle]
            Toast.makeText(this, "已選擇：$topicTitle", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupButtons() {
        // 開始廣播
        findViewById<Button>(R.id.btn_start_broadcast).setOnClickListener { startBroadcast() }

        // 停止並返回
        findViewById<Button>(R.id.btn_stop_broadcast).setOnClickListener {
            stopBroadcast()
            finish()
        }

        // 右上角返回
        findViewById<Button>(R.id.btn_back).setOnClickListener {
            stopBroadcast()
            finish()
        }
    }

    private fun startBroadcast() {
        if (selectedText == null) {
            Toast.makeText(this, "請先選擇要廣播的內容", Toast.LENGTH_SHORT).show()
            return
        }
        if (!robot.isReady) return

        // 初始化狀態
        isPatrolling = true
        currentRouteIndex = 0 // 重置到路線的第一個點

        // ✅ 病房導覽政策模式：從目前位置直接去目標病房，途中持續播報
        if (isRoomRouteBroadcast) {
            val goToTarget = normalizeRoomTarget(targetRoom) // 可選：處理 826A/826a
            robot.goTo(goToTarget)
            Toast.makeText(this, "開始政策宣導，前往：$goToTarget（途中持續播報）", Toast.LENGTH_SHORT).show()
            speakLoop()
            return
        }

        // ✅ 原本主畫面政策宣導：維持巡邏模式
        val firstLocation = patrolRoute[0]
        robot.goTo(firstLocation)
        Toast.makeText(this, "開始巡邏廣播，前往：$firstLocation", Toast.LENGTH_SHORT).show()

        speakLoop()
    }

    private fun speakLoop() {
        // 只要還在巡邏模式，就一直講
        if (isPatrolling && selectedText != null) {
            robot.speak(TtsRequest.create(selectedText!!, false))
        }
    }

    // TTS 狀態監聽：講完了就再講一次
    override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
        if (ttsRequest.status == TtsRequest.Status.COMPLETED && isPatrolling) {
            // 講完一句後，馬上接續講下一句 (形成循環)
            speakLoop()
        }
    }

    // 導航狀態監聽：自動接續下一個地點
    override fun onGoToLocationStatusChanged(
        location: String,
        status: String,
        descriptionId: Int,
        description: String
    ) {
        if (!isPatrolling) return

        // ✅ 病房導覽政策模式：抵達病房就結束
        if (isRoomRouteBroadcast) {
            if (status.equals("complete", ignoreCase = true) &&
                location.equals(targetRoom, ignoreCase = true)
            ) {
                Toast.makeText(this, "已抵達 $location，政策宣導結束", Toast.LENGTH_SHORT).show()
                stopBroadcast()
                return
            }

            if (status.equals("abort", ignoreCase = true)) {
                Toast.makeText(this, "導航中斷，停止政策宣導", Toast.LENGTH_LONG).show()
                stopBroadcast()
            }
            return
        }

        // ✅ 原本巡邏模式（保留）
        if (status.equals("complete", ignoreCase = true)) {
            currentRouteIndex++
            if (currentRouteIndex < patrolRoute.size) {
                val nextLocation = patrolRoute[currentRouteIndex]
                Toast.makeText(this, "抵達 $location，轉往：$nextLocation", Toast.LENGTH_SHORT).show()
                robot.goTo(nextLocation)
            } else {
                Toast.makeText(this, "巡邏結束，已回到護理站！", Toast.LENGTH_LONG).show()
                stopBroadcast()
            }
        } else if (status.equals("abort", ignoreCase = true)) {
            Toast.makeText(this, "導航中斷，停止廣播", Toast.LENGTH_LONG).show()
            stopBroadcast()
        }
    }

    companion object {
        const val EXTRA_TARGET_ROOM = "extra_target_room"
    }

    private var targetRoom: String = ""
    private var isRoomRouteBroadcast = false

    private fun normalizeRoomTarget(raw: String): String {
        val clean = raw.trim()
        val upper = clean.uppercase()
        return if (Regex("^8\\d{2}[ABC]?$").matches(upper)) upper.lowercase() else clean
    }

    private fun stopBroadcast() {
        isPatrolling = false
        currentRouteIndex = 0
        robot.stopMovement()         // 停止輪子
        robot.cancelAllTtsRequests() // 停止說話
    }
}
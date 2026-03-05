package com.example.temiapp

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.robotemi.sdk.Robot
import com.robotemi.sdk.SttLanguage
import com.robotemi.sdk.TtsRequest
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener

class NavigationActivity : AppCompatActivity(), OnRobotReadyListener {

    companion object {
        const val EXTRA_TARGET_LOCATION = "extra_target_location"
        const val EXTRA_SOURCE_QUERY = "extra_source_query"
        const val EXTRA_START_VIDEO_ON_ARRIVAL = "extra_start_video_on_arrival"
        const val EXTRA_VIDEO_MODE = "extra_video_mode"
        const val EXTRA_VIDEO_KEY = "extra_video_key"
        private const val TAG = "NavigationActivity"
    }

    private var pendingAutoTarget: String? = null
    private var startVideoOnArrival: Boolean = false
    private var videoMode: String = VideoActivity.MODE_HEALTH_EDU
    private var videoKey: String = ""

    private var activeTarget: String? = null
    private var arrivalConsumed: Boolean = false
    private var isRobotReady: Boolean = false

    private lateinit var robot: Robot
    private var isTouring = false
    private var isReturningToStart = false

    private var currentDialog: Dialog? = null

    private lateinit var layoutOverlay: RelativeLayout
    private lateinit var imgOverlay: ImageView
    private lateinit var txtSubtitle: TextView

    private val handler = Handler(Looper.getMainLooper())

    // --- 導覽文字設定 ---
    private val nursingStationText = "這裡是護理站和諮詢站，若您有任何醫療需求，請諮詢護理站人員；若您需要辦理出院或查詢住院費用請至諮詢站諮詢書記。"
    private val dirtyRoomText = "這裡是污物室，請依垃圾分類標示丟棄正確物品、衣服棉被請放入藍色污衣桶、尿布請丟棄至洗手台旁尿布垃圾桶，非醫療廢棄物請至配膳室執行垃圾分類。"
    private val pantryRoomText = "這裡是配膳室，為了愛護地球，請您依垃圾分類標示完成垃圾分類，廚餘請倒入廚餘桶；這裡也有製冰機，僅供冰敷或冰枕使用，不可以食用；而飲水機半夜會有消毒時間，取用時請注意時間。"
    private val linenText = "這裡是被服用品車，請依標示自行拿取所需衣物、被套、枕頭套，再次提醒請不要囤積被服，若您需要更換棉被，請找護理師，若您需要吹風機請自行取用，使用完畢請歸位。"
    private val wheelchairText = "這裡是輪椅放置處，若您需要使用輪椅時，請自行取用，使用完畢請主動歸位。"
    private val scaleText = "這裡是身高體重計、坐磅放置處，病房每週五需測量體重，若您需使用身高體重計，請護理師協助掃描手圈條碼，若使用坐磅或站式體重計，請使用後歸位，使用坐磅時請注意輪子需固定。"

    private val goToLocationStatusListener = object : OnGoToLocationStatusChangedListener {
        override fun onGoToLocationStatusChanged(
            location: String,
            status: String,
            descriptionId: Int,
            description: String
        ) {
            // 若已停止且 overlay 未顯示，忽略完成回報
            if (!isTouring && !layoutOverlay.isShown) return

            if (status.equals("complete", ignoreCase = true)) {
                handleArrivalLogic(location.trim())
            }
        }
    }

    private val asrListener = object : Robot.AsrListener {
        override fun onAsrResult(asrResult: String, sttLanguage: SttLanguage) {
            val text = asrResult.replace(Regex("[\\s\\u3000]+"), "")
            val target = SpeechTargetParser.parseTargetLocation(asrResult)

            runOnUiThread {
                try {
                    when {
                        target != null -> startGoToLocation(target, false)
                        text.contains("全區導覽") || text.contains("介紹環境") ->
                            findViewById<Button>(R.id.btn_full_tour).performClick()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            try {
                robot.finishConversation()
            } catch (_: Exception) {
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)

        startVideoOnArrival = intent.getBooleanExtra(EXTRA_START_VIDEO_ON_ARRIVAL, false)
        videoMode = intent.getStringExtra(EXTRA_VIDEO_MODE) ?: VideoActivity.MODE_HEALTH_EDU
        videoKey = intent.getStringExtra(EXTRA_VIDEO_KEY).orEmpty()
        arrivalConsumed = false

        robot = Robot.getInstance()

        layoutOverlay = findViewById(R.id.layout_overlay)
        imgOverlay = findViewById(R.id.img_overlay_location)
        txtSubtitle = findViewById(R.id.txt_overlay_subtitle)

        setupNavigationButtons()
        handleAutoNavigationFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)

        startVideoOnArrival = intent.getBooleanExtra(EXTRA_START_VIDEO_ON_ARRIVAL, false)
        videoMode = intent.getStringExtra(EXTRA_VIDEO_MODE) ?: VideoActivity.MODE_HEALTH_EDU
        videoKey = intent.getStringExtra(EXTRA_VIDEO_KEY).orEmpty()
        arrivalConsumed = false

        handleAutoNavigationFromIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        robot.addOnRobotReadyListener(this)
        robot.addOnGoToLocationStatusChangedListener(goToLocationStatusListener)
        robot.addAsrListener(asrListener)
    }

    override fun onStop() {
        super.onStop()
        robot.removeOnRobotReadyListener(this)
        robot.removeOnGoToLocationStatusChangedListener(goToLocationStatusListener)
        robot.removeAsrListener(asrListener)
        forceStopEverything()
    }

    override fun onRobotReady(isReady: Boolean) {
        isRobotReady = isReady
        if (!isReady) return

        pendingAutoTarget?.let { target ->
            startGoToLocation(target, false)
            pendingAutoTarget = null
        }
    }

    private fun handleAutoNavigationFromIntent(intent: Intent?) {
        val i = intent ?: return

        val raw = i.getStringExtra(EXTRA_TARGET_LOCATION)?.trim().orEmpty()
        if (raw.isBlank()) return

        val target = normalizeTargetName(raw)
        val sourceQuery = i.getStringExtra(EXTRA_SOURCE_QUERY).orEmpty()
        Log.d(TAG, "auto target=$target, query=$sourceQuery")

        activeTarget = target

        if (isRobotReady || robot.isReady) {
            startGoToLocation(target, false)
        } else {
            pendingAutoTarget = target
            Toast.makeText(this, "已收到語音指令：$target", Toast.LENGTH_SHORT).show()
        }
    }

    private fun normalizeTargetName(raw: String): String {
        val trimmed = raw.trim()
            .replace("汙物室", "污物室")

        // 先處理充電座 / home base（避免空白被吃掉）
        val lowerNoSpace = trimmed
            .replace(Regex("[\\s\\u3000]+"), "")
            .lowercase()

        if (trimmed == "充電座" || lowerNoSpace == "homebase") {
            return "home base"
        }

        val clean = trimmed.replace(Regex("[\\s\\u3000]+"), "")
        val upper = clean.uppercase()
        if (Regex("^8\\d{2}[ABC]?$").matches(upper)) return upper.lowercase()

        return clean
    }

    private fun forceStopEverything() {
        robot.stopMovement()
        robot.cancelAllTtsRequests()
        handler.removeCallbacksAndMessages(null)

        isTouring = false
        isReturningToStart = false

        hideOverlayUI()

        currentDialog?.dismiss()
        currentDialog = null
    }

    private fun setupNavigationButtons() {
        val btnNursing = findViewById<Button>(R.id.btn_loc_nursing)
        val btnPantry = findViewById<Button>(R.id.btn_loc_pantry)
        val btnDirty = findViewById<Button>(R.id.btn_loc_dirty)
        val btnLinen = findViewById<Button>(R.id.btn_loc_linen)
        val btnWheelchair = findViewById<Button>(R.id.btn_loc_wheelchair)
        val btnScale = findViewById<Button>(R.id.btn_loc_scale)
        val btnCharge = findViewById<Button>(R.id.btn_go_charge)
        val btnFullTour = findViewById<Button>(R.id.btn_full_tour)
        val btnBack = findViewById<Button>(R.id.btn_back)
        val btnSkip = findViewById<Button>(R.id.btn_skip)

        btnSkip.setOnClickListener { forceStopEverything() }

        btnNursing.setOnClickListener { startGoToLocation("護理站", false) }
        btnPantry.setOnClickListener { startGoToLocation("配膳室", false) }
        btnDirty.setOnClickListener { startGoToLocation("污物室", false) }
        btnLinen.setOnClickListener { startGoToLocation("被服車", false) }
        btnWheelchair.setOnClickListener { startGoToLocation("輪椅區", false) }
        btnScale.setOnClickListener { startGoToLocation("體重計", false) }
        btnCharge.setOnClickListener { startGoToLocation("充電座", false) }

        btnFullTour.setOnClickListener {
            if (!robot.isReady) return@setOnClickListener
            isTouring = true
            isReturningToStart = false

            showOverlayUI("開始全區導覽，前往護理站...", R.drawable.nursing_station_img)
            robot.speak(TtsRequest.create("開始全區導覽，現在前往護理站", false))
            robot.goTo("護理站")
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun startGoToLocation(locationName: String, tourMode: Boolean) {
        handler.removeCallbacksAndMessages(null)
        if (!robot.isReady) return

        isTouring = tourMode
        if (!tourMode) isReturningToStart = false

        val normalizedLocation = normalizeTargetName(locationName)
        activeTarget = normalizedLocation   // <- 補這行

        val displayName = if (normalizedLocation == "home base") "充電座" else normalizedLocation

        if (!tourMode) {
            showOverlayUI("正在前往：$displayName...", R.drawable.nursing_station_img)
        }

        robot.speak(TtsRequest.create("現在前往$displayName", false))
        robot.goTo(normalizedLocation)
        Toast.makeText(this, "前往 $displayName", Toast.LENGTH_SHORT).show()
    }

    private fun handleArrivalLogic(location: String) {
        if (isTouring && location == "護理站" && isReturningToStart) {
            isTouring = false
            isReturningToStart = false
            runOnUiThread {
                Toast.makeText(this, "全區導覽結束", Toast.LENGTH_SHORT).show()
                hideOverlayUI()
                showCustomDialog()
            }
            return
        }

        // ✅ 病房模式：抵達目標病房後直接開 VideoActivity（自動播放）
        if (startVideoOnArrival && !arrivalConsumed) {
            val target = activeTarget?.trim().orEmpty()
            if (target.isNotEmpty() && location.equals(target, ignoreCase = true)) {
                arrivalConsumed = true
                val i = Intent(this, VideoActivity::class.java).apply {
                    putExtra(VideoActivity.EXTRA_MODE, videoMode)
                    putExtra(VideoActivity.EXTRA_ROOM, target)
                    putExtra(VideoActivity.EXTRA_AUTOPLAY_KEY, videoKey)
                    putExtra(VideoActivity.EXTRA_AFTER_ASK_AND_CHARGE, true)
                }
                startActivity(i)
                finish()
                return
            }
        }

        val onActionComplete = { checkNextMove(location) }

        val locationData = when (location) {
            "護理站" -> Pair(nursingStationText, R.drawable.nursing_station_img)
            "體重計" -> Pair(scaleText, R.drawable.scale_img)
            "被服車" -> Pair(linenText, R.drawable.linen_img)
            "污物室", "汙物室" -> Pair(dirtyRoomText, R.drawable.dirty_room_img)
            "配膳室" -> Pair(pantryRoomText, R.drawable.pantry_img)
            "輪椅區" -> Pair(wheelchairText, R.drawable.wheelchair_img)
            else -> null
        }

        if (locationData != null) {
            val (speakText, imageResId) = locationData
            showOverlayUI(speakText, imageResId)
            speakWithCallback(speakText, onActionComplete)
        } else {
            if (location == "home base" || location == "充電座") {
                robot.speak(TtsRequest.create("很高興為您服務，我現在要充電了。"))
            } else {
                robot.speak(TtsRequest.create("$location 到了。"))
            }
            if (!isTouring) hideOverlayUI()
        }
    }

    private fun speakWithCallback(text: String, onComplete: () -> Unit) {
        val ttsListener = object : Robot.TtsListener {
            override fun onTtsStatusChanged(ttsRequest: TtsRequest) {
                if (ttsRequest.status == TtsRequest.Status.COMPLETED ||
                    ttsRequest.status == TtsRequest.Status.ERROR
                ) {
                    robot.removeTtsListener(this)
                    runOnUiThread {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (isTouring || layoutOverlay.visibility == View.VISIBLE) {
                                onComplete()
                            }
                        }, 500)
                    }
                }
            }
        }
        robot.addTtsListener(ttsListener)
        robot.speak(TtsRequest.create(text, false))
    }

    private fun checkNextMove(currentLocation: String) {
        if (isTouring) {
            val nextLocation = getNextTourLocation(currentLocation)
            if (nextLocation != null) {
                if (nextLocation == "護理站") isReturningToStart = true

                runOnUiThread {
                    txtSubtitle.text = "即將前往：$nextLocation..."
                    Toast.makeText(this, "導覽繼續，2秒後前往：$nextLocation", Toast.LENGTH_SHORT).show()
                }

                handler.postDelayed({
                    robot.speak(TtsRequest.create("現在前往$nextLocation", false))
                    robot.goTo(nextLocation)
                }, 2000)
            } else {
                isTouring = false
                hideOverlayUI()
            }
        } else {
            hideOverlayUI()
            if (currentLocation == "護理站") runOnUiThread { showCustomDialog() }
        }
    }

    private fun getNextTourLocation(current: String): String? {
        return when (current) {
            "護理站" -> "體重計"
            "體重計" -> "污物室"
            "污物室", "汙物室" -> "被服車"
            "被服車" -> "配膳室"
            "配膳室" -> "輪椅區"
            "輪椅區" -> "護理站"
            else -> null
        }
    }

    private fun showOverlayUI(subtitle: String, imageResId: Int) {
        runOnUiThread {
            txtSubtitle.text = subtitle
            imgOverlay.setImageResource(imageResId)
            layoutOverlay.visibility = View.VISIBLE
        }
    }

    private fun hideOverlayUI() {
        runOnUiThread { layoutOverlay.visibility = View.GONE }
    }

    private fun showCustomDialog() {
        if (isFinishing || isDestroyed) return
        val dialog = Dialog(this)
        currentDialog = dialog

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_question)
        dialog.setCancelable(false)

        val btnYes = dialog.findViewById<Button>(R.id.btn_yes)
        val btnNo = dialog.findViewById<Button>(R.id.btn_no)

        btnYes.setOnClickListener {
            robot.speak(TtsRequest.create("請問有什麼需要幫忙的呢？", false))
            dialog.dismiss()
            currentDialog = null
        }

        btnNo.setOnClickListener {
            robot.speak(TtsRequest.create("好的，謝謝您", false))
            dialog.dismiss()
            currentDialog = null
        }

        dialog.show()
        robot.speak(TtsRequest.create("請問是否還有其他問題？", false))
    }
}

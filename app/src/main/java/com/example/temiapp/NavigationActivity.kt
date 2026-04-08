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
import com.robotemi.sdk.listeners.OnGoToLocationStatusChangedListener
import com.robotemi.sdk.listeners.OnRobotReadyListener

class NavigationActivity : AppCompatActivity(), OnRobotReadyListener {

    companion object {
        const val EXTRA_TARGET_LOCATION = "extra_target_location"
        const val EXTRA_SOURCE_QUERY = "extra_source_query"
        const val EXTRA_START_VIDEO_ON_ARRIVAL = "extra_start_video_on_arrival"
        const val EXTRA_VIDEO_MODE = "extra_video_mode"
        const val EXTRA_VIDEO_KEY = "extra_video_key"
        // 🌟 修正：為了能接收到 TemiWebServer 傳來的指令，改為 "extra_is_full_tour"
        const val EXTRA_START_FULL_TOUR = "extra_is_full_tour"
        private const val TAG = "NavigationActivity"
    }

    private var pendingAutoTarget: String? = null
    private var startVideoOnArrival: Boolean = false
    private var videoMode: String = VideoActivity.MODE_HEALTH_EDU
    private var videoKey: String = ""
    private var pendingStartFullTour: Boolean = false

    private var activeTarget: String? = null
    private var arrivalConsumed: Boolean = false
    private var isRobotReady: Boolean = false

    private lateinit var robot: Robot
    private lateinit var speechManager: SpeechManager
    private var isTouring = false
    private var isReturningToStart = false

    private var currentDialog: Dialog? = null

    private lateinit var layoutOverlay: RelativeLayout
    private lateinit var imgOverlay: ImageView
    private lateinit var txtSubtitle: TextView

    private val handler = Handler(Looper.getMainLooper())

    private fun normNoSpace(s: String): String =
        s.replace(Regex("[\\s\\u3000]+"), "").lowercase()

    private fun resolveGoToName(internalKey: String): String {
        val locs = try {
            robot.locations
        } catch (_: Exception) {
            emptyList<String>()
        }

        if (locs.isEmpty()) return internalKey
        if (locs.contains(internalKey)) return internalKey

        val m = Regex("^(8\\d{2})([a-z])$").find(internalKey)
        if (m != null) {
            val prefix = m.groupValues[1]
            val suf = m.groupValues[2]
            val cand1 = "$prefix $suf"
            val cand2 = "$prefix ${suf.uppercase()}"
            if (locs.contains(cand1)) return cand1
            if (locs.contains(cand2)) return cand2
        }

        val hit = locs.firstOrNull { normNoSpace(it) == normNoSpace(internalKey) }
        return hit ?: internalKey
    }

    private fun isWardLocation(name: String): Boolean {
        val key = name.replace(Regex("[\\s\\u3000]+"), "").uppercase()
        return Regex("^8\\d{2}[ABC]?$").matches(key)
    }

    private fun wardKey(name: String): String =
        name.replace(Regex("[\\s\\u3000]+"), "").lowercase()

    private val nursingStationText =
        "這裡是護理站和諮詢站，若您有任何醫療需求，請諮詢護理站人員；若您需要辦理出院或查詢住院費用請至諮詢站諮詢書記。"
    private val dirtyRoomText =
        "這裡是污物室，請依垃圾分類標示丟棄正確物品、衣服棉被請放入藍色污衣桶、尿布請丟棄至洗手台旁尿布垃圾桶，非醫療廢棄物請至配膳室執行垃圾分類。"
    private val pantryRoomText =
        "這裡是配膳室，為了愛護地球，請您依垃圾分類標示完成垃圾分類，廚餘請倒入廚餘桶；這裡也有製冰機，僅供冰敷或冰枕使用，不可以食用；而飲水機半夜會有消毒時間，取用時請注意時間。"
    private val linenText =
        "這裡是被服用品車，請依標示自行拿取所需衣物、被套、枕頭套，再次提醒請不要囤積被服，若您需要更換棉被，請找護理師，若您需要吹風機請自行取用，使用完畢請歸位。"
    private val wheelchairText =
        "這裡是輪椅放置處，若您需要使用輪椅時，請自行取用，使用完畢請主動歸位。"
    private val scaleText =
        "這裡是身高體重計、坐磅放置處，病房每週五需測量體重，若您需使用身高體重計，請護理師協助掃描手圈條碼，若使用坐磅或站式體重計，請使用後歸位，使用坐磅時請注意輪子需固定。"

    private val goToLocationStatusListener = object : OnGoToLocationStatusChangedListener {
        override fun onGoToLocationStatusChanged(
            location: String,
            status: String,
            descriptionId: Int,
            description: String
        ) {
            if (!isTouring && !layoutOverlay.isShown) return

            if (status.equals("complete", ignoreCase = true)) {
                handleArrivalLogic(location.trim())
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_navigation)

        val initialTarget = intent.getStringExtra(EXTRA_TARGET_LOCATION)?.trim().orEmpty()
        val isFullTour = intent.getBooleanExtra(EXTRA_START_FULL_TOUR, false)

        // 🌟 修正：替換回原本正確的狀態管理寫法 (因為沒有 setBusy / setIdle 函式)
        if (initialTarget.isNotEmpty() || isFullTour) {
            AppStatus.isBusy = true
            AppStatus.currentTaskName = if (isFullTour) "全區導覽" else "準備前往 $initialTarget"
        } else {
            AppStatus.isBusy = false
            AppStatus.currentTaskName = "空閒"
        }

        startVideoOnArrival = intent.getBooleanExtra(EXTRA_START_VIDEO_ON_ARRIVAL, false)
        videoMode = intent.getStringExtra(EXTRA_VIDEO_MODE) ?: VideoActivity.MODE_HEALTH_EDU
        videoKey = intent.getStringExtra(EXTRA_VIDEO_KEY).orEmpty()
        arrivalConsumed = false

        robot = Robot.getInstance()
        speechManager = SpeechManager(this, robot)

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
    }

    override fun onStop() {
        robot.removeOnRobotReadyListener(this)
        robot.removeOnGoToLocationStatusChangedListener(goToLocationStatusListener)
        forceStopEverything()
        super.onStop()
    }

    override fun onDestroy() {
        speechManager.shutdown()
        AppStatus.isBusy = false
        AppStatus.currentTaskName = "空閒"
        super.onDestroy()
    }

    override fun onRobotReady(isReady: Boolean) {
        isRobotReady = isReady
        if (!isReady) return

        if (pendingStartFullTour) {
            startFullTour()
            pendingStartFullTour = false
        }

        pendingAutoTarget?.let { target ->
            startGoToLocation(target, false)
            pendingAutoTarget = null
        }
    }

    private fun handleAutoNavigationFromIntent(intent: Intent?) {
        val i = intent ?: return

        if (i.getBooleanExtra(EXTRA_START_FULL_TOUR, false)) {
            if (isRobotReady || robot.isReady) {
                startFullTour()
            } else {
                pendingStartFullTour = true
                Toast.makeText(this, "已收到指令：開始全區導覽", Toast.LENGTH_SHORT).show()
            }
            return
        }

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
            Toast.makeText(this, "已收到指令：$target", Toast.LENGTH_SHORT).show()
        }
    }

    private fun normalizeTargetName(raw: String): String {
        val trimmed = raw.trim().replace("汙物室", "污物室")

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
        speechManager.stop()
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
            startFullTour()
        }

        btnBack.setOnClickListener { finish() }
    }

    private fun startFullTour() {
        if (!robot.isReady) return

        AppStatus.isBusy = true
        AppStatus.currentTaskName = "全區導覽"

        isTouring = true
        isReturningToStart = false
        showOverlayUI("開始全區導覽，前往護理站...", R.drawable.nursing_station_img)
        speechManager.speak("開始全區導覽，現在前往護理站")
        robot.goTo("護理站")
    }

    private fun startGoToLocation(locationName: String, tourMode: Boolean) {
        handler.removeCallbacksAndMessages(null)
        if (!robot.isReady) return

        val normalizedLocation = normalizeTargetName(locationName)

        // 🔥 [終極攔截防呆] (保留學長邏輯)
        if (normalizedLocation == "全區導覽" && !tourMode) {
            startFullTour()
            return
        }

        if (tourMode) {
            AppStatus.isBusy = true
            AppStatus.currentTaskName = "全區導覽"
        } else {
            val displayName = if (normalizedLocation == "home base") "充電座" else normalizedLocation
            AppStatus.isBusy = true
            AppStatus.currentTaskName = "前往 $displayName"
        }

        isTouring = tourMode
        if (!tourMode) isReturningToStart = false

        activeTarget = normalizedLocation

        val goToName = resolveGoToName(normalizedLocation)
        val displayName = if (normalizedLocation == "home base") "充電座" else normalizedLocation

        if (!tourMode) {
            showOverlayUI("正在前往：$displayName...", R.drawable.nursing_station_img)
        }

        speechManager.speak("現在前往$displayName")
        robot.goTo(goToName)
        Toast.makeText(this, "前往 $displayName", Toast.LENGTH_SHORT).show()
    }

    private fun showWardQuestionDialog(roomKey: String) {
        if (isFinishing || isDestroyed) return

        // 🌟 補上：彈出詢問對話框時，狀態應設為忙碌
        AppStatus.isBusy = true
        AppStatus.currentTaskName = "正在詢問病患需求"

        val dialog = Dialog(this)
        currentDialog = dialog
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_question)
        dialog.setCancelable(false)

        val btnYes = dialog.findViewById<Button>(R.id.btn_yes)
        val btnNo = dialog.findViewById<Button>(R.id.btn_no)

        btnYes.setOnClickListener {
            speechManager.speak("請問有什麼需要幫忙的呢？")
            dialog.dismiss()
            currentDialog = null
            startActivity(
                Intent(this, RoomActionMenuActivity::class.java).apply {
                    putExtra(RoomActionMenuActivity.EXTRA_ROOM, roomKey)
                }
            )
            finish()
        }

        btnNo.setOnClickListener {
            speechManager.speak("好的，謝謝您，我現在回充電座。")
            dialog.dismiss()
            currentDialog = null

            val i = Intent(this, NavigationActivity::class.java).apply {
                putExtra(EXTRA_TARGET_LOCATION, "充電座")
                putExtra(EXTRA_SOURCE_QUERY, "return_charge")
                putExtra(EXTRA_START_VIDEO_ON_ARRIVAL, false)
            }
            startActivity(i)
            finish()
        }

        dialog.show()
        speechManager.speak("請問是否還有其他問題？")
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

        if (startVideoOnArrival && !arrivalConsumed) {
            val target = activeTarget?.trim().orEmpty()
            if (target.isNotEmpty() && normNoSpace(location) == normNoSpace(target)) {
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
            // 🌟 補上：地點介紹時，狀態應設為忙碌
            AppStatus.isBusy = true
            AppStatus.currentTaskName = "正在執行地點介紹"
            speakWithCallback(speakText, onActionComplete)
        } else {
            if (location == "home base" || location == "充電座") {
                speechManager.speak("很高興為您服務，我現在要充電了。")
            } else {
                if (!isTouring && isWardLocation(location)) {
                    hideOverlayUI()
                    showWardQuestionDialog(wardKey(location))
                    return
                }

                speechManager.speak("$location 到了。")
            }
            if (!isTouring) hideOverlayUI()
        }
    }

    private fun speakWithCallback(text: String, onComplete: () -> Unit) {
        // (保留學長的高級語音管理邏輯)
        speechManager.speak(text) {
            runOnUiThread {
                Handler(Looper.getMainLooper()).postDelayed({
                    if (isTouring || layoutOverlay.visibility == View.VISIBLE) {
                        onComplete()
                    }
                }, 500)
            }
        }
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
                    speechManager.speak("現在前往$nextLocation")
                    robot.goTo(nextLocation)
                }, 2000)
            } else {
                isTouring = false
                hideOverlayUI()
            }
        } else {
            hideOverlayUI()
            if (currentLocation == "護理站") {
                runOnUiThread { showCustomDialog() }
            }
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
            // 只要 Overlay 在畫面上，就維持忙碌狀態
            AppStatus.isBusy = true
        }
    }

    private fun hideOverlayUI() {
        runOnUiThread {
            layoutOverlay.visibility = View.GONE
            // 畫面收起，解鎖狀態
            AppStatus.isBusy = false
            AppStatus.currentTaskName = "空閒"
        }
    }

    private fun showCustomDialog() {
        if (isFinishing || isDestroyed) return

        // 🌟 補上：自訂問題對話框，狀態應設為忙碌
        AppStatus.isBusy = true
        AppStatus.currentTaskName = "正在詢問病患需求"

        val dialog = Dialog(this)
        currentDialog = dialog

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_question)
        dialog.setCancelable(false)

        val btnYes = dialog.findViewById<Button>(R.id.btn_yes)
        val btnNo = dialog.findViewById<Button>(R.id.btn_no)

        btnYes.setOnClickListener {
            speechManager.speak("請問有什麼需要幫忙的呢？")
            dialog.dismiss()
            currentDialog = null
            hideOverlayUI() // 呼叫隱藏 UI 會自動解鎖狀態
        }

        btnNo.setOnClickListener {
            speechManager.speak("好的，謝謝您")
            dialog.dismiss()
            currentDialog = null
            finish()
        }

        dialog.show()
        speechManager.speak("請問是否還有其他問題？")
    }
}
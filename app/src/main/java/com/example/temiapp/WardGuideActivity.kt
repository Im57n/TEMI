package com.example.temiapp

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.GridLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.ceil

class WardGuideActivity : AppCompatActivity() {

    private lateinit var prefixGrid: GridLayout
    private lateinit var tvSelectedWard: TextView

    private var selectedPrefixButton: Button? = null

    // 第一層顯示（只顯示 8XX）
    private val prefixList = listOf(
        "801", "802", "803", "805", "806",
        "807", "808", "809", "810", "811", "812", "813",
        "815", "816", "817", "818", "819", "820", "821", "822", "823",
        "825", "826", "827"
    )

    // 第二層對應（點下去跳出 A/B/C）
    private val roomMap: Map<String, List<String>> = mapOf(
        "801" to listOf("801"),
        "802" to listOf("802"),
        "803" to listOf("803"),
        "805" to listOf("805"),
        "806" to listOf("806A"),
        "807" to listOf("807A", "807B"),
        "808" to listOf("808A", "808B"),
        "809" to listOf("809A", "809B"),
        "810" to listOf("810A", "810B"),
        "811" to listOf("811A", "811B"),
        "812" to listOf("812A", "812B"),
        "813" to listOf("813A", "813B"),
        "815" to listOf("815A", "815B"),
        "816" to listOf("816A", "816B"),
        "817" to listOf("817A", "817B"),
        "818" to listOf("818A", "818B"),
        "819" to listOf("819A", "819B", "819C"),
        "820" to listOf("820A", "820B", "820C"),
        "821" to listOf("821A", "821B", "821C"),
        "822" to listOf("822A", "822B", "822C"),
        "823" to listOf("823A", "823B"),
        "825" to listOf("825A", "825B", "825C"),
        "826" to listOf("826A", "826B", "826C"),
        "827" to listOf("827")
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ward_guide)

        prefixGrid = findViewById(R.id.prefix_grid)
        tvSelectedWard = findViewById(R.id.tv_selected_ward)

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }

        // 等版面完成再塞按鈕，避免預覽/初始尺寸問題
        prefixGrid.post { renderPrefixButtons() }
    }

    private fun renderPrefixButtons() {
        prefixGrid.removeAllViews()

        val columns = calculateColumns()
        prefixGrid.columnCount = columns
        prefixGrid.rowCount = ceil(prefixList.size / columns.toDouble()).toInt()

        val isTablet = resources.configuration.smallestScreenWidthDp >= 600
        val btnHeight = if (isTablet) dp(92) else dp(72)      // 原本 64，放大
        val btnTextSize = if (isTablet) 24f else 20f          // 原本 18，放大
        val margin = if (isTablet) dp(6) else dp(4)
        val radius = if (isTablet) 18 else 14

        prefixList.forEachIndexed { index, prefix ->
            val row = index / columns
            val col = index % columns

            val btn = Button(this).apply {
                text = prefix
                isAllCaps = false
                setSingleLine(true)
                textSize = btnTextSize
                setTextColor(Color.WHITE)
                background = roundedBg("#6750A4", radius)
                setPadding(0, 0, 0, 0)
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0

                setOnClickListener { handlePrefixClick(prefix, this) }
            }

            val lp = GridLayout.LayoutParams(
                GridLayout.spec(row),
                GridLayout.spec(col, 1f)
            ).apply {
                width = 0
                height = btnHeight
                setMargins(margin, margin, margin, margin)
            }

            prefixGrid.addView(btn, lp)
        }
    }


    private fun handlePrefixClick(prefix: String, clickedBtn: Button) {
        val rooms = roomMap[prefix].orEmpty()
        if (rooms.isEmpty()) return

        // 只有一個選項就直接選（例如 806 -> 806A）
        if (rooms.size == 1) {
            applySelection(clickedBtn, rooms[0])
            return
        }

        // 多個選項：用自訂排版（請選擇病房 816A / 下一行縮排 816B）
        showIndentedRoomDialog(clickedBtn, rooms)
    }

    private fun showIndentedRoomDialog(clickedBtn: Button, rooms: List<String>) {
        var dialog: AlertDialog? = null

        val root = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }

        // 第一行：請選擇病房 + 第一個房號（同一行）
        val row0 = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }

        val title = TextView(this).apply {
            text = "請選擇病房 "
            textSize = 26f
            setTextColor(Color.parseColor("#212121"))
        }

        val firstOption = makeRoomOptionText(rooms[0]) {
            applySelection(clickedBtn, rooms[0])
            dialog?.dismiss()
        }

        row0.addView(title)
        row0.addView(firstOption)
        root.addView(row0)

        // 其餘行：縮排後顯示（例如 816B）
        for (i in 1 until rooms.size) {
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
            }

            val opt = makeRoomOptionText(rooms[i]) {
                applySelection(clickedBtn, rooms[i])
                dialog?.dismiss()
            }

            row.addView(opt, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                // 這個數字可微調，控制你要的「前面空白縮排」
                marginStart = dp(136)
                topMargin = dp(6)
            })

            root.addView(row)
        }

        dialog = AlertDialog.Builder(this)
            .setView(root)
            .create()

        dialog?.show()
    }

    private fun makeRoomOptionText(room: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = room
            textSize = 26f
            setTextColor(Color.parseColor("#212121"))
            setPadding(dp(2), dp(2), dp(2), dp(2))
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }


    private fun buildSelectTitle(rooms: List<String>): String {
        if (rooms.isEmpty()) return "請選擇病房"

        return buildString {
            append("請選擇 ")
            append(rooms[0]) // 第一行
            rooms.drop(1).forEach { room ->
                append("\n")
                append("\u3000\u3000\u3000\u3000") // 全形空白縮排
                append(room)
            }
        }
    }

    private fun applySelection(prefixBtn: Button, room: String) {
        // 取消前一次高亮
        selectedPrefixButton?.background = roundedBg("#6750A4", 14)

        // 目前前綴高亮
        prefixBtn.background = roundedBg("#2E7D32", 14)
        selectedPrefixButton = prefixBtn

        tvSelectedWard.text = "目前選擇：$room"

        // 之後可接 temi 導航：
        // Robot.getInstance().goTo(room)
    }

    private fun calculateColumns(): Int {
        val widthDp = resources.configuration.screenWidthDp
        return when {
            widthDp >= 1100 -> 6
            widthDp >= 800 -> 6
            else -> 4
        }
    }

    private fun roundedBg(colorHex: String, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(radiusDp).toFloat()
            setColor(Color.parseColor(colorHex))
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}

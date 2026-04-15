package com.example.temiapp

import android.content.Context

/**
 * 錄音檔命名對照：先找自訂錄音，找不到才 fallback 到原本 TTS。
 * 檔案請放在 res/raw，且檔名使用小寫英文、數字、底線。
 */
object RecordedSpeechCatalog {

    private val textToRawName = linkedMapOf(
        "我正在忙碌中，請稍後再叫我喔！" to "v_busy",
        "開始全區導覽，現在前往護理站" to "tour_start_nursing",
        "請問有什麼需要幫忙的呢？" to "ask_need_help",
        "好的，謝謝您，我現在回充電座。" to "return_charge",
        "好的，謝謝您" to "thank_you",
        "請問是否還有其他問題？" to "ask_other_question",
        "很高興為您服務，我現在要充電了。" to "go_charge",
        "這裡是污物室，請依垃圾分類標示丟棄正確物品。" to "intro_dirty_1",
        "但衣服棉被請先腳踩踏板，污衣桶蓋自動打開後，再放入藍色污衣桶。尿布請丟棄至洗手台旁尿布垃圾桶，非醫療廢棄物請至配膳室執行垃圾分類。" to "intro_dirty_2",
        "這裡是配膳室，為了愛護地球，請您依垃圾分類標示完成垃圾分類，廚餘請倒入廚餘桶；這裡也有製冰機，僅供冰敷或冰枕使用，不可以食用；而飲水機半夜會有消毒時間，取用時請注意時間。" to "pantry"
    )

    fun findResId(context: Context, spokenText: String): Int? {
        val exact = textToRawName[spokenText.trim()]
        if (!exact.isNullOrBlank()) {
            val resId = context.resources.getIdentifier(exact, "raw", context.packageName)
            if (resId != 0) return resId
        }

        val dynamicName = dynamicRawNameFor(spokenText)
        if (!dynamicName.isNullOrBlank()) {
            val resId = context.resources.getIdentifier(dynamicName, "raw", context.packageName)
            if (resId != 0) return resId
        }

        return null
    }

    private fun dynamicRawNameFor(text: String): String? {
        val trimmed = text.trim()

        if (trimmed.startsWith("現在前往")) {
            val target = normalizeLocation(trimmed.removePrefix("現在前往"))
            return "nav_go_${target}"
        }

        if (trimmed.endsWith(" 到了。")) {
            val target = normalizeLocation(trimmed.removeSuffix(" 到了。"))
            return "nav_arrive_${target}"
        }

        return null
    }

    private fun normalizeLocation(raw: String): String {
        return raw.trim()
            .replace("充電座", "home_base")
            .replace("護理站", "nursing")
            .replace("配膳室", "pantry")
            .replace("污物室", "dirty")
            .replace("汙物室", "dirty")
            .replace("被服車", "linen")
            .replace("輪椅區", "wheelchair")
            .replace("體重計", "scale")
            .replace(" ", "")
            .lowercase()
    }
}

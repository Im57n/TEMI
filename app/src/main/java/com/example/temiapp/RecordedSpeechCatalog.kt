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

        "這裡是護理站和諮詢站，若您有任何醫療需求，請諮詢護理站人員；若您需要辦理出院或查詢住院費用請至諮詢站諮詢書記。" to "intro_nursing",
        "這裡是污物室，請依垃圾分類標示丟棄正確物品。" to "intro_dirty_1",
        "但衣服棉被請先腳踩踏板汙衣桶蓋自動打開後，再放入藍色污衣桶、尿布請丟棄至洗手台旁尿布垃圾桶，非醫療廢棄物請至配膳室執行垃圾分類。" to "intro_dirty_2",
        "這裡是配膳室，為了愛護地球，請您依垃圾分類標示完成垃圾分類，廚餘請倒入廚餘桶；這裡也有製冰機，僅供冰敷或冰枕使用，不可以食用；而飲水機半夜會有消毒時間，取用時請注意時間。" to "intro_pantry",
        "這裡是被服用品車，請依標示自行拿取所需衣物、被套、枕頭套，再次提醒請不要囤積被服，若您需要更換棉被，請找護理師，若您需要吹風機請自行取用，使用完畢請歸位。" to "intro_linen",
        "這裡是輪椅放置處，若您需要使用輪椅時，請自行取用，使用完畢請主動歸位。" to "intro_wheelchair",
        "這裡是身高體重計、坐磅放置處，病房每週五需測量體重，若您需使用身高體重計，請護理師協助掃描手圈條碼，若使用坐磅或站式體重計，請使用後歸位，使用坐磅時請注意輪子需固定。" to "intro_scale",
        
        "大多數病人及家屬在得知將進行手術治療時，都非常緊張。" to "surgery_1_1",
        "這是一種正常的反應。" to "surgery_1_2",
        "為了讓您減少緊張的情緒，且了解一般手術的準備過程。" to "surgery_1_3",
        "請您詳閱以下有關手術前及手術後注意事項。" to "surgery_1_4",

        "手術前。" to "surgery_2_1",
        "1. 提醒您若有慢性病，應事先告知醫師服用的藥物，評估是否繼續服用。" to "surgery_2_2",
        "2. 清潔：手術前一晚請先洗淨身體，包括洗頭、刮鬍子、剪指甲。" to "surgery_2_3",
        "腹部手術者需特別注意肚臍的清潔。" to "surgery_2_4",
        "3. 住院手術前，請去除指甲油，包含光療指甲及水晶指甲。" to "surgery_2_5",
        "4. 手術前各項檢查及準備：您在入院後，會做一些例行性的檢查，例如心電圖、X光、抽血、尿液等。" to "surgery_2_6",

        "住院當日。" to "surgery_3_1",
        "1. 填寫手術及麻醉同意書：請詳讀同意書內容後簽名。" to "surgery_3_2",
        "若未滿十八歲，請由法定代理人填寫。" to "surgery_3_3",
        "2. 醫師會向您確認手術部位並劃上記號，不可移除。" to "surgery_3_4",
        "3. 灌腸：腹部或腸道手術者，手術前兩天就開始清潔腸子，並吃低渣飲食。" to "surgery_3_5",
        "目的是使腸子內清潔乾淨，減少手術後傷口感染。" to "surgery_3_6",
        "4. 練習深呼吸、咳嗽及翻身活動。" to "surgery_3_7",
        "手術前護理師會教您練習深呼吸咳嗽，請您務必勤加練習。" to "surgery_3_8",
        "因為手術後您可能因全身麻醉會有痰，須靠此技巧將痰咳出，以防肺炎發生。" to "surgery_3_9",

        "手術當日。" to "surgery_4_1",
        "1. 手術前一晚，午夜十二點起開始禁食，包括開水都不能喝。" to "surgery_4_2",
        "因為麻醉中可能會嘔吐，導致吸入性肺炎及呼吸道阻塞。" to "surgery_4_3",
        "2. 送至開刀房前會為您接上靜脈點滴，並確定點滴順暢。" to "surgery_4_4",
        "3. 去除身上飾品，如活動假牙、隱形眼鏡、項鍊、手錶、戒指或髮夾等。" to "surgery_4_5",
        "貴重物品請家人保管，若無家人陪同，可請護理站代為保管。" to "surgery_4_6",
        "4. 當護理師通知要送您去手術房時，請先上廁所排空膀胱，並換上手術衣。" to "surgery_4_7",
        "勿穿內衣褲，女性胸罩也不可穿著；手術衣請反穿，以套入方式穿著，帶子綁在背後。" to "surgery_4_8",
        "5. 手術時，工作人員會送您進入三樓手術室，請家屬陪同前往，並在等候室等候。" to "surgery_4_9",
        "手術後恢復室會有專人照顧您直到清醒，再送回病房。" to "surgery_4_10",

        "手術結束後，全身麻醉及腰椎麻醉的病人會先送到恢復室休息，等清醒後再送回病房。" to "surgery_5_1",
        "手術後可能發生之問題有以下幾點。" to "surgery_5_2",
        "1. 喉嚨痛：因手術時喉內插氣管內管之故，約一到兩天會改善。" to "surgery_5_3",
        "2. 傷口痛：傷口疼痛時請告知醫師或護理師，我們會協助您處理。" to "surgery_5_4",
        "3. 嘔吐：若有嘔吐，請告知護理師，並將頭偏向一側，可利用塑膠袋接嘔吐物。" to "surgery_5_5",
        "4. 傷口引流管：手術後若有引流管，請勿移動它。" to "surgery_5_6",
        "5. 更換傷口敷料：醫師會訂定更換傷口敷料的頻率及方式，醫師或護理師會為病人進行換藥。" to "surgery_5_7",
        "6. 翻身、咳嗽及深呼吸：為了術後肺功能正常及早日康復，請多翻身及拍背咳痰。" to "surgery_5_8",
        "最後，醫護人員會依照您的恢復狀況，提供更完善的手術後治療與照顧。" to "surgery_5_9",
        "並會循序漸進安排您的手術後活動。" to "surgery_5_10",
        "最重要的是，需有您的配合，才能使手術後的不適減至最低，並迅速恢復。" to "surgery_5_11",
        "成大醫院關心您。" to "surgery_5_12"
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

package com.example.temiapp

object SpeechTargetParser {

    private val separators = Regex("[\\s\\u3000,，。．·:：;；/_\\-]+")

    // 每個 8XX 允許的尾碼；"" 代表可無尾碼
    private val wardOptions: Map<String, List<String>> = linkedMapOf(
        "801" to listOf(""),
        "802" to listOf(""),
        "803" to listOf(""),
        "805" to listOf(""),
        "806" to listOf("A"),
        "807" to listOf("A", "B"),
        "808" to listOf("A", "B"),
        "809" to listOf("A", "B"),
        "810" to listOf("A", "B"),
        "811" to listOf("A", "B"),
        "812" to listOf("A", "B"),
        "813" to listOf("A", "B"),
        "815" to listOf("A", "B"),
        "816" to listOf("A", "B"),
        "817" to listOf("A", "B"),
        "818" to listOf("A", "B"),
        "819" to listOf("A", "B", "C"),
        "820" to listOf("A", "B", "C"),
        "821" to listOf("A", "B", "C"),
        "822" to listOf("A", "B", "C"),
        "823" to listOf("A", "B"),
        "825" to listOf("A", "B", "C"),
        "826" to listOf("A", "B", "C"),
        "827" to listOf("")
    )

    fun parseTargetLocation(raw: String): String? {
        if (raw.isBlank()) return null

        val normalized = normalize(raw)

        // 公共地點
        detectPublicLocation(normalized)?.let { return it }

        // 病房碼
        return detectWard(normalized)
    }

    private fun detectPublicLocation(t: String): String? {
        return when {
            t.contains("護理站") -> "護理站"
            t.contains("污物室") || t.contains("汙物室") || t.contains("垃圾") || t.contains("髒衣") -> "污物室"
            t.contains("配膳室") || t.contains("裝水") || t.contains("喝水") || t.contains("廚房") -> "配膳室"
            t.contains("被服車") || t.contains("被服") || t.contains("棉被") -> "被服車"
            t.contains("輪椅") -> "輪椅區"
            t.contains("體重") -> "體重計"
            t.contains("充電") || t.contains("回家") || t.contains("休息") -> "home base"
            else -> null
        }
    }

    private fun detectWard(text: String): String? {
        var t = text
            .replace("病房", "")
            .replace("房號", "")
            .replace("房号", "")
            .replace("號", "")
            .replace("号", "")

        // A/B 同音先正規化（C 不全域替換，避免 807A 被「西」干擾）
        t = t
            .replace("比", "B").replace("逼", "B").replace("筆", "B").replace("笔", "B")
            .replace("鼻", "B").replace("畢", "B").replace("毕", "B").replace("必", "B").replace("碧", "B")
            .replace("欸", "A").replace("诶", "A").replace("ㄟ", "A").replace("哎", "A").replace("艾", "A")

        val compact = t.replace(separators, "")

        // 1) 先抓直接型：807B / 807 / 820C
        Regex("8\\d{2}[ABC]?").findAll(compact).forEach { m ->
            val token = m.value
            val prefix = token.take(3)
            val suffix = if (token.length == 4) token.substring(3) else ""
            validateWard(prefix, suffix)?.let { return it }
        }

        // 2) 容錯型：巴黎西A / 八零七比 / 吧零七 b
        val (suffix, body) = extractSuffixAndBody(compact)
        val digitStream = toDigitStream(body)
        val prefix = Regex("8\\d{2}").find(digitStream)?.value ?: return null

        return validateWard(prefix, suffix)
    }

    private fun extractSuffixAndBody(compact: String): Pair<String, String> {
        detectTrailingToken(compact, listOf("A", "欸", "诶", "ㄟ", "哎", "艾"))?.let { tk ->
            return "A" to compact.dropLast(tk.length)
        }
        detectTrailingToken(compact, listOf("B", "比", "逼", "筆", "笔", "鼻", "畢", "毕", "必", "碧"))?.let { tk ->
            return "B" to compact.dropLast(tk.length)
        }
        detectTrailingToken(compact, listOf("C", "西", "希", "溪", "吸", "夕", "嘻"))?.let { tk ->
            return "C" to compact.dropLast(tk.length)
        }
        return "" to compact
    }

    private fun detectTrailingToken(text: String, tokens: List<String>): String? {
        // 長字串先比，避免短 token 先吃掉
        return tokens.sortedByDescending { it.length }.firstOrNull { text.endsWith(it) }
    }

    private fun validateWard(prefix: String, suffix: String): String? {
        val options = wardOptions[prefix] ?: return null

        if (suffix.isNotEmpty()) {
            return if (suffix in options) "$prefix$suffix" else null
        }

        // 無尾碼情況
        return when {
            "" in options -> prefix                      // e.g. 801/802/803/805/827
            options.size == 1 -> prefix + options[0]    // e.g. 806 -> 806A
            else -> null                                 // e.g. 807 沒說 A/B
        }
    }

    private fun toDigitStream(src: String): String {
        val sb = StringBuilder()

        for (c in src) {
            val d = when (c) {
                // 原本數字
                in '0'..'9' -> c

                // 0
                '零', '〇', '洞', '令', '鈴', '铃', '玲', '凌', '林', '灵', '靈', '黎' -> '0'
                // 1
                '一', '壹', '幺', '么', '衣', '醫', '医' -> '1'
                // 2
                '二', '貳', '贰', '兩', '两', '俩' -> '2'
                // 3
                '三', '參', '参' -> '3'
                // 4
                '四', '肆', '寺' -> '4'
                // 5
                '五', '伍', '午' -> '5'
                // 6
                '六', '陸', '陆', '溜', '流' -> '6'
                // 7（包含「西/希」：解決「巴黎西A」的 7）
                '七', '柒', '妻', '其', '琪', '祈', '氣', '气', '西', '希', '夕' -> '7'
                // 8（你提到的「吧」）
                '八', '捌', '吧', '巴', '叭' -> '8'
                // 9
                '九', '玖', '久' -> '9'

                else -> null
            }

            if (d != null) sb.append(d)
        }

        return sb.toString()
    }

    private fun normalize(raw: String): String {
        return raw.uppercase()
            .replace('Ａ', 'A').replace('Ｂ', 'B').replace('Ｃ', 'C')
            .replace('０', '0').replace('１', '1').replace('２', '2').replace('３', '3').replace('４', '4')
            .replace('５', '5').replace('６', '6').replace('７', '7').replace('８', '8').replace('９', '9')
    }
}

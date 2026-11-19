package com.maoneart.ioskeyboard.fonts

object FancyFontManager {

    enum class FancyStyle(val displayName: String, val previewName: String) {
        NORMAL("Normal", "Normal"),
        CURSIVE("Cursive", "𝓒𝓾𝓻𝓼𝓲𝓿𝓮"),
        GOTHIC("Gothic", "𝕲𝖔𝖙𝖍𝖎𝖈"),
        CIRCLED_BLACK("Circled Black", "🅒🅘🅡🅒🅛🅔"),
        CIRCLED_WHITE("Circled White", "Ⓒⓘⓡⓒⓛⓔ"),
        SQUARED("Squared", "🄱🄾🅇"),
        MONOSPACE("Monospace", "𝙼𝚘𝚗𝚘"),
        SMALL_CAPS("Small Caps", "sᴍᴀʟʟ"),
        DOUBLE_STRUCK("Outline Bold", "𝔹𝕠𝕝𝕕"),
        ITALIC("Italic Serif", "𝐼𝑡𝑎𝑙𝑖𝑐"),
        BOLD_SANS("Bold Sans", "𝗕𝗼𝗹𝗱")
    }

    var currentStyle: FancyStyle = FancyStyle.NORMAL

    // Unicode character mappings for instant O(1) transformation
    private val cursiveUpper = "𝓐𝓑𝓒𝓓𝓔𝓕𝓖𝓗𝓘𝓙𝓚𝓛𝓜𝓝𝓞𝓟𝓠𝓡𝓢𝓣𝓤𝓥𝓦𝓧𝓨𝓩"
    private val cursiveLower = "𝓪𝓫𝓬𝓭𝓮𝓯𝓰𝓱𝓲𝓳𝓴𝓵𝓶𝓷𝓸𝓹𝓺𝓻𝓼𝓽𝓾𝓿𝔀𝔁𝔂𝔃"

    private val gothicUpper = "𝕬𝕭𝕮𝕯𝕰𝕱𝕲𝕳𝕴𝕵𝕶𝕷𝕸𝕹𝕺𝕻𝕼𝕽𝕾𝕿𝖀𝖁𝖂𝖃𝖄𝖅"
    private val gothicLower = "𝖆𝖇𝖈𝖉𝖊𝖋𝖌𝖍𝖎𝖏𝖐𝖑𝖒𝖓𝖔𝖕𝖖𝖗𝖘𝖙𝖚𝖛𝖜𝖝𝖞𝖟"

    private val circledBlackUpper = "🅐🅑🅒🅓🅔🅕🅖🅗🅘🅙🅚🅛🅜🅝🅞🅟🅠🅡🅢🅣🅤🅥🅦🅧🅨🅩"
    private val circledBlackDigits = "⓿➊➋➌➍➎➏➐➑➒"

    private val circledWhiteUpper = "ⒶⒷⒸⒹⒺⒻⒼⒽⒾⒿⓀⓁⓂⓃⓄⓅⓆⓇⓈⓉⓊⓋⓌⓍⓎⓏ"
    private val circledWhiteLower = "ⓐⓑⓒⓓⓔⓕⓖⓗⓘⓙⓚⓛⓜⓝⓞⓟⓠⓡⓢⓣⓤⓥⓦⓧⓨⓩ"
    private val circledWhiteDigits = "⓪①②③④⑤⑥⑦⑧⑨"

    private val squaredUpper = "🄰🄱🄲🄳🄴🄵🄶🄷🄸🄹🄺🄻🄼🄽🄾🄿🅀🅁🅂🅃🅄🅅🅆🅇🅈🅉"

    private val monoUpper = "𝙰𝙱𝙲𝙳𝙴𝙵𝙶𝙷𝙸𝙹𝙺𝙻𝙼𝙽𝙾𝙿𝚀𝚁𝚂𝚃𝚄𝚅𝚆𝚇𝚈𝚉"
    private val monoLower = "𝚊𝚋𝚌𝚍𝚎𝚏𝚐𝚑𝚒𝚓𝚔𝚕𝚖𝚗𝚘𝚙𝚚𝚛𝚜𝚝𝚞𝚟𝚠𝚡𝚢𝚣"
    private val monoDigits = "𝟶𝟷𝟸𝟹𝟺𝟻𝟼𝟽𝟾𝟿"

    private val smallCapsMap: Map<Char, String> = mapOf(
        'a' to "ᴀ", 'b' to "ʙ", 'c' to "ᴄ", 'd' to "ᴅ", 'e' to "ᴇ", 'f' to "ғ", 'g' to "ɢ",
        'h' to "ʜ", 'i' to "ɪ", 'j' to "ᴊ", 'k' to "ᴋ", 'l' to "ʟ", 'm' to "ᴍ", 'n' to "ɴ",
        'o' to "ᴏ", 'p' to "ᴘ", 'q' to "ǫ", 'r' to "ʀ", 's' to "s", 't' to "ᴛ", 'u' to "ᴜ",
        'v' to "ᴠ", 'w' to "ᴡ", 'x' to "x", 'y' to "ʏ", 'z' to "ᴢ",
        'A' to "ᴀ", 'B' to "ʙ", 'C' to "ᴄ", 'D' to "ᴅ", 'E' to "ᴇ", 'F' to "ғ", 'G' to "ɢ",
        'H' to "ʜ", 'I' to "ɪ", 'J' to "ᴊ", 'K' to "ᴋ", 'L' to "ʟ", 'M' to "ᴍ", 'N' to "ɴ",
        'O' to "ᴏ", 'P' to "ᴘ", 'Q' to "ǫ", 'R' to "ʀ", 'S' to "s", 'T' to "ᴛ", 'U' to "ᴜ",
        'V' to "ᴠ", 'W' to "ᴡ", 'X' to "x", 'Y' to "ʏ", 'Z' to "ᴢ"
    )

    private val doubleStruckUpper = "𝔸𝔹ℂ𝔻𝔼𝔽𝔾ℍ𝕀𝕁𝕂𝕃𝕄ℕ𝕆ℙℚℝ𝕊𝕋𝕌𝕍𝕎𝕏𝕐ℤ"
    private val doubleStruckLower = "𝕒𝕓𝕔𝕕𝕖𝕗𝕘𝕙𝕚𝕛𝕜𝕝𝕞𝕟𝕠𝕡𝕢𝕣𝕤𝕥𝕦𝕧𝕨𝕩𝕪𝕫"
    private val doubleStruckDigits = "𝟘𝟙𝟚𝟛𝟜𝟝𝟞𝟟𝟠𝟡"

    private val italicUpper = "𝐴𝐵𝐶𝐷𝐸𝐹𝐺𝐻𝐼𝐽𝐾𝐿𝑀𝑁𝑂𝑃𝑄𝑅𝑆𝑇𝑈𝑉𝑊𝑋𝑌𝑍"
    private val italicLower = "𝑎𝑏𝑐𝑑𝑒𝑓𝑔ℎ𝑖𝑗𝑘𝑙𝑚𝑛𝑜𝑝𝑞𝑟𝑠𝑡𝑢𝑣𝑤𝑥𝑦𝑧"

    private val boldSansUpper = "𝗔𝗕𝗖𝗗𝗘𝗙𝗚𝗛𝗜𝗝𝗞𝗟𝗠𝗡𝗢𝗣𝗤𝗥𝗦𝗧𝗨𝗩𝗪𝗫𝗬𝗭"
    private val boldSansLower = "𝗮𝗯𝗰𝗱𝗲𝗳𝗴𝗵𝗶𝗷𝗸𝗹𝗺𝗻𝗼𝗽𝗾𝗿𝘀𝘁𝘂𝘃𝘄𝘅𝘆𝘇"
    private val boldSansDigits = "𝟬𝟭𝟮𝟯𝟰𝟱𝟲𝟳𝟴𝟵"

    private fun getSurrogatePairAt(s: String, index: Int): String {
        var cpIndex = 0
        var i = 0
        while (i < s.length) {
            val cp = s.codePointAt(i)
            if (cpIndex == index) {
                return String(Character.toChars(cp))
            }
            cpIndex++
            i += Character.charCount(cp)
        }
        return ""
    }

    /**
     * Transforms a single character to the current Fancy Font style.
     */
    fun transformChar(c: Char, style: FancyStyle = currentStyle): String {
        if (style == FancyStyle.NORMAL) return c.toString()

        return when (style) {
            FancyStyle.CURSIVE -> {
                when (c) {
                    in 'A'..'Z' -> getSurrogatePairAt(cursiveUpper, c - 'A')
                    in 'a'..'z' -> getSurrogatePairAt(cursiveLower, c - 'a')
                    else -> c.toString()
                }
            }
            FancyStyle.GOTHIC -> {
                when (c) {
                    in 'A'..'Z' -> getSurrogatePairAt(gothicUpper, c - 'A')
                    in 'a'..'z' -> getSurrogatePairAt(gothicLower, c - 'a')
                    else -> c.toString()
                }
            }
            FancyStyle.CIRCLED_BLACK -> {
                when (c) {
                    in 'A'..'Z' -> getSurrogatePairAt(circledBlackUpper, c - 'A')
                    in 'a'..'z' -> getSurrogatePairAt(circledBlackUpper, c - 'a')
                    in '0'..'9' -> getSurrogatePairAt(circledBlackDigits, c - '0')
                    else -> c.toString()
                }
            }
            FancyStyle.CIRCLED_WHITE -> {
                when (c) {
                    in 'A'..'Z' -> getSurrogatePairAt(circledWhiteUpper, c - 'A')
                    in 'a'..'z' -> getSurrogatePairAt(circledWhiteLower, c - 'a')
                    in '0'..'9' -> getSurrogatePairAt(circledWhiteDigits, c - '0')
                    else -> c.toString()
                }
            }
            FancyStyle.SQUARED -> {
                when (c) {
                    in 'A'..'Z' -> getSurrogatePairAt(squaredUpper, c - 'A')
                    in 'a'..'z' -> getSurrogatePairAt(squaredUpper, c - 'a')
                    else -> c.toString()
                }
            }
            FancyStyle.MONOSPACE -> {
                when (c) {
                    in 'A'..'Z' -> getSurrogatePairAt(monoUpper, c - 'A')
                    in 'a'..'z' -> getSurrogatePairAt(monoLower, c - 'a')
                    in '0'..'9' -> getSurrogatePairAt(monoDigits, c - '0')
                    else -> c.toString()
                }
            }
            FancyStyle.SMALL_CAPS -> {
                smallCapsMap[c] ?: c.toString()
            }
            FancyStyle.DOUBLE_STRUCK -> {
                when (c) {
                    in 'A'..'Z' -> getSurrogatePairAt(doubleStruckUpper, c - 'A')
                    in 'a'..'z' -> getSurrogatePairAt(doubleStruckLower, c - 'a')
                    in '0'..'9' -> getSurrogatePairAt(doubleStruckDigits, c - '0')
                    else -> c.toString()
                }
            }
            FancyStyle.ITALIC -> {
                when (c) {
                    in 'A'..'Z' -> getSurrogatePairAt(italicUpper, c - 'A')
                    in 'a'..'z' -> getSurrogatePairAt(italicLower, c - 'a')
                    else -> c.toString()
                }
            }
            FancyStyle.BOLD_SANS -> {
                when (c) {
                    in 'A'..'Z' -> getSurrogatePairAt(boldSansUpper, c - 'A')
                    in 'a'..'z' -> getSurrogatePairAt(boldSansLower, c - 'a')
                    in '0'..'9' -> getSurrogatePairAt(boldSansDigits, c - '0')
                    else -> c.toString()
                }
            }
            FancyStyle.NORMAL -> c.toString()
        }
    }

    /**
     * Transforms an entire text string to the specified Fancy Font style.
     */
    fun transformText(text: String, style: FancyStyle = currentStyle): String {
        if (style == FancyStyle.NORMAL || text.isEmpty()) return text
        val sb = StringBuilder()
        for (ch in text) {
            sb.append(transformChar(ch, style))
        }
        return sb.toString()
    }
}

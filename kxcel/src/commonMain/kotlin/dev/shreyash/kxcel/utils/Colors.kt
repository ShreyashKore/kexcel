package dev.shreyash.kxcel.utils

import kotlin.math.pow

// region --- ColorType ---

enum class ColorType {
    color,
    material,
    materialAccent,
}

// endregion

// region --- Hex utilities ---

private val hexTable = mapOf(10 to 'A', 11 to 'B', 12 to 'C', 13 to 'D', 14 to 'E', 15 to 'F')
private val hexTableReverse = hexTable.entries.associate { (k, v) -> v to k }

fun decimalToHexadecimal(decimalVal: Int): String {
    if (decimalVal == 0) return "0"
    var value = decimalVal
    val negative = value < 0
    if (negative) value = -value
    val sb = StringBuilder()
    while (value > 0) {
        val remainder = value % 16
        value /= 16
        sb.insert(0, hexTable[remainder] ?: remainder.toString())
    }
    return if (negative) "-$sb" else sb.toString()
}

fun assertHexString(hexString: String): Boolean {
    var s = hexString.replace("#", "").trim().uppercase()
    if (s.isEmpty()) return false
    if (s[0] == '-') s = s.substring(1)
    for (c in s) {
        if (c.digitToIntOrNull() == null && !hexTableReverse.containsKey(c)) return false
    }
    return true
}

fun hexadecimalToDecimal(hexString: String): Int {
    var s = hexString.replace("#", "").trim().uppercase()
    val negative = s.isNotEmpty() && s[0] == '-'
    if (negative) s = s.substring(1)
    var decimalVal = 0
    for (i in s.indices) {
        val digitInt = s[i].digitToIntOrNull()
            ?: hexTableReverse[s[i]]
            ?: throw IllegalArgumentException("Non-hex value was passed to the function")
        decimalVal += (16.0.pow( (s.length - i - 1).toDouble()) * digitInt).toInt()
    }
    return if (negative) -decimalVal else decimalVal
}

// endregion

// region --- String extension ---

/**
 * Returns [ExcelColor.black] if this string is not a valid color hex.
 */
fun String.toExcelColor(): ExcelColor = when {
    this == "none"        -> ExcelColor.none
    assertHexString(this) -> ExcelColor.valuesAsMap[this] ?: ExcelColor.fromHexString(this)
    else                  -> ExcelColor.black
}

/**
 * Validates a color hex string and returns it unchanged if valid, or [ExcelColor.black]'s hex if not.
 */
fun _isColorAppropriate(colorHex: String): String =
    if (assertHexString(colorHex) || colorHex == "none") colorHex else ExcelColor.black.colorHex

// endregion

// region --- ExcelColor ---

class ExcelColor private constructor(
    private val color: String,
    val name: String? = null,
    val type: ColorType? = null,
) {
    /** Returns the hex string, or [black]'s hex if invalid. */
    val colorHex: String
        get() = if (assertHexString(color) || color == "none") color else black.colorHex

    /** Returns the integer value, or [black]'s int if invalid. */
    val colorInt: Int
        get() = if (assertHexString(color)) hexadecimalToDecimal(color) else black.colorInt

    companion object {
        fun fromInt(colorIntValue: Int): ExcelColor = ExcelColor(decimalToHexadecimal(colorIntValue))
        fun fromHexString(colorHexValue: String): ExcelColor = ExcelColor(colorHexValue)

        val none            = ExcelColor("none")
        val black           = ExcelColor("FF000000", "black", ColorType.color)
        val black12         = ExcelColor("1F000000", "black12", ColorType.color)
        val black26         = ExcelColor("42000000", "black26", ColorType.color)
        val black38         = ExcelColor("61000000", "black38", ColorType.color)
        val black45         = ExcelColor("73000000", "black45", ColorType.color)
        val black54         = ExcelColor("8A000000", "black54", ColorType.color)
        val black87         = ExcelColor("DD000000", "black87", ColorType.color)
        val white           = ExcelColor("FFFFFFFF", "white", ColorType.color)
        val white10         = ExcelColor("1AFFFFFF", "white10", ColorType.color)
        val white12         = ExcelColor("1FFFFFFF", "white12", ColorType.color)
        val white24         = ExcelColor("3DFFFFFF", "white24", ColorType.color)
        val white30         = ExcelColor("4DFFFFFF", "white30", ColorType.color)
        val white38         = ExcelColor("62FFFFFF", "white38", ColorType.color)
        val white54         = ExcelColor("8AFFFFFF", "white54", ColorType.color)
        val white60         = ExcelColor("99FFFFFF", "white60", ColorType.color)
        val white70         = ExcelColor("B3FFFFFF", "white70", ColorType.color)
        val redAccent             = ExcelColor("FFFF5252", "redAccent", ColorType.materialAccent)
        val pinkAccent            = ExcelColor("FFFF4081", "pinkAccent", ColorType.materialAccent)
        val purpleAccent          = ExcelColor("FFE040FB", "purpleAccent", ColorType.materialAccent)
        val deepPurpleAccent      = ExcelColor("FF7C4DFF", "deepPurpleAccent", ColorType.materialAccent)
        val indigoAccent          = ExcelColor("FF536DFE", "indigoAccent", ColorType.materialAccent)
        val blueAccent            = ExcelColor("FF448AFF", "blueAccent", ColorType.materialAccent)
        val lightBlueAccent       = ExcelColor("FF40C4FF", "lightBlueAccent", ColorType.materialAccent)
        val cyanAccent            = ExcelColor("FF18FFFF", "cyanAccent", ColorType.materialAccent)
        val tealAccent            = ExcelColor("FF64FFDA", "tealAccent", ColorType.materialAccent)
        val greenAccent           = ExcelColor("FF69F0AE", "greenAccent", ColorType.materialAccent)
        val lightGreenAccent      = ExcelColor("FFB2FF59", "lightGreenAccent", ColorType.materialAccent)
        val limeAccent            = ExcelColor("FFEEFF41", "limeAccent", ColorType.materialAccent)
        val yellowAccent          = ExcelColor("FFFFFF00", "yellowAccent", ColorType.materialAccent)
        val amberAccent           = ExcelColor("FFFFD740", "amberAccent", ColorType.materialAccent)
        val orangeAccent          = ExcelColor("FFFFAB40", "orangeAccent", ColorType.materialAccent)
        val deepOrangeAccent      = ExcelColor("FFFF6E40", "deepOrangeAccent", ColorType.materialAccent)
        val red           = ExcelColor("FFF44336", "red", ColorType.material)
        val pink          = ExcelColor("FFE91E63", "pink", ColorType.material)
        val purple        = ExcelColor("FF9C27B0", "purple", ColorType.material)
        val deepPurple    = ExcelColor("FF673AB7", "deepPurple", ColorType.material)
        val indigo        = ExcelColor("FF3F51B5", "indigo", ColorType.material)
        val blue          = ExcelColor("FF2196F3", "blue", ColorType.material)
        val lightBlue     = ExcelColor("FF03A9F4", "lightBlue", ColorType.material)
        val cyan          = ExcelColor("FF00BCD4", "cyan", ColorType.material)
        val teal          = ExcelColor("FF009688", "teal", ColorType.material)
        val green         = ExcelColor("FF4CAF50", "green", ColorType.material)
        val lightGreen    = ExcelColor("FF8BC34A", "lightGreen", ColorType.material)
        val lime          = ExcelColor("FFCDDC39", "lime", ColorType.material)
        val yellow        = ExcelColor("FFFFEB3B", "yellow", ColorType.material)
        val amber         = ExcelColor("FFFFC107", "amber", ColorType.material)
        val orange        = ExcelColor("FFFF9800", "orange", ColorType.material)
        val deepOrange    = ExcelColor("FFFF5722", "deepOrange", ColorType.material)
        val brown         = ExcelColor("FF795548", "brown", ColorType.material)
        val grey          = ExcelColor("FF9E9E9E", "grey", ColorType.material)
        val blueGrey      = ExcelColor("FF607D8B", "blueGrey", ColorType.material)
        val redAccent100          = ExcelColor("FFFF8A80", "redAccent100", ColorType.materialAccent)
        val redAccent400          = ExcelColor("FFFF1744", "redAccent400", ColorType.materialAccent)
        val redAccent700          = ExcelColor("FFD50000", "redAccent700", ColorType.materialAccent)
        val pinkAccent100         = ExcelColor("FFFF80AB", "pinkAccent100", ColorType.materialAccent)
        val pinkAccent400         = ExcelColor("FFF50057", "pinkAccent400", ColorType.materialAccent)
        val pinkAccent700         = ExcelColor("FFC51162", "pinkAccent700", ColorType.materialAccent)
        val purpleAccent100       = ExcelColor("FFEA80FC", "purpleAccent100", ColorType.materialAccent)
        val purpleAccent400       = ExcelColor("FFD500F9", "purpleAccent400", ColorType.materialAccent)
        val purpleAccent700       = ExcelColor("FFAA00FF", "purpleAccent700", ColorType.materialAccent)
        val deepPurpleAccent100   = ExcelColor("FFB388FF", "deepPurpleAccent100", ColorType.materialAccent)
        val deepPurpleAccent400   = ExcelColor("FF651FFF", "deepPurpleAccent400", ColorType.materialAccent)
        val deepPurpleAccent700   = ExcelColor("FF6200EA", "deepPurpleAccent700", ColorType.materialAccent)
        val indigoAccent100       = ExcelColor("FF8C9EFF", "indigoAccent100", ColorType.materialAccent)
        val indigoAccent400       = ExcelColor("FF3D5AFE", "indigoAccent400", ColorType.materialAccent)
        val indigoAccent700       = ExcelColor("FF304FFE", "indigoAccent700", ColorType.materialAccent)
        val blueAccent100         = ExcelColor("FF82B1FF", "blueAccent100", ColorType.materialAccent)
        val blueAccent400         = ExcelColor("FF2979FF", "blueAccent400", ColorType.materialAccent)
        val blueAccent700         = ExcelColor("FF2962FF", "blueAccent700", ColorType.materialAccent)
        val lightBlueAccent100    = ExcelColor("FF80D8FF", "lightBlueAccent100", ColorType.materialAccent)
        val lightBlueAccent400    = ExcelColor("FF00B0FF", "lightBlueAccent400", ColorType.materialAccent)
        val lightBlueAccent700    = ExcelColor("FF0091EA", "lightBlueAccent700", ColorType.materialAccent)
        val cyanAccent100         = ExcelColor("FF84FFFF", "cyanAccent100", ColorType.materialAccent)
        val cyanAccent400         = ExcelColor("FF00E5FF", "cyanAccent400", ColorType.materialAccent)
        val cyanAccent700         = ExcelColor("FF00B8D4", "cyanAccent700", ColorType.materialAccent)
        val tealAccent100         = ExcelColor("FFA7FFEB", "tealAccent100", ColorType.materialAccent)
        val tealAccent400         = ExcelColor("FF1DE9B6", "tealAccent400", ColorType.materialAccent)
        val tealAccent700         = ExcelColor("FF00BFA5", "tealAccent700", ColorType.materialAccent)
        val greenAccent100        = ExcelColor("FFB9F6CA", "greenAccent100", ColorType.materialAccent)
        val greenAccent400        = ExcelColor("FF00E676", "greenAccent400", ColorType.materialAccent)
        val greenAccent700        = ExcelColor("FF00C853", "greenAccent700", ColorType.materialAccent)
        val lightGreenAccent100   = ExcelColor("FFCCFF90", "lightGreenAccent100", ColorType.materialAccent)
        val lightGreenAccent400   = ExcelColor("FF76FF03", "lightGreenAccent400", ColorType.materialAccent)
        val lightGreenAccent700   = ExcelColor("FF64DD17", "lightGreenAccent700", ColorType.materialAccent)
        val limeAccent100         = ExcelColor("FFF4FF81", "limeAccent100", ColorType.materialAccent)
        val limeAccent400         = ExcelColor("FFC6FF00", "limeAccent400", ColorType.materialAccent)
        val limeAccent700         = ExcelColor("FFAEEA00", "limeAccent700", ColorType.materialAccent)
        val yellowAccent100       = ExcelColor("FFFFFF8D", "yellowAccent100", ColorType.materialAccent)
        val yellowAccent400       = ExcelColor("FFFFEA00", "yellowAccent400", ColorType.materialAccent)
        val yellowAccent700       = ExcelColor("FFFFD600", "yellowAccent700", ColorType.materialAccent)
        val amberAccent100        = ExcelColor("FFFFE57F", "amberAccent100", ColorType.materialAccent)
        val amberAccent400        = ExcelColor("FFFFC400", "amberAccent400", ColorType.materialAccent)
        val amberAccent700        = ExcelColor("FFFFAB00", "amberAccent700", ColorType.materialAccent)
        val orangeAccent100       = ExcelColor("FFFFD180", "orangeAccent100", ColorType.materialAccent)
        val orangeAccent400       = ExcelColor("FFFF9100", "orangeAccent400", ColorType.materialAccent)
        val orangeAccent700       = ExcelColor("FFFF6D00", "orangeAccent700", ColorType.materialAccent)
        val deepOrangeAccent100   = ExcelColor("FFFF9E80", "deepOrangeAccent100", ColorType.materialAccent)
        val deepOrangeAccent400   = ExcelColor("FFFF3D00", "deepOrangeAccent400", ColorType.materialAccent)
        val deepOrangeAccent700   = ExcelColor("FFDD2C00", "deepOrangeAccent700", ColorType.materialAccent)
        val red50     = ExcelColor("FFFFEBEE", "red50", ColorType.material)
        val red100    = ExcelColor("FFFFCDD2", "red100", ColorType.material)
        val red200    = ExcelColor("FFEF9A9A", "red200", ColorType.material)
        val red300    = ExcelColor("FFE57373", "red300", ColorType.material)
        val red400    = ExcelColor("FFEF5350", "red400", ColorType.material)
        val red600    = ExcelColor("FFE53935", "red600", ColorType.material)
        val red700    = ExcelColor("FFD32F2F", "red700", ColorType.material)
        val red800    = ExcelColor("FFC62828", "red800", ColorType.material)
        val red900    = ExcelColor("FFB71C1C", "red900", ColorType.material)
        val pink50    = ExcelColor("FFFCE4EC", "pink50", ColorType.material)
        val pink100   = ExcelColor("FFF8BBD0", "pink100", ColorType.material)
        val pink200   = ExcelColor("FFF48FB1", "pink200", ColorType.material)
        val pink300   = ExcelColor("FFF06292", "pink300", ColorType.material)
        val pink400   = ExcelColor("FFEC407A", "pink400", ColorType.material)
        val pink600   = ExcelColor("FFD81B60", "pink600", ColorType.material)
        val pink700   = ExcelColor("FFC2185B", "pink700", ColorType.material)
        val pink800   = ExcelColor("FFAD1457", "pink800", ColorType.material)
        val pink900   = ExcelColor("FF880E4F", "pink900", ColorType.material)
        val purple50  = ExcelColor("FFF3E5F5", "purple50", ColorType.material)
        val purple100 = ExcelColor("FFE1BEE7", "purple100", ColorType.material)
        val purple200 = ExcelColor("FFCE93D8", "purple200", ColorType.material)
        val purple300 = ExcelColor("FFBA68C8", "purple300", ColorType.material)
        val purple400 = ExcelColor("FFAB47BC", "purple400", ColorType.material)
        val purple600 = ExcelColor("FF8E24AA", "purple600", ColorType.material)
        val purple700 = ExcelColor("FF7B1FA2", "purple700", ColorType.material)
        val purple800 = ExcelColor("FF6A1B9A", "purple800", ColorType.material)
        val purple900 = ExcelColor("FF4A148C", "purple900", ColorType.material)
        val deepPurple50  = ExcelColor("FFEDE7F6", "deepPurple50", ColorType.material)
        val deepPurple100 = ExcelColor("FFD1C4E9", "deepPurple100", ColorType.material)
        val deepPurple200 = ExcelColor("FFB39DDB", "deepPurple200", ColorType.material)
        val deepPurple300 = ExcelColor("FF9575CD", "deepPurple300", ColorType.material)
        val deepPurple400 = ExcelColor("FF7E57C2", "deepPurple400", ColorType.material)
        val deepPurple600 = ExcelColor("FF5E35B1", "deepPurple600", ColorType.material)
        val deepPurple700 = ExcelColor("FF512DA8", "deepPurple700", ColorType.material)
        val deepPurple800 = ExcelColor("FF4527A0", "deepPurple800", ColorType.material)
        val deepPurple900 = ExcelColor("FF311B92", "deepPurple900", ColorType.material)
        val indigo50  = ExcelColor("FFE8EAF6", "indigo50", ColorType.material)
        val indigo100 = ExcelColor("FFC5CAE9", "indigo100", ColorType.material)
        val indigo200 = ExcelColor("FF9FA8DA", "indigo200", ColorType.material)
        val indigo300 = ExcelColor("FF7986CB", "indigo300", ColorType.material)
        val indigo400 = ExcelColor("FF5C6BC0", "indigo400", ColorType.material)
        val indigo600 = ExcelColor("FF3949AB", "indigo600", ColorType.material)
        val indigo700 = ExcelColor("FF303F9F", "indigo700", ColorType.material)
        val indigo800 = ExcelColor("FF283593", "indigo800", ColorType.material)
        val indigo900 = ExcelColor("FF1A237E", "indigo900", ColorType.material)
        val blue50    = ExcelColor("FFE3F2FD", "blue50", ColorType.material)
        val blue100   = ExcelColor("FFBBDEFB", "blue100", ColorType.material)
        val blue200   = ExcelColor("FF90CAF9", "blue200", ColorType.material)
        val blue300   = ExcelColor("FF64B5F6", "blue300", ColorType.material)
        val blue400   = ExcelColor("FF42A5F5", "blue400", ColorType.material)
        val blue600   = ExcelColor("FF1E88E5", "blue600", ColorType.material)
        val blue700   = ExcelColor("FF1976D2", "blue700", ColorType.material)
        val blue800   = ExcelColor("FF1565C0", "blue800", ColorType.material)
        val blue900   = ExcelColor("FF0D47A1", "blue900", ColorType.material)
        val lightBlue50   = ExcelColor("FFE1F5FE", "lightBlue50", ColorType.material)
        val lightBlue100  = ExcelColor("FFB3E5FC", "lightBlue100", ColorType.material)
        val lightBlue200  = ExcelColor("FF81D4FA", "lightBlue200", ColorType.material)
        val lightBlue300  = ExcelColor("FF4FC3F7", "lightBlue300", ColorType.material)
        val lightBlue400  = ExcelColor("FF29B6F6", "lightBlue400", ColorType.material)
        val lightBlue600  = ExcelColor("FF039BE5", "lightBlue600", ColorType.material)
        val lightBlue700  = ExcelColor("FF0288D1", "lightBlue700", ColorType.material)
        val lightBlue800  = ExcelColor("FF0277BD", "lightBlue800", ColorType.material)
        val lightBlue900  = ExcelColor("FF01579B", "lightBlue900", ColorType.material)
        val cyan50    = ExcelColor("FFE0F7FA", "cyan50", ColorType.material)
        val cyan100   = ExcelColor("FFB2EBF2", "cyan100", ColorType.material)
        val cyan200   = ExcelColor("FF80DEEA", "cyan200", ColorType.material)
        val cyan300   = ExcelColor("FF4DD0E1", "cyan300", ColorType.material)
        val cyan400   = ExcelColor("FF26C6DA", "cyan400", ColorType.material)
        val cyan600   = ExcelColor("FF00ACC1", "cyan600", ColorType.material)
        val cyan700   = ExcelColor("FF0097A7", "cyan700", ColorType.material)
        val cyan800   = ExcelColor("FF00838F", "cyan800", ColorType.material)
        val cyan900   = ExcelColor("FF006064", "cyan900", ColorType.material)
        val teal50    = ExcelColor("FFE0F2F1", "teal50", ColorType.material)
        val teal100   = ExcelColor("FFB2DFDB", "teal100", ColorType.material)
        val teal200   = ExcelColor("FF80CBC4", "teal200", ColorType.material)
        val teal300   = ExcelColor("FF4DB6AC", "teal300", ColorType.material)
        val teal400   = ExcelColor("FF26A69A", "teal400", ColorType.material)
        val teal600   = ExcelColor("FF00897B", "teal600", ColorType.material)
        val teal700   = ExcelColor("FF00796B", "teal700", ColorType.material)
        val teal800   = ExcelColor("FF00695C", "teal800", ColorType.material)
        val teal900   = ExcelColor("FF004D40", "teal900", ColorType.material)
        val green50   = ExcelColor("FFE8F5E9", "green50", ColorType.material)
        val green100  = ExcelColor("FFC8E6C9", "green100", ColorType.material)
        val green200  = ExcelColor("FFA5D6A7", "green200", ColorType.material)
        val green300  = ExcelColor("FF81C784", "green300", ColorType.material)
        val green400  = ExcelColor("FF66BB6A", "green400", ColorType.material)
        val green600  = ExcelColor("FF43A047", "green600", ColorType.material)
        val green700  = ExcelColor("FF388E3C", "green700", ColorType.material)
        val green800  = ExcelColor("FF2E7D32", "green800", ColorType.material)
        val green900  = ExcelColor("FF1B5E20", "green900", ColorType.material)
        val lightGreen50  = ExcelColor("FFF1F8E9", "lightGreen50", ColorType.material)
        val lightGreen100 = ExcelColor("FFDCEDC8", "lightGreen100", ColorType.material)
        val lightGreen200 = ExcelColor("FFC5E1A5", "lightGreen200", ColorType.material)
        val lightGreen300 = ExcelColor("FFAED581", "lightGreen300", ColorType.material)
        val lightGreen400 = ExcelColor("FF9CCC65", "lightGreen400", ColorType.material)
        val lightGreen600 = ExcelColor("FF7CB342", "lightGreen600", ColorType.material)
        val lightGreen700 = ExcelColor("FF689F38", "lightGreen700", ColorType.material)
        val lightGreen800 = ExcelColor("FF558B2F", "lightGreen800", ColorType.material)
        val lightGreen900 = ExcelColor("FF33691E", "lightGreen900", ColorType.material)
        val lime50    = ExcelColor("FFF9FBE7", "lime50", ColorType.material)
        val lime100   = ExcelColor("FFF0F4C3", "lime100", ColorType.material)
        val lime200   = ExcelColor("FFE6EE9C", "lime200", ColorType.material)
        val lime300   = ExcelColor("FFDCE775", "lime300", ColorType.material)
        val lime400   = ExcelColor("FFD4E157", "lime400", ColorType.material)
        val lime600   = ExcelColor("FFC0CA33", "lime600", ColorType.material)
        val lime700   = ExcelColor("FFAFB42B", "lime700", ColorType.material)
        val lime800   = ExcelColor("FF9E9D24", "lime800", ColorType.material)
        val lime900   = ExcelColor("FF827717", "lime900", ColorType.material)
        val yellow50  = ExcelColor("FFFFFDE7", "yellow50", ColorType.material)
        val yellow100 = ExcelColor("FFFFF9C4", "yellow100", ColorType.material)
        val yellow200 = ExcelColor("FFFFF59D", "yellow200", ColorType.material)
        val yellow300 = ExcelColor("FFFFF176", "yellow300", ColorType.material)
        val yellow400 = ExcelColor("FFFFEE58", "yellow400", ColorType.material)
        val yellow600 = ExcelColor("FFFDD835", "yellow600", ColorType.material)
        val yellow700 = ExcelColor("FFFBC02D", "yellow700", ColorType.material)
        val yellow800 = ExcelColor("FFF9A825", "yellow800", ColorType.material)
        val yellow900 = ExcelColor("FFF57F17", "yellow900", ColorType.material)
        val amber50   = ExcelColor("FFFFF8E1", "amber50", ColorType.material)
        val amber100  = ExcelColor("FFFFECB3", "amber100", ColorType.material)
        val amber200  = ExcelColor("FFFFE082", "amber200", ColorType.material)
        val amber300  = ExcelColor("FFFFD54F", "amber300", ColorType.material)
        val amber400  = ExcelColor("FFFFCA28", "amber400", ColorType.material)
        val amber600  = ExcelColor("FFFFB300", "amber600", ColorType.material)
        val amber700  = ExcelColor("FFFFA000", "amber700", ColorType.material)
        val amber800  = ExcelColor("FFFF8F00", "amber800", ColorType.material)
        val amber900  = ExcelColor("FFFF6F00", "amber900", ColorType.material)
        val orange50  = ExcelColor("FFFFF3E0", "orange50", ColorType.material)
        val orange100 = ExcelColor("FFFFE0B2", "orange100", ColorType.material)
        val orange200 = ExcelColor("FFFFCC80", "orange200", ColorType.material)
        val orange300 = ExcelColor("FFFFB74D", "orange300", ColorType.material)
        val orange400 = ExcelColor("FFFFA726", "orange400", ColorType.material)
        val orange600 = ExcelColor("FFFB8C00", "orange600", ColorType.material)
        val orange700 = ExcelColor("FFF57C00", "orange700", ColorType.material)
        val orange800 = ExcelColor("FFEF6C00", "orange800", ColorType.material)
        val orange900 = ExcelColor("FFE65100", "orange900", ColorType.material)
        val deepOrange50  = ExcelColor("FFFBE9E7", "deepOrange50", ColorType.material)
        val deepOrange100 = ExcelColor("FFFFCCBC", "deepOrange100", ColorType.material)
        val deepOrange200 = ExcelColor("FFFFAB91", "deepOrange200", ColorType.material)
        val deepOrange300 = ExcelColor("FFFF8A65", "deepOrange300", ColorType.material)
        val deepOrange400 = ExcelColor("FFFF7043", "deepOrange400", ColorType.material)
        val deepOrange600 = ExcelColor("FFF4511E", "deepOrange600", ColorType.material)
        val deepOrange700 = ExcelColor("FFE64A19", "deepOrange700", ColorType.material)
        val deepOrange800 = ExcelColor("FFD84315", "deepOrange800", ColorType.material)
        val deepOrange900 = ExcelColor("FFBF360C", "deepOrange900", ColorType.material)
        val brown50   = ExcelColor("FFEFEBE9", "brown50", ColorType.material)
        val brown100  = ExcelColor("FFD7CCC8", "brown100", ColorType.material)
        val brown200  = ExcelColor("FFBCAAA4", "brown200", ColorType.material)
        val brown300  = ExcelColor("FFA1887F", "brown300", ColorType.material)
        val brown400  = ExcelColor("FF8D6E63", "brown400", ColorType.material)
        val brown600  = ExcelColor("FF6D4C41", "brown600", ColorType.material)
        val brown700  = ExcelColor("FF5D4037", "brown700", ColorType.material)
        val brown800  = ExcelColor("FF4E342E", "brown800", ColorType.material)
        val brown900  = ExcelColor("FF3E2723", "brown900", ColorType.material)
        val grey50    = ExcelColor("FFFAFAFA", "grey50", ColorType.material)
        val grey100   = ExcelColor("FFF5F5F5", "grey100", ColorType.material)
        val grey200   = ExcelColor("FFEEEEEE", "grey200", ColorType.material)
        val grey300   = ExcelColor("FFE0E0E0", "grey300", ColorType.material)
        val grey350   = ExcelColor("FFD6D6D6", "grey350", ColorType.material)
        val grey400   = ExcelColor("FFBDBDBD", "grey400", ColorType.material)
        val grey600   = ExcelColor("FF757575", "grey600", ColorType.material)
        val grey700   = ExcelColor("FF616161", "grey700", ColorType.material)
        val grey800   = ExcelColor("FF424242", "grey800", ColorType.material)
        val grey850   = ExcelColor("FF303030", "grey850", ColorType.material)
        val grey900   = ExcelColor("FF212121", "grey900", ColorType.material)
        val blueGrey50  = ExcelColor("FFECEFF1", "blueGrey50", ColorType.material)
        val blueGrey100 = ExcelColor("FFCFD8DC", "blueGrey100", ColorType.material)
        val blueGrey200 = ExcelColor("FFB0BEC5", "blueGrey200", ColorType.material)
        val blueGrey300 = ExcelColor("FF90A4AE", "blueGrey300", ColorType.material)
        val blueGrey400 = ExcelColor("FF78909C", "blueGrey400", ColorType.material)
        val blueGrey600 = ExcelColor("FF546E7A", "blueGrey600", ColorType.material)
        val blueGrey700 = ExcelColor("FF455A64", "blueGrey700", ColorType.material)
        val blueGrey800 = ExcelColor("FF37474F", "blueGrey800", ColorType.material)
        val blueGrey900 = ExcelColor("FF263238", "blueGrey900", ColorType.material)

        val values: List<ExcelColor> by lazy {
            listOf(
                black, black12, black26, black38, black45, black54, black87,
                white, white10, white12, white24, white30, white38, white54, white60, white70,
                redAccent, pinkAccent, purpleAccent, deepPurpleAccent, indigoAccent,
                blueAccent, lightBlueAccent, cyanAccent, tealAccent, greenAccent,
                lightGreenAccent, limeAccent, yellowAccent, amberAccent, orangeAccent, deepOrangeAccent,
                red, pink, purple, deepPurple, indigo, blue, lightBlue, cyan, teal,
                green, lightGreen, lime, yellow, amber, orange, deepOrange, brown, grey, blueGrey,
                redAccent100, redAccent400, redAccent700,
                pinkAccent100, pinkAccent400, pinkAccent700,
                purpleAccent100, purpleAccent400, purpleAccent700,
                deepPurpleAccent100, deepPurpleAccent400, deepPurpleAccent700,
                indigoAccent100, indigoAccent400, indigoAccent700,
                blueAccent100, blueAccent400, blueAccent700,
                lightBlueAccent100, lightBlueAccent400, lightBlueAccent700,
                cyanAccent100, cyanAccent400, cyanAccent700,
                tealAccent100, tealAccent400, tealAccent700,
                greenAccent100, greenAccent400, greenAccent700,
                lightGreenAccent100, lightGreenAccent400, lightGreenAccent700,
                limeAccent100, limeAccent400, limeAccent700,
                yellowAccent100, yellowAccent400, yellowAccent700,
                amberAccent100, amberAccent400, amberAccent700,
                orangeAccent100, orangeAccent400, orangeAccent700,
                deepOrangeAccent100, deepOrangeAccent400, deepOrangeAccent700,
                red50, red100, red200, red300, red400, red600, red700, red800, red900,
                pink50, pink100, pink200, pink300, pink400, pink600, pink700, pink800, pink900,
                purple50, purple100, purple200, purple300, purple400, purple600, purple700, purple800, purple900,
                deepPurple50, deepPurple100, deepPurple200, deepPurple300, deepPurple400,
                deepPurple600, deepPurple700, deepPurple800, deepPurple900,
                indigo50, indigo100, indigo200, indigo300, indigo400,
                indigo600, indigo700, indigo800, indigo900,
                blue50, blue100, blue200, blue300, blue400, blue600, blue700, blue800, blue900,
                lightBlue50, lightBlue100, lightBlue200, lightBlue300, lightBlue400,
                lightBlue600, lightBlue700, lightBlue800, lightBlue900,
                cyan50, cyan100, cyan200, cyan300, cyan400, cyan600, cyan700, cyan800, cyan900,
                teal50, teal100, teal200, teal300, teal400, teal600, teal700, teal800, teal900,
                green50, green100, green200, green300, green400, green600, green700, green800, green900,
                lightGreen50, lightGreen100, lightGreen200, lightGreen300, lightGreen400,
                lightGreen600, lightGreen700, lightGreen800, lightGreen900,
                lime50, lime100, lime200, lime300, lime400, lime600, lime700, lime800, lime900,
                yellow50, yellow100, yellow200, yellow300, yellow400,
                yellow600, yellow700, yellow800, yellow900,
                amber50, amber100, amber200, amber300, amber400, amber600, amber700, amber800, amber900,
                orange50, orange100, orange200, orange300, orange400,
                orange600, orange700, orange800, orange900,
                deepOrange50, deepOrange100, deepOrange200, deepOrange300, deepOrange400,
                deepOrange600, deepOrange700, deepOrange800, deepOrange900,
                brown50, brown100, brown200, brown300, brown400, brown600, brown700, brown800, brown900,
                grey50, grey100, grey200, grey300, grey350, grey400,
                grey600, grey700, grey800, grey850, grey900,
                blueGrey50, blueGrey100, blueGrey200, blueGrey300, blueGrey400,
                blueGrey600, blueGrey700, blueGrey800, blueGrey900,
            )
        }

        val valuesAsMap: Map<String, ExcelColor> by lazy {
            values.associate { it.colorHex to it }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExcelColor) return false
        return name == other.name && color == other.color && type == other.type &&
                colorHex == other.colorHex && colorInt == other.colorInt
    }

    override fun hashCode(): Int {
        var h = name?.hashCode() ?: 0
        h = 31 * h + color.hashCode()
        h = 31 * h + (type?.hashCode() ?: 0)
        return h
    }

    override fun toString(): String = colorHex
}

// endregion
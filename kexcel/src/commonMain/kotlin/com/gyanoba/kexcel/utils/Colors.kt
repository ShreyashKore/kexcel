package com.gyanoba.kexcel.utils

import kotlin.math.pow

// region --- ColorType ---

public enum class ColorType {
    Color,
    Material,
    MaterialAccent,
}

// endregion

// region --- Hex utilities ---

private val hexTable = mapOf(10 to 'A', 11 to 'B', 12 to 'C', 13 to 'D', 14 to 'E', 15 to 'F')
private val hexTableReverse = hexTable.entries.associate { (k, v) -> v to k }

internal fun decimalToHexadecimal(decimalVal: Int): String {
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

internal fun assertHexString(hexString: String): Boolean {
    var s = hexString.replace("#", "").trim().uppercase()
    if (s.isEmpty()) return false
    if (s[0] == '-') s = s.substring(1)
    for (c in s) {
        if (c.digitToIntOrNull() == null && !hexTableReverse.containsKey(c)) return false
    }
    return true
}

internal fun hexadecimalToDecimal(hexString: String): Int {
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
internal fun String.toExcelColor(): ExcelColor = when {
    this == "none"        -> ExcelColor.none
    assertHexString(this) -> ExcelColor.valuesAsMap[this] ?: ExcelColor.fromHexString(this)
    else                  -> ExcelColor.black
}

/**
 * Validates a color hex string and returns it unchanged if valid, or [ExcelColor.black]'s hex if not.
 */
internal fun _isColorAppropriate(colorHex: String): String =
    if (assertHexString(colorHex) || colorHex == "none") colorHex else ExcelColor.black.colorHex

// endregion

// region --- ExcelColor ---

public class ExcelColor private constructor(
    private val color: String,
    public val name: String? = null,
    public val type: ColorType? = null,
) {
    /** Returns the hex string, or [black]'s hex if invalid. */
    public val colorHex: String
        get() = if (assertHexString(color) || color == "none") color else black.colorHex

    /** Returns the integer value, or [black]'s int if invalid. */
    public val colorInt: Int
        get() = if (assertHexString(color)) hexadecimalToDecimal(color) else black.colorInt

    public companion object {
        public fun fromInt(colorIntValue: Int): ExcelColor = ExcelColor(decimalToHexadecimal(colorIntValue))
        public fun fromHexString(colorHexValue: String): ExcelColor = ExcelColor(colorHexValue)

        public val none: ExcelColor = ExcelColor("none")
        public val black: ExcelColor = ExcelColor("FF000000", "black", ColorType.Color)
        public val black12: ExcelColor = ExcelColor("1F000000", "black12", ColorType.Color)
        public val black26: ExcelColor = ExcelColor("42000000", "black26", ColorType.Color)
        public val black38: ExcelColor = ExcelColor("61000000", "black38", ColorType.Color)
        public val black45: ExcelColor = ExcelColor("73000000", "black45", ColorType.Color)
        public val black54: ExcelColor = ExcelColor("8A000000", "black54", ColorType.Color)
        public val black87: ExcelColor = ExcelColor("DD000000", "black87", ColorType.Color)
        public val white: ExcelColor = ExcelColor("FFFFFFFF", "white", ColorType.Color)
        public val white10: ExcelColor = ExcelColor("1AFFFFFF", "white10", ColorType.Color)
        public val white12: ExcelColor = ExcelColor("1FFFFFFF", "white12", ColorType.Color)
        public val white24: ExcelColor = ExcelColor("3DFFFFFF", "white24", ColorType.Color)
        public val white30: ExcelColor = ExcelColor("4DFFFFFF", "white30", ColorType.Color)
        public val white38: ExcelColor = ExcelColor("62FFFFFF", "white38", ColorType.Color)
        public val white54: ExcelColor = ExcelColor("8AFFFFFF", "white54", ColorType.Color)
        public val white60: ExcelColor = ExcelColor("99FFFFFF", "white60", ColorType.Color)
        public val white70: ExcelColor = ExcelColor("B3FFFFFF", "white70", ColorType.Color)
        public val redAccent: ExcelColor = ExcelColor("FFFF5252", "redAccent", ColorType.MaterialAccent)
        public val pinkAccent: ExcelColor = ExcelColor("FFFF4081", "pinkAccent", ColorType.MaterialAccent)
        public val purpleAccent: ExcelColor = ExcelColor("FFE040FB", "purpleAccent", ColorType.MaterialAccent)
        public val deepPurpleAccent: ExcelColor = ExcelColor("FF7C4DFF", "deepPurpleAccent", ColorType.MaterialAccent)
        public val indigoAccent: ExcelColor = ExcelColor("FF536DFE", "indigoAccent", ColorType.MaterialAccent)
        public val blueAccent: ExcelColor = ExcelColor("FF448AFF", "blueAccent", ColorType.MaterialAccent)
        public val lightBlueAccent: ExcelColor = ExcelColor("FF40C4FF", "lightBlueAccent", ColorType.MaterialAccent)
        public val cyanAccent: ExcelColor = ExcelColor("FF18FFFF", "cyanAccent", ColorType.MaterialAccent)
        public val tealAccent: ExcelColor = ExcelColor("FF64FFDA", "tealAccent", ColorType.MaterialAccent)
        public val greenAccent: ExcelColor = ExcelColor("FF69F0AE", "greenAccent", ColorType.MaterialAccent)
        public val lightGreenAccent: ExcelColor = ExcelColor("FFB2FF59", "lightGreenAccent", ColorType.MaterialAccent)
        public val limeAccent: ExcelColor = ExcelColor("FFEEFF41", "limeAccent", ColorType.MaterialAccent)
        public val yellowAccent: ExcelColor = ExcelColor("FFFFFF00", "yellowAccent", ColorType.MaterialAccent)
        public val amberAccent: ExcelColor = ExcelColor("FFFFD740", "amberAccent", ColorType.MaterialAccent)
        public val orangeAccent: ExcelColor = ExcelColor("FFFFAB40", "orangeAccent", ColorType.MaterialAccent)
        public val deepOrangeAccent: ExcelColor = ExcelColor("FFFF6E40", "deepOrangeAccent", ColorType.MaterialAccent)
        public val red: ExcelColor = ExcelColor("FFF44336", "red", ColorType.Material)
        public val pink: ExcelColor = ExcelColor("FFE91E63", "pink", ColorType.Material)
        public val purple: ExcelColor = ExcelColor("FF9C27B0", "purple", ColorType.Material)
        public val deepPurple: ExcelColor = ExcelColor("FF673AB7", "deepPurple", ColorType.Material)
        public val indigo: ExcelColor = ExcelColor("FF3F51B5", "indigo", ColorType.Material)
        public val blue: ExcelColor = ExcelColor("FF2196F3", "blue", ColorType.Material)
        public val lightBlue: ExcelColor = ExcelColor("FF03A9F4", "lightBlue", ColorType.Material)
        public val cyan: ExcelColor = ExcelColor("FF00BCD4", "cyan", ColorType.Material)
        public val teal: ExcelColor = ExcelColor("FF009688", "teal", ColorType.Material)
        public val green: ExcelColor = ExcelColor("FF4CAF50", "green", ColorType.Material)
        public val lightGreen: ExcelColor = ExcelColor("FF8BC34A", "lightGreen", ColorType.Material)
        public val lime: ExcelColor = ExcelColor("FFCDDC39", "lime", ColorType.Material)
        public val yellow: ExcelColor = ExcelColor("FFFFEB3B", "yellow", ColorType.Material)
        public val amber: ExcelColor = ExcelColor("FFFFC107", "amber", ColorType.Material)
        public val orange: ExcelColor = ExcelColor("FFFF9800", "orange", ColorType.Material)
        public val deepOrange: ExcelColor = ExcelColor("FFFF5722", "deepOrange", ColorType.Material)
        public val brown: ExcelColor = ExcelColor("FF795548", "brown", ColorType.Material)
        public val grey: ExcelColor = ExcelColor("FF9E9E9E", "grey", ColorType.Material)
        public val blueGrey: ExcelColor = ExcelColor("FF607D8B", "blueGrey", ColorType.Material)
        public val redAccent100: ExcelColor = ExcelColor("FFFF8A80", "redAccent100", ColorType.MaterialAccent)
        public val redAccent400: ExcelColor = ExcelColor("FFFF1744", "redAccent400", ColorType.MaterialAccent)
        public val redAccent700: ExcelColor = ExcelColor("FFD50000", "redAccent700", ColorType.MaterialAccent)
        public val pinkAccent100: ExcelColor = ExcelColor("FFFF80AB", "pinkAccent100", ColorType.MaterialAccent)
        public val pinkAccent400: ExcelColor = ExcelColor("FFF50057", "pinkAccent400", ColorType.MaterialAccent)
        public val pinkAccent700: ExcelColor = ExcelColor("FFC51162", "pinkAccent700", ColorType.MaterialAccent)
        public val purpleAccent100: ExcelColor = ExcelColor("FFEA80FC", "purpleAccent100", ColorType.MaterialAccent)
        public val purpleAccent400: ExcelColor = ExcelColor("FFD500F9", "purpleAccent400", ColorType.MaterialAccent)
        public val purpleAccent700: ExcelColor = ExcelColor("FFAA00FF", "purpleAccent700", ColorType.MaterialAccent)
        public val deepPurpleAccent100: ExcelColor = ExcelColor("FFB388FF", "deepPurpleAccent100", ColorType.MaterialAccent)
        public val deepPurpleAccent400: ExcelColor = ExcelColor("FF651FFF", "deepPurpleAccent400", ColorType.MaterialAccent)
        public val deepPurpleAccent700: ExcelColor = ExcelColor("FF6200EA", "deepPurpleAccent700", ColorType.MaterialAccent)
        public val indigoAccent100: ExcelColor = ExcelColor("FF8C9EFF", "indigoAccent100", ColorType.MaterialAccent)
        public val indigoAccent400: ExcelColor = ExcelColor("FF3D5AFE", "indigoAccent400", ColorType.MaterialAccent)
        public val indigoAccent700: ExcelColor = ExcelColor("FF304FFE", "indigoAccent700", ColorType.MaterialAccent)
        public val blueAccent100: ExcelColor = ExcelColor("FF82B1FF", "blueAccent100", ColorType.MaterialAccent)
        public val blueAccent400: ExcelColor = ExcelColor("FF2979FF", "blueAccent400", ColorType.MaterialAccent)
        public val blueAccent700: ExcelColor = ExcelColor("FF2962FF", "blueAccent700", ColorType.MaterialAccent)
        public val lightBlueAccent100: ExcelColor = ExcelColor("FF80D8FF", "lightBlueAccent100", ColorType.MaterialAccent)
        public val lightBlueAccent400: ExcelColor = ExcelColor("FF00B0FF", "lightBlueAccent400", ColorType.MaterialAccent)
        public val lightBlueAccent700: ExcelColor = ExcelColor("FF0091EA", "lightBlueAccent700", ColorType.MaterialAccent)
        public val cyanAccent100: ExcelColor = ExcelColor("FF84FFFF", "cyanAccent100", ColorType.MaterialAccent)
        public val cyanAccent400: ExcelColor = ExcelColor("FF00E5FF", "cyanAccent400", ColorType.MaterialAccent)
        public val cyanAccent700: ExcelColor = ExcelColor("FF00B8D4", "cyanAccent700", ColorType.MaterialAccent)
        public val tealAccent100: ExcelColor = ExcelColor("FFA7FFEB", "tealAccent100", ColorType.MaterialAccent)
        public val tealAccent400: ExcelColor = ExcelColor("FF1DE9B6", "tealAccent400", ColorType.MaterialAccent)
        public val tealAccent700: ExcelColor = ExcelColor("FF00BFA5", "tealAccent700", ColorType.MaterialAccent)
        public val greenAccent100: ExcelColor = ExcelColor("FFB9F6CA", "greenAccent100", ColorType.MaterialAccent)
        public val greenAccent400: ExcelColor = ExcelColor("FF00E676", "greenAccent400", ColorType.MaterialAccent)
        public val greenAccent700: ExcelColor = ExcelColor("FF00C853", "greenAccent700", ColorType.MaterialAccent)
        public val lightGreenAccent100: ExcelColor = ExcelColor("FFCCFF90", "lightGreenAccent100", ColorType.MaterialAccent)
        public val lightGreenAccent400: ExcelColor = ExcelColor("FF76FF03", "lightGreenAccent400", ColorType.MaterialAccent)
        public val lightGreenAccent700: ExcelColor = ExcelColor("FF64DD17", "lightGreenAccent700", ColorType.MaterialAccent)
        public val limeAccent100: ExcelColor = ExcelColor("FFF4FF81", "limeAccent100", ColorType.MaterialAccent)
        public val limeAccent400: ExcelColor = ExcelColor("FFC6FF00", "limeAccent400", ColorType.MaterialAccent)
        public val limeAccent700: ExcelColor = ExcelColor("FFAEEA00", "limeAccent700", ColorType.MaterialAccent)
        public val yellowAccent100: ExcelColor = ExcelColor("FFFFFF8D", "yellowAccent100", ColorType.MaterialAccent)
        public val yellowAccent400: ExcelColor = ExcelColor("FFFFEA00", "yellowAccent400", ColorType.MaterialAccent)
        public val yellowAccent700: ExcelColor = ExcelColor("FFFFD600", "yellowAccent700", ColorType.MaterialAccent)
        public val amberAccent100: ExcelColor = ExcelColor("FFFFE57F", "amberAccent100", ColorType.MaterialAccent)
        public val amberAccent400: ExcelColor = ExcelColor("FFFFC400", "amberAccent400", ColorType.MaterialAccent)
        public val amberAccent700: ExcelColor = ExcelColor("FFFFAB00", "amberAccent700", ColorType.MaterialAccent)
        public val orangeAccent100: ExcelColor = ExcelColor("FFFFD180", "orangeAccent100", ColorType.MaterialAccent)
        public val orangeAccent400: ExcelColor = ExcelColor("FFFF9100", "orangeAccent400", ColorType.MaterialAccent)
        public val orangeAccent700: ExcelColor = ExcelColor("FFFF6D00", "orangeAccent700", ColorType.MaterialAccent)
        public val deepOrangeAccent100: ExcelColor = ExcelColor("FFFF9E80", "deepOrangeAccent100", ColorType.MaterialAccent)
        public val deepOrangeAccent400: ExcelColor = ExcelColor("FFFF3D00", "deepOrangeAccent400", ColorType.MaterialAccent)
        public val deepOrangeAccent700: ExcelColor = ExcelColor("FFDD2C00", "deepOrangeAccent700", ColorType.MaterialAccent)
        public val red50: ExcelColor = ExcelColor("FFFFEBEE", "red50", ColorType.Material)
        public val red100: ExcelColor = ExcelColor("FFFFCDD2", "red100", ColorType.Material)
        public val red200: ExcelColor = ExcelColor("FFEF9A9A", "red200", ColorType.Material)
        public val red300: ExcelColor = ExcelColor("FFE57373", "red300", ColorType.Material)
        public val red400: ExcelColor = ExcelColor("FFEF5350", "red400", ColorType.Material)
        public val red600: ExcelColor = ExcelColor("FFE53935", "red600", ColorType.Material)
        public val red700: ExcelColor = ExcelColor("FFD32F2F", "red700", ColorType.Material)
        public val red800: ExcelColor = ExcelColor("FFC62828", "red800", ColorType.Material)
        public val red900: ExcelColor = ExcelColor("FFB71C1C", "red900", ColorType.Material)
        public val pink50: ExcelColor = ExcelColor("FFFCE4EC", "pink50", ColorType.Material)
        public val pink100: ExcelColor = ExcelColor("FFF8BBD0", "pink100", ColorType.Material)
        public val pink200: ExcelColor = ExcelColor("FFF48FB1", "pink200", ColorType.Material)
        public val pink300: ExcelColor = ExcelColor("FFF06292", "pink300", ColorType.Material)
        public val pink400: ExcelColor = ExcelColor("FFEC407A", "pink400", ColorType.Material)
        public val pink600: ExcelColor = ExcelColor("FFD81B60", "pink600", ColorType.Material)
        public val pink700: ExcelColor = ExcelColor("FFC2185B", "pink700", ColorType.Material)
        public val pink800: ExcelColor = ExcelColor("FFAD1457", "pink800", ColorType.Material)
        public val pink900: ExcelColor = ExcelColor("FF880E4F", "pink900", ColorType.Material)
        public val purple50: ExcelColor = ExcelColor("FFF3E5F5", "purple50", ColorType.Material)
        public val purple100: ExcelColor = ExcelColor("FFE1BEE7", "purple100", ColorType.Material)
        public val purple200: ExcelColor = ExcelColor("FFCE93D8", "purple200", ColorType.Material)
        public val purple300: ExcelColor = ExcelColor("FFBA68C8", "purple300", ColorType.Material)
        public val purple400: ExcelColor = ExcelColor("FFAB47BC", "purple400", ColorType.Material)
        public val purple600: ExcelColor = ExcelColor("FF8E24AA", "purple600", ColorType.Material)
        public val purple700: ExcelColor = ExcelColor("FF7B1FA2", "purple700", ColorType.Material)
        public val purple800: ExcelColor = ExcelColor("FF6A1B9A", "purple800", ColorType.Material)
        public val purple900: ExcelColor = ExcelColor("FF4A148C", "purple900", ColorType.Material)
        public val deepPurple50: ExcelColor = ExcelColor("FFEDE7F6", "deepPurple50", ColorType.Material)
        public val deepPurple100: ExcelColor = ExcelColor("FFD1C4E9", "deepPurple100", ColorType.Material)
        public val deepPurple200: ExcelColor = ExcelColor("FFB39DDB", "deepPurple200", ColorType.Material)
        public val deepPurple300: ExcelColor = ExcelColor("FF9575CD", "deepPurple300", ColorType.Material)
        public val deepPurple400: ExcelColor = ExcelColor("FF7E57C2", "deepPurple400", ColorType.Material)
        public val deepPurple600: ExcelColor = ExcelColor("FF5E35B1", "deepPurple600", ColorType.Material)
        public val deepPurple700: ExcelColor = ExcelColor("FF512DA8", "deepPurple700", ColorType.Material)
        public val deepPurple800: ExcelColor = ExcelColor("FF4527A0", "deepPurple800", ColorType.Material)
        public val deepPurple900: ExcelColor = ExcelColor("FF311B92", "deepPurple900", ColorType.Material)
        public val indigo50: ExcelColor = ExcelColor("FFE8EAF6", "indigo50", ColorType.Material)
        public val indigo100: ExcelColor = ExcelColor("FFC5CAE9", "indigo100", ColorType.Material)
        public val indigo200: ExcelColor = ExcelColor("FF9FA8DA", "indigo200", ColorType.Material)
        public val indigo300: ExcelColor = ExcelColor("FF7986CB", "indigo300", ColorType.Material)
        public val indigo400: ExcelColor = ExcelColor("FF5C6BC0", "indigo400", ColorType.Material)
        public val indigo600: ExcelColor = ExcelColor("FF3949AB", "indigo600", ColorType.Material)
        public val indigo700: ExcelColor = ExcelColor("FF303F9F", "indigo700", ColorType.Material)
        public val indigo800: ExcelColor = ExcelColor("FF283593", "indigo800", ColorType.Material)
        public val indigo900: ExcelColor = ExcelColor("FF1A237E", "indigo900", ColorType.Material)
        public val blue50: ExcelColor = ExcelColor("FFE3F2FD", "blue50", ColorType.Material)
        public val blue100: ExcelColor = ExcelColor("FFBBDEFB", "blue100", ColorType.Material)
        public val blue200: ExcelColor = ExcelColor("FF90CAF9", "blue200", ColorType.Material)
        public val blue300: ExcelColor = ExcelColor("FF64B5F6", "blue300", ColorType.Material)
        public val blue400: ExcelColor = ExcelColor("FF42A5F5", "blue400", ColorType.Material)
        public val blue600: ExcelColor = ExcelColor("FF1E88E5", "blue600", ColorType.Material)
        public val blue700: ExcelColor = ExcelColor("FF1976D2", "blue700", ColorType.Material)
        public val blue800: ExcelColor = ExcelColor("FF1565C0", "blue800", ColorType.Material)
        public val blue900: ExcelColor = ExcelColor("FF0D47A1", "blue900", ColorType.Material)
        public val lightBlue50: ExcelColor = ExcelColor("FFE1F5FE", "lightBlue50", ColorType.Material)
        public val lightBlue100: ExcelColor = ExcelColor("FFB3E5FC", "lightBlue100", ColorType.Material)
        public val lightBlue200: ExcelColor = ExcelColor("FF81D4FA", "lightBlue200", ColorType.Material)
        public val lightBlue300: ExcelColor = ExcelColor("FF4FC3F7", "lightBlue300", ColorType.Material)
        public val lightBlue400: ExcelColor = ExcelColor("FF29B6F6", "lightBlue400", ColorType.Material)
        public val lightBlue600: ExcelColor = ExcelColor("FF039BE5", "lightBlue600", ColorType.Material)
        public val lightBlue700: ExcelColor = ExcelColor("FF0288D1", "lightBlue700", ColorType.Material)
        public val lightBlue800: ExcelColor = ExcelColor("FF0277BD", "lightBlue800", ColorType.Material)
        public val lightBlue900: ExcelColor = ExcelColor("FF01579B", "lightBlue900", ColorType.Material)
        public val cyan50: ExcelColor = ExcelColor("FFE0F7FA", "cyan50", ColorType.Material)
        public val cyan100: ExcelColor = ExcelColor("FFB2EBF2", "cyan100", ColorType.Material)
        public val cyan200: ExcelColor = ExcelColor("FF80DEEA", "cyan200", ColorType.Material)
        public val cyan300: ExcelColor = ExcelColor("FF4DD0E1", "cyan300", ColorType.Material)
        public val cyan400: ExcelColor = ExcelColor("FF26C6DA", "cyan400", ColorType.Material)
        public val cyan600: ExcelColor = ExcelColor("FF00ACC1", "cyan600", ColorType.Material)
        public val cyan700: ExcelColor = ExcelColor("FF0097A7", "cyan700", ColorType.Material)
        public val cyan800: ExcelColor = ExcelColor("FF00838F", "cyan800", ColorType.Material)
        public val cyan900: ExcelColor = ExcelColor("FF006064", "cyan900", ColorType.Material)
        public val teal50: ExcelColor = ExcelColor("FFE0F2F1", "teal50", ColorType.Material)
        public val teal100: ExcelColor = ExcelColor("FFB2DFDB", "teal100", ColorType.Material)
        public val teal200: ExcelColor = ExcelColor("FF80CBC4", "teal200", ColorType.Material)
        public val teal300: ExcelColor = ExcelColor("FF4DB6AC", "teal300", ColorType.Material)
        public val teal400: ExcelColor = ExcelColor("FF26A69A", "teal400", ColorType.Material)
        public val teal600: ExcelColor = ExcelColor("FF00897B", "teal600", ColorType.Material)
        public val teal700: ExcelColor = ExcelColor("FF00796B", "teal700", ColorType.Material)
        public val teal800: ExcelColor = ExcelColor("FF00695C", "teal800", ColorType.Material)
        public val teal900: ExcelColor = ExcelColor("FF004D40", "teal900", ColorType.Material)
        public val green50: ExcelColor = ExcelColor("FFE8F5E9", "green50", ColorType.Material)
        public val green100: ExcelColor = ExcelColor("FFC8E6C9", "green100", ColorType.Material)
        public val green200: ExcelColor = ExcelColor("FFA5D6A7", "green200", ColorType.Material)
        public val green300: ExcelColor = ExcelColor("FF81C784", "green300", ColorType.Material)
        public val green400: ExcelColor = ExcelColor("FF66BB6A", "green400", ColorType.Material)
        public val green600: ExcelColor = ExcelColor("FF43A047", "green600", ColorType.Material)
        public val green700: ExcelColor = ExcelColor("FF388E3C", "green700", ColorType.Material)
        public val green800: ExcelColor = ExcelColor("FF2E7D32", "green800", ColorType.Material)
        public val green900: ExcelColor = ExcelColor("FF1B5E20", "green900", ColorType.Material)
        public val lightGreen50: ExcelColor = ExcelColor("FFF1F8E9", "lightGreen50", ColorType.Material)
        public val lightGreen100: ExcelColor = ExcelColor("FFDCEDC8", "lightGreen100", ColorType.Material)
        public val lightGreen200: ExcelColor = ExcelColor("FFC5E1A5", "lightGreen200", ColorType.Material)
        public val lightGreen300: ExcelColor = ExcelColor("FFAED581", "lightGreen300", ColorType.Material)
        public val lightGreen400: ExcelColor = ExcelColor("FF9CCC65", "lightGreen400", ColorType.Material)
        public val lightGreen600: ExcelColor = ExcelColor("FF7CB342", "lightGreen600", ColorType.Material)
        public val lightGreen700: ExcelColor = ExcelColor("FF689F38", "lightGreen700", ColorType.Material)
        public val lightGreen800: ExcelColor = ExcelColor("FF558B2F", "lightGreen800", ColorType.Material)
        public val lightGreen900: ExcelColor = ExcelColor("FF33691E", "lightGreen900", ColorType.Material)
        public val lime50: ExcelColor = ExcelColor("FFF9FBE7", "lime50", ColorType.Material)
        public val lime100: ExcelColor = ExcelColor("FFF0F4C3", "lime100", ColorType.Material)
        public val lime200: ExcelColor = ExcelColor("FFE6EE9C", "lime200", ColorType.Material)
        public val lime300: ExcelColor = ExcelColor("FFDCE775", "lime300", ColorType.Material)
        public val lime400: ExcelColor = ExcelColor("FFD4E157", "lime400", ColorType.Material)
        public val lime600: ExcelColor = ExcelColor("FFC0CA33", "lime600", ColorType.Material)
        public val lime700: ExcelColor = ExcelColor("FFAFB42B", "lime700", ColorType.Material)
        public val lime800: ExcelColor = ExcelColor("FF9E9D24", "lime800", ColorType.Material)
        public val lime900: ExcelColor = ExcelColor("FF827717", "lime900", ColorType.Material)
        public val yellow50: ExcelColor = ExcelColor("FFFFFDE7", "yellow50", ColorType.Material)
        public val yellow100: ExcelColor = ExcelColor("FFFFF9C4", "yellow100", ColorType.Material)
        public val yellow200: ExcelColor = ExcelColor("FFFFF59D", "yellow200", ColorType.Material)
        public val yellow300: ExcelColor = ExcelColor("FFFFF176", "yellow300", ColorType.Material)
        public val yellow400: ExcelColor = ExcelColor("FFFFEE58", "yellow400", ColorType.Material)
        public val yellow600: ExcelColor = ExcelColor("FFFDD835", "yellow600", ColorType.Material)
        public val yellow700: ExcelColor = ExcelColor("FFFBC02D", "yellow700", ColorType.Material)
        public val yellow800: ExcelColor = ExcelColor("FFF9A825", "yellow800", ColorType.Material)
        public val yellow900: ExcelColor = ExcelColor("FFF57F17", "yellow900", ColorType.Material)
        public val amber50: ExcelColor = ExcelColor("FFFFF8E1", "amber50", ColorType.Material)
        public val amber100: ExcelColor = ExcelColor("FFFFECB3", "amber100", ColorType.Material)
        public val amber200: ExcelColor = ExcelColor("FFFFE082", "amber200", ColorType.Material)
        public val amber300: ExcelColor = ExcelColor("FFFFD54F", "amber300", ColorType.Material)
        public val amber400: ExcelColor = ExcelColor("FFFFCA28", "amber400", ColorType.Material)
        public val amber600: ExcelColor = ExcelColor("FFFFB300", "amber600", ColorType.Material)
        public val amber700: ExcelColor = ExcelColor("FFFFA000", "amber700", ColorType.Material)
        public val amber800: ExcelColor = ExcelColor("FFFF8F00", "amber800", ColorType.Material)
        public val amber900: ExcelColor = ExcelColor("FFFF6F00", "amber900", ColorType.Material)
        public val orange50: ExcelColor = ExcelColor("FFFFF3E0", "orange50", ColorType.Material)
        public val orange100: ExcelColor = ExcelColor("FFFFE0B2", "orange100", ColorType.Material)
        public val orange200: ExcelColor = ExcelColor("FFFFCC80", "orange200", ColorType.Material)
        public val orange300: ExcelColor = ExcelColor("FFFFB74D", "orange300", ColorType.Material)
        public val orange400: ExcelColor = ExcelColor("FFFFA726", "orange400", ColorType.Material)
        public val orange600: ExcelColor = ExcelColor("FFFB8C00", "orange600", ColorType.Material)
        public val orange700: ExcelColor = ExcelColor("FFF57C00", "orange700", ColorType.Material)
        public val orange800: ExcelColor = ExcelColor("FFEF6C00", "orange800", ColorType.Material)
        public val orange900: ExcelColor = ExcelColor("FFE65100", "orange900", ColorType.Material)
        public val deepOrange50: ExcelColor = ExcelColor("FFFBE9E7", "deepOrange50", ColorType.Material)
        public val deepOrange100: ExcelColor = ExcelColor("FFFFCCBC", "deepOrange100", ColorType.Material)
        public val deepOrange200: ExcelColor = ExcelColor("FFFFAB91", "deepOrange200", ColorType.Material)
        public val deepOrange300: ExcelColor = ExcelColor("FFFF8A65", "deepOrange300", ColorType.Material)
        public val deepOrange400: ExcelColor = ExcelColor("FFFF7043", "deepOrange400", ColorType.Material)
        public val deepOrange600: ExcelColor = ExcelColor("FFF4511E", "deepOrange600", ColorType.Material)
        public val deepOrange700: ExcelColor = ExcelColor("FFE64A19", "deepOrange700", ColorType.Material)
        public val deepOrange800: ExcelColor = ExcelColor("FFD84315", "deepOrange800", ColorType.Material)
        public val deepOrange900: ExcelColor = ExcelColor("FFBF360C", "deepOrange900", ColorType.Material)
        public val brown50: ExcelColor = ExcelColor("FFEFEBE9", "brown50", ColorType.Material)
        public val brown100: ExcelColor = ExcelColor("FFD7CCC8", "brown100", ColorType.Material)
        public val brown200: ExcelColor = ExcelColor("FFBCAAA4", "brown200", ColorType.Material)
        public val brown300: ExcelColor = ExcelColor("FFA1887F", "brown300", ColorType.Material)
        public val brown400: ExcelColor = ExcelColor("FF8D6E63", "brown400", ColorType.Material)
        public val brown600: ExcelColor = ExcelColor("FF6D4C41", "brown600", ColorType.Material)
        public val brown700: ExcelColor = ExcelColor("FF5D4037", "brown700", ColorType.Material)
        public val brown800: ExcelColor = ExcelColor("FF4E342E", "brown800", ColorType.Material)
        public val brown900: ExcelColor = ExcelColor("FF3E2723", "brown900", ColorType.Material)
        public val grey50: ExcelColor = ExcelColor("FFFAFAFA", "grey50", ColorType.Material)
        public val grey100: ExcelColor = ExcelColor("FFF5F5F5", "grey100", ColorType.Material)
        public val grey200: ExcelColor = ExcelColor("FFEEEEEE", "grey200", ColorType.Material)
        public val grey300: ExcelColor = ExcelColor("FFE0E0E0", "grey300", ColorType.Material)
        public val grey350: ExcelColor = ExcelColor("FFD6D6D6", "grey350", ColorType.Material)
        public val grey400: ExcelColor = ExcelColor("FFBDBDBD", "grey400", ColorType.Material)
        public val grey600: ExcelColor = ExcelColor("FF757575", "grey600", ColorType.Material)
        public val grey700: ExcelColor = ExcelColor("FF616161", "grey700", ColorType.Material)
        public val grey800: ExcelColor = ExcelColor("FF424242", "grey800", ColorType.Material)
        public val grey850: ExcelColor = ExcelColor("FF303030", "grey850", ColorType.Material)
        public val grey900: ExcelColor = ExcelColor("FF212121", "grey900", ColorType.Material)
        public val blueGrey50: ExcelColor = ExcelColor("FFECEFF1", "blueGrey50", ColorType.Material)
        public val blueGrey100: ExcelColor = ExcelColor("FFCFD8DC", "blueGrey100", ColorType.Material)
        public val blueGrey200: ExcelColor = ExcelColor("FFB0BEC5", "blueGrey200", ColorType.Material)
        public val blueGrey300: ExcelColor = ExcelColor("FF90A4AE", "blueGrey300", ColorType.Material)
        public val blueGrey400: ExcelColor = ExcelColor("FF78909C", "blueGrey400", ColorType.Material)
        public val blueGrey600: ExcelColor = ExcelColor("FF546E7A", "blueGrey600", ColorType.Material)
        public val blueGrey700: ExcelColor = ExcelColor("FF455A64", "blueGrey700", ColorType.Material)
        public val blueGrey800: ExcelColor = ExcelColor("FF37474F", "blueGrey800", ColorType.Material)
        public val blueGrey900: ExcelColor = ExcelColor("FF263238", "blueGrey900", ColorType.Material)

        public val values: List<ExcelColor> by lazy {
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

        internal val valuesAsMap: Map<String, ExcelColor> by lazy {
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
package com.iptv.firetv.core.player

import android.graphics.Color
import android.graphics.Typeface
import androidx.media3.ui.CaptionStyleCompat

/** Colour of the subtitle text. [argb] is opaque. */
enum class SubColor(val label: String, val argb: Int) {
    WHITE("White", 0xFFFFFFFF.toInt()),
    YELLOW("Yellow", 0xFFFFE14D.toInt()),
    CYAN("Cyan", 0xFF5EE6FF.toInt()),
    GREEN("Green", 0xFF7CFF8E.toInt()),
    ORANGE("Orange", 0xFFFFA64D.toInt()),
    PINK("Pink", 0xFFFF8AD0.toInt()),
    BLACK("Black", 0xFF000000.toInt())
}

/** What is drawn behind the text. Transparent relies on an outline or shadow for legibility. */
enum class SubBackground(val label: String) {
    BLACK("Black box"),
    SEMI("Semi-transparent"),
    TRANSPARENT("Transparent")
}

enum class SubSize(val label: String, val fraction: Float) {
    SMALL("Small", 0.040f),
    MEDIUM("Medium", 0.0533f),
    LARGE("Large", 0.070f),
    XLARGE("Extra large", 0.090f)
}

enum class SubFont(val label: String) {
    DEFAULT("Default"),
    SANS("Sans-serif"),
    CONDENSED("Condensed"),
    SERIF("Serif"),
    MONO("Monospace"),
    CASUAL("Casual")
}

enum class SubEdge(val label: String) {
    AUTO("Automatic"),
    NONE("None"),
    OUTLINE("Outline"),
    SHADOW("Shadow")
}

/** Where on the picture subtitles sit; implemented as the bottom padding of the subtitle view. */
enum class SubPosition(val label: String, val bottomPadding: Float) {
    BOTTOM("Bottom", 0.08f),
    RAISED("Raised", 0.20f),
    MIDDLE("Middle", 0.45f),
    TOP("Top", 0.80f)
}

/** All user choices for subtitle appearance (Settings > Subtitles, and the style sheet in the player). */
data class SubtitleStyle(
    val color: SubColor = SubColor.WHITE,
    val background: SubBackground = SubBackground.TRANSPARENT,
    val size: SubSize = SubSize.MEDIUM,
    val font: SubFont = SubFont.SANS,
    val edge: SubEdge = SubEdge.OUTLINE,
    val position: SubPosition = SubPosition.BOTTOM
) {
    /** The edge actually drawn: automatic means "outline when there is no solid box behind the text". */
    val effectiveEdge: SubEdge
        get() = if (edge != SubEdge.AUTO) edge else if (background == SubBackground.BLACK) SubEdge.NONE else SubEdge.OUTLINE

    /** Background colour (ARGB) behind each cue line. */
    val backgroundArgb: Int
        get() = when (background) {
            SubBackground.BLACK -> 0xFF000000.toInt()
            SubBackground.SEMI -> 0x99000000.toInt()
            SubBackground.TRANSPARENT -> Color.TRANSPARENT
        }

    /** Edge colour: black normally, white when the text itself is black so it stays readable. */
    val edgeArgb: Int get() = if (color == SubColor.BLACK) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

    fun typeface(): Typeface = when (font) {
        SubFont.DEFAULT -> Typeface.DEFAULT
        SubFont.SANS -> Typeface.SANS_SERIF
        SubFont.CONDENSED -> Typeface.create("sans-serif-condensed", Typeface.NORMAL)
        SubFont.SERIF -> Typeface.SERIF
        SubFont.MONO -> Typeface.MONOSPACE
        SubFont.CASUAL -> Typeface.create("casual", Typeface.NORMAL)
    }

    fun toCaptionStyle(): CaptionStyleCompat = CaptionStyleCompat(
        color.argb, backgroundArgb, Color.TRANSPARENT,
        when (effectiveEdge) {
            SubEdge.OUTLINE -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            SubEdge.SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            else -> CaptionStyleCompat.EDGE_TYPE_NONE
        },
        edgeArgb, typeface()
    )

    companion object {
        inline fun <reified E : Enum<E>> parse(name: String?, default: E): E = name?.let { n -> enumValues<E>().firstOrNull { it.name == n } } ?: default
    }
}

/** One-tap combinations shown in Settings > Subtitles and in the player's style sheet. [CINEMA] is the default look. */
enum class SubtitlePreset(val label: String, val description: String, val style: SubtitleStyle) {
    CLASSIC("Classic", "White on a black box", SubtitleStyle(SubColor.WHITE, SubBackground.BLACK, SubSize.MEDIUM, SubFont.DEFAULT, SubEdge.NONE, SubPosition.BOTTOM)),
    CINEMA("Cinema", "White, transparent, outlined", SubtitleStyle(SubColor.WHITE, SubBackground.TRANSPARENT, SubSize.MEDIUM, SubFont.SANS, SubEdge.OUTLINE, SubPosition.BOTTOM)),
    SOFT_GLASS("Soft Glass", "White on a semi-transparent box, raised", SubtitleStyle(SubColor.WHITE, SubBackground.SEMI, SubSize.MEDIUM, SubFont.SANS, SubEdge.NONE, SubPosition.RAISED)),
    GOLDEN("Golden", "Yellow, transparent, outlined", SubtitleStyle(SubColor.YELLOW, SubBackground.TRANSPARENT, SubSize.MEDIUM, SubFont.SANS, SubEdge.OUTLINE, SubPosition.BOTTOM)),
    NIGHT_OWL("Night Owl", "Small cyan on a soft box, low glare", SubtitleStyle(SubColor.CYAN, SubBackground.SEMI, SubSize.SMALL, SubFont.CONDENSED, SubEdge.SHADOW, SubPosition.BOTTOM)),
    LARGE_PRINT("Large Print", "Extra large yellow on black, raised", SubtitleStyle(SubColor.YELLOW, SubBackground.BLACK, SubSize.XLARGE, SubFont.SANS, SubEdge.NONE, SubPosition.RAISED));

    companion object {
        /** The preset whose settings equal [style] exactly, or null when the user has customised something. */
        fun matching(style: SubtitleStyle): SubtitlePreset? = entries.firstOrNull { it.style == style }
        /** The preset after the one matching [style] (the first one when the style is custom); used by rows that cycle on OK. */
        fun next(style: SubtitleStyle): SubtitlePreset = matching(style)?.let { entries[(it.ordinal + 1) % entries.size] } ?: entries.first()
    }
}

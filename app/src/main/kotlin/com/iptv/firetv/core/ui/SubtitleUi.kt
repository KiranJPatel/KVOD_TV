package com.iptv.firetv.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.BorderStyle
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iptv.firetv.core.common.tr
import com.iptv.firetv.core.player.SubBackground
import com.iptv.firetv.core.player.SubColor
import com.iptv.firetv.core.player.SubEdge
import com.iptv.firetv.core.player.SubFont
import com.iptv.firetv.core.player.SubPosition
import com.iptv.firetv.core.player.SubSize
import com.iptv.firetv.core.player.SubtitlePreset
import com.iptv.firetv.core.player.SubtitleStyle

/** The next value of an enum, wrapping around (used by rows that cycle their value when OK is pressed). */
inline fun <reified E : Enum<E>> E.next(): E { val all = enumValues<E>(); return all[(ordinal + 1) % all.size] }

private fun SubFont.compose(): FontFamily = when (this) {
    SubFont.DEFAULT -> FontFamily.Default
    SubFont.SANS -> FontFamily.SansSerif
    SubFont.CONDENSED -> FontFamily(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.NORMAL))
    SubFont.SERIF -> FontFamily.Serif
    SubFont.MONO -> FontFamily.Monospace
    SubFont.CASUAL -> FontFamily(android.graphics.Typeface.create("casual", android.graphics.Typeface.NORMAL))
}

/** Shows how the chosen subtitle style will look on top of a picture, using the same choices the player applies. */
@Composable
fun SubtitlePreview(style: SubtitleStyle, modifier: Modifier = Modifier) {
    val base = 20f * (style.size.fraction / SubSize.MEDIUM.fraction)
    val color = Color(style.color.argb)
    val bg = Color(style.backgroundArgb)
    val edge = Color(style.edgeArgb)
    val family = style.font.compose()
    val text = tr("Subtitles will look like this.")
    val common = TextStyle(fontFamily = family, fontSize = base.sp, textAlign = TextAlign.Center)
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFF2E4A62), Color(0xFF6C7A89), Color(0xFFB9A58A)))), contentAlignment = when (style.position) {
        SubPosition.BOTTOM, SubPosition.RAISED -> Alignment.BottomCenter
        SubPosition.MIDDLE -> Alignment.Center
        SubPosition.TOP -> Alignment.TopCenter
    }) {
        Box(Modifier.padding(horizontal = 20.dp, vertical = if (style.position == SubPosition.BOTTOM) 12.dp else if (style.position == SubPosition.RAISED) 36.dp else 16.dp)) {
            Box(Modifier.background(bg, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp)) {
                when (style.effectiveEdge) {
                    SubEdge.OUTLINE -> {
                        Text(text, style = common.copy(color = edge, drawStyle = Stroke(width = 6f, join = StrokeJoin.Round)))
                        Text(text, style = common.copy(color = color))
                    }
                    SubEdge.SHADOW -> Text(text, style = common.copy(color = color, shadow = Shadow(edge, Offset(3f, 3f), 4f)))
                    else -> Text(text, style = common.copy(color = color))
                }
            }
        }
    }
}

/**
 * The six subtitle choices as rows that cycle on OK, with a live preview above. Used by Settings > Subtitles and by the style sheet
 * in the player, so both always offer the same options.
 */
@Composable
fun SubtitleStyleEditor(style: SubtitleStyle, onChange: (SubtitleStyle) -> Unit, modifier: Modifier = Modifier, firstFocus: androidx.compose.ui.focus.FocusRequester? = null, previewHeight: androidx.compose.ui.unit.Dp = 120.dp) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SubtitlePreview(style, Modifier.fillMaxWidth().height(previewHeight))
        val preset = SubtitlePreset.matching(style)
        SettingRow(tr("Preset"), { onChange(SubtitlePreset.next(style).style) }, icon = Icons.Filled.AutoAwesome, value = preset?.let { tr(it.label) } ?: tr("Custom"),
            subtitle = (preset ?: SubtitlePreset.next(style)).let { tr(it.description) }.let { if (preset == null) tr("Press OK to pick a preset.") else it }, focusRequester = firstFocus)
        SettingRow(tr("Text colour"), { onChange(style.copy(color = style.color.next())) }, icon = Icons.Filled.FormatColorText, value = tr(style.color.label))
        SettingRow(tr("Background"), { onChange(style.copy(background = style.background.next())) }, icon = Icons.Filled.Opacity, value = tr(style.background.label),
            subtitle = if (style.background == SubBackground.TRANSPARENT) tr("No box behind the text; an outline keeps it readable.") else null)
        SettingRow(tr("Text size"), { onChange(style.copy(size = style.size.next())) }, icon = Icons.Filled.FormatSize, value = tr(style.size.label))
        SettingRow(tr("Font"), { onChange(style.copy(font = style.font.next())) }, icon = Icons.Filled.TextFields, value = tr(style.font.label))
        SettingRow(tr("Text edge"), { onChange(style.copy(edge = style.edge.next())) }, icon = Icons.Filled.BorderStyle, value = tr(style.edge.label))
        SettingRow(tr("Position on screen"), { onChange(style.copy(position = style.position.next())) }, icon = Icons.Filled.VerticalAlignBottom, value = tr(style.position.label))
        SettingRow(tr("Reset to default"), { onChange(SubtitlePreset.CINEMA.style) }, icon = Icons.Filled.Subtitles, value = tr("Reset"))
    }
}

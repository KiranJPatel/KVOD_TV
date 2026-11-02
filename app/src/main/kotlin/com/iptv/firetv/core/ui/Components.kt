package com.iptv.firetv.core.ui

import com.iptv.firetv.core.common.tr
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage

/**
 * The one focusable building block. Focus = accent outline + soft glow + 1.04x scale (graphicsLayer, so layout never shifts).
 * onFocusChanged is placed before clickable so it observes the focus target created by clickable.
 */
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimens.CardRadius),
    scale: Float = 1.04f,
    containerColor: Color = LocalIptvColors.current.elevated,
    focusedContainerColor: Color? = null,
    onFocused: (() -> Unit)? = null,
    onFocusChange: ((Boolean) -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    /** False for pills that sit on the page background (top tabs): no outline until focused. */
    restBorder: Boolean = true,
    /** Called when the remote's Menu key is pressed while this surface is focused (used for "add to playlist" shortcuts). */
    onMenu: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val s by animateFloatAsState(if (focused) scale else 1f, tween(180), label = "focusScale")
    val glow = LocalIptvColors.current.focusGlow
    val bg = if (focused && focusedContainerColor != null) focusedContainerColor else containerColor
    Box(
        modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused?.invoke(); onFocusChange?.invoke(it.isFocused) }
            .then(if (onMenu != null) Modifier.onKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_MENU) { onMenu(); true } else false
            } else Modifier)
            .graphicsLayer { scaleX = s; scaleY = s }
            .shadow(if (focused) 14.dp else 0.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(bg)
            .then(if (focused || restBorder) Modifier.border(if (focused) 3.dp else 1.dp, if (focused) LocalIptvColors.current.focusRing else LocalIptvColors.current.divider, shape) else Modifier)
            .clickable(role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
    ) { content(focused) }
}

@Composable
fun TvButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, primary: Boolean = true, enabled: Boolean = true, focusRequester: FocusRequester? = null, danger: Boolean = false, contentDescription: String? = null) {
    val cs = MaterialTheme.colorScheme
    FocusSurface(
        onClick = { if (enabled) onClick() }, modifier = modifier.height(Dimens.ButtonHeight).graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        shape = RoundedCornerShape(Dimens.ButtonHeight / 2), scale = 1.05f,
        containerColor = when { danger -> cs.errorContainer; primary -> cs.primary; else -> LocalIptvColors.current.elevated },
        focusedContainerColor = when { danger -> cs.errorContainer; primary -> cs.primary; else -> cs.primaryContainer }, focusRequester = focusRequester
    ) {
        // With no text the button is icon-only: give it a square size (Modifier.width(Dimens.ButtonHeight)) at the call site.
        Row(Modifier.padding(horizontal = if (text.isEmpty()) 0.dp else 20.dp).align(Alignment.Center), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val c = when { danger -> cs.onErrorContainer; primary -> cs.onPrimary; else -> cs.onSurface }
            if (icon != null) Icon(icon, contentDescription ?: text.ifEmpty { null }, tint = c, modifier = Modifier.size(Dimens.IconInline))
            if (text.isNotEmpty()) Text(text, color = c, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Checkbox row (consent and other yes/no choices): same height, radius and type as every other list row. */
@Composable
fun CheckRow(label: String, checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    FocusSurface(onToggle, modifier.fillMaxWidth().height(Dimens.RowHeight), RoundedCornerShape(16.dp), 1.02f, restBorder = false, containerColor = LocalIptvColors.current.elevated) {
        Row(Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank, null, Modifier.size(Dimens.IconCircle + 2.dp), tint = if (checked) cs.primary else cs.onSurfaceVariant)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun NavItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    FocusSurface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(Dimens.RowHeight),
        shape = RoundedCornerShape(16.dp),
        scale = 1.02f,
        restBorder = false,
        containerColor = if (selected) cs.primaryContainer else LocalIptvColors.current.elevated,
        focusedContainerColor = if (selected) cs.primaryContainer else LocalIptvColors.current.elevated,
        focusRequester = focusRequester,
        onFocused = onFocused
    ) {
        Row(
            Modifier.padding(horizontal = 18.dp).align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(Dimens.IconCircle + 2.dp),
                tint = if (selected) cs.primary else cs.onSurfaceVariant
            )
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) cs.onPrimaryContainer else cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: (() -> Unit)? = null, dim: Boolean = false, focusRequester: FocusRequester? = null) {
    val cs = MaterialTheme.colorScheme
    FocusSurface(onClick, modifier.height(Dimens.ChipHeight).graphicsLayer { alpha = if (dim) 0.5f else 1f }, RoundedCornerShape(Dimens.ChipHeight / 2), 1.05f, restBorder = false,
        containerColor = if (selected) cs.onBackground else LocalIptvColors.current.elevated, onFocused = onFocused, focusRequester = focusRequester) {
        Text(text, Modifier.padding(horizontal = 16.dp).align(Alignment.Center), color = if (selected) cs.background else cs.onSurface,
            style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(bottom = 10.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)

@Composable
fun LiveBadge(modifier: Modifier = Modifier) = Box(modifier.clip(RoundedCornerShape(6.dp)).background(LocalIptvColors.current.live).padding(horizontal = 8.dp, vertical = 2.dp)) {
    Text(tr("LIVE"), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
}

/** Artwork with a monogram fallback for missing or failed images, so there is never an empty hole. */
@Composable
fun Artwork(url: String?, label: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val fallback: @Composable () -> Unit = {
        Box(modifier.background(LocalIptvColors.current.elevated), contentAlignment = Alignment.Center) {
            Text(label.trim().take(2).uppercase(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (url.isNullOrBlank()) fallback() else SubcomposeAsyncImage(model = url, contentDescription = label, modifier = modifier, contentScale = contentScale,
        loading = { Box(Modifier.fillMaxSize().background(LocalIptvColors.current.elevated)) }, error = { fallback() })
}

@Composable
fun ChannelCard(name: String, logo: String?, subtitle: String?, onClick: () -> Unit, modifier: Modifier = Modifier, live: Boolean = true, progress: Float? = null, onFocused: (() -> Unit)? = null, onMenu: (() -> Unit)? = null) {
    // Google TV-style: 16:9 logo tile, name and "now playing" underneath (not inside the tile).
    Column(modifier.width(190.dp)) {
        FocusSurface(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f), onFocused = onFocused, onMenu = onMenu) {
            Artwork(logo, name, Modifier.fillMaxSize().padding(18.dp))
            if (live) LiveBadge(Modifier.align(Alignment.TopStart).padding(12.dp))
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(14.dp))
        }
        Text(name, Modifier.padding(top = 10.dp, start = 4.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Text(subtitle, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PosterCard(title: String, poster: String?, onClick: () -> Unit, modifier: Modifier = Modifier, progress: Float? = null, subtitle: String? = null, onFocused: (() -> Unit)? = null, onMenu: (() -> Unit)? = null) {
    Column(modifier.width(168.dp)) {
        FocusSurface(onClick, Modifier.fillMaxWidth().height(250.dp), onFocused = onFocused, onMenu = onMenu) {
            Artwork(poster, title, Modifier.fillMaxSize(), ContentScale.Crop)
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(10.dp))
        }
        Text(title, Modifier.padding(top = 10.dp, start = 4.dp), style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Text(subtitle, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(LocalIptvColors.current.divider)) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(4.dp).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
fun FavoriteButton(isFavorite: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onToggle, modifier.size(Dimens.CircleButton + 4.dp), CircleShape, 1.1f) {
        Icon(if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder, if (isFavorite) "Remove from favorites" else "Add to favorites",
            tint = if (isFavorite) LocalIptvColors.current.warning else MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Center).size(Dimens.IconCircle + 2.dp))
    }
}

/** Round icon-only button with the same size as [FavoriteButton]; no visible text (the description is for accessibility only). */
@Composable
fun IconOnlyButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusSurface(onClick, modifier.size(Dimens.CircleButton + 4.dp), CircleShape, 1.1f) {
        Icon(icon, contentDescription, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.align(Alignment.Center).size(Dimens.IconCircle + 2.dp))
    }
}

@Composable
fun <T> TvRow(title: String, items: List<T>, key: ((T) -> Any)? = null, content: @Composable (T) -> Unit) {
    if (items.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        SectionTitle(title, Modifier.padding(start = 4.dp))
        // Vertical content padding leaves room for the focus scale and glow so nothing is clipped.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Dimens.Gap), contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)) {
            items(items, key = key) { content(it) }
        }
    }
}

// ----------------------------------------------------------------------------- states

@Composable
fun SkeletonBox(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.35f, 0.75f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "alpha")
    Box(modifier.clip(RoundedCornerShape(12.dp)).background(LocalIptvColors.current.elevated.copy(alpha = a)))
}

@Composable
fun SkeletonRow(modifier: Modifier = Modifier) = Row(modifier, horizontalArrangement = Arrangement.spacedBy(Dimens.Gap)) { repeat(6) { SkeletonBox(Modifier.size(200.dp, 150.dp)) } }

@Composable
fun StatePanel(title: String, message: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Filled.Inbox, isError: Boolean = false, actions: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)) {
        Icon(if (isError) Icons.Filled.ErrorOutline else icon, null, Modifier.size(48.dp), tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onBackground)
        Text(message, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { actions() }
    }
}

// ----------------------------------------------------------------------------- dialogs & input

@Composable
fun TvDialog(onDismiss: () -> Unit, title: String, subtitle: String? = null, width: Dp = 560.dp, icon: ImageVector? = null, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(Dimens.PanelRadius)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.width(width).shadow(24.dp, shape).clip(shape).background(cs.surface).border(1.dp, LocalIptvColors.current.divider, shape).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (icon != null) Box(Modifier.size(44.dp).clip(CircleShape).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, null, Modifier.size(24.dp), tint = cs.onPrimaryContainer)
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
fun ConfirmDialog(title: String, message: String, confirmText: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    val first = remember { FocusRequester() }
    TvDialog(onDismiss, title) {
        Text(message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Destructive actions start focus on Cancel so a stray OK press never deletes anything.
            TvButton(tr("Cancel"), onDismiss, primary = false, focusRequester = first)
            TvButton(confirmText, onConfirm, primary = true)
        }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}

@Composable
fun <T> OptionDialog(title: String, options: List<Pair<String, T>>, selected: T?, onSelect: (T) -> Unit, onDismiss: () -> Unit, subtitle: String? = null) {
    val req = remember { FocusRequester() }
    val cs = MaterialTheme.colorScheme
    val idx = options.indexOfFirst { it.second == selected }.coerceAtLeast(0)
    TvDialog(onDismiss, title, subtitle) {
        Column(Modifier.height((options.size.coerceAtMost(7) * 58).dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEachIndexed { i, (label, value) ->
                val sel = value == selected
                FocusSurface({ onSelect(value); onDismiss() }, Modifier.fillMaxWidth().height(Dimens.OptionRowHeight), RoundedCornerShape(16.dp), 1.02f, restBorder = false,
                    containerColor = if (sel) cs.primaryContainer else Color.Transparent, focusedContainerColor = if (sel) cs.primaryContainer else LocalIptvColors.current.elevated,
                    focusRequester = if (i == idx) req else null) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        RadioDot(sel)
                        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, color = if (sel) cs.onPrimaryContainer else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { req.requestFocus() } }
}

@Composable
fun RadioDot(selected: Boolean, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(modifier.size(20.dp).border(2.dp, if (selected) cs.primary else cs.onSurfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
        if (selected) Box(Modifier.size(10.dp).background(cs.primary, CircleShape))
    }
}

@Composable
fun TvTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, secret: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text, singleLine: Boolean = true, focusRequester: FocusRequester? = null, leadingIcon: ImageVector? = null) {
    var focused by remember { mutableStateOf(false) }
    val glow = LocalIptvColors.current.focusGlow
    Column(modifier) {
        if (label.isNotBlank()) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        BasicTextField(
            value = value, onValueChange = onValueChange, singleLine = singleLine,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(glow),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = if (secret) KeyboardType.Password else keyboardType),
            modifier = Modifier.fillMaxWidth().then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier).onFocusChanged { focused = it.isFocused }.height(Dimens.FieldHeight)
                .clip(RoundedCornerShape(18.dp)).background(LocalIptvColors.current.elevated)
                .border(if (focused) 3.dp else 1.dp, if (focused) LocalIptvColors.current.focusRing else LocalIptvColors.current.divider, RoundedCornerShape(18.dp)),
            decorationBox = { inner ->
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (leadingIcon != null) Icon(leadingIcon, null, Modifier.size(Dimens.IconInline), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { inner() }
                }
            }
        )
    }
}

/** D-pad friendly on-screen keyboard used by Search (works without the system IME). */
@Composable
fun OnScreenKeyboard(onKey: (String) -> Unit, onBackspace: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier, firstFocus: FocusRequester? = null) {
    val rows = listOf("abcdefg", "hijklmn", "opqrstu", "vwxyz'-", "0123456")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { c, ch -> FocusSurface({ onKey(ch.toString()) }, Modifier.size(Dimens.KeyHeight), RoundedCornerShape(12.dp), 1.08f, focusRequester = if (r == 0 && c == 0) firstFocus else null) {
                    Text(ch.toString().uppercase(), Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleSmall)
                } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusSurface({ onKey(" ") }, Modifier.width(112.dp).height(Dimens.KeyHeight), RoundedCornerShape(12.dp), 1.04f) { Text(tr("Space"), Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelMedium) }
            FocusSurface(onBackspace, Modifier.width(112.dp).height(Dimens.KeyHeight), RoundedCornerShape(12.dp), 1.04f) { Text(tr("Delete"), Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelMedium) }
            FocusSurface(onClear, Modifier.width(112.dp).height(Dimens.KeyHeight), RoundedCornerShape(12.dp), 1.04f) { Text(tr("Clear"), Modifier.align(Alignment.Center), style = MaterialTheme.typography.labelMedium) }
        }
    }
}

/** Numeric pad for PIN entry. */
@Composable
fun PinPad(pin: String, onChange: (String) -> Unit, onSubmit: () -> Unit, modifier: Modifier = Modifier, maxLen: Int = 6) {
    val first = remember { FocusRequester() }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("\u25CF".repeat(pin.length).ifEmpty { "Enter PIN" }, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
        listOf("123", "456", "789").forEachIndexed { r, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { c, ch -> FocusSurface({ if (pin.length < maxLen) onChange(pin + ch) }, Modifier.size(52.dp), RoundedCornerShape(12.dp), 1.08f, focusRequester = if (r == 0 && c == 0) first else null) { Text(ch.toString(), Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium) } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusSurface({ onChange(pin.dropLast(1)) }, Modifier.size(52.dp), RoundedCornerShape(12.dp), 1.08f) { Text(tr("\u232B"), Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium) }
            FocusSurface({ if (pin.length < maxLen) onChange(pin + "0") }, Modifier.size(52.dp), RoundedCornerShape(12.dp), 1.08f) { Text("0", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium) }
            FocusSurface(onSubmit, Modifier.size(52.dp), RoundedCornerShape(12.dp), 1.08f, containerColor = MaterialTheme.colorScheme.primary) { Text(tr("OK"), Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

/** 16:9 card used for Continue Watching: artwork, resume bar, title and remaining-time line below. */
@Composable
fun LandscapeCard(title: String, subtitle: String?, art: String?, progress: Float?, onClick: () -> Unit, modifier: Modifier = Modifier, onFocused: (() -> Unit)? = null) {
    Column(modifier.width(260.dp)) {
        FocusSurface(onClick, Modifier.fillMaxWidth().aspectRatio(16f / 9f), onFocused = onFocused) {
            Artwork(art, title, Modifier.fillMaxSize(), ContentScale.Crop)
            if (progress != null) ProgressLine(progress, Modifier.align(Alignment.BottomCenter).padding(14.dp))
        }
        Text(title, Modifier.padding(top = 10.dp, start = 4.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
        if (subtitle != null) Text(subtitle, Modifier.padding(start = 4.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Circular control with its label underneath (player dock). With [labelOnFocusOnly] the label appears only on the focused button,
 * which lets a dock with a dozen controls fit on one row; [active] marks a toggle that is switched on.
 */
@Composable
fun RoundIconButton(
    icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    size: Dp = Dimens.RoundControl, primary: Boolean = false, enabled: Boolean = true, focusRequester: FocusRequester? = null,
    active: Boolean = false, labelOnFocusOnly: Boolean = false
) {
    val cs = MaterialTheme.colorScheme
    var focused by remember { mutableStateOf(false) }
    val colW = if (labelOnFocusOnly) size + 12.dp else size + 32.dp
    Column(modifier.width(colW).graphicsLayer { alpha = if (enabled) 1f else 0.4f }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusSurface({ if (enabled) onClick() }, Modifier.size(size), CircleShape, 1.08f,
            containerColor = when { primary -> cs.primary; active -> cs.primaryContainer; else -> LocalIptvColors.current.elevated }, onFocusChange = { focused = it }, focusRequester = focusRequester) {
            Icon(icon, label, Modifier.align(Alignment.Center).size(if (size >= 60.dp) 32.dp else 26.dp), tint = when { primary -> cs.onPrimary; active -> cs.onPrimaryContainer; else -> cs.onSurface })
        }
        val showLabel = !labelOnFocusOnly || focused
        Text(label, Modifier.then(if (labelOnFocusOnly) Modifier.wrapContentWidth(Alignment.CenterHorizontally, unbounded = true) else Modifier).graphicsLayer { alpha = if (showLabel) 1f else 0f },
            style = MaterialTheme.typography.labelMedium, color = if (focused) cs.onSurface else cs.onSurfaceVariant, maxLines = 1, softWrap = !labelOnFocusOnly,
            fontWeight = if (focused) androidx.compose.ui.text.font.FontWeight.SemiBold else null)
    }
}

@Composable
fun SettingsGroup(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dimens.CardRadius)).background(LocalIptvColors.current.elevated).padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    value: String? = null,
    subtitle: String? = null,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    content: @Composable BoxScope.() -> Unit = {}
) {
    val cs = MaterialTheme.colorScheme
    FocusSurface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.fillMaxWidth().height(if (subtitle != null) 76.dp else Dimens.SettingRowHeight).graphicsLayer { alpha = if (enabled) 1f else 0.45f },
        shape = RoundedCornerShape(16.dp),
        scale = 1.02f,
        restBorder = false,
        containerColor = Color.Transparent,
        focusedContainerColor = LocalIptvColors.current.elevated,
        focusRequester = focusRequester
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = if (subtitle != null) 10.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, Modifier.size(Dimens.IconCircle + 2.dp), tint = cs.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.wrapContentWidth()) {
                content()
            }
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    focusRequester: FocusRequester? = null
) {
    val cs = MaterialTheme.colorScheme
    FocusSurface(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth().height(if (subtitle != null) 76.dp else Dimens.SettingRowHeight),
        shape = RoundedCornerShape(16.dp),
        scale = 1.02f,
        restBorder = false,
        containerColor = Color.Transparent,
        focusedContainerColor = LocalIptvColors.current.elevated,
        focusRequester = focusRequester
    ) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = if (subtitle != null) 10.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, Modifier.size(Dimens.IconCircle + 2.dp), tint = cs.onSurfaceVariant)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(
                if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = null,
                Modifier.size(Dimens.IconCircle + 2.dp),
                tint = if (checked) cs.primary else cs.onSurfaceVariant
            )
        }
    }
}

@Composable
fun <T> SegmentedChoice(
    options: List<Pair<String, T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.clip(RoundedCornerShape(14.dp)).background(LocalIptvColors.current.elevated).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (label, value) ->
            val sel = value == selected
            FocusSurface(
                onClick = { onSelect(value) },
                modifier = Modifier.height(36.dp),
                shape = RoundedCornerShape(10.dp),
                scale = 1.02f,
                restBorder = false,
                containerColor = if (sel) cs.primary else Color.Transparent,
                focusedContainerColor = if (sel) cs.primary else LocalIptvColors.current.elevated
            ) {
                Text(
                    label,
                    Modifier.padding(horizontal = 14.dp).align(Alignment.Center),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (sel) cs.onPrimary else cs.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun ActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    val cs = MaterialTheme.colorScheme
    FocusSurface(
        onClick = onClick,
        modifier = modifier.width(260.dp).aspectRatio(16f / 9f),
        shape = RoundedCornerShape(Dimens.CardRadius),
        scale = 1.04f,
        focusRequester = focusRequester
    ) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, null, Modifier.size(28.dp), tint = cs.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun ActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    val cs = MaterialTheme.colorScheme
    FocusSurface(
        onClick = onClick,
        modifier = modifier.width(220.dp).height(150.dp),
        shape = RoundedCornerShape(Dimens.CardRadius),
        scale = 1.04f,
        containerColor = if (accent) cs.primaryContainer else LocalIptvColors.current.elevated,
        focusedContainerColor = if (accent) cs.primaryContainer else LocalIptvColors.current.elevated,
        focusRequester = focusRequester
    ) {
        Column(
            Modifier.fillMaxSize().padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, null, Modifier.size(28.dp), tint = if (accent) cs.primary else cs.primary)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall, color = if (accent) cs.onPrimaryContainer else cs.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (accent) cs.onPrimaryContainer.copy(alpha = 0.8f) else cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    container: Color = MaterialTheme.colorScheme.primaryContainer,
    tint: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Box(
        modifier.size(size).clip(CircleShape).background(container),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tint)
    }
}

@Composable
fun LevelAdjustDialog(
    title: String,
    icon: ImageVector,
    value: Float,
    onChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    hint: String? = null,
    extraLabel: String? = null,
    onExtra: (() -> Unit)? = null,
    min: Float = 0f
) {
    val first = remember { FocusRequester() }
    TvDialog(onDismiss, title) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            if (hint != null) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TvButton(tr("Decrease"), { onChange((value - 0.1f).coerceIn(min, 1f)) }, primary = false, focusRequester = first)
                TvButton(tr("Increase"), { onChange((value + 0.1f).coerceIn(min, 1f)) }, primary = true)
                if (extraLabel != null && onExtra != null) {
                    TvButton(extraLabel, onExtra, primary = false)
                }
            }
        }
    }
    LaunchedEffect(Unit) { first.requestFocus() }
}

/**
 * Inline level control (volume, brightness) shown over the picture instead of a modal dialog, so the effect can be judged while
 * adjusting. Left/Right (or Down/Up) change the value in 5 % steps and repeat while the key is held, OK closes, and it closes by
 * itself after four seconds without a key press. Back is handled by the host screen.
 */
@Composable
fun LevelPanel(title: String, icon: ImageVector, value: Float, onChange: (Float) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier, min: Float = 0f, step: Float = 0.05f) {
    val cs = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    var activity by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(activity) { kotlinx.coroutines.delay(4_000); onClose() }
    fun nudge(direction: Int) {
        val next = (Math.round((value + direction * step) / step) * step).coerceIn(min, 1f)
        activity++; if (next != value) onChange(next)
    }
    Row(
        modifier.width(680.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xE60E1116)).padding(horizontal = 26.dp, vertical = 18.dp)
            .focusRequester(focus).focusable()
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (ev.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_LEFT, android.view.KeyEvent.KEYCODE_DPAD_DOWN -> { nudge(-1); true }
                    android.view.KeyEvent.KEYCODE_DPAD_RIGHT, android.view.KeyEvent.KEYCODE_DPAD_UP -> { nudge(1); true }
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER, android.view.KeyEvent.KEYCODE_ENTER, android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> { onClose(); true }
                    else -> false
                }
            },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Icon(icon, null, Modifier.size(34.dp), tint = Color.White)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text("${(value * 100).toInt()}%", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            }
            Box(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x33FFFFFF))) {
                Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).fillMaxHeight().background(cs.primary))
            }
            Text(tr("Left / Right to adjust  \u2022  OK to close"), style = MaterialTheme.typography.bodySmall, color = Color(0xFFA7B4C6))
        }
    }
}

@Composable
fun RowIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = Dimens.RoundControl,
    primary: Boolean = false,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    active: Boolean = false,
    labelOnFocusOnly: Boolean = false
) {
    RoundIconButton(icon, label, onClick, modifier, size, primary, enabled, focusRequester, active, labelOnFocusOnly)
}

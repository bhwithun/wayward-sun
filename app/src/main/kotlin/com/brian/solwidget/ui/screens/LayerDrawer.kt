package com.brian.solwidget.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.ripple
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max
import androidx.compose.ui.unit.sp
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.ui.theme.SolColors

internal data class LegendModel(
    val layers: ChartLayers,
    val showTooWarm: Boolean = false,
    val showTooCool: Boolean = false,
    val showHeatPump: Boolean = false,
    val showGasAux: Boolean = false,
    val showCoolRun: Boolean = false
)

private fun Modifier.expandTapTarget(): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val min = 48.dp.roundToPx()
    val width = max(placeable.width, min)
    val height = max(placeable.height, min)
    val x = (width - placeable.width) / 2
    val y = (height - placeable.height) / 2
    val lines = mutableMapOf<AlignmentLine, Int>()
    val first = placeable[FirstBaseline]
    val last = placeable[LastBaseline]
    if (first != AlignmentLine.Unspecified) lines[FirstBaseline] = first + y
    if (last != AlignmentLine.Unspecified) lines[LastBaseline] = last + y
    layout(width, height, lines) { placeable.place(x, y) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LegendButton(
    open: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onAnchor: (androidx.compose.ui.geometry.Rect) -> Unit = {}
) {
    Row(
        modifier = modifier
            .then(if (compact) Modifier else Modifier.expandTapTarget())
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false),
                onClick = onClick
            )
            .onGloballyPositioned { onAnchor(it.boundsInRoot()) }
            .semantics { contentDescription = if (open) "Hide legend" else "Show legend" }
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .border(1.dp, Color.White.copy(alpha = 0.70f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "i",
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = "Legend",
            color = Color.White.copy(alpha = 0.70f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
internal fun LegendCard(
    model: LegendModel,
    modifier: Modifier = Modifier
) {
    val groups = legendGroups(
        model.layers,
        model.showTooWarm,
        model.showTooCool,
        model.showHeatPump,
        model.showGasAux,
        model.showCoolRun
    )
    Surface(
        modifier = modifier
            .widthIn(max = 320.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        color = SolColors.Panel,
        shape = RoundedCornerShape(12.dp),
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (groups.isEmpty()) {
                Text("No layers on", color = SolColors.Muted, fontSize = 13.sp)
            }
            groups.forEach { group ->
                LegendGroup(group)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LegendGroup(group: List<LegendItem>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        group.forEach { item -> LegendChip(item, fontSize = 13.sp) }
    }
}

@Composable
private fun LegendChip(item: LegendItem, fontSize: androidx.compose.ui.unit.TextUnit = 11.sp) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = when (item.mark) {
                LegendMark.HAIRLINE -> Modifier
                    .size(width = 12.dp, height = 2.dp)
                    .background(item.color)
                LegendMark.SQUARE -> Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(item.color)
                LegendMark.DOT -> Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(item.color)
            }
        )
        Text(
            text = item.name,
            color = SolColors.Ink,
            fontSize = fontSize,
            maxLines = 1
        )
    }
}

internal enum class LayerGroup(val title: String) {
    Solar("Solar"),
    Weather("Weather"),
    Grid("Grid"),
    Home("Home")
}

internal data class LayerToggle(
    val layer: ChartLayer,
    val label: String,
    val group: LayerGroup,
    val onWidget: Boolean = false
)

/** Chart switches in drawer order. Rows marked [LayerToggle.onWidget] stay on the main drawer. */
internal val ChartLayerToggles: List<LayerToggle> = listOf(
    LayerToggle(ChartLayer.SOLCAST, "Solcast", LayerGroup.Solar),
    LayerToggle(ChartLayer.ENPHASE, "Enphase", LayerGroup.Solar),
    LayerToggle(ChartLayer.TEMPERATURE, "Temp", LayerGroup.Weather),
    LayerToggle(ChartLayer.PRECIPITATION, "Precip", LayerGroup.Weather),
    LayerToggle(ChartLayer.BUY, "Buy", LayerGroup.Grid),
    LayerToggle(ChartLayer.SELL, "Sell", LayerGroup.Grid),
    LayerToggle(ChartLayer.ECOBEE, "Ecobee", LayerGroup.Home),
    LayerToggle(ChartLayer.HVAC, "HVAC", LayerGroup.Home)
)

internal fun layerToggles(
    includeWidgetRows: Boolean,
    rows: List<LayerToggle> = ChartLayerToggles
): List<LayerToggle> = rows.filter { includeWidgetRows || !it.onWidget }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullscreenLayersButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .size(FullscreenLayersButtonSize)
            .testTag("fullscreen-layers-button")
            .clip(CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .background(Color(0xCC0B1220))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = true),
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = "Open layers" },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Menu,
            contentDescription = null,
            tint = SolColors.Ink,
            modifier = Modifier.size(24.dp)
        )
    }
}

/**
 * The layer switches, in the same order as the main drawer. Fullscreen passes
 * [includeWidgetRows] false so an "On widget" row stays on the drawer.
 */
@Composable
internal fun LayerSwitchSection(
    layers: ChartLayers,
    onToggle: (ChartLayer, Boolean) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    includeWidgetRows: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LAYERS",
                color = SolColors.Muted,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close menu",
                    tint = SolColors.Ink,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        var lastGroup: LayerGroup? = null
        layerToggles(includeWidgetRows).forEach { toggle ->
            if (toggle.group != lastGroup) {
                LayerSubhead(toggle.group.title)
                lastGroup = toggle.group
            }
            val swatch: (@Composable () -> Unit)? = when (toggle.layer) {
                ChartLayer.ECOBEE -> {
                    { EcobeeSwatch() }
                }
                ChartLayer.HVAC -> {
                    { HvacSwatch() }
                }
                else -> null
            }
            LayerSwitch(
                name = toggle.label,
                color = layerColor(toggle.layer),
                checked = layers.isOn(toggle.layer),
                swatch = swatch
            ) { onToggle(toggle.layer, it) }
        }
        TextButton(
            onClick = onReset,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        ) {
            Text("Reset layers", color = SolColors.Forecast)
        }
    }
}

@Composable
fun LayerDrawerContent(
    layers: ChartLayers,
    versionName: String,
    checkingUpdates: Boolean,
    onToggle: (ChartLayer, Boolean) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    onWebApp: () -> Unit,
    onCheckUpdates: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        LayerSwitchSection(
            layers = layers,
            onToggle = onToggle,
            onReset = onReset,
            onClose = onClose,
            includeWidgetRows = true
        )
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = SolColors.TableStripe
        )
        DrawerAction(
            title = "Web app",
            icon = { ExternalLinkIcon() },
            onClick = onWebApp
        )
        DrawerAction(
            title = "Check for updates",
            subtitle = if (checkingUpdates) "Checking…" else versionName,
            trailing = if (checkingUpdates) {
                {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = SolColors.Muted
                    )
                }
            } else {
                null
            },
            onClick = onCheckUpdates,
            enabled = !checkingUpdates
        )
        DrawerAction(title = "Settings", onClick = onSettings)
    }
}

private enum class LegendMark { DOT, SQUARE, HAIRLINE }

private data class LegendItem(
    val name: String,
    val color: Color,
    val mark: LegendMark = LegendMark.DOT
)

private fun legendGroups(
    layers: ChartLayers,
    showTooWarm: Boolean,
    showTooCool: Boolean,
    showHeatPump: Boolean,
    showGasAux: Boolean,
    showCoolRun: Boolean
): List<List<LegendItem>> = buildList {
    val trace = buildList {
        if (layers.solcast) add(LegendItem("Solcast", SolColors.Forecast))
        if (layers.enphase) add(LegendItem("Enphase", SolColors.Live))
        if (layers.temperature) add(LegendItem("Outdoor", SolColors.Temp))
    }
    if (trace.isNotEmpty()) add(trace)
    if (layers.ecobee) {
        val home = buildList {
            add(LegendItem("Setpoint", SolColors.Setpoint, LegendMark.HAIRLINE))
            if (showTooWarm) add(LegendItem("Too warm", SolColors.CoolGap.copy(alpha = 0.40f), LegendMark.SQUARE))
            if (showTooCool) add(LegendItem("Too cool", SolColors.HeatGap.copy(alpha = 0.50f), LegendMark.SQUARE))
        }
        add(home)
    }
    if (layers.hvac) {
        val runtime = buildList {
            if (showHeatPump) add(LegendItem("Heat pump", SolColors.HeatPump, LegendMark.SQUARE))
            if (showGasAux) add(LegendItem("Aux heat", SolColors.AuxHeat, LegendMark.SQUARE))
            if (showCoolRun) add(LegendItem("Cool", SolColors.CoolRun, LegendMark.SQUARE))
        }
        if (runtime.isNotEmpty()) add(runtime)
    }
    val other = buildList {
        if (layers.precipitation) add(LegendItem("Precip", SolColors.Precip.copy(alpha = 1f)))
        if (layers.buy) add(LegendItem("Buy", SolColors.Buy))
        if (layers.sell) add(LegendItem("Sell", SolColors.Sell))
    }
    if (other.isNotEmpty()) add(other)
}

private fun ChartLayers.isOn(layer: ChartLayer): Boolean = when (layer) {
    ChartLayer.TEMPERATURE -> temperature
    ChartLayer.BUY -> buy
    ChartLayer.SELL -> sell
    ChartLayer.PRECIPITATION -> precipitation
    ChartLayer.SOLCAST -> solcast
    ChartLayer.ENPHASE -> enphase
    ChartLayer.ECOBEE -> ecobee
    ChartLayer.HVAC -> hvac
}

private fun layerColor(layer: ChartLayer): Color = when (layer) {
    ChartLayer.TEMPERATURE -> SolColors.Temp
    ChartLayer.BUY -> SolColors.Buy
    ChartLayer.SELL -> SolColors.Sell
    ChartLayer.PRECIPITATION -> SolColors.Precip.copy(alpha = 1f)
    ChartLayer.SOLCAST -> SolColors.Forecast
    ChartLayer.ENPHASE -> SolColors.Live
    ChartLayer.ECOBEE -> SolColors.Setpoint
    ChartLayer.HVAC -> SolColors.HeatPump
}

@Composable
private fun LayerSubhead(title: String) {
    Text(
        text = title,
        color = SolColors.Muted,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun HvacSwatch() {
    Row(horizontalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.size(14.dp)) {
        Box(Modifier.weight(1f).fillMaxHeight().background(SolColors.HeatPump))
        Box(Modifier.weight(1f).fillMaxHeight().background(SolColors.AuxHeat))
        Box(Modifier.weight(1f).fillMaxHeight().background(SolColors.CoolRun))
    }
}

@Composable
private fun EcobeeSwatch() {
    Row(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
    ) {
        Box(Modifier.weight(1f).fillMaxHeight().background(SolColors.CoolGap))
        Box(Modifier.weight(1f).fillMaxHeight().background(SolColors.HeatGap))
    }
}

@Composable
private fun LayerSwitch(
    name: String,
    color: Color,
    checked: Boolean,
    swatch: (@Composable () -> Unit)? = null,
    onChecked: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (swatch != null) {
            swatch()
        } else {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            text = name,
            color = SolColors.Ink,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = Modifier
                .testTag("layer-switch-$name")
                .semantics { contentDescription = name },
            colors = SwitchDefaults.colors(
                checkedThumbColor = SolColors.Ink,
                checkedTrackColor = SolColors.PanelAlt,
                uncheckedThumbColor = SolColors.Muted,
                uncheckedTrackColor = SolColors.Navy,
                uncheckedBorderColor = SolColors.TableStripe
            )
        )
    }
}

@Composable
private fun DrawerAction(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = SolColors.Ink, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, color = SolColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (trailing != null) trailing()
        if (icon != null) {
            Box(Modifier.padding(start = 8.dp)) { icon() }
        }
    }
}

@Composable
private fun ExternalLinkIcon() {
    Canvas(Modifier.size(18.dp)) {
        val color = SolColors.Ink
        val w = 1.6.dp.toPx()
        val m = 1.dp.toPx()
        val box = size.minDimension * 0.58f
        val bottom = size.height - m
        val left = m
        drawLine(color, Offset(left, bottom - box), Offset(left, bottom), w)
        drawLine(color, Offset(left, bottom), Offset(left + box, bottom), w)
        drawLine(color, Offset(left + box, bottom), Offset(left + box, bottom - box * 0.42f), w)
        drawLine(color, Offset(left, bottom - box), Offset(left + box * 0.42f, bottom - box), w)
        val end = Offset(size.width - m, m)
        val start = Offset(size.width * 0.36f, size.height * 0.64f)
        drawLine(color, start, end, w)
        drawLine(color, end, Offset(end.x - 5.dp.toPx(), end.y), w)
        drawLine(color, end, Offset(end.x, end.y + 5.dp.toPx()), w)
    }
}

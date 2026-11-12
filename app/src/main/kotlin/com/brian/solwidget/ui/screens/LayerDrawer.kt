package com.brian.solwidget.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.brian.solwidget.data.ChartLayer
import com.brian.solwidget.data.ChartLayers
import com.brian.solwidget.ui.theme.SolColors

@Composable
fun ChartLegend(
    layers: ChartLayers,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    showTooWarm: Boolean = true,
    showTooCool: Boolean = true,
    showHeatPump: Boolean = false,
    showGasAux: Boolean = false,
    showCoolRun: Boolean = false
) {
        val groups = legendGroups(layers, showTooWarm, showTooCool, showHeatPump, showGasAux, showCoolRun)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .semantics { contentDescription = "Chart legend. Opens layers." },
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        groups.forEach { group ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                group.forEach { item -> LegendChip(item) }
            }
        }
    }
}

@Composable
private fun LegendChip(item: LegendItem) {
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
            fontSize = 11.sp,
            maxLines = 1
        )
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
        LayerSubhead("Solar")
        LayerSwitch("Solcast", SolColors.Forecast, layers.solcast) { onToggle(ChartLayer.SOLCAST, it) }
        LayerSwitch("Enphase", SolColors.Live, layers.enphase) { onToggle(ChartLayer.ENPHASE, it) }
        LayerSubhead("Weather")
        LayerSwitch("Temp", SolColors.Temp, layers.temperature) { onToggle(ChartLayer.TEMPERATURE, it) }
        LayerSwitch("Precip", SolColors.Precip.copy(alpha = 1f), layers.precipitation) {
            onToggle(ChartLayer.PRECIPITATION, it)
        }
        LayerSubhead("Grid")
        LayerSwitch("Buy", SolColors.Buy, layers.buy) { onToggle(ChartLayer.BUY, it) }
        LayerSwitch("Sell", SolColors.Sell, layers.sell) { onToggle(ChartLayer.SELL, it) }
        LayerSubhead("Home")
        LayerSwitch(
            "Ecobee",
            SolColors.Setpoint,
            layers.ecobee,
            swatch = { EcobeeSwatch() }
        ) { onToggle(ChartLayer.ECOBEE, it) }
        LayerSwitch(
            "HVAC",
            SolColors.HeatPump,
            layers.hvac,
            swatch = { HvacSwatch() }
        ) { onToggle(ChartLayer.HVAC, it) }
        TextButton(
            onClick = onReset,
            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
        ) {
            Text("Reset layers", color = SolColors.Forecast)
        }
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

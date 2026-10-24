package com.fuhbe.ytdlpg.simple

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop

/** Legacy geometry contract retained for the unchanged 0.6.4 tests. Production
 * selection now uses physical measured item bounds, including in RTL. */
internal fun glassDockTarget(position: Offset, size: IntSize): Int? =
    if (size.width <= 0 || size.height <= 0 || !position.x.isFinite() || !position.y.isFinite() ||
        position.x < 0f || position.x >= size.width || position.y < 0f || position.y >= size.height) null
    else (position.x / (size.width / 3f)).toInt().coerceIn(0, 2)

/** Exact same destinations and actions. No navigation until a valid release. */
@Composable internal fun GlassNavigationDock(
    area:String,onBack:()->Unit,onDownloads:()->Unit,onSettings:()->Unit,
    source:Backdrop?,modifier:Modifier=Modifier
) {
    val selected=when(area) { "downloads"->1;"settings"->2;else->0 }
    val actions=listOf(onBack,onDownloads,onSettings)
    GlassSlidingSegments(selected,3,{actions[it]()},modifier.testTag("glass-navigation"),source,"glass-navigation-capsule") {
        DockDestination("Home","Back",Icons.Default.Home,selected==0,onBack,Modifier.weight(1f).glassSegmentItem(0))
        DockDestination("Downloads","Downloads",Icons.Default.DownloadForOffline,selected==1,onDownloads,Modifier.weight(1f).glassSegmentItem(1))
        DockDestination("Settings","Settings",Icons.Default.Settings,selected==2,onSettings,Modifier.weight(1f).glassSegmentItem(2))
    }
}

@Composable private fun DockDestination(label:String, description:String, icon:ImageVector,
    active:Boolean, action:()->Unit, modifier:Modifier) {
    // Selection color is interpolated in the shared draw phase, including the lens.
    val color=MaterialTheme.colorScheme.onSurface
    val localizedDescription=uiText(description)
    val semantics=Modifier.semantics { selected=active;role=Role.Tab;contentDescription=localizedDescription }
    val body:@Composable ()->Unit={
        Column(Modifier.fillMaxWidth().padding(horizontal=3.dp,vertical=8.dp),
            horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Icon(icon,null,Modifier.size(22.dp),tint=color)
            Text(uiText(label),fontSize=10.sp,fontWeight=FontWeight.SemiBold,color=color)
        }
    }
    if(active) Box(modifier.heightIn(min=56.dp).then(semantics),contentAlignment=Alignment.Center) { body() }
    else Surface(onClick=action,color=Color.Transparent,shape=RoundedCornerShape(25.dp),
        modifier=modifier.heightIn(min=56.dp).then(semantics)) { body() }
}

/** The same component serves MP3/MP4, Normal/ZIP and Downloads/Completed. */
@Composable internal fun GlassSelectionTrack(selected:Int,count:Int,onSelected:(Int)->Unit,sourceKey:Any?=null,content:@Composable RowScope.()->Unit) {
    GlassSlidingSegments(selected,count,onSelected,modifier=Modifier.testTag("glass-segment-track"),sourceKey=sourceKey,content=content)
}

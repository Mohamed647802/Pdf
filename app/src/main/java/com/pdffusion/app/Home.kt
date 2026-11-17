package com.pdffusion.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class Tool(val title: String, val emoji: String, val sub: String, val color: Color) {
    MERGE("دمج ملفات PDF", "🔗", "حدّد عدة ملفات ثم ادمجها في ملف واحد بالترتيب الذي تريده", Color(0xFF7C5CFF)),
    SPLIT("تقسيم PDF", "✂️", "", Color(0xFF00B8D9)),
    EXTRACT("استخراج صفحات", "📑", "", Color(0xFF2ECC71)),
    DELETE("حذف صفحات", "🗑️", "", Color(0xFFFF5A6E)),
    ROTATE("تدوير الصفحات", "🔄", "", Color(0xFFFFA726)),
    REORDER("ترتيب الصفحات", "↕️", "", Color(0xFFAB47BC)),
    IMG2PDF("صور إلى PDF", "🖼️", "", Color(0xFF29B6F6)),
    PDF2IMG("PDF إلى صور", "📷", "", Color(0xFF26A69A)),
    COMPRESS("ضغط PDF", "🗜️", "", Color(0xFFFFCA28)),
    PROTECT("حماية بكلمة سر", "🔒", "", Color(0xFFEF5350)),
    UNLOCK("فك الحماية", "🔓", "", Color(0xFF66BB6A)),
    WATERMARK("علامة مائية", "💧", "", Color(0xFF42A5F5)),
    NUMBERS("ترقيم الصفحات", "🔢", "", Color(0xFFEC407A)),
    TEXT("استخراج النص", "📝", "", Color(0xFF8D6E63)),
    INFO("خصائص الملف", "ℹ️", "", Color(0xFF78909C)),
    VIEW("عرض PDF", "👁️", "", Color(0xFF5C6BC0))
}

@Composable
fun Root() {
    var tool by remember { mutableStateOf<Tool?>(null) }
    BackHandler(enabled = tool != null) { tool = null }
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalContentColor provides TextHi
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(BgTop, BgBottom)))
                .drawBehind {
                    val c1 = Offset(size.width * 0.92f, size.height * 0.04f)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Accent.copy(alpha = 0.30f), Accent.copy(alpha = 0f)),
                            center = c1,
                            radius = size.width * 0.75f
                        ),
                        radius = size.width * 0.75f,
                        center = c1
                    )
                    val c2 = Offset(size.width * 0.05f, size.height * 0.95f)
                    drawCircle(
                        Brush.radialGradient(
                            listOf(Accent2.copy(alpha = 0.18f), Accent2.copy(alpha = 0f)),
                            center = c2,
                            radius = size.width * 0.8f
                        ),
                        radius = size.width * 0.8f,
                        center = c2
                    )
                }
        ) {
            val t = tool
            if (t == null) Home { tool = it } else ToolHost(t) { tool = null }
        }
    }
}

@Composable
fun Home(onOpen: (Tool) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Danger, Accent))),
                contentAlignment = Alignment.Center
            ) {
                Text("PDF", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("PDF Fusion", color = TextHi, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("كل أدوات PDF في مكان واحد", color = TextLo, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Tile(Tool.MERGE, Modifier.fillMaxWidth().height(150.dp), onOpen, big = true)
        Squares(listOf(Tool.SPLIT, Tool.EXTRACT, Tool.DELETE), onOpen)
        Rects(listOf(Tool.ROTATE, Tool.REORDER), onOpen)
        Squares(listOf(Tool.IMG2PDF, Tool.PDF2IMG, Tool.COMPRESS), onOpen)
        Rects(listOf(Tool.PROTECT, Tool.UNLOCK), onOpen)
        Squares(listOf(Tool.WATERMARK, Tool.NUMBERS, Tool.TEXT), onOpen)
        Rects(listOf(Tool.INFO, Tool.VIEW), onOpen)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun Squares(items: List<Tool>, onOpen: (Tool) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { Tile(it, Modifier.weight(1f).aspectRatio(1f), onOpen) }
    }
}

@Composable
fun Rects(items: List<Tool>, onOpen: (Tool) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items.forEach { Tile(it, Modifier.weight(1f).height(90.dp), onOpen) }
    }
}

@Composable
fun Tile(t: Tool, modifier: Modifier, onOpen: (Tool) -> Unit, big: Boolean = false) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(t.color.copy(alpha = 0.40f), t.color.copy(alpha = 0.10f))
                )
            )
            .border(1.dp, t.color.copy(alpha = 0.40f), shape)
            .clickable { onOpen(t) }
            .padding(14.dp)
    ) {
        Text(
            t.emoji,
            fontSize = if (big) 42.sp else 28.sp,
            modifier = Modifier.align(Alignment.TopStart)
        )
        if (big) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("★ الأهم", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                t.title,
                color = TextHi,
                fontWeight = FontWeight.Bold,
                fontSize = if (big) 22.sp else 13.sp,
                maxLines = 2
            )
            if (big) Text(t.sub, color = TextLo, fontSize = 13.sp)
        }
    }
}

@Composable
fun ToolHost(tool: Tool, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MiniBtn("→", onClick = onBack)
            Spacer(Modifier.width(12.dp))
            Text(
                tool.emoji + "  " + tool.title,
                color = TextHi,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tool) {
                Tool.MERGE -> MergeScreen()
                Tool.REORDER -> ReorderScreen()
                Tool.IMG2PDF -> ImagesScreen()
                Tool.VIEW -> ViewerScreen()
                else -> SingleScreen(tool)
            }
        }
    }
}

package com.pdffusion.app

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------- منطق النقل (دوال نقية) ----------

private fun moveStep(order: List<Int>, sel: Set<Int>, d: Int): List<Int> {
    val l = order.toMutableList()
    if (d < 0) {
        for (i in 1 until l.size) {
            if (l[i] in sel && l[i - 1] !in sel) {
                val t = l[i]; l[i] = l[i - 1]; l[i - 1] = t
            }
        }
    } else {
        for (i in l.size - 2 downTo 0) {
            if (l[i] in sel && l[i + 1] !in sel) {
                val t = l[i]; l[i] = l[i + 1]; l[i + 1] = t
            }
        }
    }
    return l
}

private fun moveEdge(order: List<Int>, sel: Set<Int>, start: Boolean): List<Int> {
    val a = order.filter { it in sel }
    val b = order.filter { it !in sel }
    return if (start) a + b else b + a
}

private fun moveAt(order: List<Int>, sel: Set<Int>, target: Int, after: Boolean): List<Int> {
    if (target in sel) return order
    val moved = order.filter { it in sel }
    val rest = order.filter { it !in sel }
    val at = rest.indexOf(target) + (if (after) 1 else 0)
    return rest.subList(0, at) + moved + rest.subList(at, rest.size)
}

// ---------- الشاشة ----------

@Composable
fun ReorderScreen() {
    var pdf by remember { mutableStateOf<Pdf?>(null) }
    val op = rememberOp()
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PdfPicker(pdf) {
            pdf = it
            op.status = Status.Idle
        }
        val p = pdf
        if (p == null) {
            Panel {
                Text("↕️", fontSize = 44.sp)
                Text(
                    "اختر ملف PDF لتظهر كل صفحاته. حدّد صفحة أو أكثر ثم حرّكها للمكان الذي تريده، وبعدها عاين واحفظ أو شارك.",
                    color = TextLo,
                    fontSize = 14.sp
                )
            }
        } else if (p.pages < 0) {
            Text("🔒 الملف محمي بكلمة مرور — فُكّ حمايته أولًا", color = Danger)
        } else if (p.pages == 0) {
            Text("⚠️ تعذر قراءة الملف", color = Danger)
        } else {
            key(p.id) { ReorderBody(p, op, Modifier.weight(1f)) }
        }
    }
}

@Composable
fun ReorderBody(p: Pdf, op: Op, modifier: Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val reader = remember(p) { try { PdfReader(p.file) } catch (e: Throwable) { null } }
    DisposableEffect(reader) { onDispose { reader?.close() } }
    val original = remember(p) { (0 until p.pages).toList() }
    var order by remember(p) { mutableStateOf(original) }
    var sel by remember(p) { mutableStateOf(setOf<Int>()) }
    var moving by remember(p) { mutableStateOf(false) }
    var side by remember(p) { mutableStateOf(0) }
    var preview by remember(p) { mutableStateOf(false) }
    val changed = order != original

    fun upd(o: List<Int>) {
        order = o
        if (op.status !is Status.Busy) op.status = Status.Idle
    }

    fun save(share: Boolean) {
        if (op.status is Status.Busy) return
        val idx = order
        val base = baseName(p.name)
        op.status = Status.Busy
        op.progress = ""
        scope.launch {
            op.status = try {
                val uri = withContext(Dispatchers.IO) {
                    pagesOp(ctx, p.file, base, idx, "reordered")
                }
                if (share) shareUri(ctx, uri, "application/pdf")
                Status.Done("✅ تم حفظ الملف بالترتيب الجديد\nالمسار: Download/$APP_DIR", uri)
            } catch (e: Throwable) {
                Status.Fail(errText(e))
            }
        }
    }

    if (reader == null) {
        Text("⚠️ تعذر عرض صفحات هذا الملف", color = Danger)
    } else {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${p.pages} صفحة • محدد ${sel.size}",
                    color = TextLo,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f)
                )
                Act("تحديد الكل", Modifier.width(96.dp)) { sel = order.toSet() }
                Act("إلغاء", Modifier.width(64.dp), enabled = sel.isNotEmpty()) {
                    sel = emptySet()
                    moving = false
                }
            }

            if (sel.isEmpty()) {
                Text("اضغط على صفحة أو أكثر لتحديدها ثم حرّكها.", color = TextLo, fontSize = 12.sp)
            } else {
                Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Act("⏮ للبداية", Modifier.weight(1f)) { upd(moveEdge(order, sel, true)) }
                        Act("▶ تقديم", Modifier.weight(1f)) { upd(moveStep(order, sel, -1)) }
                        Act("تأخير ◀", Modifier.weight(1f)) { upd(moveStep(order, sel, 1)) }
                        Act("⏭ للنهاية", Modifier.weight(1f)) { upd(moveEdge(order, sel, false)) }
                    }
                    if (!moving) {
                        Act("📍 نقل المحدد إلى مكان معيّن", Modifier.fillMaxWidth()) { moving = true }
                    } else {
                        SectionLabel("اختر مكان الوضع ثم اضغط على الصفحة الهدف")
                        Pills(listOf("قبل الصفحة", "بعد الصفحة"), side) { side = it }
                        Act("إلغاء النقل", Modifier.fillMaxWidth()) { moving = false }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(order.size, key = { order[it] }) { pos ->
                    val orig = order[pos]
                    PageTile(reader, orig, pos + 1, orig in sel) {
                        if (moving) {
                            if (orig !in sel) {
                                upd(moveAt(order, sel, orig, side == 1))
                                moving = false
                            }
                        } else {
                            sel = if (orig in sel) sel - orig else sel + orig
                        }
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniBtn("↺", enabled = changed) {
                    upd(original)
                    sel = emptySet()
                    moving = false
                }
                OutlinedButton(
                    onClick = { preview = true },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("👁 معاينة", fontSize = 13.sp, maxLines = 1) }
                Button(
                    onClick = { save(false) },
                    enabled = changed && op.status !is Status.Busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = Color.White,
                        disabledContainerColor = CardHi,
                        disabledContentColor = TextLo
                    )
                ) { Text("💾 حفظ", fontSize = 13.sp, maxLines = 1) }
                OutlinedButton(
                    onClick = { save(true) },
                    enabled = changed && op.status !is Status.Busy,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("📤 مشاركة", fontSize = 13.sp, maxLines = 1) }
            }
            StatusView(op)
        }

        if (preview) {
            ReorderPreview(
                reader,
                order,
                order.indexOfFirst { it in sel }.coerceAtLeast(0)
            ) { preview = false }
        }
    }
}

@Composable
private fun Act(
    label: String,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 6.dp)
    ) {
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun PageTile(
    r: PdfReader,
    orig: Int,
    pos: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bmp by produceState<Bitmap?>(null, r, orig) {
        value = withContext(Dispatchers.IO) {
            try { r.render(orig, 320) } catch (e: Throwable) { null }
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.72f)
            .clip(shape)
            .background(Color.White)
            .border(if (selected) 3.dp else 1.dp, if (selected) Accent else CardHi, shape)
            .clickable(onClick = onClick)
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(22.dp),
                color = Accent,
                strokeWidth = 2.dp
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (selected) Accent else Color(0xCC000000))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("$pos", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        if (orig + 1 != pos) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text("كانت ${orig + 1}", color = Color.White, fontSize = 10.sp)
            }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center
            ) {
                Text("✓", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReorderPreview(
    r: PdfReader,
    order: List<Int>,
    start: Int,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(Modifier.fillMaxSize().background(BgTop)) {
            val st = rememberLazyListState(initialFirstVisibleItemIndex = start)
            Column(Modifier.fillMaxSize()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniBtn("✕", onClick = onClose)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "معاينة بالترتيب الجديد • ${order.size} صفحة",
                        color = TextHi,
                        fontWeight = FontWeight.Bold
                    )
                }
                LazyColumn(
                    Modifier.weight(1f).padding(horizontal = 12.dp),
                    state = st,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(order.size) { k ->
                        Column {
                            PageView(r, order[k])
                            Text(
                                "صفحة ${k + 1}",
                                color = TextLo,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

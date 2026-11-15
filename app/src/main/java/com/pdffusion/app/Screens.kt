package com.pdffusion.app

import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class Img(val id: Long, val uri: Uri, val name: String)

fun hintOf(t: Tool): String = when (t) {
    Tool.SPLIT -> "قسّم الملف: كل صفحة لوحدها، أو كل عدد معيّن من الصفحات، أو بنطاقات تحددها."
    Tool.EXTRACT -> "استخرج صفحات محددة في ملف PDF جديد."
    Tool.DELETE -> "احذف الصفحات غير المرغوبة واحتفظ بالباقي."
    Tool.ROTATE -> "دوّر كل الصفحات أو صفحات محددة."
    Tool.REORDER -> "أعد ترتيب صفحات الملف كما تريد."
    Tool.PDF2IMG -> "حوّل صفحات الملف إلى صور JPG أو PNG."
    Tool.COMPRESS -> "قلّل حجم الملف بضغط الصور الموجودة بداخله."
    Tool.PROTECT -> "ضع كلمة مرور لفتح الملف."
    Tool.UNLOCK -> "أزل كلمة المرور من ملف محمي (تحتاج معرفة كلمة المرور)."
    Tool.WATERMARK -> "أضف علامة مائية قطرية على كل الصفحات (حروف إنجليزية وأرقام)."
    Tool.NUMBERS -> "أضف رقم الصفحة أسفل كل صفحة."
    Tool.TEXT -> "استخرج النص من الملف إلى ملف TXT."
    Tool.INFO -> "اعرض وعدّل عنوان الملف والمؤلف والكلمات المفتاحية."
    else -> ""
}

@Composable
fun PdfPicker(current: Pdf?, onPicked: (Pdf) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            loading = true
            scope.launch {
                try {
                    onPicked(importPdf(ctx, uri))
                } catch (e: Throwable) {
                    Toast.makeText(ctx, "تعذر فتح الملف: " + errText(e), Toast.LENGTH_LONG).show()
                }
                loading = false
            }
        }
    }
    Panel(Modifier.clickable { launcher.launch(arrayOf("application/pdf")) }) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp), color = Accent, strokeWidth = 3.dp)
                Spacer(Modifier.width(10.dp))
                Text("جارٍ فتح الملف…", color = TextHi)
            }
        } else if (current == null) {
            Text("📂  اضغط هنا لاختيار ملف PDF", color = TextHi, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(current.file, current.pages < 0)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(current.name, color = TextHi, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(meta(current), color = if (current.pages <= 0) Danger else TextLo, fontSize = 12.sp)
                }
                Text("تغيير", color = Accent, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ItemRow(
    i: Int,
    n: Int,
    title: String,
    sub: String,
    subColor: Color,
    thumb: @Composable () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onDel: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(Accent),
            contentAlignment = Alignment.Center
        ) {
            Text("${i + 1}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        thumb()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextHi, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(sub, color = subColor, fontSize = 12.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MiniBtn("▲", enabled = i > 0, onClick = onUp)
            MiniBtn("▼", enabled = i < n - 1, onClick = onDown)
        }
        Spacer(Modifier.width(6.dp))
        MiniBtn("✕", tint = Danger, onClick = onDel)
    }
}

@Composable
fun MergeScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val op = rememberOp()
    var files by remember { mutableStateOf(listOf<Pdf>()) }
    var name by remember { mutableStateOf("merged") }
    var loading by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            loading = true
            scope.launch {
                val ok = ArrayList<Pdf>()
                var failed = 0
                for (u in uris) {
                    try {
                        ok.add(importPdf(ctx, u))
                    } catch (e: Throwable) {
                        failed++
                    }
                }
                files = files + ok
                loading = false
                op.status = Status.Idle
                if (failed > 0) Toast.makeText(ctx, "تعذر فتح $failed ملف", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun move(i: Int, d: Int) {
        val j = i + d
        if (j in files.indices) {
            val l = files.toMutableList()
            val t = l[i]
            l[i] = l[j]
            l[j] = t
            files = l
        }
    }

    val locked = files.count { it.pages < 0 }
    val unreadable = files.count { it.pages == 0 }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { picker.launch(arrayOf("application/pdf")) },
                modifier = Modifier.weight(1f).height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
            ) {
                Text(
                    if (files.isEmpty()) "➕  اختيار ملفات PDF" else "➕  إضافة المزيد",
                    fontWeight = FontWeight.Bold
                )
            }
            if (files.size > 1) {
                OutlinedButton(
                    onClick = { files = files.sortedBy { it.name.lowercase() } },
                    modifier = Modifier.height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("ترتيب أ-ي") }
            }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        if (files.isEmpty()) {
            Panel(Modifier.weight(1f)) {
                Text("🔗", fontSize = 44.sp)
                Text(
                    "اختر ملفين أو أكثر (تقدر تحدد عدة ملفات مرة واحدة)، ثم رتّبها بالأسهم ▲ ▼ وادمجها في ملف واحد.",
                    color = TextLo,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(files, key = { _, f -> f.id }) { i, f ->
                    ItemRow(
                        i, files.size, f.name, meta(f),
                        if (f.pages <= 0) Danger else TextLo,
                        { Thumb(f.file, f.pages < 0) },
                        { move(i, -1) },
                        { move(i, 1) },
                        { files = files.filter { it.id != f.id } }
                    )
                }
            }
            Text(
                "${files.size} ملف • ${files.sumOf { maxOf(it.pages, 0) }} صفحة • ${fmtSize(files.sumOf { it.size })}",
                color = TextLo,
                fontSize = 13.sp
            )
            if (locked > 0) {
                Text("🔒 يوجد ملف محمي بكلمة مرور — احذفه أو فُكّ حمايته أولًا.", color = Danger, fontSize = 13.sp)
            }
            if (unreadable > 0) {
                Text("⚠️ يوجد ملف تعذر قراءته — احذفه من القائمة.", color = Danger, fontSize = 13.sp)
            }
            Field(name, { name = it }, "اسم الملف الناتج")
            RunButton(
                "🔗  دمج ${files.size} ملفات",
                op,
                enabled = files.size >= 2 && locked == 0 && unreadable == 0
            ) {
                val fl = files.map { it.file }
                val outName = cleanName(name, "merged") + ".pdf"
                op.run { o ->
                    o.progress = "${fl.size} ملفات"
                    val uri = mergePdfs(ctx, fl, outName)
                    Status.Done("✅ تم دمج ${fl.size} ملفات في ملف واحد\nالمسار: Download/$APP_DIR", uri)
                }
            }
            StatusView(op)
        }
    }
}

@Composable
fun ImagesScreen() {
    val ctx = LocalContext.current
    val op = rememberOp()
    var files by remember { mutableStateOf(listOf<Img>()) }
    var name by remember { mutableStateOf("images") }
    var pageMode by remember { mutableStateOf(1) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        val t0 = System.nanoTime()
        files = files + uris.mapIndexed { k, u -> Img(t0 + k, u, displayName(ctx, u) ?: "image") }
        op.status = Status.Idle
    }

    fun move(i: Int, d: Int) {
        val j = i + d
        if (j in files.indices) {
            val l = files.toMutableList()
            val t = l[i]
            l[i] = l[j]
            l[j] = t
            files = l
        }
    }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Button(
            onClick = { picker.launch(arrayOf("image/*")) },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White)
        ) {
            Text(
                if (files.isEmpty()) "➕  اختيار صور" else "➕  إضافة المزيد",
                fontWeight = FontWeight.Bold
            )
        }
        if (files.isEmpty()) {
            Panel(Modifier.weight(1f)) {
                Text("🖼️", fontSize = 44.sp)
                Text(
                    "اختر صورة أو أكثر، رتّبها، وسيتم وضع كل صورة في صفحة داخل ملف PDF واحد.",
                    color = TextLo,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(files, key = { _, f -> f.id }) { i, f ->
                    ItemRow(
                        i, files.size, f.name, "صورة ${i + 1}", TextLo,
                        { ImgThumb(f.uri) },
                        { move(i, -1) },
                        { move(i, 1) },
                        { files = files.filter { it.id != f.id } }
                    )
                }
            }
            SectionLabel("حجم الصفحة")
            Pills(listOf("مطابق للصورة", "A4"), pageMode) { pageMode = it }
            Field(name, { name = it }, "اسم الملف الناتج")
            RunButton("🖼️  إنشاء PDF من ${files.size} صورة", op) {
                val list = files.map { it.uri }
                val a4 = pageMode == 1
                val outName = cleanName(name, "images") + ".pdf"
                op.run { o ->
                    val uri = imagesToPdf(ctx, list, outName, a4) { o.progress = it }
                    Status.Done("✅ تم إنشاء PDF من ${list.size} صورة\nالمسار: Download/$APP_DIR", uri)
                }
            }
            StatusView(op)
        }
    }
}

@Composable
fun SingleScreen(tool: Tool) {
    var pdf by remember { mutableStateOf<Pdf?>(null) }
    val op = rememberOp()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(hintOf(tool), color = TextLo, fontSize = 13.sp)
        PdfPicker(pdf) {
            pdf = it
            op.status = Status.Idle
        }
        val p = pdf
        if (p != null) {
            if (p.pages < 0 && tool != Tool.UNLOCK) {
                Panel { Text("🔒 هذا الملف محمي بكلمة مرور. افتحه أولًا بأداة «فك الحماية».", color = Danger) }
            } else if (p.pages == 0) {
                Panel { Text("⚠️ تعذر قراءة هذا الملف (قد يكون تالفًا).", color = Danger) }
            } else {
                when (tool) {
                    Tool.SPLIT -> SplitOptions(p, op)
                    Tool.EXTRACT, Tool.DELETE, Tool.ROTATE, Tool.REORDER -> PagesOptions(tool, p, op)
                    Tool.PDF2IMG -> PdfToImgOptions(p, op)
                    Tool.COMPRESS -> CompressOptions(p, op)
                    Tool.PROTECT -> ProtectOptions(p, op)
                    Tool.UNLOCK -> UnlockOptions(p, op)
                    Tool.WATERMARK -> WatermarkOptions(p, op)
                    Tool.NUMBERS -> NumbersOptions(p, op)
                    Tool.TEXT -> TextOptions(p, op)
                    Tool.INFO -> InfoOptions(p, op)
                    else -> {}
                }
                StatusView(op)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun SplitOptions(p: Pdf, op: Op) {
    val ctx = LocalContext.current
    var mode by remember { mutableStateOf(0) }
    var n by remember { mutableStateOf("2") }
    var ranges by remember { mutableStateOf("") }
    Panel {
        SectionLabel("طريقة التقسيم")
        Pills(listOf("كل صفحة", "كل N صفحات", "نطاقات"), mode) { mode = it }
        if (mode == 1) Field(n, { n = it.filter { c -> c.isDigit() } }, "عدد الصفحات في كل ملف", number = true)
        if (mode == 2) Field(ranges, { ranges = it }, "نطاقات، مثال: 1-3, 4-6, 9")
        Text("عدد صفحات الملف: ${p.pages}", color = TextLo, fontSize = 12.sp)
    }
    RunButton("✂️  تقسيم الآن", op) {
        val m = mode
        val k = n.toIntOrNull() ?: 0
        val r = ranges
        op.run { o ->
            val groups: List<List<Int>> = when (m) {
                0 -> (0 until p.pages).map { listOf(it) }
                1 -> {
                    if (k < 1) throw IllegalArgumentException("اكتب عددًا صحيحًا")
                    (0 until p.pages).chunked(k)
                }
                else -> r.split(',', '،', ';')
                    .filter { it.isNotBlank() }
                    .map { parsePages(it, p.pages) }
            }
            if (groups.isEmpty()) throw IllegalArgumentException("اكتب النطاقات")
            val where = splitPdf(ctx, p.file, baseName(p.name), groups) { o.progress = it }
            Status.Done("✅ تم إنشاء ${groups.size} ملف\nالمسار: $where")
        }
    }
}

@Composable
fun PagesOptions(tool: Tool, p: Pdf, op: Op) {
    val ctx = LocalContext.current
    var spec by remember { mutableStateOf("") }
    var angle by remember { mutableStateOf(0) }
    Panel {
        SectionLabel(
            when (tool) {
                Tool.EXTRACT -> "الصفحات المطلوب استخراجها"
                Tool.DELETE -> "الصفحات المطلوب حذفها"
                Tool.ROTATE -> "الصفحات المراد تدويرها (فارغ = كل الصفحات)"
                else -> "الترتيب الجديد للصفحات"
            }
        )
        Field(spec, { spec = it }, "مثال: 1-3, 5, 8-10")
        if (tool == Tool.ROTATE) {
            Pills(listOf("90° يمين", "180°", "90° يسار"), angle) { angle = it }
        }
        if (tool == Tool.REORDER) {
            OutlinedButton(onClick = { spec = "${p.pages}-1" }) { Text("↕️  عكس ترتيب كل الصفحات") }
            Text(
                "اكتب الصفحات بالترتيب الجديد، مثال: 3, 1, 2 (يمكن تكرار صفحة أو تجاهل صفحات).",
                color = TextLo,
                fontSize = 12.sp
            )
        }
        Text("عدد صفحات الملف: ${p.pages}", color = TextLo, fontSize = 12.sp)
    }
    RunButton("✅  تنفيذ", op) {
        val s = spec
        val a = angle
        op.run {
            val base = baseName(p.name)
            val total = p.pages
            when (tool) {
                Tool.ROTATE -> {
                    val idx = if (s.isBlank()) null else parsePages(s, total)
                    val deg = when (a) {
                        0 -> 90
                        1 -> 180
                        else -> 270
                    }
                    Status.Done("✅ تم تدوير الصفحات", rotatePdf(ctx, p.file, base, idx, deg))
                }
                Tool.DELETE -> {
                    val del = parsePages(s, total).toSet()
                    val keep = (0 until total).filter { it !in del }
                    if (keep.isEmpty()) throw IllegalArgumentException("لا يمكن حذف كل الصفحات")
                    Status.Done(
                        "✅ تم حذف ${del.size} صفحة، المتبقي ${keep.size}",
                        pagesOp(ctx, p.file, base, keep, "edited")
                    )
                }
                Tool.EXTRACT -> {
                    val idx = parsePages(s, total)
                    Status.Done("✅ تم استخراج ${idx.size} صفحة", pagesOp(ctx, p.file, base, idx, "extract"))
                }
                else -> {
                    val idx = parsePages(s, total)
                    Status.Done(
                        "✅ تم ترتيب الصفحات (${idx.size} صفحة)",
                        pagesOp(ctx, p.file, base, idx, "reordered")
                    )
                }
            }
        }
    }
}

@Composable
fun PdfToImgOptions(p: Pdf, op: Op) {
    val ctx = LocalContext.current
    var fmt by remember { mutableStateOf(0) }
    var q by remember { mutableStateOf(1) }
    var spec by remember { mutableStateOf("") }
    Panel {
        SectionLabel("صيغة الصورة")
        Pills(listOf("JPG", "PNG"), fmt) { fmt = it }
        SectionLabel("الجودة")
        Pills(listOf("عادية", "عالية", "ممتازة"), q) { q = it }
        Field(spec, { spec = it }, "الصفحات (فارغ = كل الصفحات)")
    }
    RunButton("📷  تحويل إلى صور", op) {
        val s = spec
        val png = fmt == 1
        val scale = when (q) {
            0 -> 1.3f
            1 -> 2f
            else -> 3f
        }
        op.run { o ->
            val idx = if (s.isBlank()) (0 until p.pages).toList() else parsePages(s, p.pages)
            pdfToImages(ctx, p.file, baseName(p.name), idx, png, scale) { o.progress = it }
            Status.Done("✅ تم حفظ ${idx.size} صورة\nالمسار: Pictures/$APP_DIR (تظهر في المعرض)")
        }
    }
}

@Composable
fun CompressOptions(p: Pdf, op: Op) {
    val ctx = LocalContext.current
    var lvl by remember { mutableStateOf(1) }
    Panel {
        SectionLabel("مستوى الضغط")
        Pills(listOf("خفيف", "متوسط", "قوي"), lvl) { lvl = it }
        Text(
            "يضغط الصور داخل الملف ويترك النص كما هو. الملفات النصية فقط غالبًا لا تنضغط أكثر.",
            color = TextLo,
            fontSize = 12.sp
        )
    }
    RunButton("🗜️  ضغط الآن", op) {
        val l = lvl
        op.run {
            val maxSide = when (l) {
                0 -> 2400
                1 -> 1800
                else -> 1200
            }
            val quality = when (l) {
                0 -> 80
                1 -> 62
                else -> 45
            }
            val (uri, before, after) = compressPdf(ctx, p.file, baseName(p.name), maxSide, quality)
            if (uri == null) {
                Status.Done("ℹ️ لا يمكن تصغير هذا الملف أكثر (لا توجد صور قابلة للضغط).")
            } else {
                val pct = (before - after) * 100L / before
                Status.Done("✅ من ${fmtSize(before)} إلى ${fmtSize(after)} (توفير $pct%)", uri)
            }
        }
    }
}

@Composable
fun ProtectOptions(p: Pdf, op: Op) {
    val ctx = LocalContext.current
    var a by remember { mutableStateOf("") }
    var b by remember { mutableStateOf("") }
    Panel {
        Field(a, { a = it }, "كلمة المرور", password = true)
        Field(b, { b = it }, "تأكيد كلمة المرور", password = true)
    }
    RunButton("🔒  حماية الملف", op, enabled = a.isNotEmpty()) {
        val x = a
        val y = b
        op.run {
            if (x != y) throw IllegalArgumentException("كلمتا المرور غير متطابقتين")
            Status.Done(
                "✅ تم تشفير الملف. احتفظ بكلمة المرور!",
                protectPdf(ctx, p.file, baseName(p.name), x)
            )
        }
    }
}

@Composable
fun UnlockOptions(p: Pdf, op: Op) {
    val ctx = LocalContext.current
    var pw by remember { mutableStateOf("") }
    Panel {
        Field(pw, { pw = it }, "كلمة مرور الملف", password = true)
    }
    RunButton("🔓  فك الحماية", op) {
        val x = pw
        op.run {
            Status.Done("✅ تم إنشاء نسخة بدون كلمة مرور", unlockPdf(ctx, p.file, baseName(p.name), x))
        }
    }
}

@Composable
fun WatermarkOptions(p: Pdf, op: Op) {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    Panel {
        Field(text, { text = it }, "نص العلامة المائية (إنجليزي)")
    }
    RunButton("💧  إضافة العلامة", op, enabled = text.isNotBlank()) {
        val t = text.trim()
        op.run {
            Status.Done("✅ تمت إضافة العلامة المائية", watermarkPdf(ctx, p.file, baseName(p.name), t))
        }
    }
}

@Composable
fun NumbersOptions(p: Pdf, op: Op) {
    val ctx = LocalContext.current
    var pos by remember { mutableStateOf(1) }
    Panel {
        SectionLabel("مكان الرقم أسفل الصفحة")
        Pills(listOf("يسار", "وسط", "يمين"), pos) { pos = it }
    }
    RunButton("🔢  ترقيم الصفحات", op) {
        val x = pos
        op.run {
            Status.Done("✅ تم ترقيم الصفحات", numberPagesPdf(ctx, p.file, baseName(p.name), x))
        }
    }
}

@Composable
fun TextOptions(p: Pdf, op: Op) {
    val ctx = LocalContext.current
    RunButton("📝  استخراج النص", op) {
        op.run {
            val u = extractText(ctx, p.file, baseName(p.name))
            if (u == null) {
                Status.Done("ℹ️ لا يوجد نص قابل للاستخراج (غالبًا الملف عبارة عن صور ممسوحة ضوئيًا).")
            } else {
                Status.Done("✅ تم استخراج النص\nالمسار: Download/$APP_DIR", u, "text/plain")
            }
        }
    }
}

@Composable
fun InfoOptions(p: Pdf, op: Op) {
    val ctx = LocalContext.current
    var t by remember(p) { mutableStateOf("") }
    var a by remember(p) { mutableStateOf("") }
    var s by remember(p) { mutableStateOf("") }
    var k by remember(p) { mutableStateOf("") }
    LaunchedEffect(p) {
        val v = withContext(Dispatchers.IO) {
            try { readInfo(p.file) } catch (e: Throwable) { null }
        }
        if (v != null) {
            t = v[0]
            a = v[1]
            s = v[2]
            k = v[3]
        }
    }
    Panel {
        Field(t, { t = it }, "العنوان")
        Field(a, { a = it }, "المؤلف")
        Field(s, { s = it }, "الموضوع")
        Field(k, { k = it }, "الكلمات المفتاحية")
        Text("${p.pages} صفحة • ${fmtSize(p.size)}", color = TextLo, fontSize = 12.sp)
    }
    RunButton("💾  حفظ نسخة بالبيانات الجديدة", op) {
        val v = listOf(t, a, s, k)
        op.run {
            Status.Done("✅ تم حفظ الملف بالبيانات الجديدة", writeInfo(ctx, p.file, baseName(p.name), v))
        }
    }
}

@Composable
fun ViewerScreen() {
    var pdf by remember { mutableStateOf<Pdf?>(null) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PdfPicker(pdf) { pdf = it }
        val p = pdf
        if (p != null) {
            if (p.pages > 0) {
                Box(Modifier.weight(1f)) { Reader(p) }
            } else {
                Text(
                    if (p.pages < 0) "🔒 الملف محمي بكلمة مرور — فُكّ حمايته أولًا" else "⚠️ تعذر قراءة الملف",
                    color = Danger
                )
            }
        }
    }
}

@Composable
fun Reader(p: Pdf) {
    val reader = remember(p) { try { PdfReader(p.file) } catch (e: Throwable) { null } }
    DisposableEffect(reader) { onDispose { reader?.close() } }
    if (reader == null) {
        Text("⚠️ تعذر عرض الملف", color = Danger)
    } else {
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(reader.count) { i -> PageView(reader, i) }
        }
    }
}

@Composable
fun PageView(r: PdfReader, i: Int) {
    val bmp by produceState<Bitmap?>(null, r, i) {
        value = withContext(Dispatchers.IO) {
            try { r.render(i, 1100) } catch (e: Throwable) { null }
        }
    }
    val b = bmp
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White)
    ) {
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        } else {
            Box(
                Modifier.fillMaxWidth().height(420.dp),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator(color = Accent) }
        }
    }
}

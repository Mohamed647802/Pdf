package com.pdffusion.app

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.IdentityHashMap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

const val APP_DIR = "PDF Fusion"

data class Pdf(val id: Long, val name: String, val file: File, val size: Long, val pages: Int)

// ---------- فتح وعرض ----------

fun openDoc(f: File, pw: String = ""): PDDocument =
    PDDocument.load(f, pw, MemoryUsageSetting.setupTempFileOnly())

fun <T> withRenderer(f: File, block: (PdfRenderer) -> T): T {
    val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    try {
        val r = PdfRenderer(pfd)
        try {
            return block(r)
        } finally {
            r.close()
        }
    } finally {
        pfd.close()
    }
}

fun pageCount(f: File): Int {
    try {
        return withRenderer(f) { it.pageCount }
    } catch (e: SecurityException) {
        return -1
    } catch (e: Exception) {
    }
    return try {
        openDoc(f).use { it.numberOfPages }
    } catch (e: InvalidPasswordException) {
        -1
    } catch (e: Exception) {
        0
    }
}

fun renderBitmap(
    r: PdfRenderer,
    index: Int,
    widthPx: Int = 0,
    scale: Float = 1f,
    maxSide: Int = 3000
): Bitmap {
    val p = r.openPage(index)
    try {
        val f = if (widthPx > 0) widthPx.toFloat() / p.width else scale
        var w = (p.width * f).toInt().coerceAtLeast(1)
        var h = (p.height * f).toInt().coerceAtLeast(1)
        val m = max(w, h)
        if (m > maxSide) {
            val k = maxSide.toFloat() / m
            w = (w * k).toInt().coerceAtLeast(1)
            h = (h * k).toInt().coerceAtLeast(1)
        }
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(android.graphics.Color.WHITE)
        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bmp
    } finally {
        p.close()
    }
}

fun renderThumb(f: File): Bitmap? =
    try {
        withRenderer(f) { renderBitmap(it, 0, widthPx = 150) }
    } catch (e: Throwable) {
        null
    }

class PdfReader(file: File) {
    private val pfd: ParcelFileDescriptor =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val r = PdfRenderer(pfd)
    val count: Int = r.pageCount

    @Synchronized
    fun render(i: Int, w: Int): Bitmap = renderBitmap(r, i, widthPx = w)

    @Synchronized
    fun close() {
        try { r.close() } catch (e: Throwable) { }
        try { pfd.close() } catch (e: Throwable) { }
    }
}

fun displayName(ctx: Context, uri: Uri): String? =
    try {
        ctx.contentResolver
            .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (e: Exception) {
        null
    }

suspend fun importPdf(ctx: Context, uri: Uri): Pdf = withContext(Dispatchers.IO) {
    val name = displayName(ctx, uri) ?: "document.pdf"
    val dir = File(ctx.cacheDir, "in").apply { mkdirs() }
    val f = File(dir, "${System.nanoTime()}.pdf")
    val input = ctx.contentResolver.openInputStream(uri)
        ?: throw IllegalStateException("تعذر قراءة الملف")
    input.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
    Pdf(System.nanoTime(), name, f, f.length(), pageCount(f))
}

// ---------- الحفظ ----------

fun tmp(ctx: Context, ext: String = "pdf"): File {
    val d = File(ctx.cacheDir, "out").apply { mkdirs() }
    return File(d, "${System.nanoTime()}.$ext")
}

fun saveTo(
    ctx: Context,
    src: File,
    name: String,
    mime: String,
    collection: Uri,
    rel: String
): Uri {
    val res = ctx.contentResolver
    val v = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = res.insert(collection, v)
        ?: throw IllegalStateException("تعذر إنشاء الملف في التخزين")
    val out = res.openOutputStream(uri)
        ?: throw IllegalStateException("تعذر الكتابة في التخزين")
    out.use { o -> src.inputStream().use { it.copyTo(o) } }
    val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
    res.update(uri, done, null, null)
    src.delete()
    return uri
}

fun saveToDownloads(
    ctx: Context,
    src: File,
    name: String,
    sub: String = APP_DIR,
    mime: String = "application/pdf"
): Uri = saveTo(
    ctx, src, name, mime,
    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
    Environment.DIRECTORY_DOWNLOADS + "/" + sub
)

fun cleanName(raw: String, def: String): String {
    val s = raw.trim()
        .removeSuffix(".pdf")
        .removeSuffix(".PDF")
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
    return if (s.isEmpty()) def else s
}

fun baseName(n: String): String = cleanName(n.substringBeforeLast('.', n), "document")

// ---------- أرقام الصفحات ----------

fun parsePages(spec: String, total: Int): List<Int> {
    val s = spec.map { c -> if (c in '٠'..'٩') ('0' + (c - '٠')) else c }
        .joinToString("")
        .replace(Regex("\\s*-\\s*"), "-")
    val out = ArrayList<Int>()
    val tokens = s.split(',', '،', ';', '؛', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    for (tok in tokens) {
        val parts = tok.split('-')
        val a = parts[0].toIntOrNull()
            ?: throw IllegalArgumentException("قيمة غير صحيحة: $tok")
        val b = when {
            parts.size == 1 -> a
            parts.size == 2 && parts[1].isEmpty() -> total
            parts.size == 2 -> parts[1].toIntOrNull()
                ?: throw IllegalArgumentException("قيمة غير صحيحة: $tok")
            else -> throw IllegalArgumentException("قيمة غير صحيحة: $tok")
        }
        if (a !in 1..total || b !in 1..total) {
            throw IllegalArgumentException("رقم الصفحة خارج النطاق (1-$total): $tok")
        }
        val range = if (a <= b) a..b else a downTo b
        range.forEach { out.add(it - 1) }
    }
    if (out.isEmpty()) throw IllegalArgumentException("اكتب أرقام الصفحات")
    return out
}

// ---------- العمليات ----------

fun mergePdfs(ctx: Context, files: List<File>, outName: String): Uri {
    val t = tmp(ctx)
    val m = PDFMergerUtility()
    m.destinationFileName = t.absolutePath
    for (f in files) m.addSource(f)
    m.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
    return saveToDownloads(ctx, t, outName)
}

private fun importClean(dst: PDDocument, page: PDPage) {
    val p = dst.importPage(page)
    try {
        p.annotations = p.annotations.filter { it !is PDAnnotationLink }
    } catch (e: Exception) {
    }
}

fun saveSubset(
    ctx: Context,
    doc: PDDocument,
    idx: List<Int>,
    outName: String,
    sub: String = APP_DIR
): Uri {
    val out = PDDocument()
    try {
        for (i in idx) importClean(out, doc.getPage(i))
        val t = tmp(ctx)
        out.save(t)
        return saveToDownloads(ctx, t, outName, sub)
    } finally {
        out.close()
    }
}

fun pagesOp(ctx: Context, src: File, base: String, idx: List<Int>, suffix: String): Uri {
    val doc = openDoc(src)
    try {
        return saveSubset(ctx, doc, idx, "${base}_$suffix.pdf")
    } finally {
        doc.close()
    }
}

fun splitPdf(
    ctx: Context,
    src: File,
    base: String,
    groups: List<List<Int>>,
    progress: (String) -> Unit
): String {
    val folder = "$APP_DIR/${base}_split"
    val doc = openDoc(src)
    try {
        groups.forEachIndexed { k, g ->
            progress("${k + 1} / ${groups.size}")
            saveSubset(ctx, doc, g, "${base}_${k + 1}.pdf", folder)
        }
    } finally {
        doc.close()
    }
    return "Download/$folder"
}

fun rotatePdf(ctx: Context, src: File, base: String, idx: List<Int>?, deg: Int): Uri {
    val doc = openDoc(src)
    try {
        val targets = idx ?: (0 until doc.numberOfPages).toList()
        for (i in targets) {
            val p = doc.getPage(i)
            p.rotation = ((p.rotation + deg) % 360 + 360) % 360
        }
        val t = tmp(ctx)
        doc.save(t)
        return saveToDownloads(ctx, t, "${base}_rotated.pdf")
    } finally {
        doc.close()
    }
}

fun protectPdf(ctx: Context, src: File, base: String, pw: String): Uri {
    val doc = openDoc(src)
    try {
        val policy = StandardProtectionPolicy(pw, pw, AccessPermission())
        policy.encryptionKeyLength = 128
        doc.protect(policy)
        val t = tmp(ctx)
        doc.save(t)
        return saveToDownloads(ctx, t, "${base}_protected.pdf")
    } finally {
        doc.close()
    }
}

fun unlockPdf(ctx: Context, src: File, base: String, pw: String): Uri {
    val doc = openDoc(src, pw)
    try {
        doc.isAllSecurityToBeRemoved = true
        val t = tmp(ctx)
        doc.save(t)
        return saveToDownloads(ctx, t, "${base}_unlocked.pdf")
    } finally {
        doc.close()
    }
}

fun watermarkPdf(ctx: Context, src: File, base: String, text: String): Uri {
    require(Regex("^[\\x20-\\x7E]+\$").matches(text)) {
        "العلامة المائية: حروف إنجليزية وأرقام ورموز عادية فقط"
    }
    val doc = openDoc(src)
    try {
        val font = PDType1Font.HELVETICA_BOLD
        val unit = font.getStringWidth(text) / 1000f
        val gs = PDExtendedGraphicsState()
        gs.setNonStrokingAlphaConstant(0.25f)
        gs.setStrokingAlphaConstant(0.25f)
        for (page in doc.pages) {
            val box = page.mediaBox
            val w = box.width
            val h = box.height
            val fs = min(96f, 0.7f * sqrt(w * w + h * h) / unit)
            val tw = unit * fs
            val ang = Math.toRadians(45.0)
            val cx = box.lowerLeftX + w / 2f
            val cy = box.lowerLeftY + h / 2f
            val tx = cx - (tw / 2f) * cos(ang).toFloat()
            val ty = cy - (tw / 2f) * sin(ang).toFloat()
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.setGraphicsStateParameters(gs)
                cs.setNonStrokingColor(0.5f, 0.5f, 0.5f)
                cs.beginText()
                cs.setFont(font, fs)
                cs.setTextMatrix(Matrix.getRotateInstance(ang, tx, ty))
                cs.showText(text)
                cs.endText()
            }
        }
        val t = tmp(ctx)
        doc.save(t)
        return saveToDownloads(ctx, t, "${base}_watermark.pdf")
    } finally {
        doc.close()
    }
}

fun numberPagesPdf(ctx: Context, src: File, base: String, pos: Int): Uri {
    val doc = openDoc(src)
    try {
        val font = PDType1Font.HELVETICA
        val n = doc.numberOfPages
        for (i in 0 until n) {
            val page = doc.getPage(i)
            val box = page.cropBox
            val label = "${i + 1} / $n"
            val tw = font.getStringWidth(label) / 1000f * 10f
            val x = when (pos) {
                0 -> box.lowerLeftX + 28f
                2 -> box.upperRightX - 28f - tw
                else -> box.lowerLeftX + (box.width - tw) / 2f
            }
            PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                cs.beginText()
                cs.setFont(font, 10f)
                cs.setNonStrokingColor(0.15f, 0.15f, 0.15f)
                cs.newLineAtOffset(x, box.lowerLeftY + 20f)
                cs.showText(label)
                cs.endText()
            }
        }
        val t = tmp(ctx)
        doc.save(t)
        return saveToDownloads(ctx, t, "${base}_numbered.pdf")
    } finally {
        doc.close()
    }
}

fun extractText(ctx: Context, src: File, base: String): Uri? {
    val doc = openDoc(src)
    try {
        val text = PDFTextStripper().getText(doc)
        if (text.isBlank()) return null
        val t = tmp(ctx, "txt")
        t.writeText(text, Charsets.UTF_8)
        return saveToDownloads(ctx, t, "$base.txt", APP_DIR, "text/plain")
    } finally {
        doc.close()
    }
}

fun readInfo(src: File): List<String> {
    val doc = openDoc(src)
    try {
        val i = doc.documentInformation
        return listOf(i.title ?: "", i.author ?: "", i.subject ?: "", i.keywords ?: "")
    } finally {
        doc.close()
    }
}

fun writeInfo(ctx: Context, src: File, base: String, v: List<String>): Uri {
    val doc = openDoc(src)
    try {
        val i = doc.documentInformation
        i.title = v[0]
        i.author = v[1]
        i.subject = v[2]
        i.keywords = v[3]
        doc.documentInformation = i
        val t = tmp(ctx)
        doc.save(t)
        return saveToDownloads(ctx, t, "${base}_edited.pdf")
    } finally {
        doc.close()
    }
}

// ---------- الصور ----------

fun decodeImage(ctx: Context, uri: Uri, maxSide: Int): Bitmap {
    val src = ImageDecoder.createSource(ctx.contentResolver, uri)
    val bmp = ImageDecoder.decodeBitmap(src) { dec, info, _ ->
        dec.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE)
        val w = info.size.width
        val h = info.size.height
        val m = max(w, h)
        if (m > maxSide) {
            val f = maxSide.toFloat() / m
            dec.setTargetSize(max(1, (w * f).toInt()), max(1, (h * f).toInt()))
        }
    }
    val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
    val c = Canvas(flat)
    c.drawColor(android.graphics.Color.WHITE)
    c.drawBitmap(bmp, 0f, 0f, null)
    bmp.recycle()
    return flat
}

fun imagesToPdf(
    ctx: Context,
    uris: List<Uri>,
    outName: String,
    a4: Boolean,
    progress: (String) -> Unit
): Uri {
    val doc = PDDocument()
    try {
        for ((i, u) in uris.withIndex()) {
            progress("${i + 1} / ${uris.size}")
            val bmp = decodeImage(ctx, u, 2200)
            val baos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, 88, baos)
            val img = JPEGFactory.createFromStream(doc, ByteArrayInputStream(baos.toByteArray()))
            val w = bmp.width.toFloat()
            val h = bmp.height.toFloat()
            bmp.recycle()
            val pw: Float
            val ph: Float
            if (a4) {
                val land = w > h
                pw = if (land) 841.89f else 595.28f
                ph = if (land) 595.28f else 841.89f
            } else {
                val s = 842f / max(w, h)
                pw = w * s
                ph = h * s
            }
            val page = PDPage(PDRectangle(pw, ph))
            doc.addPage(page)
            val k = min(pw / w, ph / h)
            val dw = w * k
            val dh = h * k
            PDPageContentStream(doc, page).use { cs ->
                cs.drawImage(img, (pw - dw) / 2f, (ph - dh) / 2f, dw, dh)
            }
        }
        val t = tmp(ctx)
        doc.save(t)
        return saveToDownloads(ctx, t, outName)
    } finally {
        doc.close()
    }
}

fun pdfToImages(
    ctx: Context,
    src: File,
    base: String,
    pages: List<Int>,
    png: Boolean,
    scale: Float,
    progress: (String) -> Unit
) {
    withRenderer(src) { r ->
        for ((k, idx) in pages.withIndex()) {
            progress("${k + 1} / ${pages.size}")
            val bmp = renderBitmap(r, idx, scale = scale)
            val ext = if (png) "png" else "jpg"
            val t = tmp(ctx, ext)
            t.outputStream().use { o ->
                bmp.compress(
                    if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                    92,
                    o
                )
            }
            bmp.recycle()
            saveTo(
                ctx, t, "${base}_p${idx + 1}.$ext",
                if (png) "image/png" else "image/jpeg",
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                Environment.DIRECTORY_PICTURES + "/" + APP_DIR
            )
        }
    }
}

fun compressPdf(
    ctx: Context,
    src: File,
    base: String,
    maxSide: Int,
    quality: Int
): Triple<Uri?, Long, Long> {
    val doc = openDoc(src)
    try {
        val done = IdentityHashMap<COSBase, PDImageXObject?>()
        for (page in doc.pages) {
            val res = page.resources ?: continue
            for (name in res.xObjectNames.toList()) {
                try {
                    val xo = res.getXObject(name)
                    if (xo !is PDImageXObject) continue
                    val key: COSBase = xo.cosObject
                    if (done.containsKey(key)) {
                        val n = done[key]
                        if (n != null) res.put(name, n)
                        continue
                    }
                    done[key] = null
                    val cos = xo.cosObject
                    if (xo.suffix != "jpg" || xo.isStencil ||
                        cos.containsKey(COSName.MASK) || cos.containsKey(COSName.SMASK)
                    ) continue
                    val bmp = xo.image
                    val m = max(bmp.width, bmp.height)
                    val small = if (m > maxSide) {
                        val f = maxSide.toFloat() / m
                        Bitmap.createScaledBitmap(
                            bmp,
                            max(1, (bmp.width * f).toInt()),
                            max(1, (bmp.height * f).toInt()),
                            true
                        )
                    } else bmp
                    val baos = ByteArrayOutputStream()
                    small.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    val bytes = baos.toByteArray()
                    if (bytes.size < cos.getInt(COSName.LENGTH, 0)) {
                        val ni = JPEGFactory.createFromStream(doc, ByteArrayInputStream(bytes))
                        done[key] = ni
                        res.put(name, ni)
                    }
                } catch (e: Throwable) {
                }
            }
        }
        val t = tmp(ctx)
        doc.save(t)
        val before = src.length()
        val after = t.length()
        if (after >= before) {
            t.delete()
            return Triple(null, before, after)
        }
        return Triple(saveToDownloads(ctx, t, "${base}_compressed.pdf"), before, after)
    } finally {
        doc.close()
    }
}

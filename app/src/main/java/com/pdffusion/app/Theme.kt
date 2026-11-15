package com.pdffusion.app

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

val BgTop = Color(0xFF0B0E1A)
val BgBottom = Color(0xFF141A33)
val CardBg = Color(0xFF1A2038)
val CardHi = Color(0xFF252E50)
val Accent = Color(0xFF7C5CFF)
val Accent2 = Color(0xFF00C2E0)
val Danger = Color(0xFFFF5A6E)
val Ok = Color(0xFF3DDC97)
val TextHi = Color(0xFFEDEFFA)
val TextLo = Color(0xFF9AA3C7)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            secondary = Accent2,
            background = BgTop,
            surface = CardBg,
            onBackground = TextHi,
            onSurface = TextHi,
            surfaceVariant = CardHi,
            onSurfaceVariant = TextLo,
            outline = Color(0xFF3A4570),
            error = Danger
        ),
        content = content
    )
}

sealed interface Status {
    object Idle : Status
    object Busy : Status
    class Done(
        val msg: String,
        val uri: Uri? = null,
        val mime: String = "application/pdf"
    ) : Status
    class Fail(val msg: String) : Status
}

fun errText(e: Throwable): String = when (e) {
    is OutOfMemoryError -> "الذاكرة لا تكفي لهذه العملية"
    is InvalidPasswordException -> "كلمة المرور غير صحيحة أو الملف محمي"
    is SecurityException -> "الملف محمي بكلمة مرور"
    else -> e.message ?: e.javaClass.simpleName
}

class Op(private val scope: CoroutineScope) {
    var status by mutableStateOf<Status>(Status.Idle)
    var progress by mutableStateOf("")

    fun run(block: (Op) -> Status) {
        if (status is Status.Busy) return
        status = Status.Busy
        progress = ""
        scope.launch {
            status = try {
                withContext(Dispatchers.IO) { block(this@Op) }
            } catch (e: Throwable) {
                Status.Fail(errText(e))
            }
        }
    }
}

@Composable
fun rememberOp(): Op {
    val scope = rememberCoroutineScope()
    return remember { Op(scope) }
}

fun openUri(ctx: Context, uri: Uri, mime: String) {
    val i = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(i)
    } catch (e: Exception) {
        Toast.makeText(ctx, "لا يوجد تطبيق لفتح هذا الملف", Toast.LENGTH_SHORT).show()
    }
}

fun shareUri(ctx: Context, uri: Uri, mime: String) {
    val i = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(i, "مشاركة"))
}

fun fmtSize(b: Long): String = when {
    b >= 1_048_576L -> String.format(Locale.US, "%.1f MB", b / 1_048_576.0)
    b >= 1024L -> "${b / 1024} KB"
    else -> "$b B"
}

fun meta(p: Pdf): String = when {
    p.pages < 0 -> "🔒 محمي بكلمة مرور • " + fmtSize(p.size)
    p.pages == 0 -> "⚠️ تعذر قراءة الملف • " + fmtSize(p.size)
    else -> "${p.pages} صفحة • " + fmtSize(p.size)
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(CardBg)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

@Composable
fun SectionLabel(t: String) =
    Text(t, color = TextLo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)

@Composable
fun MiniBtn(label: String, enabled: Boolean = true, tint: Color = TextHi, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(CardHi)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) tint else TextLo.copy(alpha = 0.35f),
            fontSize = 15.sp
        )
    }
}

@Composable
fun Pills(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) Accent else CardHi)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    o,
                    color = if (sel) Color.White else TextLo,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    number: Boolean = false,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text
        ),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
fun RunButton(label: String, op: Op, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled && op.status !is Status.Busy,
        modifier = Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Accent,
            contentColor = Color.White,
            disabledContainerColor = CardHi,
            disabledContentColor = TextLo
        )
    ) {
        Text(label, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatusView(op: Op) {
    val ctx = LocalContext.current
    when (val s = op.status) {
        Status.Idle -> {}
        Status.Busy -> Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(26.dp), color = Accent, strokeWidth = 3.dp)
                Spacer(Modifier.width(12.dp))
                Text("جارٍ المعالجة… " + op.progress, color = TextHi)
            }
        }
        is Status.Done -> Panel {
            Text(s.msg, color = Ok, fontSize = 14.sp)
            val u = s.uri
            if (u != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { openUri(ctx, u, s.mime) },
                        modifier = Modifier.weight(1f)
                    ) { Text("فتح") }
                    OutlinedButton(
                        onClick = { shareUri(ctx, u, s.mime) },
                        modifier = Modifier.weight(1f)
                    ) { Text("مشاركة") }
                }
            }
        }
        is Status.Fail -> Panel {
            Text("⚠️ " + s.msg, color = Danger, fontSize = 14.sp)
        }
    }
}

@Composable
fun ThumbBox(b: Bitmap?, placeholder: String) {
    Box(
        Modifier
            .size(width = 46.dp, height = 62.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(CardHi),
        contentAlignment = Alignment.Center
    ) {
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(placeholder, fontSize = 20.sp)
        }
    }
}

@Composable
fun Thumb(file: File, locked: Boolean) {
    val bmp by produceState<Bitmap?>(null, file) {
        value = withContext(Dispatchers.IO) { if (locked) null else renderThumb(file) }
    }
    ThumbBox(bmp, if (locked) "🔒" else "📄")
}

@Composable
fun ImgThumb(uri: Uri) {
    val ctx = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            try { decodeImage(ctx, uri, 160) } catch (e: Throwable) { null }
        }
    }
    ThumbBox(bmp, "🖼️")
}

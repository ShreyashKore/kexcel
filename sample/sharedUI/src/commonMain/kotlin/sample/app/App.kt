package sample.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.openFilePicker
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import kotlinx.coroutines.launch

private val Background = Color(0xFF1E1E1E)
private val Accent = Color(0xFF1565C0)
private val TitleColor = Color(0xFF4FC3F7)
private val TextColor = Color(0xFFDDDDDD)
private val SubtitleColor = Color(0xFF9E9E9E)

private val TitleStyle = TextStyle(color = TitleColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
private val SubtitleStyle = TextStyle(color = SubtitleColor, fontSize = 13.sp)
private val MonoStyle = TextStyle(color = TextColor, fontSize = 13.sp, fontFamily = FontFamily.Monospace)

@Composable
fun App() {
    var tab by remember { mutableStateOf(0) }

    Column(Modifier.fillMaxSize().background(Background)) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            BasicText(
                "Kexcel sample",
                style = TextStyle(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(4.dp))
            BasicText("A Kotlin Multiplatform Excel (.xlsx) library.", style = SubtitleStyle)
        }

        Row(Modifier.fillMaxWidth()) {
            TabButton("Open a file", tab == 0) { tab = 0 }
            TabButton("API samples", tab == 1) { tab = 1 }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                0 -> OpenFileSection()
                else -> SamplesSection()
            }
        }
    }
}

/**
 * Section 1 — open a workbook from the device, decode it with Kexcel, show its
 * contents, and save a copy that has been decoded-encoded through the library.
 */
@Composable
private fun OpenFileSection() {
    val scope = rememberCoroutineScope()
    var opened by remember { mutableStateOf<OpenedWorkbook?>(null) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        Row {
            ActionButton(if (busy) "Working…" else "Open .xlsx…") {
                scope.launch {
                    busy = true
                    status = ""
                    try {
                        val picked = FileKit.openFilePicker()
                        if (picked == null) {
                            status = "Open cancelled."
                        } else {
                            opened = openWorkbook(picked)
                            status = "Opened '${picked.name}' (${picked.size()} bytes) and decoded it with Kexcel."
                        }
                    } catch (t: Throwable) {
                        opened = null
                        status = "Could not open file: ${t.message}"
                    } finally {
                        busy = false
                    }
                }
            }

            val current = opened
            if (current != null) {
                Spacer(Modifier.width(12.dp))
                ActionButton("Save decoded-encoded…") {
                    scope.launch {
                        busy = true
                        try {
                            // Re-encode through the library, then save — this is NOT a copy of
                            // the original file; the bytes are produced by Kexcel's writer.
                            val bytes = current.reEncode()
                            if (bytes == null) {
                                status = "Encoding returned no bytes."
                            } else {
                                val path = saveXlsxFile("decoded-encoded-${current.fileName}", bytes)
                                status = if (path != null) {
                                    "Re-encoded via Kexcel and saved to: $path"
                                } else {
                                    "Save cancelled."
                                }
                            }
                        } catch (t: Throwable) {
                            status = "Could not save file: ${t.message}"
                        } finally {
                            busy = false
                        }
                    }
                }
            }
        }

        if (status.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            BasicText(status, style = SubtitleStyle)
        }

        val current = opened
        if (current != null) {
            Spacer(Modifier.height(20.dp))
            current.sheets.forEach { sheet -> SheetBlock(sheet) }
        }
    }
}

/** Renders one sheet as plain text: header row and pipe-separated cell values. */
@Composable
private fun SheetBlock(sheet: SheetView) {
    Column(Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        BasicText(sheet.name, style = TitleStyle)
        Spacer(Modifier.height(2.dp))
        BasicText(
            "${sheet.totalRows} rows × ${sheet.totalCols} cols" +
                if (sheet.truncated) "  (showing a truncated preview)" else "",
            style = SubtitleStyle,
        )
        Spacer(Modifier.height(8.dp))

        // Cells can be wide, so let the grid scroll horizontally on its own.
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            if (sheet.rows.isEmpty()) {
                BasicText("(empty sheet)", style = MonoStyle)
            } else {
                sheet.rows.forEach { row ->
                    BasicText(
                        row.joinToString("  |  ").ifEmpty { " " },
                        style = MonoStyle,
                        softWrap = false,
                    )
                    Spacer(Modifier.height(3.dp))
                }
            }
        }
    }
}

/** Section 2 — runs the API usage samples from [runAllSamples]. */
@Composable
private fun SamplesSection() {
    val sections = remember { runAllSamples() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
    ) {
        BasicText(
            "Each block below runs real Kexcel code. See ExcelSamples.kt for the source.",
            style = SubtitleStyle,
        )
        Spacer(Modifier.height(18.dp))

        sections.forEach { section ->
            BasicText(section.title, style = TitleStyle)
            Spacer(Modifier.height(6.dp))
            section.lines.forEach { line ->
                BasicText(line, style = MonoStyle)
                Spacer(Modifier.height(2.dp))
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RowScope.TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .weight(1f)
            .background(if (selected) Accent else Color(0xFF2A2A2A))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            label,
            style = TextStyle(
                color = if (selected) Color.White else SubtitleColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Accent)
            .then( Modifier.clickable(onClick = onClick))
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        BasicText(
            text,
            style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium),
        )
    }
}

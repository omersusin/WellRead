package com.omersusin.wellread.utils

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jsoup.Jsoup
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val MAX_BYTES  = 50L * 1024 * 1024  // 50 MB copy cap
private const val MAX_CHARS  = 500_000             // ~100 K words
private const val TIMEOUT_MS = 60_000L

@Singleton
class FileParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── Public API ────────────────────────────────────────────────────────────

    suspend fun parsePdf(uri: Uri):      ParseResult = parseViaCache(uri, "pdf",  ::parsePdfFile)
    suspend fun parseTxt(uri: Uri):      ParseResult = parseViaCache(uri, "txt",  ::parseTxtFile)
    suspend fun parseEpub(uri: Uri):     ParseResult = parseViaCache(uri, "epub", ::parseEpubFile)
    suspend fun parseDocx(uri: Uri):     ParseResult = parseViaCache(uri, "docx", ::parseDocxFile)
    suspend fun parseHtml(uri: Uri):     ParseResult = parseViaCache(uri, "html", ::parseHtmlFile)
    suspend fun parseMarkdown(uri: Uri): ParseResult = parseViaCache(uri, "md",   ::parseMdFile)
    suspend fun parseRtf(uri: Uri):      ParseResult = parseViaCache(uri, "rtf",  ::parseRtfFile)

    // ── Pipeline: copy URI -> temp file, parse, delete ────────────────────────

    private suspend fun parseViaCache(
        uri: Uri,
        ext: String,
        parse: (File) -> ParseResult
    ): ParseResult = withContext(Dispatchers.IO) {
        var tmp: File? = null
        try {
            tmp = withTimeout(30_000L) { copyToCache(uri, ext) }
                ?: return@withContext ParseResult.Error(
                    "Could not read the selected file.\n\nMake sure the file is accessible and try again."
                )
            withTimeout(TIMEOUT_MS) { parse(tmp) }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("Loading timed out. The file may be too large.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            ParseResult.Error("Not enough memory. Try a smaller file.")
        } catch (e: Exception) {
            ParseResult.Error("Could not import: ${e.message ?: "Unknown error"}")
        } finally {
            try { tmp?.delete() } catch (_: Exception) {}
        }
    }

    private fun copyToCache(uri: Uri, ext: String): File? {
        val dest = File(context.cacheDir, "wr_${System.currentTimeMillis()}.$ext")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().buffered(65536).use { output ->
                    val buf = ByteArray(65536)
                    var total = 0L
                    var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        total += n
                        if (total >= MAX_BYTES) break
                    }
                }
            }
            if (dest.exists() && dest.length() > 0) dest else { dest.delete(); null }
        } catch (_: Exception) {
            dest.delete()
            null
        }
    }

    // ── PDF ───────────────────────────────────────────────────────────────────

    private fun parsePdfFile(file: File): ParseResult {
        val text = extractTextFromPdf(file.readBytes())
        if (text.isBlank() || text.length < 20)
            return ParseResult.Error(
                "Could not extract text from this PDF.\n" +
                "The file may be scanned/image-based or encrypted.\n\n" +
                "Try a text-based PDF or convert to TXT/EPUB."
            )
        val out = text.take(MAX_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    private fun extractTextFromPdf(bytes: ByteArray): String {
        val sb = StringBuilder()
        try {
            val content   = String(bytes, Charsets.ISO_8859_1)
            val tjPat     = Regex("""\(([^)\\]*(?:\\.[^)\\]*)*)\)\s*Tj""")
            val tjaPat    = Regex("""\[([^\]]*)\]\s*TJ""")
            val strPat    = Regex("""\(([^)\\]*(?:\\.[^)\\]*)*)\)""")
            val btEtPat   = Regex("""BT(.*?)ET""", RegexOption.DOT_MATCHES_ALL)
            var found     = false
            var blockCount = 0

            for (block in btEtPat.findAll(content)) {
                if (++blockCount > 3000 || sb.length > MAX_CHARS) break
                val bt = block.groupValues[1]
                for (m in tjPat.findAll(bt)) {
                    val t = decodePdfString(m.groupValues[1])
                    if (t.isNotBlank()) { sb.append(t).append(' '); found = true }
                }
                for (m in tjaPat.findAll(bt)) {
                    for (s in strPat.findAll(m.groupValues[1])) {
                        val t = decodePdfString(s.groupValues[1])
                        if (t.isNotBlank()) { sb.append(t); found = true }
                    }
                    sb.append(' ')
                }
            }

            if (!found || sb.length < 50) {
                sb.clear()
                var cnt = 0
                for (m in strPat.findAll(content)) {
                    if (++cnt > 8000 || sb.length > MAX_CHARS) break
                    val t = decodePdfString(m.groupValues[1])
                    if (t.length > 1 && t.all { c -> c.code in 32..126 || c == '\n' })
                        sb.append(t).append(' ')
                }
            }
        } catch (_: Exception) {}
        return sb.toString()
            .replace(Regex("[^\u0020-\u007E\n\r\t]"), " ")
            .replace(Regex("\\s{3,}"), "\n")
            .trim()
    }

    private fun decodePdfString(raw: String): String = raw
        .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
        .replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\")
        .filter { c -> c.code in 32..126 || c == '\n' || c == '\r' }

    // ── TXT ───────────────────────────────────────────────────────────────────

    private fun parseTxtFile(file: File): ParseResult {
        if (file.length() == 0L) return ParseResult.Error("File is empty")
        val text = try { file.readText(Charsets.UTF_8) }
                   catch (_: Exception) { file.readText(Charsets.ISO_8859_1) }
        if (text.isBlank()) return ParseResult.Error("File is empty")
        val out = text.take(MAX_CHARS)
        return ParseResult.Success(out.trim(), TextProcessor.countWords(out))
    }

    // ── EPUB ──────────────────────────────────────────────────────────────────

    private fun parseEpubFile(file: File): ParseResult {
        val sb = StringBuilder()
        ZipInputStream(file.inputStream().buffered(65536)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory &&
                    (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm"))
                ) {
                    val html = zip.readBytes().toString(Charsets.UTF_8)
                    val text = runCatching { Jsoup.parse(html).body().text() }.getOrElse { "" }
                    if (text.length > 30) sb.append(text).append("\n\n")
                }
                zip.closeEntry()
                entry = zip.nextEntry
                if (sb.length > MAX_CHARS) break
            }
        }
        val text = sb.toString().trim()
        if (text.isBlank() || text.length < 50)
            return ParseResult.Error(
                "Could not extract text from EPUB.\n" +
                "The file may be DRM-protected or in an unsupported format."
            )
        val out = text.take(MAX_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    // ── DOCX ──────────────────────────────────────────────────────────────────

    private fun parseDocxFile(file: File): ParseResult {
        var xmlContent: String? = null
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xmlContent = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val xml = xmlContent ?: return ParseResult.Error("Not a valid DOCX file.")
        val sb  = StringBuilder()
        val wt  = Regex("""<w:t[^>]*>([^<]*)</w:t>""")
        for (para in xml.split("</w:p>")) {
            val words = wt.findAll(para).map { it.groupValues[1] }.joinToString("")
            if (words.isNotBlank()) sb.append(words.trim()).append("\n")
            if (sb.length > MAX_CHARS) break
        }
        val text = sb.toString().replace(Regex("\n{3,}"), "\n\n").trim()
        if (text.isBlank()) return ParseResult.Error("Could not extract text from this DOCX file.")
        val out = text.take(MAX_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    // ── HTML ──────────────────────────────────────────────────────────────────

    private fun parseHtmlFile(file: File): ParseResult {
        val text = Jsoup.parse(file.readText(Charsets.UTF_8)).body().text().trim()
        if (text.isBlank()) return ParseResult.Error("No text found in HTML file.")
        val out = text.take(MAX_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    // ── Markdown ──────────────────────────────────────────────────────────────

    private fun parseMdFile(file: File): ParseResult {
        val raw = file.readText(Charsets.UTF_8)
        val text = raw
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("`{1,3}[^`]*`{1,3}"), "")
            .replace(Regex("^>+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
            .trim()
        if (text.isBlank()) return ParseResult.Error("No text found in Markdown file.")
        val out = text.take(MAX_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    // ── RTF ───────────────────────────────────────────────────────────────────

    private fun parseRtfFile(file: File): ParseResult {
        val raw = file.readText(Charsets.ISO_8859_1)
        // Strip RTF control words and groups, keep readable ASCII
        val sb = StringBuilder()
        var i = 0
        var inControl = false
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '\\' -> {
                    inControl = true
                    // skip the control word or escaped char
                    i++
                    if (i < raw.length) {
                        if (raw[i] == '\'' ) { i += 2 } // hex escape \'xx
                        else if (raw[i] == '\n' || raw[i] == '\r') { sb.append('\n') }
                        else { while (i < raw.length && raw[i].isLetter()) i++ }
                    }
                    inControl = false
                    continue
                }
                c == '{' || c == '}' -> { /* skip group delimiters */ }
                c.code in 32..126 -> sb.append(c)
                c == '\n' || c == '\r' -> sb.append(' ')
            }
            i++
        }
        val text = sb.toString()
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (text.isBlank() || text.length < 20)
            return ParseResult.Error("Could not extract text from RTF file.")
        val out = text.take(MAX_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }
}

sealed class ParseResult {
    data class Success(val text: String, val wordCount: Int) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

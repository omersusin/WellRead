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

private const val MAX_COPY_BYTES = 50 * 1024 * 1024   // 50 MB copy cap
private const val MAX_TEXT_CHARS = 500_000             // ~100K words
private const val PARSE_TIMEOUT  = 60_000L             // 60 s

@Singleton
class FileParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── Public API ────────────────────────────────────────────────────────────
    // All parsers use the copy-to-cache pipeline to avoid content URI issues

    suspend fun parsePdf(uri: Uri): ParseResult  = parseViaCache(uri, "pdf",  ::parsePdfFile)
    suspend fun parseTxt(uri: Uri): ParseResult  = parseViaCache(uri, "txt",  ::parseTxtFile)
    suspend fun parseEpub(uri: Uri): ParseResult = parseViaCache(uri, "epub", ::parseEpubFile)
    suspend fun parseDocx(uri: Uri): ParseResult = parseViaCache(uri, "docx", ::parseDocxFile)
    suspend fun parseHtml(uri: Uri): ParseResult = parseViaCache(uri, "html", ::parseHtmlFile)
    suspend fun parseMarkdown(uri: Uri): ParseResult = parseViaCache(uri, "md", ::parseMdFile)

    // ── Core pipeline ─────────────────────────────────────────────────────────
    // 1. Copy content URI to app cacheDir  (30 s timeout)
    // 2. Parse local file                  (60 s timeout)
    // 3. Delete temp file
    //
    // Copying first solves all content URI permission / FUSE blocking issues.
    // Worker threads can freely read files in cacheDir without any URI grants.

    private suspend fun parseViaCache(
        uri: Uri,
        ext: String,
        parse: (File) -> ParseResult
    ): ParseResult = withContext(Dispatchers.IO) {
        var tempFile: File? = null
        try {
            tempFile = withTimeout(30_000L) { copyToCache(uri, ext) }
                ?: return@withContext ParseResult.Error(
                    "Could not read the selected file.\n\n" +
                    "Make sure the file is accessible and try again."
                )

            withTimeout(PARSE_TIMEOUT) { parse(tempFile) }

        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("Loading timed out. The file may be too large.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: OutOfMemoryError) {
            ParseResult.Error("Not enough memory. Try a smaller file.")
        } catch (e: Exception) {
            ParseResult.Error("Could not import: ${e.message ?: "Unknown error"}")
        } finally {
            try { tempFile?.delete() } catch (_: Exception) {}
        }
    }

    // Copies content URI stream into a temp file in app's private cache dir.
    // Uses a 64 KB rolling buffer — never pre-allocates the full file size.
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
                        if (total >= MAX_COPY_BYTES) break
                    }
                }
            }
            if (dest.exists() && dest.length() > 0) dest else { dest.delete(); null }
        } catch (_: Exception) {
            dest.delete()
            null
        }
    }

    // ── Local file parsers ────────────────────────────────────────────────────

    private fun parsePdfFile(file: File): ParseResult {
        if (file.length() > 8 * 1024 * 1024)
            return ParseResult.Error(
                "PDF too large (${file.length() / 1024 / 1024} MB). Max supported: 8 MB.\n\n" +
                "Try converting to TXT or EPUB."
            )
        val text = extractTextFromPdf(file.readBytes())
        if (text.isBlank() || text.length < 20)
            return ParseResult.Error(
                "Could not extract text from this PDF.\n" +
                "The file may be scanned, image-based or encrypted.\n\n" +
                "Try a text-based PDF or TXT file."
            )
        val out = text.take(MAX_TEXT_CHARS)
        return ParseResult.Success(out.trim(), TextProcessor.countWords(out))
    }

    private fun parseTxtFile(file: File): ParseResult {
        if (file.length() == 0L) return ParseResult.Error("File is empty")
        val text = try { file.readText(Charsets.UTF_8) }
                   catch (_: Exception) { file.readText(Charsets.ISO_8859_1) }
        if (text.isBlank()) return ParseResult.Error("File is empty")
        val out = text.take(MAX_TEXT_CHARS)
        return ParseResult.Success(out.trim(), TextProcessor.countWords(out))
    }

    private fun parseEpubFile(file: File): ParseResult {
        val sb = StringBuilder()
        var totalBytes = 0L
        ZipInputStream(file.inputStream().buffered(65536)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name.lowercase()
                if (!entry.isDirectory &&
                    (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm"))
                ) {
                    val html  = zip.readBytes().toString(Charsets.UTF_8)
                    val text  = runCatching { Jsoup.parse(html).body().text() }.getOrElse { "" }
                    totalBytes += html.length
                    if (text.length > 30) sb.append(text).append("\n\n")
                }
                zip.closeEntry()
                entry = zip.nextEntry
                if (sb.length > MAX_TEXT_CHARS || totalBytes > 30L * 1024 * 1024) break
            }
        }
        val text = sb.toString().trim()
        if (text.isBlank() || text.length < 50)
            return ParseResult.Error(
                "Could not extract text from EPUB.\n" +
                "The file may be DRM-protected or in an unsupported format."
            )
        val out = text.take(MAX_TEXT_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    private fun parseDocxFile(file: File): ParseResult {
        var xmlContent: String? = null
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xmlContent = zip.readBytes().toString(Charsets.UTF_8); break
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
            if (sb.length > MAX_TEXT_CHARS) break
        }
        val text = sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
        if (text.isBlank() || text.length < 10)
            return ParseResult.Error("Could not extract text from this DOCX file.")
        val out = text.take(MAX_TEXT_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    private fun parseHtmlFile(file: File): ParseResult {
        val text = Jsoup.parse(file.readText(Charsets.UTF_8)).body().text().trim()
        if (text.isBlank()) return ParseResult.Error("No text found in HTML file")
        val out = text.take(MAX_TEXT_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

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
        if (text.isBlank()) return ParseResult.Error("No text found in Markdown file")
        val out = text.take(MAX_TEXT_CHARS)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    // ── PDF extraction ────────────────────────────────────────────────────────

    private fun extractTextFromPdf(bytes: ByteArray): String {
        val sb = StringBuilder(minOf(bytes.size / 4, 512 * 1024))
        try {
            val content = String(bytes, Charsets.ISO_8859_1)
            val tjPat   = Regex("""\(([^)\\]{0,300}(?:\\.[^)\\]{0,300})*)\)\s*Tj""")
            val tjaPat  = Regex("""\[([^\]]{0,2000})\]\s*TJ""")
            val strPat  = Regex("""\(([^)\\]{0,400})\)""")
            val btEtPat = Regex("""BT(.{0,4000}?)ET""", RegexOption.DOT_MATCHES_ALL)

            var found = false
            var blockCount = 0
            for (block in btEtPat.findAll(content)) {
                if (++blockCount > 3000 || sb.length > MAX_TEXT_CHARS) break
                val blockText = block.groupValues[1]
                for (m in tjPat.findAll(blockText)) {
                    val t = decodePdfString(m.groupValues[1])
                    if (t.isNotBlank()) { sb.append(t).append(' '); found = true }
                }
                for (m in tjaPat.findAll(blockText)) {
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
                    if (++cnt > 8000 || sb.length > MAX_TEXT_CHARS) break
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
}

sealed class ParseResult {
    data class Success(val text: String, val wordCount: Int) : ParseResult()
    data class Error(val message: String) : ParseResult()
}


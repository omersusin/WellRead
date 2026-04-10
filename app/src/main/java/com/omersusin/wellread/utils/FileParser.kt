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
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── PDF ────────────────────────────────────────────────────────
    suspend fun parsePdf(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(30_000L) {
                val bytes = context.contentResolver.openInputStream(uri)
                    ?.use { it.readBytes() }
                    ?: return@withTimeout ParseResult.Error("Cannot open file")

                val text = extractTextFromPdf(bytes)
                if (text.isBlank() || text.length < 20) {
                    return@withTimeout ParseResult.Error(
                        "Could not extract text from this PDF.\n" +
                        "The file may be scanned/image-based or encrypted.\n\n" +
                        "Try a text-based PDF or TXT file."
                    )
                }
                ParseResult.Success(text.trim(), TextProcessor.countWords(text))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("PDF loading timed out. The file may be too large.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ParseResult.Error("PDF error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun extractTextFromPdf(bytes: ByteArray): String {
        val sb = StringBuilder()
        try {
            val content = String(bytes, Charsets.ISO_8859_1)
            val tjPattern = Regex("""\(([^)\\]*(?:\\.[^)\\]*)*)\)\s*Tj""")
            val tjArrayPattern = Regex("""\[([^\]]*)\]\s*TJ""")
            val stringPattern = Regex("""\(([^)\\]*(?:\\.[^)\\]*)*)\)""")
            val btEtPattern = Regex("""BT(.*?)ET""", RegexOption.DOT_MATCHES_ALL)

            var foundInBlocks = false
            btEtPattern.findAll(content).forEach { block ->
                val blockText = block.groupValues[1]
                tjPattern.findAll(blockText).forEach { match ->
                    val text = decodePdfString(match.groupValues[1])
                    if (text.isNotBlank()) { sb.append(text).append(" "); foundInBlocks = true }
                }
                tjArrayPattern.findAll(blockText).forEach { match ->
                    stringPattern.findAll(match.groupValues[1]).forEach { strMatch ->
                        val text = decodePdfString(strMatch.groupValues[1])
                        if (text.isNotBlank()) { sb.append(text); foundInBlocks = true }
                    }
                    sb.append(" ")
                }
            }

            if (!foundInBlocks || sb.length < 50) {
                sb.clear()
                stringPattern.findAll(content).forEach { match ->
                    val text = decodePdfString(match.groupValues[1])
                    if (text.length > 1 && text.all { c ->
                            c.code in 32..126 || c == '\n' || c == '\r' || c == '\t'
                        }) {
                        sb.append(text).append(" ")
                    }
                }
            }

            if (sb.length < 50) {
                sb.clear()
                try {
                    val utf16Content = String(bytes, Charsets.UTF_16BE)
                    val readable = utf16Content.filter { c -> c.code in 32..126 || c == '\n' }
                    if (readable.length > 100) sb.append(readable)
                } catch (_: Exception) {}
            }

        } catch (_: Exception) {
            try {
                val raw = String(bytes, Charsets.ISO_8859_1)
                val words = StringBuilder()
                var buf = StringBuilder()
                for (c in raw) {
                    if (c.code in 32..126) buf.append(c)
                    else {
                        if (buf.length > 2) words.append(buf).append(" ")
                        buf.clear()
                    }
                }
                sb.append(words)
            } catch (_: Exception) {}
        }

        return sb.toString()
            .replace(Regex("[^\u0020-\u007E\n\r\t]"), " ")
            .replace(Regex("\\s{3,}"), "\n")
            .replace(Regex("(?<=[.!?])\\s*\n\\s*"), "\n")
            .trim()
    }

    private fun decodePdfString(raw: String): String = raw
        .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
        .replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\")
        .filter { c -> c.code in 32..126 || c == '\n' || c == '\r' }

    // ── TXT ────────────────────────────────────────────────────────
    suspend fun parseTxt(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ParseResult.Error("Cannot open file")
            val text = try {
                stream.use { it.bufferedReader(Charsets.UTF_8).readText() }
            } catch (_: Exception) {
                context.contentResolver.openInputStream(uri)
                    ?.use { it.bufferedReader(Charsets.ISO_8859_1).readText() }
                    ?: return@withContext ParseResult.Error("Cannot read file")
            }
            if (text.isBlank()) return@withContext ParseResult.Error("File is empty")
            ParseResult.Success(text.trim(), TextProcessor.countWords(text))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── EPUB ───────────────────────────────────────────────────────
    // DÜZELTME: Eski parser byte-by-byte pos++ döngüsü kullanıyordu
    // (5MB EPUB = 5 milyon iterasyon = freeze). Artık ZipInputStream kullanıyoruz.
    suspend fun parseEpub(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(60_000L) {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")

                val sb = StringBuilder()

                ZipInputStream(inputStream.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name.lowercase()
                        if (!entry.isDirectory &&
                            (name.endsWith(".html") ||
                             name.endsWith(".xhtml") ||
                             name.endsWith(".htm"))
                        ) {
                            val htmlBytes = zip.readBytes()
                            val html = htmlBytes.toString(Charsets.UTF_8)
                            val text = try {
                                Jsoup.parse(html).body().text()
                            } catch (_: Exception) { "" }
                            if (text.length > 30) {
                                sb.append(text).append("\n\n")
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }

                val text = sb.toString().trim()
                if (text.isBlank() || text.length < 50) {
                    return@withTimeout ParseResult.Error(
                        "Could not extract text from EPUB.\n" +
                        "The file may be DRM-protected or in an unsupported format."
                    )
                }
                ParseResult.Success(text, TextProcessor.countWords(text))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("EPUB loading timed out. The file may be too large.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ParseResult.Error("EPUB error: ${e.message ?: "Unknown error"}")
        }
    }
}

    // ── DOCX ──────────────────────────────────────────────────────────────────
    suspend fun parseDocx(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        var tempFile: java.io.File? = null
        try {
            tempFile = copyToCache(uri, "docx")
                ?: return@withContext ParseResult.Error("Could not read the selected file.")
            withTimeout(60_000L) { parseDocxFile(tempFile) }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("Loading timed out.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ParseResult.Error("Could not import DOCX: ${e.message}") }
        finally { try { tempFile?.delete() } catch (_: Exception) {} }
    }

    // ── HTML ──────────────────────────────────────────────────────────────────
    suspend fun parseHtml(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        var tempFile: java.io.File? = null
        try {
            tempFile = copyToCache(uri, "html")
                ?: return@withContext ParseResult.Error("Could not read the selected file.")
            withTimeout(60_000L) { parseHtmlFile(tempFile) }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("Loading timed out.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ParseResult.Error("Could not import HTML: ${e.message}") }
        finally { try { tempFile?.delete() } catch (_: Exception) {} }
    }

    // ── Markdown ──────────────────────────────────────────────────────────────
    suspend fun parseMarkdown(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        var tempFile: java.io.File? = null
        try {
            tempFile = copyToCache(uri, "md")
                ?: return@withContext ParseResult.Error("Could not read the selected file.")
            withTimeout(60_000L) { parseMdFile(tempFile) }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("Loading timed out.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ParseResult.Error("Could not import Markdown: ${e.message}") }
        finally { try { tempFile?.delete() } catch (_: Exception) {} }
    }

    // ── RTF ───────────────────────────────────────────────────────────────────
    suspend fun parseRtf(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        var tempFile: java.io.File? = null
        try {
            tempFile = copyToCache(uri, "rtf")
                ?: return@withContext ParseResult.Error("Could not read the selected file.")
            withTimeout(60_000L) { parseRtfFile(tempFile) }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("Loading timed out.")
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { ParseResult.Error("Could not import RTF: ${e.message}") }
        finally { try { tempFile?.delete() } catch (_: Exception) {} }
    }

    // ── copyToCache helper ────────────────────────────────────────────────────
    private fun copyToCache(uri: Uri, ext: String): java.io.File? {
        val dest = java.io.File(context.cacheDir, "wr_${System.currentTimeMillis()}.$ext")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().buffered(65536).use { output ->
                    val buf = ByteArray(65536)
                    var total = 0L; var n: Int
                    while (input.read(buf).also { n = it } != -1) {
                        output.write(buf, 0, n)
                        total += n
                        if (total >= 50L * 1024 * 1024) break
                    }
                }
            }
            if (dest.exists() && dest.length() > 0) dest else { dest.delete(); null }
        } catch (_: Exception) { dest.delete(); null }
    }

    // ── File parsers ──────────────────────────────────────────────────────────
    private fun parseDocxFile(file: java.io.File): ParseResult {
        var xmlContent: String? = null
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    xmlContent = zip.readBytes().toString(Charsets.UTF_8); break
                }
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
        val xml = xmlContent ?: return ParseResult.Error("Not a valid DOCX file.")
        val sb  = StringBuilder()
        val wt  = Regex("""<w:t[^>]*>([^<]*)</w:t>""")
        for (para in xml.split("</w:p>")) {
            val words = wt.findAll(para).map { it.groupValues[1] }.joinToString("")
            if (words.isNotBlank()) sb.append(words.trim()).append("\n")
            if (sb.length > 500_000) break
        }
        val text = sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
        if (text.isBlank()) return ParseResult.Error("Could not extract text from DOCX.")
        return ParseResult.Success(text.take(500_000), TextProcessor.countWords(text))
    }

    private fun parseHtmlFile(file: java.io.File): ParseResult {
        val text = Jsoup.parse(file.readText(Charsets.UTF_8)).body().text().trim()
        if (text.isBlank()) return ParseResult.Error("No text found in HTML file.")
        val out = text.take(500_000)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    private fun parseMdFile(file: java.io.File): ParseResult {
        val text = file.readText(Charsets.UTF_8)
            .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("\\*([^*]+)\\*"), "$1")
            .replace(Regex("`{1,3}[^`]*`{1,3}"), "")
            .replace(Regex("^>+\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
            .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
            .trim()
        if (text.isBlank()) return ParseResult.Error("No text found in Markdown file.")
        val out = text.take(500_000)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }

    private fun parseRtfFile(file: java.io.File): ParseResult {
        val raw = file.readText(Charsets.ISO_8859_1)
        val text = raw
            .replace(Regex("\\{[^{}]*}"), " ")
            .replace(Regex("\\\\[a-z*]+\\d* ?"), " ")
            .replace(Regex("\\\\[^a-z]"), "")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (text.isBlank() || text.length < 20)
            return ParseResult.Error("Could not extract text from RTF file.")
        val out = text.take(500_000)
        return ParseResult.Success(out, TextProcessor.countWords(out))
    }


sealed class ParseResult {
    data class Success(val text: String, val wordCount: Int) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

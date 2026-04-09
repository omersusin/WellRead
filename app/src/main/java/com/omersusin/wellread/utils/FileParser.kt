package com.omersusin.wellread.utils

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

// Hard limits to prevent OOM / infinite loops
private const val MAX_PDF_BYTES  = 8  * 1024 * 1024   // 8 MB
private const val MAX_TXT_BYTES  = 4  * 1024 * 1024   // 4 MB
private const val MAX_EPUB_BYTES = 30 * 1024 * 1024   // 30 MB (decompressed progressively)
private const val MAX_TEXT_CHARS = 500_000             // ~100K words
private const val PARSE_TIMEOUT  = 45_000L             // 45 s

@Singleton
class FileParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── PDF ───────────────────────────────────────────────────────────────────
    suspend fun parsePdf(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(PARSE_TIMEOUT) {
                val size = getFileSize(uri)
                if (size > MAX_PDF_BYTES) {
                    return@withTimeout ParseResult.Error(
                        "PDF is too large (${size / 1024 / 1024} MB).\n" +
                        "Maximum supported size is ${MAX_PDF_BYTES / 1024 / 1024} MB.\n\n" +
                        "Try converting to TXT or EPUB."
                    )
                }
                val bytes = readStream(uri, MAX_PDF_BYTES)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")
                ensureActive()
                val text = extractTextFromPdf(bytes)
                if (text.isBlank() || text.length < 20)
                    return@withTimeout ParseResult.Error(
                        "Could not extract text from this PDF.\n" +
                        "The file may be scanned/image-based or encrypted.\n\n" +
                        "Try a text-based PDF or TXT file."
                    )
                val out = text.take(MAX_TEXT_CHARS)
                ParseResult.Success(out.trim(), TextProcessor.countWords(out))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("PDF loading timed out. Try a smaller or simpler PDF.")
        } catch (e: CancellationException) { throw e
        } catch (e: OutOfMemoryError) {
            ParseResult.Error("Not enough memory to load this PDF.")
        } catch (e: Exception) {
            ParseResult.Error("PDF error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun extractTextFromPdf(bytes: ByteArray): String {
        val sb = StringBuilder(minOf(bytes.size / 4, 1024 * 1024))
        try {
            val content = String(bytes, Charsets.ISO_8859_1)
            // All patterns have bounded quantifiers to prevent catastrophic backtracking
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

    // ── TXT ───────────────────────────────────────────────────────────────────
    suspend fun parseTxt(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(PARSE_TIMEOUT) {
                val bytes = readStream(uri, MAX_TXT_BYTES)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")
                val text = try { String(bytes, Charsets.UTF_8) }
                           catch (_: Exception) { String(bytes, Charsets.ISO_8859_1) }
                if (text.isBlank()) return@withTimeout ParseResult.Error("File is empty")
                val out = text.take(MAX_TEXT_CHARS)
                ParseResult.Success(out.trim(), TextProcessor.countWords(out))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("File loading timed out.")
        } catch (e: CancellationException) { throw e
        } catch (e: OutOfMemoryError) {
            ParseResult.Error("File too large to load.")
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── EPUB ──────────────────────────────────────────────────────────────────
    suspend fun parseEpub(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(PARSE_TIMEOUT) {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")
                val sb = StringBuilder()
                var totalBytes = 0
                ZipInputStream(inputStream.buffered(65536)).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        ensureActive()
                        val name = entry.name.lowercase()
                        if (!entry.isDirectory &&
                            (name.endsWith(".html") || name.endsWith(".xhtml") || name.endsWith(".htm"))
                        ) {
                            val entryBytes = zip.readBytes()
                            totalBytes += entryBytes.size
                            val text = runCatching {
                                Jsoup.parse(entryBytes.toString(Charsets.UTF_8)).body().text()
                            }.getOrElse { "" }
                            if (text.length > 30) sb.append(text).append("\n\n")
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                        if (sb.length > MAX_TEXT_CHARS || totalBytes > MAX_EPUB_BYTES) break
                    }
                }
                val text = sb.toString().trim()
                if (text.isBlank() || text.length < 50)
                    return@withTimeout ParseResult.Error(
                        "Could not extract text from EPUB.\n" +
                        "The file may be DRM-protected or in an unsupported format."
                    )
                val out = text.take(MAX_TEXT_CHARS)
                ParseResult.Success(out, TextProcessor.countWords(out))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("EPUB loading timed out.")
        } catch (e: CancellationException) { throw e
        } catch (e: OutOfMemoryError) {
            ParseResult.Error("EPUB too large to load.")
        } catch (e: Exception) {
            ParseResult.Error("EPUB error: ${e.message ?: "Unknown error"}")
        }
    }

    // ── DOCX ──────────────────────────────────────────────────────────────────
    suspend fun parseDocx(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(PARSE_TIMEOUT) {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")
                var xmlContent: String? = null
                ZipInputStream(inputStream.buffered()).use { zip ->
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
                val xml = xmlContent
                    ?: return@withTimeout ParseResult.Error("Not a valid DOCX file.")
                ensureActive()
                val sb = StringBuilder()
                val wt = Regex("""<w:t[^>]*>([^<]*)</w:t>""")
                for (para in xml.split("</w:p>")) {
                    val words = wt.findAll(para).map { it.groupValues[1] }.joinToString("")
                    if (words.isNotBlank()) sb.append(words.trim()).append("\n")
                    if (sb.length > MAX_TEXT_CHARS) break
                }
                val text = sb.toString().replace(Regex("\\n{3,}"), "\n\n").trim()
                if (text.isBlank() || text.length < 10)
                    return@withTimeout ParseResult.Error("Could not extract text from this DOCX file.")
                val out = text.take(MAX_TEXT_CHARS)
                ParseResult.Success(out, TextProcessor.countWords(out))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("DOCX loading timed out.")
        } catch (e: CancellationException) { throw e
        } catch (e: OutOfMemoryError) {
            ParseResult.Error("DOCX too large to load.")
        } catch (e: Exception) {
            ParseResult.Error("DOCX error: ${e.message ?: "Unknown error"}")
        }
    }

    // ── HTML ──────────────────────────────────────────────────────────────────
    suspend fun parseHtml(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(PARSE_TIMEOUT) {
                val bytes = readStream(uri, MAX_TXT_BYTES)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")
                val text = Jsoup.parse(String(bytes, Charsets.UTF_8)).body().text().trim()
                if (text.isBlank()) return@withTimeout ParseResult.Error("No text found in HTML")
                val out = text.take(MAX_TEXT_CHARS)
                ParseResult.Success(out, TextProcessor.countWords(out))
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            ParseResult.Error("HTML error: ${e.message ?: "Unknown error"}")
        }
    }

    // ── Markdown ──────────────────────────────────────────────────────────────
    suspend fun parseMarkdown(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(PARSE_TIMEOUT) {
                val bytes = readStream(uri, MAX_TXT_BYTES)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")
                val raw = String(bytes, Charsets.UTF_8)
                val text = raw
                    .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
                    .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
                    .replace(Regex("\\*([^*]+)\\*"), "$1")
                    .replace(Regex("`{1,3}[^`]*`{1,3}"), "")
                    .replace(Regex("^>+\\s*", RegexOption.MULTILINE), "")
                    .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
                    .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
                    .trim()
                if (text.isBlank()) return@withTimeout ParseResult.Error("No text found")
                val out = text.take(MAX_TEXT_CHARS)
                ParseResult.Success(out, TextProcessor.countWords(out))
            }
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            ParseResult.Error("Markdown error: ${e.message ?: "Unknown error"}")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Stream-read up to [limit] bytes using a small buffer. Never pre-allocates [limit]. */
    private fun readStream(uri: Uri, limit: Int): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val out = ByteArrayOutputStream(minOf(limit, 256 * 1024))
            val buf = ByteArray(65536)
            var total = 0
            var read: Int
            while (stream.read(buf).also { read = it } != -1) {
                out.write(buf, 0, read)
                total += read
                if (total >= limit) break
            }
            out.toByteArray()
        }
    }

    /** Returns file size in bytes, -1 if unknown. */
    private fun getFileSize(uri: Uri): Long = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1L
    } catch (_: Exception) { -1L }
}

sealed class ParseResult {
    data class Success(val text: String, val wordCount: Int) : ParseResult()
    data class Error(val message: String) : ParseResult()
}


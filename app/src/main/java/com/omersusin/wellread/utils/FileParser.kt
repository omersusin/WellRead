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

private const val MAX_FILE_BYTES = 50 * 1024 * 1024   // 50 MB hard cap
private const val PARSE_TIMEOUT  = 60_000L             // 60 s

@Singleton
class FileParser @Inject constructor(
    @ApplicationContext private val context: Context
) {

    // ── PDF ────────────────────────────────────────────────────────────────────
    suspend fun parsePdf(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(PARSE_TIMEOUT) {
                val bytes = readBytesLimited(uri)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")

                if (bytes.isEmpty())
                    return@withTimeout ParseResult.Error("File is empty")

                val text = extractTextFromPdf(bytes)
                if (text.isBlank() || text.length < 20)
                    return@withTimeout ParseResult.Error(
                        "Could not extract text from this PDF.\n" +
                        "The file may be scanned/image-based or encrypted.\n\n" +
                        "Try a text-based PDF or TXT file."
                    )
                ParseResult.Success(text.trim(), TextProcessor.countWords(text))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("PDF loading timed out. The file may be too large or complex.")
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            ParseResult.Error("PDF error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun extractTextFromPdf(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size / 4)
        try {
            val content = String(bytes, Charsets.ISO_8859_1)
            val tjPattern      = Regex("""\(([^)\\]*(?:\\.[^)\\]*)*)\)\s*Tj""")
            val tjArrayPattern = Regex("""\[([^\]]*)\]\s*TJ""")
            val stringPattern  = Regex("""\(([^)\\]*(?:\\.[^)\\]*)*)\)""")
            val btEtPattern    = Regex("""BT(.*?)ET""", RegexOption.DOT_MATCHES_ALL)

            var foundInBlocks = false
            btEtPattern.findAll(content).take(2000).forEach { block ->
                val blockText = block.groupValues[1]
                tjPattern.findAll(blockText).forEach { m ->
                    val t = decodePdfString(m.groupValues[1])
                    if (t.isNotBlank()) { sb.append(t).append(' '); foundInBlocks = true }
                }
                tjArrayPattern.findAll(blockText).forEach { m ->
                    stringPattern.findAll(m.groupValues[1]).forEach { s ->
                        val t = decodePdfString(s.groupValues[1])
                        if (t.isNotBlank()) { sb.append(t); foundInBlocks = true }
                    }
                    sb.append(' ')
                }
            }

            if (!foundInBlocks || sb.length < 50) {
                sb.clear()
                stringPattern.findAll(content).take(5000).forEach { m ->
                    val t = decodePdfString(m.groupValues[1])
                    if (t.length > 1 && t.all { c -> c.code in 32..126 || c == '\n' })
                        sb.append(t).append(' ')
                }
            }
        } catch (_: Exception) {
            try {
                val raw = String(bytes, Charsets.ISO_8859_1)
                val buf = StringBuilder()
                var run = StringBuilder()
                for (c in raw) {
                    if (c.code in 32..126) run.append(c)
                    else { if (run.length > 3) buf.append(run).append(' '); run.clear() }
                }
                sb.append(buf)
            } catch (_: Exception) {}
        }
        return sb.toString()
            .replace(Regex("[^\u0020-\u007E\n\r\t]"), " ")
            .replace(Regex("\\s{3,}"), "\n")
            .trim()
    }

    private fun decodePdfString(raw: String): String = raw
        .replace("\\n", "\n").replace("\\r", "\r").replace("\\t", "\t")
        .replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\")
        .filter { c -> c.code in 32..126 || c == '\n' || c == '\r' }

    // ── TXT ────────────────────────────────────────────────────────────────────
    suspend fun parseTxt(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(30_000L) {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")
                val text = try {
                    stream.use { s ->
                        // Read in chunks with a size cap to prevent OOM
                        val sb = StringBuilder()
                        val reader = s.bufferedReader(Charsets.UTF_8)
                        val buf = CharArray(64 * 1024)
                        var read: Int
                        var total = 0
                        while (reader.read(buf).also { read = it } != -1) {
                            sb.append(buf, 0, read)
                            total += read
                            if (total > 10 * 1024 * 1024) break // 10 MB cap for TXT
                        }
                        sb.toString()
                    }
                } catch (_: Exception) {
                    context.contentResolver.openInputStream(uri)
                        ?.use { it.bufferedReader(Charsets.ISO_8859_1).readText() }
                        ?: return@withTimeout ParseResult.Error("Cannot read file")
                }
                if (text.isBlank()) return@withTimeout ParseResult.Error("File is empty")
                ParseResult.Success(text.trim(), TextProcessor.countWords(text))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("File loading timed out. The file may be too large.")
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error")
        }
    }

    // ── EPUB ───────────────────────────────────────────────────────────────────
    suspend fun parseEpub(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            withTimeout(PARSE_TIMEOUT) {
                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withTimeout ParseResult.Error("Cannot open file")

                val sb = StringBuilder()
                ZipInputStream(inputStream.buffered()).use { zip ->
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
                    }
                }
                val text = sb.toString().trim()
                if (text.isBlank() || text.length < 50)
                    return@withTimeout ParseResult.Error(
                        "Could not extract text from EPUB.\n" +
                        "The file may be DRM-protected or in an unsupported format."
                    )
                ParseResult.Success(text, TextProcessor.countWords(text))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("EPUB loading timed out.")
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            ParseResult.Error("EPUB error: ${e.message ?: "Unknown error"}")
        }
    }

    // ── DOCX ───────────────────────────────────────────────────────────────────
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
                    ?: return@withTimeout ParseResult.Error(
                        "Not a valid DOCX file.\nPlease ensure the file is a .docx document."
                    )

                // Extract text from w:t elements, add paragraph breaks at w:p
                val sb = StringBuilder()
                val wt = Regex("""<w:t[^>]*>([^<]*)</w:t>""")
                val wp = Regex("""</w:p>""")

                // Process paragraph by paragraph
                xml.split("</w:p>").forEach { para ->
                    val words = wt.findAll(para).map { it.groupValues[1] }.joinToString("")
                    if (words.isNotBlank()) sb.append(words.trim()).append("\n")
                }

                val text = sb.toString()
                    .replace(Regex("\\n{3,}"), "\n\n")
                    .trim()

                if (text.isBlank() || text.length < 10)
                    return@withTimeout ParseResult.Error("Could not extract text from this DOCX file.")

                ParseResult.Success(text, TextProcessor.countWords(text))
            }
        } catch (e: TimeoutCancellationException) {
            ParseResult.Error("DOCX loading timed out.")
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            ParseResult.Error("DOCX error: ${e.message ?: "Unknown error"}")
        }
    }

    // ── HTML file ──────────────────────────────────────────────────────────────
    suspend fun parseHtml(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ParseResult.Error("Cannot open file")
            val html = stream.use { it.bufferedReader(Charsets.UTF_8).readText() }
            val text = Jsoup.parse(html).body().text().trim()
            if (text.isBlank()) return@withContext ParseResult.Error("No text found in HTML file")
            ParseResult.Success(text, TextProcessor.countWords(text))
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            ParseResult.Error("HTML error: ${e.message ?: "Unknown error"}")
        }
    }

    // ── Markdown ───────────────────────────────────────────────────────────────
    suspend fun parseMarkdown(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            val stream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ParseResult.Error("Cannot open file")
            val raw = stream.use { it.bufferedReader(Charsets.UTF_8).readText() }
            // Strip markdown syntax to get plain text
            val text = raw
                .replace(Regex("^#{1,6}\\s+", RegexOption.MULTILINE), "")
                .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
                .replace(Regex("\\*([^*]+)\\*"), "$1")
                .replace(Regex("__([^_]+)__"), "$1")
                .replace(Regex("_([^_]+)_"), "$1")
                .replace(Regex("`{1,3}[^`]*`{1,3}"), "")
                .replace(Regex("^>+\\s*", RegexOption.MULTILINE), "")
                .replace(Regex("!?\\[([^]]*)]\\([^)]*\\)"), "$1")
                .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "")
                .replace(Regex("^\\d+\\.\\s+", RegexOption.MULTILINE), "")
                .replace(Regex("-{3,}|\\*{3,}|_{3,}"), "")
                .trim()
            if (text.isBlank()) return@withContext ParseResult.Error("No text found in Markdown file")
            ParseResult.Success(text, TextProcessor.countWords(text))
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            ParseResult.Error("Markdown error: ${e.message ?: "Unknown error"}")
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────
    private fun readBytesLimited(uri: Uri): ByteArray? {
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            val buf = ByteArray(MAX_FILE_BYTES + 1)
            var totalRead = 0
            var read: Int
            while (stream.read(buf, totalRead, buf.size - totalRead).also { read = it } != -1) {
                totalRead += read
                if (totalRead >= MAX_FILE_BYTES) break
            }
            buf.copyOf(totalRead)
        }
    }
}

sealed class ParseResult {
    data class Success(val text: String, val wordCount: Int) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

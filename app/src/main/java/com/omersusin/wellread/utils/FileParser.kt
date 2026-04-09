package com.omersusin.wellread.utils

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileParser @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun parsePdf(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ParseResult.Error("Cannot open file")

            val bytes = inputStream.readBytes()
            inputStream.close()

            val text = extractTextFromPdf(bytes)

            if (text.isBlank() || text.length < 20) {
                return@withContext ParseResult.Error(
                    "Could not extract text from this PDF. " +
                    "The file may be scanned/image-based or encrypted."
                )
            }

            ParseResult.Success(text.trim(), TextProcessor.countWords(text))
        } catch (e: Exception) {
            ParseResult.Error("PDF error: ${e.message ?: "Unknown error"}")
        }
    }

    private fun extractTextFromPdf(bytes: ByteArray): String {
        val sb = StringBuilder()
        try {
            // Strategy 1: Extract text between BT and ET markers
            val content = String(bytes, Charsets.ISO_8859_1)

            // Find all text showing operators: Tj, TJ, '  "
            val tjPattern = Regex("""\(([^)\\]*(?:\\.[^)\\]*)*)\)\s*Tj""")
            val tjArrayPattern = Regex("""\[([^\]]*)\]\s*TJ""")
            val stringPattern = Regex("""\(([^)\\]*(?:\\.[^)\\]*)*)\)""")

            // Extract BT...ET blocks
            val btEtPattern = Regex("""BT(.*?)ET""", RegexOption.DOT_MATCHES_ALL)
            val blocks = btEtPattern.findAll(content)

            var foundInBlocks = false
            blocks.forEach { block ->
                val blockText = block.groupValues[1]

                // Find Tj operators
                tjPattern.findAll(blockText).forEach { match ->
                    val text = decodePdfString(match.groupValues[1])
                    if (text.isNotBlank()) {
                        sb.append(text).append(" ")
                        foundInBlocks = true
                    }
                }

                // Find TJ arrays
                tjArrayPattern.findAll(blockText).forEach { match ->
                    val arrayContent = match.groupValues[1]
                    stringPattern.findAll(arrayContent).forEach { strMatch ->
                        val text = decodePdfString(strMatch.groupValues[1])
                        if (text.isNotBlank()) {
                            sb.append(text)
                            foundInBlocks = true
                        }
                    }
                    sb.append(" ")
                }
            }

            // Strategy 2: If blocks didn't work, try raw string extraction
            if (!foundInBlocks || sb.length < 50) {
                sb.clear()
                // Find all parenthesized strings in the document
                val allStrings = stringPattern.findAll(content)
                allStrings.forEach { match ->
                    val text = decodePdfString(match.groupValues[1])
                    // Filter out non-text content (binary data, etc.)
                    if (text.length > 1 && text.all { c ->
                        c.code in 32..126 || c == '\n' || c == '\r' || c == '\t'
                    }) {
                        sb.append(text).append(" ")
                    }
                }
            }

            // Strategy 3: UTF-16 encoded PDFs
            if (sb.length < 50) {
                sb.clear()
                try {
                    val utf16Content = String(bytes, Charsets.UTF_16BE)
                    val readable = utf16Content.filter { c ->
                        c.code in 32..126 || c == '\n'
                    }
                    if (readable.length > 100) {
                        sb.append(readable)
                    }
                } catch (e: Exception) {
                    // ignore
                }
            }

        } catch (e: Exception) {
            // Strategy 4: Last resort - read as latin1 and filter printable chars
            try {
                val raw = String(bytes, Charsets.ISO_8859_1)
                val words = StringBuilder()
                var wordBuffer = StringBuilder()

                for (c in raw) {
                    if (c.code in 32..126) {
                        wordBuffer.append(c)
                    } else {
                        if (wordBuffer.length > 2) {
                            words.append(wordBuffer).append(" ")
                        }
                        wordBuffer.clear()
                    }
                }
                sb.append(words)
            } catch (e2: Exception) {
                // give up
            }
        }

        // Clean up the extracted text
        return sb.toString()
            .replace(Regex("[^\\x20-\\x7E\\n\\r\\t]"), " ")
            .replace(Regex("\\s{3,}"), "\n")
            .replace(Regex("(?<=[.!?])\\s*\\n\\s*"), "\n")
            .trim()
    }

    private fun decodePdfString(raw: String): String {
        return raw
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\(", "(")
            .replace("\\)", ")")
            .replace("\\\\", "\\")
            .filter { c -> c.code in 32..126 || c == '\n' || c == '\r' }
    }

    suspend fun parseTxt(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)?.readText()
                ?: context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.ISO_8859_1)?.readText()
                ?: return@withContext ParseResult.Error("Cannot open file")

            if (text.isBlank()) return@withContext ParseResult.Error("File is empty")

            ParseResult.Success(text.trim(), TextProcessor.countWords(text))
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun parseEpub(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ParseResult.Error("Cannot open file")

            val bytes = inputStream.readBytes()
            inputStream.close()

            // EPUB is a ZIP file - find HTML content files
            val sb = StringBuilder()
            var pos = 0

            while (pos < bytes.size - 4) {
                // ZIP local file header signature: PK\x03\x04
                if (bytes[pos] == 0x50.toByte() &&
                    bytes[pos+1] == 0x4B.toByte() &&
                    bytes[pos+2] == 0x03.toByte() &&
                    bytes[pos+3] == 0x04.toByte()) {

                    // Skip to filename length
                    if (pos + 30 < bytes.size) {
                        val fnLen = (bytes[pos+26].toInt() and 0xFF) or
                                ((bytes[pos+27].toInt() and 0xFF) shl 8)
                        val extraLen = (bytes[pos+28].toInt() and 0xFF) or
                                ((bytes[pos+29].toInt() and 0xFF) shl 8)

                        if (pos + 30 + fnLen < bytes.size) {
                            val filename = String(bytes, pos + 30, fnLen, Charsets.UTF_8)

                            // Only process HTML/XHTML content files
                            if (filename.endsWith(".html") || filename.endsWith(".xhtml") ||
                                filename.endsWith(".htm") || filename.contains("content")) {

                                val dataStart = pos + 30 + fnLen + extraLen
                                val compressedSize = (bytes[pos+18].toLong() and 0xFF) or
                                        ((bytes[pos+19].toLong() and 0xFF) shl 8) or
                                        ((bytes[pos+20].toLong() and 0xFF) shl 16) or
                                        ((bytes[pos+21].toLong() and 0xFF) shl 24)

                                if (dataStart + compressedSize <= bytes.size && compressedSize > 0) {
                                    try {
                                        val compressedData = bytes.copyOfRange(
                                            dataStart,
                                            (dataStart + compressedSize).toInt().coerceAtMost(bytes.size)
                                        )
                                        val inflater = java.util.zip.Inflater(true)
                                        inflater.setInput(compressedData)
                                        val output = ByteArray(compressedSize.toInt() * 4)
                                        val resultLength = inflater.inflate(output)
                                        inflater.end()

                                        val html = String(output, 0, resultLength, Charsets.UTF_8)
                                        val text = html
                                            .replace(Regex("<style[^>]*>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
                                            .replace(Regex("<script[^>]*>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
                                            .replace(Regex("<[^>]+>"), " ")
                                            .replace(Regex("&nbsp;"), " ")
                                            .replace(Regex("&amp;"), "&")
                                            .replace(Regex("&lt;"), "<")
                                            .replace(Regex("&gt;"), ">")
                                            .replace(Regex("&#?[a-zA-Z0-9]+;"), " ")
                                            .replace(Regex("\\s{2,}"), " ")
                                            .trim()

                                        if (text.length > 50) {
                                            sb.append(text).append("\n\n")
                                        }
                                    } catch (e: Exception) {
                                        // Skip this entry
                                    }
                                }
                            }
                        }
                    }
                }
                pos++
            }

            val text = sb.toString().trim()
            if (text.isBlank() || text.length < 50) {
                return@withContext ParseResult.Error("Could not extract text from EPUB")
            }

            ParseResult.Success(text, TextProcessor.countWords(text))
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error")
        }
    }
}

sealed class ParseResult {
    data class Success(val text: String, val wordCount: Int) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

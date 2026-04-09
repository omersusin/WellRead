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
            // PDF text extraction via content resolver
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ParseResult.Error("Cannot open file")
            val bytes = inputStream.readBytes()
            inputStream.close()
            // Basic PDF text extraction - find text between stream markers
            val content = String(bytes, Charsets.ISO_8859_1)
            val sb = StringBuilder()
            val regex = Regex("""BT.*?ET""", RegexOption.DOT_MATCHES_ALL)
            regex.findAll(content).forEach { match ->
                val tj = Regex("""\(([^)]*)\)\s*Tj""")
                tj.findAll(match.value).forEach { tj ->
                    sb.append(tj.groupValues[1]).append(" ")
                }
            }
            var text = sb.toString().trim()
            if (text.length < 50) {
                // Fallback: try reading as plain text
                text = String(bytes).filter { it.code in 32..126 || it == '\n' || it == '\r' }
                    .replace(Regex("[^\\x20-\\x7E\\n\\r]"), " ")
                    .replace(Regex("\\s{3,}"), "\n")
                    .trim()
            }
            if (text.isBlank()) return@withContext ParseResult.Error("Could not extract text from PDF")
            ParseResult.Success(text, TextProcessor.countWords(text))
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun parseTxt(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText()
                ?: return@withContext ParseResult.Error("Cannot open file")
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
            // Basic EPUB text extraction - strip HTML tags from content
            val content = String(bytes, Charsets.UTF_8)
            val text = content
                .replace(Regex("<[^>]*>"), " ")
                .replace(Regex("&[a-z]+;"), " ")
                .replace(Regex("\\s{2,}"), " ")
                .trim()
            if (text.isBlank()) return@withContext ParseResult.Error("Could not extract text from EPUB")
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

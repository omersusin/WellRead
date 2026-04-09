package com.omersusin.wellread.utils

import android.content.Context
import android.net.Uri
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfReader
import com.itextpdf.kernel.pdf.canvas.parser.PdfTextExtractor
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
            val reader = PdfReader(inputStream)
            val pdf = PdfDocument(reader)
            val sb = StringBuilder()
            for (i in 1..pdf.numberOfPages) {
                sb.append(PdfTextExtractor.getTextFromPage(pdf.getPage(i)))
                sb.append("\n")
            }
            pdf.close()
            val text = sb.toString().trim()
            ParseResult.Success(text, TextProcessor.countWords(text))
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun parseTxt(uri: Uri): ParseResult = withContext(Dispatchers.IO) {
        try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return@withContext ParseResult.Error("Cannot open file")
            ParseResult.Success(text.trim(), TextProcessor.countWords(text))
        } catch (e: Exception) {
            ParseResult.Error(e.message ?: "Unknown error")
        }
    }
}

sealed class ParseResult {
    data class Success(val text: String, val wordCount: Int) : ParseResult()
    data class Error(val message: String) : ParseResult()
}

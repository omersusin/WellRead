package com.omersusin.wellread.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebImporter @Inject constructor(
    private val client: OkHttpClient
) {
    suspend fun importFromUrl(url: String): WebImportResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android) WellRead/1.0")
                .build()
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return@withContext WebImportResult.Error("Empty response")
            val doc = Jsoup.parse(html)

            // Remove clutter
            doc.select("script, style, nav, footer, header, aside, iframe, .ad, .advertisement, #comments").remove()

            val title = doc.title().ifBlank {
                doc.selectFirst("h1")?.text() ?: "Web Article"
            }

            // Try article body first, then fallback
            val contentEl = doc.selectFirst("article")
                ?: doc.selectFirst("main")
                ?: doc.selectFirst(".post-content")
                ?: doc.selectFirst(".article-body")
                ?: doc.selectFirst(".content")
                ?: doc.body()

            val text = contentEl?.text()?.trim() ?: return@withContext WebImportResult.Error("No content found")

            if (text.length < 100) return@withContext WebImportResult.Error("Content too short")

            WebImportResult.Success(
                title = title,
                text = text,
                wordCount = TextProcessor.countWords(text),
                sourceUrl = url
            )
        } catch (e: Exception) {
            WebImportResult.Error(e.message ?: "Network error")
        }
    }
}

sealed class WebImportResult {
    data class Success(
        val title: String,
        val text: String,
        val wordCount: Int,
        val sourceUrl: String
    ) : WebImportResult()
    data class Error(val message: String) : WebImportResult()
}

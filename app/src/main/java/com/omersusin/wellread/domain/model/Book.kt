package com.omersusin.wellread.domain.model

data class Book(
    val id: Long = 0,
    val title: String,
    val author: String = "Unknown",
    val filePath: String = "",
    val sourceUrl: String = "",
    val coverPath: String = "",
    val type: BookType,
    val totalWords: Int = 0,
    val currentPosition: Int = 0,
    val isFinished: Boolean = false,
    val addedAt: Long = System.currentTimeMillis(),
    val lastReadAt: Long = 0L,
    /** Cached text – used for WEB, CLIPBOARD, and any other type where
     *  re-parsing on every open would be expensive or fragile. */
    val content: String = ""
)

enum class BookType {
    PDF,
    EPUB,
    TXT,
    WEB,
    DOCX,
    MARKDOWN,
    HTML,
    CLIPBOARD
}

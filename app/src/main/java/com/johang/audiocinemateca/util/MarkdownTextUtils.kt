package com.johang.audiocinemateca.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Convierte texto en Markdown básico (encabezados, viñetas, negritas y cursivas)
 * a un [AnnotatedString] nativo de Jetpack Compose.
 */
fun parseMarkdownToAnnotatedString(markdown: String): AnnotatedString {
    return buildAnnotatedString {
        val lines = markdown.replace("\r\n", "\n").split("\n")
        var hasContent = false

        for (rawLine in lines) {
            val trimmed = rawLine.trim()
            if (trimmed == "---" || trimmed == "***") {
                continue
            }

            if (hasContent) {
                append("\n")
            }
            hasContent = true

            val headingText = when {
                trimmed.startsWith("### ") -> trimmed.removePrefix("### ")
                trimmed.startsWith("## ") -> trimmed.removePrefix("## ")
                trimmed.startsWith("# ") -> trimmed.removePrefix("# ")
                else -> null
            }

            if (headingText != null) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                    appendInlineMarkdown(headingText)
                }
            } else {
                val bulletMatch = Regex("^(\\s*)[*-]\\s+(.*)$").find(rawLine)
                if (bulletMatch != null) {
                    val indent = bulletMatch.groupValues[1]
                    val content = bulletMatch.groupValues[2]
                    append("$indent• ")
                    appendInlineMarkdown(content)
                } else {
                    appendInlineMarkdown(rawLine)
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(text: String) {
    var index = 0
    while (index < text.length) {
        if (text.startsWith("**", index)) {
            val endIndex = text.indexOf("**", index + 2)
            if (endIndex > index + 2) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(text.substring(index + 2, endIndex))
                }
                index = endIndex + 2
                continue
            }
        }

        if (text[index] == '*') {
            val endIndex = text.indexOf('*', index + 1)
            if (endIndex > index + 1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(text.substring(index + 1, endIndex))
                }
                index = endIndex + 1
                continue
            }
        }

        val nextStar = text.indexOf('*', index + 1)
        val end = if (nextStar == -1) text.length else nextStar
        append(text.substring(index, end))
        index = end
    }
}

/**
 * Convierte un documento Markdown completo a HTML5 semántico y accesible para WebView,
 * respetando encabezados (h1-h4), listas ordenadas/desordenadas, enlaces clickeables,
 * citas, código en línea, negritas, cursivas y paleta adaptable Claro/Oscuro.
 */
fun parseMarkdownToHtml(
    markdown: String,
    backgroundHex: String = "#121212",
    surfaceHex: String = "#1E1E24",
    textHex: String = "#F3F4F6",
    secondaryTextHex: String = "#C4C7D0",
    accentHex: String = "#60A5FA",
    dividerHex: String = "#2E323D"
): String {
    val lines = markdown.replace("\r\n", "\n").split("\n")
    val body = StringBuilder()

    var listStack = mutableListOf<String>() // "ul" o "ol"

    fun closeAllLists() {
        while (listStack.isNotEmpty()) {
            val tag = listStack.removeAt(listStack.lastIndex)
            body.append("</$tag>\n")
        }
    }

    val paragraphBuffer = mutableListOf<String>()
    fun flushParagraph() {
        if (paragraphBuffer.isNotEmpty()) {
            val joined = paragraphBuffer.joinToString(" ")
            body.append("<p>${formatInlineMarkdownToHtml(joined)}</p>\n")
            paragraphBuffer.clear()
        }
    }

    val orderedRegex = Regex("^\\s*\\d+\\.\\s+(.*)$")
    val bulletRegex = Regex("^(\\s*)[*-]\\s+(.*)$")

    for (rawLine in lines) {
        val trimmed = rawLine.trim()

        if (trimmed.isEmpty()) {
            flushParagraph()
            continue
        }

        if (trimmed == "---" || trimmed == "***") {
            flushParagraph()
            closeAllLists()
            body.append("<hr />\n")
            continue
        }

        when {
            trimmed.startsWith("#### ") -> {
                flushParagraph()
                closeAllLists()
                body.append("<h4>${formatInlineMarkdownToHtml(trimmed.removePrefix("#### "))}</h4>\n")
                continue
            }
            trimmed.startsWith("### ") -> {
                flushParagraph()
                closeAllLists()
                body.append("<h3>${formatInlineMarkdownToHtml(trimmed.removePrefix("### "))}</h3>\n")
                continue
            }
            trimmed.startsWith("## ") -> {
                flushParagraph()
                closeAllLists()
                body.append("<h2>${formatInlineMarkdownToHtml(trimmed.removePrefix("## "))}</h2>\n")
                continue
            }
            trimmed.startsWith("# ") -> {
                flushParagraph()
                closeAllLists()
                body.append("<h1>${formatInlineMarkdownToHtml(trimmed.removePrefix("# "))}</h1>\n")
                continue
            }
        }

        if (trimmed.startsWith("> ")) {
            flushParagraph()
            closeAllLists()
            body.append("<blockquote>${formatInlineMarkdownToHtml(trimmed.removePrefix("> "))}</blockquote>\n")
            continue
        }

        val orderedMatch = orderedRegex.find(rawLine)
        if (orderedMatch != null) {
            flushParagraph()
            if (listStack.lastOrNull() != "ol") {
                closeAllLists()
                listStack.add("ol")
                body.append("<ol>\n")
            }
            body.append("<li>${formatInlineMarkdownToHtml(orderedMatch.groupValues[1])}</li>\n")
            continue
        }

        val bulletMatch = bulletRegex.find(rawLine)
        if (bulletMatch != null) {
            flushParagraph()
            val indentCount = bulletMatch.groupValues[1].length
            val itemContent = formatInlineMarkdownToHtml(bulletMatch.groupValues[2])
            if (indentCount >= 2 && listStack.isNotEmpty()) {
                if (listStack.size == 1) {
                    listStack.add("ul")
                    body.append("<ul>\n")
                }
            } else {
                while (listStack.size > 1) {
                    body.append("</${listStack.removeAt(listStack.lastIndex)}>\n")
                }
                if (listStack.lastOrNull() != "ul") {
                    closeAllLists()
                    listStack.add("ul")
                    body.append("<ul>\n")
                }
            }
            body.append("<li>$itemContent</li>\n")
            continue
        }

        // Texto normal
        closeAllLists()
        paragraphBuffer.add(trimmed)
    }

    flushParagraph()
    closeAllLists()

    return """
        <!DOCTYPE html>
        <html lang="es">
        <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <style>
                :root {
                    color-scheme: light dark;
                }
                * {
                    box-sizing: border-box;
                }
                body {
                    margin: 0;
                    padding: 20px 18px 40px 18px;
                    background-color: $backgroundHex;
                    color: $textHex;
                    font-family: -apple-system, BlinkMacSystemFont, "Roboto", "Segoe UI", sans-serif;
                    font-size: 17px;
                    line-height: 1.65;
                    word-wrap: break-word;
                }
                h1 {
                    font-size: 1.55em;
                    line-height: 1.3;
                    margin: 0 0 14px 0;
                    color: $accentHex;
                }
                h2 {
                    font-size: 1.25em;
                    line-height: 1.35;
                    margin: 22px 0 10px 0;
                    color: $accentHex;
                }
                h3 {
                    font-size: 1.08em;
                    line-height: 1.4;
                    margin: 16px 0 8px 0;
                    color: $textHex;
                }
                p {
                    margin: 0 0 12px 0;
                }
                ul, ol {
                    margin: 6px 0 14px 0;
                    padding-left: 22px;
                }
                li {
                    margin-bottom: 8px;
                }
                hr {
                    border: none;
                    border-top: 1px solid $dividerHex;
                    margin: 20px 0;
                }
                a {
                    color: $accentHex;
                    font-weight: 600;
                    text-decoration: underline;
                    padding: 2px 0;
                    display: inline-block;
                }
                code {
                    background-color: $surfaceHex;
                    color: $textHex;
                    padding: 2px 6px;
                    border-radius: 6px;
                    font-family: monospace;
                    font-size: 0.92em;
                }
                blockquote {
                    margin: 12px 0;
                    padding: 10px 14px;
                    background-color: $surfaceHex;
                    border-left: 4px solid $accentHex;
                    border-radius: 6px;
                    color: $secondaryTextHex;
                }
                em {
                    color: $secondaryTextHex;
                }
            </style>
        </head>
        <body>
            <main role="main">
                ${body}
            </main>
        </body>
        </html>
    """.trimIndent()
}

private fun escapeHtml(input: String): String {
    return input
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}

private fun formatInlineMarkdownToHtml(raw: String): String {
    // Extraer enlaces [texto](url) antes de escapar HTML
    val linkRegex = Regex("\\[([^\\]]+)\\]\\((https?://[^)\\s]+)\\)")
    val placeholders = mutableListOf<Pair<String, String>>()
    var working = linkRegex.replace(raw) { match ->
        val token = "___MDLINK_${placeholders.size}___"
        val label = escapeHtml(match.groupValues[1])
        val url = escapeHtml(match.groupValues[2])
        placeholders.add(token to "<a href=\"$url\">$label</a>")
        token
    }

    working = escapeHtml(working)

    // Código en línea `...`
    working = Regex("`([^`]+)`").replace(working) { "<code>${it.groupValues[1]}</code>" }
    // Negritas **...**
    working = Regex("\\*\\*([^*]+)\\*\\*").replace(working) { "<strong>${it.groupValues[1]}</strong>" }
    // Cursivas *...*
    working = Regex("\\*([^*]+)\\*").replace(working) { "<em>${it.groupValues[1]}</em>" }

    // Restaurar enlaces
    for ((token, htmlLink) in placeholders) {
        working = working.replace(token, htmlLink)
    }
    return working
}

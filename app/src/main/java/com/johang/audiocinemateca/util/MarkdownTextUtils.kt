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

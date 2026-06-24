package com.example.myapplication

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

object SearchUtils {
    /**
     * Highlights the occurrences of [query] within [text] using [highlightColor].
     */
    fun highlightText(
        text: String,
        query: String,
        highlightColor: Color = Color(0xFFFFD54F).copy(alpha = 0.7f), // Nice Amber color
        contentColor: Color = Color.Unspecified
    ): AnnotatedString {
        if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
            return AnnotatedString(text)
        }

        return buildAnnotatedString {
            var start = 0
            val lowerText = text.lowercase()
            val lowerQuery = query.trim().lowercase()
            
            if (lowerQuery.isEmpty()) {
                append(text)
                return@buildAnnotatedString
            }

            while (true) {
                val index = lowerText.indexOf(lowerQuery, start)
                if (index == -1) {
                    append(text.substring(start))
                    break
                }
                
                append(text.substring(start, index))
                withStyle(
                    style = SpanStyle(
                        background = highlightColor,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(text.substring(index, index + lowerQuery.length))
                }
                start = index + lowerQuery.length
            }
        }
    }
}

package io.github.rosemoe.sora.lsp.editor.text

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.URLSpan
import java.util.Locale

class SimpleMarkdownRenderer {
    fun render(
        markdown: String,
        boldColor: Int,
        inlineCodeColor: Int,
        blockCodeColor: Int,
        codeTypeface: Typeface,
        linkColor: Int? = null,
        headingScale: FloatArray = DEFAULT_HEADING_SCALE
    ): Spanned {
        val normalized = normalize(markdown)
        val blocks = parseBlocks(normalized)
        return build(blocks, boldColor, inlineCodeColor, blockCodeColor, codeTypeface, linkColor, headingScale)
    }

    private fun build(
        blocks: List<Block>,
        boldColor: Int,
        inlineCodeColor: Int,
        blockCodeColor: Int,
        codeTypeface: Typeface,
        linkColor: Int?,
        headingScale: FloatArray
    ): Spanned {
        val builder = SpannableStringBuilder()
        var firstBlock = true
        for (block in blocks) {
            if (builder.isNotEmpty()) {
                if (!firstBlock) {
                    builder.append("\n\n")
                }
            }
            when (block) {
                is Block.Heading -> {
                    val start = builder.length
                    appendInlines(builder, block.inlines, boldColor, inlineCodeColor, codeTypeface, linkColor)
                    val end = builder.length
                    builder.setSpan(StyleSpan(Typeface.BOLD), start, end, SPAN_MODE)
                    val scaleIndex = (block.level - 1).coerceIn(0, headingScale.lastIndex)
                    builder.setSpan(RelativeSizeSpan(headingScale[scaleIndex]), start, end, SPAN_MODE)
                }
                is Block.Paragraph -> {
                    appendInlines(builder, block.inlines, boldColor, inlineCodeColor, codeTypeface, linkColor)
                }
                is Block.CodeBlock -> {
                    val start = builder.length
                    builder.append(block.content)
                    val end = builder.length
                    if (end > start) {
                        builder.setSpan(TypefaceSpanCompat(codeTypeface), start, end, SPAN_MODE)
                        builder.setSpan(ForegroundColorSpan(blockCodeColor), start, end, SPAN_MODE)
                    }
                }
                is Block.ListBlock -> {
                    var number = block.startIndex
                    for (item in block.items) {
                        if (builder.isNotEmpty() && builder.last() != '\n') {
                            builder.append('\n')
                        }
                        val prefixStart = builder.length
                        val label = if (block.ordered) {
                            val value = String.format(Locale.getDefault(), "%d. ", number)
                            number++
                            value
                        } else {
                            "• "
                        }
                        builder.append(label)
                        val prefixEnd = builder.length
                        val itemStart = builder.length
                        appendInlines(builder, item, boldColor, inlineCodeColor, codeTypeface, linkColor)
                        val itemEnd = builder.length
                        builder.setSpan(LeadingMarginSpan.Standard(leadingMargin, leadingMargin + indentMargin), prefixStart, itemEnd, SPAN_MODE)
                        if (!block.ordered) {
                            builder.setSpan(StyleSpan(Typeface.BOLD), prefixStart, prefixEnd, SPAN_MODE)
                        }
                    }
                }
                is Block.Quote -> {
                    val start = builder.length
                    builder.append('│')
                    builder.append(' ')
                    val contentStart = builder.length
                    appendInlines(builder, block.inlines, boldColor, inlineCodeColor, codeTypeface, linkColor)
                    val end = builder.length
                    builder.setSpan(StyleSpan(Typeface.ITALIC), contentStart, end, SPAN_MODE)
                    builder.setSpan(LeadingMarginSpan.Standard(leadingMargin, leadingMargin + indentMargin), start, end, SPAN_MODE)
                }
                is Block.HorizontalRule -> {
                    builder.append(lineSeparator)
                }
            }
            firstBlock = false
        }
        return builder
    }

    private fun appendInlines(
        builder: SpannableStringBuilder,
        inlines: List<Inline>,
        boldColor: Int,
        inlineCodeColor: Int,
        codeTypeface: Typeface,
        linkColor: Int?
    ) {
        for (inline in inlines) {
            when (inline) {
                is Inline.Text -> builder.append(inline.value)
                is Inline.Bold -> {
                    val start = builder.length
                    appendInlines(builder, inline.children, boldColor, inlineCodeColor, codeTypeface, linkColor)
                    val end = builder.length
                    builder.setSpan(StyleSpan(Typeface.BOLD), start, end, SPAN_MODE)
                    builder.setSpan(ForegroundColorSpan(boldColor), start, end, SPAN_MODE)
                }
                is Inline.Italic -> {
                    val start = builder.length
                    appendInlines(builder, inline.children, boldColor, inlineCodeColor, codeTypeface, linkColor)
                    val end = builder.length
                    builder.setSpan(StyleSpan(Typeface.ITALIC), start, end, SPAN_MODE)
                }
                is Inline.Code -> {
                    val start = builder.length
                    builder.append(inline.value)
                    val end = builder.length
                    builder.setSpan(TypefaceSpanCompat(codeTypeface), start, end, SPAN_MODE)
                    builder.setSpan(ForegroundColorSpan(inlineCodeColor), start, end, SPAN_MODE)
                }
                is Inline.Link -> {
                    val start = builder.length
                    appendInlines(builder, inline.label, boldColor, inlineCodeColor, codeTypeface, linkColor)
                    val end = builder.length
                    builder.setSpan(URLSpan(inline.url), start, end, SPAN_MODE)
                    if (linkColor != null) {
                        builder.setSpan(ForegroundColorSpan(linkColor), start, end, SPAN_MODE)
                    }
                }
            }
        }
    }

    private fun parseBlocks(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = text.split('\n')
        var index = 0
        while (index < lines.size) {
            val raw = lines[index]
            val line = raw.trimEnd()
            if (line.isEmpty()) {
                index++
                continue
            }
            if (line.startsWith("```")) {
                val result = parseCodeBlock(lines, index)
                blocks.add(result.first)
                index = result.second
                continue
            }
            if (isHeading(line)) {
                val result = parseHeading(line)
                blocks.add(result)
                index++
                continue
            }
            if (isHorizontalRule(line)) {
                blocks.add(Block.HorizontalRule)
                index++
                continue
            }
            if (isQuote(line)) {
                val result = parseQuote(lines, index)
                blocks.add(result.first)
                index = result.second
                continue
            }
            if (isList(line)) {
                val result = parseList(lines, index)
                blocks.add(result.first)
                index = result.second
                continue
            }
            val result = parseParagraph(lines, index)
            blocks.add(result.first)
            index = result.second
        }
        return blocks
    }

    private fun parseCodeBlock(lines: List<String>, startIndex: Int): Pair<Block.CodeBlock, Int> {
        val builder = StringBuilder()
        var index = startIndex + 1
        while (index < lines.size) {
            val line = lines[index]
            if (line.trim().startsWith("```")) {
                index++
                break
            }
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            builder.append(line)
            index++
        }
        return Pair(Block.CodeBlock(builder.toString()), index)
    }

    private fun parseHeading(line: String): Block.Heading {
        var level = 0
        while (level < line.length && line[level] == '#') {
            level++
        }
        val content = line.substring(level).trimStart()
        val inlines = parseInlines(content)
        return Block.Heading(level.coerceIn(1, 6), inlines)
    }

    private fun parseQuote(lines: List<String>, startIndex: Int): Pair<Block.Quote, Int> {
        val builder = StringBuilder()
        var index = startIndex
        while (index < lines.size) {
            val line = lines[index]
            if (!isQuote(line.trimStart())) {
                break
            }
            val content = line.trimStart().removePrefix(">")
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            builder.append(content.trimStart())
            index++
        }
        return Pair(Block.Quote(parseInlines(builder.toString())), index)
    }

    private fun parseList(lines: List<String>, startIndex: Int): Pair<Block.ListBlock, Int> {
        val items = mutableListOf<List<Inline>>()
        var index = startIndex
        val firstLine = lines[index]
        val ordered = isOrderedList(firstLine.trimStart())
        var counter = extractListNumber(firstLine.trimStart())
        while (index < lines.size) {
            val raw = lines[index]
            val trimmed = raw.trimStart()
            if (!isList(trimmed)) {
                break
            }
            val markerEnd = listMarkerEnd(trimmed)
            val contentBuilder = StringBuilder(trimmed.substring(markerEnd).trimStart())
            index++
            while (index < lines.size) {
                val cont = lines[index]
                if (cont.isEmpty()) {
                    break
                }
                if (cont.startsWith("    ") || cont.startsWith("\t")) {
                    contentBuilder.append('\n')
                    contentBuilder.append(cont.trimStart())
                    index++
                    continue
                }
                break
            }
            items.add(parseInlines(contentBuilder.toString()))
        }
        if (!ordered) {
            counter = 1
        }
        if (ordered && counter <= 0) {
            counter = 1
        }
        return Pair(Block.ListBlock(ordered, items, counter), index)
    }

    private fun parseParagraph(lines: List<String>, startIndex: Int): Pair<Block.Paragraph, Int> {
        val builder = StringBuilder()
        var index = startIndex
        while (index < lines.size) {
            val line = lines[index]
            if (line.trim().isEmpty()) {
                break
            }
            if (isBoundary(line.trimStart())) {
                break
            }
            if (builder.isNotEmpty()) {
                builder.append(' ')
            }
            builder.append(line.trim())
            index++
        }
        return Pair(Block.Paragraph(parseInlines(builder.toString())), index)
    }

    private fun isBoundary(line: String): Boolean {
        if (line.isEmpty()) {
            return true
        }
        if (line.startsWith("```")) {
            return true
        }
        if (isHeading(line)) {
            return true
        }
        if (isQuote(line)) {
            return true
        }
        if (isHorizontalRule(line)) {
            return true
        }
        if (isList(line)) {
            return true
        }
        return false
    }

    private fun parseInlines(text: String): List<Inline> {
        val nodes = mutableListOf<Inline>()
        var index = 0
        val length = text.length
        while (index < length) {
            val current = text[index]
            if (current == '`') {
                val closing = text.indexOf('`', index + 1)
                if (closing > index) {
                    val content = text.substring(index + 1, closing)
                    nodes.add(Inline.Code(content))
                    index = closing + 1
                    continue
                }
            }
            if (current == '*' || current == '_') {
                val delimiter = if (index + 1 < length && text[index + 1] == current) {
                    "$current$current"
                } else {
                    "$current"
                }
                val closing = findClosing(text, delimiter, index)
                if (closing > index) {
                    val inside = text.substring(index + delimiter.length, closing)
                    val children = parseInlines(inside)
                    if (delimiter.length == 2) {
                        nodes.add(Inline.Bold(children))
                    } else {
                        nodes.add(Inline.Italic(children))
                    }
                    index = closing + delimiter.length
                    continue
                }
            }
            if (current == '[') {
                val closingBracket = findClosingBracket(text, index)
                if (closingBracket > index) {
                    val label = text.substring(index + 1, closingBracket)
                    val urlStart = closingBracket + 1
                    if (urlStart < length && text[urlStart] == '(') {
                        val closingParen = findClosingParen(text, urlStart)
                        if (closingParen > urlStart) {
                            val url = text.substring(urlStart + 1, closingParen)
                            val children = parseInlines(label)
                            nodes.add(Inline.Link(children, url))
                            index = closingParen + 1
                            continue
                        }
                    }
                }
            }
            val nextIndex = nextSpecial(text, index)
            val content = text.substring(index, nextIndex)
            nodes.add(Inline.Text(content))
            index = nextIndex
        }
        return mergeText(nodes)
    }

    private fun nextSpecial(text: String, start: Int): Int {
        var index = start
        while (index < text.length) {
            val c = text[index]
            if (c == '`' || c == '*' || c == '_' || c == '[') {
                break
            }
            index++
        }
        return index.coerceAtLeast(start + 1)
    }

    private fun mergeText(nodes: List<Inline>): List<Inline> {
        val merged = mutableListOf<Inline>()
        val buffer = StringBuilder()
        for (node in nodes) {
            if (node is Inline.Text) {
                buffer.append(node.value)
            } else {
                if (buffer.isNotEmpty()) {
                    merged.add(Inline.Text(buffer.toString()))
                    buffer.clear()
                }
                merged.add(node)
            }
        }
        if (buffer.isNotEmpty()) {
            merged.add(Inline.Text(buffer.toString()))
        }
        return merged.filterNot { it is Inline.Text && it.value.isEmpty() }
    }

    private fun findClosing(text: String, delimiter: String, startIndex: Int): Int {
        var index = startIndex + delimiter.length
        while (index <= text.length - delimiter.length) {
            if (text.regionMatches(index, delimiter, 0, delimiter.length)) {
                return index
            }
            index++
        }
        return -1
    }

    private fun findClosingBracket(text: String, startIndex: Int): Int {
        var depth = 0
        var index = startIndex
        while (index < text.length) {
            val c = text[index]
            if (c == '[') {
                depth++
            } else if (c == ']') {
                depth--
                if (depth == 0) {
                    return index
                }
            }
            index++
        }
        return -1
    }

    private fun findClosingParen(text: String, startIndex: Int): Int {
        var depth = 0
        var index = startIndex
        while (index < text.length) {
            val c = text[index]
            if (c == '(') {
                depth++
            } else if (c == ')') {
                depth--
                if (depth == 0) {
                    return index
                }
            }
            index++
        }
        return -1
    }

    private fun isHeading(line: String): Boolean {
        if (!line.startsWith('#')) {
            return false
        }
        var count = 0
        while (count < line.length && line[count] == '#') {
            count++
        }
        if (count == line.length) {
            return false
        }
        return line[count].isWhitespace()
    }

    private fun isQuote(line: String): Boolean {
        return line.startsWith(">")
    }

    private fun isHorizontalRule(line: String): Boolean {
        val trimmed = line.replace(" ", "")
        return trimmed == "***" || trimmed == "---" || trimmed == "___"
    }

    private fun isList(line: String): Boolean {
        return unorderedPattern.matches(line) || orderedPattern.matches(line)
    }

    private fun isOrderedList(line: String): Boolean {
        return orderedPattern.matches(line)
    }

    private fun extractListNumber(line: String): Int {
        val match = orderedPattern.matchEntire(line) ?: return 1
        return match.groupValues[1].toIntOrNull() ?: 1
    }

    private fun listMarkerEnd(line: String): Int {
        val match = markerPattern.find(line) ?: return 0
        return match.range.last + 1
    }

    private fun normalize(input: String): String {
        var text = input.replace("\r\n", "\n")
        text = text.replace("\r", "\n")
        text = text.replace(brRegex, "\n")
        text = headingRegex.replace(text) { matchResult ->
            val level = matchResult.groupValues[1].toInt()
            val body = matchResult.groupValues[2].trim()
            "${"#".repeat(level)} $body"
        }
        text = blockquoteRegex.replace(text) { matchResult ->
            val body = matchResult.groupValues[1].trim()
            "> $body"
        }
        text = strongRegex.replace(text, "**$1**")
        text = emRegex.replace(text, "*$1*")
        text = codeRegex.replace(text, "`$1`")
        text = preRegex.replace(text) { matchResult ->
            val body = matchResult.groupValues[1]
            "```\n$body\n```"
        }
        text = liRegex.replace(text) { matchResult ->
            val body = matchResult.groupValues[1].trim()
            "- $body"
        }
        text = linkRegex.replace(text) { matchResult ->
            val href = matchResult.groupValues[1]
            val label = matchResult.groupValues[2]
            "[$label]($href)"
        }
        text = text.replace(ulRegex, "")
        text = text.replace(olRegex, "")
        text = text.replace(pCloseRegex, "\n\n")
        text = pOpenRegex.replace(text, "")
        text = text.replace("&nbsp;", " ")
        text = text.replace("&lt;", "<")
        text = text.replace("&gt;", ">")
        text = text.replace("&amp;", "&")
        text = text.replace(multiNewlineRegex, "\n\n")
        return text.trim()
    }

    private class TypefaceSpanCompat(private val typeface: Typeface) : MetricAffectingSpan() {
        override fun updateDrawState(tp: TextPaint) {
            apply(tp)
        }

        override fun updateMeasureState(tp: TextPaint) {
            apply(tp)
        }

        private fun apply(paint: TextPaint) {
            paint.typeface = typeface
        }
    }

    private sealed interface Block {
        class Heading(val level: Int, val inlines: List<Inline>) : Block
        class Paragraph(val inlines: List<Inline>) : Block
        class CodeBlock(val content: String) : Block
        class ListBlock(val ordered: Boolean, val items: List<List<Inline>>, val startIndex: Int) : Block
        class Quote(val inlines: List<Inline>) : Block
        data object HorizontalRule : Block
    }

    private sealed interface Inline {
        class Text(val value: String) : Inline
        class Bold(val children: List<Inline>) : Inline
        class Italic(val children: List<Inline>) : Inline
        class Code(val value: String) : Inline
        class Link(val label: List<Inline>, val url: String) : Inline
    }

    companion object {
        private const val SPAN_MODE = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        private val unorderedPattern = Regex("^[\\*\\-+]\\s+.+")
        private val orderedPattern = Regex("^(\\d+)\\.\\s+.+")
        private val markerPattern = Regex("^(?:\\d+\\.|[\\*\\-+])\\s+")
        private val brRegex = Regex("(?i)<br\\s*/?>")
        private val headingRegex = Regex("(?is)<h([1-6])[^>]*>(.*?)</h\\1>")
        private val blockquoteRegex = Regex("(?is)<blockquote[^>]*>(.*?)</blockquote>")
        private val strongRegex = Regex("(?is)<strong[^>]*>(.*?)</strong>")
        private val emRegex = Regex("(?is)<em[^>]*>(.*?)</em>")
        private val codeRegex = Regex("(?is)<code[^>]*>(.*?)</code>")
        private val preRegex = Regex("(?is)<pre[^>]*>(.*?)</pre>")
        private val liRegex = Regex("(?is)<li[^>]*>(.*?)</li>")
        private val linkRegex = Regex("(?is)<a[^>]+href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>(.*?)</a>")
        private val ulRegex = Regex("(?is)</?ul[^>]*>")
        private val olRegex = Regex("(?is)</?ol[^>]*>")
        private val pOpenRegex = Regex("(?is)<p[^>]*>")
        private val pCloseRegex = Regex("(?is)</p>")
        private val multiNewlineRegex = Regex("\n{3,}")
        private const val leadingMargin = 24
        private const val indentMargin = 24
        private const val lineSeparator = "──────────"
        private val DEFAULT_HEADING_SCALE = floatArrayOf(1.6f, 1.4f, 1.25f, 1.1f, 1.05f, 1.0f)
    }
}

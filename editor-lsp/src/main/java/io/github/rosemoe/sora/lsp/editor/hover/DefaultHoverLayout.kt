package io.github.rosemoe.sora.lsp.editor.hover

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ScrollView
import android.widget.TextView
import io.github.rosemoe.sora.lsp.R
import io.github.rosemoe.sora.lsp.editor.text.SimpleMarkdownRenderer
import io.github.rosemoe.sora.lsp.editor.text.curvedTextScale
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkedString
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.jsonrpc.messages.Either

class DefaultHoverLayout : HoverLayout {
    private lateinit var window: HoverWindow
    private lateinit var root: View
    private lateinit var container: ScrollView
    private lateinit var hoverTextView: TextView
    private var textColor: Int = 0
    private var highlightColor: Int = 0
    private var codeTypeface: Typeface = Typeface.MONOSPACE
    private var baselineEditorTextSize: Float? = null
    private var baselineHoverTextSize: Float? = null
    private var latestEditorTextSize: Float? = null
    private val markdownRenderer = SimpleMarkdownRenderer()

    override fun attach(window: HoverWindow) {
        this.window = window
    }

    override fun createView(inflater: LayoutInflater): View {
        root = inflater.inflate(R.layout.hover_tooltip_window, null, false)
        container = root.findViewById(R.id.hover_scroll_container)
        hoverTextView = root.findViewById(R.id.hover_text)
        hoverTextView.movementMethod = LinkMovementMethod()
        baselineHoverTextSize = hoverTextView.textSize
        latestEditorTextSize?.let { applyEditorScale(it) }
        return root
    }

    override fun applyColorScheme(colorScheme: EditorColorScheme, typeface: Typeface) {
        val editor = window.editor
        textColor = colorScheme.getColor(EditorColorScheme.HOVER_TEXT_NORMAL)
        highlightColor = colorScheme.getColor(EditorColorScheme.HOVER_TEXT_HIGHLIGHTED)
        codeTypeface = typeface
        hoverTextView.setTextColor(textColor)

        val drawable = GradientDrawable().apply {
            cornerRadius = editor.dpUnit * 8
            setColor(colorScheme.getColor(EditorColorScheme.HOVER_BACKGROUND))
            val strokeWidth = editor.dpUnit.toInt().coerceAtLeast(1)
            setStroke(strokeWidth, colorScheme.getColor(EditorColorScheme.HOVER_BORDER))
        }
        root.background = drawable
    }

    override fun renderHover(hover: Hover) {
        hoverTextView.text = buildHoverText(hover)
        container.post { container.smoothScrollTo(0, 0) }
    }

    override fun onTextSizeChanged(oldSize: Float, newSize: Float) {
        if (!::hoverTextView.isInitialized) {
            return
        }
        if (newSize <= 0f) {
            return
        }
        if (baselineEditorTextSize == null) {
            if (oldSize <= 0f) {
                return
            }
            baselineEditorTextSize = oldSize
            baselineHoverTextSize = baselineHoverTextSize ?: hoverTextView.textSize
        }
        latestEditorTextSize = newSize
        applyEditorScale(newSize)
    }

    private fun applyEditorScale(targetEditorSize: Float) {
        val editorBaseline = baselineEditorTextSize ?: return
        val textBaseline = baselineHoverTextSize ?: hoverTextView.textSize
        val scale = targetEditorSize / editorBaseline
        if (scale <= 0f) {
            return
        }
        val curvedScale = curvedTextScale(scale)
        hoverTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textBaseline * curvedScale)
    }

    private fun buildHoverText(hover: Hover): CharSequence {
        val hoverContents = hover.contents ?: return ""
        val rawText = if (hoverContents.isLeft) {
            val items = hoverContents.left.orEmpty()
            items.joinToString("\n\n") { either -> formatMarkedStringEither(either) }
        } else {
            val markup = hoverContents.right
            formatMarkupContent(markup)
        }

        return markdownRenderer.render(
            markdown = rawText,
            boldColor = highlightColor,
            inlineCodeColor = highlightColor,
            blockCodeColor = textColor,
            codeTypeface = codeTypeface,
            linkColor = highlightColor
        )
    }

    private fun formatMarkedStringEither(either: Either<String, MarkedString>?): String {
        if (either == null) {
            return ""
        }
        return if (either.isLeft) {
            either.left ?: ""
        } else {
            formatMarkedString(either.right)
        }
    }

    private fun formatMarkedString(markedString: MarkedString?): String {
        if (markedString == null) {
            return ""
        }
        val language = markedString.language
        val value = markedString.value ?: return ""
        if (language.isNullOrEmpty()) {
            return value
        }
        return "```$language\n$value\n```"
    }

    private fun formatMarkupContent(markupContent: MarkupContent?): String {
        if (markupContent == null) {
            return ""
        }
        val value = markupContent.value
        return value ?: ""
    }
}

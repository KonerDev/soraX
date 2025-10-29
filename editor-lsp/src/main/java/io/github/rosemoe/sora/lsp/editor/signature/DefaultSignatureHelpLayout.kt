package io.github.rosemoe.sora.lsp.editor.signature

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import io.github.rosemoe.sora.lsp.R
import io.github.rosemoe.sora.widget.schemes.EditorColorScheme
import org.eclipse.lsp4j.SignatureInformation
import org.eclipse.lsp4j.SignatureHelp

class DefaultSignatureHelpLayout : SignatureHelpLayout {
    private lateinit var window: SignatureHelpWindow
    private lateinit var root: View
    private lateinit var container: ScrollView
    private lateinit var signatureTextView: TextView
    private lateinit var documentationTextView: TextView
    private lateinit var navigationContainer: View
    private lateinit var previousButton: ImageView
    private lateinit var nextButton: ImageView
    private lateinit var counterView: TextView
    private var textColor = 0
    private var highlightColor = 0

    private var signatureHelp: SignatureHelp? = null
    private var currentIndex = 0

    override fun attach(window: SignatureHelpWindow) {
        this.window = window
    }

    override fun createView(inflater: LayoutInflater): View {
        root = inflater.inflate(R.layout.signature_help_tooltip_window, null, false)
        container = root.findViewById(R.id.signature_help_scroll_container)
        signatureTextView = root.findViewById(R.id.signature_help_signature_text)
        documentationTextView = root.findViewById(R.id.signature_help_documentation_text)
        navigationContainer = root.findViewById(R.id.signature_help_navigation_container)
        previousButton = root.findViewById(R.id.signature_help_previous)
        nextButton = root.findViewById(R.id.signature_help_next)
        counterView = root.findViewById(R.id.signature_help_counter)

        previousButton.setOnClickListener {
            signatureHelp?.let {
                if (it.signatures.isEmpty()) return@setOnClickListener
                currentIndex = (currentIndex - 1).let { idx -> if (idx < 0) it.signatures.lastIndex else idx }
                updateDisplayedSignature()
            }
        }
        nextButton.setOnClickListener {
            signatureHelp?.let {
                if (it.signatures.isEmpty()) return@setOnClickListener
                currentIndex = (currentIndex + 1) % it.signatures.size
                updateDisplayedSignature()
            }
        }
        return root
    }

    override fun applyColorScheme(colorScheme: EditorColorScheme, typeface: Typeface) {
        val editor = window.editor
        textColor = colorScheme.getColor(EditorColorScheme.SIGNATURE_TEXT_NORMAL)
        highlightColor = colorScheme.getColor(EditorColorScheme.SIGNATURE_TEXT_HIGHLIGHTED_PARAMETER)
        signatureTextView.typeface = typeface
        signatureTextView.setTextColor(textColor)
        documentationTextView.typeface = typeface
        documentationTextView.setTextColor(textColor)
        counterView.typeface = typeface
        counterView.setTextColor(textColor)
       /* previousButton.setTextColor(textColor)
        nextButton.setTextColor(textColor)*/
        val drawable = GradientDrawable()
        drawable.cornerRadius = editor.dpUnit * 8
        drawable.setColor(colorScheme.getColor(EditorColorScheme.SIGNATURE_BACKGROUND))
        root.background = drawable
    }

    override fun renderSignatures(signatureHelp: SignatureHelp) {
        this.signatureHelp = signatureHelp
        val activeSignature = signatureHelp.activeSignature ?: 0
        val signatures = signatureHelp.signatures
        if (signatures.isEmpty()) {
            signatureTextView.text = ""
            documentationTextView.text = ""
            counterView.text = "0/0"
            previousButton.isEnabled = false
            nextButton.isEnabled = false
            navigationContainer.visibility = View.GONE
            return
        }

        currentIndex = activeSignature.coerceIn(0, signatures.lastIndex)
        val enableNavigation = signatures.size > 1
        navigationContainer.visibility = if (enableNavigation) View.VISIBLE else View.GONE
        previousButton.visibility = if (enableNavigation) View.VISIBLE else View.GONE
        nextButton.visibility = if (enableNavigation) View.VISIBLE else View.GONE
        counterView.visibility = if (enableNavigation) View.VISIBLE else View.GONE
        previousButton.isEnabled = enableNavigation
        nextButton.isEnabled = enableNavigation
        updateDisplayedSignature()
    }

    private fun updateDisplayedSignature() {
        val signatureHelp = signatureHelp ?: return
        val signatures = signatureHelp.signatures
        if (signatures.isEmpty()) return

        val signature = signatures[currentIndex]
        val builder = SpannableStringBuilder()
        val activeSignatureIndex = signatureHelp.activeSignature ?: 0
        val isActive = currentIndex == activeSignatureIndex
        val activeParameterIndex = signature.activeParameter
            ?: signatureHelp.activeParameter
            ?: -1

        appendSignatureText(builder, signature, activeParameterIndex, isActive)
        signatureTextView.text = builder

        val documentation = signature.documentation
        val documentationText = when {
            documentation == null -> ""
            documentation.isLeft -> documentation.left
            else -> documentation.right?.value ?: ""
        }
        documentationTextView.text = documentationText
        documentationTextView.visibility = if (documentationText.isEmpty()) View.GONE else View.VISIBLE

        counterView.text = "${currentIndex + 1}/${signatures.size}"
        container.post { container.smoothScrollTo(0, 0) }
    }

    private fun appendSignatureText(
        builder: SpannableStringBuilder,
        signature: SignatureInformation,
        activeParameterIndex: Int,
        isActiveSignature: Boolean
    ) {
        val parameters = signature.parameters.orEmpty()
        val activeParameter = parameters.getOrNull(activeParameterIndex)
        val startIndex = builder.length
        builder.append(signature.label.substringBefore('('))
        builder.setSpan(
            ForegroundColorSpan(textColor),
            startIndex,
            builder.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        builder.append("(")
        for (i in parameters.indices) {
            val parameter = parameters[i]
            val label = parameter.label.left
            val spanStart = builder.length
            builder.append(label)
            val highlight = parameter == activeParameter && activeParameterIndex >= 0
            val color = when {
                highlight -> highlightColor
                // isActiveSignature -> activeSignatureColor
                else -> textColor
            }
            builder.setSpan(
                ForegroundColorSpan(color),
                spanStart,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (highlight) {
                builder.setSpan(
                    StyleSpan(Typeface.BOLD),
                    spanStart,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val hasNext = i != parameters.lastIndex
            if (hasNext) {
                builder.append(", ")
            }
        }
        builder.append(")")
        if (isActiveSignature) {
            builder.setSpan(
                StyleSpan(Typeface.BOLD),
                startIndex,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }
}

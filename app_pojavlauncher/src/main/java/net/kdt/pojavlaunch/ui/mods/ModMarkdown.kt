package net.kdt.pojavlaunch.ui.mods

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color

/**
 * Just enough of Markdown for a Modrinth project page.
 *
 * <b>A subset on purpose, and the subset is documented here.</b> Modrinth bodies are Markdown
 * with raw HTML mixed in, and a full renderer is a library this launcher does not ship (release
 * builds do not run R8, so every dependency ships whole; see the handbook). What a mod page
 * actually needs to be readable is headings, paragraphs, lists, code, emphasis and links, so
 * that is what this renders. Everything else degrades to its text: HTML tags are stripped,
 * image references become their alt text, tables collapse to their cell text. The page never
 * claims to be the website; the "Open on Modrinth" link is the way to the whole thing.
 */
sealed class MdBlock {
    class Heading(val level: Int, val text: AnnotatedString) : MdBlock()
    class Paragraph(val text: AnnotatedString) : MdBlock()
    class Bullet(val text: AnnotatedString) : MdBlock()
    class Code(val text: String) : MdBlock()
}

private val HTML_TAG = Regex("""<[^>]{1,200}>""")
private val IMAGE = Regex("""!\[([^\]]*)]\(([^)\s]+)[^)]*\)""")
private val LINK = Regex("""\[([^\]]+)]\(([^)\s]+)[^)]*\)""")
private val BOLD = Regex("""\*\*([^*]+)\*\*|__([^_]+)__""")
private val ITALIC = Regex("""\*([^*\n]+)\*|_([^_\n]+)_""")
private val CODE_SPAN = Regex("""`([^`\n]+)`""")
private val HEADING = Regex("""^(#{1,6})\s+(.*)$""")
private val BULLET_LINE = Regex("""^\s*[-*+]\s+(.*)$""")
private val NUMBERED_LINE = Regex("""^\s*\d{1,3}[.)]\s+(.*)$""")

/**
 * Parse a body into blocks.
 *
 * Line-oriented, one pass, no recursion: a fenced code block swallows lines until its closing
 * fence, a heading is its own block, consecutive ordinary lines merge into a paragraph, and a
 * blank line ends one. Anything this mis-parses renders as plain text rather than failing,
 * which is the property that matters against a third party's markup.
 */
fun parseMarkdown(body: String, accent: Color): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()

    fun flush() {
        val text = paragraph.toString().trim()
        paragraph.setLength(0)
        if (text.isNotEmpty()) blocks.add(MdBlock.Paragraph(inline(text, accent)))
    }

    val lines = body.replace("\r\n", "\n").split('\n')
    var i = 0
    while (i < lines.size) {
        val raw = lines[i]
        val line = raw.trimEnd()
        when {
            line.trimStart().startsWith("```") -> {
                flush()
                val code = StringBuilder()
                i++
                while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                    code.append(lines[i]).append('\n')
                    i++
                }
                if (code.isNotEmpty()) blocks.add(MdBlock.Code(code.toString().trimEnd()))
            }
            HEADING.matches(line) -> {
                flush()
                val match = HEADING.matchEntire(line)!!
                blocks.add(MdBlock.Heading(
                    match.groupValues[1].length,
                    inline(match.groupValues[2], accent)
                ))
            }
            BULLET_LINE.matches(line) -> {
                flush()
                blocks.add(MdBlock.Bullet(inline(BULLET_LINE.matchEntire(line)!!.groupValues[1], accent)))
            }
            NUMBERED_LINE.matches(line) -> {
                flush()
                blocks.add(MdBlock.Bullet(inline(NUMBERED_LINE.matchEntire(line)!!.groupValues[1], accent)))
            }
            line.isBlank() -> flush()
            // A separator or a table rule carries no words; drop it rather than draw dashes.
            line.trim().all { it == '-' || it == '=' || it == '|' || it == ':' || it == ' ' } -> flush()
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                // Table rows keep their cell text, lose their pipes.
                paragraph.append(line.trim().removePrefix("|").removeSuffix("|").replace("|", "  "))
            }
        }
        i++
    }
    flush()
    return blocks
}

/**
 * Inline markup: images to their alt text, links to tappable accent spans, then emphasis.
 *
 * Links use [LinkAnnotation.Url] with no listener, which hands the tap to the platform's URI
 * handler: the launcher does not need to know a browser exists, only that the text does.
 */
private fun inline(text: String, accent: Color): AnnotatedString {
    var cleaned = text.replace(IMAGE) { match -> match.groupValues[1] }
    cleaned = cleaned.replace(HTML_TAG, "")

    data class Span(val start: Int, val end: Int, val style: SpanStyle?, val url: String?)

    // Links are located first, on the cleaned text, because their syntax contains the characters
    // the emphasis passes would mangle. The builder then walks the pieces in order.
    val result = buildAnnotatedString {
        var cursor = 0
        for (match in LINK.findAll(cleaned)) {
            if (match.range.first > cursor) {
                appendStyled(cleaned.substring(cursor, match.range.first), accent)
            }
            val label = match.groupValues[1]
            val url = match.groupValues[2]
            if (url.startsWith("http://") || url.startsWith("https://")) {
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(
                            style = SpanStyle(
                                color = accent,
                                textDecoration = TextDecoration.Underline
                            )
                        )
                    )
                ) { append(label) }
            } else {
                // A relative link points inside modrinth.com and cannot be resolved from here;
                // its words still belong in the sentence.
                append(label)
            }
            cursor = match.range.last + 1
        }
        if (cursor < cleaned.length) appendStyled(cleaned.substring(cursor), accent)
    }
    return result
}

/** Bold, italic and code spans on a piece of text with no links in it. */
private fun AnnotatedString.Builder.appendStyled(text: String, accent: Color) {
    // One combined scan, so overlapping emphasis degrades to first-match rather than doubling.
    var cursor = 0
    val matches = (BOLD.findAll(text).map { Triple(it.range, boldOf(it), null as String?) } +
            CODE_SPAN.findAll(text).map { Triple(it.range, null as SpanStyle?, it.groupValues[1]) })
        .sortedBy { it.first.first }
    var lastEnd = -1
    for ((range, bold, code) in matches) {
        if (range.first <= lastEnd) continue
        if (range.first > cursor) appendItalic(text.substring(cursor, range.first))
        if (code != null) {
            withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = accent)) { append(code) }
        } else if (bold != null) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(boldText(text.substring(range)))
            }
        }
        cursor = range.last + 1
        lastEnd = range.last
    }
    if (cursor < text.length) appendItalic(text.substring(cursor))
}

private fun boldOf(match: MatchResult): SpanStyle = SpanStyle(fontWeight = FontWeight.Bold)

private fun boldText(matched: String): String =
    matched.removeSurrounding("**").removeSurrounding("__")

/** Italic runs inside otherwise-plain text. Stars beat underscores, as in the source. */
private fun AnnotatedString.Builder.appendItalic(text: String) {
    var cursor = 0
    for (match in ITALIC.findAll(text)) {
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        val inner = match.groupValues[1].ifEmpty { match.groupValues[2] }
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inner) }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

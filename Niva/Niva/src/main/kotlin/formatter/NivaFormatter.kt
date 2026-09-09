package main.formatter

import java.io.File

data class FormatResult(val changed: Boolean, val text: String)

private data class KeywordOccurrence(
    val start: Int,
    val colon: Int,
    val word: String,
)

private data class UnaryOccurrence(
    val start: Int,
    val word: String,
)

private data class ControlOccurrences(
    val bar: Int?,
    val barDepth: Int?,
    val arrow: Int?,
    val hasElse: Boolean,
    val hasReset: Boolean,
)

private data class LineInfo(
    val keys: List<KeywordOccurrence>,
    val unary: UnaryOccurrence?,
    val control: ControlOccurrences,
    val endsWithComma: Boolean,
    val startDepth: Int,
    val endDepth: Int,
    val inTripleStringEnd: Boolean,
) {
    val firstMessageStart: Int? = keys.firstOrNull()?.start ?: unary?.start
}

fun formatNivaFile(file: File): FormatResult {
    val original = file.readText()
    val formatted = formatNivaSource(original)
    if (formatted != original) {
        file.writeText(formatted)
    }
    return FormatResult(formatted != original, formatted)
}

fun formatNivaSource(source: String): String {
    val lineSeparator = if ("\r\n" in source) "\r\n" else "\n"
    val normalized = source.replace("\r\n", "\n")
    val hasFinalNewLine = normalized.endsWith("\n")
    val body = if (hasFinalNewLine) normalized.dropLast(1) else normalized
    if (body.isEmpty()) return source

    val lines = body.split("\n").toMutableList()
    alignAdjacentControlSymbols(lines)
    splitContinuationKeywords(lines)
    alignAdjacentKeywords(lines)
    alignAdjacentUnaryMessages(lines)

    val result = lines.joinToString("\n") + if (hasFinalNewLine) "\n" else ""
    return if (lineSeparator == "\r\n") result.replace("\n", "\r\n") else result
}

private fun splitContinuationKeywords(lines: MutableList<String>) {
    var i = 1
    while (i < lines.size) {
        val infos = analyzeLines(lines)
        val previous = infos[i - 1]
        val current = infos[i]
        if (
            lines[i - 1].isNotBlank() &&
            lines[i].isNotBlank() &&
            previous.startDepth == current.startDepth &&
            !previous.control.hasReset &&
            !current.control.hasReset &&
            !previous.inTripleStringEnd &&
            !current.inTripleStringEnd &&
            previous.keys.size == 1 &&
            current.keys.size > 1 &&
            lines[i].substring(0, current.keys.first().start).isBlank()
        ) {
            val replacement = splitKeywordLine(lines[i], current.keys, previous.keys.single().start)
            lines.removeAt(i)
            lines.addAll(i, replacement)
            i += replacement.size
        } else {
            i++
        }
    }
}

private fun splitKeywordLine(
    line: String,
    keys: List<KeywordOccurrence>,
    indent: Int,
): List<String> {
    return keys.mapIndexed { index, key ->
        val nextKeyStart = keys.getOrNull(index + 1)?.start ?: line.length
        val content = line.substring(key.start, nextKeyStart).trim()
        " ".repeat(indent) + content
    }
}

private fun alignAdjacentControlSymbols(lines: MutableList<String>) {
    alignAdjacentBars(lines)
    alignAdjacentSymbol(
        lines = lines,
        position = { it.control.arrow },
        target = { indexes, infos -> indexes.map { infos[it].control.arrow!! }.max() },
        shouldJoin = { previous, current ->
                previous.startDepth == current.startDepth &&
                !previous.control.hasReset &&
                !current.control.hasReset &&
                previous.control.bar == current.control.bar
        },
        shouldFormatGroup = { start, endExclusive, infos ->
            val previous = infos.getOrNull(start - 1)
            val next = infos.getOrNull(endExclusive)
            val followedByElse = next != null &&
                next.control.hasElse &&
                next.control.bar == infos[start].control.bar &&
                next.control.barDepth == infos[start].control.barDepth
            previous == null ||
                !previous.control.hasReset ||
                previous.control.bar != infos[start].control.bar ||
                previous.control.barDepth != infos[start].control.barDepth ||
                followedByElse
        },
    )
}

private fun alignAdjacentBars(lines: MutableList<String>) {
    var infos = analyzeLines(lines)
    var groupStart: Int? = null
    var groupDepth: Int? = null

    fun flushGroup(endExclusive: Int) {
        val start = groupStart ?: return
        val depth = groupDepth ?: return
        val indexes = (start until endExclusive).filter { infos[it].control.barDepth == depth }

        if (indexes.size > 1) {
            val positions = indexes.map { infos[it].control.bar!! }
            val targetPosition = if (
                indexes.any { infos[it].control.hasElse } ||
                indexes.any { infos[it].control.barDepth != infos[it].startDepth }
            ) {
                positions.max()
            } else {
                positions.min()
            }
            val deltas = indexes.associateWith { targetPosition - infos[it].control.bar!! }

            for (index in indexes) {
                val delta = deltas.getValue(index)
                if (delta != 0) {
                    lines[index] = moveTokenTo(lines[index], infos[index].control.bar!!, targetPosition)
                }

                val nextIndex = indexes.firstOrNull { it > index } ?: endExclusive
                for (nestedIndex in index + 1 until nextIndex) {
                    if (
                        delta != 0 &&
                        lines[nestedIndex].isNotBlank() &&
                        infos[nestedIndex].control.bar == null &&
                        isNestedInsideBarGroup(infos[nestedIndex], depth)
                    ) {
                        lines[nestedIndex] = shiftIndent(lines[nestedIndex], delta)
                    }
                }
            }
            infos = analyzeLines(lines)
        }

        groupStart = null
        groupDepth = null
    }

    for (index in lines.indices) {
        val info = infos[index]
        val barDepth = info.control.barDepth

        if (lines[index].isBlank() || info.inTripleStringEnd) {
            flushGroup(index)
            continue
        }

        if (barDepth != null) {
            val depth = groupDepth
            if (groupStart == null || depth == null) {
                groupStart = index
                groupDepth = barDepth
            } else if (barDepth != depth) {
                flushGroup(index)
                groupStart = index
                groupDepth = barDepth
            }
            continue
        }

        val depth = groupDepth
        if (groupStart != null && depth != null && !isNestedInsideBarGroup(info, depth)) {
            flushGroup(index)
        }
    }
    flushGroup(lines.size)
}

private fun isNestedInsideBarGroup(info: LineInfo, depth: Int): Boolean {
    return info.startDepth > depth || info.endDepth > depth
}

private fun shiftIndent(line: String, delta: Int): String {
    if (delta == 0 || line.isEmpty()) return line
    if (delta > 0) return " ".repeat(delta) + line

    val leadingSpaces = line.takeWhile { it == ' ' }.length
    val removeCount = minOf(leadingSpaces, -delta)
    return line.drop(removeCount)
}

private fun alignAdjacentSymbol(
    lines: MutableList<String>,
    position: (LineInfo) -> Int?,
    target: (indexes: List<Int>, infos: List<LineInfo>) -> Int,
    shouldJoin: (LineInfo, LineInfo) -> Boolean,
    shouldFormatGroup: (start: Int, endExclusive: Int, infos: List<LineInfo>) -> Boolean = { _, _, _ -> true },
) {
    var infos = analyzeLines(lines)
    var groupStart: Int? = null

    fun flushGroup(endExclusive: Int) {
        val start = groupStart ?: return
        val indexes = (start until endExclusive).filter { position(infos[it]) != null }
        if (indexes.size > 1 && shouldFormatGroup(start, endExclusive, infos)) {
            val targetPosition = target(indexes, infos)
            for (index in indexes) {
                val symbolPosition = position(infos[index])!!
                lines[index] = moveTokenTo(lines[index], symbolPosition, targetPosition)
            }
            infos = analyzeLines(lines)
        }
        groupStart = null
    }

    for (index in lines.indices) {
        val info = infos[index]
        val canJoinGroup = lines[index].isNotBlank() &&
            position(info) != null &&
            !info.inTripleStringEnd

        if (!canJoinGroup) {
            flushGroup(index)
            continue
        }

        val start = groupStart
        if (start == null) {
            groupStart = index
            continue
        }

        val previous = infos[index - 1]
        if (
            lines[index - 1].isBlank() ||
            position(previous) == null ||
            !shouldJoin(previous, info)
        ) {
            flushGroup(index)
            groupStart = index
        }
    }
    flushGroup(lines.size)
}

private fun moveTokenTo(line: String, tokenStart: Int, targetStart: Int): String {
    if (tokenStart == targetStart) return line

    val before = line.substring(0, tokenStart)
    val tokenAndAfter = line.substring(tokenStart)
    val trimmedBefore = before.trimEnd()
    return if (trimmedBefore.isEmpty()) {
        " ".repeat(targetStart) + tokenAndAfter
    } else {
        val spaces = (targetStart - trimmedBefore.length).coerceAtLeast(1)
        trimmedBefore + " ".repeat(spaces) + tokenAndAfter
    }
}

private fun alignAdjacentKeywords(lines: MutableList<String>) {
    val infos = analyzeLines(lines)
    var groupStart: Int? = null

    fun flushGroup(endExclusive: Int) {
        val start = groupStart ?: return
        if (endExclusive - start > 1) {
            val groupInfos = infos.subList(start, endExclusive)
            val formatted = alignGroup(lines.subList(start, endExclusive), groupInfos)
            for (offset in formatted.indices) {
                lines[start + offset] = formatted[offset]
            }
        }
        groupStart = null
    }

    for (index in lines.indices) {
        val info = infos[index]
        val canJoinGroup = lines[index].isNotBlank() &&
            info.keys.isNotEmpty() &&
            !info.inTripleStringEnd

        if (!canJoinGroup) {
            flushGroup(index)
            continue
        }

        val start = groupStart
        if (start == null) {
            groupStart = index
            continue
        }

        val previous = infos[index - 1]
        if (
            lines[index - 1].isBlank() ||
            previous.keys.isEmpty() ||
            previous.startDepth != info.startDepth ||
            previous.endDepth != info.endDepth ||
            previous.control.hasReset ||
            info.control.hasReset ||
            previous.control.bar != info.control.bar ||
            previous.control.arrow != info.control.arrow ||
            !canContinueKeywordGroup(lines[index], info, previous)
        ) {
            flushGroup(index)
            groupStart = index
        }
    }
    flushGroup(lines.size)
}

private fun canContinueKeywordGroup(
    line: String,
    info: LineInfo,
    previous: LineInfo,
): Boolean {
    val firstKey = info.keys.firstOrNull() ?: return false
    return line.substring(0, firstKey.start).isBlank() || previous.endsWithComma
}

private fun alignGroup(lines: List<String>, infos: List<LineInfo>): List<String> {
    val targetColons = calculateTargetColons(lines, infos)

    return lines.mapIndexed { index, line ->
        alignLine(line, infos[index].keys, targetColons)
    }
}

private fun calculateTargetColons(lines: List<String>, infos: List<LineInfo>): IntArray {
    val maxKeys = infos.maxOf { it.keys.size }
    val targetColons = IntArray(maxKeys)
    val currentLengths = IntArray(lines.size) { index ->
        val firstKey = infos[index].keys.firstOrNull()
        if (firstKey != null && lines[index].substring(0, firstKey.start).isNotBlank()) {
            firstKey.start
        } else {
            0
        }
    }

    for (keyIndex in 0 until maxKeys) {
        val linesWithKey = infos.indices.filter { infos[it].keys.getOrNull(keyIndex) != null }
        val colons = linesWithKey.map { infos[it].keys[keyIndex].colon }
        val anchor = if (keyIndex == 0 && infos.withIndex().all { (index, info) ->
                info.isOnlyWhitespaceBeforeFirstKey(lines[index])
            }
        ) {
            colons.min()
        } else {
            colons.first()
        }
        val minimumPossibleColon = linesWithKey.maxOf { lineIndex ->
            val key = infos[lineIndex].keys[keyIndex]
            val minimumSpace = if (currentLengths[lineIndex] == 0 ||
                (keyIndex == 0 && lines[lineIndex].substring(0, key.start).isNotBlank())
            ) {
                0
            } else {
                1
            }
            currentLengths[lineIndex] + minimumSpace + key.word.length
        }
        targetColons[keyIndex] = maxOf(anchor, minimumPossibleColon)

        for (lineIndex in linesWithKey) {
            val keys = infos[lineIndex].keys
            val key = keys[keyIndex]
            val segmentEnd = keys.getOrNull(keyIndex + 1)?.start ?: lines[lineIndex].length
            val rawSegment = lines[lineIndex].substring(key.start, segmentEnd)
            val segment = if (keyIndex == keys.lastIndex) rawSegment else rawSegment.trimEnd()
            currentLengths[lineIndex] = targetColons[keyIndex] - key.word.length + segment.length
        }
    }
    return targetColons
}

private fun LineInfo.isOnlyWhitespaceBeforeFirstKey(line: String): Boolean {
    val firstKey = keys.firstOrNull() ?: return false
    return line.substring(0, firstKey.start).isBlank()
}

private fun alignLine(
    line: String,
    keys: List<KeywordOccurrence>,
    targetColons: IntArray,
): String {
    if (keys.isEmpty()) return line

    val result = StringBuilder()
    for (index in keys.indices) {
        val key = keys[index]
        val segmentEnd = keys.getOrNull(index + 1)?.start ?: line.length
        val prefix = if (index == 0) line.substring(0, key.start) else ""

        if (index == 0 && prefix.isNotBlank()) {
            result.append(prefix)
        }

        val minimumSpace = if (result.isEmpty() || (index == 0 && prefix.isNotBlank())) 0 else 1
        val targetStart = targetColons[index] - key.word.length
        val spaces = (targetStart - result.length).coerceAtLeast(minimumSpace)
        result.append(" ".repeat(spaces))

        val rawSegment = line.substring(key.start, segmentEnd)
        val segment = if (index == keys.lastIndex) rawSegment else rawSegment.trimEnd()
        result.append(segment)
    }
    return result.toString()
}

private fun alignAdjacentUnaryMessages(lines: MutableList<String>) {
    var infos = analyzeLines(lines)
    for (index in 1 until lines.size) {
        val info = infos[index]
        val previous = infos[index - 1]
        val targetStart = previous.firstMessageStart
        val unary = info.unary

        if (
            lines[index - 1].isNotBlank() &&
            lines[index].isNotBlank() &&
            unary != null &&
            targetStart != null &&
            previous.startDepth == info.startDepth &&
            previous.endDepth == info.endDepth &&
            !previous.control.hasReset &&
            !info.control.hasReset &&
            previous.control.bar == info.control.bar &&
            previous.control.arrow == info.control.arrow &&
            !previous.inTripleStringEnd &&
            !info.inTripleStringEnd
        ) {
            lines[index] = alignUnaryLine(lines[index], unary, targetStart)
            infos = analyzeLines(lines)
        }
    }
}

private fun alignUnaryLine(line: String, unary: UnaryOccurrence, targetStart: Int): String {
    val suffix = line.substring(unary.start)
    return " ".repeat(targetStart) + suffix
}

private fun analyzeLines(lines: List<String>): List<LineInfo> {
    var depth = 0
    var inTripleString = false
    return lines.map { line ->
        val startDepth = depth
        val keys = mutableListOf<KeywordOccurrence>()
        val words = mutableListOf<UnaryOccurrence>()
        val commas = mutableListOf<Int>()
        var bar: Int? = null
        var barDepth: Int? = null
        var arrow: Int? = null
        var hasElse = false
        var hasReset = false
        var i = 0
        var inString: Char? = null

        while (i < line.length) {
            if (inTripleString) {
                if (line.startsWith("\"\"\"", i)) {
                    inTripleString = false
                    i += 3
                } else {
                    i++
                }
                continue
            }

            val stringDelimiter = inString
            if (stringDelimiter != null) {
                if (line[i] == '\\') {
                    i += 2
                } else {
                    if (line[i] == stringDelimiter) inString = null
                    i++
                }
                continue
            }

            when {
                line.startsWith("//", i) -> break
                line.startsWith("\"\"\"", i) -> {
                    inTripleString = true
                    i += 3
                }
                line[i] == '"' || line[i] == '\'' -> {
                    inString = line[i]
                    i++
                }
                line[i] == '(' || line[i] == '[' || line[i] == '{' -> {
                    depth++
                    i++
                }
                line[i] == ')' || line[i] == ']' || line[i] == '}' -> {
                    depth = (depth - 1).coerceAtLeast(0)
                    i++
                }
                depth == startDepth && line[i] == '^' -> {
                    hasReset = true
                    i++
                }
                depth == startDepth && line[i] == ',' -> {
                    commas.add(i)
                    i++
                }
                line.startsWith("|=>", i) -> {
                    if (bar == null) {
                        bar = i
                        barDepth = depth
                    }
                    hasElse = true
                    i += 3
                }
                depth == startDepth && line.startsWith("=>", i) -> {
                    if (arrow == null) arrow = i
                    i += 2
                }
                isStandaloneBar(line, i) -> {
                    if (bar == null) {
                        bar = i
                        barDepth = depth
                    }
                    i++
                }
                depth == startDepth && isWordStart(line[i]) -> {
                    val start = i
                    i++
                    while (i < line.length && isWordPart(line[i])) i++
                    val word = line.substring(start, i)
                    if (
                        i < line.length &&
                        line[i] == ':' &&
                        line.getOrNull(i + 1) != ':' &&
                        line.getOrNull(start - 1) != ':'
                    ) {
                        keys.add(KeywordOccurrence(start, i, word))
                    } else if (word !in ignoredUnaryWords) {
                        words.add(UnaryOccurrence(start, word))
                    }
                }
                else -> i++
            }
        }

        LineInfo(
            keys = keys,
            unary = findUnaryOnlyMessage(line, keys, words, commas),
            control = ControlOccurrences(bar, barDepth, arrow, hasElse, hasReset),
            endsWithComma = commas.lastOrNull() == line.trimEnd().lastIndex,
            startDepth = startDepth,
            endDepth = depth,
            inTripleStringEnd = inTripleString,
        )
    }
}

private fun findUnaryOnlyMessage(
    line: String,
    keys: List<KeywordOccurrence>,
    words: List<UnaryOccurrence>,
    commas: List<Int>,
): UnaryOccurrence? {
    if (keys.isNotEmpty()) return null
    val firstComma = commas.firstOrNull()
    if (firstComma != null) {
        return words.lastOrNull { word ->
            val wordEnd = word.start + word.word.length
            wordEnd <= firstComma && line.substring(wordEnd, firstComma).isBlank()
        }
    }
    if (words.size != 1) return null
    val word = words.single()
    val afterWord = line.substring(word.start + word.word.length)
    return if (afterWord.isBlank()) word else null
}

private fun isWordStart(char: Char): Boolean = char.isLetter() || char == '_'

private fun isWordPart(char: Char): Boolean = char.isLetterOrDigit() || char == '_' || char == '-'

private val ignoredUnaryWords = setOf("true", "null", "false", "type", "union", "enum", "Unit")

private fun isStandaloneBar(line: String, index: Int): Boolean {
    if (line[index] != '|') return false
    val next = line.getOrNull(index + 1)
    val previous = line.getOrNull(index - 1)
    return next != '|' && next != '>' && next != '=' && previous != '|'
}

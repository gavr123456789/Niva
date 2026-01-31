package inlineReplSystem
import java.io.*

//class LineAndContent(val line: Int, val content: String, val count: Int)

// unused
//fun inlineReplSystem(file: File) {
//
//    val lines = file.useLines { it.toList() }
//
//    val q = lines.map {
//        val (patnLineContent, countStr) = it.split("***")
//        val (pathLine, content) = patnLineContent.split("|||")
//        val (path, line) = pathLine.split(":::")
//        val lineAndContent = LineAndContent(line = line.toInt(), content = content, count = countStr.toInt())
//
//        path to lineAndContent
//    }
//
//    val lineNumberToContent = mutableMapOf<String, MutableList<LineAndContent>>()
//    q.forEach { (t, u) ->
//        lineNumberToContent.getOrPut(t) { mutableListOf() }.add(u)
//    }
//
//    addCommentAboveLine(lineNumberToContent)
//}

//
//fun addCommentAboveLine(lineNumberToContent: Map<String, MutableList<LineAndContent>>) {
//
//    lineNumberToContent.forEach { (k, v) ->
//        val lines = File(k).useLines { it.toMutableList() }
//
//        var linesAdded = 0
//        v.forEach { it ->
//
//
//            if (it.line >= 1 && it.line <= lines.size) {
//                val lineNumToAdd = it.line - 1 + linesAdded
//
//                // separate when it is already //> on line above, usual >, and > with number
//                if (lineNumToAdd > 0 && lines[lineNumToAdd - 1].startsWith("//>")) {
//                    // count how many values there already
//                    val countValues = lines[lineNumToAdd - 1].count { it == ',' } + 1
//                    // after this action there will be one more value
//                    val realCount = it.count - 1
//                    if (countValues > realCount) {
//                        val needToDrop = countValues - realCount
//                        val currentValues = lines[lineNumToAdd - 1]
//                            .split(", ")
//                            .drop(needToDrop).toMutableList()
//                        currentValues.add(it.content)
//                        val str = "//> ${currentValues.joinToString(", ")}"
//
//                        lines[lineNumToAdd - 1] = str
//                    } else {
//                        lines[lineNumToAdd - 1] += ", ${it.content}"
//                    }
//                } else {
//                    lines.add(lineNumToAdd, "//> ${it.content}")
//                    linesAdded++
//                }
//            } else {
//                throw Exception("Inline REPL System: Got line #${it.line} but all lines are only ${lines.size}")
//            }
//        }
//
//        val writer = BufferedWriter(FileWriter(k))
//        for (updatedLine in lines) {
//            writer.write(updatedLine)
//            writer.newLine()
//        }
//        writer.close()
//    }
//}
package frontend.util

import frontend.meta.TokenType
import frontend.parser.parsing.Parser
import frontend.parser.parsing.check
import frontend.parser.types.ast.InternalTypes
import java.lang.Error

fun String.capitalizeFirstLetter(): String {
    if (isEmpty()) {
        return this
    }
    val result = substring(0, 1).uppercase() + substring(1)
    return result
}

fun String.removeDoubleQuotes(): String = this.substring(1, this.count() - 1)

fun String.isSimpleTypes(): InternalTypes? {
    return when (this) {
        "int" -> InternalTypes.Int
        "bool" -> InternalTypes.Boolean
        "float" -> InternalTypes.Float
        "string" -> InternalTypes.String
        else -> null
    }
}

fun String.addIndentationForEachString(ident: Int): String {
    if (ident == 0) return this

    val realIdent = ident * 4
    val realIdentString = " ".repeat(realIdent)
    val splitted = this.split("\n")
    val lastElem = splitted.count() - 1
    return buildString {
        splitted.filter { it != "\n" }.forEachIndexed { i, it ->
                append(realIdentString, it)
            if (i != lastElem) append("\n")
        }
    }

}


fun Parser.checkTokUntilEndOfLine(tok: TokenType): Boolean {
    var c = 0
    do {
        if (check(tok, c)) {
            return true
        }
        c++

        val q = check(TokenType.EndOfLine, c)
        val w = check(TokenType.EndOfFile, c)

        println()
    } while (!(check(TokenType.EndOfLine, c) || check(TokenType.EndOfFile, c)))
    return false
}
enum class OS_Type {
    WINDOWS,
    LINUX,
    MAC
}

fun getOSType(): OS_Type {
    val osName = System.getProperty("os.name")
    return when {
        osName.contains("dows") -> OS_Type.WINDOWS
        osName.contains("nux") -> OS_Type.WINDOWS
        osName.contains("mac") -> OS_Type.WINDOWS
        else -> throw Error("Unknown OS: $osName")
    }
}


fun checkIfAllStringsEqual(list: List<String>): Boolean {
    if (list.isEmpty()) {
        return true // Если список пустой, считаем, что все элементы равны между собой
    }

    val firstElement = list[0]
    return list.all { it == firstElement }
}

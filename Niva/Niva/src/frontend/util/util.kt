package frontend.util

import frontend.meta.TokenType
import frontend.parser.parsing.Parser
import frontend.parser.parsing.check
import frontend.parser.types.ast.InternalTypes

fun List<String>.toCalmelCase(): String =
    this[0] + this.drop(1).map { it.capitalizeFirstLetter() }.joinToString("") { it }


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
        InternalTypes.Int.name -> InternalTypes.Int
        InternalTypes.Boolean.name -> InternalTypes.Boolean
        InternalTypes.Float.name -> InternalTypes.Float
        InternalTypes.String.name -> InternalTypes.String
        InternalTypes.Unit.name -> InternalTypes.Unit
        InternalTypes.Char.name -> InternalTypes.Char
        InternalTypes.Any.name -> InternalTypes.Any
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
    } while (!(check(TokenType.EndOfLine, c) || check(TokenType.EndOfFile, c)))
    return false
}

enum class CurrentOS {
    WINDOWS,
    LINUX,
    MAC
}

fun getOSType(): CurrentOS {
    val osName = System.getProperty("os.name").lowercase()
    return when {
        osName.contains("dows") -> CurrentOS.WINDOWS
        osName.contains("nux") -> CurrentOS.LINUX
        osName.contains("mac") -> CurrentOS.MAC
        else -> throw Error("Unknown OS: $osName")
    }
}

fun getOsPathSeparator() = when (getOSType()) {
    CurrentOS.WINDOWS -> "\\"
    CurrentOS.LINUX, CurrentOS.MAC -> "/"
}

operator fun String.div(arg: String) = this + getOsPathSeparator() + arg

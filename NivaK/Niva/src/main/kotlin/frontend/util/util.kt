package frontend.util

import frontend.meta.TokenType
import frontend.parser.parsing.Parser
import frontend.parser.parsing.check
import frontend.parser.types.ast.InternalTypes

fun String.capitalizeFirstLetter(): String {
    if (isEmpty()) {
        return this
    }
    val result = substring(0, 1).uppercase() + substring(1)
    return result
}

fun String.isSimpleTypes(): InternalTypes? {
    return when (this) {
        "int" -> InternalTypes.int
        "bool" -> InternalTypes.boolean
        "float" -> InternalTypes.float
        "string" -> InternalTypes.string
        else -> null
    }
}

fun String.addIdentationForEachString(ident: Int): String {
    val realIdent = ident * 4
    val realIdentString = " ".repeat(realIdent)
    val splitted = this.split("\n")
    return buildString {
        splitted.forEach {
            append(realIdentString, it, "\n")
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

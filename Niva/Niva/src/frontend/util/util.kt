package frontend.util

import frontend.meta.Position
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.parser.parsing.Parser
import frontend.parser.parsing.check
import frontend.parser.types.ast.InternalTypes
import frontend.resolver.TypeField
import frontend.resolver.compare2Types
import java.io.File

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

//fun getOsPathSeparator() = when (getOSType()) {
//    CurrentOS.WINDOWS -> "\\"
//    CurrentOS.LINUX, CurrentOS.MAC -> "/"
//}

operator fun String.div(arg: String) = buildString { append(this@div, "/", arg) }


fun <T> setDiff(x: Set<T>, y: Set<T>): Set<T> {
    if (x.count() == y.count() || x.count() > y.count())
        return x - y
    else (x.count() < y.count())
    return y - x
}


fun containSameFields(fields1: MutableList<TypeField>, fields2: MutableList<TypeField>): Boolean {
    if (fields1.count() != fields2.count()) return false

    fields1.forEachIndexed { i, t1 ->
        val t2 = fields2.find { compare2Types(t1.type, it.type) }
        if (t2 == null) return false
    }
    return true
}


private class FakeToken {
    companion object {
        val fakeToken = Token(
            TokenType.Identifier, "Fake Token", 0, Position(0, 1),
            Position(0, 1), File("Compiler Error")
        )
    }
}

fun createFakeToken(): Token = FakeToken.fakeToken

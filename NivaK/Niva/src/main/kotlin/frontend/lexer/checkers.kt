package frontend.lexer

fun String.isDigit(): Boolean {
    for (c in this) {
        if (!c.isDigit()) return false
    }
    return true
}

fun String.isAlphaNumeric(): Boolean {
    for (c in this) {
        if (!c.isLetterOrDigit()) return false
    }
    return true
}
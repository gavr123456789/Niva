package main.frontend.util

import frontend.resolver.TypeField
import frontend.resolver.compare2Types

//fun Parser.checkTokUntilEndOfLine(tok: TokenType): Boolean {
//    var c = 0
//    do {
//        if (check(tok, c)) {
//            return true
//        }
//        c++
//    } while (!(check(TokenType.EndOfLine, c) || check(TokenType.EndOfFile, c)))
//    return false
//}



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





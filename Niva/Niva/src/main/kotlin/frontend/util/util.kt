package main.frontend.util

import frontend.resolver.KeywordArg
import frontend.resolver.compare2Types
import main.frontend.meta.Token

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

fun childContainSameFieldsAsParent(
    fieldsChild: MutableList<KeywordArg>,
    fieldsParent: MutableList<KeywordArg>,
    token: Token
): Boolean {
    // child has fewer fields than parent, it needs at least same amount or more
    if (fieldsChild.count() < fieldsParent.count())
        return false

    // child must have each field of the parent
    // so we need walk throuth fields of the parent and check that child contains each of them
    fieldsParent.forEachIndexed { i, t1 ->
        val t2 = fieldsChild.find { compare2Types(t1.type, it.type, token) }
        if (t2 == null)
            return false
    }
    return true
}





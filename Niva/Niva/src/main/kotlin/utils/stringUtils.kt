package main.utils

import main.frontend.parser.types.ast.InternalTypes
import kotlin.collections.count
import kotlin.collections.drop
import kotlin.collections.filter
import kotlin.collections.forEachIndexed
import kotlin.collections.joinToString
import kotlin.collections.map
import kotlin.text.append
import kotlin.text.count
import kotlin.text.isEmpty
import kotlin.text.repeat
import kotlin.text.split
import kotlin.text.substring
import kotlin.text.uppercase


fun String.isGeneric() = count() == 1 && this[0].isUpperCase()





// experriments

object StringUtils {
    /**
     * Returns a minimal set of characters that have to be removed from (or added to) the respective
     * strings to make the strings equal.
     */
    fun diff(a: String, b: String): Pair<String> {
        return diffHelper(a, b, HashMap())
    }

    /**
     * Recursively compute a minimal set of characters while remembering already computed substrings.
     * Runs in O(n^2).
     */
    private fun diffHelper(a: String, b: String, lookup: MutableMap<Long, Pair<String>>): Pair<String> {
        val key = (a.length.toLong()) shl 32 or b.length.toLong()
        if (!lookup.containsKey(key)) {
            val value: Pair<String>
            if (a.isEmpty() || b.isEmpty()) {
                value = Pair(a, b)
            } else if (a[0] == b[0]) {
                value = diffHelper(a.substring(1), b.substring(1), lookup)
            } else {
                val aa = diffHelper(a.substring(1), b, lookup)
                val bb = diffHelper(a, b.substring(1), lookup)
                value = if (aa.first.length + aa.second.length < bb.first.length + bb.second.length) {
                    Pair(a[0].toString() + aa.first, aa.second)
                } else {
                    Pair(bb.first, b[0].toString() + bb.second)
                }
            }
            lookup[key] = value
        }
        return lookup[key]!!
    }

    class Pair<T>(val first: T, val second: T) {
        override fun toString(): String {
            return "($first,$second)"
        }
    }
}

fun List<String>.toCamelCase(): String =
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

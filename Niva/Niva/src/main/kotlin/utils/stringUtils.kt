package main.utils


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

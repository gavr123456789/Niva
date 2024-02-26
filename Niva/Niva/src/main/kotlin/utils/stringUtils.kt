package main.utils


fun String.isGeneric() = count() == 1 && this[0].isUpperCase()
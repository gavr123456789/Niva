package main.codogenjs

import frontend.resolver.Type

// Преобразуем типы в безопасные имена для функций JS
internal fun Type.toJsMangledName(): String = when (this) {
    is Type.NullableType -> this.realType.toJsMangledName() + "_opt"
    is Type.InternalType -> this.name
    is Type.UnresolvedType -> "Unresolved"
    is Type.Lambda -> "Fn__" + this.args.joinToString("__") { it.type.toJsMangledName() } + "__ret__" + this.returnType.toJsMangledName()
    is Type.UserLike -> buildString {
        // core/common пакеты опускаем
        if (this@toJsMangledName.pkg.isNotEmpty() && this@toJsMangledName.pkg != "core" && this@toJsMangledName.pkg != "common") {
            append(this@toJsMangledName.pkg.replace('.', '_').replace("::", "_"), "_")
        }
        append(this@toJsMangledName.emitName)
        if (this@toJsMangledName.typeArgumentList.isNotEmpty()) {
            append("__")
            append(this@toJsMangledName.typeArgumentList.joinToString("__") { it.toJsMangledName() })
        }
        if (this@toJsMangledName.isMutable) append("__mut")
    }
}

internal fun String.ifJsKeywordPrefix(): String = when (this) {
    "var", "let", "const", "function", "default", "class", "return", "new", "delete" -> "_$this"
    else -> this
}

internal fun String.addIndentationForEachStringJs(indent: Int): String {
    val pref = "    ".repeat(indent)
    val result = this.lines().joinToString("\n") { if (it.isEmpty()) it else pref + it }
    return result
}

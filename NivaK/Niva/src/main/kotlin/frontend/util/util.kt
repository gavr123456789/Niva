package frontend.util

import frontend.parser.types.InternalTypes

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

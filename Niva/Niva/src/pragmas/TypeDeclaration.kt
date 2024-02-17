package frontend.parser.types.ast

class KeyPragma(
    name: String,
    val value: Primary
) : Pragma(name)

sealed class Pragma(val name: String)
class SingleWordPragma(
    name: String,
) : Pragma(name)

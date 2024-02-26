package frontend.parser.types.ast

import main.frontend.parser.types.ast.Primary

class KeyPragma(
    name: String,
    val value: Primary
) : Pragma(name)

sealed class Pragma(val name: String)
class SingleWordPragma(
    name: String,
) : Pragma(name)

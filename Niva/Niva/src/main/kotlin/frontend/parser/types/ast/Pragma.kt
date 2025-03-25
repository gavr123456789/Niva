package frontend.parser.types.ast

import main.frontend.parser.types.ast.Primary

sealed class Pragma(val name: String)

class KeyPragma(
    name: String,
    val value: Primary
) : Pragma(name)


class SingleWordPragma(
    name: String,
) : Pragma(name)



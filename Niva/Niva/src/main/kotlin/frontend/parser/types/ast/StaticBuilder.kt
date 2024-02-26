package main.frontend.parser.types.ast

import frontend.resolver.Type
import main.frontend.meta.Token


class StaticBuilder(
    val statements: List<Statement>,
    defaultAction: CodeBlock? = null,
    type: Type?,
    token: Token,
) : Receiver(type, token)



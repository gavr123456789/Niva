package frontend.parser.types.ast

import frontend.meta.Token
import frontend.resolver.Type


class StaticBuilder(
    val statements: List<Statement>,
    defaultAction: CodeBlock? = null,
    type: Type?,
    token: Token,
) : Receiver(type, token)



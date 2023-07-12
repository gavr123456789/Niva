package frontend.parser.types.ast.toStr

import frontend.parser.types.ast.LiteralExpression


fun LiteralExpression.toNivaStr(): String {
    return this.str
}

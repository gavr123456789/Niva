package main.frontend.resolver.messageResolving

import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.addImport
import frontend.resolver.findPackageOrError
import frontend.resolver.getCurrentPackage
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.UnaryMsg
import main.frontend.parser.types.ast.UnaryMsgKind
import main.frontend.resolver.findAnyMethod

fun Resolver.resolveUnaryAliasLambda(statement: UnaryMsg, receiverType: Type.Lambda, receiverToken: Token): Type {
    val errorTok = statement.token
    val lambdaType = typeDB.lambdaTypes[receiverType.alias]
        ?: receiverToken.compileError("Can't find type alias for lambda ${receiverType.alias}::$receiverType")
    val receiverPkg = findPackageOrError(receiverType.pkg, errorTok)
    val s = findAnyMethod(lambdaType, statement.selectorName, receiverPkg, MessageDeclarationType.Unary)
        ?: errorTok.compileError("Can't find message ${statement.selectorName} for type alias: ${receiverType.alias}")

    statement.apply {
        type = s.returnType
        type?.errors = s.errors
        pragmas = s.pragmas
        declaration = s.declaration
        kind = UnaryMsgKind.Unary
    }


    getCurrentPackage(errorTok).addImport(s.pkg)

    return s.returnType
}

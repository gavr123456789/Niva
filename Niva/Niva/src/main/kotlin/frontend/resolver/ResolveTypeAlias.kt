package frontend.resolver

import main.frontend.parser.types.ast.TypeAST
import main.frontend.parser.types.ast.TypeAliasDeclaration

fun Resolver.resolveTypeAlias(statement: TypeAliasDeclaration) {
    if (statement.realTypeAST !is TypeAST.Lambda && typeTable[statement.realTypeAST.name] == null) {
        unResolvedTypeDeclarations.add(currentPackageName, statement)
        return
    }

    val realType = statement.realTypeAST.toType(typeDB, typeTable, customPkg = currentPackageName)
    statement.realType = realType
    if (realType is Type.Lambda) {
        realType.alias = statement.typeName
//        realType.pkg = currentPackageName
    }

    addNewType(realType, statement, alias = true, alreadyCheckedOnUnique = false)
}
package main.frontend.typer

import frontend.meta.compileError
import frontend.parser.types.ast.Receiver
import frontend.parser.types.ast.TypeAST
import frontend.parser.types.ast.VarDeclaration
import frontend.resolver.*
import frontend.resolver.Type.RecursiveType.copy
import main.RED
import main.WHITE
import main.YEL
import main.utils.isGeneric

fun Resolver.resolveVarDeclaration(
    statement: VarDeclaration,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>
) {
    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
    // currentNode, depth + 1
    currentLevel++
    resolve(listOf(statement.value), previousAndCurrentScope, statement)
    currentLevel--
    val value = statement.value
    val valueType = value.type
        ?: statement.token.compileError("In var declaration $WHITE${statement.name}$RED value doesn't got type")
    val statementDeclaredType = statement.valueTypeAst

    // generics in right part, but real type in left, x::List::Int = List
    var copyType: Type? = null
    if (value is Receiver &&
        valueType is Type.UserType &&
        valueType.typeArgumentList.find { it.name.isGeneric() } != null &&
        statementDeclaredType is TypeAST.UserType && statementDeclaredType.typeArgumentList.find { it.name.isGeneric() } == null
    ) {
        copyType = valueType.copy()

        if (statementDeclaredType.typeArgumentList.count() != copyType.typeArgumentList.count()) {
            statement.token.compileError(
                "Not all generic type are presented, statement has ${
                    statementDeclaredType.typeArgumentList.joinToString(
                        ", "
                    ) { it.name }
                } but type has ${copyType.typeArgumentList.joinToString(", ") { it.name }}"
            )
        }
        val newTypeArgList: MutableList<Type> = mutableListOf()
        statementDeclaredType.typeArgumentList.forEachIndexed { i, typeAST ->
            // find every type of statement and replace it in type

            val e = typeDB.getTypeOfIdentifierReceiver(
                typeAST.name,
                value,
                getCurrentImports(statement.token),
                currentPackageName
            ) ?: typeAST.token.compileError("Can't find type $YEL${typeAST.name}")

            e.beforeGenericResolvedName = copyType.typeArgumentList[i].name
            newTypeArgList.add(e)
        }
        copyType.typeArgumentList = newTypeArgList
        value.type = copyType
    }
//    if (value is Receiver && (valueType is Type.UserLike && valueType.typeArgumentList.isNotEmpty() || valueType is Type.NullableType && valueType.realType is Type.UserLike && valueType.realType.typeArgumentList.isNotEmpty())) {
//        val type = if (valueType is Type.NullableType) valueType.realType else valueType
//        val letterTable = mutableMapOf<String, Type>()
//
//
//        recursiveGenericResolving(type as Type.UserType, letterTable, mutableMapOf())
//        // take args from value.receiver.type.typeArgList
//    }

    // check that declared type == inferred type
    if (statementDeclaredType != null) {
        val statementDeclared = statementDeclaredType.toType(typeDB, typeTable)
        if (!compare2Types(statementDeclared, valueType)) {
            val text = "${statementDeclaredType.name} != ${valueType.name}"
            statement.token.compileError("Type declared for ${YEL}${statement.name}$RED is not equal for it's value type ${YEL}`$text`")
        }
    }

    currentScope[statement.name] = copyType ?: valueType
    if (currentLevel == 0) {
        topLevelStatements.add(statement)
    }
}



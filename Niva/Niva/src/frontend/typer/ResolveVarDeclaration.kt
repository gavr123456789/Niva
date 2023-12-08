package main.frontend.typer

import frontend.meta.compileError
import frontend.parser.types.ast.Receiver
import frontend.parser.types.ast.TypeAST
import frontend.parser.types.ast.VarDeclaration
import frontend.typer.*

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
        ?: statement.token.compileError("In var declaration ${statement.name} value doesn't got type")
    val statementDeclaredType = statement.valueType

    // generics in right part, but real type in left, x::List::Int = List
    var copyType: Type? = null
    if (value is Receiver &&
        valueType is Type.UserType &&
        valueType.typeArgumentList.find { it.name.length == 1 && it.name[0].isUpperCase() } != null &&
        statementDeclaredType is TypeAST.UserType && statementDeclaredType.typeArgumentList.find { it.name.length == 1 && it.name[0].isUpperCase() } == null
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
            ) ?: typeAST.token.compileError("Cant find type ${typeAST.name}")

            e.beforeGenericResolvedName = copyType.typeArgumentList[i].name
            newTypeArgList.add(e)
        }
        copyType.typeArgumentList = newTypeArgList
        value.type = copyType
    }

    // check that declared type == inferred type
    if (statementDeclaredType != null) {
        if (statementDeclaredType.name != valueType.name) {
            val text = "${statementDeclaredType.name} != ${valueType.name}"

            statement.token.compileError("Type declared for ${statement.name} is not equal for it's value type `$text`")
        }
    }

    currentScope[statement.name] = copyType ?: valueType

    if (currentLevel == 0) {
        topLevelStatements.add(statement)
    }
}

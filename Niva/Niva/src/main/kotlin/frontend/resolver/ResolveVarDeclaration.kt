package main.frontend.typer

import frontend.resolver.*
import frontend.resolver.Type.RecursiveType.copy
import main.utils.RED
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.utils.isGeneric

fun Resolver.resolveVarDeclaration(
    statement: VarDeclaration,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
) {
    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
    // currentNode, depth + 1
    currentLevel++
    resolveSingle((statement.value), previousAndCurrentScope, statement)
    currentLevel--


    val valueOfVarDecl = statement.value
    var typeOfValueInVarDecl = valueOfVarDecl.type
        ?: statement.token.compileError("Compiler BUG: In var declaration $WHITE${statement.name}$RED value doesn't got type")
    val definedASTType = statement.valueTypeAst

    if (valueOfVarDecl is MessageSendKeyword) {
        val first = valueOfVarDecl.messages.first()
        if (first is KeywordMsg && first.kind == KeywordLikeType.Setter) {
            statement.token.compileError("Don't use setter as expression, it doesn't returns anything.\n       Maybe you wanted to use update and copy, then remove ${RED}mut$RESET modifier from type declaration")
        }
    }

    // generics in right part, but real type in left, x::List::Int = List
    var copyType: Type? = null
    if (valueOfVarDecl is Receiver &&
        typeOfValueInVarDecl is Type.UserType &&
        typeOfValueInVarDecl.typeArgumentList.find { it.name.isGeneric() } != null &&
        definedASTType is TypeAST.UserType && definedASTType.typeArgumentList.find { it.name.isGeneric() } == null
    ) {
        copyType = typeOfValueInVarDecl.copy()

        if (definedASTType.typeArgumentList.count() != copyType.typeArgumentList.count()) {
            statement.token.compileError(
                "Not all generic type are presented, statement has ${
                    definedASTType.typeArgumentList.joinToString(
                        ", "
                    ) { it.name }
                } but type has ${copyType.typeArgumentList.joinToString(", ") { it.name }}"
            )
        }
        val newTypeArgList: MutableList<Type> = mutableListOf()
        definedASTType.typeArgumentList.forEachIndexed { i, typeAST ->
            // find every type of statement and replace it in type

            val e = typeDB.getTypeOfIdentifierReceiver(
                typeAST.name,
                valueOfVarDecl,
                getCurrentImports(statement.token),
                currentPackageName
            ) ?: typeAST.token.compileError("Can't find type $YEL${typeAST.name}")

            e.beforeGenericResolvedName = copyType.typeArgumentList[i].name
            newTypeArgList.add(e)
        }
        copyType.typeArgumentList = newTypeArgList
        valueOfVarDecl.type = copyType
    }

    // check that declared type == inferred type
    if (definedASTType != null) {
        val statementDeclared = definedASTType.toType(typeDB, typeTable)
        val rightPartType = typeOfValueInVarDecl
        if (!compare2Types(statementDeclared, rightPartType, unpackNull = true)) {
            val text = "$definedASTType != $rightPartType"
            statement.token.compileError("Type declared for ${YEL}${statement.name}$RESET is not equal for it's value type ${YEL}$text")
        }

        typeOfValueInVarDecl = statementDeclared


        // if x::Int? = 42 then we need to wrap type to Nullable
        if (definedASTType.isNullable && rightPartType !is Type.NullableType) {
            val nullableType = Type.NullableType(rightPartType)
            valueOfVarDecl.type = nullableType
            typeOfValueInVarDecl = nullableType
        }
        // x::Sas = [x::Int -> x inc]
        // right part here doesn't contain information about alias
//        if (valueType is Type.Lambda && statementDeclared is Type.Lambda && statementDeclared.alias != null) {
//            valueType.alias = statementDeclared.alias
//        }

    }

    currentScope[statement.name] = copyType ?: typeOfValueInVarDecl
    addToTopLevelStatements(statement)
}



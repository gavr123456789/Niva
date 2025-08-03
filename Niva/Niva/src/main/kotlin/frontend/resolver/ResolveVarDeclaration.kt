package main.frontend.typer

import frontend.resolver.*
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.utils.RED
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.utils.isGeneric


// x = {} // ERROR need x::List::Int = {}
fun checkThatCollectionIsTyped(statement: VarDeclaration) {
    if (statement.valueTypeAst != null) return
    val value = statement.value
    val emptyMap = value is MapCollection && value.initElements.isEmpty()
    val emptyList = value is CollectionAst && value.initElements.isEmpty()
    if (emptyMap || emptyList) {
        statement.token.compileError("(x::mut List::Int = {})\nCan't infer type of empty collection, please specify it like x::MutableList::Int = {}")
    }
}

fun replaceCollectionWithMutable(name: String) = when(name) {
    "List" -> "MutableList"
    "Set" -> "MutableSet"
    "Map" -> "MutableMap"
    else -> name
}

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
    // check that collection is typed
    checkThatCollectionIsTyped(statement)
    val definedASTType = statement.valueTypeAst

    if (valueOfVarDecl is MessageSendKeyword) {
        val first = valueOfVarDecl.messages.first()
        if (first is KeywordMsg && first.kind == KeywordLikeType.Setter) {
            statement.token.compileError("Don't use setter as expression, it doesn't returns anything.\n       Maybe you wanted to use update and copy, then remove ${RED}mut$RESET modifier from type declaration")
        }
    }

    // generics in right part, but real type in left, x::List::Int = List::T
    var copyType: Type? = null
    if (
        definedASTType is TypeAST.UserType && definedASTType.typeArgumentList.find { it.name.isGeneric() } == null &&
        valueOfVarDecl is Receiver &&
        typeOfValueInVarDecl is Type.UserLike &&
        typeOfValueInVarDecl.typeArgumentList.find { it.name.isGeneric() } != null
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
//        copyType.typeArgumentList = newTypeArgList
        copyType.replaceTypeArguments(newTypeArgList)
        valueOfVarDecl.type = copyType

        if (valueOfVarDecl is MessageSend) {
            val tt = valueOfVarDecl.receiver.type
            if (tt is Type.UserLike && tt.typeArgumentList.count() == newTypeArgList.count()) {
//                tt.typeArgumentList = newTypeArgList
                tt.replaceTypeArguments(newTypeArgList)
            }
        }
    }

    // check that declared type == inferred type
    if (definedASTType != null) {
        val statementDeclared = definedASTType.toType(typeDB, typeTable)
        val rightPartType = typeOfValueInVarDecl
        if (!compare2Types(statementDeclared, rightPartType, statement.token, unpackNull = false, compareMutability = false)) {
            val text = "$definedASTType != $rightPartType"
            statement.token.compileError("Type declared for ${YEL}${statement.name}$RESET is not equal for it's value type ${YEL}$text")
        }

        // check that its making mutable something that already exist and immutable
        if (statementDeclared.isMutable && !rightPartType.isMutable
            && valueOfVarDecl is MessageSend && valueOfVarDecl.messages.isNotEmpty())
        {
            val f = valueOfVarDecl.messages.first()
            val errorText = "Cant create mutable from already existing value, please use the constructor or immutable copy"
            when (f) {
                is KeywordMsg -> {
                    if (f.kind != KeywordLikeType.Constructor &&
                        f.kind != KeywordLikeType.CustomConstructor &&
                        f.kind != KeywordLikeType.SetterImmutableCopy)
                    {
                        statement.token.compileError(errorText)
                    }
                }
                is UnaryMsg -> {
                    if (f.kind == UnaryMsgKind.Getter) {
                        statement.token.compileError(errorText)
                    }

                }
                else -> {}
            }

        }

        typeOfValueInVarDecl = statementDeclared

        // if x::Int? = 42 then we need to wrap type to Nullable
        if (definedASTType.isNullable && rightPartType !is Type.NullableType) {
            val nullableType = Type.NullableType(rightPartType)
            valueOfVarDecl.type = nullableType
            typeOfValueInVarDecl = nullableType
        }

        // if value is a collection then assign correct mutability to it
        if (definedASTType.isMutable) {

            if (valueOfVarDecl is CollectionAst) {
                valueOfVarDecl.isMutableCollection = true
            } else if (valueOfVarDecl is MapCollection) {
                valueOfVarDecl.isMutable = true
            }
            if (typeOfValueInVarDecl is Type.UserLike) {
                typeOfValueInVarDecl = typeOfValueInVarDecl.copy()
                typeOfValueInVarDecl.emitName = replaceCollectionWithMutable(typeOfValueInVarDecl.emitName)
                typeOfValueInVarDecl.isMutable = true
                if (copyType != null && copyType is Type.UserLike) {
                    copyType.isMutable = true
                    copyType.emitName = typeOfValueInVarDecl.emitName
                }
            }
        }

    }
    currentScope[statement.name] = copyType ?: typeOfValueInVarDecl

    // DevMode
//    devModeSetInlineRepl(valueOfVarDecl, resolvingMessageDeclaration)

    getCurrentPackage(statement.token).addImport(typeOfValueInVarDecl.pkg)
    addToTopLevelStatements(statement)
}


fun Resolver.resolveDestruction(
    statement: DestructingAssign,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>,
) {
    // check that value has this fields
    // {name age} = person

    val resolveValue = {
        currentLevel++
        val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
        resolveSingle((statement.value), previousAndCurrentScope, statement)
        currentLevel--
    }()



    val type = statement.value.type
    val valueTok = statement.value.token

    if (type == null)
        valueTok.compileError("Compiler bug: value of destruct assign not resolved")

    if (type !is Type.UserLike)
        valueTok.compileError("${statement.value} doesn't has fields")

    if (statement.names.count() > type.fields.count())
        valueTok.compileError("Destructing ${statement.names.count()} fields, but $type has only ${type.fields}")

    statement.names.forEachIndexed { i, name ->
        // check that there are such name
        val field = type.fields.find { it.name == name.name }
        if (field == null)
            name.token.compileError("There is no $name field in $type, fields: \n${type.fields.joinToString("\n")}")
        // assign type of that name
        name.type = field.type
        // add to scope
        currentScope[name.name] = field.type
    }


}



package frontend.resolver

import main.utils.CYAN
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*

fun Resolver.setTypeForCollection(typeArgumentList: MutableList<Type>, statement2: Receiver, collectionTypeName: String): Type.UserType {
    val listType =
        this.projects[currentProjectName]!!.packages["core"]!!.types[collectionTypeName] as Type.UserType

    val collectionType = Type.UserType(
        name = collectionTypeName,
        typeArgumentList = typeArgumentList,
        fields = mutableListOf(),
        pkg = "core",
        protocols = listType.protocols,
        typeDeclaration = null
    ).also {

        if (statement2 is CollectionAst && statement2.isMutableCollection)
            it.isMutable = true
        else if (statement2 is MapCollection && statement2.isMutable)
            it.isMutable = true
    }
    statement2.type = collectionType
    return collectionType
}
fun Resolver.resolveCollection(
    statement: CollectionAst,
    typeName: String,
    previousAndCurrentScope: MutableMap<String, Type>,
) {
    val nearVarDecl = findNearestVarDeclInStack()
    val nearVarDeclType = nearVarDecl?.valueTypeAst
    if (statement.initElements.isNotEmpty()) {
        // resolve args
        statement.initElements.forEach {
            if (it is Primary && it.typeAST != null) {
                it.type = it.typeAST.toType(typeDB, typeTable)
            } else {
                currentLevel++
                resolveSingle(it, previousAndCurrentScope, statement)
                currentLevel--
            }
        }

        val firstElem = statement.initElements[0]
        var firstElemType = firstElem.type
        if (firstElemType != null) {

//            firstElemType.beforeGenericResolvedName = "T" // Default List has T type
            firstElemType = firstElemType.cloneAndChangeBeforeGeneric("T")
            // set Any type of collection, if {1, "sas"}
            val anyType = if (statement.initElements.count() > 1) {
                val argTypesNames = statement.initElements.map { it.type?.name }.toSet()
                if (argTypesNames.count() > 1)
                    Resolver.defaultTypes[InternalTypes.Any]
                else null
            } else null

            // try to find list with the same generic type
            setTypeForCollection(mutableListOf(anyType ?: firstElemType), statement, typeName)

//            firstElemType.beforeGenericResolvedName = null
        } else {
            statement.token.compileError("Compiler bug: Can't get type of elements of list literal")
        }
    } else if (nearVarDecl != null && nearVarDeclType != null && isCollection(nearVarDeclType.name)) {
        // check that literal type is same as var declared type
        val typeFromAstDecl = nearVarDeclType.toType(typeDB, typeTable)
        if (typeFromAstDecl is Type.UserLike) {
            if (typeName != typeFromAstDecl.name) {
                statement.token.compileError("Declared type of collection: $typeFromAstDecl but literal used for $typeName")
            }
            val pkg = getCurrentPackage(statement.token)
            typeFromAstDecl.typeArgumentList.forEach {
                pkg.addImport(it.pkg)
            }
        }

        statement.type = typeFromAstDecl
    } else {
        // check if this collection is inside a collection constructor like List(Int) val: {}
        val nearConstructor = findNearestCollectionConstructorInStack()
        if (nearConstructor != null) {
            // use type arguments from constructor
            val constructorType = when (val receiver = nearConstructor.receiver) {
                is IdentifierExpr -> receiver.type as? Type.UserLike
                    ?: (receiver.typeAST as? TypeAST.UserType)?.toType(typeDB, typeTable, resolver = this) as? Type.UserLike
                else -> nearConstructor.receiver.type as? Type.UserLike
            }
            val genericTypes = constructorType?.typeArgumentList
            if (!genericTypes.isNullOrEmpty()) {
                setTypeForCollection(genericTypes.toMutableList(), statement, typeName)
                return
            }
        }

        setTypeForCollection(mutableListOf(Type.UnknownGenericType("T")), statement, typeName)
    }

}

fun Resolver.findNearestVarDeclInStack(): VarDeclaration? =
    stack.reversed().find { it is VarDeclaration } as VarDeclaration?

fun Resolver.findNearestCodeBlockInStack(): CodeBlock? =
    stack.reversed().find { it is CodeBlock } as CodeBlock?

fun Resolver.findNearestCollectionConstructorInStack(): KeywordMsg? {
    val kwMsg = stack.reversed().find { it is KeywordMsg } as? KeywordMsg ?: return null
    // check if this is a collection constructor like List(Int) val: {}
    val receiver = kwMsg.receiver
    val receiverType = receiver.type as? Type.UserLike
    val receiverTypeAst = (receiver as? IdentifierExpr)?.typeAST as? TypeAST.UserType
    val receiverName = receiverType?.name ?: receiverTypeAst?.name ?: return null
    val lastArg = kwMsg.args.lastOrNull()?.keywordArg ?: return null
    val isCollectionConstructor = kwMsg.kind == KeywordLikeType.Constructor &&
        isCollection(receiverName) &&
        (receiverType?.fields?.isEmpty() ?: true) &&
        (lastArg is CollectionAst || lastArg is MapCollection)
    return if (isCollectionConstructor) kwMsg else null
}

fun Resolver.resolveSet(
    statement: SetCollection,
    previousAndCurrentScope: MutableMap<String, Type>,
) {
    val collectionName = "Set"//if(!statement.isMutable) "Set" else "MutableSet"

    resolveCollection(statement, collectionName, previousAndCurrentScope)

    for (i in 0 until statement.initElements.count() - 1) {
        for (j in i + 1 until statement.initElements.count()) {
            if (statement.initElements[i] is Primary) {
                val first = statement.initElements[i].toString()
                val second = statement.initElements[j].toString()
                if (first == second) {
                    println("${YEL}Warning: set contains the same element: $WHITE$first$RESET")
                }
            }
        }
    }

}

fun Resolver.resolveMap(
    statement: MapCollection,
    rootStatement: Statement?,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>
): Type {
    val type = resolveMap2(statement, rootStatement, previousScope, currentScope)
    val realType = if (statement.isMutable) type.copyAnyType().also { it.isMutable = true } else type

    statement.type = realType
    return realType
}

fun Resolver.resolveMap2(
    statement: MapCollection,
    rootStatement: Statement?,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>
): Type {
    // get type of the first key
    // get type of the first value
    if (statement.initElements.isEmpty() && (rootStatement is VarDeclaration && rootStatement.valueTypeAst != null)) {
        val type = rootStatement.valueTypeAst!!.toType(typeDB, typeTable)
        if (type.name != "Map") {
            statement.token.compileError("Expected $type but u used empty Map literal, {} - list, #() - set")
        }
        return type
    }
//    val collectionName = "Map"//if(!statement.isMutable) "Map" else "MutableMap"
    if (statement.initElements.isEmpty()) {
        // check if this map is inside a collection constructor like Map(Int, String) val: #{}
        val nearConstructor = findNearestCollectionConstructorInStack()
        if (nearConstructor != null) {
            // use type arguments from constructor
            val constructorType = when (val receiver = nearConstructor.receiver) {
                is IdentifierExpr -> receiver.type as? Type.UserLike
                    ?: (receiver.typeAST as? TypeAST.UserType)?.toType(typeDB, typeTable, resolver = this) as? Type.UserLike
                else -> nearConstructor.receiver.type as? Type.UserLike
            }
            val genericTypes = constructorType?.typeArgumentList
            if (genericTypes != null && genericTypes.size >= 2) {
                return setTypeForCollection(genericTypes.toMutableList(), statement, "Map")
            }
        }

        return setTypeForCollection(mutableListOf(Type.UnknownGenericType("T"), Type.UnknownGenericType("G")), statement, "Map")
    }
    val (key, value) = statement.initElements[0]
    currentLevel++
    statement.initElements.forEach { (key, value) ->
        resolveSingle((key), (currentScope + previousScope).toMutableMap(), statement)
        resolveSingle((value), (currentScope + previousScope).toMutableMap(), statement)

    }
    currentLevel--

    val keyType = key.type ?: key.token.compileError("Can't resolve type of key: ${CYAN}${key.str}")
    val valueType = value.type ?: value.token.compileError("Can't resolve type of value: ${WHITE}${value.str}")

    val mapTypeFromDb =
        this.projects["common"]!!.packages["core"]!!.types["Map"] as Type.UserType

    val unifiedValueType = if (statement.initElements.count() > 1) {
        val type1 = statement.initElements[0].second.type!!
        val type2 = statement.initElements[1].second.type!!
        val unified = findGeneralRoot(type1, type2)
        unified ?:
        Resolver.defaultTypes[InternalTypes.Any]!!
    } else
        valueType

    val mapType = Type.UserType(
        name = mapTypeFromDb.name,
        typeArgumentList = mutableListOf(keyType, unifiedValueType),
        fields = mutableListOf(),
        pkg = "core",
        protocols = mapTypeFromDb.protocols,
        typeDeclaration = null
    )

    return mapType
}

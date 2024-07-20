package frontend.resolver

import main.utils.CYAN
import main.utils.RESET
import main.utils.WHITE
import main.utils.YEL
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*

fun Resolver.fillCollectionType(typeArgumentList: List<Type>, statement2: Receiver, collectionTypeName: String) {
    val listType =
        this.projects[currentProjectName]!!.packages["core"]!!.types[collectionTypeName] as Type.UserType

    val collectionType = Type.UserType(
        name = collectionTypeName,
        typeArgumentList = typeArgumentList,
        fields = mutableListOf(),
        pkg = "core",
        protocols = listType.protocols,
        typeDeclaration = null
    )
    statement2.type = collectionType
}

fun Resolver.resolveCollection(
    statement: CollectionAst,
    typeName: String,
    previousAndCurrentScope: MutableMap<String, Type>,
    rootStatement: Statement?
) {


    if (statement.initElements.isNotEmpty()) {
        // resolve args
        statement.initElements.forEach {
            if (it is Primary && it.typeAST != null) {
                it.type = it.typeAST.toType(typeDB, typeTable)
            } else {
                currentLevel++
                resolveSingle(it, previousAndCurrentScope, statement)
//                if (it.type == null) it.token.compileError("Compiler bug: Can't infer type of $it")
                currentLevel--
            }
        }

        val firstElem = statement.initElements[0]
        val firstElemType = firstElem.type
        if (firstElemType != null) {
            firstElemType.beforeGenericResolvedName = "T" // Default List has T type

            // set Any type of collection, if {1, "sas"}
            val anyType = if (statement.initElements.count() > 1) {
                val argTypesNames = statement.initElements.map { it.type?.name }.toSet()
                if (argTypesNames.count() > 1)
                    Resolver.defaultTypes[InternalTypes.Any]
                else null
            } else null

//            val anyType2: Type.InternalType? = null
//            val firstElemType2: Type? = null
            // try to find list with the same generic type
            fillCollectionType(listOf(anyType ?: firstElemType), statement, typeName)

        } else {
            statement.token.compileError("Compiler bug: Can't get type of elements of list literal")
        }
    } else if (rootStatement is VarDeclaration && rootStatement.valueTypeAst != null) {
        val type = rootStatement.valueTypeAst!!.toType(typeDB, typeTable)//fix
        statement.type = type
    }
    // empty collection assigned to keyword argument
//    else if (rootStatement is KeywordMsg && currentArgumentNumber != -1) {
//        fillCollectionType(listOf(Type.UnknownGenericType("T")), statement)
//
//    }
    else {
        fillCollectionType(listOf(Type.UnknownGenericType("T")), statement, typeName)
    }

}


fun Resolver.resolveSet(
    statement: SetCollection,
    previousAndCurrentScope: MutableMap<String, Type>,
    rootStatement: Statement?,
) {
    resolveCollection(statement, "MutableSet", previousAndCurrentScope, rootStatement)

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
) {
    // get type of the first key
    // get type of the first value
    if (statement.initElements.isEmpty() && (rootStatement is VarDeclaration && rootStatement.valueTypeAst != null)) {
        val type = rootStatement.valueTypeAst!!.toType(typeDB, typeTable)//fix
        statement.type = type
        return
    }

    if (statement.initElements.isEmpty() && (rootStatement is VarDeclaration && rootStatement.valueTypeAst != null)) {
        val type = rootStatement.valueTypeAst!!.toType(typeDB, typeTable)//fix
        statement.type = type
        return
    }
    if (statement.initElements.isEmpty()) {

        fillCollectionType(listOf(Type.UnknownGenericType("T"), Type.UnknownGenericType("G")), statement, "MutableMap")
        return
    }
    val (key, value) = statement.initElements[0]
    currentLevel++
    resolveSingle((key), (currentScope + previousScope).toMutableMap(), statement)
    resolveSingle((value), (currentScope + previousScope).toMutableMap(), statement)
    currentLevel--

    val keyType = key.type ?: key.token.compileError("Can't resolve type of key: ${CYAN}${key.str}")
    val valueType = value.type ?: value.token.compileError("Can't resolve type of value: ${WHITE}${value.str}")

    val mapTypeFromDb =
        this.projects["common"]!!.packages["core"]!!.types["MutableMap"] as Type.UserType

    val mapType = Type.UserType(
        name = "MutableMap",
        typeArgumentList = listOf(keyType, valueType),
        fields = mutableListOf(),
        pkg = "core",
        protocols = mapTypeFromDb.protocols,
        typeDeclaration = null
    )
    statement.type = mapType
}

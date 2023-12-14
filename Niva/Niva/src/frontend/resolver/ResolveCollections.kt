package frontend.resolver

import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.parser.types.ast.Collection
import main.CYAN
import main.WHITE
import main.YEL


fun Resolver.resolveCollection(
    statement: Collection, typeName: String,
    previousAndCurrentScope: MutableMap<String, Type>,
    rootStatement: Statement?
) {
    if (statement.initElements.isNotEmpty()) {
        // resolve args
        statement.initElements.forEach {
            if (it.typeAST != null) {
                it.type = it.typeAST.toType(typeDB, typeTable)
            } else {
                currentLevel++
                resolve(listOf(it), previousAndCurrentScope, statement)
                currentLevel--
            }
        }

        val firstElem = statement.initElements[0]
        if (firstElem.typeAST != null) {
            val firstElemType = firstElem.typeAST.toType(typeDB, typeTable)//fix
            firstElemType.beforeGenericResolvedName = "T" // Default List has T type
            val listType =
                this.projects["common"]!!.packages["core"]!!.types[typeName] as Type.UserType


            // infer Any type of collection
            val anyType = if (statement.initElements.count() > 1) {
                val argTypesNames = statement.initElements.map { it.type?.name }.toSet()
                if (argTypesNames.count() > 1)
                    Resolver.defaultTypes[InternalTypes.Any]
                else null
            } else null

            // try to find list with the same generic type
            val listProtocols = listType.protocols
            val collectionType = Type.UserType( // alreadyExistsListType ?:
                name = typeName,
                typeArgumentList = listOf(anyType ?: firstElemType),
                fields = mutableListOf(),
                pkg = currentPackageName,
                protocols = listProtocols
            )

            statement.type = collectionType



        } else {
            statement.token.compileError("Cant get type of elements of list literal")
        }
    } else if (rootStatement is VarDeclaration && rootStatement.valueType != null) {
        val type = rootStatement.valueType!!.toType(typeDB, typeTable)//fix
        statement.type = type
    }

}


fun Resolver.resolveSet(
    statement: SetCollection,
    previousAndCurrentScope: MutableMap<String, Type>,
    rootStatement: Statement?,
) {
    resolveCollection(statement, "MutableSet", previousAndCurrentScope,rootStatement)

    for (i in 0 until statement.initElements.count() - 1) {
        for (j in i + 1 until statement.initElements.count()) {
            val first = statement.initElements[i].token.lexeme
            val second = statement.initElements[j].token.lexeme
            if (first == second) {
                println("${YEL}Warning: set contains the same element: $WHITE$first")
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
    if (statement.initElements.isEmpty() && (rootStatement is VarDeclaration && rootStatement.valueType != null)) {
        val type = rootStatement.valueType!!.toType(typeDB, typeTable)//fix
        statement.type = type
        return
    }
    val (key, value) = statement.initElements[0]
    currentLevel++
    resolve(listOf(key), (currentScope + previousScope).toMutableMap(), statement)
    resolve(listOf(value), (currentScope + previousScope).toMutableMap(), statement)
    currentLevel--

    val keyType = key.type ?: key.token.compileError("Can't resolve type of key: ${CYAN}${key.str}")
    val valueType = value.type ?: value.token.compileError("Can't resolve type of value: ${WHITE}${value.str}")

    val mapTypeFromDb =
        this.projects["common"]!!.packages["core"]!!.types["MutableMap"] as Type.UserType
    val listTypeFromDb =
        this.projects["common"]!!.packages["core"]!!.types["MutableList"] as Type.UserType

    val listTypeOfValues = Type.UserType(
        name = "MutableList",
        typeArgumentList = listOf(valueType),
        fields = mutableListOf(),
        pkg = currentPackageName,
        protocols = listTypeFromDb.protocols
    )
    val listTypeOfKeys = Type.UserType(
        name = "MutableList",
        typeArgumentList = listOf(keyType),
        fields = mutableListOf(),
        pkg = currentPackageName,
        protocols = listTypeFromDb.protocols
    )

    val mapType = Type.UserType(
        name = "MutableMap",
        typeArgumentList = listOf(keyType, valueType),
        fields = mutableListOf(
            TypeField("values", listTypeOfValues),
            TypeField("keys", listTypeOfKeys)
        ),
        pkg = currentPackageName,
        protocols = mapTypeFromDb.protocols
    )
    statement.type = mapType
}

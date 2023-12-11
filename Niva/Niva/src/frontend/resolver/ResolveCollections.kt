package main.frontend.typer

import frontend.meta.compileError
import frontend.parser.types.ast.*
import frontend.resolver.*

fun Resolver.resolveList(statement: ListCollection, rootStatement: Statement?) {
    if (statement.initElements.isNotEmpty()) {
        val firstElem = statement.initElements[0]
        if (firstElem.typeAST != null) {
            val firstElemType = firstElem.typeAST.toType(typeDB, typeTable)//fix
            firstElemType.beforeGenericResolvedName = "T" // Default List has T type
            val listType =
                this.projects["common"]!!.packages["core"]!!.types["MutableList"] as Type.UserType

            // try to find list with the same generic type
            val typeName = "MutableList"

            val listProtocols = listType.protocols

            val genericType = Type.UserType( // alreadyExistsListType ?:
                name = typeName,
                typeArgumentList = listOf(firstElemType),
                fields = mutableListOf(),
                pkg = currentPackageName,
                protocols = listProtocols
            )

            statement.type = genericType
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
    rootStatement: Statement?,
) {
    if (statement.initElements.isNotEmpty()) {
        val firstElem = statement.initElements[0]
        if (firstElem.typeAST != null) {
            val firstElemType = firstElem.typeAST.toType(typeDB, typeTable)//fix
            firstElemType.beforeGenericResolvedName = "T" // Default List has T type
            val listType =
                this.projects["common"]!!.packages["core"]!!.types["MutableSet"] as Type.UserType

            // try to find list with the same generic type
            val typeName = "MutableSet"

            val listProtocols = listType.protocols

            val genericType = Type.UserType( // alreadyExistsListType ?:
                name = typeName,
                typeArgumentList = listOf(firstElemType),
                fields = mutableListOf(),
                pkg = currentPackageName,
                protocols = listProtocols
            )

            statement.type = genericType
        } else {
            statement.token.compileError("Cant get type of elements of list literal")
        }
    } else if (rootStatement is VarDeclaration && rootStatement.valueType != null) {
        val type = rootStatement.valueType!!.toType(typeDB, typeTable)//fix
        statement.type = type
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

    val keyType = key.type ?: key.token.compileError("Can't resolve type of key: ${key.str}")
    val valueType = value.type ?: value.token.compileError("Can't resolve type of value: ${value.str}")

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

package frontend.typer

import frontend.meta.compileError
import frontend.parser.types.ast.*

fun Resolver.resolveCodeBlock(
    statement: CodeBlock,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>,
    rootStatement: Statement?
) {

    // [] vs x = []
    if ((rootStatement != null && (rootStatement !is VarDeclaration && rootStatement !is Message)) || rootStatement == null) {
        statement.isSingle = true
    }

    val namedLambdaArgs = statement.inputList
    namedLambdaArgs.forEach {
        if (it.typeAST != null) {
            it.type = it.typeAST.toType(typeDB, typeTable)//fix
        }
    }

    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()


    var isThisWhileCycle = true
    var metaDataFound: KeywordMsgMetaData? = null
    var itArgType: Type? = null
    // resolve generic args and just args, kinda
    val genericLetterToTypes = mutableMapOf<String, Type>()
    if (rootStatement != null && rootStatement is KeywordMsg && currentArgumentNumber != -1 && rootStatement.receiver !is CodeBlock && rootStatement.receiver.type !is Type.Lambda) {

        val rootReceiverType = rootStatement.receiver.type!!
        val metaDataFromDb = findKeywordMsgType(
            rootReceiverType,
            rootStatement.selectorName,
            rootStatement.token
        )
        metaDataFound = metaDataFromDb
        val currentArgType = metaDataFromDb.argTypes[currentArgumentNumber]

        // List(T, G) map::[T -> G] -> G = []

        val rootType = typeTable[rootReceiverType.name]//testing
        val testDB = typeDB.getType(rootReceiverType.name)
        if (rootType is Type.UserType && rootReceiverType is Type.UserType) {
            fillGenericsWithLettersByOrder(rootType)

            rootType.typeArgumentList.forEachIndexed { i, it ->
                val beforeName = it.beforeGenericResolvedName

                if (it.name.length == 1 && it.name[0].isUpperCase()) {

                    val sameButResolvedArg = rootReceiverType.typeArgumentList[i]

                    if (sameButResolvedArg.name.length == 1) {
                        throw Exception("Arg ${sameButResolvedArg.name} is unresolved")
                    }
                    genericLetterToTypes[it.name] = sameButResolvedArg
                } else if (beforeName != null && beforeName.length == 1 && beforeName[0].isUpperCase()) {
                    // was resolved somehow
                    genericLetterToTypes[beforeName] = it

                }
            }
        }

        if (currentArgType.type is Type.Lambda) {
            // if this is lambda with one arg, and no namedArgs, then add "it" to scope
            if (currentArgType.type.args.count() == 1 && namedLambdaArgs.isEmpty()) {
                val typeOfFirstArgs = currentArgType.type.args[0].type
                val typeForIt = if (typeOfFirstArgs !is Type.UnknownGenericType) {
                    typeOfFirstArgs
                } else {
                    val foundRealType = genericLetterToTypes[typeOfFirstArgs.name]
                        ?: throw Exception("Can't find resolved type ${typeOfFirstArgs.name} while resolving lambda")
                    foundRealType
                }
                previousAndCurrentScope["it"] = typeForIt
                itArgType = typeForIt
            } else if (currentArgType.type.args.isNotEmpty()) {
                if (currentArgType.type.args.count() != namedLambdaArgs.count()) {
                    statement.token.compileError("Number of arguments for code block: ${currentArgType.type.args.count()}, you passed ${namedLambdaArgs.count()}")
                }

//                    rootType.typeArgumentList
                currentArgType.type.args.forEachIndexed { i, typeField ->
                    val typeForArg = if (typeField.type !is Type.UnknownGenericType) {
                        typeField.type
                    } else {
                        val foundRealType = genericLetterToTypes[typeField.type.name]
                            ?: throw Exception("Can't find resolved type ${typeField.type.name} while resolving lambda")
                        foundRealType
                    }
                    // check declared type of argument first
//                    if ()
//                    namedLambdaArgs[i].typeAST
                    if (namedLambdaArgs[i].type == null)
                        namedLambdaArgs[i].type = typeForArg
                }
            }

        }

        isThisWhileCycle = false

    }

    namedLambdaArgs.forEach {
        previousAndCurrentScope[it.name] = it.type!!
    }

    currentLevel++
    resolve(statement.statements, previousAndCurrentScope, statement)
    currentLevel--
    val lastExpression = statement.statements.last()

    // Add lambda type to code-block itself
    val returnType =
        if (lastExpression is Expression) lastExpression.type!! else Resolver.defaultTypes[InternalTypes.Unit]!!

    val args = statement.inputList.map {
        TypeField(name = it.name, type = it.type!!)
    }.toMutableList()


    if (itArgType != null && args.isEmpty()) {
        if (compare2Types(returnType, itArgType) && metaDataFound != null) {
            val e = metaDataFound.argTypes[0]
            if (e.type is Type.Lambda) {
                returnType.beforeGenericResolvedName = e.type.returnType.name
            }
        }
        args.add(TypeField("it", itArgType))
    }
    val type = Type.Lambda(
        args = args,
        returnType = returnType, // Тут у return Type before должен быть G
        pkg = currentPackageName
    )

    statement.type = type


    // add whileTrue argument of lambda type
    if (isThisWhileCycle && rootStatement is KeywordMsg) {
        val receiverType = rootStatement.receiver.type

        // find if it is a whileTrue or whileFalse than add an argument of type lambda to receiver
        if (receiverType is Type.Lambda && receiverType.args.isEmpty() &&
            (rootStatement.selectorName == "whileTrue" || rootStatement.selectorName == "whileFalse")

        ) {
            receiverType.args.add(
                TypeField(
                    name = rootStatement.selectorName,
                    type = Type.Lambda(
                        args = mutableListOf(),
                        returnType = Resolver.defaultTypes[InternalTypes.Any]!!,
                        pkg = currentPackageName
                    )
                )
            )
        }
    }

    if (currentLevel == 0) {
        topLevelStatements.add(statement)
    }
}

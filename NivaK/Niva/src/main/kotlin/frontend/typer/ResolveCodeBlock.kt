package frontend.typer

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


    val variables = statement.inputList
    variables.forEach {
        if (it.typeAST != null) {
            it.type = it.typeAST.toType(typeTable)
        } else {
            it.type = getTypeForIdentifier(it, previousScope, currentScope)
        }

    }

    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()

    variables.forEach {
        previousAndCurrentScope[it.name] = it.type!!
    }

    var isThisWhileCycle = true
    var metaDataFound: KeywordMsgMetaData? = null
    var itArgType: Type? = null
    // if this is lambda with one arg, then add "it" to scope
    // TODO don't add it if this lambda has named arg
    val genericLetterToTypes = mutableMapOf<String, Type>()
    if (rootStatement != null && rootStatement is KeywordMsg && currentArgumentNumber != -1) {
        if (rootStatement.receiver !is CodeBlock) {
            val rootReceiverType = rootStatement.receiver.type!!
            val metaData = findKeywordMsgType(
                rootReceiverType,
                rootStatement.selectorName,
                rootStatement.token
            )
            metaDataFound = metaData
            val currentArgType = metaData.argTypes[currentArgumentNumber]

            // List(T, G) map::[T -> G] -> G = []

            val rootType = typeTable[rootReceiverType.name]
            if (rootType is Type.UserType && rootReceiverType is Type.UserType) {
                rootType.typeArgumentList.forEachIndexed { i, it ->
                    if (it.name.length == 1 && it.name[0].isUpperCase()) {
                        val sameButResolvedArg = rootReceiverType.typeArgumentList[i]

                        if (sameButResolvedArg.name.length == 1) {
                            throw Exception("Arg ${sameButResolvedArg.name} is unresolved")
                        }
                        genericLetterToTypes[it.name] = sameButResolvedArg
                    }
                }
            }


            if (currentArgType.type is Type.Lambda && currentArgType.type.args.count() == 1) {
                val typeOfFirstArgs = currentArgType.type.args[0].type
                val typeForIt = if (typeOfFirstArgs !is Type.UnknownGenericType) {
                    typeOfFirstArgs
                } else {
                    val foundRealType = genericLetterToTypes[typeOfFirstArgs.name]
                        ?: throw Exception("Can't find resolved type ${typeOfFirstArgs.name} while resolvind lambda")
                    foundRealType
                }
                previousAndCurrentScope["it"] = typeForIt
                itArgType = typeForIt
            }

            isThisWhileCycle = false
        }
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

package frontend.resolver.messageResolving

import frontend.parser.parsing.MessageDeclarationType
import frontend.resolver.KeywordMsgMetaData
import frontend.resolver.Resolver
import frontend.resolver.Type
import frontend.resolver.Type.RecursiveType.copy
import frontend.resolver.KeywordArg
import frontend.resolver.compare2Types
import frontend.resolver.fillGenericsWithLettersByOrder
import frontend.resolver.resolve
import frontend.resolver.toType
import main.utils.RESET
import main.utils.WHITE
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.resolver.findAnyMsgType
import main.utils.isGeneric
import java.lang.Exception
import kotlin.collections.count
import kotlin.collections.forEach
import kotlin.collections.forEachIndexed
import kotlin.collections.isNotEmpty
import kotlin.collections.last
import kotlin.collections.map
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.toMutableList
import kotlin.collections.toMutableMap


fun Resolver.resolveCodeBlockAsBody(
    statement: CodeBlock,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>,
    rootStatement: Statement?,
) {
    resolveCodeBlock(statement, previousScope, currentScope, rootStatement)
    statement.type = (statement.type as Type.Lambda).returnType
}

fun Resolver.resolveCodeBlock(
    statement: CodeBlock,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>,
    rootStatement: Statement?,
) {

    // [] vs x = []
    if ((rootStatement != null && (rootStatement !is VarDeclaration && rootStatement !is Message && rootStatement !is ControlFlow && rootStatement !is ReturnStatement)) || rootStatement == null) {
        statement.isSingle = true
    }

    val namedLambdaArgs =  statement.inputList
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
    val genericLetterToTypesOfReceiver = mutableMapOf<String, Type>()
    if (rootStatement is KeywordMsg &&
        rootStatement.kind != KeywordLikeType.CustomConstructor &&
        rootStatement.kind != KeywordLikeType.Constructor &&
        currentArgumentNumber != -1 &&
        rootStatement.receiver !is CodeBlock &&
        rootStatement.receiver.type !is Type.Lambda)
    {

        val rootReceiverType = rootStatement.receiver.type!!
        val metaDataFromDb = findAnyMsgType(
            rootReceiverType,
            rootStatement.selectorName,
            rootStatement.token,
            MessageDeclarationType.Keyword
        ) as KeywordMsgMetaData
        metaDataFound = metaDataFromDb
        val currentArg = metaDataFromDb.argTypes[currentArgumentNumber]
        // T sas -> T = []
        // 4 sas
        // adding Int as T
        if (metaDataFromDb.forGeneric) {
            genericLetterToTypesOfReceiver["T"] = rootReceiverType
        }
        // List(T, G) map::[T -> G] -> G = []
        if (rootReceiverType is Type.UserType && rootReceiverType.typeArgumentList.isNotEmpty()) {
            val rootType = rootReceiverType.copy()

            fillGenericsWithLettersByOrder(rootType)

            rootType.typeArgumentList.forEachIndexed { i, it ->
                val beforeName = it.beforeGenericResolvedName

                if (it.name.isGeneric()) {

                    val sameButResolvedArg = rootReceiverType.typeArgumentList[i]

//                    if (sameButResolvedArg.name.isGeneric()) {
                        // check that current resolving message declaration has some generics
//                        println("olala, looks like the $sameButResolvedArg is Known generic")
//                        throw Exception("Arg ${sameButResolvedArg.name} is unresolved")
//                    }
                    genericLetterToTypesOfReceiver[it.name] = sameButResolvedArg
                } else if (beforeName != null && beforeName.isGeneric()) {
                    // was resolved somehow
                    genericLetterToTypesOfReceiver[beforeName] = it
                }
            }
        }
        val currentArgType = currentArg.type
        if (currentArgType is Type.Lambda) {
            // if this is lambda with receiver, then remove this arg
            val lambdaArgsFromDb = if (currentArgType.extensionOfType != null) {
                if (currentArgType.args.first().name != "this") {
                    statement.token.compileError("Compiler bug, no this arg in extension lambda")
                }
//                thisArgType = currentArgType.extensionOfType
                previousAndCurrentScope["this"] = currentArgType.extensionOfType
                currentArgType.args.drop(1)
            } else currentArgType.args


            // if this is lambda with one arg, and no namedArgs, then add 'it' to scope
            if (lambdaArgsFromDb.count() == 1 && namedLambdaArgs.isEmpty()) {
                val typeOfFirstArgs = lambdaArgsFromDb[0].type
                val typeForIt = if (typeOfFirstArgs !is Type.UnknownGenericType) {
                    typeOfFirstArgs
                } else {
                    val foundRealType = genericLetterToTypesOfReceiver[typeOfFirstArgs.name]
                        ?: throw Exception("Can't find resolved type ${typeOfFirstArgs.name} while resolving codeblock, use receiver that has generic param")
                    foundRealType
                }
                previousAndCurrentScope["it"] = typeForIt
                itArgType = typeForIt
            } else if (lambdaArgsFromDb.isNotEmpty()) {

                if (lambdaArgsFromDb.count() != namedLambdaArgs.count()) {
                    statement.token.compileError("Number of arguments for code block: ${WHITE}${lambdaArgsFromDb.count()}$RESET, you passed ${WHITE}${namedLambdaArgs.count()}")
                }

                namedLambdaArgs.asSequence()
                    .filter { it.type == null }
                    .forEachIndexed { i, it ->
                        val typeField = lambdaArgsFromDb[i]

                        val typeForArg = if (typeField.type !is Type.UnknownGenericType) {
                            typeField.type
                        } else {
                            val foundRealType = genericLetterToTypesOfReceiver[typeField.type.name]
                                ?: genericLetterToTypes[typeField.type.name]
                                ?: statement.token.compileError("Compiler error: Can't find resolved type ${typeField.type.name} while resolving codeblock, use receiver with some generic field")
                            foundRealType
                        }

                        it.type = typeForArg
                    }
            }
        }
        isThisWhileCycle = false
    }

    namedLambdaArgs.forEach {
        val type =
            it.type ?: it.token.compileError("Compiler bug: can't infer type of $WHITE${it.name} codeblock parameter")
        previousAndCurrentScope.putIfAbsent(it.name, type)
    }


    currentLevel++
    resolve(statement.statements, previousAndCurrentScope, statement)
    currentLevel--
    if (statement.statements.isEmpty()) {
        statement.token.compileError("Codeblock doesn't contain code")
    }

    val lastExpression = statement.statements.last()
    // Add lambda type to code-block itself
    val unitType = Resolver.defaultTypes[InternalTypes.Unit]!!
    val returnType =
        when (lastExpression) {
            is ControlFlow -> {
                if (lastExpression.kind != ControlFlowKind.Statement && lastExpression.kind != ControlFlowKind.StatementTypeMatch) {
                    lastExpression.type!!
                } else unitType
            }

            is Expression -> lastExpression.type!!
            else -> unitType
        }
    val args = statement.inputList.map {
        KeywordArg(name = it.name, type = it.type!!)
    }.toMutableList()


    if (itArgType != null && args.isEmpty()) {
        if (compare2Types(returnType, itArgType) && metaDataFound != null) {
            val e = metaDataFound.argTypes[0]
            val type = e.type
            if (type is Type.Lambda) {
                returnType.beforeGenericResolvedName = type.returnType.name
            }
        }
        args.add(KeywordArg("it", itArgType))
    }
    val type = Type.Lambda(
        args = args,
        returnType = returnType,
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
                KeywordArg(
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
}

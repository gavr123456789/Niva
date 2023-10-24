package frontend.typer

import frontend.meta.compileError
import frontend.parser.types.ast.*

fun Resolver.resolveMessage(
    statement: Message,
    previousScope: MutableMap<String, Type>,
    currentScope: MutableMap<String, Type>
) {
    when (statement) {
        is KeywordMsg -> {
            /// check for constructor
            if (statement.receiver.type == null) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(listOf(statement.receiver), previousAndCurrentScope, statement)
                currentLevel--
            }
            val receiverType =
                statement.receiver.type
                    ?: statement.token.compileError("Can't infer receiver ${statement.receiver.str} type")


            fun resolveKindOfKeyword(statement: KeywordMsg): KeywordLikeType {
                if (receiverType is Type.Lambda) {
                    return KeywordLikeType.ForCodeBlock
                }
                val receiverText = statement.receiver.str
                val q = typeTable[receiverText]
                if (receiverText == "Project" || receiverText == "Bind") {
                    statement.token.compileError("We cant get here, type Project are ignored")
                }

                if (q != null) {
                    if (q is Type.UserUnionRootType) {
                        statement.token.compileError("You can't instantiate Union root: ${q.name}")
                    }
                    statement.kind = KeywordLikeType.Constructor
                    return KeywordLikeType.Constructor
                }
                // This is Setter or Keyword now

                // if the amount of keyword's arg is 1, and its name on of the receiver field, then its setter
                if (statement.args.count() == 1 && receiverType is Type.UserType) {
                    val keyArgText = statement.args[0].selectorName
                    // find receiver arg same as keyArgText
                    val receiverArgWithSameName = receiverType.fields.find { it.name == keyArgText }
                    if (receiverArgWithSameName != null) {
                        // this is setter
                        statement.kind = KeywordLikeType.Setter
                        statement.type = receiverArgWithSameName.type
                        return KeywordLikeType.Setter
                    }
                }
                // this is Keyword
                statement.kind = KeywordLikeType.Keyword
                return KeywordLikeType.Keyword
            }

            val kind = resolveKindOfKeyword(statement)

            val letterToRealType = mutableMapOf<String, Type>()
            /// resolve arguments

            val msgTypeFromDB = if (kind == KeywordLikeType.Keyword) findKeywordMsgType(
                receiverType,
                statement.selectorName,
                statement.token
            ) else null


            // resolve args types
            val args = statement.args
            args.forEachIndexed { argNum, it ->
                if (it.keywordArg.type == null) {
                    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                    currentLevel++
                    currentArgumentNumber = argNum
                    resolve(listOf(it.keywordArg), previousAndCurrentScope, statement)
                    currentLevel--

                    val argType = it.keywordArg.type!!

                    // we need to check for generic args only if it is Keyword
                    if (msgTypeFromDB != null) {
                        val typeFromDBForThisArg = msgTypeFromDB.argTypes[argNum].type
                        if (typeFromDBForThisArg.name.length == 1 && typeFromDBForThisArg.name[0].isUpperCase()) {
                            letterToRealType[typeFromDBForThisArg.name] = argType
                        }

                        if (typeFromDBForThisArg is Type.Lambda) {
                            if (argType !is Type.Lambda) {
                                throw Exception("If typeFromDBForThisArg is lambda then argType must be lambda")
                            }
                            /// remember letter to type args
                            typeFromDBForThisArg.args.forEachIndexed { i, typeField ->
                                val beforeGenericResolvedName = typeField.type.beforeGenericResolvedName
                                if (typeField.type.name.length == 1 && typeField.type.name[0].isUpperCase()) {
                                    letterToRealType[typeFromDBForThisArg.name] = argType.args[i].type
                                } else if (beforeGenericResolvedName != null && beforeGenericResolvedName.length == 1 && beforeGenericResolvedName[0].isUpperCase()) {
                                    letterToRealType[beforeGenericResolvedName] = argType.args[i].type
                                }
                            }
                            /// remember letter to return type
                            val returnTypeBefore = typeFromDBForThisArg.returnType.beforeGenericResolvedName

                            if (typeFromDBForThisArg.returnType.name.length == 1 && typeFromDBForThisArg.returnType.name[0].isUpperCase()) {
                                letterToRealType[typeFromDBForThisArg.returnType.name] = argType.returnType
                            } else if (returnTypeBefore != null && returnTypeBefore.length == 1 && returnTypeBefore[0].isUpperCase()) {
                                letterToRealType[returnTypeBefore] = argType.returnType
                            }

                        }
                    }


                }
            }
            currentArgumentNumber = -1

            /// end of resolving arguments

            // if receiverType is lambda then we need to check does it have same argument names and types
            if (receiverType is Type.Lambda) {

                // need
                if (receiverType.args.count() != statement.args.count()) {
                    val setOfHaveFields = statement.args.map { it.selectorName }.toSet()
                    val setOfNeededFields = receiverType.args.map { it.name }.toSet()
                    val extraOrMissed = statement.args.count() > receiverType.args.count()
                    val whatIsMissingOrExtra =
                        if (extraOrMissed)
                            (setOfHaveFields - setOfNeededFields).joinToString(", ") { it }
                        else
                            (setOfNeededFields - setOfHaveFields).joinToString(", ") { it }

                    val beginText =
                        statement.receiver.str + " " + statement.args.joinToString(": ") { it.selectorName } + ":"
                    val text =
                        if (extraOrMissed)
                            "For $beginText code block eval, extra fields are listed: $whatIsMissingOrExtra"
                        else
                            "For $beginText code block eval, not all fields are listed, you missed: $whatIsMissingOrExtra"
                    statement.token.compileError(text)
                }

                statement.args.forEachIndexed { ii, it ->
                    // name check
                    if (it.selectorName != receiverType.args[ii].name) {
                        statement.token.compileError("${it.selectorName} is not valid arguments for lambda ${statement.receiver.str}, the valid arguments are: ${statement.args.map { it.selectorName }} on Line ${statement.token.line}")
                    }
                    // type check
                    val isTypesEqual = compare2Types(it.keywordArg.type!!, receiverType.args[ii].type)
                    if (!isTypesEqual) {
                        statement.token.compileError(
                            "${it.selectorName} is not valid type for lambda ${statement.receiver.str}, the valid arguments are: ${statement.args.map { it.keywordArg.type?.name }}"
                        )
                    }
                }

                statement.type = receiverType.returnType
                statement.kind = KeywordLikeType.ForCodeBlock
                return
            }



            when (kind) {
                KeywordLikeType.Constructor -> {
                    // check that all fields are filled
                    var replacerTypeIfItGeneric: Type? = null
                    if (receiverType is Type.UserType) {
                        // collect all fields from parents
                        val listOfAllParentsFields = mutableListOf<TypeField>()
                        var parent = receiverType.parent
                        while (parent != null && parent is Type.UserType) {
                            listOfAllParentsFields.addAll(parent.fields)
                            parent = parent.parent
                        }

                        val receiverFields = receiverType.fields //+ listOfAllParentsFields
                        // check that amount of arguments if right
                        if (statement.args.count() != receiverFields.count()) {

                            val setOfHaveFields = statement.args.map { it.selectorName }.toSet()
                            val setOfNeededFields = receiverFields.map { it.name }.toSet()
                            val extraOrMissed = statement.args.count() > receiverFields.count()
                            val whatIsMissingOrExtra =
                                if (extraOrMissed)
                                    (setOfHaveFields - setOfNeededFields).joinToString(", ") { it }
                                else
                                    (setOfNeededFields - setOfHaveFields).joinToString(", ") { it }


                            val errorText =
                                statement.receiver.str + " " + statement.args.joinToString(": ") { it.selectorName } + ":"
                            val text =
                                if (extraOrMissed)
                                    "For $errorText constructor call, extra fields are listed: $whatIsMissingOrExtra"
                                else
                                    "For $errorText constructor call, not all fields are listed, you missed: $whatIsMissingOrExtra"
                            statement.token.compileError(text)
                        }

                        val typeFieldsNamesSet = statement.args.map { it.selectorName }.toSet()
                        val receiverFieldsSet = receiverFields.map { it.name }.toSet()

                        if (typeFieldsNamesSet != receiverFieldsSet) {
                            statement.token.compileError("In constructor message for type ${statement.receiver.str} fields $typeFieldsNamesSet != $receiverFieldsSet")
                        }

//                        statement.args.forEachIndexed { i, arg ->
//                            val typeField = receiverFields[i].name
//                            // check that every arg name is right
//                            if (typeField != arg.selectorName) {
//                                statement.token.compileError("In constructor message for type ${statement.receiver.str} field $typeField != ${arg.selectorName}")
//                            }
//                        }

                        // replace every Generic type with real
                        if (receiverType.typeArgumentList.isNotEmpty()) {
                            replacerTypeIfItGeneric = Type.UserType(
                                name = receiverType.name,
                                typeArgumentList = receiverType.typeArgumentList.toList(),
                                fields = receiverType.fields,
                                isPrivate = receiverType.isPrivate,
                                pkg = receiverType.pkg,
                                protocols = receiverType.protocols.toMutableMap()
                            )
                            // match every type argument with fields
                            // replace fields types to real one
                            val map = mutableMapOf<String, Type>()
                            replacerTypeIfItGeneric.typeArgumentList.forEach { typeArg ->
                                val fieldsOfThisType =
                                    replacerTypeIfItGeneric.fields.filter { it.type.name == typeArg.name }
                                fieldsOfThisType.forEach { genericField ->
                                    // find real type from arguments
                                    val real = statement.args.find { it.selectorName == genericField.name }
                                        ?: statement.token.compileError("Can't find real type for field: ${genericField.name} of generic type: ${genericField.type.name}")
                                    val realType = real.keywordArg.type
                                        ?: real.keywordArg.token.compileError("Panic: ${real.selectorName} doesn't have type")
                                    genericField.type = realType
                                    map[typeArg.name] = realType
                                }
                            }
                            // replace typeFields to real ones
                            val realTypes = replacerTypeIfItGeneric.typeArgumentList.toMutableList()
                            map.forEach { (fieldName, fieldRealType) ->
                                val fieldIndex = realTypes.indexOfFirst { it.name == fieldName }
                                realTypes[fieldIndex] = fieldRealType
                            }
                            replacerTypeIfItGeneric.typeArgumentList = realTypes
                        }

                    }

                    statement.type = replacerTypeIfItGeneric ?: receiverType
                }

                KeywordLikeType.Setter -> {
                    // Nothing to do, because checke for setter already sets the type of statement
                }

                KeywordLikeType.Keyword -> {
                    val msgTypeFromDB = findKeywordMsgType(receiverType, statement.selectorName, statement.token)

                    val returnType = if (msgTypeFromDB.returnType is Type.UnknownGenericType) {
                        val realTypeFromTable = letterToRealType[msgTypeFromDB.returnType.name]
                        if (realTypeFromTable == null) {
                            throw Exception("Cant find generic type ${msgTypeFromDB.returnType.name} in letterToRealType table $letterToRealType")
                        }
                        realTypeFromTable
                    } else if (msgTypeFromDB.returnType is Type.UserType && msgTypeFromDB.returnType.typeArgumentList.find { it.name.length == 1 } != null) {
                        // что если у обычного кейворда возвращаемый тип имеет нересолвнутые женерик параметры
                        val returnType = msgTypeFromDB.returnType
                        val newResolvedTypeArgs = mutableListOf<Type>()

                        // идем по каждому, если он не резолвнутый, то добавляем из таблицы, если резолвнутый то добавляем так
                        returnType.typeArgumentList.forEach { typeArg ->
                            val isNotResolved = typeArg.name.length == 1 && typeArg.name[0].isUpperCase()
                            if (isNotResolved) {
                                val resolvedLetterType = letterToRealType[typeArg.name]
                                if (resolvedLetterType == null) {
                                    throw Exception("Can't find generic type: ${typeArg.name} in letter table")
                                }
                                newResolvedTypeArgs.add(resolvedLetterType)
                                resolvedLetterType.beforeGenericResolvedName = typeArg.name
                            } else {
                                newResolvedTypeArgs.add(typeArg)
                            }
                        }


                        Type.UserType(
                            name = returnType.name,
                            typeArgumentList = newResolvedTypeArgs,
                            fields = returnType.fields,
                            isPrivate = returnType.isPrivate,
                            pkg = returnType.pkg,
                            protocols = returnType.protocols
                        )
                    } else msgTypeFromDB.returnType
                    statement.type = returnType
                }

                KeywordLikeType.ForCodeBlock -> {
                    throw Exception("We can't reach here, because we do early return")
                }
            }

        }

        is BinaryMsg -> {
            val receiver = statement.receiver

            receiver.type = when (receiver) {
                is ExpressionInBrackets -> resolveExpressionInBrackets(receiver, previousScope, currentScope)
                is IdentifierExpr -> getTypeForIdentifier(receiver, currentScope, previousScope)
                is LiteralExpression.FalseExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                is LiteralExpression.TrueExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                is LiteralExpression.FloatExpr -> Resolver.defaultTypes[InternalTypes.Float]
                is LiteralExpression.IntExpr -> Resolver.defaultTypes[InternalTypes.Int]
                is LiteralExpression.StringExpr -> Resolver.defaultTypes[InternalTypes.String]
                is KeywordMsg, is UnaryMsg, is BinaryMsg -> receiver.type

                is MessageSend, is MapCollection, is ListCollection, is CodeBlock -> TODO()
            }
            var receiverType = receiver.type!!


            // resolve messages
            if (statement.unaryMsgsForArg.isNotEmpty()) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(statement.unaryMsgsForArg, previousAndCurrentScope, statement)
                currentLevel--
            }

            val isUnaryForReceiver = statement.unaryMsgsForReceiver.isNotEmpty()
            if (isUnaryForReceiver) {
                val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()
                currentLevel++
                resolve(statement.unaryMsgsForReceiver, previousAndCurrentScope, statement)
                currentLevel--
            }
            // q = "sas" + 2 toString
            // find message for this type
            val messageReturnType =
                if (isUnaryForReceiver)
                    findBinaryMessageType(
                        statement.unaryMsgsForReceiver.last().type!!,
                        statement.selectorName,
                        statement.token
                    )
                else
                    findBinaryMessageType(receiverType, statement.selectorName, statement.token)
            statement.type = messageReturnType

        }

        is UnaryMsg -> {

            // if a type already has a field with the same name, then this is getter, not unary send
            val receiver = statement.receiver

            // for constructors
            var isStaticCall = false

            if (receiver.type == null)
                receiver.type = when (receiver) {

                    is ExpressionInBrackets -> resolveExpressionInBrackets(receiver, currentScope, previousScope)
                    is CodeBlock -> TODO()
                    is ListCollection -> TODO()
                    is MapCollection -> TODO()
                    // receiver
                    is UnaryMsg -> TODO()
                    is BinaryMsg -> TODO()
                    is KeywordMsg -> TODO()

                    is MessageSend -> TODO()


                    is IdentifierExpr -> getTypeForIdentifier(receiver, currentScope, previousScope)


                    is LiteralExpression.FalseExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                    is LiteralExpression.TrueExpr -> Resolver.defaultTypes[InternalTypes.Boolean]
                    is LiteralExpression.FloatExpr -> Resolver.defaultTypes[InternalTypes.Float]
                    is LiteralExpression.IntExpr -> Resolver.defaultTypes[InternalTypes.Int]
                    is LiteralExpression.StringExpr -> Resolver.defaultTypes[InternalTypes.String]
                }

            // if this is message for type
            if (receiver is IdentifierExpr) {
                if (typeTable[receiver.str] != null) {
                    isStaticCall = true
                }
            }
            val receiverType = receiver.type!!



            if (receiverType is Type.Lambda) {
                if (statement.selectorName != "exe") {
                    if (receiverType.args.isNotEmpty())
                        statement.token.compileError("Lambda ${statement.str} on Line ${statement.token.line} takes more than 0 arguments, please use keyword message with it's args names")
                    else
                        statement.token.compileError("For lambda ${statement.str} on Line ${statement.token.line} you can use only unary 'do' message")
                }


                if (receiverType.args.isNotEmpty()) {
                    statement.token.compileError("Lambda ${statement.str} on Line ${statement.token.line} takes more than 0 arguments, please use keyword message with it's args names")
                }

                statement.type = receiverType.returnType
                statement.kind = UnaryMsgKind.ForCodeBlock
                return
            }


            val checkForGetter = {
                if (receiverType is Type.UserLike) {
                    val fieldWithSameName = receiverType.fields.find { it.name == statement.selectorName }
                    Pair(fieldWithSameName != null, fieldWithSameName)
                } else Pair(false, null)
            }

            val (isGetter, w) = checkForGetter()
            if (isGetter) {
                statement.kind = UnaryMsgKind.Getter
                statement.type = w!!.type

            } else {
                // usual message
                // find this message

                val messageReturnType =
                    if (!isStaticCall)
                        findUnaryMessageType(receiverType, statement.selectorName, statement.token)
                    else
                        findStaticMessageType(receiverType, statement.selectorName, statement.token)

                statement.kind = UnaryMsgKind.Unary
                statement.type = messageReturnType
            }
        }
    }
}

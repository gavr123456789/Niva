@file:Suppress("UnusedReceiverParameter")

package frontend.resolver

import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
import main.frontend.resolver.messageResolving.addErrorEffect
import main.frontend.resolver.messageResolving.resolveBinaryMsg
import main.frontend.resolver.messageResolving.resolveKeywordMsg
import main.frontend.resolver.messageResolving.resolveStaticBuilder
import main.frontend.resolver.messageResolving.resolveUnaryMsg
import main.utils.GlobalVariables
import main.utils.RESET
import main.utils.YEL
import main.utils.isGeneric

fun fillGenericsWithLettersByOrder(type: Type.UserLike) {
    if (type.typeArgumentList.count() > 2) {
        throw Exception("Generics with more than 2 params are not supported yet")
    }
    val genericLetters = listOf("T", "G")

    val newArgs = type.typeArgumentList.mapIndexed { i, it ->
        val k = genericLetters[i]
        it.cloneAndChangeBeforeGeneric(k)
    }
    type.replaceTypeArguments(newArgs)
}

// goes like
// |List::List::Int -> List::|List::Int
// |List::T         -> List::|T
// found that T is List::Int
fun getTableOfLettersFromType(type: Type.UserLike, typeFromDb: Type.UserLike, result: MutableMap<String, Type>) {
    fun addToResultIfItsGeneric(next1: Type, next2: Type) {
        if (next1 is Type.UnknownGenericType && next2 is Type.UnknownGenericType && next1.name == next2.name) {
            return
        }
        if (next1 is Type.UnknownGenericType) {
            result[next1.name] = next2
        }
        if (next2 is Type.UnknownGenericType) {
            result[next2.name] = next1
        }
    }

    val type1Count = type.typeArgumentList.count()
    val type2Count = typeFromDb.typeArgumentList.count()

    if (type1Count == 1 && type2Count == 1) {
        val x = type.typeArgumentList.first()
        val y = typeFromDb.typeArgumentList.first()
        if (x is Type.UserLike && y is Type.UserLike) {
            getTableOfLettersFromType(x, y, result)
        }
        addToResultIfItsGeneric(x, y)
    }

    if (type1Count > 1 && type2Count > 1) {
        // probably just collect every generics arguments, and real type from same place in type1
        val iter1 = type.typeArgumentList.listIterator()
        val iter2 =typeFromDb.typeArgumentList.listIterator()

        while (iter2.hasNext()) {
            val next1 = iter1.next()
            val next2 = iter2.next()
            if (next1 is Type.UserLike && next2 is Type.UserLike) {
                getTableOfLettersFromType(next1, next2, result)
            }
            addToResultIfItsGeneric(next1, next2)
        }
    }
    if (type1Count == 0) {
        if (type is Type.UnknownGenericType) {
            if (typeFromDb !is Type.UnknownGenericType || typeFromDb.name != type.name) {
                result[type.name] = typeFromDb
            }
        }
    }
    if (type2Count == 0) {
        if (typeFromDb is Type.UnknownGenericType) {
            if (type !is Type.UnknownGenericType || type.name != typeFromDb.name) {
                result[typeFromDb.name] = type
            }
        }
    }
}

fun resolveReceiverGenericsFromArgs(receiverType: Type, args: List<KeywordArgAst>, tok: Token): Type {
    if (receiverType !is Type.UserLike) return receiverType
    // replace every Generic type with real
    if (receiverType.typeArgumentList.isEmpty()) {
        return receiverType
    }
    val replacerTypeIfItGeneric = receiverType.copy()

    // match every type argument with fields
    val map = mutableMapOf<String, Type>()
    replacerTypeIfItGeneric.typeArgumentList.forEach { typeArg ->
        val fieldsOfThisType =
            replacerTypeIfItGeneric.fields.filter { it.type.name == typeArg.name }
        fieldsOfThisType.forEach { genericField ->
            // find real type from arguments
            val real = args.find { it.name == genericField.name }
                ?: tok.compileError("Can't find real type for field: ${YEL}${genericField.name}${RESET} of generic type: ${YEL}${genericField.type.name}${RESET}")
            val realType = real.keywordArg.type
                ?: real.keywordArg.token.compileError("Compiler bug: ${YEL}${real.name}${RESET} doesn't have type")
            map[typeArg.name] = realType
        }
    }
    // replace typeFields to real ones
    val realTypes = replacerTypeIfItGeneric.typeArgumentList.toMutableList()
    map.forEach { (fieldName, fieldRealType) ->
        val fieldIndex = realTypes.indexOfFirst { it.name == fieldName }
        realTypes[fieldIndex] = fieldRealType
        // replace all fields of generic type
        replacerTypeIfItGeneric.fields.forEach {
            if (it.type.name == fieldName) {
                it.type = fieldRealType
            }
        }
    }
//    replacerTypeIfItGeneric.typeArgumentList = realTypes
    replacerTypeIfItGeneric.replaceTypeArguments(realTypes)
    return replacerTypeIfItGeneric
}


fun Resolver.resolveMessage(
    statement: Message,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
) {
    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()

    val (returnType, msgFromDb) = when (statement) {
        is UnaryMsg -> resolveUnaryMsg(statement, previousAndCurrentScope)
        is KeywordMsg -> resolveKeywordMsg(statement, previousScope, currentScope)
        is BinaryMsg -> resolveBinaryMsg(statement, previousAndCurrentScope)
        is StaticBuilder -> resolveStaticBuilder(statement, currentScope, previousScope)
    }


    // check errors and mutability

    // if we see the method with possible errors but not resolved body
    // that means it was declared after currently resolving method
    if (msgFromDb != null) {
        val decl = statement.declaration
        val receiver = statement.receiver
        val receiverType = receiver.type!!

        if (GlobalVariables.capabilities && receiverType is Type.UserLike && receiverType.isBinding && !this.resolvingMainFile) {
            statement.token.compileError("Can't use bindings outside of main entry point when capabilities enabled: ${YEL}${receiverType}")
        }
        //check that it was mutable call for mutable type
        if (msgFromDb.forMutableType && receiver is IdentifierExpr) {
            val receiverFromScope = previousAndCurrentScope[receiver.name]
            if (receiverFromScope != null && !receiverFromScope.isMutable) {
                val decl = msgFromDb.declaration?.toString() ?: msgFromDb.name
                statement.token.compileError("receiver type $receiverType is not mutable, but $decl declared for mutable type, use `x::mut $receiverType = ...`")
            }
        } else if (msgFromDb.forMutableType && receiver is MessageSend) {
            val receiverType = receiver.type!!
            if (!receiverType.isMutable) {
                val decl = msgFromDb.declaration?.toString() ?: msgFromDb.name
                statement.token.compileError("receiver type $receiverType is not mutable, but $decl declared for mutable type, use `x::mut $receiverType = ...`")

            }
        }
        //



        if (decl?.returnTypeAST?.errors?.isEmpty() == true && decl.stackOfPossibleErrors.isEmpty()) {
            // this means the errors were not resolved yet
            this.resolvingMessageDeclaration = decl
            resolveMessageDeclaration(decl, true, previousScope, false)
        }
    }

    val type = addErrorEffect(msgFromDb, returnType, statement)
    statement.type = type

    val receiverType = statement.receiver.type
    if (receiverType?.errors?.isNotEmpty() == true &&
        statement.selectorName != "orPANIC" &&
        statement.selectorName != "orValue" &&
        statement.selectorName != "ifError") {

        statement.receiver.token.compileError("Can't send message to ${statement.receiver.type} that can contain error, use orValue: | orPANIC | ifError: [it]")
    }



    if (GlobalVariables.isLspMode) {
        onEachStatement!!(statement, currentScope, previousScope, statement.token.file) // message
    }
}


fun replaceAllGenericsToRealTypeRecursive(
    type: Type.UserLike,
    letterToRealType: MutableMap<String, Type>,
    receiverGenericsTable: MutableMap<String, Type>
): Type.UserLike {
    val newResolvedTypeArgs2 = mutableListOf<Type>()

    val copyType = type.copy()

    copyType.typeArgumentList.forEach { typeArg ->
        val isSingleGeneric = typeArg.name.isGeneric()

        if (isSingleGeneric) {
            var resolvedLetterType = letterToRealType[typeArg.name] ?: receiverGenericsTable[typeArg.name]

            if (resolvedLetterType != null) {
                resolvedLetterType = resolvedLetterType.cloneAndChangeBeforeGeneric(typeArg.name)
                newResolvedTypeArgs2.add(resolvedLetterType)
            } else {
                // we need to know from here, is this generic need to be resolved or not
                newResolvedTypeArgs2.add(typeArg)
            }
        } else if (typeArg is Type.UserLike && typeArg.typeArgumentList.isNotEmpty()) {
            newResolvedTypeArgs2.add(
                replaceAllGenericsToRealTypeRecursive(
                    typeArg,
                    letterToRealType,
                    receiverGenericsTable
                )
            )
        } else {
            newResolvedTypeArgs2.add(typeArg)
        }
    }
    
    val genericLetters = listOf("T", "G")
    val genericLetterToIndex = mutableMapOf<String, Int>()
    genericLetters.forEachIndexed { index, letter ->
        if (index < copyType.typeArgumentList.size) {
            genericLetterToIndex[letter] = index
        }
    }
    
    val newResolvedFields = mutableListOf<KeywordArg>()
    copyType.fields.forEach { field ->
        val fieldType = field.type
        val isSingleGeneric = fieldType.name.isGeneric()

        val newFieldType = if (isSingleGeneric) {
            // First try to get the real type from typeArguments by generic letter order
            val genericIndex = genericLetterToIndex[fieldType.name]
            val resolvedLetterType = if (genericIndex != null) {
                newResolvedTypeArgs2.getOrNull(genericIndex)
            } else {
                letterToRealType[fieldType.name] ?: receiverGenericsTable[fieldType.name]
            }
            resolvedLetterType?.cloneAndChangeBeforeGeneric(fieldType.name) ?: fieldType
        } else if (fieldType is Type.UserLike && fieldType.typeArgumentList.isNotEmpty()) {
            replaceAllGenericsToRealTypeRecursive(
                fieldType,
                letterToRealType,
                receiverGenericsTable
            )
        } else {
            fieldType
        }

        newResolvedFields.add(KeywordArg(field.name, newFieldType))
    }

    return copyType.also {
        it.replaceTypeArguments(newResolvedTypeArgs2)
        it.fields.clear()
        it.fields.addAll(newResolvedFields)
    }
}

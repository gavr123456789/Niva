@file:Suppress("UnusedReceiverParameter")

package frontend.resolver

import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.parser.types.ast.*
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

    type.typeArgumentList.forEachIndexed { i, it ->
        val k = genericLetters[i]
        it.beforeGenericResolvedName = k
    }
}

// goes like
// |List::List::Int -> List::|List::Int
// |List::T         -> List::|T
// found that T is List::Int
fun getTableOfLettersFromType(type: Type.UserLike, typeFromDb: Type.UserLike, result: MutableMap<String, Type>) {
    fun addToResultIfItsGeneric(next1: Type, next2: Type) {
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
            result[type.name] = typeFromDb
        }
    }
    if (type2Count == 0) {
        if (typeFromDb is Type.UnknownGenericType) {
            result[typeFromDb.name] = type
        }
    }
}

// нужно идти по дженерикам обоих типов, и когда дженерико в типа type больеш не будет, взять оставшиеся у returnTypeFromDb
//fun getTableOfLettersFrom_TypeArgumentListOfType(type: Type.UserLike, returnTypeFromDb: Type.UserLike, result: MutableMap<String, Type>) {
//    if (type.typeArgumentList.count() > 3) {
//        throw Exception("Generics with more than 3 params are not supported yet")
//    }
//    val genericLetters = listOf("T", "G", "J")
//
////    val iter = type.typeArgumentList.listIterator()
////    val iter2 = returnTypeFromDb.typeArgumentList.listIterator()
////
////    while (iter.hasNext()) {
////        val next1 = iter.next()
////        val next2 = iter2.next()
////        if (next1 is Type.UserLike && next2 is Type.UserLike) {
////            getTableOfLettersFrom_TypeArgumentListOfType(next1, next2, result)
////        }
////    }
//
//    type.typeArgumentList.forEachIndexed { i, it ->
//        val k = genericLetters[i]
//        if (it is Type.UserLike) {
//
//        } else if (!it.name.isGeneric() ) //&& !(it is Type.UserLike && it.typeArgumentList.isNotEmpty())
//            result[k] = it
//    }
//
//}

// 2!
// 1) выделить отсюда код который рекурсивно собирает все дженерики из аргументов
// 2) запускать этот код отдельно до резолва боди функции
// 3) добавлять результат в type args
// 4) проверять если type args функции содержат такуюже букву которую мы не можем найти в таблице, то искать нам ее не надо
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
    replacerTypeIfItGeneric.typeArgumentList = realTypes
    return replacerTypeIfItGeneric
}


fun Resolver.resolveMessage(
    statement: Message,
    currentScope: MutableMap<String, Type>,
    previousScope: MutableMap<String, Type>
) {
    val previousAndCurrentScope = (previousScope + currentScope).toMutableMap()

    when (statement) {
        is KeywordMsg -> {
            resolveKeywordMsg(statement, previousScope, currentScope)
        }
        is BinaryMsg -> resolveBinaryMsg(statement, previousAndCurrentScope)
        is UnaryMsg -> resolveUnaryMsg(statement, previousAndCurrentScope)
        is StaticBuilder -> resolveStaticBuilder(statement, currentScope, previousScope)
    }

    if (GlobalVariables.isLspMode) {
        onEachStatement!!(statement, currentScope, previousScope, statement.token.file) // message
    }
}


fun replaceAllGenericsToRealTypeRecursive(
    type: Type.UserLike,
    letterToRealType: MutableMap<String, Type>,
    receiverGenericsTable: MutableMap<String, Type>
): Type.UserType {
    val newResolvedTypeArgs2 = mutableListOf<Type>()

    val copyType = type.copy()

    copyType.typeArgumentList.forEach { typeArg ->
        val isSingleGeneric = typeArg.name.isGeneric()

        if (isSingleGeneric) {
            val resolvedLetterType =
                letterToRealType[typeArg.name] ?: receiverGenericsTable[typeArg.name]
            if (resolvedLetterType != null) {
                newResolvedTypeArgs2.add(resolvedLetterType)
                resolvedLetterType.beforeGenericResolvedName = typeArg.name
            } else {
                // we need to know from here, is this generic need to be resolved or not
                newResolvedTypeArgs2.add(typeArg)
//                println("olala, looks like the $typeArg is Known generic")
//                TODO()
            }
        } else if (typeArg is Type.UserLike && type.typeArgumentList.isNotEmpty()) {
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

    return Type.UserType(
        name = copyType.name,
        typeArgumentList = newResolvedTypeArgs2,
        fields = copyType.fields,
        isPrivate = copyType.isPrivate,
        pkg = copyType.pkg,
        protocols = copyType.protocols,
        typeDeclaration = copyType.typeDeclaration
    )
}

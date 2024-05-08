@file:Suppress("UNUSED_PARAMETER", "unused")

package frontend.resolver

import frontend.parser.types.ast.KeyPragma
import main.frontend.meta.Position
import main.frontend.meta.Token
import main.frontend.meta.TokenType
import main.frontend.parser.types.ast.InternalTypes
import main.frontend.parser.types.ast.LiteralExpression
import java.io.File


fun createIntProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    floatType: Type.InternalType,
    doubleType: Type.InternalType,
    intRangeType: Type.InternalType,
    anyType: Type.InternalType,
    charType: Type.InternalType

): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("inc", intType),
            createUnary("dec", intType),
            createUnary("toFloat", floatType),
            createUnary("toDouble", doubleType),
            createUnary("toString", stringType),
            createUnary("toChar", charType),
        ),
        binaryMsgs = mutableMapOf(
            createBinary("==", intType, boolType),
            createBinary("!=", intType, boolType),
            createBinary(">", intType, boolType),
            createBinary("<", intType, boolType),
            createBinary("<=", intType, boolType),
            createBinary(">=", intType, boolType),
            createBinary("+", intType, intType),
            createBinary("-", intType, intType),
            createBinary("*", intType, intType),
            createBinary("%", intType, intType),
            createBinary("/", intType, intType),
            createBinary("..", intType, intRangeType),
            createBinary("..<", intType, intRangeType),

            ),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("plus", intType), intType),
            createKeyword(KeywordArg("to", intType), intRangeType),
            createKeyword(KeywordArg("downTo", intType), intRangeType),
            createKeyword(
                "toDo",
                listOf(
                    KeywordArg("to", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(KeywordArg("do", intType)), anyType))
                ),
                intType
            ),

            createKeyword(
                "untilDo",
                listOf(
                    KeywordArg("until", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(KeywordArg("do", intType)), anyType))
                ),
                intType
            ),

            createKeyword(
                "downToDo",
                listOf(
                    KeywordArg("downTo", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(KeywordArg("do", intType)), anyType))
                ),
                intType
            ),
        ),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}

fun createFloatProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    floatType: Type.InternalType,

    ): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    3.24.mod(3.4)
    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("inc", floatType),
            createUnary("dec", floatType),
            createUnary("toInt", intType),
            createUnary("toString", stringType),

            createUnary("isInfinite", boolType),
            createUnary("isFinite", boolType),
            createUnary("isNaN", boolType),
        ),
        binaryMsgs = mutableMapOf(
            createBinary("==", intType, boolType),
            createBinary("!=", intType, boolType),
            createBinary(">", intType, boolType),
            createBinary("<", intType, boolType),
            createBinary("<=", intType, boolType),
            createBinary(">=", intType, boolType),

            createBinary("==", floatType, boolType),
            createBinary("!=", floatType, boolType),
            createBinary(">", floatType, boolType),
            createBinary("<", floatType, boolType),
            createBinary("<=", floatType, boolType),
            createBinary(">=", floatType, boolType),

            createBinary("+", intType, floatType),
            createBinary("-", intType, floatType),
            createBinary("*", intType, floatType),
            createBinary("%", intType, floatType),
            createBinary("/", intType, floatType),

            createBinary("+", floatType, floatType),
            createBinary("-", floatType, floatType),
            createBinary("*", floatType, floatType),
            createBinary("%", floatType, floatType),
            createBinary("/", floatType, floatType),
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("mod", intType), floatType).rename("get"),

            ),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}


val createUnary = { name: String, returnType: Type ->
    name to UnaryMsgMetaData(name, returnType, "core")
}
val createBinary = { name: String, argType: Type, returnType: Type ->
    name to BinaryMsgMetaData(name, argType, returnType, "core")
}
val createKeyword = { name: String, args: List<KeywordArg>, returnType: Type ->
    name to KeywordMsgMetaData(name, args, returnType, "core")
}

fun createKeyword(arg: KeywordArg, returnType: Type): Pair<String, KeywordMsgMetaData> {
    return arg.name to KeywordMsgMetaData(arg.name, listOf(arg), returnType, "core")
}

fun Pair<String, UnaryMsgMetaData>.emit(str: String): Pair<String, UnaryMsgMetaData> {
    this.second.pragmas.add(createEmitAtttribure(str))
    return this
}

fun Pair<String, KeywordMsgMetaData>.emitKw(str: String): Pair<String, KeywordMsgMetaData> {
    this.second.pragmas.add(createEmitAtttribure(str))
    return this
}

fun Pair<String, KeywordMsgMetaData>.rename(str: String): Pair<String, KeywordMsgMetaData> {
    this.second.pragmas.add(createRenameAtttribure(str))
    return this
}

fun createStringProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    charType: Type.InternalType,
    any: Type.InternalType,
    floatType: Type.InternalType,
    doubleType: Type.InternalType,
    intRangeType: Type.InternalType
): MutableMap<String, Protocol> {


//    val listOfString = Type.UserType(
//        name = "MutableList",
//        typeArgumentList = listOf(stringType),
//        fields = mutableListOf(),
//        pkg = "core",
//    )

    val result = mutableMapOf<String, Protocol>()
    val arithmeticProtocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("trim", stringType),
            createUnary("trimIndent", stringType),
            createUnary("isBlank", boolType),
            createUnary("isEmpty", boolType),
            createUnary("isAlphaNumeric", boolType),
            createUnary("isNotBlank", boolType),
            createUnary("isNotEmpty", boolType),
            createUnary("toInt", intType),
            createUnary("toFloat", floatType),
            createUnary("toDouble", doubleType),
            createUnary("first", charType),
            createUnary("last", charType),
            createUnary("indices", intRangeType).emit("$0.indices"),


            createUnary("echo", unitType),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("+", stringType, stringType),
            createBinary("==", stringType, boolType),
            createBinary("!=", stringType, boolType),
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(
                "replaceWith",
                listOf(KeywordArg("replace", stringType), KeywordArg("with", stringType)), stringType
            )
                .rename("replace"),
            createForEachKeyword(charType, unitType),
            createForEachKeywordIndexed(intType, charType, unitType),
            createFilterKeyword(charType, boolType, stringType),

            createKeyword(KeywordArg("startsWith", stringType), boolType),
            createKeyword(KeywordArg("contains", stringType), boolType),
            createKeyword(KeywordArg("endsWith", stringType), boolType),


            createKeyword(KeywordArg("substring", intType), stringType),
            createKeyword(KeywordArg("substringAfter", stringType), stringType),
            createKeyword(KeywordArg("substringAfterLast", stringType), stringType),
            createKeyword(KeywordArg("substringBefore", stringType), stringType),
            createKeyword(KeywordArg("substringBeforeLast", stringType), stringType),

            createKeyword(
                "fromTo",
                listOf(KeywordArg("from", intType), KeywordArg("to", intType)),
                stringType
            ).rename("substring"),


            createKeyword(KeywordArg("at", intType), charType).rename("get"),
            createKeyword(KeywordArg("drop", intType), stringType),
            createKeyword(KeywordArg("dropLast", intType), stringType),
        ),
    )

    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}


@Suppress("UNUSED_PARAMETER")
fun createBoolProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    any: Type.InternalType,
    genericParam: Type.UnknownGenericType
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            createUnary("not", boolType),
            createUnary("isFalse", boolType),
            createUnary("isTrue", boolType),
            createUnary("echo", unitType),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("==", boolType, boolType),
            createBinary("!=", boolType, boolType),
            createBinary("&&", boolType, boolType),
            createBinary("||", boolType, boolType),
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("or", boolType), boolType),
            createKeyword(KeywordArg("and", boolType), boolType),
            createKeyword(KeywordArg("xor", boolType), boolType),

            createKeyword(KeywordArg("ifTrue", Type.Lambda(mutableListOf(), genericParam)), unitType),
            createKeyword(KeywordArg("ifFalse", Type.Lambda(mutableListOf(), genericParam)), unitType),

            createKeyword(
                "ifTrueIfFalse",

                listOf(
                    KeywordArg("ifTrue", Type.Lambda(mutableListOf(), genericParam)),
                    KeywordArg("ifFalse", Type.Lambda(mutableListOf(), genericParam))
                ),

                genericParam
            ),
            createKeyword(
                "ifFalseIfTrue",

                listOf(
                    KeywordArg("ifFalse", Type.Lambda(mutableListOf(), genericParam)),
                    KeywordArg("ifTrue", Type.Lambda(mutableListOf(), genericParam))
                ),

                genericParam
            )


        ),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}


fun createCharProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    charType: Type.InternalType,
    any: Type.InternalType,
    charRange: Type.InternalType
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()
    val arithmeticProtocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("dec", charType),
            createUnary("inc", charType),
            createUnary("isDigit", boolType),
            createUnary("isLetter", boolType),
            createUnary("isUpperCase", boolType),
            createUnary("isLowerCase", boolType),
            createUnary("isWhitespace", boolType),

            createUnary("isLetterOrDigit", boolType),

            createUnary("lowercase", stringType),
            createUnary("lowercaseChar", charType),
            createUnary("uppercaseChar", charType),

            createUnary("digitToInt", intType),

            createUnary("echo", unitType)

        ),
        binaryMsgs = mutableMapOf(
            createBinary("+", stringType, stringType),
            createBinary("-", stringType, stringType),
            createBinary("==", stringType, boolType),
            createBinary("!=", stringType, boolType),
            createBinary("..", charType, charRange),
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(
                KeywordArg("addInt", intType),
                charType
            ).rename("plus")
        ),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}

fun createNullableAnyProtocols(realType: Type?): MutableMap<String, Protocol> {
    // receiver is already T(on kotlin side)
    // so we need only one generic here
    val unitType = Resolver.defaultTypes[InternalTypes.Unit]!!
    val nothingType = Resolver.defaultTypes[InternalTypes.Nothing]!!
    val genericR = Type.UnknownGenericType("T")

    val realTypeOrNothing = realType ?: nothingType

    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("echonnl", unitType),
            createUnary("unpackOrError", realTypeOrNothing)//.emit("$0!!")
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("unpackOrValue", realTypeOrNothing), realTypeOrNothing)
                .emitKw("($0 ?: $1)"),

            createKeyword(
                KeywordArg(
                    "unpack",
                    Type.Lambda(mutableListOf(KeywordArg("it", realTypeOrNothing)), genericR)
                ),
                unitType
            ),

            createKeyword(
                "unpackOr",
                listOf(
                    KeywordArg(
                        "unpack",
                        Type.Lambda(mutableListOf(KeywordArg("it", realTypeOrNothing)), genericR)
                    ),
                    KeywordArg(
                        "or",
                        genericR
                    ),
                ),

                genericR
            ),
            createKeyword(KeywordArg("unpackOrDo", realTypeOrNothing), realTypeOrNothing)
                .emitKw("$0 ?: $1"),
        ),
    )
    return mutableMapOf(protocol.name to protocol)
}

fun createAnyProtocols(
    unitType: Type.InternalType,
    any: Type.InternalType,
    boolType: Type.InternalType,
    stringType: Type.InternalType

): MutableMap<String, Protocol> {
    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("echonnl", unitType),
            createUnary("toString", stringType),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("==", any, boolType),
            createBinary("!=", any, boolType),
        ),
        keywordMsgs = mutableMapOf(),
    )
    return mutableMapOf(protocol.name to protocol)
}

fun createRangeProtocols(
    rangeType: Type.InternalType,
    boolType: Type.InternalType,

    itType: Type.InternalType,
    unitType: Type.InternalType,
    any: Type.InternalType,
): MutableMap<String, Protocol> {

    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("isEmpty", boolType),
            createUnary("first", itType),
            createUnary("last", itType),
            createUnary("random", itType),
        ),
        binaryMsgs = mutableMapOf(
            createBinary("==", rangeType, boolType),
            createBinary("!=", rangeType, boolType)
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("step", itType), rangeType),

            createForEachKeyword(itType, unitType),
            createForEachKeywordIndexed(itType, itType, unitType),
            createFilterKeyword(itType, boolType, rangeType),

            createKeyword(KeywordArg("contains", itType), boolType)
        ),
    )
    return mutableMapOf(protocol.name to protocol)
}

fun createExceptionForCustomErrors(
    selfType: Type.Union,
): MutableMap<String, Protocol> {

    val nothingType = Resolver.defaultTypes[InternalTypes.Nothing]!!
    val stringType = Resolver.defaultTypes[InternalTypes.String]!!

    val protocol = Protocol(
        name = "core",
        unaryMsgs = mutableMapOf(
            createUnary("throw", nothingType).emit("(throw $0)").also { it.second.addError(selfType) }
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
//            createKeyword(KeywordArg("addSuppressed", errorType), unitType),
        ),
        staticMsgs = mutableMapOf(
            throwWithMessageGenerate(stringType, nothingType),
        )
    )
    return mutableMapOf(protocol.name to protocol)
}

fun throwWithMessageGenerate(stringType: Type, nothingType: Type) =
    createKeyword(KeywordArg("throwWithMessage", stringType), nothingType).emitKw("throwWithMessage($1)")

fun createExceptionProtocols(
    errorType: Type.UnionBranchType,
    unitType: Type.InternalType,
    nothingType: Type.InternalType,
    stringType: Type.InternalType
): MutableMap<String, Protocol> {
    val nothingTypeWithError = nothingType.copyAnyType().also { it.errors = mutableSetOf(errorType) }


    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("throw", nothingTypeWithError).emit("(throw $0)")//.also { it.second.errors.add() }
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("addSuppressed", errorType), unitType),
        ),
        staticMsgs = mutableMapOf(
            throwWithMessageGenerate(stringType, nothingTypeWithError).also { it.second.errors = mutableSetOf(errorType) },
        )
    )
    return mutableMapOf(protocol.name to protocol)
}

fun createStringBuilderProtocols(
    stringBuilderType: Type.UserType,
    anyType: Type.InternalType,
    stringType: Type.InternalType
): MutableMap<String, Protocol> {

    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("toString", stringType),
            createUnary("first", stringType),
            createUnary("last", stringType),

            ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("append", anyType), stringBuilderType),
        ),
        staticMsgs = mutableMapOf(
            createKeyword(KeywordArg("withText", stringType), stringBuilderType),
        )
    )
    return mutableMapOf(protocol.name to protocol)
}


fun createListProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    mutListType: Type.UserType,
    listTypeOfDifferentGeneric: Type.UserType,
    itType: Type.UnknownGenericType,
    differentGenericType: Type.UnknownGenericType,
    sequenceType: Type.UserType,
    pairType: Type.UserType
): MutableMap<String, Protocol> {

    val list = Type.UserType(
        name = "List",
        fields = mutListType.fields,
        typeArgumentList = listOf(Type.UnknownGenericType("T")),
        pkg = "core",
        protocols = mutListType.protocols
    )
    val listOfLists = Type.UserType(
        name = "List",
        fields = mutListType.fields,
        typeArgumentList = listOf(list),
        pkg = "core",
        protocols = mutListType.protocols
    )
    val pairOf2ListsType = Type.UserType(
        name = "Pair",
        fields = mutListType.fields,
        typeArgumentList = listOf(sequenceType, sequenceType), // List<T>, List<G>
        pkg = "core",
        protocols = pairType.protocols
    )

    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("echo", unitType),
            createUnary("first", itType),
            createUnary("firstOrNull", itType),

            createUnary("last", itType),
            createUnary("lastOrNull", itType),
            createUnary("clear", unitType),

            createUnary("toList", list),
            createUnary("toMutableList", mutListType),

            createUnary("shuffled", mutListType),

            createUnary("asSequence", sequenceType),
            createUnary("isEmpty", boolType),
            createUnary("isNotEmpty", boolType),
            createUnary("reversed", mutListType),
            createUnary("sum", mutListType),
            ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
            createForEachKeyword(itType, unitType),
            createOnEach(mutListType, itType, unitType),

            createForEachKeywordIndexed(intType, itType, unitType),
            createMapKeyword(itType, differentGenericType, listTypeOfDifferentGeneric),
            createMapKeywordIndexed(intType, itType, differentGenericType, listTypeOfDifferentGeneric),
            createFilterKeyword(itType, boolType, mutListType),

            createKeyword(KeywordArg("add", itType), unitType),
            createKeyword(KeywordArg("at", intType), itType).rename("get"),
            createKeyword(KeywordArg("at", intType), itType).rename("get"),
            createKeyword(KeywordArg("atOrNull", intType), Type.NullableType(itType)).rename("getOrNull"),
            createKeyword(KeywordArg("removeAt", intType), unitType),
            createKeyword(KeywordArg("remove", itType), unitType),
            createKeyword(KeywordArg("contains", itType), unitType),
            createKeyword(KeywordArg("addAll", mutListType), boolType),
            createKeyword(KeywordArg("drop", intType), mutListType),
            createKeyword(KeywordArg("dropLast", intType), mutListType),
            createKeyword(KeywordArg("chunked", intType), listOfLists),

            createKeyword(KeywordArg("joinWith", stringType), stringType).rename("joinToString"),
            createKeyword(
                KeywordArg(
                    "joinTransform",
                    Type.Lambda(
                        mutableListOf(KeywordArg("transform", itType)),
                        differentGenericType
                    )
                ),
                stringType
            ).rename("joinToString"),

            createKeyword(
                KeywordArg(
                    "sortedBy",
                    Type.Lambda(
                        mutableListOf(KeywordArg("transform", itType)),
                        differentGenericType
                    )
                ),
                mutListType
            ),

            createKeyword(
                "joinWithTransform",
                listOf(
                    KeywordArg(
                        "joinWith",
                        stringType
                    ),
                    KeywordArg(
                        "transform",
                        Type.Lambda(
                            mutableListOf(KeywordArg("transform", itType)),
                            differentGenericType
                        )
                    )
                ),
                stringType
            ),

            // fold
            createKeyword(
                "injectInto",
                listOf(
                    KeywordArg(
                        "inject",
                        differentGenericType
                    ),
                    KeywordArg(
                        "info",
                        Type.Lambda(
                            mutableListOf(KeywordArg("acc", differentGenericType), KeywordArg("each", itType)),
                            differentGenericType
                        )
                    )
                ),
                differentGenericType
            ).rename("fold"),


            // reduce
            createKeyword(
                "reduce",
                listOf(
                    KeywordArg(
                        "reduce",
                        Type.Lambda(
                            mutableListOf(KeywordArg("acc", itType), KeywordArg("each", itType)),
                            differentGenericType
                        )
                    )
                ),
                itType
            ),

            // partition, fun <T> Iterable<T>.partition(predicate: (T) -> Boolean): Pair<List<T>, List<T>>
            createKeyword(
                "partition",
                listOf(
                    KeywordArg(
                        "predicate",
                        Type.Lambda(
                            mutableListOf(KeywordArg("predicate", itType)),
                            boolType
                        )
                    )
                ),
                pairOf2ListsType
            ),
            // sumOf, fun <T> Iterable<T>.sumOf(selector: (T) -> Int): Int
            createSumOf(itType),
            createFind(itType, boolType),

            createKeyword(
                "viewFromTo",
                listOf(KeywordArg("viewFrom", intType), KeywordArg("to", intType)),
                mutListType
            ).rename("subList"),

            createKeyword(
                "atPut",
                listOf(
                    KeywordArg("at", intType),
                    KeywordArg("put", itType)
                ),
                unitType
            ).rename("set"),


            )
    )

    return mutableMapOf(collectionProtocol.name to collectionProtocol)
}

fun createSumOf(itType: Type) =
    createKeyword(
        "sumOf",
        listOf(
            KeywordArg(
                "sumOf",
                Type.Lambda(
                    mutableListOf(KeywordArg("predicate", itType)),
                    itType
                )
            )
        ),
        itType
    )

fun createFind(itType: Type, boolType: Type): Pair<String, KeywordMsgMetaData> {

    val nullableIfNeeded = if (itType is Type.NullableType) itType else Type.NullableType(itType)

    return createKeyword(
        "find",
        listOf(
            KeywordArg(
                "find",
                Type.Lambda(
                    mutableListOf(KeywordArg("predicate", itType)),
                    boolType
                )
            )
        ),
        nullableIfNeeded
    )
}




fun createSetProtocols(
    intType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    setType: Type.UserType,
    setTypeOfDifferentGeneric: Type.UserType,
    itType: Type.UnknownGenericType,
    differentGenericType: Type.UnknownGenericType,
    listType: Type.UserType
): MutableMap<String, Protocol> {
    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("echo", unitType),
            createUnary("clear", unitType),
            createUnary("first", itType),
            createUnary("last", itType),
            createUnary("toList", listType),
            createUnary("toMutableList", listType),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("==", setType, boolType),
            createBinary("!=", setType, boolType),
            createBinary("+", setType, setType),
            createBinary("+", itType, setType),
            createBinary("-", setType, setType),
            createBinary("-", itType, setType),

            ),
        keywordMsgs = mutableMapOf(
            createForEachKeyword(itType, unitType),
            createOnEach(setType, itType, unitType),

            createMapKeyword(itType, differentGenericType, setTypeOfDifferentGeneric),
            createMapKeywordIndexed(intType, itType, differentGenericType, setTypeOfDifferentGeneric),


            createFilterKeyword(itType, boolType, setType),

            createKeyword(KeywordArg("add", itType), unitType),
            createKeyword(KeywordArg("remove", itType), boolType),
            createKeyword(KeywordArg("addAll", setType), boolType),
            createKeyword(KeywordArg("intersect", setType), setType),
            createKeyword(KeywordArg("contains", itType), boolType),
            createKeyword(KeywordArg("containsAll", setType), boolType),
        )
    )

    return mutableMapOf(collectionProtocol.name to collectionProtocol)
}

fun createCompilerProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    typeType: Type.UserType
): MutableMap<String, Protocol> {
    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(),
        staticMsgs = mutableMapOf(
            createKeyword(KeywordArg("getName", intType), stringType),
            createKeyword(KeywordArg("getType", intType), typeType),
        )
    )

    return mutableMapOf(collectionProtocol.name to collectionProtocol)
}

private fun createOnEach(
    collectionType: Type.UserType,
    genericTypeOfSetElements: Type,
    unitType: Type.InternalType
) = createKeyword(
    "onEach",
    listOf(
        KeywordArg(
            "onEach",
            Type.Lambda(
                mutableListOf(
                    KeywordArg("onEach", genericTypeOfSetElements)
                ),
                unitType
            )
        )
    ),
    collectionType
)

private fun createForEachKeyword(
    genericTypeOfSetElements: Type,
    unitType: Type.InternalType
) = createKeyword(
    "forEach",
    listOf(
        KeywordArg(
            "forEach",
            Type.Lambda(
                mutableListOf(
                    KeywordArg("forEach", genericTypeOfSetElements)
                ),
                unitType
            )
        )
    ),
    unitType
)

private fun createForEachKeywordIndexed(
    intType: Type.InternalType,
    itType: Type,
    unitType: Type.InternalType
) = createKeyword(
    "forEachIndexed",
    listOf(
        KeywordArg(
            "forEachIndexed",
            Type.Lambda(
                mutableListOf(
                    KeywordArg("i", intType),
                    KeywordArg("it", itType),
                ),
                unitType
            )
        )
    ),
    unitType
)

private fun createMapKeywordIndexed(
    intType: Type.InternalType,
    itType: Type,

    differentGenericType: Type.UnknownGenericType,
    listTypeOfDifferentGeneric: Type.UserType
) = createKeyword(
    "mapIndexed",
    listOf(
        KeywordArg(
            "mapIndexed",
            Type.Lambda(
                mutableListOf(
                    KeywordArg("i", intType),
                    KeywordArg("it", itType),
                ),
                differentGenericType
            )
        )
    ),
    listTypeOfDifferentGeneric
)


private fun createStringLiteral(lexeme: String) = LiteralExpression.StringExpr(
    Token(
        kind = TokenType.String,
        lexeme = """"$lexeme"""",
        line = 0,
        pos = Position(0, 0),
        relPos = Position(0, 0),
        file = File("."),
    )
)

fun createStringPragma(k: String, v: String) =
    KeyPragma(name = k, value = createStringLiteral(v))

fun createRenameAtttribure(v: String) =
    createStringPragma("rename", v)

fun createEmitAtttribure(v: String) =
    createStringPragma("emit", v)

fun createMapProtocols(
    intType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    mapType: Type.UserType,
    mapTypeOfDifferentGeneric: Type.UserType,
    keyType: Type.UnknownGenericType,
    valueType: Type.UnknownGenericType,
    setType: Type.UserType,
    setTypeOfDifferentGeneric: Type.UserType,
//    entryType: Type.UserType
): MutableMap<String, Protocol> {


    val result = mutableMapOf<String, Protocol>()

    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("echo", unitType),
            createUnary("clear", unitType),
            createUnary("keys", setType).emit("$0.keys"),
            createUnary("values", setTypeOfDifferentGeneric).emit("$0.values"),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("+", mapType, mapType),
            createBinary("-", mapType, valueType)
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(
                "forEach",
                listOf(
                    KeywordArg(
                        "forEach",
                        Type.Lambda(
                            mutableListOf(
                                KeywordArg("key", keyType),
                                KeywordArg("value", valueType),
                            ),
                            unitType
                        )
                    )
                ),
                unitType
            ),

            createKeyword(
                "map",
                listOf(
                    KeywordArg(
                        "map",
                        Type.Lambda(
                            mutableListOf(
//                                KeywordArg("e", entryType),
                                KeywordArg("key", keyType),
                                KeywordArg("value", valueType),
                            ),
                            unitType,
                            specialFlagForLambdaWithDestruct = true
                        )
                    )
                ),
                unitType,

                ),
            createKeyword(
                "filter",
                listOf(
                    KeywordArg(
                        "filter",
                        Type.Lambda(
                            mutableListOf(
//                                KeywordArg("e", entryType),

                                KeywordArg("key", keyType),
                                KeywordArg("value", valueType),
                            ),
                            unitType,
                            specialFlagForLambdaWithDestruct = true
                        )
                    )
                ),
                unitType,

                ),

            createKeyword(
                "atPut",
                listOf(
                    KeywordArg("at", keyType),
                    KeywordArg("put", valueType)
                ),
                unitType
            )
                .rename("set"),

            createKeyword(KeywordArg("at", keyType), Type.NullableType(valueType))
                .rename("get"),

            createKeyword(KeywordArg("remove", keyType), Type.NullableType(keyType)),
            createKeyword(KeywordArg("addAll", mapType), unitType),
            createKeyword(KeywordArg("containsKey", keyType), boolType),
            createKeyword(KeywordArg("containsValue", valueType), boolType)
        ),

        )

    result[collectionProtocol.name] = collectionProtocol
    return result
}

private fun createMapKeyword(
    genericTypeOfListElements: Type,
    differentGenericType: Type.UnknownGenericType,
    listTypeOfDifferentGeneric: Type.UserType
) = createKeyword(
    KeywordArg(
        "map",
        Type.Lambda(
            mutableListOf(KeywordArg("transform", genericTypeOfListElements)),
            differentGenericType
        ) // return list map of type of last expression
    ),
    listTypeOfDifferentGeneric
)

private fun createFilterKeyword(
    genericTypeOfSetElements: Type,
    boolType: Type.InternalType,
    returnType: Type
) = createKeyword(
    KeywordArg(
        "filter",
        Type.Lambda(mutableListOf(KeywordArg("filter", genericTypeOfSetElements)), boolType)
    ),
    returnType
)

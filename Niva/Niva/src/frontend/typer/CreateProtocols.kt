@file:Suppress("UNUSED_PARAMETER")

package frontend.typer

import frontend.meta.Position
import frontend.meta.Token
import frontend.meta.TokenType
import frontend.parser.parsing.CodeAttribute
import frontend.parser.types.ast.LiteralExpression
import java.io.File


fun createIntProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    floatType: Type.InternalType,
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
            createKeyword("plus", KeywordArg("plus", intType), intType),
            createKeyword("to", KeywordArg("to", intType), intRangeType),
            createKeyword("downTo", KeywordArg("downTo", intType), intRangeType),
            createKeyword(
                "toDo",
                listOf(
                    KeywordArg("to", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(TypeField("do", intType)), anyType))
                ),
                intType
            ),

            createKeyword(
                "untilDo",
                listOf(
                    KeywordArg("until", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(TypeField("do", intType)), anyType))
                ),
                intType
            ),

            createKeyword(
                "downToDo",
                listOf(
                    KeywordArg("downTo", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(TypeField("do", intType)), anyType))
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
    intRangeType: Type.InternalType,
    anyType: Type.InternalType,

    ): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("inc", intType),
            createUnary("dec", intType),
            createUnary("toInt", floatType),
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
        ),
        keywordMsgs = mutableMapOf(

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

fun createKeyword(name: String, arg: KeywordArg, returnType: Type): Pair<String, KeywordMsgMetaData> {
    return name to KeywordMsgMetaData(name, listOf(arg), returnType, "core")
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
    floatType: Type.InternalType
): MutableMap<String, Protocol> {


    val listOfString = Type.UserType(
        name = "List",
        typeArgumentList = listOf(stringType),
        fields = mutableListOf(),
        pkg = "core",
    )

    val result = mutableMapOf<String, Protocol>()
    val arithmeticProtocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("isDigit", boolType),
            createUnary("isBlank", boolType),
            createUnary("isEmpty", boolType),
            createUnary("isAlphaNumeric", boolType),
            createUnary("isNotBlank", boolType),
            createUnary("isNotEmpty", boolType),
            createUnary("trimIndent", stringType),
            createUnary("toInt", intType),
            createUnary("toFloat", floatType),


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
                listOf(KeywordArg("replace", intType), KeywordArg("with", intType)), stringType
            )
                .rename("replace"),
            createForEachKeyword(charType, unitType),
            createForEachKeywordIndexed(intType, charType, unitType),
            createFilterKeyword(charType, boolType, stringType),

            createKeyword("substring", KeywordArg("substring", intType), stringType),
            createKeyword("fromTo", listOf(KeywordArg("from", intType), KeywordArg("to", intType)) , stringType).rename("substring"),


            createKeyword("at", KeywordArg("at", intType), charType).rename("get"),
            createKeyword("drop", KeywordArg("drop", intType), stringType),
            createKeyword("split", KeywordArg("split", stringType), listOfString),
            createKeyword("dropLast", KeywordArg("dropLast", intType), stringType),
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
            createKeyword("or", KeywordArg("or", boolType), boolType),
            createKeyword("and", KeywordArg("and", boolType), boolType),
            createKeyword("xor", KeywordArg("xor", boolType), boolType),

            createKeyword("ifTrue", KeywordArg("ifTrue", Type.Lambda(
                mutableListOf(
//                    TypeField("x", genericParam)
                ),
                unitType
            )), unitType),


            createKeyword(
                "ifFalse", KeywordArg(
                    "ifFalse", Type.Lambda(
                        mutableListOf(
//                            TypeField("x", genericParam)
                        ),
                        unitType
                    )
                ), unitType
            ),


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
    any: Type.InternalType
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
        ),
        keywordMsgs = mutableMapOf(),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}

fun createAnyProtocols(
    unitType: Type.InternalType,
    any: Type.InternalType
): MutableMap<String, Protocol> {

    val result = mutableMapOf<String, Protocol>()
    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("echonnl", unitType),
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(),
    )
    result[protocol.name] = protocol
    return result
}

fun createIntRangeProtocols(
    rangeType: Type.InternalType,
    boolType: Type.InternalType,

    intType: Type.InternalType,
    unitType: Type.InternalType,
    any: Type.InternalType
): MutableMap<String, Protocol> {

    val result = mutableMapOf<String, Protocol>()
    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("isEmpty", boolType),
            createUnary("first", intType),
            createUnary("last", intType),
            createUnary("random", intType),
        ),
        binaryMsgs = mutableMapOf(
            createBinary("==", rangeType, boolType),
            createBinary("!=", rangeType, boolType)
        ),
        keywordMsgs = mutableMapOf(
            createKeyword("step", KeywordArg("step", intType), rangeType),

            createKeyword(
                "forEach",
                listOf(
                    KeywordArg(
                        "forEach",
                        Type.Lambda(
                            mutableListOf(
                                TypeField("forEach", intType)
                            ),
                            unitType
                        )
                    )
                ),
                unitType
            ),

            createKeyword("contains", KeywordArg("contains", intType), boolType),


            ),
    )
    result[protocol.name] = protocol
    return result
}


fun createExceptionProtocols(
    errorType: Type.UserType,
    unitType: Type.InternalType,
    nothingType: Type.InternalType,
    stringType: Type.InternalType
): MutableMap<String, Protocol> {

    val result = mutableMapOf<String, Protocol>()
    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
            createKeyword("addSuppressed", KeywordArg("addSuppressed", errorType), unitType),
        ),
        staticMsgs = mutableMapOf(
            createKeyword("throwWithMessage", KeywordArg("throwWithMessage", stringType), nothingType),
        )
    )
    result[protocol.name] = protocol
    return result
}


fun createListProtocols(
    intType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    listType: Type.UserType,
    listTypeOfDifferentGeneric: Type.UserType,
    itType: Type.UnknownGenericType,
    differentGenericType: Type.UnknownGenericType,
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("echo", unitType),
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
            createForEachKeyword(itType, unitType),
            createForEachKeywordIndexed(intType, itType, unitType),
            createMapKeyword(itType, differentGenericType, listTypeOfDifferentGeneric),
            createFilterKeyword(itType, boolType, listType),

            createKeyword("add", KeywordArg("add", itType), unitType),
            createKeyword("at", KeywordArg("at", intType), itType).rename("get"),
            createKeyword("removeAt", KeywordArg("removeAt", intType), unitType),
            createKeyword("addAll", KeywordArg("addAll", listType), boolType),
            createKeyword("atPut", KeywordArg("at", itType), unitType),
            createKeyword("drop", KeywordArg("drop", intType), listType),
            createKeyword("dropLast", KeywordArg("dropLast", intType), listType),

            createKeyword("viewFromTo", listOf(KeywordArg("viewFrom", intType), KeywordArg("to", intType)) , listType).rename("subList"),

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

    result[collectionProtocol.name] = collectionProtocol
    return result
}


fun createSetProtocols(
    intType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    setType: Type.UserType,
    setTypeOfDifferentGeneric: Type.UserType,
    genericTypeOfSetElements: Type.UnknownGenericType,
    differentGenericType: Type.UnknownGenericType,
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("echo", unitType),
            createUnary("removeAll", boolType),
            createUnary("clear", boolType),
        ),
        binaryMsgs = mutableMapOf(
            createBinary("==", setType, boolType),
            createBinary("!=", setType, boolType),
            createBinary("+", setType, setType),
            createBinary("+", genericTypeOfSetElements, setType),
            createBinary("-", setType, setType),
            createBinary("-", genericTypeOfSetElements, setType),

            ),
        keywordMsgs = mutableMapOf(
            createForEachKeyword(genericTypeOfSetElements, unitType),
            createMapKeyword(genericTypeOfSetElements, differentGenericType, setTypeOfDifferentGeneric),
            createFilterKeyword(genericTypeOfSetElements, boolType, setType),

            createKeyword("add", KeywordArg("add", genericTypeOfSetElements), unitType),
            createKeyword("remove", KeywordArg("remove", genericTypeOfSetElements), boolType),

            createKeyword("addAll", KeywordArg("addAll", setType), boolType),

            createKeyword("intersect", KeywordArg("intersect", setType), setType),


            )
    )

    result[collectionProtocol.name] = collectionProtocol
    return result
}

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
                    TypeField("forEach", genericTypeOfSetElements)
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
                    TypeField("i", intType),
                    TypeField("it", itType),
                ),
                unitType
            )
        )
    ),
    unitType
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

fun createCodeAttribute(k: String, v: String) =
    CodeAttribute(name = k, value = createStringLiteral(v))

fun createRenameAtttribure(v: String) =
    createCodeAttribute("rename", v)


fun createMapProtocols(
    intType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    mapType: Type.UserType,
    mapTypeOfDifferentGeneric: Type.UserType,
    keyType: Type.UnknownGenericType,
    valueType: Type.UnknownGenericType,
    setType: Type.UserType,
    listType: Type.UserType,

    ): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("echo", unitType),
            createUnary("clear", unitType),
            createUnary("values", listType),
            createUnary("keys", setType),

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
                                TypeField("key", keyType),
                                TypeField("value", valueType),
                            ),
                            unitType
                        )
                    )
                ),
                unitType
            ),
            createMapKeyword(keyType, valueType, mapTypeOfDifferentGeneric),
            createFilterKeyword(keyType, boolType, mapType),

            createKeyword(
                "atPut",
                listOf(
                    KeywordArg("at", keyType),
                    KeywordArg("put", valueType)
                ),
                unitType
            )
                .rename("set"),

            createKeyword("at", KeywordArg("at", keyType), valueType)
                .rename("get"),

            createKeyword("remove", KeywordArg("remove", keyType), intType),
            createKeyword("putAll", KeywordArg("addAll", mapType), unitType),
            createKeyword("containsKey", KeywordArg("containsKey", keyType), boolType),
            createKeyword("containsValue", KeywordArg("containsValue", valueType), boolType),


            )
    )

    result[collectionProtocol.name] = collectionProtocol
    return result
}

private fun createMapKeyword(
    genericTypeOfListElements: Type,
    differentGenericType: Type.UnknownGenericType,
    listTypeOfDifferentGeneric: Type.UserType
) = createKeyword(
    "map",
    KeywordArg(
        "map",
        Type.Lambda(
            mutableListOf(TypeField("transform", genericTypeOfListElements)),
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
    "filter",
    KeywordArg(
        "filter",
        Type.Lambda(mutableListOf(TypeField("filter", genericTypeOfSetElements)), boolType)
    ),
    returnType
)

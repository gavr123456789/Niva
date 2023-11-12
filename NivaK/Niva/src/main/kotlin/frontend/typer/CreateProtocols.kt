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


val createUnary = { name: String, returnType: Type.InternalType ->
    name to UnaryMsgMetaData(name, returnType, "core")
}
val createBinary = { name: String, argType: Type, returnType: Type.InternalType ->
    name to BinaryMsgMetaData(name, argType, returnType, "core")
}
val createKeyword = { name: String, args: List<KeywordArg>, returnType: Type.InternalType ->
    name to KeywordMsgMetaData(name, args, returnType, "core")
}

fun createKeyword(name: String, arg: KeywordArg, returnType: Type): Pair<String, KeywordMsgMetaData> {
    return name to KeywordMsgMetaData(name, listOf(arg), returnType, "core")
}

fun createStringProtocols(
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
            createUnary("length", stringType),
            createUnary("isDigit", boolType),
            createUnary("isBlank", boolType),
            createUnary("isEmpty", boolType),
            createUnary("isAlphaNumeric", boolType),
            createUnary("isNotBlank", boolType),
            createUnary("isNotEmpty", boolType),
            createUnary("echo", unitType),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("+", stringType, stringType),
            createBinary("==", stringType, boolType),
            createBinary("!=", stringType, boolType),
        ),
        keywordMsgs = mutableMapOf(
            createKeyword("get", KeywordArg("get", intType), charType),
            createKeyword("drop", KeywordArg("drop", intType), stringType),
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
    any: Type.InternalType
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            createUnary("not", boolType),
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
            createUnary("echo", unitType)
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
    genericTypeOfListElements: Type.UnknownGenericType,
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
            createKeyword(
                "forEach",
                listOf(
                    KeywordArg(
                        "forEach",
                        Type.Lambda(
                            mutableListOf(
                                TypeField("forEach", genericTypeOfListElements)
                            ),
                            unitType
                        )
                    )
                ),
                unitType
            ),
            createKeyword(
                "map",
                KeywordArg(
                    "map",
                    Type.Lambda(
                        mutableListOf(TypeField("transform", genericTypeOfListElements)),
                        differentGenericType
                    ) // return list map of type of last expression
                ),
                listTypeOfDifferentGeneric
            ),
            createKeyword(
                "filter",
                KeywordArg(
                    "filter",
                    Type.Lambda(mutableListOf(TypeField("filter", genericTypeOfListElements)), boolType)
                ),
                listType
            ),

            createKeyword("add", KeywordArg("add", genericTypeOfListElements), unitType),
            createKeyword("get", KeywordArg("get", intType), genericTypeOfListElements),
            createKeyword("removeAt", KeywordArg("removeAt", intType), unitType),
            createKeyword("addAll", KeywordArg("addAll", listType), boolType),
            createKeyword("atPut", KeywordArg("at", genericTypeOfListElements), unitType),
            createKeyword("drop", KeywordArg("drop", intType), listType),
            createKeyword("dropLast", KeywordArg("dropLast", intType), listType),
            createKeyword(
                "atPut",
                listOf(
                    KeywordArg("at", intType),
                    KeywordArg("put", genericTypeOfListElements)
                ),
                unitType
            )
                .also { it.second.pragmas.add(createRenameAtttribure("set")) },


            )
    )

    result[collectionProtocol.name] = collectionProtocol
    return result
}

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
    genericTypeOfListElements: Type.UnknownGenericType,
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
            createKeyword(
                "forEach",
                listOf(
                    KeywordArg(
                        "forEach",
                        Type.Lambda(
                            mutableListOf(
                                TypeField("key", genericTypeOfListElements),
                                TypeField("value", differentGenericType),
                            ),
                            unitType
                        )
                    )
                ),
                unitType
            ),
            createKeyword(
                "map",
                KeywordArg(
                    "map",
                    Type.Lambda(
                        mutableListOf(TypeField("transform", genericTypeOfListElements)),
                        differentGenericType
                    ) // return list map of type of last expression
                ),
                mapTypeOfDifferentGeneric
            ),
            createKeyword(
                "filter",
                KeywordArg(
                    "filter",
                    Type.Lambda(mutableListOf(TypeField("filter", genericTypeOfListElements)), boolType)
                ),
                mapType
            ),

            createKeyword(
                "atPut",
                listOf(
                    KeywordArg("at", genericTypeOfListElements),
                    KeywordArg("put", differentGenericType)
                ),
                unitType
            )
                .also { it.second.pragmas.add(createRenameAtttribure("set")) },

            createKeyword("at", KeywordArg("at", genericTypeOfListElements), differentGenericType)
                .also { it.second.pragmas.add(createRenameAtttribure("get")) },

            createKeyword("removeAt", KeywordArg("removeAt", intType), intType),
            createKeyword("addAll", KeywordArg("addAll", mapType), boolType)


        )
    )

    result[collectionProtocol.name] = collectionProtocol
    return result
}

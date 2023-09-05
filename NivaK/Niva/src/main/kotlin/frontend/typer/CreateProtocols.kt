package frontend.typer


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
            createUnary("str", stringType),
            createUnary("echo", unitType),
            createUnary("inc", intType),
            createUnary("dec", intType),
            createUnary("toFloat", floatType),
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

            ),
        keywordMsgs = mutableMapOf(
            createKeyword("plus", KeywordArg("plus", intType), intType),
            createKeyword(
                "toDo",
                listOf(
                    KeywordArg("to", intType),
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
    name to UnaryMsgMetaData(name, returnType)
}
val createBinary = { name: String, argType: Type, returnType: Type.InternalType ->
    name to BinaryMsgMetaData(name, argType, returnType)
}
val createKeyword = { name: String, args: List<KeywordArg>, returnType: Type.InternalType ->
    name to KeywordMsgMetaData(name, args, returnType)
}

fun createKeyword(name: String, arg: KeywordArg, returnType: Type.InternalType): Pair<String, KeywordMsgMetaData> {
    return name to KeywordMsgMetaData(name, listOf(arg), returnType)
}

fun createStringProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    @Suppress("UNUSED_PARAMETER")
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    charType: Type.InternalType,
    any: Type.InternalType
): MutableMap<String, Protocol> {

    val result = mutableMapOf<String, Protocol>()
    val arithmeticProtocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("get", charType),
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

@Suppress("UNUSED_PARAMETER")
fun createListProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    listType: Type.InternalType,
    anyType: Type.InternalType,
    unknownGenericType: Type.InternalType
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val w = mutableListOf(1)
    w.map { }


    val arithmeticProtocol = Protocol(
        name = "arithmetic",
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
                        Type.Lambda(mutableListOf(TypeField("forEach", unknownGenericType)), unknownGenericType)
                    )
                ),
                intType
            ),
            createKeyword(
                "map",
                listOf(
                    KeywordArg(
                        "map", Type.Lambda(mutableListOf(TypeField("map", unknownGenericType)), unknownGenericType)
                    )
                ),
                listType
            ),
            createKeyword(
                "filter",
                listOf(
                    KeywordArg("filter", Type.Lambda(mutableListOf(TypeField("filter", unknownGenericType)), boolType))
                ),
                listType
            ),

            createKeyword("add", KeywordArg("add", unknownGenericType), unitType),
            createKeyword("removeAt", KeywordArg("removeAt", intType), unknownGenericType),
            createKeyword("addAll", KeywordArg("addAll", listType), boolType),


            )
    )

    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}

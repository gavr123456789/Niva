package frontend.typer


fun createIntProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    floatType: Type.InternalType,

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
            createBinary("+", intType, intType),
            createBinary("-", intType, intType),
            createBinary("*", intType, intType),
            createBinary("/", intType, intType),
        ),
        keywordMsgs = mutableMapOf(),
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
    charType: Type.InternalType
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
    boolType: Type.InternalType
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            createUnary("not", boolType),
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

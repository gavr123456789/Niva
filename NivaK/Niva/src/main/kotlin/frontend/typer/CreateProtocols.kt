package frontend.typer

import frontend.parser.types.ast.KeywordDeclarationArg

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
val createKeyword = { name: String, returnType: Type.InternalType, args: List<KeywordArg> ->
    name to KeywordMsgMetaData(name, args, returnType)
}

fun createKeyword(name: String, returnType: Type.InternalType, arg: KeywordArg): Pair<String, KeywordMsgMetaData> {
    return name to KeywordMsgMetaData(name, listOf(arg), returnType)
}

fun createStringProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
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
        ),
        binaryMsgs = mutableMapOf(
            createBinary("+", stringType, stringType),
            createBinary("==", stringType, boolType),
            createBinary("!=", stringType, boolType),
        ),
        keywordMsgs = mutableMapOf(),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}


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
            createKeyword("or", boolType, KeywordArg("or", boolType)),
            createKeyword("and", boolType, KeywordArg("or", boolType)),
            createKeyword("xor", boolType, KeywordArg("or", boolType)),
        ),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}

package frontend.typer

fun createIntProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            "str" to UnaryMsgMetaData(
                name = "str",
                returnType = stringType
            ),
            "echo" to UnaryMsgMetaData(
                name = "echo",
                returnType = unitType
            ),
            "inc" to UnaryMsgMetaData(
                name = "inc",
                returnType = intType
            ),
            "dec" to UnaryMsgMetaData(
                name = "dec",
                returnType = intType
            )

        ),
        binaryMsgs = mutableMapOf(
            "==" to BinaryMsgMetaData(
                name = "+",
                returnType = boolType
            ),
            "!=" to BinaryMsgMetaData(
                name = "+",
                returnType = boolType
            ),
            "+" to BinaryMsgMetaData(
                name = "+",
                returnType = intType
            ),
            "-" to BinaryMsgMetaData(
                name = "-",
                returnType = intType
            ),
            "*" to BinaryMsgMetaData(
                name = "*",
                returnType = intType
            ),
            "/" to BinaryMsgMetaData(
                name = "/",
                returnType = intType
            )
        ),
        keywordMsgs = mutableMapOf(),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}



fun createStringProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,

): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            "length" to UnaryMsgMetaData(
                name = "+",
                returnType = stringType
            ),
        ),
        binaryMsgs = mutableMapOf(
            "+" to BinaryMsgMetaData(
                name = "+",
                returnType = stringType
            ),
            "==" to BinaryMsgMetaData(
                name = "+",
                returnType = boolType
            ),
            "!=" to BinaryMsgMetaData(
                name = "+",
                returnType = boolType
            ),
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
    boolType: Type.InternalType,

    ): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(

        ),
        binaryMsgs = mutableMapOf(

            "==" to BinaryMsgMetaData(
                name = "+",
                returnType = boolType
            ),
            "!=" to BinaryMsgMetaData(
                name = "+",
                returnType = boolType
            ),
        ),
        keywordMsgs = mutableMapOf(),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}

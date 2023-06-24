package frontend.typer

fun createIntProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType
): MutableList<Protocol> {
    val result = mutableListOf<Protocol>()

    val arithmeticProtocol = Protocol(
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
    result.add(arithmeticProtocol)


    return result
}

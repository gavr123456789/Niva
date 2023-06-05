import frontend.parser.Parser
import frontend.parser.statements
import frontend.parser.types.*
import org.testng.annotations.Test

class ParserTest {
    @Test
    fun varDeclaration() {
        val source = "x::int = 1"
        val ast = getAst(source)
        println("ast.count = ${ast.count()}")
        assert(ast.count() == 1)
        println("ast = $ast")
        println("ast[0] = ${ast[0]}")

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name == "x")
        assert(declaration.value.type == "int")
        assert(declaration.value.str == "1")
    }

    @Test
    fun varDeclWithTypeInfer() {
        val source = "x = 1"
        val ast = getAst(source)
        assert(ast.count() == 1)

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name == "x")
        assert(declaration.value.type == "int")
        assert(declaration.value.str == "1")
    }

    @Test
    fun collectionList() {
        val source = "{1 2 3}"
        val ast = getAst(source)
        assert(ast.count() == 1)

        val declaration: MessageCall = ast[0] as MessageCall
        val messages = declaration.messages
        assert(messages.isEmpty())
        val list = declaration.receiver as ListCollection
        assert(list.type == "{int}")
        assert(list.initElements.count() == 3)
    }

    @Test
    fun literalInt() {
        val source = "1.1"
        val ast = getAst(source)
        assert(ast.count() == 1)

        val declaration: MessageCall = ast[0] as MessageCall
        val messages = declaration.messages
        assert(messages.isEmpty())
//        val list = declaration.receiver as ListCollection
//        assert(list.type == "{int}")
//        assert(list.initElements.count() == 3)
    }

    @Test
    fun varDeclWithBinary() {
        val source = "x = 1 + 1"
        val ast = getAst(source)
        assert(ast.count() == 1)

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name == "x")
        val messages = (declaration.value as MessageCall).messages
        assert(messages.count() == 1)
        val binaryMsg = messages[0] as BinaryMsg
        assert(binaryMsg.selectorName == "+")
    }

    @Test
    fun string() {
        val source = "\"sas\""
        val ast = getAst(source)
        assert(ast.count() == 1)

        val declaration = ast[0] as MessageCall

        assert(declaration.str == "\"sas\"")
        assert(declaration.receiver.str == "\"sas\"")
        assert(declaration.messages.isEmpty())
    }


    @Test
    fun helloWorld() {
        val source = "\"sas\" echo"
        val ast = getAst(source)
        assert(ast.count() == 1)

        val messageCall = ast[0] as MessageCall
        val unaryMsg = messageCall.messages[0]

        assert(unaryMsg.selectorName == "echo")
        assert(unaryMsg.receiver.type == "string")
        assert(unaryMsg.receiver.str == "\"sas\"")
    }


    @Test
    fun varDeclAndUnaryMsg() {
        val source = """
        x = 1
        x echo
    """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 2)

        val declaration = ast[0] as VarDeclaration
        val messageCall = ast[1] as MessageCall
        val unaryMsg = messageCall.messages[0]
        assert(declaration.name == "x")
        assert(declaration.value.type == "int")
        assert(declaration.value.str == "1")

        assert(unaryMsg.selectorName == "echo")
//        assert(unaryMsg.receiver.type == "int")
        assert(unaryMsg.receiver.str == "x")
    }

    @Test
    fun twoUnary() {
        val source = "3 inc inc"
        val ast = getAst(source)
        assert(ast.count() == 1)

        val messageCall: MessageCall = ast[0] as MessageCall
        assert(messageCall.messages.count() == 2)
        assert(messageCall.messages[0].selectorName == "inc")
        assert(messageCall.messages[1].selectorName == "inc")
        assert(messageCall.messages[1].receiver.type == "int")
    }

    @Test
    fun twoUnaryExpressions() {
        val source = """
            3 inc inc
            1 dec dec
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 2)

        val firstUnary: MessageCall = ast[0] as MessageCall
        assert(firstUnary.messages.count() == 2)
        assert(firstUnary.messages[0].selectorName == "inc")
        assert(firstUnary.messages[1].selectorName == "inc")
        assert(firstUnary.messages[1].receiver.type == "int")
        assert(firstUnary.messages[1].receiver.str == "3")

        val secondUnary: MessageCall = ast[1] as MessageCall
        assert(secondUnary.messages.count() == 2)
        assert(secondUnary.messages[0].selectorName == "dec")
        assert(secondUnary.messages[1].selectorName == "dec")
        assert(secondUnary.messages[1].receiver.type == "int")
        assert(secondUnary.messages[1].receiver.str == "1")
    }

    @Test
    fun allTypeOfMessages() {
        val source = """
            3 inc inc
            1 + 2
            x from: 1 to: 2
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 3)
        val unary = (ast[0] as MessageCall).messages[0]
        val binary = (ast[1] as MessageCall).messages[0]
        val keyword = (ast[2] as MessageCall).messages[0]
        assert(unary.receiver.str == "3")
        assert(binary.receiver.str == "1")
        assert(keyword.receiver.str == "x")
        assert(unary is UnaryMsg)
        assert(binary is BinaryMsg)
        assert(keyword is KeywordMsg)
    }


    @Test
    fun manyBinary() {
        val source = """
            1 + 2 - 2 / 4
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
        val messages = (ast[0] as MessageCall).messages
        assert(messages.count() == 3)
        assert(messages[0].selectorName == "+")
        assert(messages[1].selectorName == "-")
        assert(messages[2].selectorName == "/")
    }

    @Test
    fun keywordOn2lines() {
        val source = """
            x from: 1 
              to: 2
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)

        val keyword = (ast[0] as MessageCall).messages[0] as KeywordMsg
        assert(keyword.receiver.str == "x")
    }

    @Test
    fun twoKeywordOn2lines() {
        val source = """
            x from: 1 
            to: 2
            q do: this
        and: that
                  and: that
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 2)

        val keyword = (ast[0] as MessageCall).messages[0] as KeywordMsg
        assert(keyword.receiver.str == "x")

        val keyword2 = (ast[1] as MessageCall).messages[0] as KeywordMsg
        assert(keyword2.receiver.str == "q")
    }

    @Test
    fun binaryWithUnary() {
        // inc(inc(3)) + dec(dec(2))
        val source = "3 inc inc + 2 dec dec"
        val ast = getAst(source)
        assert(ast.count() == 1)

        val messageCall: MessageCall = ast[0] as MessageCall
        val messages = messageCall.messages
        assert(messages.count() == 1)

        val binaryMsg = messages[0] as BinaryMsg
        assert(binaryMsg.receiver.str == "3")
        assert(binaryMsg.argument.str == "2")

        assert(binaryMsg.unaryMsgsForReceiver.count() == 2)
        assert(binaryMsg.unaryMsgsForArg.count() == 2)

        assert(binaryMsg.unaryMsgsForReceiver[0].selectorName == "inc")
        assert(binaryMsg.unaryMsgsForReceiver[1].selectorName == "inc")

        assert(binaryMsg.unaryMsgsForArg[0].selectorName == "dec")
        assert(binaryMsg.unaryMsgsForArg[1].selectorName == "dec")

    }

    @Test
    fun binaryWithUnary2() {
        // inc(inc(3)) + dec(dec(2))
        val source = "3 inc inc + 2 dec dec + 4 sas inc"
        val ast = getAst(source)
        assert(ast.count() == 1)

        val messageCall: MessageCall = ast[0] as MessageCall
        val messages = messageCall.messages
        assert(messages.count() == 2)

        val binaryMsg = messages[0] as BinaryMsg
        assert(binaryMsg.receiver.str == "3")
        assert(binaryMsg.argument.str == "2")

        assert(binaryMsg.unaryMsgsForReceiver.count() == 2)
        assert(binaryMsg.unaryMsgsForArg.count() == 2)

        assert(binaryMsg.unaryMsgsForReceiver[0].selectorName == "inc")
        assert(binaryMsg.unaryMsgsForReceiver[1].selectorName == "inc")

        assert(binaryMsg.unaryMsgsForArg[0].selectorName == "dec")
        assert(binaryMsg.unaryMsgsForArg[1].selectorName == "dec")

    }

    @Test
    fun keywordMessage() {
        // inc(inc(3)) + dec(dec(2))
        val source = "x from: 3 inc inc + 2 dec dec to: 5"
        val ast = getAst(source)
        assert(ast.count() == 1)

        val unary: MessageCall = ast[0] as MessageCall
        val messages = unary.messages
        assert(messages.count() == 1)
        val keywordMsg = messages[0] as KeywordMsg

        assert(keywordMsg.args.count() == 2)
        assert(keywordMsg.args[0].keywordArg.type == "int")
        assert(keywordMsg.args[0].keywordArg.str == "3")
        assert(keywordMsg.args[0].unaryOrBinaryMsgsForArg.count() == 1)
        assert(keywordMsg.args[0].unaryOrBinaryMsgsForArg[0] is BinaryMsg)
        val binaryFromKeyword = keywordMsg.args[0].unaryOrBinaryMsgsForArg[0] as BinaryMsg
        assert(binaryFromKeyword.selectorName == "+")
        assert(binaryFromKeyword.unaryMsgsForArg.count() == 2)
        assert(binaryFromKeyword.unaryMsgsForReceiver.count() == 2)

        assert(keywordMsg.args[1].keywordArg.type == "int")
        assert(keywordMsg.args[1].keywordArg.str == "5")
        assert(keywordMsg.args[1].unaryOrBinaryMsgsForArg.isEmpty())
    }

    @Test
    fun unaryMessageDeclaration() {
        val source = """
            int inc = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
    }

    @Test
    fun unaryMessageDeclarationWithReturnType() {
        val source = """
            int inc -> int = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
    }
    @Test
    fun binaryMessageDeclaration() {
        val source = """
            int + x = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
    }

    @Test
    fun binaryMessageDeclarationWithReturnType() {
        val source = """
            int + x -> int = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
    }

    @Test
    fun keywordMessageDeclaration() {
        val source = """
            Person noTypeLocalName: q typeAndLocalName: w::int :nothing noLocalNameButType::int = [
              x = 1
              x sas
            ]
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
        val msgDecl = (ast[0] as MessageDeclarationKeyword)
        assert(msgDecl.name == "noTypeLocalName_typeAndLocalName_nothing_noLocalNameButType")
        assert(msgDecl.args.count() == 4)
        assert(msgDecl.body.count() == 2)

        // args
        assert(msgDecl.args[0].name == "noTypeLocalName")
        assert(msgDecl.args[0].localName == "q")
        assert(msgDecl.args[1].name == "typeAndLocalName")
        assert(msgDecl.args[1].localName == "w")
        assert(msgDecl.args[2].name == "nothing")
        assert(msgDecl.args[2].localName == null)
        assert(msgDecl.args[3].name == "noLocalNameButType")
        assert(msgDecl.args[3].localName == null)
        assert(msgDecl.args[3].type == "int")

        // body
        val body = msgDecl.body
        val varDecl = body[0] as VarDeclaration
        val msgCall = body[1] as MessageCall
        assert(varDecl.name == "x")
        assert(varDecl.valueType == "int")
        assert(msgCall.receiver.str == "x")
        assert(msgCall.messages[0].selectorName == "sas")
        assert(msgCall.messages[0].receiver.str == "x")

    }

    @Test
    fun keywordMessageDeclarationWithReturnType() {
        val source = """
            int from: x::int -> int = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
    }

    @Test
    fun typeDeclaration() {
        val source = "type Person name: string age: int"
        val ast = getAst(source)
        assert(ast.count() == 1)
        val typeDeclaration = ast[0] as TypeDeclaration
        assert(typeDeclaration.typeName == "Person")
        assert(typeDeclaration.fields.count() == 2)
        val fields = typeDeclaration.fields

        assert(fields[0].name == "name")
        assert(fields[0].type == "string")
        assert(fields[1].name == "age")
        assert(fields[1].type == "int")
    }

    @Test
    fun typeDeclarationManyLines() {
        val source = """
            type Person 
              name: string 
              age: int
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
        val typeDeclaration = ast[0] as TypeDeclaration
        assert(typeDeclaration.typeName == "Person")
        assert(typeDeclaration.fields.count() == 2)
        val fields = typeDeclaration.fields

        assert(fields[0].name == "name")
        assert(fields[0].type == "string")
        assert(fields[1].name == "age")
        assert(fields[1].type == "int")
    }

    @Test
    fun typeDeclarationManyLinesSemi() {
        val source = """
            type Person name: string 
              age: int
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
        val typeDeclaration = ast[0] as TypeDeclaration
        assert(typeDeclaration.typeName == "Person")
        assert(typeDeclaration.fields.count() == 2)
        val fields = typeDeclaration.fields

        assert(fields[0].name == "name")
        assert(fields[0].type == "string")
        assert(fields[1].name == "age")
        assert(fields[1].type == "int")
    }

    @Test
    fun typeDeclarationManyLinesGeneric() {
        val source = """
            type Person name: string 
              age: int
              `generic
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
        val typeDeclaration = ast[0] as TypeDeclaration
        assert(typeDeclaration.typeName == "Person")
        assert(typeDeclaration.fields.count() == 3)
        assert(typeDeclaration.fields[2].name == "generic")
    }


    @Test
    fun typeDeclarationManyLinesGeneric2() {
        val source = """
        union Shape area: int =
            | Rectangle => width: int height: int
            | Circle    => radius: int
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
        val unionDeclaration = ast[0] as UnionDeclaration
        val branches = unionDeclaration.branches

        assert(unionDeclaration.typeName == "Shape")
        assert(branches.count() == 2)
        assert(branches[0].typeName == "Rectangle")
        assert(branches[0].fields.count() == 2)
        assert(branches[0].fields[0].name == "width")
        assert(branches[0].fields[1].name == "height")
        assert(branches[1].fields.count() == 1)
        assert(branches[1].fields[0].name == "radius")

        assert(branches[0].typeName == "Rectangle")
        assert(branches[1].typeName == "Circle")
    }

    @Test
    fun ifStatement() {
        val source = """
        | 5 > 3 => 1 echo
        | 7 < 6 => [
          someVal asString echo
        ]
        |=> else branch sas
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
        val iF = ast[0] as ControlFlow.IfStatement
        val elseBranches = iF.elseBranch
        val ifBranches = iF.ifBranches
        assert(ifBranches.count() == 2)
        val firstIfExpression = ifBranches[0] as IfBranch.IfBranchSingleExpr
        val secondIfBody = ifBranches[1] as IfBranch.IfBranchWithBody
        assert(elseBranches != null && elseBranches.count() == 1)
    }

    @Test
    fun switchStatement() {
        val source = """
        | y
        | 6 => 8 to: 7
        | 9 inc => "sas" echo
        |=> else branch sas
        """.trimIndent()
        val ast = getAst(source)
        assert(ast.count() == 1)
        assert(ast[0] is ControlFlow.SwitchStatement)
        val switchStatement = ast[0] as ControlFlow.SwitchStatement
        val msgCall = switchStatement.switch as MessageCall
        val receiver = msgCall.receiver
        assert(receiver.str == "y")
        assert(msgCall.messages.isEmpty())


    }


}

fun getAst(source: String): List<Statement> {
    val tokens = lex(source)
    val parser = Parser(file = "", tokens = tokens, source = "sas.niva")
    val ast = parser.statements()
    return ast
}

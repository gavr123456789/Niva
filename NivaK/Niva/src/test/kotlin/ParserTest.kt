import frontend.parser.parsing.Parser
import frontend.parser.parsing.keyword
import frontend.parser.parsing.statements
import frontend.parser.types.ast.*
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.io.File

class ParserTest {
    @Test
    fun varDeclaration() {
        val source = "x::int = 1"
        val ast = getAstTest(source)
        println("ast.count = ${ast.count()}")
        assert(ast.count() == 1)
        println("ast = $ast")
        println("ast[0] = ${ast[0]}")

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name == "x")
//        assert(declaration.value.type?.name == "int")
        assert(declaration.value.str == "1")
    }

    @Test
    fun varDeclWithTypeInfer() {
        val source = "x = 1"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name == "x")
//        assert(declaration.value.type?.name == "int")
        assert(declaration.value.str == "1")
    }

    @Test
    fun collectionList() {
        val source = "{1 2 3}"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val list = ast[0] as ListCollection
        assert(list.initElements.count() == 3)
    }

//    @Test
//    fun collectionListOfObjectConstructors() {
//        val source = "{Person age: 1, Person age: 2, Person age: 3}"
//        val ast = getAstTest(source)
//        assert(ast.count() == 1)
//
//        val list = ast[0] as ListCollection
//        assert(list.initElements.count() == 3)
//    }

    @Test
    fun literalInt() {
        val source = "1.1"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        assert(ast[0] is LiteralExpression)
    }

    @Test
    fun varDeclWithBinary() {
        val source = "x = 1 + 1"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name == "x")
        val messages = (declaration.value as MessageSend).messages
        assert(messages.count() == 1)
        val binaryMsg = messages[0] as BinaryMsg
        assert(binaryMsg.selectorName == "+")
    }

    @Test
    fun string() {
        val source = "\"sas\""
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val declaration = ast[0] as LiteralExpression
        assert(declaration.str == "\"sas\"")
    }


    @Test
    fun helloWorld() {
        val source = "\"sas\" echo"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val messageSend = ast[0] as MessageSend
        val unaryMsg = messageSend.messages[0]

        assert(unaryMsg.selectorName == "echo")
//        assert(unaryMsg.receiver.type?.name == "string")
        assert(unaryMsg.receiver.str == "\"sas\"")
    }


    @Test
    fun varDeclAndUnaryMsg() {
        val source = """
        x = 1
        x echo
    """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)

        val declaration = ast[0] as VarDeclaration
        val messageSend = ast[1] as MessageSend
        val unaryMsg = messageSend.messages[0]
        assert(declaration.name == "x")
        assert(declaration.value.str == "1")
        assert(unaryMsg.selectorName == "echo")
        assert(unaryMsg.receiver.str == "x")
    }


    @Test
    fun binaryFirst() {
        val source = "1 + 2 to: 3"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val messageSend = ast[0] as MessageSendKeyword
        assert(messageSend.messages.count() == 1)
        val kwMessage = messageSend.messages[0] as KeywordMsg
        assert(kwMessage.args[0].selectorName == "to")
        assert(kwMessage.receiver is MessageSendBinary)
        val binaryReceiver = kwMessage.receiver as MessageSendBinary
        val binMsg = binaryReceiver.messages[0] as BinaryMsg
        assert(binMsg.receiver.str == "1")
        assert(binMsg.argument.str == "2")
        assert(binMsg.selectorName == "+")
    }

    @Test
    fun manyBinaryFirstBeforeKeyword() {
        val source = "1 + 2 + 2 to: 3"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val messageSend: MessageSend = ast[0] as MessageSend
        assert(messageSend.messages.count() == 1)
        val kwMessage = messageSend.messages[0] as KeywordMsg
        assert(kwMessage.args[0].selectorName == "to")
        assert(kwMessage.receiver is MessageSend)

    }

    @Test
    fun kw() {
        val fakeFile = File("Niva.iml")
        val source = "1 to: 2 + 3"
        val tokens = lex(source, fakeFile)
        val parser = Parser(file = fakeFile, tokens = tokens, source = source)
        parser.keyword()
    }

    @Test
    fun unaryEachReceiverIsPrevious() {
        val source = "person name first"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val messageSend: MessageSend = ast[0] as MessageSend
        val q = messageSend.messages
        val first = q[1]
        Assertions.assertEquals(first.receiver.str, "name")
    }

    @Test
    fun unaryFirst() {
        val source = "1 sas to: 2"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        assert(ast[0] is MessageSend)
    }

    @Test
    fun twoUnary() {
        val source = "3 inc inc"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val messageSend: MessageSend = ast[0] as MessageSend
        assert(messageSend.messages.count() == 2)
        assert(messageSend.messages[0].selectorName == "inc")
        assert(messageSend.messages[1].selectorName == "inc")
    }

    @Test
    fun twoUnaryExpressions() {
        val source = """
            3 inc inc
            1 dec dec
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)

        val firstUnary: MessageSend = ast[0] as MessageSend
        assert(firstUnary.messages.count() == 2)
        assert(firstUnary.messages[0].selectorName == "inc")
        assert(firstUnary.messages[1].selectorName == "inc")
        assert(firstUnary.messages[1].receiver.str == "inc")

        val secondUnary: MessageSend = ast[1] as MessageSend
        assert(secondUnary.messages.count() == 2)
        assert(secondUnary.messages[0].selectorName == "dec")
        assert(secondUnary.messages[1].selectorName == "dec")
        assert(secondUnary.messages[1].receiver.str == "dec")
    }

    @Test
    fun allTypeOfMessages() {
        val source = """
            3 inc inc
            1 + 2
            x from: 1 to: 2
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 3)
        val unary = (ast[0] as MessageSend).messages[0]
        val binary = (ast[1] as MessageSend).messages[0]
        val keyword = (ast[2] as MessageSend).messages[0]
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
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val messages = (ast[0] as MessageSend).messages
        assert(messages.count() == 3)
        assert(messages[0].selectorName == "+")
        assert(messages[1].selectorName == "-")
        assert(messages[2].selectorName == "/")
        assert(messages[1].receiver == messages[0])
        assert(messages[2].receiver == messages[1])
    }

    @Test
    fun keywordOn2lines() {
        val source = """
            x from: 1 inc inc
              to: 2
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val keyword = (ast[0] as MessageSend).messages[0] as KeywordMsg
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
        val ast = getAstTest(source)
        assert(ast.count() == 2)

        val keyword = (ast[0] as MessageSend).messages[0] as KeywordMsg
        assert(keyword.receiver.str == "x")

        val keyword2 = (ast[1] as MessageSend).messages[0] as KeywordMsg
        assert(keyword2.receiver.str == "q")
    }

    @Test
    fun binaryWithUnary() {
        // inc(inc(3)) + dec(dec(2))
        val source = "3 inc inc + 2 dec dec"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val messageSend: MessageSend = ast[0] as MessageSend
        val messages = messageSend.messages
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
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val messageSend: MessageSend = ast[0] as MessageSend
        val messages = messageSend.messages
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
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val unary: MessageSend = ast[0] as MessageSend
        val messages = unary.messages
        assert(messages.count() == 1)
        val keywordMsg = messages[0] as KeywordMsg

        assert(keywordMsg.args.count() == 2)
//        assert(keywordMsg.args[0].keywordArg.type?.name == "int")
        assert(keywordMsg.args[0].keywordArg.str == "3")
//        assert(keywordMsg.args[0].unaryOrBinaryMsgsForArg.count() == 1)
//        assert(keywordMsg.args[0].unaryOrBinaryMsgsForArg[0] is BinaryMsg)
//        val binaryFromKeyword = keywordMsg.args[0].unaryOrBinaryMsgsForArg[0] as BinaryMsg
//        assert(binaryFromKeyword.selectorName == "+")
//        assert(binaryFromKeyword.unaryMsgsForArg.count() == 2)
//        assert(binaryFromKeyword.unaryMsgsForReceiver.count() == 2)

//        assert(keywordMsg.args[1].keywordArg.type?.name == "int")
        assert(keywordMsg.args[1].keywordArg.str == "5")
//        assert(keywordMsg.args[1].unaryOrBinaryMsgsForArg.isEmpty())
    }

    @Test
    fun unaryMessageDeclaration() {
        val source = """
            Int inc = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun unaryMessageDeclarationWithReturnType() {
        val source = """
            Int wew -> Int = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun binaryMessageDeclaration() {
        val source = """
            Int + x = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is MessageDeclarationBinary)
    }

    @Test
    fun binaryMessageDeclarationWithType() {
        val source = """
            Int + x::Int = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is MessageDeclarationBinary)

    }

    @Test
    fun binaryMessageDeclarationWithReturnType() {
        val source = """
            int + x -> int = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAstTest(source)
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
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val msgDecl = (ast[0] as MessageDeclarationKeyword)
        assert(msgDecl.name == "noTypeLocalNameTypeAndLocalNameNothingNoLocalNameButType")
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
//        assert(msgDecl.args[3].type?.name == "int")

        // body
        val body = msgDecl.body
        val varDecl = body[0] as VarDeclaration
        val msgCall = body[1] as MessageSend
        assert(varDecl.name == "x")
//        assert(varDecl.valueType?.name == "int")
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
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun keywordMessageDeclarationNoLocalName() {
        val source = """
            int from::int -> int = [
              x = 1
              y sas
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun keywordMessageDeclarationNoLocalNameLambda() {
        val source = """
            Int x::[Int, Int -> Int] = [
                1 echo
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }


    @Test
    fun typeDeclaration() {
        val source = "type Person name: string age: int"
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val typeDeclaration = ast[0] as TypeDeclaration
        assert(typeDeclaration.typeName == "Person")
        assert(typeDeclaration.fields.count() == 2)
        val fields = typeDeclaration.fields

        assert(fields[0].name == "name")
        assert(fields[0].type?.name == "string")
        assert(fields[1].name == "age")
        assert(fields[1].type?.name == "int")
    }

    @Test
    fun typeDeclarationManyLines() {
        val source = """
            type Person 
              name: string 
              age: int
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val typeDeclaration = ast[0] as TypeDeclaration
        assert(typeDeclaration.typeName == "Person")
        assert(typeDeclaration.fields.count() == 2)
        val fields = typeDeclaration.fields

        assert(fields[0].name == "name")
//        assert(fields[0].type?.name == "string")
        assert(fields[1].name == "age")
//        assert(fields[1].type?.name == "int")
    }

    @Test
    fun typeDeclarationManyLinesSemi() {
        val source = """
            type Person name: string 
              age: int
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val typeDeclaration = ast[0] as TypeDeclaration
        assert(typeDeclaration.typeName == "Person")
        assert(typeDeclaration.fields.count() == 2)
        val fields = typeDeclaration.fields

        assert(fields[0].name == "name")
//        assert(fields[0].type?.name == "string")
        assert(fields[1].name == "age")
//        assert(fields[1].type?.name == "int")
    }

    @Test
    fun typeDeclarationManyLinesGeneric() {
        val source = """
            type Person name: string 
              age: int
              `generic
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val typeDeclaration = ast[0] as TypeDeclaration
        assert(typeDeclaration.typeName == "Person")
        assert(typeDeclaration.fields.count() == 3)
        assert(typeDeclaration.fields[2].name == "generic")
    }


    @Test
    fun typeDeclarationUnion() {
        val source = """
        union Shape area: int =
            | Rectangle => width: int height: int
            | Circle    => radius: int
        """.trimIndent()
        val ast = getAstTest(source)
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
        | x count == 22 => 1 echo
        | 7 < 6 => [
          y = x count + 10
          y echo
        ]
        |=> else branch sas
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val iF = ast[0] as ControlFlow.If
        assert(iF.kind == ControlFlowKind.Statement)
        val elseBranches = iF.elseBranch
        val ifBranches = iF.ifBranches
        assert(ifBranches.count() == 2)
        assert(ifBranches[0] is IfBranch.IfBranchSingleExpr)
        assert(ifBranches[1] is IfBranch.IfBranchWithBody)
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
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is ControlFlow.Switch)
        val switchStatement = ast[0] as ControlFlow.Switch
        assert(switchStatement.kind == ControlFlowKind.Statement)
        val switch = switchStatement.switch as IdentifierExpr
        assert(switch.str == "y")
    }

    @Test
    fun genericTypeDeclaration() {
        val source = """
        x::List::int = 1
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }


    // x::Map(int, string)
    @Test
    fun genericTypeDeclarationWith2Types() {
        val source = """
        x::List::Map(int, string) = 1
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun genericTypeDeclarationWithMapOfMap() {
        val source = """
        x::Map(int, Map(int, string)) = 1
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun genericTypeDeclarationWithListOfListOfPerson() {
        val source = """
        x::List::List::Person = 1
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun constructor() {
        val source = """
        constructor Person default = Person name: "" age: 0 
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val constr = ast[0] as ConstructorDeclaration
        assert(constr.forType.name == "Person")
        assert(constr.body.size == 1)
    }

    @Test
    fun nullableValue() {

        val source = """
            x::int? = null
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun codeBlockType() {
        val source = """
            x::[int, bool -> string]? = null
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is VarDeclaration)
    }

    @Test
    fun codeBlock() {
        val source = """
            x = [x, y -> x + y]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is VarDeclaration)
    }

    @Test
    fun codeBlockShort() {
        val source = """
            x = [x + y]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is VarDeclaration)
    }

    @Test
    fun codeBlockWithType() {
        val source = """
            x = [x::Int, y::Int -> x + y]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is VarDeclaration)
    }

    @Test
    fun pipeOperator() {
        val source = """
        1 to: 2 |> from: 3 |> kek: 5
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun pipeOperator2() {
        val source = """
        1 + 1 |> inc 
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun pipeOperator3() {
        val source = """
        this - 1 |> factorial * this
        """.trimIndent()


        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }


    @Test
    fun cascadeOperator() {
        val source = """
        1 inc; + 2; dec; + 5; from: "sas"
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val q = ast[0] as MessageSend
        assert(q.messages.count() == 5)
    }

//    @Test
//    fun cascadeOperator2() {
//        val source = """
//        1 inc; inc + 2
//        """.trimIndent()
//        val ast = getAstTest(source)
//        assert(ast.count() == 1)
//        val q = ast[0] as MessageSend
//        assert(q.messages.count() == 2)
//    }

    @Test
    fun asdas() {
        val source = """
        1 from: 1 to: 2 
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is MessageSend)
    }

    @Test
    fun messageSendWithPackageName() {
        val source = """
        1 Sas.from: 1 to: 2 
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is MessageSend)
    }


    @Test
    fun dotOperator() {

        val source = """
        x Sas.sus
        x = "path to file" Package.unaryMessage
        Package.Type create: "sas"
        1 Package.from: 1 to: 2 
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 4)
        val q = ast[0] as MessageSend
        val u = q.messages[0] as UnaryMsg
        assert(u.selectorName == "sus")
        assert(u.path.first() == "Sas")
        val w = ((ast[1] as VarDeclaration).value as MessageSendUnary).messages[0] as UnaryMsg
        assert(w.path.first() == "Package")
        assert(w.selectorName == "unaryMessage")
        val e = ((ast[2] as MessageSendKeyword).receiver as IdentifierExpr).name
        assert(e == "Type")
        val r = ((ast[3] as MessageSendKeyword).messages[0] as KeywordMsg)
        assert(r.selectorName == "fromTo")
        assert(r.path.last() == "from")
    }

    @Test
    fun typeAlias() {
        val source = """
        alias MyInt = Int
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val q = ast[0] as AliasDeclaration
        assert(q.typeName == "MyInt")
        assert(q.matchedTypeName == "Int")
    }

    @Test
    fun bracketExpression() {

        val source = """
            (3 + 5)
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun returnStatementSimple() {

        val source = """
            ^ 5
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun comment() {
        val source = """
            // sas
            x = 5
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun inlineRepl() {
        val source = """
            > 5 + 5
            5 + 5
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
        val q = ast[0] as Expression
        val w = ast[1] as Expression
        assert(q.isInlineRepl)
        assert(!w.isInlineRepl)
    }

    @Test
    fun mutableVariable() {

        val source = """
            mut x = 6
            x <- 7
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
        val q = ast[1] as Assign

        assert(q.name == "x")
        assert(q.value is LiteralExpression.IntExpr)
    }

    @Test
    fun manyBinarys() {

        val source = """
            mut x = 6
            x <- 7
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
        val q = ast[1] as Assign

        assert(q.name == "x")
        assert(q.value is LiteralExpression.IntExpr)
    }

//    @Test
//    fun unaryOnManyLines() {
//
//        val source = """
//            obj call1
//              call2
//              call3
//        """.trimIndent()
//        val ast = getAstTest(source)
//        assert(ast.count() == 1)
//        val q = ast[0] as MessageSendUnary
//        assert(q.messages.count() == 3)
//        assert(q.messages[0].selectorName == "call1")
//        assert(q.messages[1].selectorName == "call2")
//        assert(q.messages[2].selectorName == "call3")
//    }

//    @Test
//    fun unaryOnManyLines2() {
//
//        val source = """
//            obj
//              call1
//              call2
//              call3
//        """.trimIndent()
//        val ast = getAstTest(source)
//        assert(ast.count() == 1)
//        val q = ast[0] as MessageSendUnary
//        assert(q.messages.count() == 3)
//        assert(q.messages[0].selectorName == "call1")
//        assert(q.messages[1].selectorName == "call2")
//        assert(q.messages[2].selectorName == "call3")
//
//    }

}

fun getAstTest(source: String): List<Statement> {
    val fakeFile = File("Niva.iml")
    val tokens = lex(source, fakeFile)
    val parser = Parser(file = fakeFile, tokens = tokens, source = source)
    return parser.statements()
}

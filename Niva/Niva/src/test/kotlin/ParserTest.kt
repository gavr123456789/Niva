@file:Suppress("RedundantLambdaOrAnonymousFunction")

import frontend.parser.parsing.Parser
import frontend.parser.parsing.keyword
import frontend.parser.parsing.statements
import main.frontend.meta.CompilerError
import main.frontend.parser.types.ast.Assign
import main.frontend.parser.types.ast.BinaryMsg
import main.frontend.parser.types.ast.ConstructorDeclaration
import main.frontend.parser.types.ast.ControlFlow
import main.frontend.parser.types.ast.ControlFlowKind
import main.frontend.parser.types.ast.DestructingAssign
import main.frontend.parser.types.ast.EnumDeclarationRoot
import main.frontend.parser.types.ast.ErrorDomainDeclaration
import main.frontend.parser.types.ast.Expression
import main.frontend.parser.types.ast.ExpressionInBrackets
import main.frontend.parser.types.ast.IdentifierExpr
import main.frontend.parser.types.ast.IfBranch
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.ListCollection
import main.frontend.parser.types.ast.LiteralExpression
import main.frontend.parser.types.ast.MapCollection
import main.frontend.parser.types.ast.MessageDeclarationBinary
import main.frontend.parser.types.ast.MessageDeclarationKeyword
import main.frontend.parser.types.ast.MessageDeclarationUnary
import main.frontend.parser.types.ast.MessageSend
import main.frontend.parser.types.ast.MessageSendBinary
import main.frontend.parser.types.ast.MessageSendKeyword
import main.frontend.parser.types.ast.MessageSendUnary
import main.frontend.parser.types.ast.ReturnStatement
import main.frontend.parser.types.ast.SetCollection
import main.frontend.parser.types.ast.Statement
import main.frontend.parser.types.ast.StaticBuilder
import main.frontend.parser.types.ast.StaticBuilderDeclaration
import main.frontend.parser.types.ast.TypeAST
import main.frontend.parser.types.ast.TypeDeclaration
import main.frontend.parser.types.ast.UnaryMsg
import main.frontend.parser.types.ast.UnionRootDeclaration
import main.frontend.parser.types.ast.VarDeclaration
import main.lex
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ParserTest {
    @Test
    fun varDeclaration() {
        val source = "x::Int = 1"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assert(declaration.name == "x")
        assert(declaration.value.str == "1")
    }

    @Test
    fun varDeclWithTypeInfer() {

        val source = "x = 1"
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val declaration: VarDeclaration = ast[0] as VarDeclaration
        assertTrue(declaration.name == "x")
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

    @Test
    fun collectionMap() {
        val source = "#{1 2 3 4}"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val map = ast[0] as MapCollection
        assert(map.initElements.count() == 2)
    }

    @Test
    fun collectionSet() {
        val source = "#(1 2 3 3)"
        val ast = getAstTest(source)
        assert(ast.count() == 1)

        val map = ast[0] as SetCollection
        assert(map.initElements.count() == 4)
    }

    @Test
    fun collectionOfMessages() {
        val source = """
            {(1 inc) (2 inc) (3 inc)}
            #{(1 dec) ("a b" split: " " |> first), 2 "b"}
            #((1..3 |> random) (1..3 |> random))
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 3)

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
        assert(kwMessage.args[0].name == "to")
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
        assert(kwMessage.args[0].name == "to")
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
        assertEquals(first.receiver.str, "name")
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
        val keyword2 = (ast[2] as MessageSend).messages[0]
        assert(unary.receiver.str == "3")
        assert(binary.receiver.str == "1")
        assert(keyword2.receiver.str == "x")
        assert(unary is UnaryMsg)
        assert(binary is BinaryMsg)
        assert(keyword2 is KeywordMsg)

        val keyword = keyword2 as KeywordMsg

        val from = keyword.args.first().keywordArg
        val to = keyword.args.last().keywordArg
        val fullKeywordTok = keyword.token
        assert(from.token.relPos.start == 8 && from.token.relPos.end == 9)
        assert(to.token.relPos.start == 14 && to.token.relPos.end == 15)
        assert(fullKeywordTok.relPos.start == 2 && fullKeywordTok.relPos.end == 12)


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

        assert(keyword.token.lineEnd == 2)
        assert(keyword.token.relPos.end == 4)
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
    fun keywordFirstKeyStartOnNewLine() {
        val source = """
            x 
            from: 1 
            to: 2
            q 
            do: this
        and: that
                  and: that
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
    }

    @Test
    fun keywordFirstKeysArgStartOnNewLine() {
        val source = """
            x from: 
                1 
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val q = ast[0] as MessageSendKeyword
        assertTrue { q.messages[0].selectorName == "from" }
    }

    @Test
    fun keywordFirstkeysStartOnNewLineAndAssign() {
        val source = """
            q = x from: 
                1 
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val q = ast[0] as VarDeclaration
        val w = q.value as MessageSendKeyword
        assertTrue { w.messages[0].selectorName == "from" }
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
        assert(keywordMsg.args[0].keywordArg.str == "3")
        assert(keywordMsg.args[1].keywordArg.str == "5")
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
            Person noTypeLocalName: q typeAndLocalName: w::int noLocalNameButType::int = [
              x = 1
              x sas
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val msgDecl = (ast[0] as MessageDeclarationKeyword)
        assert(msgDecl.name == "noTypeLocalNameTypeAndLocalNameNoLocalNameButType")
        assert(msgDecl.args.count() == 3)
        assert(msgDecl.body.count() == 2)

        // args
        assert(msgDecl.args[0].name == "noTypeLocalName")
        assert(msgDecl.args[0].localName == "q")
        assert(msgDecl.args[1].name == "typeAndLocalName")
        assert(msgDecl.args[1].localName == "w")

        assert(msgDecl.args[2].name == "noLocalNameButType")
        assert(msgDecl.args[2].localName == null)

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
        assert(fields[0].typeAST?.name == "string")
        assert(fields[1].name == "age")
        assert(fields[1].typeAST?.name == "int")
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
        assert(fields[1].name == "age")
    }

//    @Test
//    fun typeDeclarationManyLinesGeneric() {
//        val source = """
//            type Person name: string
//              age: int
//              `generic
//        """.trimIndent()
//        val ast = getAstTest(source)
//        assert(ast.count() == 1)
//        val typeDeclaration = ast[0] as TypeDeclaration
//        assert(typeDeclaration.typeName == "Person")
//        assert(typeDeclaration.fields.count() == 3)
//        assert(typeDeclaration.fields[2].name == "generic")
//    }


    @Test
    fun typeDeclarationUnion() {
        val source = """
        union Shape area: int =
            | Rectangle width: int height: int
            | Circle    radius: int
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val unionDeclaration = ast[0] as UnionRootDeclaration
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
        _
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
        assert(constr.forTypeAst.name == "Person")
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

    @Suppress("UNCHECKED_CAST")
    @Test
    fun pipeOperator() {
        val source = """
        1 to: 2 |> from: 3 |> kek: 5
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        // all msgs except first must be in brackets, for correct codegen
        val q = (ast[0] as MessageSendKeyword).messages as List<KeywordMsg>
        assert(q[1].isPiped)
        assert(q[2].isPiped)
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
    fun pipeOperatorManyLines() {
        val source = """
        1 inc |> 
          inc |> 
          inc |> 
          inc 
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

    @Test
    fun cascadeOperatorManyKeywords() {
        val source = """
        a from: 1; to: 2

        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val q = ast[0] as MessageSendKeyword
        val w = q.messages[0]
        val e = q.messages[1]
        assertTrue { w.receiver == e.receiver }
    }
    @Test
    fun cascadeOperatorOnLambdaCall() {
        val source = """
        codeblock = [label::Int -> y + y]
        btnResult = codeblock label: 1; addCssClass: "suggested-action"

        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
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
    fun simpleKeyword() {
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


//    @Test
//    fun dotOperatorForTypesAndMessages() {
//
//        val source = """
//        x Sas.sus
//        x = "path to file" Package.unaryMessage
//        Package.Type create: "sas"
//        1 Package.from: 1 to: 2
//        """.trimIndent()
//
//        val ast = getAstTest(source)
//        assert(ast.count() == 4)
//        val q = ast[0] as MessageSend
//        val u = q.messages[0] as UnaryMsg
//        assert(u.selectorName == "sus")
//        assert(u.path.first() == "Sas")
//        val w = ((ast[1] as VarDeclaration).value as MessageSendUnary).messages[0] as UnaryMsg
//        assert(w.path.first() == "Package")
//        assert(w.selectorName == "unaryMessage")
//        val e = ((ast[2] as MessageSendKeyword).receiver as IdentifierExpr).name
//        assert(e == "Type")
//        val r = ((ast[3] as MessageSendKeyword).messages[0] as KeywordMsg)
//        assert(r.selectorName == "fromTo")
//        assert(r.path.last() == "from")
//    }


    @Test
    fun dotOperatorForTypes() {

        val source = """
        Package.Type create: "sas"
        x = Package.Package.Type x: 1
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 2)

        val e = ((ast[0] as MessageSendKeyword).receiver as IdentifierExpr).name
        assert(e == "Type")
        val r = (((ast[1] as VarDeclaration).value as MessageSendKeyword).messages[0] as KeywordMsg)
        assert(r.selectorName == "x")
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

    @Test
    fun commentsInsideUnionsAndCF() {
        val source = """
            union Shape area: Int =
            // | Circle     radius: Int
            | Rectangle width: Int height: Int
            x = Rectangle width: 2 height: 3 area: 6
            | x
            // | Circle => x radius echo
            | Rectangle => x width echo
            
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 3)
        assert(ast[0] is UnionRootDeclaration)
        assert(ast[1] is VarDeclaration)
        assert(ast[2] is ControlFlow.Switch)
    }

    @Test
    fun emptyType() {

        val source = """
           type Sas
           type Sus
           type Ses
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 3)
        assert(ast[0] is TypeDeclaration)
        assert(ast[1] is TypeDeclaration)
        assert(ast[2] is TypeDeclaration)
    }

    @Test
    fun emptyUnionType() {

        val source = """
            union Nothing
            union NoUnionBranches width: Int
            union NoFieldsButBranches =
                | Circle radius: Int
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 3)
        assert(ast[0] is UnionRootDeclaration)
        assert(ast[1] is UnionRootDeclaration)
        assert(ast[1] is UnionRootDeclaration)
    }

    @Test
    fun codeAttributes() {

        val source = """
           @ a: 1 b: "sas"
           type Person
           @ sas: 212 sus: "wqw"
           Person from::Int = 1 echo
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
        assert((ast[0] as TypeDeclaration).pragmas.count() == 2)
        assert((ast[1] as MessageDeclarationKeyword).pragmas.count() == 2)
    }

    @Test
    fun codeAttributesInsideBind() {

        val source = """
           Bind package: "kotlin.random" content: [
                type Random
                @ktName: "setReadable"
                constructor Random from::Int until::Int -> Int
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
//        assert((ast[0] as TypeDeclaration).pragmas.count() == 2)
//        assert((ast[1] as MessageDeclarationKeyword).pragmas.count() == 2)
    }

    @Test
    fun emptyBody() {

        val source = """
            Int sas::Int = []
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert((ast[0] as MessageDeclarationKeyword).args.count() == 1)
    }

    @Test
    fun twoBodyArgs() {

        val source = """
            Bind package: [
            1 echo
            ] content: [
            2 echo
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun returnWithoutExpression() {

        val source = """
            ^
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun unionsBranchesWithoutFields() {

        val source = """
            union Result =
                | Some x: Int
                | None
            union Result =
                | Some x: Int
                | None
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
    }

    // ^ means that this branch include other union, to distinguish it from branch with no fields
    @Test
    fun unionsInsideUnionForwardDeclaration() {

        val source = """
            union Sas =
                | ^Tat
                | Sos
            union Tat =
                | Tut
                | Tam
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
        val q = (ast[0] as UnionRootDeclaration).branches[0]
        assertTrue{q.isRoot}
    }

    @Test
    fun twoNamedArgsCodeBlock() {
        val source = """
            map foreach: [ k, v ->
                1 echo
            ]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun typeNoParamsButGeneric() {
        val source = """
            type Saas::T
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun lambdaNoArgs() {
        val source = """
            x::[-> Int] = [5]
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }


    @Test
    fun manyLinesKeywordDecl() {
        val source = """
            Int
                from::Int
                to::Int
            = []
            Ште from::Int to::String = []
            
            Int + x::Int = []
            Int unary -> Int = []
            
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 4)
    }

    @Test
    fun dotAsThisInsideMethods() {
        val source = """
            Int from::Int = from
            
            Int
                from::Int
                to::Int
            = [
                . from: 1
            ]
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 2)
    }

    @Test
    fun constructorForGeneric() {
        val source = """
            List::Int create
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun newIfSingleline() {
        val source = """
            _| 1 > 2 => 3 |=> 4
            _| 5 > 6 => 7
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 2)
    }

    @Test
    fun newIfMultiline() {
        val source = """
            _
            | 1 > 2 => 4
            |=> 7
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun newSingleIfSyntax() {
        val source = """
            42 > 0 => "Single Expression if!" echo
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun newSingleIfSyntaxAfterIfElseIfChain() {
        val source = """
            _| 5 > 6 => 7
            42 > 0 => "Single Expression if!" echo
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 2)
    }

    @Test
    fun enumDecl() {
        val source = """
            enum Color r: Int g: Int b: Int =
            | RED   r: 255 g: 0 b: 0
            | GREEN r: 0 g: 255 b: 0
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is EnumDeclarationRoot)
        assert((ast[0] as EnumDeclarationRoot).branches.count() == 2)
    }

    @Test
    fun double() {
        val source = """
            3.14d
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is LiteralExpression.DoubleExpr)
    }

    @Test
    fun singleIfExpr() {
        val source = """
            o = 4 < 0 => "yes" |=> "no"
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is VarDeclaration)
    }

    @Test
    fun returnSingleIfExpr() {
        val source = """
            ^ this > 0 => 1 |=> -1
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is ReturnStatement)
    }

    @Test
    fun pipeUnaryAfterBinary() {
        val source = """
            width * height |> toFloat
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val send = ast[0] as MessageSendBinary
        assert(send.messages.count() == 2)
    }

    @Test
    fun pipeUnaryAfterKw() {
        val source = """
            LoggedIn name: "Oleг" |> welcome
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val send = ast[0] as MessageSendKeyword
        assert(send.messages.count() == 2)
    }

    @Test
    fun pipeUnaryAfterTypeConstructor() {
        val source = """
            Guest |> welcome
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val send = ast[0] as MessageSendUnary
        assert(send.messages.count() == 1 && send.messages[0].selectorName == "welcome")
    }

    @Test
    fun pipeInsideKwArgInsideParenthesise() {
        val source = """
            Person name: ("Alice" |> getName)
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val send = ast[0] as MessageSendKeyword
        assert(send.messages.count() == 1 && send.messages[0].selectorName == "name")
        assert((send.messages[0] as KeywordMsg).args[0].keywordArg is ExpressionInBrackets)
    }


    @Test
    fun allMsgDeclaration() {
        val source1 = """
            Person unary -> Int = 1
        """.trimIndent()

        val ast = getAstTest(source1)
        assert(ast.count() == 1)

        val source2 = """
            Person + binary::Int = 1 echo
        """.trimIndent()

        val ast2 = getAstTest(source2)
        assert(ast2.count() == 1)

        val source3 = """
            Person key::Int word::String = 1 echo
        """.trimIndent()

        val ast3 = getAstTest(source3)
        assert(ast3.count() == 1)

        val source4 = """
            Person withLocalName: x::Int = 1 echo
        """.trimIndent()

        val ast4 = getAstTest(source4)
        assert(ast4.count() == 1)

    }

    @Test
    fun extendTypeWithManyMsgs() {
        val source = """
            extend Person [
              on unary -> Int = 1 echo
              on + binary::Int = 1 echo
              on key::Int word::String = 1 echo
              on withLocalName: x::Int = 1 echo
            ]
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }


    @Test
    fun nullVarDecl() {
        val source = """
            mut x::Int? = null
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert((ast[0] as VarDeclaration).valueTypeAst?.name == "Int")
        val value = (ast[0] as VarDeclaration).valueTypeAst as TypeAST.UserType
        assert(value.name == "Int")
        assert(value.isNullable == true)
    }


    // The next syntax for if elif else
//    @Test
//    fun switchOnNothing() {
//        val source = """
//            |
//            | y > 6 => 1 echo
//            | x == 6 => 2 echo
//        """.trimIndent()
//
//        // is the same as when() {}, so it is if else if
//
//        val ast = getAstTest(source)
//        assert(ast.count() == 1)
//
//    }

    @Test
    fun manyLineBinary() {
        val source = """
            x = 1 +
            2
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun switchWithVariants() {
        val source = """
            x = 1
            | x
            | 1, 2, 3 => "sas" echo
            |=> "sus" echo
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 2)
    }

    @Test
    fun staticBuilderCall() {
        val source = """
            sas [ 
                it echo 
                defaultAction = [
                  it echo
                ]
            ] 
        """.trimIndent()

        // is the same as when() {}, so it is if else if

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val staticB = ast[0] as StaticBuilder
        assert(staticB.statements[0] is MessageSendUnary)
    }

    @Test
    fun inlineCanBeOnlyExpression() {
        // when inline was parser as statement here, line 2-3 was parsed as KeywordMessageDeclaration
        val source = """
            y = 1 from: 5
            >y
            x::Int = 5
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 3)

    }

    @Test
    fun codeBlockFalseArgs() {
        val source = """
           
          fillGroups = [
            words1::MutableMap(Int, String) = #{}
            words2::MutableMap(Int, String) = #{}
            
            1 echo
          ]
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)

    }

    @Test
    fun manyCases() {
        val source = """
           
          | 1
          | 1,2,3 => 4
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)

    }

    @Test
    fun pipedStaticSend() {
        val source = """
          FileSystem read: "strings.txt" toPath |>
            split: "\n"
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val kw = ast[0] as MessageSendKeyword
        val secondMsg = kw.messages[1] as KeywordMsg
        val receiver = secondMsg.receiver
        assert(receiver is KeywordMsg)
    }

    @Test
    fun constructorKeywordWithLocalName() {
        val source = """
            constructor Person from: q::Int = q echo
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)

    }

    @Test
    fun doCallInVarDecl() {
        val source = """
            x = [1]
            y::Int = x do
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 2)
        val y = ast[1] as VarDeclaration
        assertTrue { y.value is MessageSendUnary }
    }


    @Test
    fun singleWordPragma() {
        val source = """
            @Sortable Compose
            Int sas = 1 echo
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val msgUnaryDecl = ast[0] as MessageDeclarationUnary
        assertTrue{msgUnaryDecl.pragmas.count() == 2}
    }

    @Test
    fun extensionLambda() {
        val source = """
            Something msg::Person[x::Int, y::Int -> Unit] = [
                p = Person new
                // first variant
                msg this: p x: 1 y: 2
            ]
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert(ast[0] is MessageDeclarationKeyword)

    }

    @Test
    fun assignWithNewLine() {
        val source = """
            x = 
            1
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Suppress("UNUSED_VARIABLE", "unused")
    @Test
    fun msgsForPipedMustHaveMsgAsReceiver() {
        // the bug is that inc has x as receiver, instead of x |> unpack, so null send error
        val source = """
            x |> unpackOrError inc
            list at: 5 |> unpackOrError inc
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 2)
        val unary = {
            val q = ast[0] as MessageSendUnary
            val first = q.messages.first() as UnaryMsg
            val last = q.messages.last() as UnaryMsg
            assertTrue { last.receiver == first }
        }()
        val kw = {
            val q = ast[1] as MessageSendKeyword
            val second = q.messages[1] as UnaryMsg
            val last = q.messages.last() as UnaryMsg
            assertTrue { last.receiver == second }
        }()
    }

    @Test
    fun returnTypeASTIsNullable() {
        // the bug is that inc has x as receiver, instead of x |> unpack, so null send error
        val source = """
            Int sas -> Int? = 1 > 2 => null |=> 5
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assert((ast[0] as MessageDeclarationUnary).returnTypeAST?.isNullable == true)
    }

    @Test
    fun typedCall() {
        // the bug is that inc has x as receiver, instead of x |> unpack, so null send error
        val source = """
            list::Int sas
            list::Int + 1
            list::Int sas: "text"
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 3)
    }


    @Test
    fun methodReference2() {
        val source = """
            x = &String x
            x = &String +
            x = &String x:y:
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 3)
    }


    @Test
    fun oneLineUnion() {
        val source = """
            union A = B | C
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
//        val msgUnaryDecl = ast[0] as MessageDeclarationKeyword
    }

    @Test
    fun qualifier() {
        val source = """
            window = (org.gnome.adw.ApplicationWindow app: app)
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun typeAlias() {
        val source = """
            type Sas = [Int -> Int]
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun typeAliasSimple() {
        val source = """
            type MyInt = Int
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun kwForLambda() {
        val source = """
            [x::Int -> x inc] hi: 3
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun mutReference() {
        val source = """
        x::mut Person = 1
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val w = ast[0] as VarDeclaration
        val e = w.valueTypeAst!!

        assertTrue { e.mutable }
    }

    @Test
    fun errorAST_Type() {
        val source = """
        x::Int!Error = 1
        x::Int!{Error1 Error2 } = 1
        x::Int! = 1
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 3)
        val q = ((ast[0] as VarDeclaration).valueTypeAst!!) as TypeAST.UserType
        val w = ((ast[1] as VarDeclaration).valueTypeAst!!) as TypeAST.UserType
        val e = ((ast[2] as VarDeclaration).valueTypeAst!!) as TypeAST.UserType

        assertTrue { q.errors!!.count() == 1 }
        assertTrue { w.errors!!.count() == 2 }
        assertTrue { e.errors!!.isEmpty() }


    }

    @Test
    fun errordomain() {
        val source = """
            errordomain MyError =
            | Error1 x: Int
            | Error2 x: Int
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val q = ast[0]
        assertTrue {q is ErrorDomainDeclaration}
    }


    @Test
    fun bang() {
        val source = """
            {1 2} forEach: [
                !!
            ]
            
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun newMessageDeclSyntax() {
        val source = """
            type Fer
              q: Int? // field
              w: [ -> ] // lambda
              on unary = 1 inc
              on + n::Int = 45
              on from: x::Int = []
        """.trimIndent()
        val ast = getAstTest(source)
        println(ast.count())

        assertEquals(ast.count(), 4)
//        val q = ast[0]
//        assertTrue {q is TypeDeclaration}
    }

    @Test
    fun tokenPositions() {
        val source = "x abc"
        val ast = getAstTest(source)

        assertEquals(ast.count(), 1)
        val q = ast.first()

        assertTrue {
            q.token.pos.start == 0 &&
            q.token.pos.end == 4
        }
    }


    @Test
    fun builderCall() {
        val source = """
            x = buildString [ "sas" ]
        """.trimIndent()

        val ast = getAstTest(source)
        assert(ast.count() == 1)
    }

    @Test
    fun staticBuildDeclaration() {
        val source = """
            builder HTML init::[ -> Unit] -> HTML = [
                html = HTML new
                html init
            ]
        """.trimIndent()

        // is the same as when() {}, so it is if else if

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val staticB = ast[0] as StaticBuilderDeclaration
        assert(staticB.msgDeclaration.args[0].typeAST is TypeAST.Lambda)
    }

    @Test
    fun staticBuildDeclarationWithArgs() {
        val source = """
            builder Card width::Int height::Int -> Card = [
                html = HTML new
            ]
        """.trimIndent()

        // is the same as when() {}, so it is if else if

        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val staticB = ast[0] as StaticBuilderDeclaration
        assertTrue { staticB.msgDeclaration.args.first().typeAST!!.name == "Int" &&
                staticB.msgDeclaration.args.last().typeAST!!.name == "Int"}
    }

    @Test
    fun staticBuildDeclarationWithArgsWithReceiver() {
        // Surface is the receiver type
        // Card is the builder's name
        val source = """
            Surface builder Card width::Int height::Int -> Card = [
                html = HTML new
            ]
        """.trimIndent()


        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val staticB = ast[0] as StaticBuilderDeclaration
        assertTrue {
            staticB.msgDeclaration is MessageDeclarationKeyword
        }
        assertTrue {
            staticB.msgDeclaration.args.first().typeAST!!.name == "Int" &&
                    staticB.msgDeclaration.args.last().typeAST!!.name == "Int"
        }
        assertTrue {
            staticB.receiverAst!!.name == "Surface"
        }
    }

    @Test
    fun staticBuildDeclarationWithArgsWithReceiver2() {
        // Surface is the receiver type
        // Card is the builder's name
        val source = """
            StringBuilder builder buildSomething -> String = [
                1 echo
            ]
            
        """.trimIndent()


        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val staticB = ast[0] as StaticBuilderDeclaration
        assertIs<MessageDeclarationKeyword>(staticB.msgDeclaration)
    }



    @Test
    fun builderCallsWithKeys() {
        val source = """
            Card (width: 24 height: 30) [
                1 echo
            ]
            Card [
                1 echo
            ]
        """.trimIndent()
        val ast = getAstTest(source)

        assertEquals(ast.count(), 2)
        val withArgs = ast.first() as StaticBuilder
        val withoutArgs = ast.last() as StaticBuilder
        assertTrue { withArgs.args.count() == 2 && withoutArgs.args.isEmpty() }
    }

    @Test
    fun builderWithUnary() {
        val source = """
            Card [
                1 echo
            ] sas
        """.trimIndent()
        val ast = getAstTest(source)

        assertEquals(ast.count(), 1)
    }

    @Test
    fun builderWithUnaryWithReceiver() {
        val source = """
            card Card [
                1 echo
            ]
        """.trimIndent()
        val ast = getAstTest(source)

        assertEquals(ast.count(), 1)
    }

    @Test
    fun builderWithArgsWithReceiverCall() {
        val source = """
            "rar" Card (width: 24 height: 30) [
                1 echo
            ]
        """.trimIndent()
        val ast = getAstTest(source)

        assertEquals(ast.count(), 1)
    }

    @Test
    fun dotTokenLength() {
        val source = "org.gnome.Button"
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        val tok = (ast[0] as IdentifierExpr).token

        assertTrue {tok.relPos.start == 0 && tok.relPos.end == 16}
    }

    @Test
    fun argsTokLength() {
        val source = """
            Sasss keyword::String -> Int = 34
            constructor Sasss from::Int -> Int = 23
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
        val tok = (ast[0] as MessageDeclarationKeyword).args[0].typeAST!!.token

        assertTrue { tok.relPos.start == 15 && tok.relPos.end == 21 }
    }

    @Test
    fun destruct() {
        val source = """
            {name age} = person
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assertTrue {ast[0] is DestructingAssign}
    }

    @Test
    fun destructMsg() {
        val source = """
            {name age} = Person name: "sas" age: 23
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 1)
        assertTrue { ast[0] is DestructingAssign }
    }

    @Test
    fun correctParsingErrorLine() {

    val source = """
            union CombinatorResult = 
            |
             
        """.trimIndent()
        try {
            getAstTest(source)

        } catch (e: CompilerError) {
            assertTrue { e.token.line == 2 }
        }
    }

    @Test
    fun inlineAndSuspendFunc() {
        val source = """
            Int sas ^= 1
            Int sas::Int >>= 1
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)

    }


    @Test
    fun manyLineCommentsNotRuiningTokLineNumbers() {
        val source = """
            // qwf
            // ars
            // ars
            Int sas ^= 1
            Int sas::Int >>= 1
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
        val firstTok = ast[0].token
        val secondTok = ast[1].token
        assert(firstTok.line == 4)
        assert(secondTok.line == 5)

    }

    @Test
    fun manyLineDocCommentsNotRuiningTokLineNumbers() {
        val source = """
            /// qwf
            /// ars
            Int sas ^= 1
            Int sas::Int >>= 1
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)
        val firstTok = ast[0].token
        val secondTok = ast[1].token
        assert(firstTok.line == 3)
        assert(secondTok.line == 4)

    }

    @Test
    fun strangeCommentsBug() {
        val source = """
            // qwf

            x = 1
            x echo
        """.trimIndent()
        val ast = getAstTest(source)
        assert(ast.count() == 2)


    }


//    @Test
//    fun onWithNoReturnType() {
//        val source = """
//          extend AdwRow [
//            on addPrefix::Widget
//            on addSuffix::Widget
//          ]
//        """.trimIndent()
//        val ast = getAstTest(source)
//        assert(ast.count() == 1)
//    }

}

fun getAstTest(source: String): List<Statement> {
    val fakeFile = File("Niva.iml")
    val tokens = lex(source, fakeFile)
    val parser = Parser(file = fakeFile, tokens = tokens, source = source)
    return parser.statements()
}

import frontend.parser.types.ast.*
import frontend.typer.Resolver
import frontend.typer.Type
import frontend.typer.resolve
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.File

fun resolve(source: String): List<Statement> {
    val ast = getAstTest(source)
    val resolver = createDefaultResolver(ast)

    return resolver.resolve(resolver.statements, mutableMapOf())
}

private fun createDefaultResolver(statements: List<Statement>) = Resolver(
    projectName = "common",
    mainFile = File("sas.niva"),
    statements = statements.toMutableList()
)


class ResolverTest {
    @Test
    fun getterCall() {
        val source = """
            type Person name: String
            person = Person name: "sas"
            person name
        """.trimIndent()
        val ktCode = resolve(source)
        assert(ktCode.count() == 3)
        val q = ktCode[2] as MessageSendUnary
        assert(q.type?.name == "String")

        val getter = q.messages[0]
        assert(getter.type?.name == "String")
        assert((getter as UnaryMsg).kind == UnaryMsgKind.Getter)

    }

    @Test
    fun keywordCall() {
        val source = """
            Int add2::Int = [
                ^ this + add2
            ]
        """.trimIndent()
        val ast = resolve(source)
        assert(ast.count() == 1)

    }

    @Test
    fun constructorCall() {
        val source = """
            x = "sas"
            type Person name: String age: Int
            person = Person name: x age: 5
        """.trimIndent()

        val ast = resolve(source)
        assert(ast.count() == 3)

        val q = ast[2] as VarDeclaration

        val value = q.value as MessageSendKeyword
        assert(value.type?.name == "Person")
        assert(value.receiver.type?.name == "Person")

        val message = value.messages[0]
        assert((message as KeywordMsg).kind == KeywordLikeType.Constructor)
        assert(message.type?.name == "Person")
        val firstArg = message.args[0]
        val secondArg = message.args[1]

        assert(firstArg.keywordArg.type!!.name == "String")
        assert(secondArg.keywordArg.type!!.name == "Int")
    }

    @Test
    fun setterCall() {
        val source = """
            type Person name: String age: Int
            person = Person name: "sas" age: 5
            Person sas -> Unit = [
              this name: "sus"
            ]
        """.trimIndent()

        val ktCode = resolve(source)
        assert(ktCode.count() == 3)

        val q = ktCode[2] as MessageDeclarationUnary
        val firstOfBody = q.body.first() as MessageSendKeyword
        val msg = firstOfBody.messages[0]
        val receiver = msg.receiver
        assert(receiver.type?.name == "Person")
        assert(receiver.str == "this")
        assert((msg as KeywordMsg).kind == KeywordLikeType.Setter)
    }


    @Test
    fun selfCall() {
        val source = """
            type Person name: String age: Int
            person = Person name: "sas" age: 5
            Person sas -> Unit = [
              this name
            ]
        """.trimIndent()

        val ktCode = resolve(source)
        assert(ktCode.count() == 3)

        val q = ktCode[2] as MessageDeclarationUnary
        val firstOfBody = q.body.first() as MessageSendUnary
        val msg = firstOfBody.messages[0]
        val receiver = msg.receiver
        assert(receiver.type?.name == "Person")
        assert(receiver.str == "this")
        assert((msg as UnaryMsg).kind == UnaryMsgKind.Getter)
    }

    @Test
    fun projectSetting() {
        val source = """
            Project package: "files" protocol: "path"
            type Person name: String age: Int
        """.trimIndent()

        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        resolver.resolve(resolver.statements, mutableMapOf())
        val proj = resolver.projects["common"]!!
        val pack = proj.packages["files"]!!
        val protocol = (pack.types["Person"]!!) as Type.UserType //!!.protocols["path"]!!

        assert(resolver.currentProtocolName == "path")
        assert(resolver.currentPackageName == "files")
        assert(pack.packageName == "files")
        assert(protocol.fields[0].name == "name")
        assert(protocol.fields[1].name == "age")
    }

    @Test
    fun defaultTypesInCorePackage() {
        val source = """
            Project package: "files" protocol: "path"
            type Person name: String age: Int
        """.trimIndent()
        val ast = getAstTest(source)

        val resolver = createDefaultResolver(ast)

        val q = resolver.projects["common"]!!
        val w = q.packages["core"]!!
        val e = w.types
        assert(e["Int"] != null)
        assert(e["String"] != null)
        assert(e["Float"] != null)
        assert(e["Boolean"] != null)
        assert(e["Unit"] != null)
        assert(e["Project"] != null)
        assert(e["Bind"] != null)

    }

    @Test
    fun registerUnary() {

        val source = """
            Project package: "files" protocol: "path"
            type Person name: String age: Int
            Person filePath -> Unit = [1 echo]
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())

        assert(statements.count() == 3)
        assert(resolver.currentPackageName == "files")
        assert(resolver.currentProjectName == "common")
        assert(resolver.currentProtocolName == "path")

        val proj = resolver.projects["common"]!!
        val pack = proj.packages["files"]!!
        val protocol = pack.types["Person"]!!.protocols["path"]!!
        val unary = protocol.unaryMsgs["filePath"]!!
        assert(unary.name == "filePath")
    }

    @Test
    fun registerTopLevelStatements() {

        val source = """
            1 echo
            9 echo
            Project package: "files" protocol: "path"
            x = 8
            type Person name: String age: Int
            Person filePath -> Unit = [1 echo]
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        resolver.resolve(resolver.statements, mutableMapOf())

        assert(resolver.currentPackageName == "files")
        assert(resolver.currentProjectName == "common")
        assert(resolver.currentProtocolName == "path")

        val proj = resolver.projects["common"]!!
        val pack = proj.packages["files"]!!
        val protocol = pack.types["Person"]!!.protocols["path"]!!
        val unary = protocol.unaryMsgs["filePath"]!!
        assert(unary.name == "filePath")

        assert(resolver.topLevelStatements.isNotEmpty())
        assert(resolver.topLevelStatements.count() == 3)
    }
//
//    @Test
//    fun recreateKtFolder() {
//        val path = "C:\\Users\\gavr\\Documents\\Projects\\Fun\\NivaExperiments\\exampleProj\\src\\main\\kotlin"
//
//        val source = """
//            1 echo
//            9 echo
//            Project package: "files" protocol: "path"
//            x = 8
//            type Person name: String age: Int
//            Person filePath -> Unit = [1 echo]
//        """.trimIndent()
//
//
//        val ast = getAstTest(source).toMutableList()
//
//        val resolver = createDefaultResolver(ast)
//        resolver.resolve(resolver.statements, mutableMapOf())
//
//        resolver.generateKtProject(path)
//    }

//    @Test
//    fun manySources() {
//        val path = "C:\\Users\\gavr\\Documents\\Projects\\Fun\\NivaExperiments\\exampleProj\\src\\main\\kotlin"
//
//        val source1 = """
//            1 echo
//            9 echo
//            Project package: "files" protocol: "path"
//            x = 8
//            type Person name: String age: Int
//            Person filePath -> Unit = [1 echo]
//        """.trimIndent()
//
//        val source2 = """
//            1 echo
//            9 echo
//            Project package: "collections"
//            x = 8
//            type MegaMassive count: Int
//            MegaMassive add -> Unit = [1 echo]
//        """.trimIndent()
//
//        val source3 = """
//            1 echo
//            9 echo
//            Project package: "collectionsUnlim"
//            x = 8
//            type MegaMassive2 count: Int
//            MegaMassive2 add -> Unit = [1 echo]
//        """.trimIndent()
//
//        val ast1 = getAstTest(source1).toMutableList()
//        val ast2 = getAstTest(source2).toMutableList()
//        val ast3 = getAstTest(source3).toMutableList()
//
//        val ast = ast3 + ast2
//
//        val resolver = createDefaultResolver(ast)
//
//        resolver.resolve(resolver.statements, mutableMapOf())
//
//        resolver.generateKtProject(path)
//    }


    @Test
    fun codeBlockEval() {

        val source = """
            x = [x::Int, y::Int -> x + y]
            x x: 1 y: 2
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)
        val lambdaCall = ((statements[1]) as MessageSendKeyword).messages[0] as KeywordMsg
        lambdaCall.args.forEach {
            assert(it.keywordArg.type != null)
        }
    }

    @Test
    fun codeBlockEvalTopLevel() {

        val source = """
            [x::Int, y::Int -> x + y]
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        resolver.resolve(resolver.statements, mutableMapOf())
    }

    @Test
    fun doCycle() {

        val source = """
            1 to: 3 do: [ 1 echo ]
        """.trimIndent()

        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 1)
        val lambdaCall = ((statements[0]) as MessageSendKeyword).messages[0] as KeywordMsg
        lambdaCall.args.forEach {
            assert(it.keywordArg.type != null)
        }
    }

    @Test
    fun exeLambda() {

        val source = """
            x = [1 echo]
            x exe 
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)
//        val lambdaCall = ((statements[1]) as MessageSendKeyword).messages[0] as KeywordMsg
//        lambdaCall.args.forEach {
//            assert(it.keywordArg.type != null)
//        }
    }

    @Test
    fun simpleUnary() {
        val source = """
            1 echo
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 1)
    }

    @Test
    fun lambdaArgument() {

        val source = """
            Int to: x::Int doo::[Int -> Int] = [
              1 echo
            ]
            1 to: 2 doo: [5 + 5]
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)
//        val lambdaCall = ((statements[1]) as MessageSendKeyword).messages[0] as KeywordMsg
//        lambdaCall.args.forEach {
//            assert(it.keywordArg.type != null)
//        }
    }

    @Test
    fun intListCollection() {

        val source = """
            {1 2 3}
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 1)
        val listCollection = statements[0] as ListCollection
        assert(listCollection.type != null)
        assertEquals("List", listCollection.type?.name)
        val type = listCollection.type as Type.UserType
        assertEquals("Int", type.typeArgumentList[0].name)
    }


    @Test
    fun ifBranchesReturnSameType() {

        val source = """
            x = | 7
            | 6 => 6
            | 9 => 5
            |=> 7
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 1)
    }


    @Test
    fun intListMap() {

        val source = """
            littleList = {1 2 3 4 5}
            
            newList = littleList map: [
              it + 1
            ]
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)
    }

    @Test
    fun constructor() {

        val source = """
            type Wallet amount: Int
            constructor Wallet empty = Wallet amount: 0 
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)
        val construct = ast[1] as ConstructorDeclaration
        assert(construct.body.count() == 1)
    }

    @Test
    fun keyWordDeclaration() {

        val source = """
            Int from::Int = [
              from echo
            ]
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 1)
    }

    @Test
    fun receiverIsBinaryThenReturnsDifferentType() {

        val source = """
             6 > 5 && (6 < 10)
        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 1)
    }

    @Test
    fun unionTypes() {

        val source = """
            union Shape area: Int =
            | Rectangle => width: Int height: Int
            | Circle    => radius: Int
            circle = Circle radius: 25

        """.trimIndent()


        val ast = getAstTest(source)
        val resolver = createDefaultResolver(ast)
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)
    }

}




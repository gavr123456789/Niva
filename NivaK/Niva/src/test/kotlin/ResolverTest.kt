import frontend.parser.types.ast.*
import frontend.typer.Resolver
import frontend.typer.Type
import frontend.typer.generateKtProject
import frontend.typer.resolve
import org.junit.jupiter.api.Test
import java.io.File

fun resolve(source: String): List<Statement> {
    val ast = getAst(source)
    val resolver = Resolver(
        projectName = "common",
        mainFilePath = File("sas.niva"),
        statements = ast.toMutableList(),
    )

    return resolver.resolve(resolver.statements, mutableMapOf())
}


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
              self name: "sus"
            ]
        """.trimIndent()

        val ktCode = resolve(source)
        assert(ktCode.count() == 3)

        val q = ktCode[2] as MessageDeclarationUnary
        val firstOfBody = q.body.first() as MessageSendKeyword
        val msg = firstOfBody.messages[0]
        val receiver = msg.receiver
        assert(receiver.type?.name == "Person")
        assert(receiver.str == "self")
        assert((msg as KeywordMsg).kind == KeywordLikeType.Setter)
    }


    @Test
    fun selfCall() {
        val source = """
            type Person name: String age: Int
            person = Person name: "sas" age: 5
            Person sas -> Unit = [
              self name
            ]
        """.trimIndent()

        val ktCode = resolve(source)
        assert(ktCode.count() == 3)

        val q = ktCode[2] as MessageDeclarationUnary
        val firstOfBody = q.body.first() as MessageSendUnary
        val msg = firstOfBody.messages[0]
        val receiver = msg.receiver
        assert(receiver.type?.name == "Person")
        assert(receiver.str == "self")
        assert((msg as UnaryMsg).kind == UnaryMsgKind.Getter)
    }

    @Test
    fun projectSetting() {
        val source = """
            Project package: "files" protocol: "path"
            type Person name: String age: Int
        """.trimIndent()

        val ast = getAst(source)
        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast.toMutableList()
        )
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
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
        val ast = getAst(source)

        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast.toMutableList()
        )

        val q = resolver.projects["common"]!!
        val w = q.packages["core"]!!
        val e = w.types
        assert(e["Int"] != null)
        assert(e["String"] != null)
        assert(e["Float"] != null)
        assert(e["Boolean"] != null)
        assert(e["Unit"] != null)
        assert(e["Project"] != null)

    }

    @Test
    fun registerUnary() {

        val source = """
            Project package: "files" protocol: "path"
            type Person name: String age: Int
            Person filePath -> Unit = [1 echo]
        """.trimIndent()


        val ast = getAst(source)
        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast.toMutableList()
        )
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


        val ast = getAst(source)
        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast.toMutableList()
        )
        val statements = resolver.resolve(resolver.statements, mutableMapOf())

//        assert(statements.count() == 3)
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

    @Test
    fun recreateKtFolder() {
        val path = "C:\\Users\\gavr\\Documents\\Projects\\Fun\\NivaExperiments\\exampleProj\\src\\main\\kotlin"

        val source = """
            1 echo
            9 echo
            Project package: "files" protocol: "path"
            x = 8
            type Person name: String age: Int
            Person filePath -> Unit = [1 echo]
        """.trimIndent()


        val ast = getAst(source).toMutableList()

        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast
        )
        resolver.resolve(resolver.statements, mutableMapOf())

        resolver.generateKtProject(path)
    }

    @Test
    fun manySources() {
        val path = "C:\\Users\\gavr\\Documents\\Projects\\Fun\\NivaExperiments\\exampleProj\\src\\main\\kotlin"

        val source1 = """
            1 echo
            9 echo
            Project package: "files" protocol: "path"
            x = 8
            type Person name: String age: Int
            Person filePath -> Unit = [1 echo]
        """.trimIndent()

        val source2 = """
            1 echo
            9 echo
            Project package: "collections" 
            x = 8
            type MegaMassive count: Int
            MegaMassive add -> Unit = [1 echo]
        """.trimIndent()

        val source3 = """
            1 echo
            9 echo
            Project package: "collectionsUnlim" 
            x = 8
            type MegaMassive2 count: Int
            MegaMassive2 add -> Unit = [1 echo]
        """.trimIndent()

        val ast1 = getAst(source1).toMutableList()
        val ast2 = getAst(source2).toMutableList()
        val ast3 = getAst(source3).toMutableList()

        val ast = ast3 + ast2

        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast1.toMutableList(),
        )

        resolver.resolve(resolver.statements, mutableMapOf())

        resolver.generateKtProject(path)
    }


    @Test
    fun codeBlockEval() {

        val source = """
            x = [x::Int, y::Int -> x + y]
            x x: 1 y: 2
        """.trimIndent()


        val ast = getAst(source)
        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast.toMutableList()
        )
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


        val ast = getAst(source)
        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast.toMutableList()
        )
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
//        assert(statements.count() == 2)
//        val lambdaCall = ((statements[1]) as MessageSendKeyword).messages[0] as KeywordMsg
//        lambdaCall.args.forEach {
//            assert(it.keywordArg.type != null)
//        }
    }

    @Test
    fun doCycle() {

        val source = """
            1 to: 3 do: [ 1 echo ]
        """.trimIndent()

        val q: (Int) -> Int = { it }


        val ast = getAst(source)
        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast.toMutableList()
        )
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


        val ast = getAst(source)
        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast.toMutableList()
        )
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)
//        val lambdaCall = ((statements[1]) as MessageSendKeyword).messages[0] as KeywordMsg
//        lambdaCall.args.forEach {
//            assert(it.keywordArg.type != null)
//        }
    }

    @Test
    fun lambdaArgument() {

        val source = """
            Int to: x::Int doo::[Int -> Int] = [
              1 echo
            ]
            1 to: 2 doo: [5 + 5]
        """.trimIndent()


        val ast = getAst(source)
        val resolver = Resolver(
            projectName = "common",
            mainFilePath = File("sas.niva"),
            statements = ast.toMutableList()
        )
        val statements = resolver.resolve(resolver.statements, mutableMapOf())
        assert(statements.count() == 2)
//        val lambdaCall = ((statements[1]) as MessageSendKeyword).messages[0] as KeywordMsg
//        lambdaCall.args.forEach {
//            assert(it.keywordArg.type != null)
//        }
    }


}



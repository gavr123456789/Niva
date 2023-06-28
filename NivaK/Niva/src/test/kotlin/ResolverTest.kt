import frontend.parser.types.ast.*
import frontend.typer.Resolver
import frontend.typer.Type
import frontend.typer.resolve
import org.junit.jupiter.api.Test

fun resolve(source: String): List<Statement> {
    val ast = getAst(source)
    val resolver = Resolver(
        projectName = "common",
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
            type Person name: String age: Int
            person = Person name: "sas" age: 5
        """.trimIndent()

        val ktCode = resolve(source)
        assert(ktCode.count() == 2)

        val q = ktCode[1] as VarDeclaration

        val value = q.value as MessageSendKeyword
        assert(value.type?.name == "Person")
        assert(value.receiver.type?.name == "Person")

        val message = value.messages[0]
        assert((message as KeywordMsg).kind == KeywordLikeType.Constructor)
        assert(message.type?.name == "Person")
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
        val resolver = Resolver(
            projectName = "common",
            statements = mutableListOf()
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
}

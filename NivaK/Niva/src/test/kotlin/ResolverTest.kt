import codogen.codogenKt
import frontend.parser.types.ast.*
import frontend.typer.Resolver
import frontend.typer.resolve
import org.junit.jupiter.api.Test

fun resolve(source: String): List<Statement> {
    val ast = getAst(source)
    val resolver = Resolver(
        projectName = "sas",
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
        assert(getter.kind == UnaryMsgKind.Getter)
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
        assert(message.kind == KeywordLikeType.Constructor)
        assert(message.type?.name == "Person")
    }

    @Test
    fun setterCall() {
        val source = """
            type Person name: String age: Int
            person = Person name: "sas" age: 5
            Person sas = [
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
        assert(msg.kind == KeywordLikeType.Setter)
    }

    @Test
    fun selfCall() {
        val source = """
            type Person name: String age: Int
            person = Person name: "sas" age: 5
            Person sas = [
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
        assert(msg.kind == UnaryMsgKind.Getter)
    }


}

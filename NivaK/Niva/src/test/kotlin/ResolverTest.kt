import codogen.codogenKt
import frontend.parser.types.ast.Statement
import frontend.typer.Resolver
import frontend.typer.resolve
import org.junit.jupiter.api.Test

fun resolve(source: String): List<Statement> {
    val ast = getAst(source)
    val resolver = Resolver(
        projectName = "sas",
        statements = ast.toMutableList(),
    )

    return resolver.resolve(resolver.statements, 0, 0)
}



class ResolverTest {
    @Test
    fun unaryCall() {
//        val source = """
//            Project name: "sas" package: "sus"
//            type Person name: String age: Int
//            Person sas = 1 echo
//
//        """.trimIndent()

        val source = """
            type Person name: String
            person = Person name: "sas"
            1 echo
        """.trimIndent()
        val ktCode = resolve(source)
    }

}

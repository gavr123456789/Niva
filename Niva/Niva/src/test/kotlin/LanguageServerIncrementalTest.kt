import main.frontend.meta.CompilerError
import main.languageServer.LS
import main.languageServer.resolveAllFirstTime
import main.languageServer.resolveIncremental
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LanguageServerIncrementalTest {
    @Test
    fun removingTypeDeclarationAgainInvalidatesIncrementalCache() {
        val dir = createTempDirectory("niva-ls-incremental").toFile()
        val mainFile = dir.resolve("main.niva").also { it.writeText("") }
        val otherFile = dir.resolve("models.niva")
        val withType = """
            type Foo
            Foo id -> Foo = this
        """.trimIndent()
        val withoutType = """
            Foo id -> Foo = this
        """.trimIndent()
        otherFile.writeText(withType)

        val ls = LS()
        ls.resolveAllFirstTime(mainFile.toURI().toString(), fillNonIncrementalStore = true, changedFileContent = "")

        assertFailsWith<CompilerError> {
            ls.resolveIncremental(otherFile.toURI().toString(), withoutType, changeLine = 0)
        }

        ls.resolveIncremental(otherFile.toURI().toString(), withType, changeLine = 0)

        assertFailsWith<CompilerError> {
            ls.resolveIncremental(otherFile.toURI().toString(), withoutType, changeLine = 0)
        }
    }

    @Test
    fun syntaxErrorDuringFullResolveKeepsLastGoodNonIncrementalStore() {
        val dir = createTempDirectory("niva-ls-syntax-error").toFile()
        val mainFile = dir.resolve("main.niva").also { it.writeText("") }
        val otherFile = dir.resolve("models.niva")
        val validSource = """
            type Foo
            Foo id -> Foo = this
        """.trimIndent()
        otherFile.writeText(validSource)

        val ls = LS()
        ls.resolveAllFirstTime(mainFile.toURI().toString(), fillNonIncrementalStore = true, changedFileContent = "")

        assertFailsWith<CompilerError> {
            ls.resolveAllFirstTime(
                otherFile.toURI().toString(),
                fillNonIncrementalStore = true,
                changedFileContent = "$validSource\nFoo broken = ["
            )
        }

        assertTrue(mainFile.absolutePath in ls.nonIncrementalStore)
        ls.resolveIncremental(otherFile.toURI().toString(), validSource, changeLine = 0)
    }
}

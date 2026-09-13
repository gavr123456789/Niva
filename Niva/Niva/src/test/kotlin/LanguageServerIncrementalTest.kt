import main.frontend.meta.CompilerError
import main.frontend.parser.types.ast.IdentifierExpr
import main.languageServer.LS
import main.languageServer.LspResult
import main.languageServer.onCompletion
import main.languageServer.resolveAllFirstTime
import main.languageServer.resolveIncremental
import main.languageServer.resolveNonIncremental
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LanguageServerIncrementalTest {
    @Test
    fun completionInsidePartialMessageNameUsesReceiverFromPreviousValidLineState() {
        val dir = createTempDirectory("niva-ls-partial-message-completion").toFile()
        val validSource = """
            type Person name: String
            person = Person name: "Alice"
            person name
        """.trimIndent()
        val partialSource = """
            type Person name: String
            person = Person name: "Alice"
            person nam
        """.trimIndent()
        val mainFile = dir.resolve("main.niva").also { it.writeText(validSource) }

        val ls = LS()
        val uri = mainFile.toURI().toString()
        ls.resolveAllFirstTime(uri, fillNonIncrementalStore = true, changedFileContent = validSource)

        val result = ls.onCompletion(uri, line = 2, character = "person nam".length, sourceText = partialSource)

        assertTrue(result is LspResult.Found, "Expected Found, got ${result::class.simpleName}; ${ls.debugCountsLine()}")
        val statement = result.statement
        assertTrue(statement is IdentifierExpr)
        assertEquals("person", statement.name)
        assertEquals("Person", statement.type?.name)
    }

    @Test
    fun completionInsidePartialMessageNameOnNewLineUsesReceiverFromPreviousScope() {
        val dir = createTempDirectory("niva-ls-partial-message-new-line-completion").toFile()
        val validSource = """
            type Sas
            Sas from: Int tooo: Int = from + tooo
            sas = Sas new
        """.trimIndent()
        val partialSource = "$validSource\nsas fro"
        val mainFile = dir.resolve("main.niva").also { it.writeText(validSource) }

        val ls = LS()
        val uri = mainFile.toURI().toString()
        ls.resolveAllFirstTime(uri, fillNonIncrementalStore = true, changedFileContent = validSource)

        val result = ls.onCompletion(uri, line = 3, character = "sas fro".length, sourceText = partialSource)

        assertTrue(result is LspResult.Found, "Expected Found, got ${result::class.simpleName}; ${ls.debugCountsLine()}")
        val statement = result.statement
        assertTrue(statement is IdentifierExpr)
        assertEquals("sas", statement.name)
        assertEquals("Sas", statement.type?.name)
    }

    @Test
    fun completionAfterUnresolvedFullMessageNameUsesReceiverFallback() {
        val dir = createTempDirectory("niva-ls-unresolved-message-completion").toFile()
        val validSource = """
            type Sas
            Sas from: Int tooo: Int = from + tooo
            sas = Sas new
        """.trimIndent()
        val partialSource = "$validSource\nsas from"
        val mainFile = dir.resolve("main.niva").also { it.writeText(validSource) }

        val ls = LS()
        val uri = mainFile.toURI().toString()
        ls.resolveAllFirstTime(uri, fillNonIncrementalStore = true, changedFileContent = validSource)

        val result = ls.onCompletion(uri, line = 3, character = "sas from".length, sourceText = partialSource)

        assertTrue(result is LspResult.Found, "Expected Found, got ${result::class.simpleName}; ${ls.debugCountsLine()}")
        val statement = result.statement
        assertTrue(statement is IdentifierExpr)
        assertEquals("sas", statement.name)
        assertEquals("Sas", statement.type?.name)
    }

    @Test
    fun failedTopLevelExpressionResolveKeepsPreviousTypedStateForCompletion() {
        val dir = createTempDirectory("niva-ls-failed-expression-keeps-types").toFile()
        val validSource = """
            type Sas
            Sas from: Int tooo: Int = from + tooo
            sas = Sas new
        """.trimIndent()
        val invalidSource = "$validSource\nsas from"
        val mainFile = dir.resolve("main.niva").also { it.writeText(validSource) }

        val ls = LS()
        val uri = mainFile.toURI().toString()
        ls.resolveAllFirstTime(uri, fillNonIncrementalStore = true, changedFileContent = validSource)

        assertFailsWith<CompilerError> {
            ls.resolveIncremental(uri, invalidSource, changeLine = 3)
        }

        val result = ls.onCompletion(uri, line = 3, character = "sas from".length, sourceText = invalidSource)

        assertTrue(result is LspResult.Found, "Expected Found, got ${result::class.simpleName}; ${ls.debugCountsLine()}")
        val statement = result.statement
        assertTrue(statement is IdentifierExpr)
        assertEquals("sas", statement.name)
        assertEquals("Sas", statement.type?.name)
    }

    @Test
    fun failedFullResolveKeepsPreviousTypedMegaStoreStateForCompletion() {
        val dir = createTempDirectory("niva-ls-failed-full-resolve-keeps-types").toFile()
        val validSource = """
            type Sas
            Sas from: Int tooo: Int = from + tooo
            sas = Sas new
        """.trimIndent()
        val invalidSource = "$validSource\nsas from"
        val mainFile = dir.resolve("main.niva").also { it.writeText(validSource) }

        val ls = LS()
        val uri = mainFile.toURI().toString()
        ls.resolveAllFirstTime(uri, fillNonIncrementalStore = true, changedFileContent = validSource)

        assertFailsWith<CompilerError> {
            ls.resolveNonIncremental(uri, invalidSource, forceFull = true)
        }

        val result = ls.onCompletion(uri, line = 3, character = "sas from".length, sourceText = invalidSource)

        assertTrue(result is LspResult.Found, "Expected Found, got ${result::class.simpleName}; ${ls.debugCountsLine()}")
        val statement = result.statement
        assertTrue(statement is IdentifierExpr)
        assertEquals("sas", statement.name)
        assertEquals("Sas", statement.type?.name)
    }

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

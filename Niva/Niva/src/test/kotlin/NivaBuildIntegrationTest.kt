import java.io.File
import java.nio.file.Files
import java.time.Duration
import main.run
import main.utils.listFilesDownUntilNivaIsFoundRecursively
import frontend.resolver.parseFilesToAST
import frontend.resolver.nivaSourceModuleId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class NivaBuildIntegrationTest {
    private val projectDir = File(System.getProperty("user.dir"))

    @Test
    fun `builds mira type system`() {
        val tempDir = Files.createTempDirectory("niva-integration-").toFile()
        try {
            val checkoutDir = File(tempDir, "mira-type-system")
            runProcess(
                command = listOf(
                    "git",
                    "clone",
                    "--depth",
                    "1",
                    "https://codeberg.org/FishingHacks/mira-type-system",
                    checkoutDir.absolutePath
                ),
                workingDir = tempDir,
                timeout = Duration.ofMinutes(3),
                label = "git clone mira-type-system"
            )

            runNivaBuild(checkoutDir)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `builds NivaInNiva project`() {
        val nivaInNivaDir = projectDir.parentFile.resolve("NivaInNiva")
        assertTrue(
            nivaInNivaDir.resolve("main.niva").isFile,
            "Expected NivaInNiva project with main.niva at ${nivaInNivaDir.absolutePath}"
        )

        runNivaBuild(nivaInNivaDir)
    }

    @Test
    fun `kotlin source loader filters foreign bindings before parsing`() {
        val tempDir = Files.createTempDirectory("niva-bindings-").toFile()
        try {
            File(tempDir, "main.niva").writeText("")
            File(tempDir, "shared.niva").writeText("")
            File(tempDir, "folder.go.bind").mkdir()
            File(tempDir, "folder.go.bind/common.niva").writeText("")
            File(tempDir, "clock.kotlin.bind.niva").writeText(
                "Bind package: \"example\" content: [ type Clock ]"
            )
            File(tempDir, "io.go.bind.niva").writeText("package main\nfunc (")
            File(tempDir, "old.bind.niva").writeText("not valid Niva {{{")
            File(tempDir, ".hidden").mkdir()
            File(tempDir, ".hidden/secret.go.bind.niva").writeText("package main\nfunc (")

            val selected = listFilesDownUntilNivaIsFoundRecursively(tempDir, "niva")
            val selectedNames = selected.map { it.relativeTo(tempDir).invariantSeparatorsPath }.toSet()
            assertEquals(
                setOf("main.niva", "shared.niva", "folder.go.bind/common.niva", "clock.kotlin.bind.niva"),
                selectedNames
            )
            assertEquals(selected.map { it.path }.sorted(), selected.map { it.path })

            val parsed = parseFilesToAST(
                mainFileContent = File(tempDir, "main.niva").readText(),
                otherFileContents = selected.filter { it.name != "main.niva" },
                mainFilePath = File(tempDir, "main.niva").absolutePath,
                resolveOnlyOneFile = false,
            )
            assertEquals(3, parsed.third.size)
            assertEquals(3, parsed.second.size)
            assertEquals(setOf("clock.bind", "shared", "common"), parsed.second.map { it.first }.toSet())
            assertEquals("clock.bind", nivaSourceModuleId(File(tempDir, "clock.kotlin.bind.niva")))
            assertEquals("stackTrace", nivaSourceModuleId(File(tempDir, "stackTrace.kotlin.bind.niva")))
            assertEquals("compiler", nivaSourceModuleId(File(tempDir, "compiler.kotlin.bind.niva")))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun runNivaBuild(workingDir: File) {
        run(arrayOf("build", workingDir.absolutePath))
    }

    private fun runProcess(
        command: List<String>,
        workingDir: File,
        timeout: Duration,
        label: String
    ): String {
        val outputFile = Files.createTempFile("niva-integration-output-", ".log").toFile()
        try {
            val process = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .redirectOutput(outputFile)
                .start()

            val finished = process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                fail("$label timed out after $timeout\n${outputFile.readText()}")
            }

            val exitCode = process.exitValue()
            val output = outputFile.readText()
            if (exitCode != 0) {
                fail("$label failed with exit code $exitCode\n$output")
            }
            return output
        } finally {
            outputFile.delete()
        }
    }
}

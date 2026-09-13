import java.io.File
import java.nio.file.Files
import java.time.Duration
import main.run
import kotlin.test.Test
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

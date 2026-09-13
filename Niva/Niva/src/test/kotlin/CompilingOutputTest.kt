import main.utils.RED
import main.utils.RESET
import main.utils.formatFailedTestsOutput
import kotlin.test.Test
import kotlin.test.assertEquals

class CompilingOutputTest {
    @Test
    fun failedTestOutputKeepsStandardOutAndMultilineFailureText() {
        val gradleOutput = """
            Reusing configuration cache.
            > Task :test FAILED
            
            resolverTestsTest > errorEffectsMismatch() STANDARD_OUT
                first output line
                second output line
            
            resolverTestsTest > errorEffectsMismatch() FAILED
                java.lang.Exception: No such constructor throw' for type  Type(Error ).
                Available constructors:
                  - sas
            
            1 test completed, 1 failed
            
            FAILURE: Build failed with an exception.
            BUILD FAILED in 1s
            4 actionable tasks: 1 executed, 3 up-to-date
        """.trimIndent()

        assertEquals(
            """
                resolverTestsTest > errorEffectsMismatch() ${RED}❌$RESET
                first output line
                second output line
                No such constructor throw' for type  Type(Error ).
                Available constructors:
                  - sas
            """.trimIndent(),
            formatFailedTestsOutput(gradleOutput)
        )
    }
}

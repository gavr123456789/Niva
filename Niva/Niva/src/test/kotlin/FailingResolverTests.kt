import main.frontend.meta.CompilerError
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class FailingResolverTests {
    @Test
    fun mutListnotEqualList() {
        val source = """
            x = {1 2 3}!
            y::mut List(Int) = x reversed
        """.trimIndent()
        assertThrows<CompilerError> {
            val (_) = resolveWithResolver(source)
        }
    }
    @Test
    fun noMutabilityCompareBetweenGenericArgs(){
        // there is no point to create type without fields as mutable anyway
        val source = """
            type Ball
        
            balls::List(mut Ball) = {(Ball new toMut)}
        """.trimIndent()
        val (x) = resolveWithResolver(source)
        assert(x.count() == 2)
    }

    @Test
    fun checkForErrorsWorksOnTopLevel(){
        val source = """
            T sas -> T!Error = [
                ^ this
                Error throwWithMessage: "qfwars"
            ]
            
            1 sas orPANIC echo
        """.trimIndent()
        val (x) = resolveWithResolver(source)
        assert(x.count() == 2)
    }
}
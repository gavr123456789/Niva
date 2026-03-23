import frontend.resolver.Type
import main.frontend.meta.CompilerError
import main.frontend.parser.types.ast.KeywordMsg
import main.frontend.parser.types.ast.MessageDeclarationKeyword
import main.frontend.parser.types.ast.MessageSendKeyword
import main.frontend.parser.types.ast.TypeAST
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

class FailingResolverTests {
    @Test
    fun pipesAreNowBrackets(){
        val source = """
            Int from: Int = 0
            ((1 from: 2) from: 3) from: 4
            5 from: 6, from: 7, from: 8
        """.trimIndent()
        val (x) = resolveWithResolver(source)
        assert(x.count() == 3)
    }

    @Test
    fun usingTypeInsteadOfValue() {

        val source = """
            type Sas
            Unit sas: Sas = []
            ()sas: Sas
        """.trimIndent()


        assertFails {
            resolve(source)
//            assert(statements.count() == 3)
        }
    }

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

    @Test
    fun sendMethodToMethod2() {

        val source = """
            String msg::String[Int, Int -> Unit] = [
                str = "sas"
                msg this: str Int: 1 Int: 2
            ]
            
            String x::Int y::Int = 1 echo
            
            "sas" msg: &String x:y: 
        """.trimIndent()
        val statements = resolve(source)
        assert(statements.count() == 3)
        val q = statements[0] as MessageDeclarationKeyword
        val w = q.args[0].typeAST!! as TypeAST.Lambda
        assertTrue { w.extensionOfType!!.name == "String" }

        val x = (statements[2] as MessageSendKeyword).messages.first() as KeywordMsg
        val t = x.args.first().keywordArg.type!! as Type.Lambda
        assertTrue { t.args.count() == 3 }
        assertTrue { t.args.first().type.name == "String" }
    }

    @Test
    fun assignOnlyToRealThings() {
        val source = """
            type LearnModel
              answered: Bool
            
            LearnModel answer = answer <- true
        """.trimIndent()
        assertThrows<CompilerError> {
            resolveWithResolver(source)
        }
    }
}







//@Test
//    fun builder() {
//        /// example
//        fun buildString2(buildString2: StringBuilder.((String) -> Any) -> Unit): String {
//            val b = StringBuilder()
//            val defaultAction = { default: String ->
//                b.append(default)
//            }
//            buildString2(b, defaultAction)
//            return b.toString()
//
//        }
//        buildString2 { defaultAction ->
//            defaultAction("sas")
//        }
//        ///
//
//        val source = """
//            builder StringBuilder buildString -> String = [
//                b = StringBuilder new
//                defaultAction = [ default::String ->
//                    b append: default
//                ]
//
//
//                build this: b defaultAction: defaultAction
//
//                ^ b toString
//            ]
//
//            buildString [
//                1 echo
//                "sas"
//                "sus"
//                345
//            ]
//        """.trimIndent()
//        val (statements, _) = resolveWithResolver(source)
//        assert(statements.count() == 2)
//    }



//@Test
//fun builderWithArgs() {
//
//    val source = """
//            type Card
//            Card sas = 1
//            builder Card width::Int height::Int -> String = [
//                card = Card new
//                defaultAction = [ default::String ->
//                    1 echo
//                ]
//
//
//                build this: card defaultAction: defaultAction
//
//                ^ card toString
//            ]
//
//            Card (width: 24 height: 30) [
//                1 echo
//                this sas
//            ]
//
//        """.trimIndent()
//    val (statements, _) = resolveWithResolver(source)
//    assert(statements.count() == 4)
//}



//    @Test
//    fun builderWithArgsWithReceiver() {
//
//        val source = """
//            type Card
//            Card sas = 1
//            String builder Card width::Int height::Int -> String = [
//                card = Card new
//                defaultAction = [ default::String ->
//                    1 echo
//                ]
//
//
//                build this: card defaultAction: defaultAction
//
//                ^ card toString
//            ]
//
//        """.trimIndent()
//        val (statements, _) = resolveWithResolver(source)
//        assert(statements.count() == 3)
//    }


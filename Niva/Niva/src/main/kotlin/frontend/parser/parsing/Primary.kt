package frontend.parser.parsing

import main.frontend.meta.TokenType
import main.frontend.parser.parsing.simpleReceiver
import main.frontend.parser.parsing.staticBuilderFromUnary
import main.frontend.parser.parsing.staticBuilderFromUnaryWithArgs
import main.frontend.parser.types.ast.MessageSendBinary
import main.frontend.parser.types.ast.MessageSendKeyword
import main.frontend.parser.types.ast.MessageSendUnary
import main.frontend.parser.types.ast.Receiver
import main.utils.RED
import main.utils.RESET
import kotlin.collections.isNotEmpty
import kotlin.text.startsWith


// also recevier can be unary or binary message

// receiver examples - (receiver)
// (1) sas // simple unary
// (1) + 2 // simple binary
// (1) to: 2 // simple keyword
// (1 sas) to: 2 // keyword with unary receiver
// (1 + 2) to: 2 // keyword with binary receiver
// (1 inc + 2 inc) to: 2 // keyword with binary receiver
// So unary and binary messages can be the receiver,
// also collections
// also code blocks

// receiver like collection, code block, identifier,
fun Parser.unaryOrBinaryMessageOrPrimaryReceiver(
    customReceiver: Receiver? = null,
    insideKeywordArgument: Boolean = false
): Receiver {

    val safePoint = current
    try {
        // we don't need to parse cascade if we are inside keyword argument parsing, since this cascade will be applied to
        // the kw argument itself, like x from: 1 - 1 |> echo, echo will be applied to 1 - 1, not x
        // or Person name: "Alice" |> getName
        when (val messageSend =
            unaryOrBinary(
                customReceiver = customReceiver,
                parsePipe = !insideKeywordArgument, //|| check(TokenType.EndOfLine, 1) || check(TokenType.EndOfLine),
                parseCascade = !insideKeywordArgument
            )) {
            is MessageSendUnary -> {
                return if (messageSend.messages.isNotEmpty()) {
                    if (check(TokenType.OpenBracket)) {
                        staticBuilderFromUnary(messageSend)
                    } else if (check(TokenType.OpenParen)) {
                        staticBuilderFromUnaryWithArgs(messageSend)
                    } else
                        messageSend
                } else
                    messageSend.receiver
            }

            is MessageSendBinary -> {
                return if (messageSend.messages.isNotEmpty())
                    messageSend
                else
                    messageSend.receiver
            }

            is MessageSendKeyword -> error("keyword can be a receiver only when piped, 1 from: 2 |> to: 3")
        }
    } catch (e: Throwable) {
        if (e.message?.startsWith("${RED}Error:${RESET}") == true) {
            throw e
        }
        current = safePoint
    }
    current = safePoint
    // no messages as receiver
    return simpleReceiver()
}

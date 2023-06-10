package frontend.parser.types


//class MessageCall {
//
//}


//
//sealed class MessageSend(
//    val receiver: Expression,
//    type: Type?,
//    token: Token
//): Expression(type, token)
//
//class UnarySend (
//    val unaryTail: List<Unary>,
//
//    receiver: Expression,
//    type: Type?,
//    token: Token
//): MessageSend(receiver, type, token)
//
//class BinarySend (
//    binaryTail: List<Binary>,
//
//    receiver: Expression,
//    type: Type?,
//    token: Token
//): MessageSend(receiver, type, token)
//
//
//class KeywordPair(
//    receiver: Expression,
//    binaryAndUnaryMessages: List<MessageSend>
//)
//
//class KeywordSend (
//    keywordPair: List<KeywordPair>,
//
//    receiver: Expression,
//    type: Type?,
//    token: Token
//): MessageSend(receiver, type, token)
//
//
//
//sealed class Message2(
//
//    type: Type?,
//    token: Token
//): Expression(type, token)
//
//class Keyword (
//
//    type: Type?,
//    token: Token
//): Message2(type, token)
//
//class Binary (
//    val binary: Expression,
//    val unarySend: UnarySend,
//    type: Type?,
//    token: Token
//): Message2(type, token)
//
//class Unary (
//
//    type: Type?,
//    token: Token
//): Message2(type, token)

@file:Suppress("UNUSED_PARAMETER", "unused")

package frontend.resolver

import frontend.parser.types.ast.KeyPragma
import main.frontend.meta.Position
import main.frontend.meta.Token
import main.frontend.meta.TokenType
import main.frontend.parser.types.ast.DocComment
import main.frontend.parser.types.ast.InternalTypes
import main.frontend.parser.types.ast.LiteralExpression
import java.io.File


fun createIntProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    floatType: Type.InternalType,
    doubleType: Type.InternalType,
    intRangeType: Type.InternalType,
    anyType: Type.InternalType,
    charType: Type.InternalType,
    longType: Type.InternalType

): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType, ),
            createUnary("inc", intType, docComment = "increments the number by 1\n1 inc == 2"),
            createUnary("dec", intType, "1 dec == 0"),
            createUnary("toFloat", floatType),
            createUnary("toDouble", doubleType),
            createUnary("toLong", longType),
            createUnary("toString", stringType),
            createUnary("toChar", charType),
        ),
        binaryMsgs = mutableMapOf(
            createBinary("==", intType, boolType),
            createBinary("!=", intType, boolType),
            createBinary(">", intType, boolType),
            createBinary("<", intType, boolType),
            createBinary("<=", intType, boolType),
            createBinary(">=", intType, boolType),
            createBinary("+", intType, intType),
            createBinary("-", intType, intType),
            createBinary("*", intType, intType),
            createBinary("%", intType, intType, "5 % 2 == 1"),
            createBinary("/", intType, intType),
            createBinary("..", intType, intRangeType, "Creates a range from this value to the specified (included), use ..< for excluded"),
            createBinary("..<", intType, intRangeType, "Creates a range from this value to the specified (excluded)"),
            ),
        keywordMsgs = mutableMapOf(
//            createKeyword(KeywordArg("plus", intType), intType),
            createKeyword(KeywordArg("downTo", intType), intRangeType, "Returns a Range from this value down to the specified to value with the step -1"),
            createKeyword(
                "toDo",
                listOf(
                    KeywordArg("to", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(KeywordArg("do", intType)), anyType))
                ),
                unitType,
                docComment = "1 to: 3 do: [it echo] // 1 2 3"
            ),
            createKeyword(
                "repeat",
                listOf(
                    KeywordArg("repeat", Type.Lambda(mutableListOf(KeywordArg("it", intType)), anyType))
                ),
                unitType,
                docComment = "`3 repeat: [it echo] // 0 1 2`"
            ),
            createKeyword(
                "toByDo",
                listOf(
                    KeywordArg("to", intType),
                    KeywordArg("by", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(KeywordArg("do", intType)), anyType))
                ),
                unitType,
                docComment = "1 to: 10 by: 2 do: [it echo] // 1 3 5 7 9"
            ),

            createKeyword(
                "untilDo",
                listOf(
                    KeywordArg("until", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(KeywordArg("do", intType)), anyType))
                ),
                unitType,
                "1 until: 4 do: [it echo] // 1 2 3"
            ),

            createKeyword(
                "downToDo",
                listOf(
                    KeywordArg("downTo", intType),
                    KeywordArg("do", Type.Lambda(mutableListOf(KeywordArg("do", intType)), anyType))
                ),
                unitType,
                "3 downTo: 1 do: [it echo] // 3 2 1"
            ),
        ),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}

fun createFloatProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    floatType: Type.InternalType,

    ): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("inc", floatType),
            createUnary("dec", floatType),
            createUnary("toInt", intType),
            createUnary("toString", stringType),

            createUnary("isInfinite", boolType),
            createUnary("isFinite", boolType),
            createUnary("isNaN", boolType),
        ),
        binaryMsgs = mutableMapOf(
            createBinary("==", intType, boolType),
            createBinary("!=", intType, boolType),
            createBinary(">", intType, boolType),
            createBinary("<", intType, boolType),
            createBinary("<=", intType, boolType),
            createBinary(">=", intType, boolType),

            createBinary("==", floatType, boolType),
            createBinary("!=", floatType, boolType),
            createBinary(">", floatType, boolType),
            createBinary("<", floatType, boolType),
            createBinary("<=", floatType, boolType),
            createBinary(">=", floatType, boolType),

            createBinary("+", intType, floatType),
            createBinary("-", intType, floatType),
            createBinary("*", intType, floatType),
            createBinary("%", intType, floatType),
            createBinary("/", intType, floatType),

            createBinary("+", floatType, floatType),
            createBinary("-", floatType, floatType),
            createBinary("*", floatType, floatType),
            createBinary("%", floatType, floatType),
            createBinary("/", floatType, floatType),
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("mod", intType), floatType).rename("get"),

            ),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}

fun String.createDocComment(): DocComment =
    DocComment(this)

fun createUnary(name: String, returnType: Type, docComment: String? = null, forMutable: Boolean = false): Pair<String, UnaryMsgMetaData> =
    name to UnaryMsgMetaData(name, returnType, "core", declaration = null, docComment = docComment?.createDocComment()).also { it.forMutableType = forMutable }

fun createBinary(name: String, argType: Type, returnType: Type, docComment: String? = null, forMutable: Boolean = false): Pair<String, BinaryMsgMetaData> =
    name to BinaryMsgMetaData(name, argType, returnType, "core", declaration = null, docComment = docComment?.createDocComment()).also { it.forMutableType = forMutable }

fun createKeyword(name: String, args: List<KeywordArg>, returnType: Type, docComment: String? = null, forMutable: Boolean = false) =
    name to KeywordMsgMetaData(name, args, returnType, "core", declaration = null,
        docComment = docComment?.createDocComment()).also { it.forMutableType = forMutable }

fun createKeyword(arg: KeywordArg, returnType: Type, docComment: String? = null, forMutable: Boolean = false): Pair<String, KeywordMsgMetaData> =
    arg.name to KeywordMsgMetaData(arg.name, listOf(arg), returnType, "core", declaration = null,
        docComment = docComment?.createDocComment()).also { it.forMutableType = forMutable }


fun Pair<String, UnaryMsgMetaData>.emit(str: String): Pair<String, UnaryMsgMetaData> {
    this.second.pragmas.add(createEmitAtttribure(str))
    return this
}

fun Pair<String, KeywordMsgMetaData>.emitKw(str: String): Pair<String, KeywordMsgMetaData> {
    this.second.pragmas.add(createEmitAtttribure(str))
    return this
}

fun Pair<String, UnaryMsgMetaData>.renameUnary(str: String): Pair<String, UnaryMsgMetaData> {
    this.second.pragmas.add(createRenameAtttribure(str))
    return this
}

fun Pair<String, KeywordMsgMetaData>.rename(str: String): Pair<String, KeywordMsgMetaData> {
    this.second.pragmas.add(createRenameAtttribure(str))
    return this
}

fun createStringProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    charType: Type.InternalType,
    any: Type.InternalType,
    floatType: Type.InternalType,
    doubleType: Type.InternalType,
    intRangeType: Type.InternalType,
//    genericType: Type.UnknownGenericType,
//    listOfGenericType: Type.UserType
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()
    val arithmeticProtocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType, "Length of this String"),
            createUnary("reversed", stringType, "Returns a string with characters in reversed order"),
            createUnary("trim", stringType, "Returns a string having leading and trailing whitespace removed"),
            createUnary("trimIndent", stringType, "Detects a common minimal indent of all the input lines, removes it from every line"),
            createUnary("isBlank", boolType, "true if this char sequence is empty or consists solely of whitespace characters"),
            createUnary("isEmpty", boolType, "Only the \"\" is true"),
            createUnary("isAlphaNumeric", boolType, "True if its only digits and characters like \"abc123\""),
            createUnary("isNotBlank", boolType, "Opposite to isBlank"),
            createUnary("isNotEmpty", boolType, "Opposite to isEmpty"),
            createUnary("toInt", intType),
            createUnary("toFloat", floatType),
            createUnary("toDouble", doubleType),
            createUnary("uppercase", stringType, "Returns a copy of this string converted to upper case using Unicode mapping rules of the invariant locale."),
            createUnary("lowercase", doubleType, "Returns a copy of this string converted to lower case using Unicode mapping rules of the invariant locale."),
            createUnary("first", charType, "Returns the first character or panic"),
            createUnary("last", charType, "Returns the last character or panic"),
            createUnary("indices", intRangeType).emit("$0.indices"), // not a function, no need `()`
            createUnary("echo", unitType),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("+", stringType, stringType, "Concatenate 2 Strings"),
            createBinary("==", stringType, boolType),
            createBinary("!=", stringType, boolType),
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(
                "replaceWith",
                listOf(KeywordArg("replace", stringType), KeywordArg("with", stringType)), stringType,
                "\"Tom the cat\" replace: \"cat\" with: \"mouse\""
            ).rename("replace"),
            createForEachKeyword(charType, unitType, docComment = """"abc" forEach: [char -> char echo] // a b c """),
            createForEachKeywordIndexed(intType, charType, unitType, """"abc" forEachIndexed: [index, char -> char echo] // a b c """),
            createFilterKeyword(charType, boolType, stringType, """"abc" filter: [it != 'c'] // "ab""""),

            createKeyword(KeywordArg("startsWith", stringType), boolType, """"abc" startsWith: "ab" // true"""),
            createKeyword(KeywordArg("contains", stringType), boolType),
            createKeyword(KeywordArg("endsWith", stringType), boolType, "see startWith:"),


            createKeyword(KeywordArg("substring", intType), stringType, "Returns a substring of this string that starts at the specified startIndex and continues to the end of the string"),
            createKeyword(KeywordArg("slice", intRangeType), stringType, docComment = """
                | Returns a string containing characters of the original string at the specified range
                |```niva
                | "abcd" slice: 0..<3 // abc
                |```""".trimMargin()),
            createKeyword(KeywordArg("substringAfter", stringType), stringType,
                "Returns a substring after the first occurrence of delimiter"),
            createKeyword(KeywordArg("substringAfterLast", stringType), stringType),
            createKeyword(KeywordArg("substringBefore", stringType), stringType, "see substringAfter:"),
            createKeyword(KeywordArg("substringBeforeLast", stringType), stringType),

            createKeyword(
                "substringFromTo",
                listOf(KeywordArg("substringFrom", intType), KeywordArg("to", intType)),
                stringType,
                "see substring:"
            ).rename("substring"),


            createKeyword(KeywordArg("at", intType), charType, "Returns the character of this string at the specified index or panic").rename("get"),
            createKeyword(KeywordArg("drop", intType), stringType, "Returns a string with the first n characters removed\n\"abc\" drop: 5 // empty \"\""),
            createKeyword(KeywordArg("dropLast", intType), stringType, "Returns a string with the last n characters removed"),
        ),
    )

    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}


@Suppress("UNUSED_PARAMETER")
fun createBoolProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    any: Type.InternalType,
    genericParam: Type.UnknownGenericType
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()

    val arithmeticProtocol = Protocol(
        name = "arithmetic",
        unaryMsgs = mutableMapOf(
            createUnary("not", boolType, "true not == false"),
            createUnary("isFalse", boolType),
            createUnary("isTrue", boolType),
            createUnary("echo", unitType),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("==", boolType, boolType),
            createBinary("!=", boolType, boolType),
            createBinary("&&", boolType, boolType),
            createBinary("||", boolType, boolType),
        ),
        keywordMsgs = mutableMapOf(

            createKeyword(KeywordArg("or", boolType), boolType, "Performs a logical or operation between this Boolean and the other one. Unlike the || operator, this function does not perform short-circuit evaluation."),
            createKeyword(KeywordArg("and", boolType), boolType, "see or:"),
            createKeyword(KeywordArg("xor", boolType), boolType),

            createKeyword(KeywordArg("ifTrue", Type.Lambda(mutableListOf(), genericParam)), unitType, "Evaluates the body if its true"),
            createKeyword(KeywordArg("ifFalse", Type.Lambda(mutableListOf(), genericParam)), unitType, "Evaluates the body if its false"),

            createKeyword(
                "ifTrueIfFalse",

                listOf(
                    KeywordArg("ifTrue", Type.Lambda(mutableListOf(), genericParam)),
                    KeywordArg("ifFalse", Type.Lambda(mutableListOf(), genericParam))
                ),

                genericParam
            ),
            createKeyword(
                "ifFalseIfTrue",

                listOf(
                    KeywordArg("ifFalse", Type.Lambda(mutableListOf(), genericParam)),
                    KeywordArg("ifTrue", Type.Lambda(mutableListOf(), genericParam))
                ),

                genericParam
            )


        ),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}


fun createCharProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    charType: Type.InternalType,
    any: Type.InternalType,
    charRange: Type.InternalType
): MutableMap<String, Protocol> {
    val result = mutableMapOf<String, Protocol>()
    val arithmeticProtocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(

            createUnary("dec", charType, "'b' inc == 'a'"),
            createUnary("inc", charType, "'a' inc == 'b'"),
            createUnary("isDigit", boolType, "'1' isDigit == true"),
            createUnary("isLetter", boolType, "'a' isLetter == true"),
            createUnary("isUpperCase", boolType, "'A' isUpperCase == true"),
            createUnary("isLowerCase", boolType, "'a' isLowerCase == true" ),
            createUnary("isWhitespace", boolType, "Includes ' ' '\r' '\n' '\t'"),

            createUnary("isLetterOrDigit", boolType),

            createUnary("lowercase", stringType, "Converts this character to lower case using Unicode mapping rules of the invariant locale"),
            createUnary("lowercaseChar", charType, "see lowercase"),
            createUnary("uppercase", charType, "see lowercase"),
            createUnary("uppercaseChar", charType, "see lowercase"),

            createUnary("digitToInt", intType, "'2' digitToInt == 2, or panic"),

            createUnary("echo", unitType)
        ),
        binaryMsgs = mutableMapOf(
            createBinary("+", intType, charType, "a + 2 == c"),
            createBinary("-", intType, charType),
            createBinary("==", charType, boolType),
            createBinary("!=", charType, boolType),
            createBinary("..", charType, charRange, "creates a iterable CharRange"),
        ),
        keywordMsgs = mutableMapOf(

        ),
    )
    result[arithmeticProtocol.name] = arithmeticProtocol
    return result
}



fun createNullableAnyProtocols(realType: Type?): MutableMap<String, Protocol> {
    // receiver is already T(on kotlin side)
    // so we need only one generic here
    val unitType = Resolver.defaultTypes[InternalTypes.Unit]!!
    val stringType = Resolver.defaultTypes[InternalTypes.String]!!
    val nothingType = Resolver.defaultTypes[InternalTypes.Nothing]!!
    val genericR = Type.UnknownGenericType("T")

    val realTypeOrNothing = realType ?: nothingType

    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("echonnl", unitType, "echo no new line"),
            createUnary("unpackOrPANIC", realTypeOrNothing, "if the value is null - exit the program")//.emit("$0!!")
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("unpackOrValue", realTypeOrNothing), realTypeOrNothing),

            createKeyword(
                KeywordArg(
                    "unpack",
                    Type.Lambda(mutableListOf(KeywordArg("it", realTypeOrNothing)), genericR)
                ),
                unitType,
                "Do something with value if its not nullable. " +
                         "`x unpack: [it echo]`"
            ),

            createKeyword(
                "unpackOr",
                listOf(
                    KeywordArg(
                        "unpack",
                        Type.Lambda(mutableListOf(KeywordArg("it", realTypeOrNothing)), genericR)
                    ),
                    KeywordArg(
                        "or",
                        genericR
                    ),
                ),

                genericR,
                "unpack and transform if not null, or default value\n`x unpack: [it toString] or: \"no value\"`"
            ),

            createKeyword(
                "unpackOrMsg",
                listOf(
                    KeywordArg(
                        "orMsg",
                        stringType
                    ),
                ),
                realTypeOrNothing,
                "unpack or exit program with message"
            ),
//            createKeyword(KeywordArg("unpackOrDo", realTypeOrNothing), realTypeOrNothing)
//                .emitKw("$0 ?: $1"),
        ),
    )
    return mutableMapOf(protocol.name to protocol)
}

fun createDynamicProtocol(currentType: Type, dynamicType: Type): Protocol {
    val protocol = Protocol(
        name = "common",
        staticMsgs = mutableMapOf(
            createKeyword(KeywordArg("toDynamic", currentType), dynamicType, "Creates Dynamic from current type"),
            createKeyword(KeywordArg("fromDynamic", dynamicType), currentType, "Creates Dynamic from current type")
        )
    )
    return protocol
}

fun createAnyProtocols(
    unitType: Type.InternalType,
    any: Type.InternalType,
    boolType: Type.InternalType,
    stringType: Type.InternalType

): MutableMap<String, Protocol> {
    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("echonnl", unitType, "echo no new line"),
            createUnary("toString", stringType),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("==", any, boolType),
            createBinary("!=", any, boolType),
        ),
        keywordMsgs = mutableMapOf(),
    )
    return mutableMapOf(protocol.name to protocol)
}

fun createRangeProtocols(
    rangeType: Type.InternalType,
    boolType: Type.InternalType,
    itType: Type.InternalType,
    unitType: Type.InternalType,

    listOfIt: Type.UserType,
    sequenceOfIt: Type.UserType
): MutableMap<String, Protocol> {
    //     listType: Type.UserType,
    //    sequenceType: Type.UserType,
    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("isEmpty", boolType),
            createUnary("first", itType),
            createUnary("last", itType),
            createUnary("random", itType, "Returns a random element from this range"),
            createUnary("toList", listOfIt),
            createUnary("asSequence", sequenceOfIt),
        ),
        binaryMsgs = mutableMapOf(
            createBinary("==", rangeType, boolType),
            createBinary("!=", rangeType, boolType)
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("step", itType), rangeType, "The step of the progression"),

            createForEachKeyword(itType, unitType),
            createForEachKeywordIndexed(itType, itType, unitType),
            createFilterKeyword(itType, boolType, rangeType),

            createKeyword(KeywordArg("contains", itType), boolType)
        ),
    )
    return mutableMapOf(protocol.name to protocol)
}


fun createTestProtocols(
    rangeType: Type.InternalType,
    boolType: Type.InternalType,

    itType: Type.InternalType,
    unitType: Type.InternalType,
    any: Type.InternalType,
): MutableMap<String, Protocol> {

    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(

        ),
        binaryMsgs = mutableMapOf(
        ),
        staticMsgs = mutableMapOf(
//            createKeyword(
//                KeywordArg(
//                    "assertTrue",
//                    Type.Lambda(
//                        mutableListOf(),
//                        boolType
//                    )
//                ),
//                unitType
//            ).emitKw("kotlin.test.assertTrue(block = $1)"),
//            createKeyword(KeywordArg("assertTrue", itType), boolType).emitKw("kotlin.test.assertTrue $1")
        ),
    )
    return mutableMapOf(protocol.name to protocol)
}


const val ERROR_THROW_COMMENT = "If not caught will be exit the program with stack trace printed"
fun createExceptionForCustomErrors(
    selfType: Type.Union,
): MutableMap<String, Protocol> {

    val nothingType = Resolver.defaultTypes[InternalTypes.Nothing]!!
    val stringType = Resolver.defaultTypes[InternalTypes.String]!!

    val protocol = Protocol(
        name = "core",
        unaryMsgs = mutableMapOf(
            createUnary("throw", nothingType, ERROR_THROW_COMMENT).also { it.second.addError(selfType) } // .rename("throw")
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
//            createKeyword(KeywordArg("addSuppressed", errorType), unitType),
        ),
        staticMsgs = mutableMapOf(
            throwWithMessageGenerate(stringType, nothingType),
        )
    )
    return mutableMapOf(protocol.name to protocol)
}

fun throwWithMessageGenerate(stringType: Type, nothingType: Type) =
    createKeyword(KeywordArg("throwWithMessage", stringType), nothingType).emitKw("throwWithMessage($1)")

fun createExceptionProtocols(
    errorType: Type.UnionBranchType,
    unitType: Type.InternalType,
    nothingType: Type.InternalType,
    stringType: Type.InternalType
): MutableMap<String, Protocol> {
    val nothingTypeWithError = nothingType.copyAnyType().also { it.errors = mutableSetOf(errorType) }


    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("echo", unitType),
            createUnary("throw", nothingTypeWithError, ERROR_THROW_COMMENT).emit("(throw $0)")//.also { it.second.errors.add() }
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("addSuppressed", errorType), unitType),
        ),
        staticMsgs = mutableMapOf(
            throwWithMessageGenerate(stringType, nothingTypeWithError).also {
                it.second.addError(errorType)
            }
        )
    )
    return mutableMapOf(protocol.name to protocol)
}

fun createStringBuilderProtocols(
    stringBuilderType: Type.UserType,
    anyType: Type.InternalType,
    stringType: Type.InternalType,
    intType: Type.InternalType
): MutableMap<String, Protocol> {

    val protocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
            createUnary("toString", stringType),
            createUnary("first", stringType),
            createUnary("last", stringType),
            createUnary("count", intType),

            ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(
            createKeyword(KeywordArg("append", anyType), stringBuilderType, "Appends the string representation of the arg"),
            createKeyword(KeywordArg("appendLine", anyType), stringBuilderType, "Appends the string representation of the arg "),
        ),
        staticMsgs = mutableMapOf(
//            createKeyword(KeywordArg("withText", stringType), stringBuilderType),
        )
    )
    return mutableMapOf(protocol.name to protocol)
}


fun createListProtocols(
    isMutable: Boolean,
    intType: Type.InternalType,
    stringType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    currentType: Type.UserType,
    listTypeOfDifferentGeneric: Type.UserType,
    itType: Type.UnknownGenericType,
    differentGenericType: Type.UnknownGenericType,
    sequenceType: Type.UserType,
    pairType: Type.UserType,
    listType: Type.UserType,
    mutListType: Type.UserType,

    ): MutableMap<String, Protocol> {
    val listOfLists = Type.UserType(
        name = "List",
        fields = listType.fields,
        typeArgumentList = mutableListOf(listType),
        pkg = "core",
        protocols = listType.protocols,
        typeDeclaration = null

    )
    val pairOf2ListsType = Type.UserType(
        name = "Pair",
        fields = pairType.fields,
        typeArgumentList = mutableListOf(sequenceType, sequenceType), // List<T>, List<G>
        pkg = "core",
        protocols = pairType.protocols,
        typeDeclaration = null
    )
    val itTypeNullable = Type.NullableType(itType)
    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("echo", unitType),
            createUnary("first", itType),

            createUnary("last", itType, "last or panic, use lastOrNull for safety"),
            createUnary("firstOrNull", itTypeNullable),
            createUnary("lastOrNull", itTypeNullable),

            createUnary("toList", listType, "Immutable list, elements will be shadow copied"),
            createUnary("toMutableList", mutListType, "Mutable list, elements will be shadow copied"),
//            createUnary("m", mutListType, "Mutable list, elements will be shadow copied").renameUnary("toMutableList"),

            createUnary("shuffled", listType, "Like in Solitaire"),

            createUnary("asSequence", sequenceType, "All processing methods like filter map, will execute lazy"),
            createUnary("isEmpty", boolType),
            createUnary("isNotEmpty", boolType),
            createUnary("reversed", listType),
            createUnary("sum", intType, "{1 2 3} sum == 6"),
            ),
        binaryMsgs = mutableMapOf(
            createBinary("+", currentType, listType, "new list and all elements of the give"),
            createBinary("-", currentType, listType, "new list except the elements contained in the give"),
        ),
        keywordMsgs = mutableMapOf(
            createForEachKeyword(itType, unitType),
            // mut
            createOnEach(currentType, itType, unitType),


            createForEachKeywordIndexed(intType, itType, unitType, """
                {1 2 3} forEachIndexed: [i, it -> 
                    "${"\$i"} element is ${"\$it"}" echo
                ] 
            """.trimIndent()),
            createMapKeyword(itType, differentGenericType, listTypeOfDifferentGeneric),
            createMapKeywordIndexed(intType, itType, differentGenericType, listTypeOfDifferentGeneric),
            createFilterKeyword(itType, boolType, currentType),
            createKeyword(KeywordArg("plus", itType), listType, "Returns a new list with given element"),
            createKeyword(KeywordArg("minus", itType), listType, "Returns a new list without given element"),

            createKeyword(KeywordArg("at", intType), itType, "like list[x] in C, can panic").rename("get"),
            createKeyword(KeywordArg("plusElement", itType), currentType, "new collection with element added"),
            createKeyword(KeywordArg("atOrNull", intType), itTypeNullable, "safe version of at").rename("getOrNull"),

            createKeyword(KeywordArg("contains", itType), boolType, "{1 2 3} contains: 1 is true"),
            createKeyword(KeywordArg("drop", intType), listType, "new list except first n elements"),
            createKeyword(KeywordArg("dropLast", intType), listType, "new list except last n elements."),
            createKeyword(KeywordArg("chunked", intType), listOfLists, "Splits this collection into a list of lists each not exceeding the given size"),
            createKeyword(KeywordArg("joinWith", stringType), stringType, """{1 2 3} joinWith: ", " is 1, 2, 3""").rename("joinToString"),
            createKeyword(
                KeywordArg(
                    "joinTransform",
                    Type.Lambda(
                        mutableListOf(KeywordArg("transform", itType)),
                        stringType
                    )
                ),
                stringType,
                """
                    Usable for joining fields of objects without unpacking them, elements joining with ", " 
                    ```Scala
                    type Person name: String
                    {(Person name: "Bob") (Person name: "Alice")} 
                        joinTransform: [ it name ] // Bob, Alice
                   ```
                """.trimIndent()
            ).rename("joinToString"),

            createKeyword(
                KeywordArg(
                    "indexOfFirst",
                    Type.Lambda(mutableListOf(KeywordArg("indexOfFirst", itType)), boolType)
                ),
                intType
            ),
            createKeyword(
                KeywordArg(
                    "firstOrNull",
                    Type.Lambda(mutableListOf(KeywordArg("firstOrNull", itType)), boolType)
                ),
                itTypeNullable
            ),
            createKeyword(
                KeywordArg(
                    "indexOfLast",
                    Type.Lambda(mutableListOf(KeywordArg("indexOfLast", itType)), boolType)
                ),
                intType
            ),

            createKeyword(
                KeywordArg(
                    "sortedBy",
                    Type.Lambda(
                        mutableListOf(KeywordArg("transform", itType)),
                        differentGenericType
                    )
                ),
                listType,
                """
                    For sorting collection of objects by one of their field
                    ```Scala
                    {(Person name: "Bob") (Person name: "Alice")} 
                        sortedBy: [ it name ]
                    // {Person name: "Alice", Person name: "Bob"}
                    ```
                """.trimIndent()
            ),


            createKeyword(
                "joinWithTransform",
                listOf(
                    KeywordArg(
                        "joinWith",
                        stringType
                    ),
                    KeywordArg(
                        "transform",
                        Type.Lambda(
                            mutableListOf(KeywordArg("transform", itType)),
                            stringType
                        )
                    )
                ),
                stringType,
                """Same as joinToString, but you can choose how to join(not only ", ")"""
            ),

            // fold
            createKeyword(
                "injectInto",
                listOf(
                    KeywordArg(
                        "inject",
                        differentGenericType
                    ),
                    KeywordArg(
                        "into",
                        Type.Lambda(
                            mutableListOf(KeywordArg("acc", differentGenericType), KeywordArg("each", itType)),
                            differentGenericType
                        )
                    )
                ),
                differentGenericType,
                """Accumulates value starting with injected value and applying operation from left to right to current accumulator value and each element(kinda fold)
                    |{1 2 3} inject: 0 into: [acc, next -> acc + next] // is 6
                """.trimMargin()
            ).rename("fold"),


            // reduce
            createKeyword(
                "reduce",
                listOf(
                    KeywordArg(
                        "reduce",
                        Type.Lambda(
                            mutableListOf(KeywordArg("acc", itType), KeywordArg("each", itType)),
                            differentGenericType
                        )
                    )
                ),
                itType,
                "Accumulates value starting with the first element and applying operation from left to right to current accumulator value and each element" +
                        "same as inject:into: but without accumulator arg"
            ),


            // partition, fun <T> Iterable<T>.partition(predicate: (T) -> Boolean): Pair<List<T>, List<T>>
            createKeyword(
                "partition",
                listOf(
                    KeywordArg(
                        "partition",
                        Type.Lambda(
                            mutableListOf(KeywordArg("partition", itType)),
                            boolType
                        )
                    )
                ),
                pairOf2ListsType,
                """Splits the original collection into pair of lists, where first list contains elements for which predicate yielded true, while second list contains elements for which predicate yielded false.
                    |{1 2 3 4} partition: [it % 2 == 0] // {[2, 4], [1, 3]}
                """.trimMargin()
            ),
            // sumOf, fun <T> Iterable<T>.sumOf(selector: (T) -> Int): Int
            createSumOf(itType),
            createFind(itType, boolType),

            createKeyword(
                "viewFromTo",
                listOf(KeywordArg("viewFrom", intType), KeywordArg("to", intType)),
                currentType,
                "Returns a view of the portion of this list between the specified fromIndex (inclusive) and toIndex (exclusive). The returned list is backed by this list, so non-structural changes in the returned list are reflected in this list, and vice-versa."
            ).rename("subList"),


        )
    )

    // this check is not needed since we are getting it from "forMutable" parameter
//    if (isMutable || true) {
        // unary
        val clear = createUnary("clear", unitType, forMutable = true)
        collectionProtocol.unaryMsgs[clear.first] = clear.second
        // kw
        val mutKwMsgs = mutableMapOf(
            createKeyword(KeywordArg("add", itType), boolType, forMutable = true),
            createKeyword(KeywordArg("addFirst", itType), unitType, forMutable = true),
            createKeyword(KeywordArg("addAll", currentType), boolType, "Add all items from other collection", forMutable = true),
            createKeyword(KeywordArg("removeAt", intType), unitType, "Remove element by index", forMutable = true),
            createKeyword(KeywordArg("remove", itType), unitType, "Remove element", forMutable = true),
            createKeyword(
                "atPut",
                listOf(
                    KeywordArg("at", intType),
                    KeywordArg("put", itType)
                ),
                unitType,
                "like C arr[x] = y",
                forMutable = true
            ).rename("set"),
            createKeyword(
                "atInsert",
                listOf(
                    KeywordArg("at", intType),
                    KeywordArg("insert", itType)
                ),
                unitType,
                "Inserts an element into the list at the specified index",
                forMutable = true
            ).rename("add")
        )
        collectionProtocol.keywordMsgs.putAll(mutKwMsgs)
//    }
    return mutableMapOf(collectionProtocol.name to collectionProtocol)
}

fun createSumOf(itType: Type) =
    createKeyword(
        "sumOf",
        listOf(
            KeywordArg(
                "sumOf",
                Type.Lambda(
                    mutableListOf(KeywordArg("predicate", itType)),
                    itType
                )
            )
        ),
        itType
    )

fun createFind(itType: Type, boolType: Type): Pair<String, KeywordMsgMetaData> {

    val nullableIfNeeded = if (itType is Type.NullableType) itType else Type.NullableType(itType)
    return createKeyword(
        "find",
        listOf(
            KeywordArg(
                "find",
                Type.Lambda(
                    mutableListOf(KeywordArg("predicate", itType)),
                    boolType
                )
            )
        ),
        nullableIfNeeded,
        "Returns the first element matching the given predicate, or null if no such element was found."
    )
}




fun createSetProtocols(
    isMutable: Boolean,
    intType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    mutableSetType: Type.UserType,
    setTypeOfDifferentGeneric: Type.UserType,
    itType: Type.UnknownGenericType,
    differentGenericType: Type.UnknownGenericType,
    listType: Type.UserType,
    setType: Type.UserType,
): MutableMap<String, Protocol> {
    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("echo", unitType),
            createUnary("clear", unitType, "removes every element"),
            createUnary("first", itType),
            createUnary("last", itType),
            createUnary("toList", listType),
            createUnary("toMutableList", listType),

            createUnary("toMutableSet", mutableSetType),
//            createUnary("m", mutableSetType).renameUnary("toMutableSet"),
            createUnary("toSet", setType),

            ),
        binaryMsgs = mutableMapOf(
            createBinary("==", mutableSetType, boolType),
            createBinary("!=", mutableSetType, boolType),
            createBinary("+", mutableSetType, mutableSetType),
            createBinary("+", itType, mutableSetType),
            createBinary("-", mutableSetType, mutableSetType),
            createBinary("-", itType, mutableSetType),

            ),
        keywordMsgs = mutableMapOf(
            createForEachKeyword(itType, unitType),
            createOnEach(mutableSetType, itType, unitType),

            createMapKeyword(itType, differentGenericType, setTypeOfDifferentGeneric),
            createMapKeywordIndexed(intType, itType, differentGenericType, setTypeOfDifferentGeneric),


            createFilterKeyword(itType, boolType, mutableSetType),

            createKeyword(KeywordArg("plus", itType), setType, "Returns a new set with given element"),
            createKeyword(KeywordArg("minus", itType), setType, "Returns a new set without given element"),
            createKeyword(KeywordArg("intersect", mutableSetType), mutableSetType),
            createKeyword(KeywordArg("contains", itType), boolType),
            createKeyword(KeywordArg("containsAll", mutableSetType), boolType, "Checks if all elements in the specified collection are contained in this set"),
        )
    )

    // this check is not needed since we are getting it from "forMutable" parameter
//    if (isMutable || true) {
        val add = createKeyword(KeywordArg("add", itType), unitType, forMutable = true)
        val remove = createKeyword(KeywordArg("remove", itType), boolType, forMutable = true)
        val addAll = createKeyword(KeywordArg("addAll", mutableSetType), boolType, forMutable = true)

        collectionProtocol.keywordMsgs[add.first] = add.second
        collectionProtocol.keywordMsgs[remove.first] = remove.second
        collectionProtocol.keywordMsgs[addAll.first] = addAll.second
//    }

    return mutableMapOf(collectionProtocol.name to collectionProtocol)
}


enum class CompilerMessages(val str: String) {
    GetName(str = "getName"),
    Debug(str = "debug"),
}
fun createCompilerProtocols(
    intType: Type.InternalType,
    stringType: Type.InternalType,
    typeType: Type.UserType,
    listOfString: Type.UserType,
    unitType: Type.InternalType,
): MutableMap<String, Protocol> {
    val commonProtocol = Protocol(
        name = "common",
        unaryMsgs = mutableMapOf(
//            createUnary("getArgs", listOfString),
//            createUnary("debug", unitType),
        ),
        binaryMsgs = mutableMapOf(),
        keywordMsgs = mutableMapOf(),
        staticMsgs = mutableMapOf(
            createUnary("debug", unitType, "Prints every variable from current scope"),
            createKeyword(KeywordArg("getName", intType), stringType),
//            createKeyword(KeywordArg("getType", intType), typeType),
            )
    )

    return mutableMapOf(commonProtocol.name to commonProtocol)
}

private fun createOnEach(
    collectionType: Type.UserType,
    genericTypeOfSetElements: Type,
    unitType: Type.InternalType
) = createKeyword(
    "onEach",
    listOf(
        KeywordArg(
            "onEach",
            Type.Lambda(
                mutableListOf(
                    KeywordArg("onEach", genericTypeOfSetElements)
                ),
                unitType
            )
        )
    ),
    collectionType
)

private fun createForEachKeyword(
    genericTypeOfSetElements: Type,
    unitType: Type.InternalType,
    docComment: String? = null
) = createKeyword(
    "forEach",
    listOf(
        KeywordArg(
            "forEach",
            Type.Lambda(
                mutableListOf(
                    KeywordArg("forEach", genericTypeOfSetElements)
                ),
                unitType
            )
        )
    ),
    unitType,
    docComment
)

private fun createForEachKeywordIndexed(
    intType: Type.InternalType,
    itType: Type,
    unitType: Type.InternalType,
    docComment: String? = null
) = createKeyword(
    "forEachIndexed",
    listOf(
        KeywordArg(
            "forEachIndexed",
            Type.Lambda(
                mutableListOf(
                    KeywordArg("i", intType),
                    KeywordArg("it", itType),
                ),
                unitType
            )
        )
    ),
    unitType,
    docComment
)

private fun createMapKeywordIndexed(
    intType: Type.InternalType,
    itType: Type,

    differentGenericType: Type.UnknownGenericType,
    listTypeOfDifferentGeneric: Type.UserType
) = createKeyword(
    "mapIndexed",
    listOf(
        KeywordArg(
            "mapIndexed",
            Type.Lambda(
                mutableListOf(
                    KeywordArg("i", intType),
                    KeywordArg("it", itType),
                ),
                differentGenericType
            )
        )
    ),
    listTypeOfDifferentGeneric
)


private fun createStringLiteral(lexeme: String) = LiteralExpression.StringExpr(
    Token(
        kind = TokenType.String,
        lexeme = """"$lexeme"""",
        line = 0,
        pos = Position(0, 0),
        relPos = Position(0, 0),
        file = File("."),
    )
)

fun createStringPragma(k: String, v: String) =
    KeyPragma(name = k, value = createStringLiteral(v))

fun createRenameAtttribure(v: String) =
    createStringPragma("rename", v)

fun createEmitAtttribure(v: String) =
    createStringPragma("emit", v)

fun createMapProtocols(
    isMutable: Boolean,
    intType: Type.InternalType,
    unitType: Type.InternalType,
    boolType: Type.InternalType,
    mutableMapType: Type.UserType,
    keyType: Type.UnknownGenericType,
    valueType: Type.UnknownGenericType,
    setType: Type.UserType,
    setTypeOfDifferentGeneric: Type.UserType,
    mapType: Type.UserType,

    ): MutableMap<String, Protocol> {

    val result = mutableMapOf<String, Protocol>()
    val collectionProtocol = Protocol(
        name = "collectionProtocol",
        unaryMsgs = mutableMapOf(
            createUnary("count", intType),
            createUnary("isEmpty", boolType),
            createUnary("isNotEmpty", boolType),
            createUnary("echo", unitType),
            createUnary("keys", setType).emit("$0.keys"),
            createUnary("values", setTypeOfDifferentGeneric).emit("$0.values"),
            createUnary("toMap", mapType, "Mutable map, elements will be shadow copied"),
            createUnary("toMutableMap", mutableMapType, "Mutable map, elements will be shadow copied"),
//            createUnary("m", mutableMapType, "Mutable map, elements will be shadow copied").renameUnary("toMutableMap")
            ),
        binaryMsgs = mutableMapOf(
            createBinary("+", mutableMapType, mutableMapType, "new map containing keys and values of both maps"),
            createBinary("-", valueType, mutableMapType, "new map containing all entries of the original map except the entry with the given key")
        ),
        keywordMsgs = mutableMapOf(
            createKeyword(
                "forEach",
                listOf(
                    KeywordArg(
                        "forEach",
                        Type.Lambda(
                            mutableListOf(
                                KeywordArg("key", keyType),
                                KeywordArg("value", valueType),
                            ),
                            unitType
                        )
                    )
                ),
                unitType
            ),

            createKeyword(
                "map",
                listOf(
                    KeywordArg(
                        "map",
                        Type.Lambda(
                            mutableListOf(
//                                KeywordArg("e", entryType),
                                KeywordArg("key", keyType),
                                KeywordArg("value", valueType),
                            ),
                            unitType,
                            specialFlagForLambdaWithDestruct = true
                        )
                    )
                ),
                unitType,

                ),
            createKeyword(
                "filter",
                listOf(
                    KeywordArg(
                        "filter",
                        Type.Lambda(
                            mutableListOf(
//                                KeywordArg("e", entryType),

                                KeywordArg("key", keyType),
                                KeywordArg("value", valueType),
                            ),
                            unitType,
                            specialFlagForLambdaWithDestruct = true
                        )
                    )
                ),
                unitType,
                ),



            createKeyword(KeywordArg("at", keyType), Type.NullableType(valueType))
                .rename("get"),


            createKeyword(KeywordArg("containsKey", keyType), boolType),
            createKeyword(KeywordArg("containsValue", valueType), boolType)
        ),

        )

    // this check is not needed since we are getting it from "forMutable" parameter
//    if (isMutable || true) {
        val unary = mutableMapOf(
            createUnary("clear", unitType, forMutable = true),
        )
        val mutKwMsgs = mutableMapOf(
            createKeyword(KeywordArg("remove", keyType), Type.NullableType(keyType), forMutable = true),
            createKeyword(KeywordArg("putAll", mutableMapType), unitType, forMutable = true),
            createKeyword(
                "atPut",
                listOf(
                    KeywordArg("at", keyType),
                    KeywordArg("put", valueType)
                ),
                unitType,
                forMutable = true
            ).rename("set"),
        )
        collectionProtocol.keywordMsgs.putAll(mutKwMsgs)
        collectionProtocol.unaryMsgs.putAll(unary)

//    }
    result[collectionProtocol.name] = collectionProtocol
    return result
}

fun createMapKeyword(
    genericTypeOfListElements: Type,
    differentGenericType: Type.UnknownGenericType,
    listTypeOfDifferentGeneric: Type.UserType
) = createKeyword(
    KeywordArg(
        "map",
        Type.Lambda(
            mutableListOf(KeywordArg("transform", genericTypeOfListElements)),
            differentGenericType
        ) // return list map of type of last expression
    ),
    listTypeOfDifferentGeneric
)

private fun createFilterKeyword(
    genericTypeOfSetElements: Type,
    boolType: Type.InternalType,
    returnType: Type,
    docComment: String? = null
) = createKeyword(
    KeywordArg(
        "filter",
        Type.Lambda(mutableListOf(KeywordArg("filter", genericTypeOfSetElements)), boolType)
    ),
    returnType,
    docComment
)

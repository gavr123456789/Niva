@file:Suppress("unused")

package main

import frontend.Lexer
import frontend.lex

import main.frontend.meta.CompilerError
import main.frontend.meta.Token
import main.frontend.meta.compileError
import main.frontend.meta.createFakeToken
import main.utils.*
import java.io.File
import kotlin.system.exitProcess

fun lex(source: String, file: File): MutableList<Token> {
    val lexer = Lexer(source, file)
    return lexer.lex()
}



const val fakeFileSourceGOOD = """
type Cond = [ -> Boolean] 
Cond whileLoop: block::[ -> Boolean] -> Unit = 
  this do => [
    block do => [this whileLoop: block]
  ] |=> Unit

mut x = 3
[x > 0] whileLoop: [
  x <- x dec
  x echo
  false
]



type Lexer 
  source: String
  line: Int
  start: Int
  current: Int
  spaces: Int
  linePos: Int
  lineCurrent: Int
  tokens: MutableList::Token

constructor Lexer source::String = Lexer 
  source: source
  line: 1
  start: 0
  current: 0
  spaces: 0
  linePos: 0
  lineCurrent: 0
  tokens: {}

Lexer done = current >= source count

Lexer peek = this peekDistance: 0 length: 1
Lexer peekDistance: distance::Int length::Int -> String = [
  b = StringBuilder new
  mut i = distance

  // IGNORED || current + i > source.lastIndex || current + i < 0
  [b count < length] whileLoop: [
    mut condition = true
    this done 
      => condition <- false
      |=> b append: (source at: current + i)
    
    i <- i inc
    condition
  ]

  ^ b toString
]

Lexer step = this step: 1
Lexer step: n::Int -> String = [
  b = StringBuilder new

  // IGNORED current > source.lastIndex
  [b count < n] whileLoop: [
    mut condition = true
    this done 
      => condition <- false
      |=> b append: (source at: current)
    
    current <- current inc
    condition
  ]
  
  linePos <- linePos + n
  ^ b toString
]


Lexer check: arg::String = this check: arg distance: 0
Lexer check: arg::String distance::Int -> Boolean = [
  this done => ^false
  ^(this peekDistance: distance length: arg count) == arg
]

Lexer match::String -> Boolean = [
  this check: match |> not => ^ false
  this step: match count
  ^ true
]


Lexer createTokenWithType: tokType::TokenType -> Token = [
  lexeme = source slice: start..<current

  end = current - 1

  tok = Token 
    kind: tokType 
    lexeme: lexeme
    line: line
    pos: (Position start: start end: end) 
    lineEnd: -1

  ^tok
]


Lexer incLine = this incLineWithNewToken: false
Lexer incLineWithNewToken: needNewToken::Boolean = [
  line <- line inc
  linePos <- 0
  // needNewToken => [
  //   start <- current
  // ]
]


String isDigit -> Boolean = [
  this forEach: [
    it isDigit not => ^false
  ]
  ^true
]
String isAlphaNumeric -> Boolean = [
  this forEach: [
    it isLetterOrDigit not => ^false
  ]
  ^true
]

Lexer stepWhileDigits = [
  [this peek isDigit && (this done not)] whileTrue: [
    this step
  ]
]
Lexer stepWhileAlphaNumeric = [
  [(this peek isAlphaNumeric || (this check: "_")) && (this done not)] whileTrue: [
    this step
  ]
]

Lexer parseNumberOrDotDot = [ 
  mut kind = TokenType.Integer
  .stepWhileDigits

  // .. binary operator
  (.check: "..<") || (.check: "..") => [
    this createTokenWithType: TokenType.Integer
    start <- start inc 
    .match: "..<"
    .match: ".."


  ]
]






Lexer parseIdentifier = [ ]

Lexer next = [
  
  this done => ^

  letter = this step
  // check for all complex variants like words and numbers first
  letter first isDigit => this parseNumberOrDotDot
  letter first isLetter => this parseIdentifier

  // spaces and stuff
  (this match: " ") || (this match: "\t") => [
    spaces <- spaces inc
    start <- start inc
    ^
  ]
  this match: "\n" => [
    this incLine
    ^
  ]
  
  // then match on symbols
  tokType = | letter 
  | "(" => TokenType.OpenParen
  | ")" => TokenType.CloseParen
  | "{" => TokenType.OpenBrace
  | "}" => TokenType.CloseBrace
  | "[" => TokenType.OpenBracket
  | "]" => TokenType.CloseBracket

  | "," => TokenType.Comma
  | ":" => TokenType.Colon
  | ";" => TokenType.Cascade
  
  //TODO NEGATIVE NUMS
  | "-" => // -(BinSym), -42(Number)
    TokenType.BinarySymbol 

  | "+", "/", "*" => TokenType.BinarySymbol

  | "." => // ., ..(BinSym), ..<(BinSym)
    this match: ".<" => TokenType.BinarySymbol |=>
    this match: "." => TokenType.BinarySymbol |=> TokenType.Dot
  | "|" => // |, |>, |=>
    this match: ">" => TokenType.Pipe |=>
    this match: "=>" => TokenType.Else |=> TokenType.If
  
  | "=" => // =, ==, =>
    this match: "=" => TokenType.BinarySymbol |=>
    this match: ">" => TokenType.Then |=> TokenType.Assign
  |=> TokenType.Unknown

  // create token with that type
  tokens add: (this createTokenWithType: tokType)
]

Lexer main  = [
  // while not done, scan next token with `next`
  [this done not] whileTrue: [
    this next
    start <- current
    lineCurrent <- linePos
  ]

]

"""

fun main(args: Array<String>) {

//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/experiments/main.niva")
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/Lexer/main.niva")
//    val args = arrayOf("run", "/home/gavr/Documents/Projects/bazar/Examples/parserCombinator/main.niva")
//    val args = arrayOf("--verbose","build", "/home/gavr/Documents/Projects/bazar/Examples/turtle/main.niva")

//    val qqq = "file:///home/gavr/Documents/Projects/bazar/Examples/Lexer/lexer.niva"
//    val qqq = "file:///home/gavr/Documents/Projects/bazar/Examples/JSON/main.niva"

//    try {
//        val ls = LS()
//        val resolver = ls.resolveAll(qqq)
//
//
//        ls.resolveAllWithChangedFile(
//            qqq,
//            fakeFileSourceGOOD
//        )
//
//        (1..200).forEach {
//            ls.onCompletion(qqq, 131, 20)
//        }
//        ls.onCompletion(qqq, 131, 20)
//
//    }
//    catch (e: OnCompletionException) {
//        println(e.scope)
//    }

    if (help(args)) return
    run(args)
}

// just `niva run` means default file is main.niva, `niva run file.niva` runs with this file as root
fun getPathToMainOrSingleFile(args: List<String>): String =
    if (args.count() >= 2) {
        // niva run/test/build "sas.niva"
        val fileNameArg = args[1]
        if (File(fileNameArg).exists()) {
            fileNameArg
        } else {
            createFakeToken().compileError("File $fileNameArg doesn't exist")
        }
    } else if (args.count() == 1 && args[0].contains(".")) {
        // Single arg "niva sas.niva"
        args[0]
    } else if (args.count() == 0) {
        File("examples/Main/main.niva").absolutePath
    }


    else {
        // niva run\test\build...
        val mainNiva = "main.niva"
        val mainScala = "main.scala"

        if (File(mainNiva).exists())
            mainNiva
        else if (File(mainScala).exists())
            mainScala
        else {
            println("Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`")
            exitProcess(-1)
//                createFakeToken().compileError("Can't find `main.niva` or `main.scala` please specify the file after run line `niva run file.niva`")
        }
    }

fun run(args2: Array<String>) {
    val args = args2.toMutableList()

//    readJar("/home/gavr/.gradle/caches/modules-2/files-2.1/io.github.jwharm.javagi/gtk/0.9.0/2caa1960a0bec1c8ed7127a6804693418441f166/gtk-0.9.0.jar")

    val startTime = System.currentTimeMillis()

    val am = ArgsManager(args)
    val mainArg = am.mainArg()
    val pm = PathManager(getPathToMainOrSingleFile(args), mainArg)

    if (mainArg == MainArgument.DAEMON) {
        daemon(pm, mainArg)
    }

    // resolve all files!
    val resolver = try {
        compileProjFromFile(pm, compileOnlyOneFile = mainArg == MainArgument.SINGLE_FILE_PATH, tests = mainArg == MainArgument.TEST, verbose = am.verbose)
    } catch (e: CompilerError) {
        if (!GlobalVariables.isLspMode)
            println(e.message)
        exitProcess(-1)
    }
    val secondTime = System.currentTimeMillis()
    am.time(secondTime - startTime, false)


    val inlineRepl = File("inline_repl.txt").absoluteFile

    val compiler = CompilerRunner(
        pm.pathToInfroProject,
        inlineRepl,
        resolver.compilationTarget,
        resolver.compilationMode,
        pm.mainNivaFileWhileDevFromIdea.nameWithoutExtension,
        resolver
    )


    val specialPkgToInfoPrint = getSpecialInfoArg(args, am.infoIndex)

    when (mainArg) {
        MainArgument.BUIlD -> compiler.runCommand(dist = true, buildFatJar = true)
        MainArgument.DISRT -> compiler.runCommand(dist = true)
        MainArgument.RUN ->
            compiler.runCommand()

        MainArgument.TEST -> {
            compiler.runCommand(runTests = true)
        }

            MainArgument.SINGLE_FILE_PATH -> {
            compiler.runCommand(dist = am.compileOnly)
        }

        MainArgument.INFO_ONLY ->
            compiler.infoPrint(false, specialPkgToInfoPrint)

        MainArgument.USER_DEFINED_INFO_ONLY ->
            compiler.infoPrint(true, specialPkgToInfoPrint)

        MainArgument.RUN_FROM_IDEA -> {
            compiler.runCommand(dist = false)
        }

        MainArgument.DAEMON -> {
            daemon(pm, mainArg)
        }

        MainArgument.LSP -> TODO()

    }

    am.time(System.currentTimeMillis() - secondTime, true)
}


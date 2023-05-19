package frontend.parser


fun Parser.EXPRESSION() {
    return ADDITION()
}

fun Parser.ADDITION() {
    var left = CALL()

    while (check("+") || check("-")) {

    }
}

fun Parser.CALL() {

}

fun Parser.MULTIPLICATION() {

}

fun Parser.EXPONENTIATION() {

}

fun Parser.BASIC() {

}


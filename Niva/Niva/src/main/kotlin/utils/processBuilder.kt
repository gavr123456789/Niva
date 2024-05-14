package main.utils

fun runProcess(command: String) {
    ProcessBuilder().command(command)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
        .redirectInput(ProcessBuilder.Redirect.INHERIT)
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start().waitFor()
}

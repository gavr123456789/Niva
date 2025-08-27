package main.utils

fun runProcess(command: String) {
    println("Running command: $command\n")
    ProcessBuilder().command(command)
        .redirectOutput(ProcessBuilder.Redirect.INHERIT)
//        .redirectInput(ProcessBuilder.Redirect.INHERIT)
//        .redirectInput(ProcessBuilder.Redirect.PIPE) // <--- stdin отключён
        .redirectError(ProcessBuilder.Redirect.INHERIT)
        .start()
        .waitFor()
}

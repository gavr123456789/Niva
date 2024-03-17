repositories {
    maven(url = "https://jitpack.io")
}


tasks.withType(JavaCompile::class.java) {
    options.compilerArgs = listOf("--enable-preview")
}

tasks.withType(JavaExec::class.java) {
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=/usr/lib64:/lib64:/lib:/usr/lib:/lib/x86_64-linux-gnu"
    )
}

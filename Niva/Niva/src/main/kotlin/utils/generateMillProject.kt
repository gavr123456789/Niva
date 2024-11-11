package main.utils

import java.io.File


fun generateMillInFolder(path: String) {
    val rootDir = File(path)

    val nivaDir = File(rootDir, "niva")
    val srcDir = File(nivaDir, "src")
    val testDir = File(nivaDir, "test/src/foo")

    // Создаем все необходимые каталоги
    srcDir.mkdirs()
    testDir.mkdirs()

    // Создание и запись в файл build.mill
    val buildFile = File(rootDir, "build.mill")
    buildFile.writeText(MILL_BUILD)

    // Создание и запись в файл mill
    val millFile = File(rootDir, "mill")
    millFile.writeText(MILL_SH)
    millFile.setExecutable(true)  // Даем права на исполнение для shell-скрипта

    // Создание пустых файлов Main.kt и FooTest.kt
    val mainKtFile = File(srcDir, "Main.kt")
    mainKtFile.writeText("")  // Оставляем пустым

    val fooTestKtFile = File(testDir, "FooTest.kt")
    fooTestKtFile.writeText("")  // Оставляем пустым

    println("Структура файлов успешно создана в каталоге: $path")
}


const val MILL_VERSION = "0.12.1"

const val MILL_BUILD = """
//// SNIPPET:BUILD
package build
import mill._, kotlinlib._

object niva extends KotlinModule {

  def kotlinVersion = "2.0.21"

  def mainClass = Some("MainKt")

  def ivyDeps = Agg(
    ivy"com.github.ajalt.clikt:clikt-jvm:4.4.0",
    ivy"org.jetbrains.kotlinx:kotlinx-html-jvm:0.11.0"
  )

  object test extends KotlinTests with TestModule.Junit5 {
    def ivyDeps = super.ivyDeps() ++ Agg(
      ivy"io.kotest:kotest-runner-junit5-jvm:5.9.1"
    )
  }
}
"""

const val MILL_SH = "#!/usr/bin/env sh\n" +
        "\n" +
        "# This is a wrapper script, that automatically download mill from GitHub release pages\n" +
        "# You can give the required mill version with MILL_VERSION env variable\n" +
        "# If no version is given, it falls back to the value of DEFAULT_MILL_VERSION\n" +
        "\n" +
        "set -e\n" +
        "\n" +
        "if [ -z \"\${DEFAULT_MILL_VERSION}\" ] ; then\n" +
        "  DEFAULT_MILL_VERSION=0.12.1\n" +
        "fi\n" +
        "\n" +
        "if [ -z \"\$MILL_VERSION\" ] ; then\n" +
        "  if [ -f \".mill-version\" ] ; then\n" +
        "    MILL_VERSION=\"\\$(head -n 1 .mill-version 2> /dev/null)\"\n" +
        "  elif [ -f \".config/mill-version\" ] ; then\n" +
        "    MILL_VERSION=\"\\$(head -n 1 .config/mill-version 2> /dev/null)\"\n" +
        "  elif [ -f \"mill\" ] && [ \"\\$0\" != \"mill\" ] ; then\n" +
        "    MILL_VERSION=$(grep -F \"DEFAULT_MILL_VERSION=\" \"mill\" | head -n 1 | cut -d= -f2)\n" +
        "  else\n" +
        "    MILL_VERSION=\$DEFAULT_MILL_VERSION\n" +
        "  fi\n" +
        "fi\n" +
        "\n" +
        "if [ \"x\${XDG_CACHE_HOME}\" != \"x\" ] ; then\n" +
        "  MILL_DOWNLOAD_PATH=\"\${XDG_CACHE_HOME}/mill/download\"\n" +
        "else\n" +
        "  MILL_DOWNLOAD_PATH=\"\${HOME}/.cache/mill/download\"\n" +
        "fi\n" +
        "MILL_EXEC_PATH=\"\${MILL_DOWNLOAD_PATH}/\${MILL_VERSION}\"\n" +
        "\n" +
        "version_remainder=\"\$MILL_VERSION\"\n" +
        "MILL_MAJOR_VERSION=\"\${version_remainder % %.*}\"; version_remainder=\"\${version_remainder#*.}\"\n" +
        "MILL_MINOR_VERSION=\"\${version_remainder % %.*}\"; version_remainder=\"\${version_remainder#*.}\"\n" +
        "\n" +
        "if [ ! -s \"\$MILL_EXEC_PATH\" ] ; then\n" +
        "  mkdir -p \"\$MILL_DOWNLOAD_PATH\"\n" +
        "  if [ \"\$MILL_MAJOR_VERSION\" -gt 0 ] || [ \"\$MILL_MINOR_VERSION\" -ge 5 ] ; then\n" +
        "    ASSEMBLY=\"-assembly\"\n" +
        "  fi\n" +
        "  DOWNLOAD_FILE=\$MILL_EXEC_PATH-tmp-download\n" +
        "  MILL_VERSION_TAG=\\$(echo \$MILL_VERSION | sed -E 's/([^-]+)(-M[0-9]+)?(-.*)?/\\1\\2/')\n" +
        "  MILL_DOWNLOAD_URL=\"https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/\$MILL_VERSION/mill-dist-\$MILL_VERSION.jar\"\n" +
        "  curl --fail -L -o \"\$DOWNLOAD_FILE\" \"\$MILL_DOWNLOAD_URL\"\n" +
        "  chmod +x \"\$DOWNLOAD_FILE\"\n" +
        "  mv \"\$DOWNLOAD_FILE\" \"\$MILL_EXEC_PATH\"\n" +
        "  unset DOWNLOAD_FILE\n" +
        "  unset MILL_DOWNLOAD_URL\n" +
        "fi\n" +
        "\n" +
        "if [ -z \"\$MILL_MAIN_CLI\" ] ; then\n" +
        "  MILL_MAIN_CLI=\"\${0}\"\n" +
        "fi\n" +
        "\n" +
        "MILL_FIRST_ARG=\"\"\n" +
        "\n" +
        " # first arg is a long flag for \"--interactive\" or starts with \"-i\"\n" +
        "if [ \"\\$1\" = \"--bsp\" ] || [ \"\${1#\"-i\"}\" != \"\\$1\" ] || [ \"\\$1\" = \"--interactive\" ] || [ \"\\$1\" = \"--no-server\" ] || [ \"\\$1\" = \"--repl\" ] || [ \"\\$1\" = \"--help\" ] ; then\n" +
        "  # Need to preserve the first position of those listed options\n" +
        "  MILL_FIRST_ARG=\\$1\n" +
        "  shift\n" +
        "fi\n" +
        "\n" +
        "unset MILL_DOWNLOAD_PATH\n" +
        "unset MILL_VERSION\n" +
        "\n" +
        "exec \$MILL_EXEC_PATH \$MILL_FIRST_ARG -D \"mill.main.cli=\${MILL_MAIN_CLI}\" \"\\$@\"\n"
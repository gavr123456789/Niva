package main.utils

import java.io.File


// writes to build.mill file with deps changed
//fun regenerateMill(pathToMillBuildFile: String, deps: List<String>) {
//
//}


fun generateMillProjectTemplateIfNotExist(pathToRootOfMain: String) {
    val rootDir = File(pathToRootOfMain)

    if (rootDir.exists())
        return

    rootDir.mkdirs()


    val nivaDir = File(rootDir, "niva")
    val srcDir = File(nivaDir, "src")
    val testDir = File(nivaDir, "test/src/foo")

    // Создаем все необходимые каталоги
    srcDir.mkdirs()
    testDir.mkdirs()

    // Создание и запись в файл build.mill
    val buildFile = File(rootDir, "build.mill")
//    buildFile.writeText(MILL_BUILD)
    buildFile.writeText("") // we will regenerate it anyway

    // Создание и запись в файл mill
    val millFile = File(rootDir, "mill")
    if (getOSType() == CurrentOS.WINDOWS)
        millFile.writeText(MILL_BAT)
    else
        millFile.writeText(MILL_SH)

    millFile.setExecutable(true)  // Даем права на исполнение для shell-скрипта

    // Создание пустых файлов Main.kt и FooTest.kt
//    val mainKtFile = File(srcDir, "Main.kt")
//    mainKtFile.writeText("")  // Оставляем пустым

    val fooTestKtFile = File(testDir, "FooTest.kt")
    fooTestKtFile.writeText("")  // Оставляем пустым

    println("The file structure has been successfully created: ${rootDir.canonicalPath}, do not forget to add it to git ignore")
}


//const val MILL_VERSION = "0.12.1"

const val MILL_BUILD = """
package build
import mill._, kotlinlib._

object niva extends KotlinModule {

  def kotlinVersion = "2.0.21"

  def mainClass = Some("mainNiva.MainKt")

  
  //%IMPL%
  
  def kotlincOptions = super.kotlincOptions() ++ Seq("-nowarn")
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
        "    MILL_VERSION=\"$(head -n 1 .mill-version 2> /dev/null)\"\n" +
        "  elif [ -f \".config/mill-version\" ] ; then\n" +
        "    MILL_VERSION=\"$(head -n 1 .config/mill-version 2> /dev/null)\"\n" +
        "  elif [ -f \"mill\" ] && [ \"$0\" != \"mill\" ] ; then\n" +
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
        "MILL_MAJOR_VERSION=\"\${version_remainder%%.*}\"; version_remainder=\"\${version_remainder#*.}\"\n" +
        "MILL_MINOR_VERSION=\"\${version_remainder%%.*}\"; version_remainder=\"\${version_remainder#*.}\"\n" +
        "\n" +
        "if [ ! -s \"\$MILL_EXEC_PATH\" ] ; then\n" +
        "  mkdir -p \"\$MILL_DOWNLOAD_PATH\"\n" +
        "  if [ \"\$MILL_MAJOR_VERSION\" -gt 0 ] || [ \"\$MILL_MINOR_VERSION\" -ge 5 ] ; then\n" +
        "    ASSEMBLY=\"-assembly\"\n" +
        "  fi\n" +
        "  DOWNLOAD_FILE=\$MILL_EXEC_PATH-tmp-download\n" +
        "  MILL_VERSION_TAG=$(echo \$MILL_VERSION | sed -E 's/([^-]+)(-M[0-9]+)?(-.*)?/\\1\\2/')\n" +
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
        "if [ \"$1\" = \"--bsp\" ] || [ \"\${1#\"-i\"}\" != \"$1\" ] || [ \"$1\" = \"--interactive\" ] || [ \"$1\" = \"--no-server\" ] || [ \"$1\" = \"--repl\" ] || [ \"$1\" = \"--help\" ] ; then\n" +
        "  # Need to preserve the first position of those listed options\n" +
        "  MILL_FIRST_ARG=$1\n" +
        "  shift\n" +
        "fi\n" +
        "\n" +
        "unset MILL_DOWNLOAD_PATH\n" +
        "unset MILL_VERSION\n" +
        "\n" +
        "exec \$MILL_EXEC_PATH \$MILL_FIRST_ARG -D \"mill.main.cli=\${MILL_MAIN_CLI}\" \"$@\"\n"


const val MILL_BAT = """
@echo off

rem This is a wrapper script, that automatically download mill from GitHub release pages
rem You can give the required mill version with --mill-version parameter
rem If no version is given, it falls back to the value of DEFAULT_MILL_VERSION
rem
rem Project page: https://github.com/lefou/millw
rem Script Version: 0.4.12
rem
rem If you want to improve this script, please also contribute your changes back!
rem
rem Licensed under the Apache License, Version 2.0

rem setlocal seems to be unavailable on Windows 95/98/ME
rem but I don't think we need to support them in 2019
setlocal enabledelayedexpansion

if [!DEFAULT_MILL_VERSION!]==[] (
    set "DEFAULT_MILL_VERSION=0.11.4"
)

if [!GITHUB_RELEASE_CDN!]==[] (
    set "GITHUB_RELEASE_CDN="
)

if [!MILL_MAIN_CLI!]==[] (
    set "MILL_MAIN_CLI=%~f0"
)

set "MILL_REPO_URL=https://github.com/com-lihaoyi/mill"

rem %~1% removes surrounding quotes
if [%~1%]==[--mill-version] (
  if not [%~2%]==[] (
    set MILL_VERSION=%~2%
    rem shift command doesn't work within parentheses
    set "STRIP_VERSION_PARAMS=true"
  ) else (
    echo You specified --mill-version without a version. 1>&2
    echo Please provide a version that matches one provided on 1>&2
    echo %MILL_REPO_URL%/releases 1>&2
    exit /b 1
  )
)

if not defined STRIP_VERSION_PARAMS GOTO AfterStripVersionParams
rem strip the: --mill-version {version}
shift
shift
:AfterStripVersionParams

if [!MILL_VERSION!]==[] (
  if exist .mill-version (
      set /p MILL_VERSION=<.mill-version
  ) else (
    if exist .config\mill-version (
      set /p MILL_VERSION=<.config\mill-version
    )
  )
)

if [!MILL_VERSION!]==[] (
    set MILL_VERSION=%DEFAULT_MILL_VERSION%
)

if [!MILL_DOWNLOAD_PATH!]==[] (
    set MILL_DOWNLOAD_PATH=%USERPROFILE%\.mill\download
)

rem without bat file extension, cmd doesn't seem to be able to run it
set MILL=%MILL_DOWNLOAD_PATH%\!MILL_VERSION!.bat

if not exist "%MILL%" (
    set VERSION_PREFIX=%MILL_VERSION:~0,4%
    rem Since 0.5.0
    set DOWNLOAD_SUFFIX=-assembly
    rem Since 0.11.0
    set DOWNLOAD_FROM_MAVEN=1
    if [!VERSION_PREFIX!]==[0.0.] (
        set DOWNLOAD_SUFFIX=
        set DOWNLOAD_FROM_MAVEN=0
    )
    if [!VERSION_PREFIX!]==[0.1.] (
        set DOWNLOAD_SUFFIX=
        set DOWNLOAD_FROM_MAVEN=0
    )
    if [!VERSION_PREFIX!]==[0.2.] (
        set DOWNLOAD_SUFFIX=
        set DOWNLOAD_FROM_MAVEN=0
    )
    if [!VERSION_PREFIX!]==[0.3.] (
        set DOWNLOAD_SUFFIX=
        set DOWNLOAD_FROM_MAVEN=0
   )
    if [!VERSION_PREFIX!]==[0.4.] (
        set DOWNLOAD_SUFFIX=
        set DOWNLOAD_FROM_MAVEN=0
    )
    if [!VERSION_PREFIX!]==[0.5.] (
        set DOWNLOAD_FROM_MAVEN=0
    )
    if [!VERSION_PREFIX!]==[0.6.] (
        set DOWNLOAD_FROM_MAVEN=0
    )
    if [!VERSION_PREFIX!]==[0.7.] (
        set DOWNLOAD_FROM_MAVEN=0
    )
    if [!VERSION_PREFIX!]==[0.8.] (
        set DOWNLOAD_FROM_MAVEN=0
    )
    if [!VERSION_PREFIX!]==[0.9.] (
        set DOWNLOAD_FROM_MAVEN=0
    )
    set VERSION_PREFIX=%MILL_VERSION:~0,5%
    if [!VERSION_PREFIX!]==[0.10.] (
        set DOWNLOAD_FROM_MAVEN=0
    )
    set VERSION_PREFIX=%MILL_VERSION:~0,8%
    if [!VERSION_PREFIX!]==[0.11.0-M] (
        set DOWNLOAD_FROM_MAVEN=0
    )
    set VERSION_PREFIX=

    for /F "delims=- tokens=1" %%A in ("!MILL_VERSION!") do set MILL_VERSION_BASE=%%A
    for /F "delims=- tokens=2" %%A in ("!MILL_VERSION!") do set MILL_VERSION_MILESTONE=%%A
	set VERSION_MILESTONE_START=!MILL_VERSION_MILESTONE:~0,1!
    if [!VERSION_MILESTONE_START!]==[M] (
        set MILL_VERSION_TAG="!MILL_VERSION_BASE!-!MILL_VERSION_MILESTONE!"
    ) else (
        set MILL_VERSION_TAG=!MILL_VERSION_BASE!
    )

    rem there seems to be no way to generate a unique temporary file path (on native Windows)
    set DOWNLOAD_FILE=%MILL%.tmp

    if [!DOWNLOAD_FROM_MAVEN!]==[1] (
        set DOWNLOAD_URL=https://repo1.maven.org/maven2/com/lihaoyi/mill-dist/!MILL_VERSION!/mill-dist-!MILL_VERSION!.jar
    ) else (
        set DOWNLOAD_URL=!GITHUB_RELEASE_CDN!%MILL_REPO_URL%/releases/download/!MILL_VERSION_TAG!/!MILL_VERSION!!DOWNLOAD_SUFFIX!
    )

    echo Downloading mill %MILL_VERSION% from !DOWNLOAD_URL! ... 1>&2

    if not exist "%MILL_DOWNLOAD_PATH%" mkdir "%MILL_DOWNLOAD_PATH%"
    rem curl is bundled with recent Windows 10
    rem but I don't think we can expect all the users to have it in 2019
    where /Q curl
    if %ERRORLEVEL% EQU 0 (
        curl -f -L "!DOWNLOAD_URL!" -o "!DOWNLOAD_FILE!"
    ) else (
        rem bitsadmin seems to be available on Windows 7
        rem without /dynamic, github returns 403
        rem bitsadmin is sometimes needlessly slow but it looks better with /priority foreground
        bitsadmin /transfer millDownloadJob /dynamic /priority foreground "!DOWNLOAD_URL!" "!DOWNLOAD_FILE!"
    )
    if not exist "!DOWNLOAD_FILE!" (
        echo Could not download mill %MILL_VERSION% 1>&2
        exit /b 1
    )

    move /y "!DOWNLOAD_FILE!" "%MILL%"

    set DOWNLOAD_FILE=
    set DOWNLOAD_SUFFIX=
)

set MILL_DOWNLOAD_PATH=
set MILL_VERSION=
set MILL_REPO_URL=

rem Need to preserve the first position of those listed options
set MILL_FIRST_ARG=
if [%~1%]==[--bsp] (
  set MILL_FIRST_ARG=%1%
) else (
  if [%~1%]==[-i] (
    set MILL_FIRST_ARG=%1%
  ) else (
    if [%~1%]==[--interactive] (
      set MILL_FIRST_ARG=%1%
    ) else (
      if [%~1%]==[--no-server] (
        set MILL_FIRST_ARG=%1%
      ) else (
        if [%~1%]==[--repl] (
          set MILL_FIRST_ARG=%1%
        ) else (
          if [%~1%]==[--help] (
            set MILL_FIRST_ARG=%1%
          )
        )
      )
    )
  )
)

set "MILL_PARAMS=%*%"

if not [!MILL_FIRST_ARG!]==[] (
  if defined STRIP_VERSION_PARAMS (
    for /f "tokens=1-3*" %%a in ("%*") do (
        set "MILL_PARAMS=%%d"
    )
  ) else (
    for /f "tokens=1*" %%a in ("%*") do (
      set "MILL_PARAMS=%%b"
    )
  )
) else (
  if defined STRIP_VERSION_PARAMS (
    for /f "tokens=1-2*" %%a in ("%*") do (
        rem strip %%a - It's the "--mill-version" option.
        rem strip %%b - it's the version number that comes after the option.
        rem keep  %%c - It's the remaining options.
        set "MILL_PARAMS=%%c"
    )
  )
)

"%MILL%" %MILL_FIRST_ARG% -D "mill.main.cli=%MILL_MAIN_CLI%" %MILL_PARAMS%
"""
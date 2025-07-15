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

    srcDir.mkdirs()
    testDir.mkdirs()

    val buildFile = File(rootDir, "build.mill")

    buildFile.writeText("") // we will regenerate it anyway

    val millFile: File
    if (getOSType() == CurrentOS.WINDOWS) {
        millFile = File(rootDir, "mill.bat")
        millFile.writeText(MILL_BAT)
    }
    else {
        millFile = File(rootDir, "mill")
        millFile.writeText(MILL_SH)
    }
    millFile.setExecutable(true)

    val fooTestKtFile = File(testDir, "FooTest.kt")
    fooTestKtFile.writeText("")  // Оставляем пустым

    println("The file structure has been successfully created: ${rootDir.canonicalPath}, do not forget to add it to git ignore")
}


const val MILL_BUILD = $$"""//| mill-version: 1.0.0
    
package build
import mill.*, kotlinlib.*

object niva extends KotlinModule {
  def kotlinVersion = "2.2.0"

  def mainClass = Some("mainNiva.MainKt")
  
  //%IMPL%
  
  def processors = Task {
    defaultResolver().classpath(
      Seq(
        mvn"org.jetbrains.kotlin:kotlin-serialization-compiler-plugin:2.2.0"
      )
    )
  }

  def kotlincOptions = super.kotlincOptions() ++ Seq(
    s"-Xplugin=${processors().head.path}",
    "-nowarn"
  )  
  
  object test extends KotlinTests, TestModule.Junit5 {
    def mvnDeps = Seq(
      mvn"io.kotest:kotest-runner-junit5:5.9.1"
    )
  }
}
"""

const val MILL_SH = $$$$$$"""
#!/usr/bin/env sh

# This is a wrapper script, that automatically selects or downloads Mill from Maven Central or GitHub release pages.
#
# This script determines the Mill version to use by trying these sources
#   - env-variable `MILL_VERSION`
#   - local file `.mill-version`
#   - local file `.config/mill-version`
#   - `mill-version` from YAML fronmatter of current buildfile
#   - if accessible, find the latest stable version available on Maven Central (https://repo1.maven.org/maven2)
#   - env-variable `DEFAULT_MILL_VERSION`
#
# If a version has the suffix '-native' a native binary will be used.
# If a version has the suffix '-jvm' an executable jar file will be used, requiring an already installed Java runtime.
# If no such suffix is found, the script will pick a default based on version and platform.
#
# Once a version was determined, it tries to use either
#    - a system-installed mill, if found and it's version matches
#    - an already downloaded version under ~/.cache/mill/download
#
# If no working mill version was found on the system,
# this script downloads a binary file from Maven Central or Github Pages (this is version dependent)
# into a cache location (~/.cache/mill/download).
#
# Mill Project URL: https://github.com/com-lihaoyi/mill
# Script Version: 1.0.0-M1-21-7b6fae-DIRTY892b63e8
#
# If you want to improve this script, please also contribute your changes back!
# This script was generated from: dist/scripts/src/mill.sh
#
# Licensed under the Apache License, Version 2.0

set -e

if [ "$1" = "--setup-completions" ] ; then
  # Need to preserve the first position of those listed options
  MILL_FIRST_ARG=$1
  shift
fi

if [ -z "${DEFAULT_MILL_VERSION}" ] ; then
  DEFAULT_MILL_VERSION="0.12.10"
fi


if [ -z "${GITHUB_RELEASE_CDN}" ] ; then
  GITHUB_RELEASE_CDN=""
fi


MILL_REPO_URL="https://github.com/com-lihaoyi/mill"

if [ -z "${CURL_CMD}" ] ; then
  CURL_CMD=curl
fi

# Explicit commandline argument takes precedence over all other methods
if [ "$1" = "--mill-version" ] ; then
    echo "The --mill-version option is no longer supported." 1>&2
fi

MILL_BUILD_SCRIPT=""

if [ -f "build.mill" ] ; then
  MILL_BUILD_SCRIPT="build.mill"
elif [ -f "build.mill.scala" ] ; then
  MILL_BUILD_SCRIPT="build.mill.scala"
elif [ -f "build.sc" ] ; then
  MILL_BUILD_SCRIPT="build.sc"
fi

# Please note, that if a MILL_VERSION is already set in the environment,
# We reuse it's value and skip searching for a value.

# If not already set, read .mill-version file
if [ -z "${MILL_VERSION}" ] ; then
  if [ -f ".mill-version" ] ; then
    MILL_VERSION="$(tr '\r' '\n' < .mill-version | head -n 1 2> /dev/null)"
  elif [ -f ".config/mill-version" ] ; then
    MILL_VERSION="$(tr '\r' '\n' < .config/mill-version | head -n 1 2> /dev/null)"
  elif [ -n "${MILL_BUILD_SCRIPT}" ] ; then
    MILL_VERSION="$(cat ${MILL_BUILD_SCRIPT} | grep '//[|]  *mill-version:  *' | sed 's;//|  *mill-version:  *;;')"
  fi
fi

MILL_USER_CACHE_DIR="${XDG_CACHE_HOME:-${HOME}/.cache}/mill"

if [ -z "${MILL_DOWNLOAD_PATH}" ] ; then
  MILL_DOWNLOAD_PATH="${MILL_USER_CACHE_DIR}/download"
fi

# If not already set, try to fetch newest from Github
if [ -z "${MILL_VERSION}" ] ; then
  # TODO: try to load latest version from release page
  echo "No mill version specified." 1>&2
  echo "You should provide a version via a '//| mill-version: ' comment or a '.mill-version' file." 1>&2

  mkdir -p "${MILL_DOWNLOAD_PATH}"
  LANG=C touch -d '1 hour ago' "${MILL_DOWNLOAD_PATH}/.expire_latest" 2>/dev/null || (
    # we might be on OSX or BSD which don't have -d option for touch
    # but probably a -A [-][[hh]mm]SS
    touch "${MILL_DOWNLOAD_PATH}/.expire_latest"; touch -A -010000 "${MILL_DOWNLOAD_PATH}/.expire_latest"
  ) || (
    # in case we still failed, we retry the first touch command with the intention
    # to show the (previously suppressed) error message
    LANG=C touch -d '1 hour ago' "${MILL_DOWNLOAD_PATH}/.expire_latest"
  )

  # POSIX shell variant of bash's -nt operator, see https://unix.stackexchange.com/a/449744/6993
  # if [ "${MILL_DOWNLOAD_PATH}/.latest" -nt "${MILL_DOWNLOAD_PATH}/.expire_latest" ] ; then
  if [ -n "$(find -L "${MILL_DOWNLOAD_PATH}/.latest" -prune -newer "${MILL_DOWNLOAD_PATH}/.expire_latest")" ]; then
    # we know a current latest version
    MILL_VERSION=$(head -n 1 "${MILL_DOWNLOAD_PATH}"/.latest 2> /dev/null)
  fi

  if [ -z "${MILL_VERSION}" ] ; then
    # we don't know a current latest version
    echo "Retrieving latest mill version ..." 1>&2
    LANG=C ${CURL_CMD} -s -i -f -I ${MILL_REPO_URL}/releases/latest 2> /dev/null  | grep --ignore-case Location: | sed s'/^.*tag\///' | tr -d '\r\n' > "${MILL_DOWNLOAD_PATH}/.latest"
    MILL_VERSION=$(head -n 1 "${MILL_DOWNLOAD_PATH}"/.latest 2> /dev/null)
  fi

  if [ -z "${MILL_VERSION}" ] ; then
    # Last resort
    MILL_VERSION="${DEFAULT_MILL_VERSION}"
    echo "Falling back to hardcoded mill version ${MILL_VERSION}" 1>&2
  else
    echo "Using mill version ${MILL_VERSION}" 1>&2
  fi
fi

MILL_NATIVE_SUFFIX="-native"
MILL_JVM_SUFFIX="-jvm"
FULL_MILL_VERSION=$MILL_VERSION
ARTIFACT_SUFFIX=""
set_artifact_suffix(){
  if [ "$(expr substr $(uname -s) 1 5 2>/dev/null)" = "Linux" ]; then
    if [ "$(uname -m)" = "aarch64" ]; then
      ARTIFACT_SUFFIX="-native-linux-aarch64"
    else
      ARTIFACT_SUFFIX="-native-linux-amd64"
    fi
  elif [ "$(uname)" = "Darwin" ]; then
    if [ "$(uname -m)" = "arm64" ]; then
      ARTIFACT_SUFFIX="-native-mac-aarch64"
    else
      ARTIFACT_SUFFIX="-native-mac-amd64"
    fi
  else
     echo "This native mill launcher supports only Linux and macOS." 1>&2
     exit 1
  fi
}

case "$MILL_VERSION" in
    *"$MILL_NATIVE_SUFFIX")
  MILL_VERSION=${MILL_VERSION%"$MILL_NATIVE_SUFFIX"}
  set_artifact_suffix
  ;;

    *"$MILL_JVM_SUFFIX")
    MILL_VERSION=${MILL_VERSION%"$MILL_JVM_SUFFIX"}
  ;;

    *)
  case "$MILL_VERSION" in
    0.1.*) ;;
    0.2.*) ;;
    0.3.*) ;;
    0.4.*) ;;
    0.5.*) ;;
    0.6.*) ;;
    0.7.*) ;;
    0.8.*) ;;
    0.9.*) ;;
    0.10.*) ;;
    0.11.*) ;;
    0.12.*) ;;
    *)
      set_artifact_suffix
  esac
  ;;
esac

MILL="${MILL_DOWNLOAD_PATH}/$MILL_VERSION$ARTIFACT_SUFFIX"

try_to_use_system_mill() {
  if [ "$(uname)" != "Linux" ]; then
    return 0
  fi

  MILL_IN_PATH="$(command -v mill || true)"

  if [ -z "${MILL_IN_PATH}" ]; then
    return 0
  fi

  SYSTEM_MILL_FIRST_TWO_BYTES=$(head --bytes=2 "${MILL_IN_PATH}")
  if [ "${SYSTEM_MILL_FIRST_TWO_BYTES}" = "#!" ]; then
	  # MILL_IN_PATH is (very likely) a shell script and not the mill
	  # executable, ignore it.
	  return 0
  fi

  SYSTEM_MILL_PATH=$(readlink -e "${MILL_IN_PATH}")
  SYSTEM_MILL_SIZE=$(stat --format=%s "${SYSTEM_MILL_PATH}")
  SYSTEM_MILL_MTIME=$(stat --format=%y "${SYSTEM_MILL_PATH}")

  if [ ! -d "${MILL_USER_CACHE_DIR}" ]; then
    mkdir -p "${MILL_USER_CACHE_DIR}"
  fi

  SYSTEM_MILL_INFO_FILE="${MILL_USER_CACHE_DIR}/system-mill-info"
  if [ -f "${SYSTEM_MILL_INFO_FILE}" ]; then
    parseSystemMillInfo() {
        LINE_NUMBER="${1}"
        # Select the line number of the SYSTEM_MILL_INFO_FILE, cut the
        # variable definition in that line in two halves and return
        # the value, and finally remove the quotes.
        sed -n "${LINE_NUMBER}p" "${SYSTEM_MILL_INFO_FILE}" |\
            cut -d= -f2 |\
            sed 's/"\(.*\)"/\1/'
    }

    CACHED_SYSTEM_MILL_PATH=$(parseSystemMillInfo 1)
    CACHED_SYSTEM_MILL_VERSION=$(parseSystemMillInfo 2)
    CACHED_SYSTEM_MILL_SIZE=$(parseSystemMillInfo 3)
    CACHED_SYSTEM_MILL_MTIME=$(parseSystemMillInfo 4)

    if [ "${SYSTEM_MILL_PATH}" = "${CACHED_SYSTEM_MILL_PATH}" ] \
           && [ "${SYSTEM_MILL_SIZE}" = "${CACHED_SYSTEM_MILL_SIZE}" ] \
           && [ "${SYSTEM_MILL_MTIME}" = "${CACHED_SYSTEM_MILL_MTIME}" ]; then
      if [ "${CACHED_SYSTEM_MILL_VERSION}" = "${MILL_VERSION}" ]; then
          MILL="${SYSTEM_MILL_PATH}"
          return 0
      else
          return 0
      fi
    fi
  fi

  SYSTEM_MILL_VERSION=$(${SYSTEM_MILL_PATH} --version | head -n1 | sed -n 's/^Mill.*version \(.*\)/\1/p')

  cat <<EOF > "${SYSTEM_MILL_INFO_FILE}"
CACHED_SYSTEM_MILL_PATH="${SYSTEM_MILL_PATH}"
CACHED_SYSTEM_MILL_VERSION="${SYSTEM_MILL_VERSION}"
CACHED_SYSTEM_MILL_SIZE="${SYSTEM_MILL_SIZE}"
CACHED_SYSTEM_MILL_MTIME="${SYSTEM_MILL_MTIME}"
EOF

  if [ "${SYSTEM_MILL_VERSION}" = "${MILL_VERSION}" ]; then
    MILL="${SYSTEM_MILL_PATH}"
  fi
}
try_to_use_system_mill

# If not already downloaded, download it
if [ ! -s "${MILL}" ] || [ "$MILL_TEST_DRY_RUN_LAUNCHER_SCRIPT" = "1" ] ; then
  case $MILL_VERSION in
    0.0.* | 0.1.* | 0.2.* | 0.3.* | 0.4.* )
      DOWNLOAD_SUFFIX=""
      DOWNLOAD_FROM_MAVEN=0
      ;;
    0.5.* | 0.6.* | 0.7.* | 0.8.* | 0.9.* | 0.10.* | 0.11.0-M* )
      DOWNLOAD_SUFFIX="-assembly"
      DOWNLOAD_FROM_MAVEN=0
      ;;
    *)
      DOWNLOAD_SUFFIX="-assembly"
      DOWNLOAD_FROM_MAVEN=1
      ;;
  esac
  case $MILL_VERSION in
    0.12.0 | 0.12.1 | 0.12.2 | 0.12.3 | 0.12.4 | 0.12.5 | 0.12.6 | 0.12.7 | 0.12.8 | 0.12.9 | 0.12.10 | 0.12.11 )
      DOWNLOAD_EXT="jar"
      ;;
    0.12.* )
      DOWNLOAD_EXT="exe"
      ;;
    0.* )
      DOWNLOAD_EXT="jar"
      ;;
    *)
      DOWNLOAD_EXT="exe"
      ;;
  esac

  DOWNLOAD_FILE=$(mktemp mill.XXXXXX)
  if [ "$DOWNLOAD_FROM_MAVEN" = "1" ] ; then
    DOWNLOAD_URL="https://repo1.maven.org/maven2/com/lihaoyi/mill-dist${ARTIFACT_SUFFIX}/${MILL_VERSION}/mill-dist${ARTIFACT_SUFFIX}-${MILL_VERSION}.${DOWNLOAD_EXT}"
  else
    MILL_VERSION_TAG=$(echo "$MILL_VERSION" | sed -E 's/([^-]+)(-M[0-9]+)?(-.*)?/\1\2/')
    DOWNLOAD_URL="${GITHUB_RELEASE_CDN}${MILL_REPO_URL}/releases/download/${MILL_VERSION_TAG}/${MILL_VERSION}${DOWNLOAD_SUFFIX}"
    unset MILL_VERSION_TAG
  fi

  if [ "$MILL_TEST_DRY_RUN_LAUNCHER_SCRIPT" = "1" ] ; then
    echo $DOWNLOAD_URL
    echo $MILL
    exit 0
  fi
  # TODO: handle command not found
  echo "Downloading mill ${MILL_VERSION} from ${DOWNLOAD_URL} ..." 1>&2
  ${CURL_CMD} -f -L -o "${DOWNLOAD_FILE}" "${DOWNLOAD_URL}"
  chmod +x "${DOWNLOAD_FILE}"
  mkdir -p "${MILL_DOWNLOAD_PATH}"
  mv "${DOWNLOAD_FILE}" "${MILL}"

  unset DOWNLOAD_FILE
  unset DOWNLOAD_SUFFIX
fi

if [ -z "$MILL_MAIN_CLI" ] ; then
  MILL_MAIN_CLI="${0}"
fi

MILL_FIRST_ARG=""
if [ "$1" = "--bsp" ] || [ "${1#"-i"}" != "$1" ] || [ "$1" = "--interactive" ] || [ "$1" = "--no-server" ] || [ "$1" = "--no-daemon" ] || [ "$1" = "--repl" ] || [ "$1" = "--help" ] ; then
  # Need to preserve the first position of those listed options
  MILL_FIRST_ARG=$1
  shift
fi

unset MILL_DOWNLOAD_PATH
unset MILL_OLD_DOWNLOAD_PATH
unset OLD_MILL
unset MILL_VERSION
unset MILL_REPO_URL

# -D mill.main.cli is for compatibility with Mill 0.10.9 - 0.13.0-M2
# We don't quote MILL_FIRST_ARG on purpose, so we can expand the empty value without quotes
# shellcheck disable=SC2086
exec "${MILL}" $MILL_FIRST_ARG -D "mill.main.cli=${MILL_MAIN_CLI}" "$@"
"""


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
    set "DEFAULT_MILL_VERSION=1.0.0"
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
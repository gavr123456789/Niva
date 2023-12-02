@echo off
setlocal

set "archive_path=Niva\Niva\build\distributions\Niva-SNAPSHOT-1.0.zip"

@REM if "%1"=="" (
@REM   echo use jvm or bin argument
@REM   exit /b 1
@REM )

cd .\Niva\Niva\
call .\gradlew.bat distZip -quiet
cd ..\..

powershell -Command "Expand-Archive -Path '%archive_path%' -DestinationPath niva_compiler -Force"
@REM if "%1"=="jvm" (
  echo jvm build...
  cd niva_compiler\Niva-SNAPSHOT-1.0\bin\
  mkdir "%USERPROFILE%\.niva\infroProject"
  xcopy /E /I /Y /Q ..\..\..\Niva\infroProject "%USERPROFILE%\.niva\infroProject"
@REM )

if "%1"=="bin" (
  echo binary version is not supported on Windows yet, use Linux
  exit /b 1
  echo Building binary...
  where native-image >nul 2>nul
  if errorlevel 1 (
    echo cant find native-image, on Arch linux do 'yay -S jdk21-graalvm-bin', 'nix shell nixpkgs#graalvm-ce' on nix
    exit /b 1
  )

  cd niva_compiler\Niva-SNAPSHOT-1.0\lib\
  native-image --static --no-fallback -O3 -march=compatibility --initialize-at-build-time --class-path annotations-13.0.jar;kotlin-stdlib-1.9.20.jar;Niva-jvm-SNAPSHOT-1.0.jar main.MainKt -o niva
  move niva ..\..

  cd ..\..
  mkdir "%USERPROFILE%\.niva\infroProject"
  xcopy /E /I /Y ..\Niva\infroProject "%USERPROFILE%\.niva\infroProject"
  rmdir /s /q Niva-SNAPSHOT-1.0
)

(
  echo type Person name: String age: Int
  echo Person say::String = (name + " saying " + say^) echo
  echo person = Person name: "Alice" age: 24
  echo person say: "Hello world!"

  echo list = {1 2 3}
  echo list2 = list filter: [it ^> 1]
  echo ^>list
) > main.scala

if "%1"=="bin" (
  .\niva main.scala
)
@REM if "%1"=="jvm" (
call .\Niva.bat main.scala
@REM )
pause

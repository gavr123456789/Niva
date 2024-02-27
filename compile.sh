#!/usr/bin/env sh

archive_path="Niva/Niva/build/distributions/niva-1.0-SNAPSHOT.zip"
jars="annotations-23.0.0.jar:kfswatch-jvm-1.0.0.jar:kotlin-stdlib-2.0.0-Beta4.jar:kotlin-stdlib-jdk7-1.9.10.jar:kotlin-stdlib-jdk8-1.9.10.jar:kotlinx-coroutines-core-jvm-1.8.0.jar:niva-1.0-SNAPSHOT.jar"

if [ "$1" == "" ]; then
  echo "use jvm or bin argument"
  exit 1
fi

cd ./Niva/Niva/ || exit
./gradlew distZip -quiet
cd ../..
unzip -o -q "$archive_path" -d niva_compiler

if [ "$1" == "jvm" ]; then
  echo "jvm build..."
  cd niva_compiler/niva-1.0-SNAPSHOT/bin/ || exit
  mkdir -p ~/.niva/infroProject
  cp -r ../../../Niva/infroProject ~/.niva/

fi

if [ "$1" == "bin" ]; then
  echo "Building binary..."
  if ! command -v native-image &> /dev/null; then
    echo "cant find native-image, on Arch linux do 'yay -S jdk21-graalvm-bin', 'nix shell nixpkgs#graalvm-ce' on nix"
    exit 1
  fi
  cd niva_compiler/niva-1.0-SNAPSHOT/lib/ || exit
  native-image --static --no-fallback -O3 -march=compatibility --initialize-at-build-time --class-path $jars main.MainKt -o niva
  mkdir -p ~/.niva/bin/
  cp -r niva ~/.niva/bin/niva
  mv niva ../..

  cd ../..
  mkdir -p ~/.niva/infroProject
  cp -r ../Niva/infroProject ~/.niva/
  # remove dir with jars
  rm -rf niva-SNAPSHOT-1.0


fi

echo 'type Person name: String age: Int
Person say::String = (name + " saying " + say) echo
person = Person name: "Alice" age: 24
person say: "Hello world!"

list = {1 2 3}
list2 = list filter: [it > 1]
>list' > main.scala
# xdg-open main.scala
printf "\n\n"

if [ "$1" == "bin" ]; then
  echo "building hello world project"
  ./niva main.scala
  printf "\n\n"
  echo "niva compiler binary is located inside the niva_compiler folder and ~/.niva/bin"
fi
if [ "$1" == "jvm" ]; then
  echo "building hello world project"
  ./niva main.scala
  printf "\n\n"
  echo "niva compiler jar is located inside the niva_compiler/niva-1.0-SNAPSHOT/bin folder"
fi


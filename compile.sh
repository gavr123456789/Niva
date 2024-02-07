#!/usr/bin/env sh

archive_path="Niva/Niva/build/distributions/niva-SNAPSHOT-1.0.zip"

if [ "$1" == "" ]; then
  echo "use jvm or bin argument"
  exit 1
fi

cd ./Niva/Niva/
./gradlew distZip -quiet
cd ../..
unzip -o -q "$archive_path" -d niva_compiler

if [ "$1" == "jvm" ]; then
  echo "jvm build..."
  cd niva_compiler/niva-SNAPSHOT-1.0/bin/
  mkdir -p ~/.niva/infroProject
  cp -r ../../../Niva/infroProject ~/.niva/

fi

if [ "$1" == "bin" ]; then
  echo "Building binary..."
  if ! command -v native-image &> /dev/null; then
    echo "cant find native-image, on Arch linux do 'yay -S jdk21-graalvm-bin', 'nix shell nixpkgs#graalvm-ce' on nix"
    exit 1
  fi
  cd niva_compiler/niva-SNAPSHOT-1.0/lib/
  native-image --static --no-fallback -O3 -march=compatibility --initialize-at-build-time --class-path annotations-13.0.jar:kotlin-stdlib-1.9.20.jar:niva-jvm-SNAPSHOT-1.0.jar main.MainKt -o niva
  cp -r niva ~/.niva/bin/
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
  ./niva main.scala
  echo "niva compiler binary is located inside the niva_compiler folder and ~/.niva/bin"
fi
if [ "$1" == "jvm" ]; then
  ./niva main.scala
  echo "niva compiler jar is located inside the niva_compiler/Niva-SNAPSHOT-1.0/bin folder"
fi


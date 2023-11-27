#!/usr/bin/env sh

archive_path="Niva/Niva/build/distributions/Niva-SNAPSHOT-1.0.zip"


cd ./Niva/Niva/
./gradlew distZip -quiet
cd ../..
unzip -o -q "$archive_path" -d niva_compiler

if [ "$1" == "jvm" ]; then
  echo "jvm build..."
  cd niva_compiler/Niva-SNAPSHOT-1.0/bin/
  cp -r ../../../Niva/infroProject ~/.niva/

fi

if [ "$1" == "bin" ]; then
  echo "Building binary..."
  if ! command -v native-image &> /dev/null; then
    echo "cant find native-image, on Arch linux do 'yay -S jdk21-graalvm-bin', 'nix shell nixpkgs#graalvm-ce' on nix"
    exit 1
  fi
  cd niva_compiler/Niva-SNAPSHOT-1.0/lib/
  native-image --static --no-fallback -O3 -march=compatibility --initialize-at-build-time --class-path annotations-13.0.jar:kotlin-stdlib-1.9.20.jar:Niva-jvm-SNAPSHOT-1.0.jar main.MainKt -o niva
  mv niva ../..

  cd ../..
  cp -r ../Niva/infroProject ~/.niva/infroProject
  # remove dir with jars
  rm -rf Niva-SNAPSHOT-1.0
fi

echo 'type Person name: String age: Int
Person say::String = (name + " saying " + say) echo
person = Person name: "Alice" age: 24
person say: "Hello world!"

list = {1 2 3}
list2 = list filter: [it > 1]
>list' > main.scala
# xdg-open main.scala

if [ "$1" == "bin" ]; then
  ./niva main.scala
fi
if [ "$1" == "jvm" ]; then
  ./Niva main.scala
fi

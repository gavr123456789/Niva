# JAR
`cd Niva/Niva`
`./gradlew buildJvmNiva`
binary is created in `~/.niva/niva/bin`
## Add to PATH
replace $path with `~/.niva/niva/bin`
- Bash: `echo 'export PATH=$PATH:$path' >> ~/.bashrc && source ~/.bashrc`
- Fish: `set -U fish_user_paths ${path} $fish_user_paths`
- ZSH: `zsh: echo 'export PATH=$PATH:$path' >> ~/.zshrc && source ~/.zshrc`
- Windows: `windows: setx PATH \"%PATH%;$path`

# LSP for VSCode
Instructions: https://github.com/gavr123456789/niva-vscode-bundle
1) `git clone https://github.com/gavr123456789/vaLSe.git`
2) `./gradlew installDist`
3) copy path to folder `vaLSe/build/install/nivals/bin/nivals`
4) install `niva` extension
5) in ext settings set the path to vaLSe that you copied before


# Nix
To get started with Niva, use the provided shell.nix file.
Enter the following command: `nix-shell`
This command will set up the necessary dependencies and run the compile script to produce a binary file.
Afterwards, you can run the Niva compiler with the following command:
```bash
./niva_compiler/niva <file>
```

# Native binary(if you are familiar with GraalVM)
GraalVM binary compiles hello world 300ms faster on M1(70% of the time takes gradle anyway)
If you have graalvm in your JAVA_HOME then run:
`./gradlew buildNativeNiva` this will create native binary in ~/.niva/bin

## Install GraalVM
Arch: `yay -S jdk22-graalvm-bin`
`archlinux-java status`
`archlinux-java set <JAVA_ENV_NAME>`
[select java on arch](https://wiki.archlinux.org/title/Java#Switching_between_JVM)

macOS: `brew install --cask graalvm-jdk`
`/usr/libexec/java_home -V`
`export JAVA_HOME='/usr/libexec/java_home -v 22.0.2'`
[select java on mac os](https://stackoverflow.com/questions/21964709/how-to-set-or-change-the-default-java-jdk-version-on-macos)
If you have `expanded from macro 'NS_FORMAT_ARGUMENT'` problem with buildNativeNiva on macOS then [update XCode](https://wails.io/docs/guides/troubleshooting/#my-mac-app-gives-me-weird-compilation-errors)
`xcode-select -p && sudo xcode-select --switch /Library/Developer/CommandLineTools`

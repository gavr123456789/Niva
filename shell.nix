{ pkgs ? import <nixpkgs> {} }: pkgs.mkShell {
    nativeBuildInputs = with pkgs.buildPackages; [
      graalvm-ce
      gradle
      jdk21
      unzip
    ];
    shellHook = ''
      ./compile.sh bin
    '';
}
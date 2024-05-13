plugins {
    kotlin("jvm") version "2.0.0-RC1"
    application
    id("org.graalvm.buildtools.native") version "0.10.1"
    id("maven-publish")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("io.github.irgaly.kfswatch:kfswatch:1.0.0")

    testImplementation(kotlin("test"))
}


tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

graalvmNative {
    binaries {
        named("main") {
            buildArgs.add("-O4")
            mainClass.set("main.MainKt")
        }
    }
    binaries.all {

        imageName.set("niva")
        buildArgs.add("-O4")
        buildArgs.add("--static")
        buildArgs.add("--no-fallback")
        buildArgs.add("-march=native")
        buildArgs.add("--initialize-at-build-time")
    }
}

application {
    mainClass = "main.MainKt"
}



publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "com.github.gavr123456789"
            artifactId = "niva"
            version = "0.1"

            from(components["java"])
        }
    }
}


//////OLD
//plugins {
//    id("org.graalvm.buildtools.native") version "0.10.1"
//}
//
//
//
//graalvmNative {
//    binaries {
//        named("main") {
//            mainClass.set("main.MainKt")
//        }
//    }
//    binaries.all {
//
//        imageName.set("niva")
//        buildArgs.add("-O4")
//        buildArgs.add("--static")
//        buildArgs.add("--no-fallback")
//        buildArgs.add("-march=native")
//        buildArgs.add("--initialize-at-build-time")
//    }
//}

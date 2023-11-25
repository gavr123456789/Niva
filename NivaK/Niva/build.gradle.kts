//kotlin {
//    jvm {
//        compilations {
//            val main = getByName("main")
//            tasks {
//                register<Jar>("myFatJar") {
//                    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
//
//                    group = "application"
//                    manifest {
//                        attributes["Implementation-Title"] = "Gradle Jar File Example"
//                        attributes["Implementation-Version"] = archiveVersion
//                        attributes["Main-Class"] = "[[mainClassPath]]"
//                    }
//                    archiveBaseName.set("${project.name}-fat")
//                    from(main.output.classesDirs, main.compileDependencyFiles)
//                    with(jar.get() as CopySpec)
//                }
//            }
//        }
//    }
//}

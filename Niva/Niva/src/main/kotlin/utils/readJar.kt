package main.utils

//import frontend.resolver.Type
//import java.net.URL
//import java.net.URLClassLoader
//import java.util.jar.JarFile

//
//fun readJar(pathToJar: String) {
//    val jarUrl = URL("file:///$pathToJar")
//    val loader = URLClassLoader(arrayOf(jarUrl))
////    loader
//    val jar = JarFile(pathToJar)
//
//    val entryes = jar.entries()
//
//    var counter = 0
//    var errorConter = 0
//    val nivaTypes = mutableListOf<Type.UserType>()
//    while (entryes.hasMoreElements()) {
//        val e = entryes.nextElement()
//        if (!e.isDirectory && e.name.endsWith(".class")) {
//            counter++
//            val className = e.name.replace("/", ".").replace(".class", "")
//            try {
//                val c = loader.loadClass(className)
//                val methods = c.methods
////                val params = w.parameters
//                val fields = c.fields.filter { it.name != "INSTANCE" }
//                if (fields.count() > 1 || fields.count() == 0) {
//                    nivaTypes.add(
//                        Type.UserType(
//                            name = c.name,
//                            typeArgumentList = emptyList(),
////                            fields = fields.map {
////                                KeywordArg(
////                                    name = it.name,
////                                    type = Type.UnknownGenericType(name = it.name, pkg = it.type?.`package`?.name ?: "???")
////                                )
////                            }.toMutableList(),
//                            fields = mutableListOf(),
//                            pkg =  c?.`package`?.name ?: "???"
//                        )
//                    )
//                }
//            } catch (e: Throwable) {
//                errorConter++
//            }
//        }
//
//    }
//
//    jar.close()
//}

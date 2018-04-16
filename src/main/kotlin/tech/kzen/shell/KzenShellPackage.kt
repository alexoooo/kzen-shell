package tech.kzen.shell

//import com.google.common.reflect.ClassPath
//import java.lang.invoke.MethodHandles


object KzenShellPackage {
    val name: String =
            javaClass.`package`.name

    val pathName = name.replace('.', '/')

//    private val classLoader =
//            javaClass.classLoader
////            MethodHandles.lookup().lookupClass().classLoader
//
//    private val classPath: ClassPath =
//            ClassPath.from(classLoader)
//
//    val resources: List<ClassPath.ResourceInfo> by lazy {
//        classPath.resources
//                .filter {
//                    it.resourceName.startsWith(pathName) &&
//                    ! it.resourceName.endsWith(".class")
////                    it.resourceName != "source_tips" &&
////                    ! it.resourceName.endsWith(".class") &&
////                    it.url().path.contains(pathName)
//                }
//    }
}
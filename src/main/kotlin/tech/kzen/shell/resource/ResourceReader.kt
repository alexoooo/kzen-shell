package tech.kzen.shell.resource

import com.google.common.io.Resources
import com.google.common.reflect.ClassPath
import tech.kzen.shell.KzenShellPackage
import tech.kzen.shell.resource.ResourceTree
import java.lang.invoke.MethodHandles
import java.nio.file.Path
import java.nio.file.Paths

class ResourceReader {
    companion object {
        private val classLoader =
//                javaClass.classLoader
                MethodHandles.lookup().lookupClass().classLoader

        private val classPath: ClassPath =
                ClassPath.from(classLoader)
    }


    fun read(packagePath: String): ResourceTree {
        val resources = classPath
                .resources
                .filter {
                    it.resourceName.startsWith(packagePath) &&
                    ! it.resourceName.endsWith(".class")
                }

        val files = mutableMapOf<Path, ByteArray>()

        for (resource in resources) {
//            val path =
//            val startOfName = resource.resourceName.lastIndexOf("/")

            val subPath = resource.resourceName.substring(packagePath.length)

            val subPackage = Paths.get(subPath)
//                    resource.resourceName
//                    .substring(packagePath.length, startOfName)

//            val name =
////                    resource.resourceName.substring(startOfName)
//                    path.fileName

            files[subPackage] = resource.asByteSource()!!.read()!!
        }

        return ResourceTree(files)
    }
}
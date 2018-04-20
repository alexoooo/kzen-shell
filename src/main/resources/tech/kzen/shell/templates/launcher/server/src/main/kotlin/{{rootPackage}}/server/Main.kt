@file:JvmName("Main")

package {{rootPackage}}.server

import {{rootPackage}}.common.getAnswer

fun main(args: Array<String>) {
    println("Hello from JVM! The answer is ${getAnswer()}.")
}

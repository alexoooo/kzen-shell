package tech.kzen.shell.process


// Carries what the launcher shows under a failed project row: the exit code when the child died on its
//  own during boot (null when it was alive but never served HTTP, and so was reaped), plus the tail of
//  its output — captured after the drain has consumed everything the child wrote.
class MainJarProcessStartException(
    message: String,
    val exitCode: Int?,
    val recentOutput: List<String>
): IllegalStateException(message)

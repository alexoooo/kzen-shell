package tech.kzen.shell.model

import kotlinx.serialization.Serializable


// One entry in the GET /shell/project response: a user-launched project and its lifecycle state.
//  Serialized by kotlinx.serialization (SER5); the `state` string is the wire contract shared (by shape, not
//  code) with kzen-launcher's RunningProject/RunningState DTO.
//  Values: "starting" | "running" | "stopping" | "failed" | "exited".
@Serializable
data class RunningProjectStatus(
    val name: String,
    val state: String,

    // Set when the child died on its own — after it was running ("exited") or during boot ("failed").
    val exitCode: Int? = null,

    // Tail of the child's output, populated for the "failed" and "exited" states so the launcher can show
    //  why it died without the user hunting for a log file.
    val recentOutput: List<String>? = null
)

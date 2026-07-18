package tech.kzen.shell.model

import kotlinx.serialization.Serializable


// One entry in the GET /shell/project response: a user-launched project and its lifecycle state.
//  Serialized by kotlinx.serialization (SER5); the `state` string is the wire contract shared (by shape, not
//  code) with kzen-launcher's RunningProject/RunningState DTO. Values: "starting" | "running" | "stopping" | "failed".
@Serializable
data class RunningProjectStatus(
    val name: String,
    val state: String
)

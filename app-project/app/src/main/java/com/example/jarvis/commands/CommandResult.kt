package com.example.jarvis.commands

/** Result returned after executing a command. */
sealed class CommandResult {
    data class Success(val message: String) : CommandResult()
    data class Failure(val reason: String)  : CommandResult()
    data class NeedsPermission(val permission: String, val rationale: String) : CommandResult()
    data class NeedsSetup(val instruction: String) : CommandResult()
    object Silent : CommandResult()
}

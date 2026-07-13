package com.amazecc.app.shared.utils

enum class ErrorLevel {
    SILENT, LOG, WARN, ERROR
}

data class ReportErrorOptions(
    val level: ErrorLevel = ErrorLevel.ERROR,
    val userMessage: String? = null,
    val context: String? = null
)

object ErrorUtils {
    fun reportError(err: Throwable, options: ReportErrorOptions = ReportErrorOptions()) {
        val prefix = if (options.context != null) "[${options.context}]" else ""
        val message = err.message ?: err.toString()

        when (options.level) {
            ErrorLevel.SILENT -> {
                // Do nothing
            }
            ErrorLevel.LOG -> {
                println("$prefix $message")
                err.printStackTrace()
            }
            ErrorLevel.WARN -> {
                println("WARN: $prefix $message")
                err.printStackTrace()
            }
            ErrorLevel.ERROR -> {
                println("ERROR: $prefix $message")
                err.printStackTrace()
            }
        }

        if (options.userMessage != null) {
            // Could integrate with a toast/notification system here
        }
    }
    
    fun reportError(messageObj: Any, options: ReportErrorOptions = ReportErrorOptions()) {
        if (messageObj is Throwable) {
            reportError(messageObj, options)
            return
        }
        
        val prefix = if (options.context != null) "[${options.context}]" else ""
        val message = messageObj.toString()

        when (options.level) {
            ErrorLevel.SILENT -> {}
            ErrorLevel.LOG -> println("$prefix $message")
            ErrorLevel.WARN -> println("WARN: $prefix $message")
            ErrorLevel.ERROR -> println("ERROR: $prefix $message")
        }

        if (options.userMessage != null) {
            // Could integrate with a toast/notification system here
        }
    }
}

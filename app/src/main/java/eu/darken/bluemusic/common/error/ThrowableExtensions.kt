package eu.darken.bluemusic.common.error

import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.InvocationTargetException
import kotlin.reflect.KClass

val Throwable.causes: Sequence<Throwable>
    get() = sequence {
        var subCause = cause
        while (subCause != null) {
            yield(subCause)
            subCause = subCause.cause
        }
    }

fun Throwable.getRootCause(): Throwable {
    var error = this
    while (error.cause != null) {
        error = error.cause!!
    }
    if (error is InvocationTargetException) {
        error = error.targetException
    }
    return error
}

fun Throwable.hasCause(exceptionClazz: KClass<out Throwable>): Boolean {
    if (exceptionClazz.isInstance(this)) return true
    return exceptionClazz.isInstance(this.getRootCause())
}

fun Throwable.getStackTraceString(): String {
    val sw = StringWriter(256)
    val pw = PrintWriter(sw, false)
    printStackTrace(pw)
    pw.flush()
    return sw.toString()
}

fun Throwable.tryUnwrap(kClass: KClass<RuntimeException> = RuntimeException::class): Throwable =
    if (!kClass.isInstance(this)) this else cause ?: this

/**
 * Attaches a secondary failure to the one being reported. Cleanup can hand back the very throwable
 * it is cleaning up after — a recorder broken in one way throws it on the start line and again on
 * the teardown line — and [Throwable.addSuppressed] rejects self-suppression with an
 * [IllegalArgumentException], which would abort the cleanup before the failure is ever reported.
 */
fun Throwable.addSuppressedSafely(other: Throwable) {
    if (this !== other) addSuppressed(other)
}
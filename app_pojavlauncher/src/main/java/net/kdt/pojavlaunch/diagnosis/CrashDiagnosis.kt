package net.kdt.pojavlaunch.diagnosis

import android.content.Context
import net.kdt.pojavlaunch.R
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets

/** One explained crash: what happened, why, what to try, and the log line that says so. */
class Diagnosis(
    val title: String,
    val explanation: String,
    val advice: String,
    val evidence: String?
)

/**
 * Reads the crashed session's log and names the failure in plain language.
 *
 * The whole engine is built around one requirement: the screen that explains a crash must never
 * cause one. So every stage is wrapped — a rule whose pattern fails to compile is skipped, an
 * extraction that goes wrong falls back to the unformatted wording, and [diagnose] as a whole
 * returns null rather than throwing, at which point the caller shows the same dialog it always
 * showed. Nothing here is on the happy path of anything else.
 *
 * The log is read as a bounded tail rather than whole: `latestlog.txt` has no size cap, and a
 * chatty modded session can make it enormous, but a crash always writes its reason at the end.
 *
 * Rules are ordered by specificity, first match wins. A missing-dependency message is worth more
 * than the `NoClassDefFoundError` twenty lines below it, and both are worth more than the exit
 * code — so the code-based rules (fatal signals, the kill by Android) only speak when the log
 * said nothing recognisable.
 *
 * Patterns stay in the subset shared by Kotlin's and Python's regex engines on purpose: the
 * pre-push check in `scripts/` re-runs them against fixture crash logs, which is the only test
 * this project can run without a device.
 */
object CrashDiagnosis {

    /** 128 KiB — several hundred lines of stack trace, read in one bounded gulp. */
    private const val TAIL_BYTES = 131072L
    private const val EVIDENCE_MAX_CHARS = 220

    private class Rule(
        val id: String,
        val pattern: String,
        val titleRes: Int,
        val explanationRes: Int,
        val adviceRes: Int
    )

    /**
     * Ordered by specificity. Loader verdicts first (they name the fix outright), then the JVM's
     * own failures, then the environment, and only then the broad mod-crash signatures that many
     * of the above would also leave behind.
     */
    private val RULES = listOf(
        Rule(
            "fabric_dependency",
            "ModResolutionException|[Ii]ncompatible mod set|Unmet dependency listing",
            R.string.crash_dep_title, R.string.crash_fabric_dep_explanation,
            R.string.crash_fabric_dep_advice_plain
        ),
        Rule(
            "forge_dependency",
            "Missing or unsupported mandatory dependencies",
            R.string.crash_dep_title, R.string.crash_forge_dep_explanation_plain,
            R.string.crash_forge_dep_advice
        ),
        Rule(
            "duplicate_mods",
            "DuplicateModsError|Found duplicate mods|Duplicate mod id",
            R.string.crash_duplicate_title, R.string.crash_duplicate_explanation_plain,
            R.string.crash_duplicate_advice
        ),
        Rule(
            "java_version",
            "UnsupportedClassVersionError",
            R.string.crash_java_version_title, R.string.crash_java_version_explanation_plain,
            R.string.crash_java_version_advice
        ),
        Rule(
            "java_too_new",
            "module java\\.base does not export",
            R.string.crash_java_too_new_title, R.string.crash_java_too_new_explanation,
            R.string.crash_java_too_new_advice
        ),
        Rule(
            "memory",
            "java\\.lang\\.OutOfMemoryError",
            R.string.crash_memory_title, R.string.crash_memory_explanation,
            R.string.crash_memory_advice
        ),
        Rule(
            "memory_native",
            "insufficient memory for the Java Runtime|Native memory allocation",
            R.string.crash_memory_native_title, R.string.crash_memory_native_explanation,
            R.string.crash_memory_native_advice
        ),
        Rule(
            "renderer",
            "eglMakeCurrent|EGL_BAD|Could not create context|does not appear to support OpenGL|GLFW error|libGL error",
            R.string.crash_renderer_title, R.string.crash_renderer_explanation,
            R.string.crash_renderer_advice
        ),
        Rule(
            "storage",
            "No space left on device|ENOSPC",
            R.string.crash_storage_title, R.string.crash_storage_explanation,
            R.string.crash_storage_advice
        ),
        Rule(
            "account",
            "InvalidCredentialsException|Status: 401",
            R.string.crash_account_title, R.string.crash_account_explanation,
            R.string.crash_account_advice
        ),
        // Deliberately narrow: "Connection reset" appears in perfectly normal multiplayer logs
        // every time a server drops the player, so it must never count as a diagnosis.
        Rule(
            "network",
            "java\\.net\\.UnknownHostException|java\\.net\\.SocketTimeoutException|Network is unreachable",
            R.string.crash_network_title, R.string.crash_network_explanation,
            R.string.crash_network_advice
        ),
        Rule(
            "corrupt_install",
            "zip END header not found|invalid END header|Could not find or load main class",
            R.string.crash_corrupt_title, R.string.crash_corrupt_explanation,
            R.string.crash_corrupt_advice
        ),
        Rule(
            "mod_broken",
            "MixinApplyError|InjectionError|mixin\\.throwables|mixin\\.injection|NoSuchMethodError|NoClassDefFoundError",
            R.string.crash_mod_broken_title, R.string.crash_mod_broken_explanation,
            R.string.crash_mod_broken_advice
        )
    )

    /**
     * Explain a crash, or null when even explaining failed.
     *
     * Null is a promise to the caller that it is safe to fall back to the old dialog: the engine
     * either produces a complete set of display strings or gets out of the way entirely.
     */
    @JvmStatic
    fun diagnose(context: Context, exitCode: Int, isSignal: Boolean, logFile: File?): Diagnosis? =
        runCatching {
            val tail = readTail(logFile)
            scanLog(context, tail) ?: fromExitCode(context, exitCode, isSignal, tail)
        }.getOrNull()

    private fun scanLog(context: Context, tail: String?): Diagnosis? {
        if (tail.isNullOrEmpty()) return null
        for (rule in RULES) {
            // A rule that fails to compile or match is a rule that stays silent, nothing more.
            val match = runCatching { Regex(rule.pattern).find(tail) }.getOrNull() ?: continue
            val base = Diagnosis(
                title = context.getString(rule.titleRes),
                explanation = context.getString(rule.explanationRes),
                advice = context.getString(rule.adviceRes),
                evidence = evidenceAt(tail, match.range.first)
            )
            return runCatching { enrich(context, rule.id, tail, base) }.getOrDefault(base)
        }
        return null
    }

    /**
     * Sharpen a finding with names pulled out of the log — the missing mod, the Java versions.
     * Purely additive: any misstep hands back the unformatted wording untouched.
     */
    private fun enrich(context: Context, id: String, tail: String, base: Diagnosis): Diagnosis {
        when (id) {
            "java_version" -> {
                val needed = Regex("class file version (\\d+)").find(tail)
                    ?.groupValues?.get(1)?.toInt() ?: return base
                val have = Regex("recognizes class file versions up to (\\d+)").find(tail)
                    ?.groupValues?.get(1)?.toInt() ?: return base
                // Class file 52 is Java 8, and the offset has held ever since.
                if (needed <= 44 || have <= 44) return base
                return Diagnosis(
                    base.title,
                    context.getString(
                        R.string.crash_java_version_explanation, needed - 44, have - 44
                    ),
                    base.advice, base.evidence
                )
            }
            "fabric_dependency" -> {
                val install = Regex("Install ([^,\\n]+)").find(tail)
                    ?.groupValues?.get(1)?.trim()?.trimEnd('.') ?: return base
                if (install.isEmpty() || install.length > 60) return base
                return Diagnosis(
                    base.title, base.explanation,
                    context.getString(R.string.crash_fabric_dep_advice, install),
                    base.evidence
                )
            }
            "forge_dependency" -> {
                val match = Regex("Mod ID: '([^']+)', Requested by: '([^']+)'").find(tail)
                    ?: return base
                return Diagnosis(
                    base.title,
                    context.getString(
                        R.string.crash_forge_dep_explanation,
                        match.groupValues[2], match.groupValues[1]
                    ),
                    base.advice, base.evidence
                )
            }
            "duplicate_mods" -> {
                val id1 = Regex("Duplicate mod id: ([A-Za-z0-9_-]+)").find(tail)
                    ?.groupValues?.get(1)
                val id2 = Regex("Mod ID: '([^']+)' from mod files").find(tail)
                    ?.groupValues?.get(1)
                val modId = id1 ?: id2 ?: return base
                return Diagnosis(
                    base.title,
                    context.getString(R.string.crash_duplicate_explanation, modId),
                    base.advice, base.evidence
                )
            }
        }
        return base
    }

    /**
     * What the exit code alone can say once the log has said nothing.
     *
     * Only SIGABRT ever arrives on the signal path — the JVM folds its fatal signals into an
     * abort, and SIGKILL cannot be caught at all — but the neighbouring codes are matched anyway
     * so a changed native side never downgrades this to the generic card.
     */
    private fun fromExitCode(
        context: Context,
        exitCode: Int,
        isSignal: Boolean,
        tail: String?
    ): Diagnosis {
        val killed = (isSignal && exitCode == 9) || (!isSignal && exitCode == 137)
        val native = isSignal || exitCode == 134 || exitCode == 139
        return when {
            killed -> Diagnosis(
                context.getString(R.string.crash_killed_title),
                context.getString(R.string.crash_killed_explanation),
                context.getString(R.string.crash_memory_native_advice),
                lastLine(tail)
            )
            native -> Diagnosis(
                context.getString(R.string.crash_native_title),
                context.getString(R.string.crash_native_explanation),
                context.getString(R.string.crash_native_advice),
                lastLine(tail)
            )
            else -> Diagnosis(
                context.getString(R.string.crash_generic_title),
                context.getString(R.string.crash_generic_explanation),
                context.getString(R.string.crash_generic_advice),
                lastLine(tail)
            )
        }
    }

    /**
     * The last chunk of the log, decoded so it can never fail.
     *
     * ISO-8859-1 maps every byte to exactly one char — no partial UTF-8 sequence at the cut
     * point can throw — and the patterns are all ASCII, so nothing is lost by it.
     */
    private fun readTail(logFile: File?): String? = runCatching {
        if (logFile == null || !logFile.isFile) return@runCatching null
        RandomAccessFile(logFile, "r").use { raf ->
            val length = raf.length()
            val start = maxOf(0L, length - TAIL_BYTES)
            val size = (length - start).toInt()
            if (size <= 0) return@use null
            raf.seek(start)
            val buffer = ByteArray(size)
            raf.readFully(buffer)
            String(buffer, StandardCharsets.ISO_8859_1)
        }
    }.getOrNull()

    /** The whole log line around a match, trimmed to something a card can carry. */
    private fun evidenceAt(tail: String, index: Int): String? {
        val from = tail.lastIndexOf('\n', index).let { if (it == -1) 0 else it + 1 }
        val to = tail.indexOf('\n', index).let { if (it == -1) tail.length else it }
        if (from >= to) return null
        return tail.substring(from, to).trim().take(EVIDENCE_MAX_CHARS).ifEmpty { null }
    }

    /** The last thing the game said, for the cards that have no matched line to quote. */
    private fun lastLine(tail: String?): String? {
        if (tail == null) return null
        return tail.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .lastOrNull()
            ?.take(EVIDENCE_MAX_CHARS)
    }
}

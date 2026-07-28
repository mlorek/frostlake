/*
 * Copyright 2026 MLorek
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.frostlake.executor.udf;

/**
 * Explains Python-version failures. The engine embeds GraalPy, which implements Python 3.12, while
 * Snowflake objects may declare a NEWER runtime ({@code RUNTIME_VERSION = '3.11'}/{@code '3.12'}) and
 * use syntax or standard-library features added after 3.12. When such a body fails to parse, the raw
 * message says nothing about the version gap, so it is annotated here.
 *
 * <p>The declared version NEVER pre-rejects an object: a body declaring a newer runtime is usually
 * valid here too. The explanation is attached only when execution actually failed with a parse error.
 */
public final class PythonRuntimeDiagnostics {

    /** Python version implemented by the embedded interpreter. */
    private static final String EMBEDDED_PYTHON = PythonRuntime.EMBEDDED_PYTHON;

    private PythonRuntimeDiagnostics() {
    }

    /** Python version implemented by the embedded interpreter, as a minor number (3.x → x). */
    private static final int EMBEDDED_MINOR = 12;

    /**
     * True when {@code runtimeVersion} names a Python NEWER than the embedded interpreter — the only
     * case where the version can explain a parse failure (null/unparseable → unknown → false).
     */
    public static boolean declaresNewerPython(final String runtimeVersion) {
        if (runtimeVersion == null) {
            return false;
        }
        final String trimmed = runtimeVersion.trim();
        if (!trimmed.startsWith("3.")) {
            return false;
        }
        try {
            return Integer.parseInt(trimmed.substring(2).split("\\.")[0]) > EMBEDDED_MINOR;
        } catch (final NumberFormatException notANumber) {
            return false;
        }
    }

    /**
     * True when {@code error} is a Python PARSE failure rather than a runtime error. Jython reports
     * these as SyntaxError; the ANTLR wording ("mismatched input", "no viable alternative") is
     * matched too, since a body rejected by the grammar can surface either form.
     */
    public static boolean isSyntaxFailure(final Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause()) {
            final String text = t.getMessage();
            if (text != null
                    && (text.contains("SyntaxError")
                        || text.contains("mismatched input")
                        || text.contains("no viable alternative")
                        || text.contains("IndentationError"))) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    /**
     * Constructs in {@code body} that postdate the embedded interpreter, as a short human-readable
     * list, or null when none are recognized. Deliberately a cheap textual scan: it only enriches an
     * error message that is already being thrown, and never gates execution.
     */
    public static String python3Constructs(final String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        final StringBuilder found = new StringBuilder();
        appendIf(found, containsTypeParameterDefault(body), "type-parameter defaults, PEP 696 (3.13+)");
        appendIf(found, body.contains("from typing import ReadOnly") || body.contains("typing.ReadOnly"),
            "typing.ReadOnly (3.13+)");
        appendIf(found, body.contains("from typing import TypeIs") || body.contains("typing.TypeIs"),
            "typing.TypeIs (3.13+)");
        appendIf(found, body.contains("PythonFinalizationError"), "PythonFinalizationError (3.13+)");
        return found.length() == 0 ? null : found.toString();
    }

    private static void appendIf(final StringBuilder target, final boolean present, final String label) {
        if (!present) {
            return;
        }
        if (target.length() > 0) {
            target.append(", ");
        }
        target.append(label);
    }

    /** True when a def/class line declares a type parameter with a DEFAULT — {@code def f[T = int]}. */
    private static boolean containsTypeParameterDefault(final String body) {
        for (final String rawLine : body.split("\n")) {
            final String line = rawLine.trim();
            if (!line.startsWith("def ") && !line.startsWith("class ")) {
                continue;
            }
            final int open = line.indexOf('[');
            final int close = line.indexOf(']', open + 1);
            if (open >= 0 && close > open && line.substring(open + 1, close).contains("=")) {
                return true;
            }
        }
        return false;
    }

    /**
     * True when a {@code def} line carries a parameter annotation — {@code def f(x: int)}. Scanned
     * per line so a dict literal or a slice elsewhere in the body cannot look like an annotation.
     */
    private static boolean containsParameterAnnotation(final String body) {
        for (final String rawLine : body.split("\n")) {
            final String line = rawLine.trim();
            if (!line.startsWith("def ")) {
                continue;
            }
            final int open = line.indexOf('(');
            final int close = line.lastIndexOf(')');
            if (open >= 0 && close > open && line.substring(open + 1, close).contains(":")) {
                return true;
            }
        }
        return false;
    }

    /**
     * The message for a failed Python execution: the original error, plus — when the object declares
     * a Python 3 runtime and the failure was a parse error — what actually went wrong and which
     * constructs triggered it.
     *
     * @param kind        "function" or "procedure", for the message text
     * @param name        the object's name
     * @param runtimeVersion the declared RUNTIME_VERSION (may be null)
     * @param body        the Python source, scanned for Python-3-only constructs
     * @param error       the failure being reported
     */
    public static String describeFailure(final String kind, final String name, final String runtimeVersion,
                                         final String body, final Throwable error) {
        final StringBuilder message = new StringBuilder("Error executing Python ")
            .append(kind).append(' ').append(name).append(": ").append(error.getMessage());
        if (!declaresNewerPython(runtimeVersion) || !isSyntaxFailure(error)) {
            return message.toString();
        }
        message.append(" — the ").append(kind).append(" declares RUNTIME_VERSION '").append(runtimeVersion)
            .append("' but this engine embeds Python ").append(EMBEDDED_PYTHON)
            .append(", so syntax added after that version cannot be parsed");
        final String constructs = python3Constructs(body);
        if (constructs != null) {
            message.append(" (body uses ").append(constructs).append(')');
        }
        message.append(". Rewrite the body using Python ").append(EMBEDDED_PYTHON)
            .append("-compatible syntax, or run this object on Snowflake.");
        return message.toString();
    }
}

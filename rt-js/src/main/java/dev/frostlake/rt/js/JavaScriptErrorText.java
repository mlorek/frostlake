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

package dev.frostlake.rt.js;

import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.SourceSection;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.script.ScriptException;

/**
 * The sentence an uncaught JavaScript error reaches SQL with, in the account's shape (live-verified):
 * {@code JavaScript execution error: Uncaught TypeError: Cannot read properties of null (reading 'x') in F
 * at 'return null.x;' position 12}, then {@code stackstrace: } and the handler's frame, {@code F line: 1}.
 * The quoted line is the handler body's own, as written, and the position its column: the property a
 * failed read names, the {@code =} of a failed write, otherwise where the failing construct starts. The
 * engine's wording of a failed property read or write is given the account's, and a thrown plain object
 * reads {@code #<Object>}. A failure raised by host code, such as a statement a procedure runs, keeps the
 * caller's own sentence.
 */
final class JavaScriptErrorText {

    /** The engine's wording of a failed property read. */
    private static final Pattern FAILED_READ =
        Pattern.compile("TypeError: Cannot read property '(.*)' of (null|undefined)");

    /** The engine's wording of a failed property write. */
    private static final Pattern FAILED_WRITE =
        Pattern.compile("TypeError: Cannot set property '(.*)' of (null|undefined)");

    private JavaScriptErrorText() {
    }

    /**
     * @param failure   the engine's failure
     * @param name      the handler's name as the catalog holds it
     * @param body      the handler's body as written
     * @param firstLine the line of the evaluated source that holds the body's first line
     * @param fallback  the sentence for a failure that is not the script's own
     * @return the sentence
     */
    static String of(final ScriptException failure, final String name, final String body, final int firstLine,
                     final String fallback) {
        final PolyglotException guest = guestFailure(failure);
        if (guest == null) {
            return fallback;
        }
        String message = String.valueOf(guest.getMessage());
        final Matcher read = FAILED_READ.matcher(message);
        final Matcher write = FAILED_WRITE.matcher(message);
        final boolean failedRead = read.matches();
        final boolean failedWrite = !failedRead && write.matches();
        if (failedRead) {
            message = "TypeError: Cannot read properties of " + read.group(2) + " (reading '" + read.group(1) + "')";
        } else if (failedWrite) {
            message = "TypeError: Cannot set properties of " + write.group(2) + " (setting '" + write.group(1) + "')";
        } else if (guest.getGuestObject() != null && guest.getGuestObject().getMetaObject() != null
                && "Object".equals(guest.getGuestObject().getMetaObject().getMetaSimpleName())) {
            // A thrown plain object: V8 prints its tag.
            message = "#<Object>";
        }
        final String[] lines = body.split("\n", -1);
        int line = 1;
        int position = 0;
        final SourceSection at = guest.getSourceLocation() != null ? guest.getSourceLocation() : firstGuestFrame(guest);
        final boolean thrownError = guest.getSourceLocation() == null;
        if (at != null && at.getStartLine() >= firstLine && at.getStartLine() - firstLine < lines.length) {
            line = at.getStartLine() - firstLine + 1;
            position = at.getStartColumn() - 1;
            final String text = String.valueOf(at.getCharacters());
            if (failedRead && text.lastIndexOf('.') >= 0) {
                position += text.lastIndexOf('.') + 1;
            } else if (failedWrite && lines[line - 1].indexOf('=', position) >= 0) {
                position = lines[line - 1].indexOf('=', position);
            } else if (thrownError && lines[line - 1].indexOf("throw") >= 0
                    && lines[line - 1].indexOf("throw") == lines[line - 1].lastIndexOf("throw")) {
                // A thrown Error is placed where it was built; the account places it at its throw.
                position = lines[line - 1].indexOf("throw");
            }
        }
        return "JavaScript execution error: Uncaught " + message + " in " + name + " at '" + lines[line - 1]
            + "' position " + position + "\nstackstrace: \n" + frames(guest, name, firstLine, lines.length, line);
    }

    /**
     * The frames listed after {@code stackstrace: }: each named function the failure passed through inside
     * the body, innermost first, then the handler itself at the line it had reached. The handler is the
     * outermost frame inside the body; the wrappers around it lie outside the body's lines.
     */
    private static String frames(final PolyglotException guest, final String name, final int firstLine,
                                 final int bodyLines, final int failureLine) {
        final List<String> roots = new ArrayList<>();
        final List<Integer> lines = new ArrayList<>();
        for (final PolyglotException.StackFrame frame : guest.getPolyglotStackTrace()) {
            if (!frame.isGuestFrame() || frame.getSourceLocation() == null) {
                continue;
            }
            final int line = frame.getSourceLocation().getStartLine() - firstLine + 1;
            if (line >= 1 && line <= bodyLines) {
                roots.add(frame.getRootName());
                lines.add(line);
            }
        }
        if (roots.isEmpty()) {
            return name + " line: " + failureLine;
        }
        final StringBuilder listed = new StringBuilder();
        for (int i = 0; i < roots.size() - 1; i++) {
            final String root = roots.get(i);
            if (root != null && !root.isEmpty() && !root.startsWith(":")) {
                listed.append(root).append(" line: ").append(lines.get(i)).append('\n');
            }
        }
        return listed.append(name).append(" line: ").append(lines.get(lines.size() - 1)).toString();
    }

    /** Where the failing script frame stands, for a failure that carries no location of its own. */
    private static SourceSection firstGuestFrame(final PolyglotException guest) {
        for (final PolyglotException.StackFrame frame : guest.getPolyglotStackTrace()) {
            if (frame.isGuestFrame() && frame.getSourceLocation() != null) {
                return frame.getSourceLocation();
            }
        }
        return null;
    }

    /** The script's own failure behind the engine's, or null when host code raised it. */
    private static PolyglotException guestFailure(final Throwable failure) {
        Throwable cause = failure;
        while (cause != null) {
            if (cause instanceof PolyglotException) {
                final PolyglotException polyglot = (PolyglotException) cause;
                return polyglot.isGuestException() && !polyglot.isHostException() ? polyglot : null;
            }
            cause = cause.getCause();
        }
        return null;
    }
}

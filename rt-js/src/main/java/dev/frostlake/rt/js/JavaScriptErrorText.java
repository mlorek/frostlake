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
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * The sentence an uncaught JavaScript error reaches SQL with, in the account's shape (live-verified):
 * {@code JavaScript execution error: Uncaught TypeError: Cannot read properties of null (reading 'x') in F
 * at 'return null.x;' position 12}, then {@code stackstrace: } and the frames inside the handler, innermost
 * first, a function without a name as an empty one, and last the handler's own, {@code F line: 1}.
 *
 * <p>The quoted line is the handler body's own, as written, and the position its column, placed where the
 * account's engine places each kind of failure: the property a failed read or call names, the bracket of an
 * indexed read or of a call of something that has no name, the {@code =} of a failed write or assignment, the
 * operator of a failed operation, a {@code new} expression's start, and a name that is not defined where
 * {@link JavaScriptNamePlace} tells. An error that was thrown is quoted at the {@code throw} that sent it out
 * ({@link JavaScriptThrowSite}), while its frames stay those of where it arose. The engine's wording is given
 * the account's ({@link JavaScriptFaultWording}); what was thrown is told as the account tells it, an error by
 * its name and message; a text {@code JSON.parse} refused is quoted in place of the body's line. A user
 * exception reaches SQL as its message alone. A failure raised by host code, such as a statement a procedure
 * runs, keeps the caller's own sentence.
 */
final class JavaScriptErrorText {

    private static final String STACK = "\nstackstrace: \n";

    private JavaScriptErrorText() {
    }

    /**
     * @param failure   the engine's failure
     * @param name      the handler's name as the catalog holds it
     * @param body      the handler's body as written
     * @param firstLine the line of the evaluated source that holds the body's first line
     * @param script    the evaluated source, which tells the handler's frames from the engine's own
     * @param host      the engine's host object, or null
     * @param fallback  the sentence for a failure that is not the script's own
     * @return the sentence
     */
    static String of(final Throwable failure, final String name, final String body, final int firstLine,
                     final String script, final JavaScriptHost host, final String fallback) {
        final PolyglotException guest = guestFailure(failure);
        if (guest == null) {
            return fallback;
        }
        final Value thrown = guest.getGuestObject();
        if (flag(thrown, "_isCustomizableErrorFormat")) {
            return memberText(thrown, "message", "");
        }
        final String described = host == null ? null : host.describe(thrown);
        final String told = described != null ? described : sentence(guest, thrown);
        final String[] lines = body.split("\n", -1);
        // The frames inside the body, innermost first: where each starts, and its source. A class's own frame,
        // which stands for a constructor it was not written, is not listed.
        final List<String> roots = new ArrayList<>();
        final List<Integer> starts = new ArrayList<>();
        final List<String> sources = new ArrayList<>();
        for (final PolyglotException.StackFrame frame : guest.getPolyglotStackTrace()) {
            final SourceSection where = frame.isGuestFrame() ? frame.getSourceLocation() : null;
            if (!inBody(where, script, firstLine, lines.length)
                    || String.valueOf(where.getCharacters()).startsWith("class ")) {
                continue;
            }
            roots.add(frame.getRootName());
            starts.add(offset(lines, where, firstLine));
            sources.add(String.valueOf(where.getCharacters()));
        }
        final SourceSection own = inBody(guest.getSourceLocation(), script, firstLine, lines.length)
            ? guest.getSourceLocation() : null;
        if (own == null) {
            // An error a class of the handler's builds arises where its constructor reaches the parent's; the
            // account lists and places it from the expression that builds it.
            while (roots.size() > 1 && sources.get(0).startsWith("super(")) {
                roots.remove(0);
                starts.remove(0);
                sources.remove(0);
            }
        }
        // A failure the engine places at the handler's whole body has no place in it: the body stands for it.
        final boolean unplaced = own == null && roots.isEmpty();
        final int begin = unplaced ? 0 : own != null ? offset(lines, own, firstLine) : starts.get(0);
        final String section = unplaced ? body : own != null ? String.valueOf(own.getCharacters()) : sources.get(0);
        final JavaScriptSourceScan scan = new JavaScriptSourceScan(body);
        if (flag(thrown, "keepErrorMessage")) {
            // Raised by the API object's own code, whose line the account quotes.
            return "JavaScript execution error: Uncaught " + told + " in " + name
                + " at '    throw err;' position 4" + STACK + "Snowflake.throwUnredactedError line: 15\n" + name
                + " line: " + lineOf(lines, begin + new JavaScriptSourceScan(section).callPlace());
        }
        final V8JsonFault json = host != null && told.startsWith("SyntaxError: ")
            ? host.jsonFaultOf(told.substring("SyntaxError: ".length())) : null;
        if (json != null) {
            final int call = begin + new JavaScriptSourceScan(section).callPlace();
            return "JavaScript execution error: Uncaught " + told + " in " + name + " at '" + json.line()
                + "' position " + json.column() + STACK + frames(roots, starts, sources, name, lines, call);
        }
        final int origin = Math.min(place(told, own != null || unplaced, section, begin, scan, body, unplaced),
            body.length());
        final int site = thrownAt(origin, own == null && builds(section), starts, sources,
            new JavaScriptThrowSite(scan, body));
        final int line = lineOf(lines, site);
        return "JavaScript execution error: Uncaught "
            + JavaScriptFaultWording.of(told, section, lines[lineOf(lines, origin) - 1], body,
                around(scan, body, begin))
            + " in " + name + " at '" + lines[line - 1] + "' position " + (site - lineStart(lines, line)) + STACK
            + frames(roots, starts, sources, name, lines, origin);
    }

    /**
     * Where a failure is placed in the body.
     *
     * @param message the engine's sentence
     * @param own     whether the failure names the failing expression itself, rather than the call that led to it
     * @param section the source of that expression or call
     * @param begin   where it starts in the body
     * @param whole   the reading of the body
     * @param body    the body
     * @param handler whether the section is the handler's whole body
     * @return where the account places it
     */
    private static int place(final String message, final boolean own, final String section, final int begin,
                             final JavaScriptSourceScan whole, final String body, final boolean handler) {
        final JavaScriptSourceScan scan = new JavaScriptSourceScan(section);
        final boolean iterates = message.endsWith(" is not iterable") || message.contains("Symbol(Symbol.iterator)");
        if (iterates && section.startsWith("...")) {
            // A spread is placed at what it spreads, a loop over values at what it loops over.
            return begin + scan.firstFrom(3);
        }
        if (iterates && (handler || section.startsWith("{"))) {
            // The engine places a loop's failure at the body of the function the loop is in: the loop is the
            // only one over values that function has, or the place stays the function's.
            final int looped = onlyLoopOver(whole, body, handler ? -1 : begin);
            return looped >= 0 ? looped : begin;
        }
        if (!own) {
            // Raised inside a builtin: placed at the call that reached it.
            return begin + scan.callPlace();
        }
        final int last = scan.lastBefore(section.length());
        if (message.contains("Cannot read property ") || message.contains("private member")) {
            if (message.contains("Symbol(Symbol.iterator)") && scan.assignment() >= 0) {
                // An array pattern is placed at what it takes apart.
                return begin + scan.firstFrom(scan.assignment() + 1);
            }
            if (last >= 0 && section.charAt(last) == ']' && scan.opening(last) >= 0) {
                return begin + scan.opening(last);
            }
            int dot = last;
            while (dot >= 0 && !(scan.isCode(dot) && section.charAt(dot) == '.')) {
                dot--;
            }
            return dot >= 0 ? begin + scan.firstFrom(dot + 1) : begin;
        }
        if (message.contains("Cannot set property ")) {
            int assigned = begin;
            while (assigned < body.length() && !(whole.isCode(assigned) && body.charAt(assigned) == '=')) {
                assigned++;
            }
            return assigned < body.length() ? assigned : begin;
        }
        if (message.endsWith(" is not a function")) {
            return begin + scan.callPlace();
        }
        if (scan.assignment() >= 0 && (message.startsWith("TypeError: Assignment to constant")
                || message.startsWith("ReferenceError: "))) {
            return begin + scan.assignment();
        }
        if (message.startsWith("ReferenceError: ") && message.endsWith(" is not defined")) {
            return JavaScriptNamePlace.of(whole, body, begin);
        }
        final boolean operation = message.contains("BigInt") || message.contains("Symbol value")
            || message.contains("instanceof") || message.endsWith(" is not an Object")
            || message.endsWith("Division by zero");
        if (operation && section.startsWith("`") && section.indexOf("${") >= 0) {
            // A template is placed at the value it could not write.
            return begin + section.indexOf("${") + 2;
        }
        return operation && scan.rootOperator() >= 0 ? begin + scan.rootOperator() : begin;
    }

    /**
     * Where what the only {@code for ... of} loop of a function loops over starts, or -1 when the function has
     * none or several.
     *
     * @param function the brace that opens the function's body, or -1 for the handler's own
     */
    private static int onlyLoopOver(final JavaScriptSourceScan scan, final String body, final int function) {
        int found = -1;
        int i = 0;
        while (i < body.length()) {
            if (!scan.startsName(i)) {
                i++;
                continue;
            }
            final int end = scan.nameEnd(i);
            final int open = scan.firstFrom(end);
            if (scan.isWord(i, end, "for") && open < body.length() && body.charAt(open) == '('
                    && scan.closing(open) > open && scan.functionAround(i) == function) {
                int word = open + 1;
                while (word < scan.closing(open)) {
                    if (scan.isCode(word) && scan.closing(word) > word) {
                        word = scan.closing(word) + 1;
                    } else if (scan.startsName(word) && scan.isWord(word, scan.nameEnd(word), "of")) {
                        if (found >= 0) {
                            return -1;
                        }
                        found = scan.firstFrom(word + 2);
                        break;
                    } else {
                        word++;
                    }
                }
            }
            i = end;
        }
        return found;
    }

    /** Whether a call's source builds an error object: a {@code new} expression, or an error type called plainly. */
    private static boolean builds(final String section) {
        if (section.startsWith("new ") || section.startsWith("new(")) {
            return true;
        }
        final int open = section.indexOf('(');
        return open > 0 && section.substring(0, open).endsWith("Error") && section.lastIndexOf('.', open) < 0;
    }

    /** The bracket a place is written in: the nearest one open there, or a blank when none is. */
    private static char around(final JavaScriptSourceScan scan, final String body, final int place) {
        char found = ' ';
        int i = 0;
        while (i < place && i < body.length()) {
            if (scan.isCode(i) && scan.closing(i) > i && scan.closing(i) < place) {
                i = scan.closing(i) + 1;
                continue;
            }
            if (scan.isCode(i) && "([{".indexOf(body.charAt(i)) >= 0) {
                found = body.charAt(i);
            }
            i++;
        }
        return found;
    }

    /**
     * Where an error that arose at a place was thrown last: by the statement that throws what was built there,
     * in the function that built it or in the one it was returned to, and then by each {@code catch} clause that
     * threw it again, in the function it arose in and in every one that called that.
     */
    private static int thrownAt(final int origin, final boolean built, final List<Integer> starts,
                                final List<String> sources, final JavaScriptThrowSite site) {
        int at = origin;
        int frame = 0;
        if (built) {
            int holder = 0;
            while (holder < starts.size()) {
                final int from = starts.get(holder);
                final int thrown = site.thrownFrom(from, from + sources.get(holder).length());
                if (thrown >= 0) {
                    at = thrown;
                    frame = holder;
                    break;
                }
                if (!site.isReturned(from)) {
                    break;
                }
                holder++;
            }
        }
        for (int i = frame; i < Math.max(1, starts.size()); i++) {
            final int again = site.thrownAgain(i == frame ? at : starts.get(i));
            if (again >= 0) {
                at = again;
            }
        }
        return at;
    }

    /** What was thrown, for an engine that has no host to tell it: an error by its name and message. */
    private static String sentence(final PolyglotException guest, final Value thrown) {
        if (isError(thrown)) {
            final String name = memberText(thrown, "name", "Error");
            final String text = memberText(thrown, "message", "");
            if (text.isEmpty()) {
                return name;
            }
            return name.isEmpty() ? text : name + ": " + text;
        }
        if (thrown != null && thrown.getMetaObject() != null
                && "Object".equals(thrown.getMetaObject().getMetaSimpleName())) {
            return "#<Object>";
        }
        return String.valueOf(guest.getMessage());
    }

    /** Whether a thrown value is an error object: an instance of the handler world's {@code Error}. */
    private static boolean isError(final Value thrown) {
        if (thrown == null || !thrown.hasMembers()) {
            return false;
        }
        try {
            final Value error = thrown.getContext().getBindings("js").getMember("Error");
            return error != null && error.isMetaObject() && error.isMetaInstance(thrown);
        } catch (final RuntimeException unreadable) {
            return false;
        }
    }

    /**
     * A member of a thrown value as text, or {@code absent}. A value whose context is gone (a failure another
     * language's runtime has since closed) cannot be read, and its members are absent.
     */
    private static String memberText(final Value object, final String member, final String absent) {
        try {
            if (object == null || !object.hasMembers()) {
                return absent;
            }
            final Value value = object.getMember(member);
            if (value == null || value.isNull()) {
                return absent;
            }
            return value.isString() ? value.asString() : value.toString();
        } catch (final RuntimeException unreadable) {
            return absent;
        }
    }

    /** Whether a thrown value holds {@code member} as TRUE; a value that cannot be read holds nothing. */
    private static boolean flag(final Value object, final String member) {
        try {
            if (object == null || !object.hasMembers() || !object.hasMember(member)) {
                return false;
            }
            final Value value = object.getMember(member);
            return value != null && value.isBoolean() && value.asBoolean();
        } catch (final RuntimeException unreadable) {
            return false;
        }
    }

    /**
     * The frames listed after {@code stackstrace: }: each function the failure passed through inside the body,
     * innermost first, then the handler itself. The innermost is listed at the line the failure arose on, the
     * others at the line of the call they had reached. The handler is the outermost frame inside the body; the
     * wrappers around it lie outside the body's lines, and the engine's own code in another source.
     */
    private static String frames(final List<String> roots, final List<Integer> starts, final List<String> sources,
                                 final String name, final String[] lines, final int origin) {
        final StringBuilder listed = new StringBuilder();
        for (int i = 0; i < roots.size(); i++) {
            final int at = i == 0 ? origin : starts.get(i) + new JavaScriptSourceScan(sources.get(i)).callPlace();
            final String root = roots.get(i);
            final boolean handler = i == roots.size() - 1;
            listed.append(handler ? name : root == null || root.startsWith(":") ? "" : root).append(" line: ")
                .append(lineOf(lines, at)).append(handler ? "" : "\n");
        }
        return roots.isEmpty() ? name + " line: " + lineOf(lines, origin) : listed.toString();
    }

    /** Whether a location lies in the handler's body: in the evaluated source, on one of the body's lines. */
    private static boolean inBody(final SourceSection at, final String script, final int firstLine,
                                  final int bodyLines) {
        if (at == null || at.getSource() == null || at.getStartLine() < firstLine
                || at.getStartLine() - firstLine >= bodyLines) {
            return false;
        }
        final CharSequence source = at.getSource().getCharacters();
        return script == null || source.length() == script.length() && script.contentEquals(source);
    }

    /** Where a location of the evaluated source starts in the body. */
    private static int offset(final String[] lines, final SourceSection at, final int firstLine) {
        final int line = at.getStartLine() - firstLine + 1;
        return Math.min(lineStart(lines, line) + at.getStartColumn() - 1, lineStart(lines, line + 1) - 1);
    }

    /** Where a line of the body starts in it; the line after the last starts past the body's end. */
    private static int lineStart(final String[] lines, final int line) {
        int start = 0;
        for (int i = 0; i < line - 1 && i < lines.length; i++) {
            start += lines[i].length() + 1;
        }
        return start;
    }

    /** The line of the body a place is on, the first being 1. */
    private static int lineOf(final String[] lines, final int place) {
        int line = 1;
        while (line < lines.length && lineStart(lines, line + 1) <= place) {
            line++;
        }
        return line;
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

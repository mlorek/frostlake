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

/**
 * Where an error that left a handler was thrown last. The account's engine places an uncaught error at the
 * {@code throw} that sent it out, which need not be where it arose: an error caught and thrown again is placed
 * at the second {@code throw}, one built into a variable at the statement that throws the variable
 * (live-verified). This engine knows only where an error arose, so the {@code throw} is read from the handler's
 * source, and found where the source leaves one candidate: the only {@code throw} of a {@code catch} clause's
 * own parameter, the only {@code throw} of the variable in its function.
 */
final class JavaScriptThrowSite {

    private final JavaScriptSourceScan scan;
    private final String text;

    JavaScriptThrowSite(final JavaScriptSourceScan scan, final String text) {
        this.scan = scan;
        this.text = text;
    }

    /**
     * Follows an error out of the {@code try} blocks around a place: while the nearest one in the same function
     * has a {@code catch} clause that throws its parameter once, the error goes on from that {@code throw}.
     *
     * @param from where the error arose, or passed through
     * @return the last such {@code throw}, or -1 when no {@code try} block around the place throws it again
     */
    int thrownAgain(final int from) {
        int at = from;
        int found = -1;
        while (true) {
            final int block = tryAround(at);
            final int next = block < 0 ? -1 : caughtAndThrown(block);
            if (next < 0) {
                return found;
            }
            found = next;
            at = next;
        }
    }

    /**
     * Where the value built at a place is thrown: by the {@code throw} it is the operand of, or, when it is what
     * a variable is given, by the only {@code throw} of that variable in the same function.
     *
     * @param from where the expression that builds the value starts
     * @param end  where it ends
     * @return the {@code throw}, or -1 when the source does not tell
     */
    int thrownFrom(final int from, final int end) {
        final int after = scan.firstFrom(end);
        if (after < text.length() && ";,}".indexOf(text.charAt(after)) < 0 && !breaksBefore(end, after)) {
            return -1;
        }
        final int before = scan.lastBefore(from);
        if (before < 0) {
            return -1;
        }
        if ("throw".equals(scan.wordBefore(from))) {
            return before - 4;
        }
        if (!scan.assignsAt(before)) {
            return -1;
        }
        final int last = scan.lastBefore(before);
        if (last < 0 || !JavaScriptSourceScan.isNamePart(text.charAt(last))) {
            return -1;
        }
        final int start = scan.nameStart(last);
        final int owner = scan.lastBefore(start);
        if (owner >= 0 && text.charAt(owner) == '.') {
            return -1;
        }
        return onlyThrowOf(text.substring(start, last + 1), 0, text.length(), scan.functionAround(from));
    }

    /** Whether what a function returns starts at a place: the word before it is {@code return}. */
    boolean isReturned(final int from) {
        return "return".equals(scan.wordBefore(from));
    }

    private boolean breaksBefore(final int from, final int to) {
        return text.substring(Math.min(from, to), to).indexOf('\n') >= 0;
    }

    /** The brace of the nearest {@code try} block around a place, in the same function, or -1. */
    private int tryAround(final int place) {
        int at = place;
        while (true) {
            final int brace = scan.braceAround(at);
            if (brace < 0 || scan.opensFunction(brace)) {
                return -1;
            }
            if ("try".equals(scan.wordBefore(brace))) {
                return brace;
            }
            at = brace;
        }
    }

    /** The only {@code throw} of its parameter in the {@code catch} clause of a {@code try} block, or -1. */
    private int caughtAndThrown(final int block) {
        final int end = scan.closing(block);
        if (end < 0) {
            return -1;
        }
        final int word = scan.firstFrom(end + 1);
        if (!text.startsWith("catch", word)) {
            return -1;
        }
        final int open = scan.firstFrom(word + 5);
        if (open >= text.length() || text.charAt(open) != '(' || scan.closing(open) < 0) {
            return -1;
        }
        final String parameter = text.substring(open + 1, scan.closing(open)).trim();
        final int body = scan.firstFrom(scan.closing(open) + 1);
        if (parameter.isEmpty() || body >= text.length() || text.charAt(body) != '{' || scan.closing(body) < 0) {
            return -1;
        }
        for (int i = 0; i < parameter.length(); i++) {
            if (!JavaScriptSourceScan.isNamePart(parameter.charAt(i))) {
                return -1;
            }
        }
        return onlyThrowOf(parameter, body + 1, scan.closing(body), scan.functionAround(body + 1));
    }

    /**
     * The only statement between two places that throws a name, in a given function, or -1 when none does or
     * several do.
     */
    private int onlyThrowOf(final String name, final int from, final int to, final int function) {
        int found = -1;
        int i = from;
        while (i < to) {
            if (!scan.startsName(i)) {
                i++;
                continue;
            }
            final int end = scan.nameEnd(i);
            if (scan.isWord(i, end, "throw") && throwsName(end, name) && scan.functionAround(i) == function) {
                if (found >= 0) {
                    return -1;
                }
                found = i;
            }
            i = end;
        }
        return found;
    }

    /** Whether what follows a {@code throw} is a name alone, in parentheses or not. */
    private boolean throwsName(final int from, final String name) {
        int at = scan.firstFrom(from);
        int wrapped = 0;
        while (at < text.length() && text.charAt(at) == '(') {
            wrapped++;
            at = scan.firstFrom(at + 1);
        }
        if (!scan.startsName(at) || !scan.isWord(at, scan.nameEnd(at), name)) {
            return false;
        }
        final int nameEnd = scan.nameEnd(at);
        at = scan.firstFrom(nameEnd);
        while (wrapped > 0 && at < text.length() && text.charAt(at) == ')') {
            wrapped--;
            at = scan.firstFrom(at + 1);
        }
        return wrapped == 0 && (at >= text.length() || text.charAt(at) == ';' || text.charAt(at) == '}'
            || breaksBefore(nameEnd, at));
    }
}

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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The words the account's JavaScript engine gives an error, for an error this engine words its own way
 * (live-verified, each pair). Applied to the sentence an uncaught error reaches SQL with; an error a handler
 * catches itself still reads this engine's words. Where the account's sentence names a value or the function
 * called, it is read from the source of the failing call, and a sentence that cannot be completed that way keeps
 * this engine's words.
 */
final class JavaScriptFaultWording {

    private static final Pattern FAILED_READ =
        Pattern.compile("TypeError: Cannot read property '(.*)' of (null|undefined)");
    private static final Pattern FAILED_WRITE =
        Pattern.compile("TypeError: Cannot set property '(.*)' of (null|undefined)");
    private static final Pattern NOT_DEFINED = Pattern.compile("ReferenceError: (.*) is not defined");
    private static final Pattern NOT_A_FUNCTION = Pattern.compile("TypeError: (.*) is not a function");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("-?(\\d+(\\.\\d+)?|Infinity)");
    private static final Pattern NOT_AN_OBJECT = Pattern.compile("TypeError: (.*) is not an Object");
    private static final Pattern TEXT = Pattern.compile("'[^'\\\\]*'|\"[^\"\\\\]*\"");
    private static final Pattern NOT_ITERABLE = Pattern.compile("TypeError: (.*) is not iterable", Pattern.DOTALL);
    private static final Pattern KEYED_BY_TEXT = Pattern.compile("\\[\"([A-Za-z_$][A-Za-z0-9_$]*)\"\\]");

    /** Sentences that differ by a fixed text only: this engine's, then the account's. */
    private static final String[] FIXED = {
        "RangeError: toFixed() fraction digits need to be in range 0-100",
        "RangeError: toFixed() digits argument must be between 0 and 100",
        "RangeError: toString() expects radix in range 2-36",
        "RangeError: toString() radix argument must be between 2 and 36",
        "RangeError: toExponential() fraction digits need to be in range 0-100",
        "RangeError: toExponential() argument must be between 0 and 100",
        "TypeError: Cannot mix BigInt and other types, use explicit conversions.",
        "TypeError: Cannot mix BigInt and other types, use explicit conversions",
        "URIError: illegal escape sequence", "URIError: URI malformed",
        "RangeError: time value is not a finite number", "RangeError: Invalid time value",
        "TypeError: Right-hand-side of instanceof is not an object",
        "TypeError: Right-hand side of 'instanceof' is not an object",
        "TypeError: Right-hand-side of instanceof is not callable",
        "TypeError: Right-hand side of 'instanceof' is not callable",
        "TypeError: handler expected to be an object",
        "TypeError: Cannot create proxy with a non-object as target or handler",
        "TypeError: target expected to be an object",
        "TypeError: Cannot create proxy with a non-object as target or handler",
        "RangeError: BigInt exponent must not be negative", "RangeError: Exponent must be positive",
    };

    /** Sentences that differ around one value: this engine's pattern, then the account's with $1 for the value. */
    private static final String[] AROUND = {
        "TypeError: Assignment to constant \"(.*)\"", "TypeError: Assignment to constant variable.",
        // The prelude words a formatter's own refusal already; only a sentence it did not reach is worded here.
        "RangeError: Invalid time zone (?!specified: )(.*)", "RangeError: Invalid time zone specified: $1",
        "TypeError: Cannot add new property \"(.*)\" to non-extensible .*",
        "TypeError: Cannot add property $1, object is not extensible",
        "TypeError: Not a symbol: (.*)", "TypeError: $1 is not a symbol",
        "RangeError: Language tag is not well-formed: (.*)", "RangeError: Invalid language tag: $1",
        "SyntaxError: Cannot convert (.*) to a BigInt\\.", "SyntaxError: Cannot convert $1 to a BigInt",
        "TypeError: (Cannot .* private member .* from an object whose class did not declare it)\\.",
        "TypeError: $1",
    };

    private JavaScriptFaultWording() {
    }

    /**
     * @param message the engine's sentence, {@code Name: text}
     * @param section the source of what failed: the failing expression, or the call that reached a builtin
     * @param line    the body line the failure is on
     * @param body    the handler's body
     * @param bracket the bracket the failing expression is written in, or a blank
     * @return the account's sentence
     */
    static String of(final String message, final String section, final String line, final String body,
                     final char bracket) {
        final Matcher read = FAILED_READ.matcher(message);
        if (read.matches()) {
            return failedRead(read.group(1), read.group(2), section, line);
        }
        final Matcher write = FAILED_WRITE.matcher(message);
        if (write.matches()) {
            return "TypeError: Cannot set properties of " + write.group(2) + " (setting '" + write.group(1) + "')";
        }
        for (int i = 0; i < FIXED.length; i += 2) {
            if (FIXED[i].equals(message)) {
                return FIXED[i + 1];
            }
        }
        for (int i = 0; i < AROUND.length; i += 2) {
            final Matcher around = Pattern.compile(AROUND[i], Pattern.DOTALL).matcher(message);
            if (around.matches()) {
                return AROUND[i + 1].replace("$1", around.group(1));
            }
        }
        final JavaScriptSourceScan scan = new JavaScriptSourceScan(section);
        final Matcher iterated = NOT_ITERABLE.matcher(message);
        if (iterated.matches() && section.startsWith("...") && bracket == '(') {
            return "TypeError: Spread syntax requires ...iterable[Symbol.iterator] to be a function";
        }
        if (iterated.matches() && section.matches("(?s)new (Weak)?(Set|Map)\\(.*")) {
            // A collection tells the kind of what it was given, and the value of a number or a boolean.
            final String value = iterated.group(1);
            final String kind = PLAIN_NUMBER.matcher(value).matches() || "NaN".equals(value) ? "number " + value
                : "true".equals(value) || "false".equals(value) ? "boolean " + value : "object";
            return "TypeError: " + kind + " is not iterable (cannot read property Symbol(Symbol.iterator))";
        }
        if ("TypeError: Cannot convert a BigInt value to a number".equals(message) && scan.rootOperator() >= 0) {
            return "TypeError: Cannot mix BigInt and other types, use explicit conversions";
        }
        final String argument = scan.soleArgument();
        final boolean number = argument != null && PLAIN_NUMBER.matcher(argument).matches();
        if ("RangeError: illegal repeat count".equals(message) && number) {
            return "RangeError: Invalid count value: " + argument;
        }
        if ("RangeError: BigInt out of range".equals(message) && number && section.startsWith("BigInt(")) {
            return "RangeError: The number " + argument + " cannot be converted to a BigInt because it is not an"
                + " integer";
        }
        if ("RangeError: index is negative".equals(message) && section.startsWith("new ArrayBuffer(")) {
            return "RangeError: Invalid array buffer length";
        }
        if ("RangeError: index is negative".equals(message) && number
                && section.matches("new [A-Za-z0-9]+Array\\(.*")) {
            return "RangeError: Invalid typed array length: " + argument;
        }
        if ("TypeError: called on non-object".equals(message) && scan.calledName() != null) {
            return "TypeError: " + scan.calledName() + " called on non-object";
        }
        if (message.startsWith("TypeError: Iterator result ") && message.endsWith(" is not an object")
                && section.matches("new (Weak)?Map\\(.*")) {
            return "TypeError: Iterator value " + message.substring("TypeError: Iterator result ".length(),
                message.length() - " is not an object".length()) + " is not an entry object";
        }
        if ("TypeError: cannot create promise: executor not callable".equals(message) && number
                && section.startsWith("new Promise(")) {
            return "TypeError: Promise resolver " + argument + " is not a function";
        }
        final Matcher searched = NOT_AN_OBJECT.matcher(message);
        final int in = searched.matches() ? scan.rootOperator() : -1;
        final String key = in <= 0 ? "" : section.substring(section.startsWith("return ") ? 7 : 0, in).trim();
        if (in > 0 && section.startsWith("in", in) && TEXT.matcher(key).matches()) {
            return "TypeError: Cannot use 'in' operator to search for '" + key.substring(1, key.length() - 1)
                + "' in " + searched.group(1);
        }
        final Matcher called = NOT_A_FUNCTION.matcher(message);
        if (called.matches() && scan.calledName() != null && called.group(1).startsWith("(intermediate value)")) {
            return "TypeError: " + scan.calledName() + " is not a function";
        }
        if (called.matches() && PLAIN_NUMBER.matcher(called.group(1)).matches()
                && called.group(1).equals(argument)) {
            // A builtin that was handed a number to call tells its kind.
            return "TypeError: number " + argument + " is not a function";
        }
        if (called.matches()) {
            // What is called is told as it is written: this by its word, a member read by a text key as a member.
            final String written = called.group(1).startsWith("<this>") ? "this" + called.group(1).substring(6)
                : called.group(1);
            return "TypeError: " + KEYED_BY_TEXT.matcher(written).replaceAll(".$1") + " is not a function";
        }
        final Matcher name = NOT_DEFINED.matcher(message);
        if (name.matches() && Pattern.compile("\\b(let|const|class)\\s+" + Pattern.quote(name.group(1)) + "\\b")
                .matcher(body).find()) {
            // A name declared further on in the body is there, and not yet ready.
            return "ReferenceError: Cannot access '" + name.group(1) + "' before initialization";
        }
        return message;
    }

    /**
     * A failed read: of a property, of the iterator a spread or an array pattern asks for, or of a property an
     * object pattern takes out of what follows its {@code =}.
     */
    private static String failedRead(final String property, final String value, final String section,
                                     final String line) {
        if ("Symbol(Symbol.iterator)".equals(property)) {
            return "TypeError: " + value + " is not iterable";
        }
        if (section.equals(property)) {
            final int pattern = line.indexOf('}', line.indexOf(section));
            final int assigned = pattern < 0 ? -1 : line.indexOf('=', pattern);
            if (assigned >= 0) {
                int end = line.indexOf(';', assigned);
                if (end < 0) {
                    end = line.length();
                }
                final String source = line.substring(assigned + 1, end).trim();
                if (!source.isEmpty()) {
                    return "TypeError: Cannot destructure property '" + property + "' of '" + source + "' as it is "
                        + value + ".";
                }
            }
        }
        return "TypeError: Cannot read properties of " + value + " (reading '" + property + "')";
    }
}

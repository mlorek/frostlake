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

package dev.frostlake.functions.scalar;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * The BARE WORDS a JSON document may carry, and the one thing that tells them apart: a word with a COLON
 * after it is an attribute NAME, and any other word is a KEYWORD.
 *
 * <p>Live-verified. An unquoted NAME is a plain ASCII identifier — {@code [A-Za-z_][A-Za-z0-9_]*} — and
 * nothing wider. {@code {a:1}}, {@code {_:1}}, {@code {a_b:1}} and {@code {a1b:1}} are read and the case
 * is kept; a leading digit, a {@code $}, a {@code @}, a {@code /} and any non-ASCII letter are all
 * refused. That narrowness is the whole reason the reader does this itself: Jackson's unquoted-name rule
 * follows JavaScript identifiers, so it would also read {@code {$a$:1}}, which Snowflake REFUSES — and
 * accepting a document live rejects is the worse of the two errors.
 *
 * <p>A word in NAME position is a name whatever it spells, so {@code {true:1}}, {@code {null:1}} and
 * {@code {nan:1}} are objects with those attribute names rather than keywords.
 *
 * <p>Everywhere else a word is a KEYWORD, matched CASE-INSENSITIVELY ({@code TRUE}, {@code tRuE},
 * {@code NAN}, {@code Inf} …):
 *
 * <pre>
 *   true / false   -&gt; BOOLEAN            null      -&gt; the JSON null
 *   undefined      -&gt; the undefined      nan       -&gt; DOUBLE NaN
 *   inf / infinity -&gt; DOUBLE Infinity
 * </pre>
 *
 * <p>A SIGN may lead the non-finite words — {@code -inf} and {@code -Infinity} are negative, {@code +inf}
 * and {@code +NaN} positive — with one asymmetry that is measured rather than reasoned: {@code +NaN} is
 * read and <b>{@code -NaN} is refused</b>. Nothing else is a keyword, so {@code infin}, {@code Infinit}
 * and {@code None} keep the {@code unknown keyword "X", pos N} refusal, at the word as written.
 *
 * <p>Neither NaN nor an infinity has a BigDecimal, and the reader builds BigDecimal for floats so a
 * NUMBER(38,0) survives. So rather than relaxing the parser, the non-finite words are rewritten to a
 * placeholder before parsing and {@link #restore(JsonNode)} turns each one into its double afterwards —
 * the same route the bare {@code undefined} token already takes. Names are quoted in the same pass, which
 * also keeps a name out of the keyword rules: {@code {nan:1}} is quoted before anything looks for a
 * keyword, so the placeholder can never end up as an attribute name.
 */
public final class JsonKeywords {

    /** NUL is the one character a Snowflake VARCHAR cannot hold, so no real string can collide. */
    private static final char DELIMITER = (char) 0;

    private static final String NAN = DELIMITER + "NaN" + DELIMITER;
    private static final String POSITIVE = DELIMITER + "Inf" + DELIMITER;
    private static final String NEGATIVE = DELIMITER + "-Inf" + DELIMITER;

    /** The delimiter as it stands in the TEXT, before the parser reads the escape. */
    private static final String ESCAPED_DELIMITER = "\\u0000";

    private static final String NAN_LITERAL = "\"\\u0000NaN\\u0000\"";
    private static final String POSITIVE_LITERAL = "\"\\u0000Inf\\u0000\"";
    private static final String NEGATIVE_LITERAL = "\"\\u0000-Inf\\u0000\"";

    private JsonKeywords() {
    }

    /**
     * The document with every keyword written the one way the parser reads it: the canonical words folded
     * to lower case in place, and each non-finite word replaced by its placeholder. Text whose keywords are
     * already canonical is returned unchanged.
     */
    public static String normalize(final String text) {
        if (text == null) {
            return null;
        }
        StringBuilder out = null;
        // One char per open container: '[' an array, '{' an object waiting for its next attribute NAME,
        // '=' an object that has read the colon and is waiting for the value. That is all the state a bare
        // word needs to know which of the two things it is.
        final StringBuilder open = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (inString) {
                if (out != null) {
                    out.append(c);
                }
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            if (c == '[' || c == '{') {
                open.append(c);
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            if (c == ']' || c == '}') {
                if (open.length() > 0) {
                    open.setLength(open.length() - 1);
                }
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            if (c == ',' || c == ':') {
                if (open.length() > 0) {
                    final char top = open.charAt(open.length() - 1);
                    if (c == ':' && top == '{') {
                        open.setCharAt(open.length() - 1, '=');
                    } else if (c == ',' && top == '=') {
                        open.setCharAt(open.length() - 1, '{');
                    }
                }
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            final int wordStart = (c == '+' || c == '-') ? i + 1 : i;
            if (wordStart >= text.length() || !startsWord(text.charAt(wordStart))) {
                if (out != null) {
                    out.append(c);
                }
                continue;
            }
            int end = wordStart;
            while (end < text.length() && isWordChar(text.charAt(end))) {
                end++;
            }
            final char sign = wordStart == i ? '\0' : c;
            final String word = text.substring(wordStart, end);
            final boolean isName = sign == '\0' && expectsName(open) && colonFollows(text, end);
            final String replacement = isName ? '"' + word + '"' : replacementFor(word.toLowerCase(), sign);
            if (replacement == null) {
                if (out != null) {
                    out.append(text, i, end);
                }
                i = end - 1;
                continue;
            }
            if (out == null) {
                out = new StringBuilder(text.length() + 16);
                out.append(text, 0, i);
            }
            out.append(replacement);
            i = end - 1;
        }
        return out == null ? text : out.toString();
    }

    /** Whether the innermost container is an object still waiting for an attribute name. */
    private static boolean expectsName(final StringBuilder open) {
        return open.length() > 0 && open.charAt(open.length() - 1) == '{';
    }

    /** Whether the next thing after this word, whitespace aside, is the colon that makes it a name. */
    private static boolean colonFollows(final String text, final int from) {
        int j = from;
        while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
            j++;
        }
        return j < text.length() && text.charAt(j) == ':';
    }

    /** The characters an unquoted attribute name may START with. */
    private static boolean startsWord(final char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_';
    }

    /** The characters an unquoted attribute name may CONTINUE with. */
    private static boolean isWordChar(final char c) {
        return startsWord(c) || (c >= '0' && c <= '9');
    }

    /**
     * How a keyword is written for the parser, or null where the word is not one — including a word that is
     * already canonical, which needs no rewriting at all.
     */
    private static String replacementFor(final String word, final char sign) {
        if (word.equals("nan")) {
            // Measured: a NEGATIVE NaN is refused where a positive one is read.
            return sign == '-' ? null : NAN_LITERAL;
        }
        if (word.equals("inf") || word.equals("infinity")) {
            return sign == '-' ? NEGATIVE_LITERAL : POSITIVE_LITERAL;
        }
        if (sign != '\0') {
            return null;
        }
        if (word.equals("true") || word.equals("false") || word.equals("null") || word.equals("undefined")) {
            return word;
        }
        return null;
    }

    /**
     * Whether this parsed tree may still hold a placeholder, read off the text handed to the parser. The
     * text carries the delimiter ESCAPED — six characters, not the NUL itself, which only appears once the
     * parser has read the string literal.
     */
    public static boolean mayHoldPlaceholder(final String parsedText) {
        return parsedText != null && parsedText.contains(ESCAPED_DELIMITER);
    }

    /** Turns every placeholder left by {@link #normalize(String)} into the double it stands for. */
    public static JsonNode restore(final JsonNode node) {
        if (node == null) {
            return null;
        }
        final Double direct = valueOf(node);
        if (direct != null) {
            return new DoubleNode(direct.doubleValue());
        }
        if (node instanceof ArrayNode) {
            final ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                final Double element = valueOf(array.get(i));
                if (element != null) {
                    array.set(i, new DoubleNode(element.doubleValue()));
                } else {
                    restore(array.get(i));
                }
            }
            return array;
        }
        if (node instanceof ObjectNode) {
            final ObjectNode object = (ObjectNode) node;
            final List<String> names = new ArrayList<String>();
            final Iterator<Map.Entry<String, JsonNode>> it = object.properties().iterator();
            while (it.hasNext()) {
                names.add(it.next().getKey());
            }
            for (final String name : names) {
                final Double member = valueOf(object.get(name));
                if (member != null) {
                    object.set(name, new DoubleNode(member.doubleValue()));
                } else {
                    restore(object.get(name));
                }
            }
            return object;
        }
        return node;
    }

    /** The double a placeholder stands for, or null where this node is not one. */
    private static Double valueOf(final JsonNode node) {
        if (node == null || !node.isString()) {
            return null;
        }
        final String text = node.stringValue();
        if (NAN.equals(text)) {
            return Double.valueOf(Double.NaN);
        }
        if (POSITIVE.equals(text)) {
            return Double.valueOf(Double.POSITIVE_INFINITY);
        }
        if (NEGATIVE.equals(text)) {
            return Double.valueOf(Double.NEGATIVE_INFINITY);
        }
        return null;
    }
}

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
package dev.frostlake.functions.scalar.semistructured;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;

/**
 * What is WRONG with an XML document, in Snowflake's own words — the XML twin of {@link JsonFaultReader}.
 *
 * <p>★ THE SENTENCE IS DECIDED HERE, not translated from a parser's message. A Java parser's wording
 * ("XML document structures must start and end within the same entity.") shares nothing with live's
 * ("missing closing tags: &lt;/a&gt;, pos 3") — not the words, not the position, not even which fault
 * is reported first — so a translation layer could never line the two up. The scanner walks the text
 * itself, which is the only way the positions can be right.
 *
 * <p>★ THE ANCHOR IS PER-SENTENCE, and each was measured rather than assumed:
 * <ul>
 *   <li>an UNCLOSED document reports at the last character consumed, and lists every open tag
 *       INNERMOST FIRST — {@code <a><b><c>} is "missing closing tags: &lt;/c&gt;&lt;/b&gt;&lt;/a&gt;";</li>
 *   <li>a MISMATCHED closer reports at its own last character;</li>
 *   <li>a closer with nothing open reports at the SLASH, not at the tag's end — {@code </abcdef>} and
 *       {@code </a>} both say pos 2, so the length plays no part;</li>
 *   <li>a bare {@code <} at the end reports at the {@code <} itself, and {@code <>} at the character
 *       after it;</li>
 *   <li>content before any element is always pos 1, wherever the content ends;</li>
 *   <li>the two whole-document faults — more than one document, garbage after one — carry NO
 *       position at all.</li>
 * </ul>
 *
 * <p>★ AND THE POSITION IS COUNTED FROM THE UNTRIMMED TEXT, with a {@code line N,} clause past the
 * first line, exactly as the JSON reader counts.
 */
final class XmlFaultReader {

    /** The entities a document may name without declaring them. */
    private static final Set<String> KNOWN_ENTITIES = Set.of("amp", "lt", "gt", "quot", "apos");

    private final String text;
    private int at;

    private XmlFaultReader(final String text) {
        this.text = text;
    }

    /** The fault in a document, or null when it is well-formed. */
    static String faultOf(final String document) {
        return new XmlFaultReader(document).scan();
    }

    private String scan() {
        final Deque<String> open = new ArrayDeque<String>();
        boolean rootSeen = false;
        while (at < text.length()) {
            final char c = text.charAt(at);
            if (c == '<') {
                if (at + 1 >= text.length()) {
                    return "prematurely terminated XML document in a tag, " + positionAt(at);
                }
                final char next = text.charAt(at + 1);
                if (next == '>') {
                    return "missing tag name after <, " + positionAt(at + 1);
                }
                if (next == '!' || next == '?') {
                    final String skipped = skipSpecial();
                    if (skipped != null) {
                        return skipped;
                    }
                    continue;
                }
                if (next == '/') {
                    if (open.isEmpty()) {
                        return "closing tag with no opening tags, " + positionAt(at + 1);
                    }
                    final int slash = at + 1;
                    at += 2;
                    final String name = readName();
                    final String closed = skipTo('>');
                    if (closed != null) {
                        return closed;
                    }
                    if (!name.equals(open.peek())) {
                        // ★ A closer that matches something DEEPER on the stack is not an orphan: the
                        // tags standing above it are what went unclosed, and live names those instead.
                        // Only a name open nowhere gets the orphan sentence.
                        if (open.contains(name)) {
                            return "missing closing tags: " + closersAbove(open, name) + ", "
                                + positionAt(at - 1);
                        }
                        return "no opening tag for </" + name + ">, " + positionAt(at - 1);
                    }
                    open.pop();
                    if (open.isEmpty()) {
                        rootSeen = true;
                    }
                    continue;
                }
                if (open.isEmpty() && rootSeen) {
                    return "more than one document in the input";
                }
                at++;
                final String name = readName();
                final String attributes = readAttributes();
                if (attributes != null) {
                    return attributes;
                }
                if (at < text.length() && text.charAt(at) == '/') {
                    at++;
                    if (open.isEmpty()) {
                        rootSeen = true;
                    }
                } else {
                    open.push(name);
                }
                final String closed = skipTo('>');
                if (closed != null) {
                    return closed;
                }
                continue;
            }
            if (Character.isWhitespace(c)) {
                at++;
                continue;
            }
            if (open.isEmpty()) {
                // Content outside any element: before the first tag it is not an element at all, and
                // after the last one it is garbage — one of the two positionless sentences.
                return rootSeen ? "garbage after valid input document" : "not an XML element, pos 1";
            }
            final String entity = readTextContent();
            if (entity != null) {
                return entity;
            }
        }
        if (!open.isEmpty()) {
            final StringBuilder closers = new StringBuilder();
            for (final String name : open) {
                closers.append("</").append(name).append('>');
            }
            return "missing closing tags: " + closers + ", " + positionAt(text.length() - 1);
        }
        return null;
    }

    /** The open tags standing ABOVE a name on the stack, innermost first, as their closing tags. */
    private static String closersAbove(final Deque<String> open, final String name) {
        final StringBuilder closers = new StringBuilder();
        for (final String candidate : open) {
            if (candidate.equals(name)) {
                break;
            }
            closers.append("</").append(candidate).append('>');
        }
        return closers.toString();
    }

    /** A comment, a CDATA section or a declaration — skipped whole, since none can hold an element. */
    private String skipSpecial() {
        final String terminator;
        if (text.startsWith("<!--", at)) {
            terminator = "-->";
        } else if (text.startsWith("<![CDATA[", at)) {
            terminator = "]]>";
        } else {
            terminator = text.charAt(at + 1) == '?' ? "?>" : ">";
        }
        final int end = text.indexOf(terminator, at + 2);
        if (end < 0) {
            return "prematurely terminated XML document in a tag, " + positionAt(at);
        }
        at = end + terminator.length();
        return null;
    }

    /** An element or attribute name: everything up to whitespace, {@code =}, {@code /} or {@code >}. */
    private String readName() {
        final int start = at;
        while (at < text.length() && !Character.isWhitespace(text.charAt(at))
                && text.charAt(at) != '>' && text.charAt(at) != '/' && text.charAt(at) != '=') {
            at++;
        }
        return text.substring(start, at);
    }

    /**
     * The attribute list of an opening tag.
     *
     * <p>★ AN UNQUOTED VALUE IS LEGAL — {@code <a b=1>} parses live and comes back as
     * {@code <a b="1"></a>} — so this is not only about wording: the reader has to ACCEPT the value
     * before any sentence can be produced. What is not legal is a bare name with no value at all, or
     * a second attribute whose name does not start like one.
     */
    private String readAttributes() {
        while (true) {
            skipWhitespace();
            if (at >= text.length()) {
                return null;
            }
            final char c = text.charAt(at);
            if (c == '>' || c == '/') {
                return null;
            }
            if (!isNameStart(c)) {
                return "bad character in attribute name: '" + c + "', " + positionAt(at);
            }
            readName();
            skipWhitespace();
            if (at >= text.length() || text.charAt(at) != '=') {
                return "missing attribute value, " + positionAt(at);
            }
            at++;
            skipWhitespace();
            if (at >= text.length()) {
                return "missing attribute value, " + positionAt(text.length() - 1);
            }
            final char quote = text.charAt(at);
            if (quote == '"' || quote == '\'') {
                at++;
                final int end = text.indexOf(quote, at);
                if (end < 0) {
                    return "prematurely terminated XML document in a tag, " + positionAt(at);
                }
                at = end + 1;
            } else if (quote == '>' || quote == '/') {
                return "missing attribute value, " + positionAt(at);
            } else {
                while (at < text.length() && !Character.isWhitespace(text.charAt(at))
                        && text.charAt(at) != '>' && text.charAt(at) != '/') {
                    at++;
                }
            }
        }
    }

    /** Text between tags, checking the entities it names. */
    private String readTextContent() {
        while (at < text.length() && text.charAt(at) != '<') {
            if (text.charAt(at) == '&') {
                final int end = text.indexOf(';', at);
                if (end < 0) {
                    at++;
                    continue;
                }
                final String name = text.substring(at + 1, end);
                if (!name.startsWith("#") && !KNOWN_ENTITIES.contains(name)) {
                    return "unknown entity &" + name + ";, " + positionAt(end);
                }
                at = end + 1;
                continue;
            }
            at++;
        }
        return null;
    }

    private String skipTo(final char closer) {
        while (at < text.length() && text.charAt(at) != closer) {
            at++;
        }
        if (at >= text.length()) {
            return "prematurely terminated XML document in a tag, " + positionAt(text.length() - 1);
        }
        at++;
        return null;
    }

    private void skipWhitespace() {
        while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
            at++;
        }
    }

    private static boolean isNameStart(final char c) {
        return Character.isLetter(c) || c == '_' || c == ':';
    }

    /** A 1-based column within its line, with a {@code line N,} clause only past the first line. */
    private String positionAt(final int index) {
        int line = 1;
        int lineStart = 0;
        for (int i = 0; i < index && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                lineStart = i + 1;
            }
        }
        final String column = "pos " + (index - lineStart + 1);
        return line > 1 ? "line " + line + ", " + column : column;
    }
}

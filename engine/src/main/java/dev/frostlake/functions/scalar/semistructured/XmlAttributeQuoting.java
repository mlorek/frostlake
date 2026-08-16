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

/**
 * An UNQUOTED attribute value, quoted — because live accepts one and a strict parser does not.
 *
 * <p>{@code <a b=1/>} is a valid document on a real account and comes back as {@code <a b="1"></a>},
 * so the quotes are supplied by the reader rather than demanded of the writer. This runs only after
 * {@link XmlFaultReader} has approved the document, so it can assume the shape it walks is sound and
 * confine itself to the one thing it is for.
 */
final class XmlAttributeQuoting {

    private XmlAttributeQuoting() {
    }

    /** The document with every unquoted attribute value wrapped in double quotes. */
    static String quoted(final String text) {
        final StringBuilder out = new StringBuilder(text.length() + 8);
        int at = 0;
        boolean insideTag = false;
        while (at < text.length()) {
            final char c = text.charAt(at);
            if (!insideTag) {
                out.append(c);
                if (c == '<' && at + 1 < text.length() && text.charAt(at + 1) != '!'
                        && text.charAt(at + 1) != '?' && text.charAt(at + 1) != '/') {
                    insideTag = true;
                }
                at++;
                continue;
            }
            if (c == '>') {
                insideTag = false;
                out.append(c);
                at++;
                continue;
            }
            if (c == '"' || c == '\'') {
                final int end = text.indexOf(c, at + 1);
                if (end < 0) {
                    out.append(text, at, text.length());
                    return out.toString();
                }
                out.append(text, at, end + 1);
                at = end + 1;
                continue;
            }
            if (c == '=') {
                out.append(c);
                at++;
                while (at < text.length() && Character.isWhitespace(text.charAt(at))) {
                    out.append(text.charAt(at));
                    at++;
                }
                if (at < text.length() && text.charAt(at) != '"' && text.charAt(at) != '\'') {
                    final int start = at;
                    while (at < text.length() && !Character.isWhitespace(text.charAt(at))
                            && text.charAt(at) != '>' && text.charAt(at) != '/') {
                        at++;
                    }
                    out.append('"').append(text, start, at).append('"');
                }
                continue;
            }
            out.append(c);
            at++;
        }
        return out.toString();
    }
}

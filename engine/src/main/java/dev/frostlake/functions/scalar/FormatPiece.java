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

/**
 * One piece of a Snowflake date/time format model after the scan: either an ELEMENT — the canonical
 * upper-case spelling of a recognised element (YYYY, MON, HH24, FF3, TZH:TZM, …) — or a run of
 * LITERAL text: separators, unrecognised letters, the contents of a double-quoted run.
 */
public final class FormatPiece {
    private final String element;
    private final String literal;

    private FormatPiece(final String element, final String literal) {
        this.element = element;
        this.literal = literal;
    }

    /** An element piece, by its canonical spelling. */
    public static FormatPiece element(final String element) {
        return new FormatPiece(element, null);
    }

    /** A literal-text piece. */
    public static FormatPiece literal(final String literal) {
        return new FormatPiece(null, literal);
    }

    /** Whether this piece is a format element rather than literal text. */
    public boolean isElement() {
        return element != null;
    }

    /** The element's canonical spelling, or null for a literal piece. */
    public String element() {
        return element;
    }

    /** The literal text, or null for an element piece. */
    public String literal() {
        return literal;
    }
}

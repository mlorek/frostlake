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

package dev.frostlake.testkit;

import java.util.Locale;

/**
 * How a semi-structured cell is read for comparison.
 *
 * <p>A client is handed a VARIANT, OBJECT or ARRAY cell as its JSON TEXT — a string's own quotes
 * included — which is what the account's drivers do and what a real caller should see. The suites,
 * though, record the VALUE: {@code a} rather than {@code "a"}, and an object as its own text rather
 * than as a string holding that text. The embedded backend and the live replay harness already read
 * a cell that way, so a transport that carries JSON text has to decode it once before comparing, or
 * the same case passes on one backend and fails on another for no difference anyone cares about.
 *
 * <p>Only a semi-structured COLUMN is decoded, and only one level: a cell that is a JSON string
 * becomes that string's content, which for a whole object or array is the object's own text. Any
 * other cell — a number, a boolean, text that is not JSON at all — is left exactly as it came.
 */
public final class SemiStructuredCells {

    private SemiStructuredCells() {
    }

    /**
     * Whether a column carries semi-structured values, read from the type the transport reports.
     *
     * @param typeName the column's declared type, in any case, or null
     * @return true for VARIANT, OBJECT and ARRAY
     */
    public static boolean isSemiStructured(final String typeName) {
        if (typeName == null) {
            return false;
        }
        final String upper = typeName.toUpperCase(Locale.ROOT);
        return "VARIANT".equals(upper) || "OBJECT".equals(upper) || "ARRAY".equals(upper);
    }

    /**
     * The value a semi-structured cell carries, as the suites record it.
     *
     * @param cell the cell as the transport delivered it, or null
     * @return the JSON string's content when the cell is one, and the cell unchanged otherwise
     */
    public static String value(final String cell) {
        if (cell == null) {
            return null;
        }
        final Object parsed;
        try {
            parsed = Json.parse(cell);
        } catch (final RuntimeException notJson) {
            // Text that is not JSON at all is a value in its own right.
            return cell;
        }
        return parsed instanceof String ? (String) parsed : cell;
    }
}

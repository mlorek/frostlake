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

import java.util.Arrays;

/**
 * Array HOLES — the elisions a JSON array may be written with, which Snowflake reads as {@code undefined}
 * elements instead of refusing the document.
 *
 * <p>Live-verified. A comma with no element before it opens a hole, and the bracket that closes an array
 * closes one too — but only where the array holds a real element, so an array written entirely of commas
 * holds one element per comma rather than one more:
 *
 * <pre>
 *   [1,,2]  -&gt; [1,undefined,2]              [1,,,2] -&gt; [1,undefined,undefined,2]
 *   [,1]    -&gt; [undefined,1]                [1,2,]  -&gt; [1,2,undefined]   (THREE elements, not two)
 *   [,]     -&gt; [undefined]                  []      -&gt; []
 *   [[1,,2],3] -&gt; [[1,undefined,2],3]
 * </pre>
 *
 * <p>The trailing comma is the cell to keep in view: it is not a refusal becoming an answer but a stored
 * array growing an element, because a lenient reader would otherwise drop it.
 *
 * <p>An OBJECT has no holes — {@code {"a":,"b":1}} and <code>{,"a":1}</code> are refused as a misplaced
 * comma — so only array levels are filled, and a trailing comma in an object stays the reader's own to
 * forgive.
 *
 * <p>Holes are written out as the bare {@code undefined} token, which leaves their placement rules to
 * {@link dev.frostlake.values.VariantUndefined}.
 */
public final class JsonArrayHoles {

    /** How a hole is written for the variant reader, which already knows the token and where it may stand. */
    private static final String HOLE = "undefined";

    /** The container nesting a document is expected to reach before the level stacks have to grow. */
    private static final int INITIAL_DEPTH = 16;

    private JsonArrayHoles() {
    }

    /**
     * The document with every array hole written out as an {@code undefined} token. Text holding no hole is
     * returned unchanged, and a document that is not JSON at all is left for the reader to refuse.
     */
    public static String fill(final String text) {
        if (!mayContainHole(text)) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length() + INITIAL_DEPTH);
        // Per nesting level: whether it is an array, whether the CURRENT slot holds an element, and whether
        // the level has EVER held one — the last is what tells [1,2,] (which grows a hole) from [,] (which
        // does not).
        boolean[] isArray = new boolean[INITIAL_DEPTH];
        boolean[] slotFilled = new boolean[INITIAL_DEPTH];
        boolean[] everFilled = new boolean[INITIAL_DEPTH];
        int depth = -1;
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (inString) {
                out.append(c);
                if (escaped) {
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '[' || c == '{') {
                if (depth >= 0) {
                    slotFilled[depth] = true;
                    everFilled[depth] = true;
                }
                depth++;
                if (depth == isArray.length) {
                    isArray = Arrays.copyOf(isArray, depth * 2);
                    slotFilled = Arrays.copyOf(slotFilled, depth * 2);
                    everFilled = Arrays.copyOf(everFilled, depth * 2);
                }
                isArray[depth] = c == '[';
                slotFilled[depth] = false;
                everFilled[depth] = false;
                out.append(c);
                continue;
            }
            if (c == ']' || c == '}') {
                if (depth >= 0) {
                    if (isArray[depth] && everFilled[depth] && !slotFilled[depth]) {
                        out.append(HOLE);
                    }
                    depth--;
                }
                out.append(c);
                continue;
            }
            if (c == ',') {
                if (depth >= 0) {
                    if (isArray[depth] && !slotFilled[depth]) {
                        out.append(HOLE);
                    }
                    slotFilled[depth] = false;
                }
                out.append(c);
                continue;
            }
            if (c == '"') {
                inString = true;
            }
            if (depth >= 0 && !Character.isWhitespace(c)) {
                slotFilled[depth] = true;
                everFilled[depth] = true;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * A cheap pre-check: no document holds a hole unless a comma is followed by another comma or by a
     * closing bracket, or an opening bracket is followed by a comma. It reads a string's contents too, which
     * only costs the scan below a document it will leave unchanged.
     */
    private static boolean mayContainHole(final String text) {
        if (text == null) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c != ',' && c != '[') {
                continue;
            }
            int j = i + 1;
            while (j < text.length() && Character.isWhitespace(text.charAt(j))) {
                j++;
            }
            if (j < text.length() && (text.charAt(j) == ',' || (c == ',' && text.charAt(j) == ']'))) {
                return true;
            }
        }
        return false;
    }
}

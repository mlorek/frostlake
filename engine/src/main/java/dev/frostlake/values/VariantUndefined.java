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

package dev.frostlake.values;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.NullNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The VARIANT {@code undefined} boundary — where a SQL NULL becomes an {@link UndefinedNode} and where the
 * sentinel must be told apart from a JSON null.
 *
 * <p>Live-verified on a real account. With {@code ac = ARRAY_CONSTRUCT(1,NULL,2)} (which
 * renders {@code [1,undefined,2]}) and {@code pj = PARSE_JSON('[1,null,2]')}:
 *
 * <pre>
 *   ARRAY_CONSTRUCT(1,NULL,2)  [1,NULL,2]  ARRAY_APPEND([1],NULL)  ARRAY_PREPEND([1],NULL)
 *   ARRAY_INSERT([1],4,9)      ARRAY_REPEAT(NULL,3)  TRANSFORM([1,2], x -&gt; NULL)  [** NULL]
 *       -&gt; every SQL NULL entering an ARRAY becomes `undefined`
 *   PARSE_JSON('[1,null,2]')   ARRAY_CONSTRUCT(PARSE_JSON('null'))  OBJECT_CONSTRUCT_KEEP_NULL('k',NULL)
 *       -&gt; keep the JSON null; TO_ARRAY(NULL) is SQL NULL, OBJECT_CONSTRUCT('k',NULL) is {}
 *
 *   TYPEOF(GET(ac,1))    -&gt; SQL NULL      TYPEOF(GET(pj,1))    -&gt; 'NULL_VALUE'
 *   GET(ac,1) IS NULL    -&gt; TRUE          GET(pj,1) IS NULL    -&gt; FALSE
 *   IS_NULL_VALUE(GET(ac,1)) -&gt; SQL NULL  IS_NULL_VALUE(GET(pj,1)) -&gt; TRUE
 *   TO_JSON(ac)          -&gt; [1,undefined,2]                    ac = pj -&gt; FALSE
 * </pre>
 *
 * <p>Because an {@code undefined} never escapes the variant layer as a value, the sentinel deliberately
 * reports {@link JsonNode#isNull()} — every consumer that has not heard of it reads SQL NULL, which is the
 * right answer. Only the sites that must distinguish the two nulls call {@link #isUndefined(JsonNode)}.
 *
 * <p>An {@code undefined} lives only in an ARRAY. As an OBJECT member or as a whole VARIANT it degrades:
 * live, {@code PARSE_JSON('{"a":undefined}')} is {@code {"a":null}} and {@code PARSE_JSON('undefined')} is
 * SQL NULL — both handled by {@link #restore(JsonNode)}.
 */
public final class VariantUndefined {

    /**
     * The delimiter around the parse-time placeholder. NUL is the one character a Snowflake VARCHAR cannot
     * hold, so a genuine string value can never collide with the placeholder.
     */
    private static final char DELIMITER = (char) 0;

    /**
     * The placeholder a bare {@code undefined} token is parsed as before {@link #restore(JsonNode)} turns it
     * into the sentinel. Jackson cannot parse the JavaScript token, so the reader rewrites it to this string
     * literal first.
     */
    private static final String MARKER = DELIMITER + UndefinedNode.TOKEN + DELIMITER;

    /** The escaped JSON string literal for {@link #MARKER}, substituted into the text before parsing. */
    private static final String MARKER_LITERAL = "\"\\u0000" + UndefinedNode.TOKEN + "\\u0000\"";

    private VariantUndefined() {
    }

    /** The {@code undefined} sentinel node. */
    public static JsonNode node() {
        return UndefinedNode.getInstance();
    }

    /** Whether this tree node is the {@code undefined} sentinel rather than a JSON null (or anything else). */
    public static boolean isUndefined(final JsonNode node) {
        return node == UndefinedNode.getInstance();
    }

    /**
     * The node an ARRAY element takes when it MOVES INTO an object: an {@code undefined} degrades to a JSON
     * null, everything else passes through. Live-verified:
     * {@code ARRAYS_ZIP(ARRAY_CONSTRUCT(1,NULL), ARRAY_CONSTRUCT(2,3))} is
     * {@code [{"$1":1,"$2":2},{"$1":null,"$2":3}]} and
     * {@code ARRAYS_TO_OBJECT(['a','b'], ARRAY_CONSTRUCT(1,NULL))} is {@code {"a":1,"b":null}}.
     */
    public static JsonNode asObjectMember(final JsonNode node) {
        return isUndefined(node) ? NullNode.getInstance() : node;
    }

    /**
     * The runtime value an ARRAY element binds to in a lambda or any other scalar context: an
     * {@code undefined} is SQL NULL, everything else is its typed semi-structured value. Live-verified
     * {@code REDUCE(ARRAY_CONSTRUCT(1,NULL,2), 0, (acc,x) -> acc + COALESCE(x::int,10))} is 13,
     * so the lambda saw SQL NULL for the middle element.
     */
    public static Object elementValue(final JsonNode node) {
        return node == null || isUndefined(node) ? null : VariantValue.ofNode(node);
    }

    /**
     * Parses JSON text that may carry Snowflake's bare {@code undefined} tokens. Text without one is handed
     * straight to {@code mapper}, so the ordinary path is untouched.
     */
    public static JsonNode readTree(final ObjectMapper mapper, final String text) {
        if (text == null) {
            return null;
        }
        if (!mayContainToken(text)) {
            return mapper.readTree(text);
        }
        return restore(mapper.readTree(markTokens(text)));
    }

    /** A cheap pre-check: the text cannot hold a bare {@code undefined} token unless it contains the word. */
    public static boolean mayContainToken(final String text) {
        return text != null && text.contains(UndefinedNode.TOKEN);
    }

    /**
     * Replaces every bare {@code undefined} token OUTSIDE of double-quoted strings with {@link #MARKER_LITERAL}
     * so a JSON parser accepts it. Word boundaries are honored ({@code myundefined} stays untouched) and text
     * inside string values is never rewritten, so {@code ["undefined"]} keeps its genuine string element.
     */
    public static String markTokens(final String text) {
        if (!mayContainToken(text)) {
            return text;
        }
        final StringBuilder out = new StringBuilder(text.length() + 16);
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
            if (c == '"') {
                inString = true;
                out.append(c);
                continue;
            }
            if (c == 'u' && text.startsWith(UndefinedNode.TOKEN, i)
                    && (i == 0 || !isWordChar(text.charAt(i - 1)))
                    && (i + UndefinedNode.TOKEN.length() >= text.length()
                        || !isWordChar(text.charAt(i + UndefinedNode.TOKEN.length())))) {
                out.append(MARKER_LITERAL);
                i += UndefinedNode.TOKEN.length() - 1;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    private static boolean isWordChar(final char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Turns every placeholder left by {@link #markTokens(String)} into the sentinel, applying Snowflake's
     * placement rules: an ARRAY element keeps {@code undefined}, an OBJECT member degrades to a JSON null
     * (live: {@code PARSE_JSON('{"a":undefined}')} is {@code {"a":null}}) and a whole-value {@code undefined}
     * is returned as the sentinel for the caller to read as SQL NULL (live: {@code PARSE_JSON('undefined')}
     * is SQL NULL).
     */
    public static JsonNode restore(final JsonNode node) {
        if (node == null) {
            return null;
        }
        if (isMarker(node)) {
            return UndefinedNode.getInstance();
        }
        if (node.isArray()) {
            final ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, restore(array.get(i)));
            }
            return array;
        }
        if (node.isObject()) {
            final ObjectNode object = (ObjectNode) node;
            final List<String> keys = new ArrayList<>();
            final Iterator<String> names = object.propertyNames().iterator();
            while (names.hasNext()) {
                keys.add(names.next());
            }
            for (final String key : keys) {
                final JsonNode restored = restore(object.get(key));
                object.set(key, isUndefined(restored) ? object.nullNode() : restored);
            }
            return object;
        }
        return node;
    }

    private static boolean isMarker(final JsonNode node) {
        return node.isTextual() && MARKER.equals(node.asString());
    }
}

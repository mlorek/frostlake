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

import dev.frostlake.functions.scalar.ArrayFunctionHelper;
import dev.frostlake.types.MapType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.VariantType;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/** Types and shared reads for the MAP built-in family (MAP_KEYS, MAP_SIZE, MAP_CAT and the rest). */
public final class MapFunctionHelper {

    /**
     * The MAP type a MAP-returning built-in declares. Live derives the RESULT's key and value types
     * from its arguments — {@code SYSTEM$TYPEOF(MAP_CAT(<MAP(VARCHAR,INT)>, <MAP(VARCHAR,INT)>))} is
     * {@code MAP(VARCHAR, NUMBER(38,0))} — which the registry's one nominal declaration per function
     * cannot express. What matters for the strict-argument rule is the FAMILY: it is a MAP, so
     * {@code MAP_KEYS(MAP_CAT(…))} and {@code MAP_SIZE(MAP_CONSTRUCT('a',1))} pass, matching live,
     * where a plain OBJECT declaration would have made every nested call an argument-type error.
     */
    public static final MapType MAP = new MapType(StringType.VARCHAR, VariantType.VARIANT);

    /**
     * The array type {@code MAP_KEYS} and {@code MAP_ENTRIES} return — a STRUCTURED array, not a plain
     * one. Live-verified: {@code SYSTEM$TYPEOF(MAP_KEYS(m))} is
     * {@code ARRAY(VARCHAR(16777216) NOT NULL)} and {@code SYSTEM$TYPEOF(MAP_ENTRIES(m))} is
     * {@code ARRAY(OBJECT(key VARCHAR NOT NULL, value NUMBER(38,0)))}, and the difference is visible to
     * callers — {@code ARRAY_TO_STRING(MAP_KEYS(m), ',')} is an argument-type error over that structured
     * array while the same call over a plain ARRAY joins, and {@code ARRAY_SIZE(MAP_KEYS(m))} works.
     */
    public static final StructuredArrayType KEY_ARRAY = new StructuredArrayType(VariantType.VARIANT);

    private MapFunctionHelper() {
    }

    /**
     * The MAP argument's body, or null when it is SQL NULL. Every function in the family answers NULL
     * for a NULL map (live: {@code MAP_KEYS(NULL::MAP(VARCHAR,INT))}, {@code MAP_SIZE}, {@code
     * MAP_ENTRIES}, {@code MAP_DELETE}, {@code MAP_INSERT}, {@code MAP_PICK} and {@code
     * MAP_CONTAINS_KEY} all return NULL), so they share this read.
     */
    public static JsonNode body(final Object value) {
        final JsonNode node = ArrayFunctionHelper.parseNode(value);
        return node != null && node.isObject() ? node : null;
    }

    /**
     * A map body's keys in Snowflake's order. Live renders a MAP and returns {@code MAP_KEYS} /
     * {@code MAP_ENTRIES} key-sorted by codepoint rather than by insertion — {@code
     * MAP_CONSTRUCT('Z','x','a','y','B','z')} is {@code {"B":"z","Z":"x","a":"y"}} and its keys come
     * back {@code ["B","Z","a"]} — which is exactly the ordering
     * {@link ArrayFunctionHelper#canonicalize} already imposes on every OBJECT.
     */
    public static List<String> sortedKeys(final JsonNode body) {
        final List<String> keys = new ArrayList<>();
        final Iterator<String> names = body.propertyNames().iterator();
        while (names.hasNext()) {
            keys.add(names.next());
        }
        Collections.sort(keys);
        return keys;
    }

    /**
     * A key argument as its member name, or null for SQL NULL. Frostlake stores a MAP as an OBJECT, so
     * every key is a member NAME (text); a NUMBER-keyed map built by {@code MAP_CONSTRUCT(1,'a')}
     * therefore reads back as the string key {@code "1"} where live keeps the NUMBER — see
     * {@code docs/functions.md}.
     */
    public static String keyName(final Object value) {
        return value == null ? null : value.toString();
    }
}

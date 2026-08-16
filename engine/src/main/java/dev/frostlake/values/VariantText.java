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

import tools.jackson.core.json.JsonWriteFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * The canonical JSON text a VARIANT stores.
 *
 * <p>It is a tree's compact rendering with one deviation from strict JSON, which Snowflake shares: a
 * NON-FINITE double is written BARE. Live renders {@code PARSE_JSON('1e999')} as {@code Infinity} and
 * {@code PARSE_JSON('NaN')} as {@code NaN}, where a standard writer quotes both — and a quoted one is a
 * STRING, which loses the DOUBLE family and reads back wrong. The reader knows the bare words, so the
 * text round-trips.
 */
public final class VariantText {

    private static final ObjectMapper CANONICAL = JsonMapper.builder()
        .disable(JsonWriteFeature.WRITE_NAN_AS_STRINGS)
        .build();

    private VariantText() {
    }

    /** The canonical text of a parsed tree. */
    public static String canonical(final JsonNode node) {
        return CANONICAL.writeValueAsString(node);
    }
}

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

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.node.JsonNodeType;
import tools.jackson.databind.node.ValueNode;

/**
 * The VARIANT {@code undefined} element — the third null of Snowflake's semi-structured layer, alongside
 * SQL NULL and the JSON null.
 *
 * <p>A SQL NULL placed INTO an array becomes {@code undefined}: live-verified
 * {@code ARRAY_CONSTRUCT(1, NULL, 2)} renders {@code [1,undefined,2]} while
 * {@code PARSE_JSON('[1,null,2]')} keeps {@code [1,null,2]}, and the two arrays compare UNEQUAL.
 *
 * <p>This is a Jackson value node so an {@code undefined} lives inside the variant tree exactly where the
 * element sits, keeping every position, size and ordering intact. Two properties make it safe:
 *
 * <ul>
 *   <li>Its node type is {@link JsonNodeType#NULL}, so {@link tools.jackson.databind.JsonNode#isNull()} is
 *       TRUE. Every consumer that does not know about {@code undefined} therefore reads it as SQL NULL —
 *       which is exactly Snowflake's rule: an {@code undefined} NEVER escapes the variant layer as a value
 *       ({@code TYPEOF(GET(ac,1))} and {@code IS_NULL_VALUE(GET(ac,1))} are both SQL NULL live, and
 *       {@code GET(ac,1) IS NULL} is TRUE). Only the handful of call sites that must tell the two nulls
 *       apart consult {@link VariantUndefined}.</li>
 *   <li>It serializes as the bare token {@code undefined}, so the canonical JSON text of an array holding
 *       one is byte-identical to Snowflake's rendering — which in turn makes text-based equality, GROUP BY,
 *       DISTINCT, storage and persistence agree with the account for free.</li>
 * </ul>
 *
 * <p>An {@code undefined} only ever occupies an ARRAY element. An object member is a JSON null instead —
 * live, {@code PARSE_JSON('{"a":undefined}')} is {@code {"a":null}} and
 * {@code OBJECT_CONSTRUCT_KEEP_NULL('k', NULL)} is {@code {"k":null}}.
 */
public final class UndefinedNode extends ValueNode {

    /** The canonical JSON text Snowflake renders for this element. */
    public static final String TOKEN = "undefined";

    private static final long serialVersionUID = 1L;
    private static final UndefinedNode INSTANCE = new UndefinedNode();

    private UndefinedNode() {
    }

    /** The singleton sentinel. */
    public static UndefinedNode getInstance() {
        return INSTANCE;
    }

    @Override
    public JsonNodeType getNodeType() {
        return JsonNodeType.NULL;
    }

    @Override
    public JsonToken asToken() {
        return JsonToken.VALUE_NULL;
    }

    /** Empty text — live, {@code ARRAY_TO_STRING(ARRAY_CONSTRUCT('a', NULL), ',')} is {@code 'a,'}. */
    @Override
    public String asString() {
        return "";
    }

    @Override
    protected String _valueDesc() {
        return TOKEN;
    }

    @Override
    public void serialize(final JsonGenerator generator, final SerializationContext context) {
        generator.writeRawValue(TOKEN);
    }

    @Override
    public UndefinedNode deepCopy() {
        return this;
    }

    /** Identity equality — an {@code undefined} is never equal to a JSON null (live: {@code ac = pj} is FALSE). */
    @Override
    public boolean equals(final Object other) {
        return other == this;
    }

    @Override
    public int hashCode() {
        return TOKEN.hashCode();
    }

    /** Keeps the singleton across Java deserialization of a snapshot. */
    protected Object readResolve() {
        return INSTANCE;
    }
}

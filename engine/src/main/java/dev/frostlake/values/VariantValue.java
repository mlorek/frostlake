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
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.io.Serializable;

/**
 * Runtime value of the SQL semi-structured family (VARIANT / OBJECT / ARRAY): canonical JSON text
 * plus a lazily parsed tree.
 *
 * <p>Equality, ordering, hashing and {@link #toString()} are all over the canonical text — the
 * engine has always compared semi-structured values by their canonical JSON text (GROUP BY,
 * DISTINCT, joins, set operators), and this class preserves exactly those semantics while letting
 * strictly-typed callers (TYPEOF, GET, TO_JSON) know the value really is semi-structured rather
 * than a VARCHAR that happens to look like JSON. Producers canonicalize (sorted object keys,
 * descaled whole decimals) before wrapping; the text is stored verbatim here.
 */
public final class VariantValue implements Comparable<VariantValue>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final JsonMapper MAPPER =
        JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private final String text;
    private transient JsonNode node;

    private VariantValue(final String text) {
        this.text = text;
    }

    /** Wraps canonical JSON text; the caller guarantees it is valid, canonical JSON. */
    public static VariantValue of(final String canonicalText) {
        return new VariantValue(canonicalText);
    }

    /** Wraps a parsed tree, rendering its (compact) JSON text once. */
    public static VariantValue ofNode(final JsonNode node) {
        final VariantValue v = new VariantValue(node.toString());
        v.node = node;
        return v;
    }

    /** The canonical JSON text. */
    public String text() {
        return text;
    }

    /** The parsed tree (parsed on first access; re-parsed after deserialization). */
    public JsonNode node() {
        if (node == null) {
            // The canonical text of an array holding a SQL NULL element carries Snowflake's bare
            // `undefined` token ([1,undefined,2]), which no JSON parser accepts on its own.
            node = VariantUndefined.readTree(MAPPER, text);
        }
        return node;
    }

    public boolean isJsonObject() {
        return node().isObject();
    }

    public boolean isJsonArray() {
        return node().isArray();
    }

    /** A VARIANT JSON null (the text {@code null}) — distinct from SQL NULL. */
    public boolean isJsonNull() {
        return "null".equals(text);
    }

    @Override
    public String toString() {
        // Snowflake renders an XML-shaped variant as XML text wherever the value is stringified
        // (display, ::VARCHAR, TO_VARCHAR). Its indentation follows the JSON_INDENT session
        // parameter — pretty by default, COMPACT at JSON_INDENT = 0 (live-verified both ways).
        // Frostlake has no such parameter, so the compact form is its canonical rendering, which
        // also matches TO_XML. The shape test is structural, so gate on the cheap canonical-text
        // prefix before parsing. Equality, ordering and hashing stay on the canonical JSON text.
        if (XmlVariants.mightBeXmlText(text) && XmlVariants.isXmlElement(node())) {
            return XmlVariants.compactXml(node());
        }
        return text;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VariantValue)) {
            return false;
        }
        return text.equals(((VariantValue) other).text);
    }

    @Override
    public int hashCode() {
        return text.hashCode();
    }

    @Override
    public int compareTo(final VariantValue other) {
        return text.compareTo(other.text);
    }
}

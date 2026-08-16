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
 * <p>Equality, ordering and hashing are all over the canonical text — the
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
    private transient String comparisonKey;

    private VariantValue(final String text) {
        this.text = text;
    }

    /** Wraps canonical JSON text; the caller guarantees it is valid, canonical JSON. */
    public static VariantValue of(final String canonicalText) {
        return new VariantValue(canonicalText);
    }

    /** Wraps a parsed tree, rendering its (compact) JSON text once. */
    public static VariantValue ofNode(final JsonNode node) {
        final VariantValue v = new VariantValue(VariantText.canonical(node));
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
        // (display, ::VARCHAR, TO_VARCHAR); that form stays compact, as TO_XML does. The shape test
        // is structural, so gate on the cheap canonical-text prefix before parsing.
        if (XmlVariants.mightBeXmlText(text) && XmlVariants.isXmlElement(node())) {
            return XmlVariants.compactXml(node());
        }
        // A variant holding a STRING displays its CONTENT when read as a whole value — live prints
        // cdefg, not "cdefg", and LENGTH counts 5 — while a container member keeps its JSON quotes.
        // The display is genuinely ambiguous with a value that LOOKS structural and live accepts
        // that: TYPEOF and variant comparison separate them, never the rendering. Comparisons,
        // hashing and every JSON-consuming subsystem read text(), which keeps the quoted canonical.
        if (node().isTextual()) {
            return node().asText();
        }
        // Everything else displays at the session's JSON_INDENT, a DOUBLE inside a container in the
        // account's fifteen-decimal form. A whole-value number keeps its own spelling here, the same
        // reading a whole-value string gets above; the client surfaces (the driver's getString and the
        // HTTP wire) spell it the account's way themselves. Equality, ordering and hashing all stay on
        // the canonical compact text, which is what text() returns.
        final JsonNode tree = node();
        final String displayed = tree.isArray() || tree.isObject() ? VariantJsonText.displayTextOf(this) : null;
        return VariantJsonFormat.render(tree, displayed != null ? displayed : text);
    }

    /**
     * The key two variants share exactly when they compare equal — numbers by value whatever their
     * notation, objects whatever their key order — see {@link VariantOrder#comparisonKey}.
     */
    public String comparisonKey() {
        if (comparisonKey == null) {
            comparisonKey = VariantOrder.comparisonKey(node());
        }
        return comparisonKey;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VariantValue)) {
            return false;
        }
        final VariantValue that = (VariantValue) other;
        return text.equals(that.text) || comparisonKey().equals(that.comparisonKey());
    }

    @Override
    public int hashCode() {
        return comparisonKey().hashCode();
    }

    /** The account's VARIANT order — by kind, then within the kind; see {@link VariantOrder}. */
    @Override
    public int compareTo(final VariantValue other) {
        return VariantOrder.compare(node(), other.node());
    }
}

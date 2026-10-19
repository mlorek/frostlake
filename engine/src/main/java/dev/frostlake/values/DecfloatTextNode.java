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
import tools.jackson.databind.node.StringNode;

import java.math.BigDecimal;

/**
 * A JSON string node holding a DECFLOAT. A DECFLOAT keeps its type inside a VARIANT, an ARRAY or an OBJECT:
 * {@code TYPEOF(TO_VARIANT(1.5::DECFLOAT))} is {@code DECFLOAT}, and its JSON is the string of its text,
 * {@code TO_JSON(TO_VARIANT(1.5::DECFLOAT))} being {@code "1.5"} (live-verified). The runtime DECFLOAT value is a
 * decimal like any NUMBER's, so the type is remembered here, on the node, beside the value.
 *
 * <p>It IS a string node, so the JSON text — TO_JSON, {@code ::VARCHAR}, the stored text — is the DECFLOAT's own
 * text. Like {@link TypedScalarNode}, the type rides along in memory only: a variant re-read from its text carries
 * a plain string node again.
 */
public final class DecfloatTextNode extends StringNode {

    private static final long serialVersionUID = 1L;

    private final BigDecimal value;

    private DecfloatTextNode(final BigDecimal value) {
        super(Decfloats.text(value));
        this.value = Decfloats.held(value);
    }

    /** The DECFLOAT this member holds. */
    public BigDecimal decfloatValue() {
        return value;
    }

    /** Deep copies keep the type; the node is immutable, so the copy can be the node itself. */
    @Override
    public DecfloatTextNode deepCopy() {
        return this;
    }

    /** Whether {@code node} holds a DECFLOAT. */
    public static boolean holds(final JsonNode node) {
        return node instanceof DecfloatTextNode;
    }

    /** Whether a DECFLOAT sits anywhere in {@code node}, the node itself included. */
    public static boolean anywhereIn(final JsonNode node) {
        if (node instanceof DecfloatTextNode) {
            return true;
        }
        if (node == null || !node.isContainer()) {
            return false;
        }
        for (final JsonNode member : node) {
            if (anywhereIn(member)) {
                return true;
            }
        }
        return false;
    }

    /** The DECFLOAT {@code node} holds, or null when it holds none. */
    public static BigDecimal valueOf(final JsonNode node) {
        return node instanceof DecfloatTextNode ? ((DecfloatTextNode) node).value : null;
    }

    /** The node holding the DECFLOAT {@code value}. */
    public static DecfloatTextNode of(final BigDecimal value) {
        return new DecfloatTextNode(value);
    }

    /** The VARIANT holding the DECFLOAT {@code value}. */
    public static VariantValue variantOf(final BigDecimal value) {
        return VariantValue.ofNode(new DecfloatTextNode(value));
    }
}

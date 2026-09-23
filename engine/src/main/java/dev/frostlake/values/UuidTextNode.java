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

/**
 * A JSON string node holding a UUID. A UUID keeps its type inside a VARIANT, an ARRAY or an OBJECT:
 * {@code TYPEOF(TO_VARIANT(u))} and {@code TYPEOF(ARRAY_CONSTRUCT(u)[0])} are {@code UUID}, the member is
 * no VARCHAR to IS_VARCHAR or AS_VARCHAR, and it never equals the variant string of the same text
 * (live-verified). The runtime UUID value is its text, so the type is remembered here, on the node.
 *
 * <p>It IS a string node, so the JSON text — TO_JSON, {@code ::VARCHAR}, the stored text — is unchanged.
 * Like {@link TypedScalarNode}, the type rides along in memory only: a variant re-read from its text
 * carries a plain string node again.
 */
public final class UuidTextNode extends StringNode {

    private static final long serialVersionUID = 1L;

    public UuidTextNode(final String text) {
        super(text);
    }

    /** Deep copies keep the type; the node is immutable, so the copy can be the node itself. */
    @Override
    public UuidTextNode deepCopy() {
        return this;
    }

    /** Whether {@code node} holds a UUID. */
    public static boolean holds(final JsonNode node) {
        return node instanceof UuidTextNode;
    }

    /** Whether a UUID sits anywhere in {@code node}, the node itself included. */
    public static boolean anywhereIn(final JsonNode node) {
        if (node instanceof UuidTextNode) {
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

    /** The VARIANT holding the UUID {@code text}. */
    public static VariantValue variantOf(final String text) {
        return VariantValue.ofNode(new UuidTextNode(text));
    }
}

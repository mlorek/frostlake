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
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A JSON string node holding a DECFLOAT. A DECFLOAT keeps its type inside a VARIANT, an ARRAY or an OBJECT:
 * {@code TYPEOF(TO_VARIANT(1.5::DECFLOAT))} is {@code DECFLOAT}, and its JSON is the string of its text,
 * {@code TO_JSON(TO_VARIANT(1.5::DECFLOAT))} being {@code "1.5"} (live-verified). The runtime DECFLOAT value is a
 * decimal like any NUMBER's, so the type is remembered here, on the node, beside the value.
 *
 * <p>It IS a string node, so the JSON text — TO_JSON, {@code ::VARCHAR}, the stored text — is the DECFLOAT's own
 * text. Like {@link TypedScalarNode}, the type rides along in memory beside the text: a variant re-read from its
 * text alone carries a plain string node again. A serialized {@link VariantValue} — a checkpoint, a state snapshot,
 * a clone — therefore writes where its DECFLOATs sit ({@link #placesIn}) and puts them back when it is read
 * ({@link #restoredIn}), so a restart does not turn them into texts.
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

    /**
     * Where the DECFLOATs in {@code node} sit, each as the steps from the node down to it: an Integer for an array's
     * element, a String for an object's member; the node itself is the path of no step.
     *
     * @param node the tree
     * @return the paths, in document order; empty when the tree holds no DECFLOAT
     */
    static List<Object[]> placesIn(final JsonNode node) {
        final List<Object[]> places = new ArrayList<>();
        collectPlaces(node, new ArrayList<>(), places);
        return places;
    }

    private static void collectPlaces(final JsonNode node, final List<Object> path, final List<Object[]> places) {
        if (node instanceof DecfloatTextNode) {
            places.add(path.toArray());
        } else if (node instanceof ArrayNode) {
            for (int i = 0; i < node.size(); i++) {
                path.add(Integer.valueOf(i));
                collectPlaces(node.get(i), path, places);
                path.remove(path.size() - 1);
            }
        } else if (node instanceof ObjectNode) {
            for (final Map.Entry<String, JsonNode> member : node.properties()) {
                path.add(member.getKey());
                collectPlaces(member.getValue(), path, places);
                path.remove(path.size() - 1);
            }
        }
    }

    /**
     * A tree re-read from its text with its DECFLOATs put back: the string at each of {@code places} (see
     * {@link #placesIn}) becomes the DECFLOAT its text spells. A place that names no string is left as it is.
     *
     * @param tree   the tree parsed from the text, changed in place
     * @param places the DECFLOATs' paths
     * @return the tree, or the DECFLOAT itself where the tree is one
     */
    static JsonNode restoredIn(final JsonNode tree, final List<Object[]> places) {
        JsonNode root = tree;
        for (final Object[] place : places) {
            if (place.length == 0) {
                root = asDecfloat(root);
                continue;
            }
            JsonNode parent = root;
            for (int i = 0; i < place.length - 1 && parent != null; i++) {
                parent = place[i] instanceof Integer ? parent.get(((Integer) place[i]).intValue())
                    : parent.get((String) place[i]);
            }
            final Object last = place[place.length - 1];
            if (parent instanceof ArrayNode && last instanceof Integer) {
                final int index = ((Integer) last).intValue();
                if (index < parent.size()) {
                    ((ArrayNode) parent).set(index, asDecfloat(parent.get(index)));
                }
            } else if (parent instanceof ObjectNode && last instanceof String && parent.has((String) last)) {
                ((ObjectNode) parent).set((String) last, asDecfloat(parent.get((String) last)));
            }
        }
        return root;
    }

    /** The DECFLOAT a string node spells, or the node as it is when it is no such string. */
    private static JsonNode asDecfloat(final JsonNode node) {
        if (node == null || !node.isTextual() || node instanceof DecfloatTextNode) {
            return node;
        }
        try {
            return new DecfloatTextNode(new BigDecimal(node.asText()));
        } catch (final NumberFormatException | DecfloatRangeException notADecfloat) {
            return node;
        }
    }
}

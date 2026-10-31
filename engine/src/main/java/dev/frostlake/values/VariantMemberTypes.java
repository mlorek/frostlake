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
import tools.jackson.databind.node.DoubleNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * What a VARIANT's text does not keep of its members' types, for serialization to put back once a checkpoint, a
 * snapshot or a written result reads the text again: a double, which the text spells as the decimal a reader takes
 * it for; a FLOAT's double (see {@link FloatOriginNode}); a DATE, a TIME, a timestamp or a BINARY (see
 * {@link TypedScalarNode}); a VECTOR (see {@link TypedVectorNode}); a UUID (see {@link UuidTextNode}); and a scaled
 * NUMBER's whole number (see {@link DecimalOriginNode}). Each node of the tree takes one tag, in the order a walk
 * from the root meets them, which the tree read back from the same text is walked in too; a vector's elements take
 * none. A DECFLOAT's place is kept apart (see {@link DecfloatTextNode#placesIn}).
 */
final class VariantMemberTypes implements Serializable {

    private static final long serialVersionUID = 1L;

    /** A node its text reads back as it was. */
    private static final byte AS_READ = 0;

    /** A double, its value the next of the doubles. */
    private static final byte DOUBLE = 1;

    /** A FLOAT's double, its value the next of the doubles. */
    private static final byte FLOAT = 2;

    /** A DATE, a TIME, a timestamp or a BINARY: its value the next of the values, its text the one read back. */
    private static final byte TYPED = 3;

    /** A VECTOR, the next of the values; the array read back in its place is replaced, elements and all. */
    private static final byte VECTOR = 4;

    /** A UUID, its text the one read back. */
    private static final byte UUID = 5;

    /** A scaled NUMBER's whole number, its value the number read back. */
    private static final byte WHOLE_DECIMAL = 6;

    private byte[] tags = new byte[16];
    private int tagCount;
    private double[] doubles = new double[4];
    private int doubleCount;
    private final List<Object> values = new ArrayList<>();

    private VariantMemberTypes() {
    }

    /**
     * The types a tree's text does not keep.
     *
     * @param tree the tree
     * @return the members' types, or null where the text keeps every one
     */
    static VariantMemberTypes of(final JsonNode tree) {
        if (tree == null || !holdsAny(tree)) {
            return null;
        }
        final VariantMemberTypes types = new VariantMemberTypes();
        types.collect(tree);
        types.tags = Arrays.copyOf(types.tags, types.tagCount);
        types.doubles = Arrays.copyOf(types.doubles, types.doubleCount);
        return types;
    }

    /** Whether any node of the tree is one whose type its text does not keep. */
    private static boolean holdsAny(final JsonNode node) {
        if (tagOf(node) != AS_READ) {
            return true;
        }
        if (node instanceof ArrayNode || node instanceof ObjectNode) {
            for (final JsonNode member : node) {
                if (holdsAny(member)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The tag a node takes. */
    private static byte tagOf(final JsonNode node) {
        if (node instanceof TypedVectorNode) {
            return VECTOR;
        }
        if (node instanceof FloatOriginNode) {
            return FLOAT;
        }
        if (node instanceof DoubleNode) {
            return DOUBLE;
        }
        if (node instanceof TypedScalarNode) {
            return ((TypedScalarNode) node).typedValue() instanceof Serializable ? TYPED : AS_READ;
        }
        if (node instanceof UuidTextNode) {
            return UUID;
        }
        return node instanceof DecimalOriginNode ? WHOLE_DECIMAL : AS_READ;
    }

    private void collect(final JsonNode node) {
        final byte tag = tagOf(node);
        if (tagCount == tags.length) {
            tags = Arrays.copyOf(tags, tags.length * 2);
        }
        tags[tagCount++] = tag;
        if (tag == DOUBLE || tag == FLOAT) {
            if (doubleCount == doubles.length) {
                doubles = Arrays.copyOf(doubles, doubles.length * 2);
            }
            doubles[doubleCount++] = node.doubleValue();
        } else if (tag == TYPED) {
            values.add(((TypedScalarNode) node).typedValue());
        } else if (tag == VECTOR) {
            values.add(TypedVectorNode.vectorValueOf(node));
        } else if (node instanceof ArrayNode || node instanceof ObjectNode) {
            for (final JsonNode member : node) {
                collect(member);
            }
        }
    }

    /**
     * The tree read back from the text, with the members' types put back in place.
     *
     * @param tree the tree read from the text, changed in place
     * @return the tree, or the member that replaces it where its root is one
     */
    JsonNode restoredIn(final JsonNode tree) {
        // The places reached so far: the next tag, the next double and the next value.
        return restored(tree, new int[3]);
    }

    private JsonNode restored(final JsonNode node, final int[] at) {
        if (node == null || at[0] >= tags.length) {
            return node;
        }
        final byte tag = tags[at[0]++];
        switch (tag) {
            case DOUBLE:
                return new DoubleNode(doubles[at[1]++]);
            case FLOAT:
                return new FloatOriginNode(doubles[at[1]++]);
            case TYPED:
                return new TypedScalarNode(node.asText(), values.get(at[2]++));
            case VECTOR:
                return new TypedVectorNode((VectorValue) values.get(at[2]++));
            case UUID:
                return new UuidTextNode(node.asText());
            case WHOLE_DECIMAL:
                return new DecimalOriginNode(node.decimalValue());
            default:
                break;
        }
        if (node instanceof ArrayNode) {
            final ArrayNode array = (ArrayNode) node;
            for (int i = 0; i < array.size(); i++) {
                array.set(i, restored(array.get(i), at));
            }
        } else if (node instanceof ObjectNode) {
            final ObjectNode object = (ObjectNode) node;
            final List<String> names = new ArrayList<>();
            for (final Map.Entry<String, JsonNode> member : object.properties()) {
                names.add(member.getKey());
            }
            for (final String name : names) {
                object.set(name, restored(object.get(name), at));
            }
        }
        return node;
    }
}

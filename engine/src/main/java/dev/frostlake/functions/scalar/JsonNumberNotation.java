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

package dev.frostlake.functions.scalar;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Which JSON numbers belong to the DOUBLE family, decided by HOW THEY WERE WRITTEN.
 *
 * <p>A number written in scientific notation is a DOUBLE and a number written plainly is not — the rule
 * is the notation itself, with nothing to do with the value: live, {@code PARSE_JSON('[1.0e0]')} is
 * {@code [1.0]} with TYPEOF DOUBLE while {@code PARSE_JSON('[1.0]')} is {@code [1]} with TYPEOF INTEGER,
 * and {@code [2e0]}, {@code [0e0]}, {@code [1.0E0]}, {@code [1.0e+0]} and {@code [1.0e1]} are all DOUBLE
 * too. The sign and magnitude of the exponent never enter into it.
 *
 * <p>THE NOTATION DOES NOT SURVIVE THE PARSE, which is the whole reason this class exists. A JSON reader
 * hands back a BigDecimal, and {@code 1.0e0} and {@code 1.0} produce the same one — unscaled 10, scale 1.
 * Only a NEGATIVE scale is recoverable afterwards ({@code 1e5} keeps scale −5), so everything with a zero
 * or small positive exponent was indistinguishable from its plain spelling and lost the family. The
 * digit-count stand-in that used to cover part of this — more than 15 significant digits reading as
 * double provenance — was measured WRONG in the other direction: live types the plain
 * {@code [1.234567890123456]} as DECIMAL.
 *
 * <p>So the notation is read from the TEXT, in a second streaming pass over the same document that
 * produced the tree, walking the two in lockstep and re-typing each float token written with an
 * exponent. The pass is skipped outright unless the text contains a digit or point followed by an
 * {@code e}. Duplicate object keys cannot desynchronise the walk because the reader refuses them
 * outright (a duplicate attribute is a live refusal), so a field is matched by NAME and an element by
 * position.
 */
public final class JsonNumberNotation {

    private JsonNumberNotation() {
    }

    /**
     * The tree with every float that was WRITTEN with an exponent carried in the DOUBLE family.
     *
     * @param mapper the mapper whose reader produced {@code tree} — the second pass has to read the
     *               document under the same leniencies or it will not agree with the first
     * @param text the document exactly as the first pass read it, sentinels and relaxations included
     * @param tree the parsed tree, mutated in place and returned; null is passed through
     * @return the tree, with exponent-written floats re-typed
     */
    public static JsonNode applyExponentNotation(final ObjectMapper mapper, final String text,
                                                 final JsonNode tree) {
        if (tree == null || text == null || !mayCarryAnExponent(text)) {
            return tree;
        }
        try (JsonParser parser = mapper.createParser(text)) {
            if (parser.nextToken() == null) {
                return tree;
            }
            return rewrite(mapper, parser, tree);
        } catch (final RuntimeException unreadable) {
            // The document already parsed once; a second read that disagrees leaves the tree as it is
            // rather than failing a call that had succeeded.
            return tree;
        }
    }

    /** The parser sits ON the token {@code node} was built from; returns the node to put in its place. */
    private static JsonNode rewrite(final ObjectMapper mapper, final JsonParser parser,
                                    final JsonNode node) {
        final JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT) {
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                final String name = parser.currentName();
                parser.nextToken();
                final JsonNode field = node == null ? null : node.get(name);
                final JsonNode rewritten = rewrite(mapper, parser, field);
                if (rewritten != field && node instanceof ObjectNode) {
                    ((ObjectNode) node).set(name, rewritten);
                }
            }
            return node;
        }
        if (token == JsonToken.START_ARRAY) {
            int index = 0;
            JsonToken element = parser.nextToken();
            while (element != null && element != JsonToken.END_ARRAY) {
                final JsonNode child = node == null ? null : node.get(index);
                final JsonNode rewritten = rewrite(mapper, parser, child);
                if (rewritten != child && node instanceof ArrayNode) {
                    ((ArrayNode) node).set(index, rewritten);
                }
                index++;
                element = parser.nextToken();
            }
            return node;
        }
        if (token == JsonToken.VALUE_NUMBER_FLOAT && node != null && node.isNumber()
                && hasExponent(parser.getString())) {
            // The double is parsed from the TOKEN TEXT, not read off the node: the node is a
            // BigDecimal, which has no negative zero, so -0e0 through it forgot its sign where a
            // real account keeps it.
            return mapper.getNodeFactory().numberNode(Double.parseDouble(parser.getString()));
        }
        return node;
    }

    /** A number token's text is only the number, so the letter itself settles it. */
    private static boolean hasExponent(final String literal) {
        return literal != null && (literal.indexOf('e') >= 0 || literal.indexOf('E') >= 0);
    }

    /**
     * Whether the document is worth a second pass: an {@code e} that follows a digit or a point is the
     * only shape a numeric exponent can take. Prose inside a string can match it, which costs one pass
     * that finds nothing — never a wrong answer.
     */
    private static boolean mayCarryAnExponent(final String text) {
        for (int i = 1; i < text.length(); i++) {
            final char letter = text.charAt(i);
            if (letter != 'e' && letter != 'E') {
                continue;
            }
            final char before = text.charAt(i - 1);
            if (before == '.' || before >= '0' && before <= '9') {
                return true;
            }
        }
        return false;
    }
}

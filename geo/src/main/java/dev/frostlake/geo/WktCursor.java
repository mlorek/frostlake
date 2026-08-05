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

package dev.frostlake.geo;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * A hand-rolled cursor over WKT text for {@link GeoShapes}: words, signed decimal numbers (kept as
 * BigDecimal so plain coordinates render exactly as Snowflake shows them for GEOGRAPHY), coordinate
 * pairs, and the parenthesized list shapes WKT uses. Every method returns null (or false) on
 * malformed input; the caller raises the uniform parse error.
 */
final class WktCursor {

    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private final String text;
    private int pos;

    WktCursor(final String text) {
        this.text = text;
    }

    void skipSpace() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    boolean atEnd() {
        skipSpace();
        return pos >= text.length();
    }

    boolean consume(final char c) {
        skipSpace();
        if (pos < text.length() && text.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    String word() {
        skipSpace();
        final int start = pos;
        while (pos < text.length() && Character.isLetter(text.charAt(pos))) {
            pos++;
        }
        return text.substring(start, pos);
    }

    private JsonNode number() {
        skipSpace();
        final int start = pos;
        if (pos < text.length() && (text.charAt(pos) == '-' || text.charAt(pos) == '+')) {
            pos++;
        }
        boolean digits = false;
        while (pos < text.length() && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.')) {
            digits = true;
            pos++;
        }
        if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
            pos++;
            if (pos < text.length() && (text.charAt(pos) == '-' || text.charAt(pos) == '+')) {
                pos++;
            }
            while (pos < text.length() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
        }
        if (!digits) {
            return null;
        }
        try {
            return NODES.numberNode(new BigDecimal(text.substring(start, pos)));
        } catch (final NumberFormatException bad) {
            return null;
        }
    }

    private ArrayNode point() {
        final JsonNode x = number();
        final JsonNode y = number();
        if (x == null || y == null) {
            return null;
        }
        final ArrayNode pt = NODES.arrayNode();
        pt.add(x);
        pt.add(y);
        return pt;
    }

    /** {@code ( point )} — the POINT body. */
    List<JsonNode> pointList(final int expected) {
        if (!consume('(')) {
            return null;
        }
        final List<JsonNode> points = new ArrayList<JsonNode>();
        do {
            final ArrayNode pt = point();
            if (pt == null) {
                return null;
            }
            points.add(pt);
        } while (consume(','));
        if (!consume(')') || points.size() < expected) {
            return null;
        }
        return points;
    }

    /** {@code ( point, point, … )} with a minimum count — LINESTRING and ring bodies. */
    ArrayNode coordinateArray(final int minPoints) {
        if (!consume('(')) {
            return null;
        }
        final ArrayNode line = NODES.arrayNode();
        do {
            final ArrayNode pt = point();
            if (pt == null) {
                return null;
            }
            line.add(pt);
        } while (consume(','));
        if (!consume(')') || line.size() < minPoints) {
            return null;
        }
        return line;
    }

    /** {@code ( ring, ring, … )} where each ring needs at least 4 points — the POLYGON body. */
    ArrayNode ringArray() {
        if (!consume('(')) {
            return null;
        }
        final ArrayNode rings = NODES.arrayNode();
        do {
            final ArrayNode ring = coordinateArray(4);
            if (ring == null) {
                return null;
            }
            rings.add(ring);
        } while (consume(','));
        if (!consume(')')) {
            return null;
        }
        return rings;
    }

    /** {@code ( line, line, … )} where each member needs at least 2 points — MULTILINESTRING. */
    ArrayNode ringArrayAllowShort() {
        if (!consume('(')) {
            return null;
        }
        final ArrayNode lines = NODES.arrayNode();
        do {
            final ArrayNode line = coordinateArray(2);
            if (line == null) {
                return null;
            }
            lines.add(line);
        } while (consume(','));
        if (!consume(')')) {
            return null;
        }
        return lines;
    }

    /** MULTIPOINT accepts both {@code ((0 0), (1 1))} and the bare {@code (0 0, 1 1)} form. */
    ArrayNode multiPointArray() {
        if (!consume('(')) {
            return null;
        }
        final ArrayNode points = NODES.arrayNode();
        do {
            final ArrayNode pt;
            if (consume('(')) {
                pt = point();
                if (pt == null || !consume(')')) {
                    return null;
                }
            } else {
                pt = point();
                if (pt == null) {
                    return null;
                }
            }
            points.add(pt);
        } while (consume(','));
        if (!consume(')')) {
            return null;
        }
        return points;
    }
}

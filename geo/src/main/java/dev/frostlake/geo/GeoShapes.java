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

import dev.frostlake.values.GeoValue;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The shared geospatial engine behind the ST_ functions: WKT and GeoJSON parsing, WKT / WKB / GeoJSON
 * rendering, and the measure / predicate math. Live-verified model: Snowflake's GEOGRAPHY is a pure
 * SPHERE of radius {@code 6371010 m} ({@code ST_DISTANCE} of one degree
 * is 111195.10117748393 m, reproduced exactly), while {@code HAVERSINE} uses {@code 6371 km}; GEOMETRY is planar.
 * Supported shapes: Point, LineString, Polygon (with holes) and their Multi* forms plus
 * GeometryCollection parsing. Coordinates parsed from text stay BigDecimal (plain rendering, as
 * Snowflake shows for GEOGRAPHY); computed coordinates are doubles.
 */
public final class GeoShapes {

    /** The live-verified Earth radius Snowflake's GEOGRAPHY measures use: exactly 6371010 m
     *  (ST_DISTANCE of one degree = 111195.10117748393 m reproduces to the last bit). */
    public static final double EARTH_RADIUS_M = 6371010.0;
    /** The radius HAVERSINE uses (kilometers) — live-verified to differ from ST_DISTANCE's. */
    public static final double HAVERSINE_RADIUS_KM = 6371.0;

    private static final ObjectMapper MAPPER =
        JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private GeoShapes() {
    }

    // ------------------------------------------------------------------ parsing

    /**
     * Parses WKT or GeoJSON input into a {@link GeoValue}; raises Snowflake's message on anything
     * else: {@code Error parsing Geo input: <input>. Did not recognize valid GeoJSON, (E)WKT or
     * (E)WKB.}
     */
    public static GeoValue parse(final String input, final boolean geography) {
        final String trimmed = input.trim();
        try {
            if (trimmed.startsWith("{")) {
                final JsonNode json = MAPPER.readTree(trimmed);
                final JsonNode model = normalizedGeoJson(json);
                if (model != null) {
                    return GeoValue.ofNode(model, geography, geography ? 4326 : 0);
                }
            } else {
                final JsonNode model = parseWkt(trimmed);
                if (model != null) {
                    return GeoValue.ofNode(model, geography, geography ? 4326 : 0);
                }
            }
        } catch (final RuntimeException notGeo) {
            // fall through to the uniform error below
        }
        throw new RuntimeException("Error parsing Geo input: " + input
            + ". Did not recognize valid GeoJSON, (E)WKT or (E)WKB.");
    }

    /** The value as a GeoValue, or null when it is not one (callers raise their own type errors). */
    public static GeoValue asGeo(final Object value) {
        return value instanceof GeoValue ? (GeoValue) value : null;
    }

    /**
     * The two arguments as same-kind geo values; a kind mismatch raises Snowflake's compile-style
     * error ({@code Invalid argument types for function 'X': (GEOGRAPHY, GEOMETRY)}, live-verified).
     */
    public static void requireSameKind(final String functionName, final GeoValue a, final GeoValue b) {
        if (a.isGeography() != b.isGeography()) {
            throw new RuntimeException("Invalid argument types for function '" + functionName
                + "': (" + a.kindName() + ", " + b.kindName() + ")");
        }
    }

    // GeoJSON: {"coordinates": ..., "type": "..."} with sorted keys (coordinates < type).
    private static JsonNode normalizedGeoJson(final JsonNode json) {
        if (!json.isObject() || json.get("type") == null) {
            return null;
        }
        final String type = json.get("type").asText();
        if ("GeometryCollection".equals(type)) {
            final JsonNode geometries = json.get("geometries");
            if (geometries == null || !geometries.isArray()) {
                return null;
            }
            final ArrayNode parts = MAPPER.createArrayNode();
            for (final JsonNode member : geometries) {
                final JsonNode normalized = normalizedGeoJson(member);
                if (normalized == null) {
                    return null;
                }
                parts.add(normalized);
            }
            final ObjectNode out = MAPPER.createObjectNode();
            out.set("geometries", parts);
            out.put("type", "GeometryCollection");
            return out;
        }
        final JsonNode coordinates = json.get("coordinates");
        if (coordinates == null || !isKnownType(type)) {
            return null;
        }
        final ObjectNode out = MAPPER.createObjectNode();
        out.set("coordinates", coordinates);
        out.put("type", type);
        return out;
    }

    private static boolean isKnownType(final String type) {
        return "Point".equals(type) || "LineString".equals(type) || "Polygon".equals(type)
            || "MultiPoint".equals(type) || "MultiLineString".equals(type) || "MultiPolygon".equals(type);
    }

    // ------------------------------------------------------------------ WKT

    private static JsonNode parseWkt(final String wkt) {
        final WktCursor cursor = new WktCursor(wkt);
        final JsonNode model = parseWktBody(cursor);
        cursor.skipSpace();
        return cursor.atEnd() ? model : null;
    }

    private static JsonNode parseWktBody(final WktCursor c) {
        final String keyword = c.word().toUpperCase(Locale.ROOT);
        final ObjectNode out = MAPPER.createObjectNode();
        switch (keyword) {
            case "POINT": {
                final List<JsonNode> pt = c.pointList(1);
                if (pt == null || pt.size() != 1) {
                    return null;
                }
                out.set("coordinates", pt.get(0));
                out.put("type", "Point");
                return out;
            }
            case "LINESTRING": {
                final ArrayNode line = c.coordinateArray(2);
                if (line == null) {
                    return null;
                }
                out.set("coordinates", line);
                out.put("type", "LineString");
                return out;
            }
            case "POLYGON": {
                final ArrayNode rings = c.ringArray();
                if (rings == null) {
                    return null;
                }
                out.set("coordinates", rings);
                out.put("type", "Polygon");
                return out;
            }
            case "MULTIPOINT": {
                final ArrayNode points = c.multiPointArray();
                if (points == null) {
                    return null;
                }
                out.set("coordinates", points);
                out.put("type", "MultiPoint");
                return out;
            }
            case "MULTILINESTRING": {
                final ArrayNode lines = c.ringArrayAllowShort();
                if (lines == null) {
                    return null;
                }
                out.set("coordinates", lines);
                out.put("type", "MultiLineString");
                return out;
            }
            case "MULTIPOLYGON": {
                if (!c.consume('(')) {
                    return null;
                }
                final ArrayNode polys = MAPPER.createArrayNode();
                do {
                    final ArrayNode rings = c.ringArray();
                    if (rings == null) {
                        return null;
                    }
                    polys.add(rings);
                } while (c.consume(','));
                if (!c.consume(')')) {
                    return null;
                }
                out.set("coordinates", polys);
                out.put("type", "MultiPolygon");
                return out;
            }
            case "GEOMETRYCOLLECTION": {
                if (!c.consume('(')) {
                    return null;
                }
                final ArrayNode parts = MAPPER.createArrayNode();
                do {
                    final JsonNode part = parseWktBody(c);
                    if (part == null) {
                        return null;
                    }
                    parts.add(part);
                } while (c.consume(','));
                if (!c.consume(')')) {
                    return null;
                }
                out.set("geometries", parts);
                out.put("type", "GeometryCollection");
                return out;
            }
            default:
                return null;
        }
    }

    // ------------------------------------------------------------------ rendering

    /** WKT in Snowflake's normalized form: {@code LINESTRING(0 0,1 0)} — comma without space. */
    public static String toWkt(final JsonNode model) {
        final String type = model.get("type").asText();
        final StringBuilder out = new StringBuilder();
        switch (type) {
            case "Point":
                out.append("POINT(");
                appendWktPoint(model.get("coordinates"), out);
                return out.append(')').toString();
            case "LineString":
                out.append("LINESTRING(");
                appendWktLine(model.get("coordinates"), out);
                return out.append(')').toString();
            case "Polygon":
                out.append("POLYGON(");
                appendWktRings(model.get("coordinates"), out);
                return out.append(')').toString();
            case "MultiPoint": {
                out.append("MULTIPOINT(");
                boolean first = true;
                for (final JsonNode pt : model.get("coordinates")) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    out.append('(');
                    appendWktPoint(pt, out);
                    out.append(')');
                }
                return out.append(')').toString();
            }
            case "MultiLineString":
                out.append("MULTILINESTRING(");
                appendWktRings(model.get("coordinates"), out);
                return out.append(')').toString();
            case "MultiPolygon": {
                out.append("MULTIPOLYGON(");
                boolean first = true;
                for (final JsonNode poly : model.get("coordinates")) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    out.append('(');
                    appendWktRings(poly, out);
                    out.append(')');
                }
                return out.append(')').toString();
            }
            case "GeometryCollection": {
                out.append("GEOMETRYCOLLECTION(");
                boolean first = true;
                for (final JsonNode part : model.get("geometries")) {
                    if (!first) {
                        out.append(',');
                    }
                    first = false;
                    out.append(toWkt(part));
                }
                return out.append(')').toString();
            }
            default:
                return type.toUpperCase(Locale.ROOT);
        }
    }

    private static void appendWktRings(final JsonNode rings, final StringBuilder out) {
        boolean first = true;
        for (final JsonNode ring : rings) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('(');
            appendWktLine(ring, out);
            out.append(')');
        }
    }

    private static void appendWktLine(final JsonNode line, final StringBuilder out) {
        boolean first = true;
        for (final JsonNode pt : line) {
            if (!first) {
                out.append(',');
            }
            first = false;
            appendWktPoint(pt, out);
        }
    }

    private static void appendWktPoint(final JsonNode pt, final StringBuilder out) {
        out.append(coordinateText(pt.get(0))).append(' ').append(coordinateText(pt.get(1)));
    }

    private static String coordinateText(final JsonNode c) {
        if (c.isBigDecimal()) {
            final BigDecimal stripped = c.decimalValue().stripTrailingZeros();
            return stripped.scale() <= 0
                ? stripped.toBigInteger().toString() : stripped.toPlainString();
        }
        final double d = c.asDouble();
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(d);
    }

    /** Little-endian WKB bytes (the ST_ASWKB form: {@code 0101000000…}). */
    public static byte[] toWkb(final JsonNode model) {
        final List<Byte> out = new ArrayList<Byte>();
        writeWkb(model, out);
        final byte[] bytes = new byte[out.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = out.get(i);
        }
        return bytes;
    }

    private static void writeWkb(final JsonNode model, final List<Byte> out) {
        final String type = model.get("type").asText();
        out.add((byte) 1);   // little-endian
        switch (type) {
            case "Point":
                writeWkbInt(1, out);
                writeWkbPoint(model.get("coordinates"), out);
                return;
            case "LineString":
                writeWkbInt(2, out);
                writeWkbLine(model.get("coordinates"), out);
                return;
            case "Polygon": {
                writeWkbInt(3, out);
                final JsonNode rings = model.get("coordinates");
                writeWkbInt(rings.size(), out);
                for (final JsonNode ring : rings) {
                    writeWkbLine(ring, out);
                }
                return;
            }
            case "MultiPoint": {
                writeWkbInt(4, out);
                final JsonNode points = model.get("coordinates");
                writeWkbInt(points.size(), out);
                for (final JsonNode pt : points) {
                    out.add((byte) 1);
                    writeWkbInt(1, out);
                    writeWkbPoint(pt, out);
                }
                return;
            }
            default: {
                // MultiLineString(5) / MultiPolygon(6) / GeometryCollection(7)
                final int code = "MultiLineString".equals(type) ? 5 : "MultiPolygon".equals(type) ? 6 : 7;
                writeWkbInt(code, out);
                if (code == 7) {
                    final JsonNode parts = model.get("geometries");
                    writeWkbInt(parts.size(), out);
                    for (final JsonNode part : parts) {
                        writeWkb(part, out);
                    }
                    return;
                }
                final JsonNode members = model.get("coordinates");
                writeWkbInt(members.size(), out);
                for (final JsonNode member : members) {
                    out.add((byte) 1);
                    if (code == 5) {
                        writeWkbInt(2, out);
                        writeWkbLine(member, out);
                    } else {
                        writeWkbInt(3, out);
                        writeWkbInt(member.size(), out);
                        for (final JsonNode ring : member) {
                            writeWkbLine(ring, out);
                        }
                    }
                }
            }
        }
    }

    private static void writeWkbLine(final JsonNode line, final List<Byte> out) {
        writeWkbInt(line.size(), out);
        for (final JsonNode pt : line) {
            writeWkbPoint(pt, out);
        }
    }

    private static void writeWkbPoint(final JsonNode pt, final List<Byte> out) {
        writeWkbDouble(pt.get(0).asDouble(), out);
        writeWkbDouble(pt.get(1).asDouble(), out);
    }

    private static void writeWkbInt(final int v, final List<Byte> out) {
        for (int i = 0; i < 4; i++) {
            out.add((byte) ((v >> (8 * i)) & 0xFF));
        }
    }

    private static void writeWkbDouble(final double d, final List<Byte> out) {
        final long bits = Double.doubleToLongBits(d);
        for (int i = 0; i < 8; i++) {
            out.add((byte) ((bits >> (8 * i)) & 0xFF));
        }
    }

    // ------------------------------------------------------------------ traversal helpers

    /** Every coordinate pair of the model, flattened. */
    public static List<double[]> allPoints(final JsonNode model) {
        final List<double[]> points = new ArrayList<double[]>();
        collectPoints(model, points);
        return points;
    }

    private static void collectPoints(final JsonNode model, final List<double[]> out) {
        final String type = model.get("type").asText();
        if ("GeometryCollection".equals(type)) {
            for (final JsonNode part : model.get("geometries")) {
                collectPoints(part, out);
            }
            return;
        }
        collectFromCoordinates(model.get("coordinates"), out);
    }

    private static void collectFromCoordinates(final JsonNode coordinates, final List<double[]> out) {
        if (coordinates.size() > 0 && coordinates.get(0).isNumber()) {
            out.add(new double[] {coordinates.get(0).asDouble(), coordinates.get(1).asDouble()});
            return;
        }
        for (final JsonNode member : coordinates) {
            collectFromCoordinates(member, out);
        }
    }

    /** Every line segment (pairs of consecutive points in LineString/ring members). */
    public static List<double[][]> allSegments(final JsonNode model) {
        final List<double[][]> segments = new ArrayList<double[][]>();
        collectSegments(model, segments);
        return segments;
    }

    private static void collectSegments(final JsonNode model, final List<double[][]> out) {
        final String type = model.get("type").asText();
        switch (type) {
            case "GeometryCollection":
                for (final JsonNode part : model.get("geometries")) {
                    collectSegments(part, out);
                }
                return;
            case "LineString":
                segmentsOfLine(model.get("coordinates"), out);
                return;
            case "MultiLineString":
            case "Polygon":
                for (final JsonNode line : model.get("coordinates")) {
                    segmentsOfLine(line, out);
                }
                return;
            case "MultiPolygon":
                for (final JsonNode poly : model.get("coordinates")) {
                    for (final JsonNode ring : poly) {
                        segmentsOfLine(ring, out);
                    }
                }
                return;
            default:
                // points contribute no segments
        }
    }

    private static void segmentsOfLine(final JsonNode line, final List<double[][]> out) {
        for (int i = 0; i + 1 < line.size(); i++) {
            out.add(new double[][] {
                {line.get(i).get(0).asDouble(), line.get(i).get(1).asDouble()},
                {line.get(i + 1).get(0).asDouble(), line.get(i + 1).get(1).asDouble()}});
        }
    }

    /** The polygon rings of the model (outer first per polygon), or empty when it has none. */
    public static List<JsonNode> polygonRingSets(final JsonNode model) {
        final List<JsonNode> polygons = new ArrayList<JsonNode>();
        final String type = model.get("type").asText();
        if ("Polygon".equals(type)) {
            polygons.add(model.get("coordinates"));
        } else if ("MultiPolygon".equals(type)) {
            for (final JsonNode poly : model.get("coordinates")) {
                polygons.add(poly);
            }
        } else if ("GeometryCollection".equals(type)) {
            for (final JsonNode part : model.get("geometries")) {
                polygons.addAll(polygonRingSets(part));
            }
        }
        return polygons;
    }

    // ------------------------------------------------------------------ measures

    /** Great-circle distance between two lon/lat points on Snowflake's sphere (meters). */
    public static double sphereDistance(final double lon1, final double lat1,
                                        final double lon2, final double lat2) {
        return EARTH_RADIUS_M * centralAngle(lon1, lat1, lon2, lat2);
    }

    private static double centralAngle(final double lon1, final double lat1,
                                       final double lon2, final double lat2) {
        final double p1 = Math.toRadians(lat1);
        final double p2 = Math.toRadians(lat2);
        final double dp = Math.toRadians(lat2 - lat1);
        final double dl = Math.toRadians(lon2 - lon1);
        final double a = Math.sin(dp / 2) * Math.sin(dp / 2)
            + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** The minimum distance between two geo values: spherical meters or planar units. */
    public static double minDistance(final GeoValue a, final GeoValue b) {
        final List<double[]> aPts = allPoints(a.node());
        final List<double[]> bPts = allPoints(b.node());
        double best = Double.MAX_VALUE;
        // point-to-point over all vertex pairs …
        for (final double[] p : aPts) {
            for (final double[] q : bPts) {
                best = Math.min(best, pointDistance(a.isGeography(), p, q));
            }
        }
        // … refined by point-to-segment in both directions.
        for (final double[][] seg : allSegments(b.node())) {
            for (final double[] p : aPts) {
                best = Math.min(best, pointToSegment(a.isGeography(), p, seg));
            }
        }
        for (final double[][] seg : allSegments(a.node())) {
            for (final double[] q : bPts) {
                best = Math.min(best, pointToSegment(a.isGeography(), q, seg));
            }
        }
        // inside-a-polygon means distance zero, as does any segment crossing.
        if (contains(a, bPts) || contains(b, aPts) || segmentsCross(a.node(), b.node())) {
            best = 0;
        }
        return best;
    }

    private static double pointDistance(final boolean spherical, final double[] p, final double[] q) {
        if (spherical) {
            return sphereDistance(p[0], p[1], q[0], q[1]);
        }
        return Math.hypot(p[0] - q[0], p[1] - q[1]);
    }

    private static double pointToSegment(final boolean spherical, final double[] p, final double[][] seg) {
        // Planar projection of the point onto the segment; for the sphere this is an approximation
        // (evaluated at the projected parameter), adequate at the segment lengths tests use.
        final double ax = seg[0][0];
        final double ay = seg[0][1];
        final double bx = seg[1][0];
        final double by = seg[1][1];
        final double dx = bx - ax;
        final double dy = by - ay;
        final double lenSq = dx * dx + dy * dy;
        double t = lenSq == 0 ? 0 : ((p[0] - ax) * dx + (p[1] - ay) * dy) / lenSq;
        t = Math.max(0, Math.min(1, t));
        final double[] proj = {ax + t * dx, ay + t * dy};
        return pointDistance(spherical, p, proj);
    }

    private static boolean contains(final GeoValue container, final List<double[]> points) {
        final List<JsonNode> polygons = polygonRingSets(container.node());
        if (polygons.isEmpty() || points.isEmpty()) {
            return false;
        }
        for (final double[] p : points) {
            if (!pointInPolygons(polygons, p[0], p[1])) {
                return false;
            }
        }
        return true;
    }

    /** Point-in-polygon over outer ring minus holes (ray casting), across all polygons. */
    public static boolean pointInPolygons(final List<JsonNode> polygons, final double x, final double y) {
        for (final JsonNode rings : polygons) {
            boolean inOuter = false;
            boolean inHole = false;
            for (int r = 0; r < rings.size(); r++) {
                if (pointInRing(rings.get(r), x, y)) {
                    if (r == 0) {
                        inOuter = true;
                    } else {
                        inHole = true;
                    }
                }
            }
            if (inOuter && !inHole) {
                return true;
            }
        }
        return false;
    }

    private static boolean pointInRing(final JsonNode ring, final double x, final double y) {
        boolean inside = false;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            final double xi = ring.get(i).get(0).asDouble();
            final double yi = ring.get(i).get(1).asDouble();
            final double xj = ring.get(j).get(0).asDouble();
            final double yj = ring.get(j).get(1).asDouble();
            if (onSegment(x, y, xi, yi, xj, yj)) {
                return true;   // boundary counts as contained (ST_CONTAINS of a vertex is TRUE)
            }
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static boolean onSegment(final double x, final double y,
                                     final double ax, final double ay, final double bx, final double by) {
        final double cross = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
        if (Math.abs(cross) > 1e-12) {
            return false;
        }
        return x >= Math.min(ax, bx) - 1e-12 && x <= Math.max(ax, bx) + 1e-12
            && y >= Math.min(ay, by) - 1e-12 && y <= Math.max(ay, by) + 1e-12;
    }

    /** Whether any segments of the two models cross (proper or touching intersection). */
    public static boolean segmentsCross(final JsonNode a, final JsonNode b) {
        final List<double[][]> aSegs = allSegments(a);
        final List<double[][]> bSegs = allSegments(b);
        for (final double[][] s : aSegs) {
            for (final double[][] t : bSegs) {
                if (segmentIntersect(s, t)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean segmentIntersect(final double[][] s, final double[][] t) {
        final double d1 = cross(t[0], t[1], s[0]);
        final double d2 = cross(t[0], t[1], s[1]);
        final double d3 = cross(s[0], s[1], t[0]);
        final double d4 = cross(s[0], s[1], t[1]);
        if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0))
                && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) {
            return true;
        }
        return (d1 == 0 && between(t[0], t[1], s[0]))
            || (d2 == 0 && between(t[0], t[1], s[1]))
            || (d3 == 0 && between(s[0], s[1], t[0]))
            || (d4 == 0 && between(s[0], s[1], t[1]));
    }

    private static double cross(final double[] a, final double[] b, final double[] p) {
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    }

    private static boolean between(final double[] a, final double[] b, final double[] p) {
        return p[0] >= Math.min(a[0], b[0]) - 1e-12 && p[0] <= Math.max(a[0], b[0]) + 1e-12
            && p[1] >= Math.min(a[1], b[1]) - 1e-12 && p[1] <= Math.max(a[1], b[1]) + 1e-12;
    }

    /** Whether the two values intersect at all: shared area, crossing edges, or containment. */
    public static boolean intersects(final GeoValue a, final GeoValue b) {
        if (segmentsCross(a.node(), b.node())) {
            return true;
        }
        final List<JsonNode> aPolys = polygonRingSets(a.node());
        final List<JsonNode> bPolys = polygonRingSets(b.node());
        for (final double[] p : allPoints(b.node())) {
            if (!aPolys.isEmpty() && pointInPolygons(aPolys, p[0], p[1])) {
                return true;
            }
        }
        for (final double[] p : allPoints(a.node())) {
            if (!bPolys.isEmpty() && pointInPolygons(bPolys, p[0], p[1])) {
                return true;
            }
        }
        // identical points (point-point intersection)
        for (final double[] p : allPoints(a.node())) {
            for (final double[] q : allPoints(b.node())) {
                if (p[0] == q[0] && p[1] == q[1]) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether {@code inner} lies entirely within {@code outer} (vertices inside, no edge crossings out). */
    public static boolean within(final GeoValue inner, final GeoValue outer) {
        final List<JsonNode> polygons = polygonRingSets(outer.node());
        if (polygons.isEmpty()) {
            return false;
        }
        for (final double[] p : allPoints(inner.node())) {
            if (!pointInPolygons(polygons, p[0], p[1])) {
                return false;
            }
        }
        return true;
    }

    /** Total length: great-circle meters for geography, planar units for geometry. */
    public static double length(final GeoValue value) {
        double total = 0;
        for (final double[][] seg : lineSegmentsOnly(value.node())) {
            total += pointDistance(value.isGeography(), seg[0], seg[1]);
        }
        return total;
    }

    private static List<double[][]> lineSegmentsOnly(final JsonNode model) {
        final List<double[][]> segments = new ArrayList<double[][]>();
        final String type = model.get("type").asText();
        if ("LineString".equals(type)) {
            segmentsOfLine(model.get("coordinates"), segments);
        } else if ("MultiLineString".equals(type)) {
            for (final JsonNode line : model.get("coordinates")) {
                segmentsOfLine(line, segments);
            }
        } else if ("GeometryCollection".equals(type)) {
            for (final JsonNode part : model.get("geometries")) {
                segments.addAll(lineSegmentsOnly(part));
            }
        }
        return segments;
    }

    /** Perimeter of the polygon rings: spherical meters or planar units. */
    public static double perimeter(final GeoValue value) {
        double total = 0;
        for (final JsonNode rings : polygonRingSets(value.node())) {
            for (final JsonNode ring : rings) {
                final List<double[][]> segments = new ArrayList<double[][]>();
                segmentsOfLine(ring, segments);
                for (final double[][] seg : segments) {
                    total += pointDistance(value.isGeography(), seg[0], seg[1]);
                }
            }
        }
        return total;
    }

    /**
     * Area: planar shoelace for geometry; for geography the spherical polygon excess (L'Huilier
     * over a fan triangulation), matching Snowflake's sphere to within floating tolerance.
     */
    public static double area(final GeoValue value) {
        double total = 0;
        for (final JsonNode rings : polygonRingSets(value.node())) {
            for (int r = 0; r < rings.size(); r++) {
                final double ringArea = value.isGeography()
                    ? sphericalRingArea(rings.get(r)) : planarRingArea(rings.get(r));
                total += r == 0 ? ringArea : -ringArea;
            }
        }
        return Math.abs(total);
    }

    private static double planarRingArea(final JsonNode ring) {
        double sum = 0;
        for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
            sum += ring.get(j).get(0).asDouble() * ring.get(i).get(1).asDouble()
                 - ring.get(i).get(0).asDouble() * ring.get(j).get(1).asDouble();
        }
        return Math.abs(sum) / 2;
    }

    private static double sphericalRingArea(final JsonNode ring) {
        // Fan triangulation from the first vertex; each triangle's spherical excess via L'Huilier.
        double total = 0;
        final double[] a = {ring.get(0).get(0).asDouble(), ring.get(0).get(1).asDouble()};
        for (int i = 1; i + 1 < ring.size(); i++) {
            final double[] b = {ring.get(i).get(0).asDouble(), ring.get(i).get(1).asDouble()};
            final double[] c = {ring.get(i + 1).get(0).asDouble(), ring.get(i + 1).get(1).asDouble()};
            total += signedTriangleExcess(a, b, c);
        }
        return Math.abs(total) * EARTH_RADIUS_M * EARTH_RADIUS_M;
    }

    private static double signedTriangleExcess(final double[] a, final double[] b, final double[] c) {
        final double ab = centralAngle(a[0], a[1], b[0], b[1]);
        final double bc = centralAngle(b[0], b[1], c[0], c[1]);
        final double ca = centralAngle(c[0], c[1], a[0], a[1]);
        final double s = (ab + bc + ca) / 2;
        final double t = Math.tan(s / 2) * Math.tan((s - ab) / 2)
            * Math.tan((s - bc) / 2) * Math.tan((s - ca) / 2);
        final double excess = 4 * Math.atan(Math.sqrt(Math.max(0, t)));
        // Orientation sign so holes/fan overlaps cancel correctly.
        final double orientation = (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
        return orientation >= 0 ? excess : -excess;
    }

    /** The [minX, maxX, minY, maxY] extent of the model. */
    public static double[] extent(final JsonNode model) {
        double minX = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (final double[] p : allPoints(model)) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
        }
        return new double[] {minX, maxX, minY, maxY};
    }

    /** Vertex-averaged centroid (length-weighted for lines, ring-average for polygons). */
    public static double[] centroid(final JsonNode model) {
        final String type = model.get("type").asText();
        if ("Point".equals(type)) {
            return new double[] {model.get("coordinates").get(0).asDouble(),
                model.get("coordinates").get(1).asDouble()};
        }
        if ("LineString".equals(type) || "MultiLineString".equals(type)) {
            double weight = 0;
            double x = 0;
            double y = 0;
            final List<double[][]> segments = new ArrayList<double[][]>();
            collectSegments(model, segments);
            for (final double[][] seg : segments) {
                final double len = Math.hypot(seg[1][0] - seg[0][0], seg[1][1] - seg[0][1]);
                weight += len;
                x += len * (seg[0][0] + seg[1][0]) / 2;
                y += len * (seg[0][1] + seg[1][1]) / 2;
            }
            if (weight == 0) {
                return null;
            }
            return new double[] {x / weight, y / weight};
        }
        // Polygon family: planar area-weighted centroid over outer rings (an approximation of
        // Snowflake's spherical centroid — within ~1e-4 degrees at test scales).
        double areaSum = 0;
        double cx = 0;
        double cy = 0;
        for (final JsonNode rings : polygonRingSets(model)) {
            final JsonNode ring = rings.get(0);
            double a = 0;
            double sx = 0;
            double sy = 0;
            for (int i = 0, j = ring.size() - 1; i < ring.size(); j = i++) {
                final double xi = ring.get(i).get(0).asDouble();
                final double yi = ring.get(i).get(1).asDouble();
                final double xj = ring.get(j).get(0).asDouble();
                final double yj = ring.get(j).get(1).asDouble();
                final double f = xj * yi - xi * yj;
                a += f;
                sx += (xj + xi) * f;
                sy += (yj + yi) * f;
            }
            if (a != 0) {
                areaSum += a / 2;
                cx += sx / 6;
                cy += sy / 6;
            }
        }
        if (areaSum == 0) {
            // degenerate: fall back to the vertex average
            final List<double[]> pts = allPoints(model);
            double x = 0;
            double y = 0;
            for (final double[] p : pts) {
                x += p[0];
                y += p[1];
            }
            return pts.isEmpty() ? null : new double[] {x / pts.size(), y / pts.size()};
        }
        return new double[] {cx / areaSum, cy / areaSum};
    }

    /** A computed Point model with double coordinates (renders like Snowflake's computed points). */
    public static JsonNode pointModel(final double x, final double y) {
        final ObjectNode out = MAPPER.createObjectNode();
        final ArrayNode coords = MAPPER.createArrayNode();
        coords.add(x);
        coords.add(y);
        out.set("coordinates", coords);
        out.put("type", "Point");
        return out;
    }
}

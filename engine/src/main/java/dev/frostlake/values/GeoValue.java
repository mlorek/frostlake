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
 * Runtime value of the GEOGRAPHY / GEOMETRY types: the GeoJSON model ({@code {"coordinates": …,
 * "type": "Point"}} with sorted keys, Snowflake's default output form) plus the KIND — geography
 * (spherical, SRID 4326) or geometry (planar, SRID configurable, default 0). Stringification is the
 * compact GeoJSON text (this engine's display policy; Snowflake pretty-prints the same JSON), and
 * equality/ordering/hashing follow it.
 */
public final class GeoValue implements Comparable<GeoValue>, Serializable {

    private static final long serialVersionUID = 1L;
    private static final JsonMapper MAPPER =
        JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();

    private final String geoJson;
    private final boolean geography;
    private final int srid;
    private transient JsonNode node;

    private GeoValue(final String geoJson, final boolean geography, final int srid) {
        this.geoJson = geoJson;
        this.geography = geography;
        this.srid = srid;
    }

    /** Wraps a parsed GeoJSON tree (keys already sorted), rendering its compact text once. */
    public static GeoValue ofNode(final JsonNode node, final boolean geography, final int srid) {
        final GeoValue v = new GeoValue(node.toString(), geography, srid);
        v.node = node;
        return v;
    }

    /** The compact GeoJSON text. */
    public String geoJson() {
        return geoJson;
    }

    /** The parsed GeoJSON tree (parsed on first access; re-parsed after deserialization). */
    public JsonNode node() {
        if (node == null) {
            node = MAPPER.readTree(geoJson);
        }
        return node;
    }

    /** TRUE for GEOGRAPHY (spherical), FALSE for GEOMETRY (planar). */
    public boolean isGeography() {
        return geography;
    }

    public int getSrid() {
        return srid;
    }

    /** The GeoJSON geometry type name ({@code Point}, {@code LineString}, …). */
    public String geoType() {
        final JsonNode type = node().get("type");
        return type == null ? "" : type.asText();
    }

    /** The SQL type name for error messages: GEOGRAPHY or GEOMETRY. */
    public String kindName() {
        return geography ? "GEOGRAPHY" : "GEOMETRY";
    }

    @Override
    public String toString() {
        return geoJson;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeoValue)) {
            return false;
        }
        final GeoValue o = (GeoValue) other;
        return geography == o.geography && geoJson.equals(o.geoJson);
    }

    @Override
    public int hashCode() {
        return geoJson.hashCode();
    }

    @Override
    public int compareTo(final GeoValue other) {
        return geoJson.compareTo(other.geoJson);
    }
}

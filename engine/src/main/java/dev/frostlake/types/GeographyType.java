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

package dev.frostlake.types;

/**
 * The GEOGRAPHY column type: spherical (lon/lat, SRID 4326) geospatial values carried as
 * {@link dev.frostlake.values.GeoValue}. Values display as GeoJSON, matching Snowflake's default
 * GEOGRAPHY output format.
 */
public class GeographyType extends DataType {

    public static final GeographyType GEOGRAPHY = new GeographyType();

    public GeographyType() {
        super("GEOGRAPHY", TypeCategory.SEMI_STRUCTURED);
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        return value;
    }

    @Override
    public String formatValue(final Object value) {
        return value == null ? "NULL" : value.toString();
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other instanceof GeographyType;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        return this;
    }

    @Override
    public int getSize() {
        return Integer.MAX_VALUE;
    }
}

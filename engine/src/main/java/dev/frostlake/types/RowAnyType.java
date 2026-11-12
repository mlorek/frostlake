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
 * The type a view gives a column its body selects as a ROW: {@code CREATE VIEW v AS SELECT a, (a, b) AS r FROM t}
 * creates the view, and DESCRIBE VIEW reports R as {@code ROW(ANY)} (live-verified). No value is ever read from such a
 * column: a read of the view refuses the ROW as its body compiles.
 */
public class RowAnyType extends DataType {

    /** The one ROW(ANY) type. */
    public static final RowAnyType ROW_ANY = new RowAnyType();

    /** The ROW(ANY) type. */
    public RowAnyType() {
        super("ROW(ANY)", TypeCategory.SEMI_STRUCTURED);
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
        return other instanceof RowAnyType;
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

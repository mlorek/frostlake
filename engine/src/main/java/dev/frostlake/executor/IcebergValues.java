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

package dev.frostlake.executor;

import dev.frostlake.types.DataType;
import dev.frostlake.types.IcebergNumericType;
import dev.frostlake.types.MapType;
import dev.frostlake.types.StructuredArrayType;
import dev.frostlake.types.StructuredField;
import dev.frostlake.types.StructuredObjectType;
import dev.frostlake.values.VariantValue;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Map;

/**
 * What an Iceberg table's column a column definition typed stores of a value an INSERT, an UPDATE or a MERGE writes
 * to it, once the value is its column's NUMBER or FLOAT (live-verified): an int or a long past its bits is refused —
 * a member of a structured value too — in words of its own and outside the statement's DML envelope, {@code Number
 * out of representable range: type int, value 2147483648}; a float keeps what its 32 bits do, {@code 0.1} as
 * {@code 0.1000000015} and anything past them as an infinity.
 */
final class IcebergValues {

    private IcebergValues() {
    }

    /**
     * The value a column stores.
     *
     * @param value the value, in its column's carrier
     * @param type  the column's type
     * @return the value stored
     */
    static Object stored(final Object value, final DataType type) {
        if (value == null) {
            return null;
        }
        if (type instanceof IcebergNumericType) {
            final IcebergNumericType number = (IcebergNumericType) type;
            if (!(value instanceof Number)) {
                return value;
            }
            if (number.isWhole()) {
                requireHeld(number, decimal((Number) value));
                return value;
            }
            if (number.isSingle()) {
                final double written = ((Number) value).doubleValue();
                final float single = (float) written;
                return Double.isNaN(written) || single == written ? value : Double.valueOf(single);
            }
            return value;
        }
        final JsonNode node = value instanceof VariantValue ? ((VariantValue) value).node()
            : value instanceof JsonNode ? (JsonNode) value : null;
        if (node != null) {
            requireMembersHeld(node, type);
        }
        return value;
    }

    /** Refuses a structured value whose whole-number member the member's type does not hold. */
    private static void requireMembersHeld(final JsonNode node, final DataType type) {
        if (node == null || node.isNull()) {
            return;
        }
        if (type instanceof IcebergNumericType) {
            final IcebergNumericType number = (IcebergNumericType) type;
            if (number.isWhole() && node.isNumber()) {
                requireHeld(number, node.decimalValue());
            }
            return;
        }
        if (type instanceof StructuredObjectType && node.isObject()) {
            for (final StructuredField field : ((StructuredObjectType) type).getFields()) {
                requireMembersHeld(node.get(field.getName()), field.getDataType());
            }
        } else if (type instanceof StructuredArrayType && node.isArray()) {
            for (final JsonNode element : node) {
                requireMembersHeld(element, ((StructuredArrayType) type).getElementType());
            }
        } else if (type instanceof MapType && node.isObject()) {
            final MapType map = (MapType) type;
            for (final Map.Entry<String, JsonNode> entry : node.properties()) {
                if (map.getKeyType() instanceof IcebergNumericType
                        && ((IcebergNumericType) map.getKeyType()).isWhole()) {
                    requireHeld((IcebergNumericType) map.getKeyType(), keyNumber(entry.getKey()));
                }
                requireMembersHeld(entry.getValue(), map.getValueType());
            }
        }
    }

    /** A MAP's key as the number it spells, or null for one that spells none. */
    private static BigDecimal keyNumber(final String key) {
        try {
            return new BigDecimal(key);
        } catch (final NumberFormatException notNumber) {
            return null;
        }
    }

    private static BigDecimal decimal(final Number value) {
        return value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
    }

    private static void requireHeld(final IcebergNumericType type, final BigDecimal value) {
        if (value != null && !type.holds(value)) {
            throw new RuntimeException("Number out of representable range: type " + type.formatName() + ", value "
                + value.stripTrailingZeros().toPlainString());
        }
    }
}

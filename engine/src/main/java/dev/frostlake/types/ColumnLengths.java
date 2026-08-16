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
 * The length a client reads for a result column: a text column's length in characters and a binary
 * column's in bytes, which Snowflake's driver answers as the column's precision and display size. Every
 * other family has none.
 *
 * <p>Live-verified through the account's own JDBC driver: VARCHAR(9) reads 9, a table's bare VARCHAR
 * 16777216, a cast to bare VARCHAR 134217728, {@code u || u} over two 16MB columns 33554432 and the NULL
 * literal's VARCHAR 0; BINARY(5) reads 5 and a table's bare BINARY 8388608, while a binary the plan never
 * sized — TO_BINARY, a cast to bare BINARY — reads the 64MB maximum, 67108864, as it does wherever its
 * width is spelled.
 */
public final class ColumnLengths {

    private ColumnLengths() {
    }

    /**
     * The length of a column of the given type.
     *
     * @param type the column's declared type
     * @return its length, or null for a type that has none
     */
    public static Integer of(final DataType type) {
        if (type instanceof UuidType) {
            return null;
        }
        if (type instanceof StringType) {
            return Integer.valueOf(((StringType) type).getMaxLength());
        }
        if (type instanceof BinaryType) {
            final BinaryType binary = (BinaryType) type;
            return Integer.valueOf(binary.getWidthSpelling() == BinaryWidthSpelling.DECLARED
                ? binary.getMaxLength() : BinaryType.NOMINAL_MAXIMUM);
        }
        return null;
    }
}

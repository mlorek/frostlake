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

import dev.frostlake.values.BinaryValue;

/**
 * The BINARY family. Snowflake reports both spellings as the type {@code BINARY} everywhere a type is
 * named — DESCRIBE, INFORMATION_SCHEMA, GET_DDL, a result column and even an argument-type refusal all
 * say {@code BINARY(8)} for a VARBINARY column — so the NAME here is always BINARY and the spelling
 * survives as {@link #isFixed}, which one metadata cell reads: SHOW COLUMNS' {@code fixed} flag.
 */
public class BinaryType extends DataType {

    private final int maxLength;

    private final boolean fixed;

    /**
     * A binary of that width, taking its fixedness from the SPELLING it was declared with. Both
     * spellings are kept as an input vocabulary — the parser, the cast targets and the derived widths
     * all name one — while the type they build reports BINARY either way.
     *
     * @param name      the spelling, BINARY or VARBINARY
     * @param maxLength the declared width
     */
    public BinaryType(final String name, final int maxLength) {
        this(maxLength, !"VARBINARY".equalsIgnoreCase(name));
    }

    /**
     * A binary of that width with its fixedness stated outright, which is what a restored snapshot and
     * a fold both need — neither has a spelling to read it off.
     *
     * @param maxLength the declared width
     * @param fixed     whether SHOW COLUMNS reports it fixed
     */
    public BinaryType(final int maxLength, final boolean fixed) {
        super("BINARY", TypeCategory.BINARY);
        this.maxLength = maxLength;
        this.fixed = fixed;
    }

    public int getMaxLength() {
        return maxLength;
    }

    /**
     * Whether this binary is the FIXED spelling, which is the whole meaning of SHOW COLUMNS'
     * {@code fixed} cell. The flag is not "this is a BINARY" — every binary reports the type BINARY.
     * It says the type was spelled BINARY rather than arrived at some other way (live-verified):
     *
     * <pre>
     *   BINARY / BINARY(4) column      true      VARBINARY(4) column            false
     *   CAST(x AS BINARY(10))          true      CAST(x AS VARBINARY(10))       false
     *   CAST(bin || bin AS BINARY)     true      bin || bin, CONCAT(bin, bin)   false
     *                                            every binary-returning function
     *                                            an X'..' literal
     * </pre>
     *
     * <p>So a CAST to BINARY RESETS it — being derived is not what makes it false — and no function
     * ever produces it, TO_BINARY included, which is the pair worth remembering: {@code TO_BINARY(s)}
     * and {@code CAST(s AS BINARY)} convert identically and differ only in this cell.
     *
     * @return true when the type was spelled BINARY
     */
    public boolean isFixed() {
        return fixed;
    }

    @Override
    public Object parseValue(final String value) {
        if (value == null || value.equalsIgnoreCase("NULL")) {
            return null;
        }
        // BINARY text is BARE hex — Snowflake's VARCHAR-to-BINARY conversion knows no 0x prefix and
        // refuses one as an illegal hex value.
        return BinaryValue.fromHex(value);
    }

    @Override
    public String formatValue(final Object value) {
        if (value == null) return "NULL";
        if (value instanceof BinaryValue) {
            return ((BinaryValue) value).toHex();
        }
        if (value instanceof byte[]) {
            return BinaryValue.of((byte[]) value).toHex();
        }
        return value.toString();
    }

    @Override
    public boolean isCompatible(final DataType other) {
        return other.getCategory() == TypeCategory.BINARY;
    }

    @Override
    public DataType getCommonType(final DataType other) {
        if (!(other instanceof BinaryType)) {
            return null;
        }
        final BinaryType otherBinary = (BinaryType) other;
        final int maxLen = Math.max(this.maxLength, otherBinary.maxLength);
        // The merged spelling, live-measured over a UNION and folded pairwise:
        //
        //   BINARY(4)    with BINARY(100)      BINARY(100)     both fixed, so the merge is
        //   BINARY(4)    with VARBINARY(4)     BINARY(4)       one fixed and no widening happened
        //   BINARY(4)    with VARBINARY(8)     VARBINARY(8)    widened by a side that is not fixed
        //   VARBINARY(8) with VARBINARY(8)     VARBINARY(8)    no fixed side to inherit from
        //
        // So: fixed when BOTH sides are, or when the widths agree and one of them is. A width nobody
        // declared cannot be a declared width.
        final boolean bothFixed = this.isFixed() && otherBinary.isFixed();
        final boolean sameWidth = this.maxLength == otherBinary.maxLength;
        final boolean eitherFixed = this.isFixed() || otherBinary.isFixed();
        return new BinaryType(maxLen, bothFixed || sameWidth && eitherFixed);
    }

    @Override
    public int getSize() {
        return maxLength > 0 ? maxLength : 8388608; // Default max size (8MB)
    }

    public static BinaryType BINARY = new BinaryType("BINARY", 8388608);
    public static BinaryType VARBINARY = new BinaryType("VARBINARY", 8388608);
}

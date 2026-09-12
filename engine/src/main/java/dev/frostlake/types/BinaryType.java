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

    private final BinaryWidthSpelling widthSpelling;

    /** The width a bare BINARY column declares, and the one a stored column settles a wider binary at. */
    public static final int DEFAULT_WIDTH = 8388608;

    /** The 64MB maximum an unsized binary is named at in a refusal. */
    public static final int NOMINAL_MAXIMUM = 67108864;

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
        this(maxLength, fixed, BinaryWidthSpelling.DECLARED);
    }

    /**
     * A binary held at that width whose width the plan spells as {@code widthSpelling} says.
     *
     * @param maxLength     the width the binary is held at
     * @param fixed         whether SHOW COLUMNS reports it fixed
     * @param widthSpelling how SYSTEM$TYPEOF and a refusal name its width
     */
    public BinaryType(final int maxLength, final boolean fixed, final BinaryWidthSpelling widthSpelling) {
        super("BINARY", TypeCategory.BINARY);
        this.maxLength = maxLength;
        this.fixed = fixed;
        this.widthSpelling = widthSpelling;
    }

    public int getMaxLength() {
        return maxLength;
    }

    /** How this binary's width is spelled — see {@link BinaryWidthSpelling}. */
    public BinaryWidthSpelling getWidthSpelling() {
        return widthSpelling;
    }

    /** The type as {@code SYSTEM$TYPEOF} names it: bare BINARY when unsized, else with its width. */
    public String typeofText() {
        if (widthSpelling == BinaryWidthSpelling.UNSIZED) {
            return "BINARY";
        }
        return "BINARY(" + (widthSpelling == BinaryWidthSpelling.MAXIMUM ? NOMINAL_MAXIMUM : maxLength) + ")";
    }

    /** The type as an argument-type refusal names it: an unsized binary at the 64MB maximum. */
    public String refusalText() {
        return "BINARY(" + (widthSpelling == BinaryWidthSpelling.DECLARED ? maxLength : NOMINAL_MAXIMUM) + ")";
    }

    /**
     * The binary a piece of this one declares — SUBSTR, LEFT, RIGHT: this one's own width, and the
     * maximum where this one is unsized (live: {@code SUBSTR(b5, 1, 1)} is BINARY(5),
     * {@code SUBSTR(TO_BINARY(s), 1, 1)} BINARY(67108864)).
     */
    public BinaryType piece() {
        return widthSpelling == BinaryWidthSpelling.DECLARED ? new BinaryType("VARBINARY", maxLength)
            : AT_MAXIMUM;
    }

    /**
     * The binary a concatenation declares, from its operands' SIZED widths added up and whether any
     * operand is unsized or at the maximum. The widths add up to the 64MB maximum — live types two 8MB
     * columns concatenated as BINARY(16777216) and an 8MB one beside a BINARY(5) as BINARY(8388613) — and
     * a sum past it, or one over an unsized operand, is unsized itself: live, both
     * {@code X'00' || TO_BINARY('00')} and {@code CAST(x AS BINARY(67108864)) || X'00'} read bare BINARY.
     * Only a stored column settles the width (see {@link #atColumnWidth}). Never the fixed spelling:
     * live reads a concatenation fixed false even over two BINARY columns.
     *
     * @param sizedWidths    the sized operands' widths, added up
     * @param unsizedOperand whether any operand is unsized or at the maximum
     * @return the concatenation's type
     */
    public static BinaryType concatenation(final long sizedWidths, final boolean unsizedOperand) {
        if (unsizedOperand || sizedWidths > NOMINAL_MAXIMUM) {
            return UNSIZED;
        }
        return new BinaryType("VARBINARY", (int) sizedWidths);
    }

    /**
     * This binary as a stored column holds it: a width past {@link #DEFAULT_WIDTH} settles at that
     * default, keeping its fixedness. Live stores a CTAS over a 16MB concatenation, or over
     * {@code CAST(x AS BINARY(67108864))}, as BINARY(8388608), and declares a view's such column the same
     * while a query over the view still reads the full width. A binary within the default is itself.
     *
     * @return the binary a stored column takes
     */
    public BinaryType atColumnWidth() {
        return maxLength > DEFAULT_WIDTH ? new BinaryType(DEFAULT_WIDTH, fixed) : this;
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
        // The width's spelling folds left to right, the leading arm deciding: TO_BINARY(s) UNION X'00'
        // is bare BINARY, X'00' UNION TO_BINARY(s) is BINARY(67108864) (live-verified).
        return new BinaryType(maxLen, bothFixed || sameWidth && eitherFixed,
            widthSpelling.meeting(otherBinary.widthSpelling));
    }

    @Override
    public int getSize() {
        return maxLength > 0 ? maxLength : 8388608; // Default max size (8MB)
    }

    public static BinaryType BINARY = new BinaryType("BINARY", 8388608);
    public static BinaryType VARBINARY = new BinaryType("VARBINARY", 8388608);

    /** The binary no width was given to: TO_BINARY's, a cast to a bare VARBINARY's, the crypto family's. */
    public static final BinaryType UNSIZED = new BinaryType(DEFAULT_WIDTH, false, BinaryWidthSpelling.UNSIZED);

    /** A binary sized at the 64MB maximum, held at the default width like every other. */
    public static final BinaryType AT_MAXIMUM = new BinaryType(DEFAULT_WIDTH, false, BinaryWidthSpelling.MAXIMUM);

    /** The unsized binary of a cast to a bare BINARY ({@code fixed}) or VARBINARY. */
    public static BinaryType unsized(final boolean fixed) {
        return fixed ? new BinaryType(DEFAULT_WIDTH, true, BinaryWidthSpelling.UNSIZED) : UNSIZED;
    }
}

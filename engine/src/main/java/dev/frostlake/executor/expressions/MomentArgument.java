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


package dev.frostlake.executor.expressions;

/**
 * An argument of a statistics aggregate as the plan's formula reads it: as printed, whether it stands bare beside an
 * operator, the family whose NULL guards it in a pair of values, and an exact number's digits.
 */
final class MomentArgument {

    private final String printed;
    private final boolean bare;
    private final String family;
    private final int leading;
    private final int scale;

    private MomentArgument(final String printed, final boolean bare, final String family, final int leading,
                           final int scale) {
        this.printed = printed;
        this.bare = bare;
        this.family = family;
        this.leading = leading;
        this.scale = scale;
    }

    /**
     * An exact number.
     *
     * @param printed the argument as printed
     * @param bare    whether it stands bare as an operand
     * @param leading its digits before the point
     * @param scale   its digits after the point
     * @return the argument
     */
    static MomentArgument exact(final String printed, final boolean bare, final int leading, final int scale) {
        return new MomentArgument(printed, bare, "FIXED", leading, scale);
    }

    /**
     * A FLOAT.
     *
     * @param printed the argument as printed
     * @param bare    whether it stands bare as an operand
     * @return the argument
     */
    static MomentArgument real(final String printed, final boolean bare) {
        return new MomentArgument(printed, bare, "REAL", 0, 0);
    }

    /**
     * A text or a VARIANT, read as the FLOAT it converts to.
     *
     * @param printed the argument as printed
     * @param family  TEXT or VARIANT
     * @return the argument
     */
    static MomentArgument converted(final String printed, final String family) {
        return new MomentArgument(printed, false, family, 0, 0);
    }

    /** The argument as printed, as a COUNT reads it. */
    String getPrinted() {
        return printed;
    }

    /** The value read: an exact number or a FLOAT as it is, a text or a VARIANT as the FLOAT it converts to. */
    PlanTerm value() {
        if ("FIXED".equals(family)) {
            return new PlanTerm(printed, bare, false, leading, scale);
        }
        if ("REAL".equals(family)) {
            return new PlanTerm(printed, bare, true, 0, 0);
        }
        return new PlanTerm("CAST(" + printed + " AS FLOAT)", false, true, 0, 0);
    }

    /**
     * This argument's member of a pair of values, which the regressions read: NULL where the other member is, typed by
     * this one's family, and a FLOAT — {@code CAST(IFF(T.F IS NULL, SYSTEM$NULL_TO_FIXED(null), T.N) AS FLOAT)}.
     *
     * @param other the pair's other member
     * @return the member
     */
    PlanTerm pairedWith(final MomentArgument other) {
        final String member = "IFF(" + other.printed + " IS NULL, SYSTEM$NULL_TO_" + family + "(null), " + printed
            + ")";
        return new PlanTerm("REAL".equals(family) ? member : "CAST(" + member + " AS FLOAT)", false, true, 0, 0);
    }
}

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
package dev.frostlake.functions.scalar;

import java.util.HashMap;
import java.util.Map;

/**
 * The fixed-point slot each whole-number argument of a function is read through, which a FLOAT argument must fit
 * (live-verified; see {@link WholeNumberArgument#requireFittingFloat}). A code point, a SUBSTR, LEFT, RIGHT or INSERT
 * position or length, a REGEXP function's position, occurrence, option or group, a GETBIT place and a RANDSTR length
 * take a {@link WholeNumberArgument#WIDE} slot; the position CHARINDEX and POSITION start from, the part STRTOK and
 * SPLIT_PART take, an ARRAY_SLICE bound, an ARRAY_INSERT or ARRAY_REMOVE_AT position, an XMLGET instance and a padded
 * length a {@link WholeNumberArgument#PART} one; a BOOLAND, BOOLOR, BOOLXOR or BOOLNOT operand is read as a whole
 * number through a {@link WholeNumberArgument#WIDE} slot too, and the scale ROUND, TRUNC, CEIL and FLOOR round an
 * exact number to through a {@link WholeNumberArgument#SCALE} one (a FLOAT's is no such slot). SPLIT_PART's slot is
 * nullable whatever its argument is; any other's nullability is its argument's.
 */
public final class WholeNumberSlots {

    /** Each function's slot by argument position, null where the argument is no whole number. */
    private static final Map<String, String[]> SLOTS = new HashMap<>();

    static {
        wide("CHAR", 0);
        wide("CHR", 0);
        wide("SUBSTR", 1, 2);
        wide("SUBSTRING", 1, 2);
        wide("LEFT", 1);
        wide("RIGHT", 1);
        wide("INSERT", 1, 2);
        wide("REGEXP_INSTR", 2, 3, 4, 6);
        wide("REGEXP_SUBSTR", 2, 3, 5);
        wide("REGEXP_COUNT", 2);
        wide("REGEXP_REPLACE", 3, 4);
        wide("REGEXP_SUBSTR_ALL", 2, 3, 5);
        wide("REGEXP_EXTRACT_ALL", 2, 3, 5);
        wide("GETBIT", 1);
        wide("RANDSTR", 0);
        part("CHARINDEX", 2);
        part("POSITION", 2);
        part("STRTOK", 2);
        part("SPLIT_PART", 2);
        part("ARRAY_SLICE", 1, 2);
        part("ARRAY_INSERT", 1);
        part("ARRAY_REMOVE_AT", 1);
        part("XMLGET", 2);
        part("LPAD", 1);
        part("RPAD", 1);
        part("SPACE", 0);
        wide("BOOLAND", 0, 1);
        wide("BOOLOR", 0, 1);
        wide("BOOLXOR", 0, 1);
        wide("BOOLNOT", 0);
        put("ROUND", WholeNumberArgument.SCALE, 1);
        put("TRUNC", WholeNumberArgument.SCALE, 1);
        put("TRUNCATE", WholeNumberArgument.SCALE, 1);
        put("CEIL", WholeNumberArgument.SCALE, 1);
        put("CEILING", WholeNumberArgument.SCALE, 1);
        put("FLOOR", WholeNumberArgument.SCALE, 1);
    }

    private WholeNumberSlots() {
    }

    /**
     * @param funcName the function's name, upper-cased
     * @param position the argument's position, from 0
     * @return the slot the argument is read through, or null when it is no whole number of a function listed here
     */
    public static String slotOf(final String funcName, final int position) {
        final String[] slots = SLOTS.get(funcName);
        return slots == null || position >= slots.length ? null : slots[position];
    }

    /**
     * @param funcName the function's name, upper-cased
     * @return whether its whole-number slot reads a scale, which only an exact number is rounded to
     */
    public static boolean readsAScale(final String funcName) {
        final String[] slots = SLOTS.get(funcName);
        return slots != null && slots.length > 1 && WholeNumberArgument.SCALE.equals(slots[1]);
    }

    /**
     * @param funcName the function's name, upper-cased
     * @return whether its slot is spelled nullable whatever the argument is
     */
    public static boolean alwaysNullable(final String funcName) {
        return "SPLIT_PART".equals(funcName);
    }

    private static void wide(final String funcName, final int... positions) {
        put(funcName, WholeNumberArgument.WIDE, positions);
    }

    private static void part(final String funcName, final int... positions) {
        put(funcName, WholeNumberArgument.PART, positions);
    }

    private static void put(final String funcName, final String slot, final int... positions) {
        int length = 0;
        for (final int position : positions) {
            length = Math.max(length, position + 1);
        }
        final String[] slots = new String[length];
        for (final int position : positions) {
            slots[position] = slot;
        }
        SLOTS.put(funcName, slots);
    }
}

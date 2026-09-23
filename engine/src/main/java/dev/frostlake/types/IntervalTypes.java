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
 * The interval type a stored type name stands for — {@code INTERVAL DAY(3) TO SECOND(3)}, the name a catalog
 * snapshot keeps for a column — found by comparing the name with every interval type's own, so a restored
 * column gets back the fields and precisions it was declared with.
 */
public final class IntervalTypes {

    private static final int MAX_PRECISION = 9;

    private IntervalTypes() {
    }

    /**
     * @param name a type's name as {@link DataType#getName} gave it
     * @return the interval type of that name, or null when no interval type has it
     */
    public static DataType forName(final String name) {
        if (name == null || !name.startsWith("INTERVAL ")) {
            return null;
        }
        for (final IntervalQualifier qualifier : IntervalQualifier.values()) {
            for (int leading = 1; leading <= MAX_PRECISION; leading++) {
                if (!qualifier.endsInSecond()) {
                    if (qualifier.typeName(leading, 0).equals(name)) {
                        return qualifier.isDayTime() ? IntervalDayTimeType.of(qualifier, leading, 0)
                            : IntervalYearMonthType.of(qualifier, leading);
                    }
                    continue;
                }
                for (int fraction = 0; fraction <= MAX_PRECISION; fraction++) {
                    if (qualifier.typeName(leading, fraction).equals(name)) {
                        return IntervalDayTimeType.of(qualifier, leading, fraction);
                    }
                }
            }
        }
        return null;
    }
}

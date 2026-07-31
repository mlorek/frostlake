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
 * The three GROUPING / SORTING key positions Snowflake names in its "cannot be used as ... keys"
 * rejection. A FILE-typed expression is legal almost everywhere — it compares, de-duplicates and
 * joins fine — but it may not be a key in any of these three positions, and the message names the
 * position: live, {@code GROUP BY f} is "Expressions of type FILE cannot be used as
 * GROUP BY keys" (SQLSTATE 42804), {@code ORDER BY f} the ORDER BY variant, and
 * {@code OVER (PARTITION BY f)} the PARTITION BY variant.
 */
public enum SortKeyRole {

    /** A GROUP BY key, including a positional ordinal, a SELECT alias and a ROLLUP/CUBE element. */
    GROUP_BY("GROUP BY"),

    /** An ORDER BY key, both the statement-level clause and the one inside an OVER spec. */
    ORDER_BY("ORDER BY"),

    /** A window PARTITION BY key. */
    PARTITION_BY("PARTITION BY");

    private final String label;

    SortKeyRole(final String label) {
        this.label = label;
    }

    /** The clause name exactly as Snowflake spells it in the rejection message. */
    public String getLabel() {
        return label;
    }
}

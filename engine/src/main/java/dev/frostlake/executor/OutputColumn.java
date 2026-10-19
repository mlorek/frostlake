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

/**
 * One output column of a select list, as an ORDER BY key names it: an alias or a star's REPLACE / RENAME column,
 * which carries an expression of its own, or a column a relation supplies under its own name.
 */
final class OutputColumn {

    /** The column's canonical name. */
    final String name;
    /** Whether the column carries an expression of its own rather than a relation's column as it stands. */
    final boolean derived;
    private final OrderKeyOutputNames owner;
    private final int item;
    private final String starExpression;

    /**
     * @param name           the column's canonical name
     * @param derived        whether it carries an expression of its own
     * @param owner          the select list's names, which write a select item's definition out
     * @param item           the select item that defines it, or -1 for a star's column
     * @param starExpression a star column's expression, or null for a select item
     */
    OutputColumn(final String name, final boolean derived, final OrderKeyOutputNames owner, final int item,
                 final String starExpression) {
        this.name = name;
        this.derived = derived;
        this.owner = owner;
        this.item = item;
        this.starExpression = starExpression;
    }

    /** The expression that defines the column, as the source rows evaluate it. */
    String definition() {
        return item >= 0 ? owner.definitionOf(item) : starExpression;
    }
}

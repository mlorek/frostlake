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

package dev.frostlake.metastore.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A CHECK constraint: a boolean expression every written row must not make FALSE. Unlike the primary,
 * unique and foreign keys — which Snowflake records but does not enforce — this one IS enforced, on
 * INSERT, UPDATE and MERGE alike (live-verified). Rows already in the table when it is added are left
 * alone, which is what {@code ENABLE NOVALIDATE} says.
 *
 * <p>The expression is kept as the TEXT it was written as, because that text is what every readback
 * surface shows: DESCRIBE's {@code check} cell, GET_DDL's constraint line and the violation message
 * all quote it verbatim, case and spacing included.
 */
public class CheckConstraint {

    private final String name;
    private final String expression;
    private final boolean autoNamed;
    private final List<String> referencedColumns;

    /**
     * @param name              the constraint's name — an explicit one, or a generated SYS_CONSTRAINT_*
     * @param expression        the boolean expression, verbatim as written
     * @param autoNamed         whether the name was generated rather than written (live quotes those
     *                          in the violation message and leaves an explicit name bare)
     * @param referencedColumns the table columns the expression names, in the order it names them
     */
    public CheckConstraint(final String name, final String expression, final boolean autoNamed,
                           final List<String> referencedColumns) {
        this.name = name;
        this.expression = expression;
        this.autoNamed = autoNamed;
        this.referencedColumns = new ArrayList<>(referencedColumns);
    }

    public String getName() { return name; }
    public String getExpression() { return expression; }
    public boolean isAutoNamed() { return autoNamed; }
    public List<String> getReferencedColumns() { return new ArrayList<>(referencedColumns); }

    /** How a violation names this constraint: an explicit name bare, a generated one in quotes. */
    public String getReportedName() {
        return autoNamed ? "\"" + name + "\"" : name;
    }

    /**
     * The single column DESCRIBE hangs this check on, or null when there is not exactly one. A check
     * spanning two columns appears in NO column's cell, and neither does one naming none (live-verified).
     */
    public String describedColumn() {
        return referencedColumns.size() == 1 ? referencedColumns.get(0) : null;
    }
}

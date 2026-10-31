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

import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A statement whose query is written in parentheses, read through them: the parentheses are transparent, so the
 * statement's ORDER BY, LIMIT and FETCH are the clauses of the select they hold, wherever they are written among the
 * levels (all live-verified):
 *
 * <pre>
 *   (SELECT a FROM tt) ORDER BY b                  sorted by b, which the select reads but does not answer
 *   ((SELECT a FROM tt) LIMIT 2) ORDER BY b DESC   sorted, then limited: the two greatest b
 *   (SELECT a FROM tt ORDER BY a) ORDER BY b       Duplicate ORDER BY: ORDER.
 *   (SELECT a FROM tt LIMIT 2) LIMIT 1             Duplicate LIMIT: LIMIT.
 *   (SELECT a FROM tt LIMIT 1) FETCH FIRST 1 ROWS ONLY   Duplicate LIMIT: FETCH.
 * </pre>
 *
 * A set operation inside the parentheses is read as its own query, whose clauses apply to its combined rows.
 */
final class ParenthesizedLevels {

    private final List<FrostlakeParser.SelectStatementContext> levels;
    private final FrostlakeParser.SelectStatementContext ordering;
    private final FrostlakeParser.SelectStatementContext limiting;

    private ParenthesizedLevels(final List<FrostlakeParser.SelectStatementContext> levels,
                                final FrostlakeParser.SelectStatementContext ordering,
                                final FrostlakeParser.SelectStatementContext limiting) {
        this.levels = levels;
        this.ordering = ordering;
        this.limiting = limiting;
    }

    /**
     * The levels of a statement whose one operand is written in parentheses, from the statement inward to the one
     * whose operand is a select, its clauses refused where two levels write the same one; null where a set operation
     * stands among them.
     *
     * @param statement the statement
     * @return the levels, or null
     */
    static ParenthesizedLevels of(final FrostlakeParser.SelectStatementContext statement) {
        final List<FrostlakeParser.SelectStatementContext> levels = new ArrayList<>();
        FrostlakeParser.SelectStatementContext level = statement;
        while (true) {
            if (level.selectOperand().size() != 1) {
                return null;
            }
            levels.add(level);
            final FrostlakeParser.SelectOperandContext operand = level.selectOperand().get(0);
            if (operand.selectStatement() == null) {
                break;
            }
            level = operand.selectStatement();
        }
        FrostlakeParser.SelectStatementContext ordering = null;
        FrostlakeParser.SelectStatementContext limiting = null;
        // From the select outward, so the clause named is the outer of the two.
        for (int i = levels.size() - 1; i >= 0; i--) {
            final FrostlakeParser.SelectStatementContext outer = levels.get(i);
            if (outer.orderByClause() != null) {
                if (ordering != null) {
                    throw new RuntimeException(SqlCompilationError.of("Duplicate ORDER BY: ORDER."));
                }
                ordering = outer;
            }
            if (outer.limitClause() != null || outer.fetchClause() != null) {
                if (limiting != null) {
                    throw new RuntimeException(SqlCompilationError.of("Duplicate LIMIT: "
                        + (outer.limitClause() != null ? "LIMIT" : "FETCH") + "."));
                }
                limiting = outer;
            }
        }
        return new ParenthesizedLevels(Collections.unmodifiableList(levels), ordering, limiting);
    }

    /** The levels, the statement first and the one whose operand is the select last. */
    List<FrostlakeParser.SelectStatementContext> levels() {
        return levels;
    }

    /** The level whose operand is the select. */
    FrostlakeParser.SelectStatementContext innermost() {
        return levels.get(levels.size() - 1);
    }

    /** The select the levels hold. */
    FrostlakeParser.SelectClauseContext select() {
        return innermost().selectOperand().get(0).selectClause();
    }

    /** The level that writes the ORDER BY, or null. */
    FrostlakeParser.SelectStatementContext ordering() {
        return ordering;
    }

    /** The level that writes the LIMIT or the FETCH, or null. */
    FrostlakeParser.SelectStatementContext limiting() {
        return limiting;
    }
}

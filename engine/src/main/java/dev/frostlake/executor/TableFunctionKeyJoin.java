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

import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A table function joined with USING or NATURAL. One whose arguments read the left side is a lateral table function,
 * and live judges its key list before anything runs: a USING column either side lacks, in list order, is
 * {@code Invalid identifier <COL>} at once, and a USING list both sides carry — like a NATURAL join over a common
 * column, or any NATURAL outer join — is the lateral restriction a join predicate meets, raised once the rest of the
 * statement has been judged. A table function that reads nothing of the left side joins by its key as any relation
 * does (live-verified).
 */
final class TableFunctionKeyJoin {

    /** The restriction a lateral table function meets with a join predicate, in live's words. */
    static final String UNSUPPORTED = "Unsupported feature 'lateral table function called with OUTER JOIN syntax "
        + "or a join predicate (ON clause)'.";

    private final boolean readsTheLeftSide;

    /**
     * Judge whether the function reads the left side.
     *
     * @param executor     the executor planning the join
     * @param reference    the table function joined
     * @param leftTable    the relation joined so far
     * @param aliasToTable the relations joined so far, by name
     * @param allTables    the relations joined so far
     */
    TableFunctionKeyJoin(final QueryExecutor executor, final FrostlakeParser.TableReferenceContext reference,
                         final Table leftTable, final Map<String, Table> aliasToTable, final List<Table> allTables) {
        final List<FrostlakeParser.QualifiedNameExprContext> names = new ArrayList<>();
        collectNames(reference.tableSource(), names);
        boolean reads = !names.isEmpty();
        final ExpressionEvaluator leftScope = new ExpressionEvaluator(leftTable, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        leftScope.setMultiTableContext(aliasToTable, allTables);
        for (final FrostlakeParser.QualifiedNameExprContext name : names) {
            try {
                leftScope.validateColumnScope(ExpressionEvaluator.parse(executor.getOriginalText(name)));
            } catch (final RuntimeException notOnTheLeft) {
                reads = false;
            }
        }
        this.readsTheLeftSide = reads;
    }

    /** Whether every name the function's arguments hold, at least one, is a column of the left side. */
    boolean readsTheLeftSide() {
        return readsTheLeftSide;
    }

    /**
     * Judge the key list of a function that reads the left side, now that its shape is known.
     *
     * @param joinCtx   the join
     * @param leftTable the relation joined so far
     * @param shape     the function's output
     * @return the lateral restriction, which waits for the statement's end, or null when the join is a plain
     *     lateral one (an inner NATURAL join over no common column)
     * @throws RuntimeException {@code Invalid identifier <COL>} for the first USING column either side lacks
     */
    static RuntimeException refusal(final FrostlakeParser.JoinClauseContext joinCtx, final Table leftTable,
                                    final Table shape) {
        if (joinCtx.USING() != null) {
            for (final FrostlakeParser.QualifiedNameContext name : joinCtx.usingColumnList().qualifiedName()) {
                final String[] parts = ParseTreeText.qualifiedNameParts(name);
                final String column = parts[parts.length - 1];
                if (!leftTable.hasColumnExactly(column) || !shape.hasColumnExactly(column)) {
                    throw new RuntimeException(SqlCompilationError.of("Invalid identifier " + column));
                }
            }
            return new RuntimeException(UNSUPPORTED);
        }
        final boolean outer = joinCtx.joinType() != null && joinCtx.joinType().INNER() == null;
        return outer || sharesAColumn(leftTable, shape) ? new RuntimeException(UNSUPPORTED) : null;
    }

    private static boolean sharesAColumn(final Table left, final Table right) {
        for (final TableColumn column : left.getColumns()) {
            if (right.hasColumnExactly(column.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Every column reference under {@code node}, a query nested in it excepted. */
    private static void collectNames(final ParseTree node, final List<FrostlakeParser.QualifiedNameExprContext> out) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            out.add((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectNames(node.getChild(i), out);
        }
    }
}

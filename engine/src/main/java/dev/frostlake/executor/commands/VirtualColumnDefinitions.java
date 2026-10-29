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

package dev.frostlake.executor.commands;

import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.OutsideWindowCalls;
import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.ExpressionEvaluatorVisitor;
import dev.frostlake.executor.expressions.ExpressionSource;
import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.executor.expressions.VirtualColumnUsage;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import dev.frostlake.types.SqlTypeNames;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The virtual columns a statement defines, judged as it compiles (live-verified for CREATE TABLE):
 *
 * <ul>
 *   <li>the expression reads the columns that are not virtual and the virtual ones defined before it, a name it
 *       does not find being an invalid identifier where it stands;</li>
 *   <li>it holds nothing a value of the row alone cannot (see {@link VirtualColumnUsage}), a call refused where it
 *       stands and anything else at the column's name: {@code b INT AS (SUM(a))} is "error line 1 at position 48"
 *       and " Invalid usage of SUM aggregate function in B virtual column definition.", the sentence opening with
 *       a blank; a bind no value was given for is "Bind variable :1 not set." at the bind, and a window call the
 *       refusal of one outside the clauses that take it (see {@link OutsideWindowCalls});</li>
 *   <li>its type is the column's, refused at the column's name: {@code b VARCHAR AS (a)} over a number is "Data
 *       type of virtual column does not match the data type of its expression for column 'B'. Expected data type
 *       'VARCHAR(16777216)', found 'NUMBER(38,0)'" (see {@link VirtualColumnTypes});</li>
 *   <li>the column takes no NOT NULL and no CHECK: "Cannot set NOT NULL constraint on virtual column B.", on the
 *       prefix's own line. A DEFAULT is taken.</li>
 * </ul>
 *
 * <p>DESCRIBE TABLE, SHOW COLUMNS and GET_DDL tell the expression back as the plan prints it: {@code a + 1} is
 * {@code A + 1}.
 */
final class VirtualColumnDefinitions {

    private VirtualColumnDefinitions() {
    }

    /**
     * Refuses a constraint a virtual column cannot carry.
     *
     * @param name       the column's name
     * @param definition the column's definition
     */
    static void refuseConstraints(final String name, final FrostlakeParser.ColumnDefContext definition) {
        for (final FrostlakeParser.ColumnConstraintContext constraint : definition.columnConstraint()) {
            final String kind = constraint.NOT() != null ? "NOT NULL" : constraint.CHECK() != null ? "CHECK" : null;
            if (kind != null) {
                throw new RuntimeException(SqlCompilationError.inline(
                    "Cannot set " + kind + " constraint on virtual column " + name + "."));
            }
        }
    }

    /**
     * Judges the virtual columns of a CREATE TABLE's column list, each over the columns it may read, and records how
     * each expression is told back.
     *
     * @param columns  the columns parsed from the list, in its order
     * @param list     the list
     * @param executor the executor whose functions the expressions call
     */
    static void judge(final List<TableColumn> columns, final FrostlakeParser.ColumnListContext list,
                      final QueryExecutor executor) {
        final List<FrostlakeParser.ColumnDefContext> definitions = new ArrayList<>();
        for (final FrostlakeParser.ColumnOrConstraintContext item : list.columnOrConstraint()) {
            if (item.columnDef() != null) {
                definitions.add(item.columnDef());
            }
        }
        for (int v = 0; v < columns.size() && v < definitions.size(); v++) {
            if (!columns.get(v).isVirtual()) {
                continue;
            }
            final List<TableColumn> scope = new ArrayList<>();
            for (int c = 0; c < columns.size(); c++) {
                if (!columns.get(c).isVirtual() || c < v) {
                    scope.add(columns.get(c));
                }
            }
            judgeOne(scope, columns.get(v), definitions.get(v), executor);
        }
    }

    /**
     * Judges a virtual column ALTER TABLE … ADD COLUMN adds, over the columns it may read, and records how its
     * expression is told back.
     *
     * @param readable   the table's columns and those the statement adds before this one
     * @param column     the column added
     * @param definition its definition
     * @param executor   the executor whose functions the expression calls
     */
    static void judgeAdded(final List<TableColumn> readable, final TableColumn column,
                           final FrostlakeParser.ColumnDefContext definition, final QueryExecutor executor) {
        judgeOne(readable, column, definition, executor);
    }

    private static void judgeOne(final List<TableColumn> readable, final TableColumn column,
                                 final FrostlakeParser.ColumnDefContext definition, final QueryExecutor executor) {
        final FrostlakeParser.BooleanExprContext body = definition.virtualColumnClause().booleanExpr();
        final Token start = body.getStart();
        final SourcePosition at = ExpressionSource.place(start.getLine(), start.getCharPositionInLine());
        if (holdsPositionalBind(body)) {
            throw usage(at, "bind variable", column);
        }
        refuseUnsetBinds(body);
        // Copies: a relation numbers the columns it is given, and these are the statement's own. Their declared types
        // are the ones the expression reads.
        final List<TableColumn> scope = new ArrayList<>();
        final List<TableColumn> echoed = new ArrayList<>();
        for (final TableColumn readableColumn : readable) {
            final TableColumn copy = readableColumn.copy();
            copy.setStaticallyTyped(true);
            scope.add(copy);
            echoed.add(readableColumn.copy());
        }
        final RuntimeException window = OutsideWindowCalls.refusalOf(body,
            new Table(tableName(definition, executor), echoed, false), executor);
        if (window != null) {
            throw window;
        }
        // A relation of no name: the expression is told back with its columns bare, A + 1.
        final Table relation = new Table("DUMMY", scope, false);
        final String written = ParseTreeText.getOriginalText(body);
        final Expression parsed = ExpressionEvaluator.parse(written);
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(relation, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        final Map<String, Table> aliases = new HashMap<>();
        aliases.put(relation.getName(), relation);
        final List<Table> relations = new ArrayList<>();
        relations.add(relation);
        evaluator.setDeclaredTypeBase(relation, aliases, relations);
        final Map<String, String> spellings = new HashMap<>();
        functionSpellings(body, spellings);
        final String refused = VirtualColumnUsage.refused(parsed, executor.getFunctionRegistry(), evaluator,
            spellings);
        if (refused != null) {
            final SourcePosition within = VirtualColumnUsage.refusedAt(parsed, executor.getFunctionRegistry(),
                evaluator, spellings);
            throw usage(within != null ? inBody(start, within) : nameOf(definition), refused, column);
        }
        final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(start.getLine(),
            start.getCharPositionInLine()));
        final DataType found;
        try {
            evaluator.validateColumnScope(parsed);
            evaluator.validateFunctionNames(parsed);
            found = evaluator.inferStaticType(parsed);
        } finally {
            ExpressionSource.end(displaced);
        }
        if (found != null && !VirtualColumnTypes.matches(column.getDataType(), found)) {
            final Token name = definition.columnDefName().getStart();
            final SourcePosition named = ExpressionSource.place(name.getLine(), name.getCharPositionInLine());
            throw new RuntimeException(SqlCompilationError.at(named.getLine(), named.getCharPositionInLine(),
                "Data type of virtual column does not match the data type of its expression for column '"
                + column.getName() + "'. Expected data type '" + SqlTypeNames.canonical(column.getDataType())
                + "', found '" + SqlTypeNames.canonical(found) + "'"));
        }
        column.setVirtualExpression(written, told(parsed, written, relation, executor));
    }

    /**
     * Refuses an ALTER TABLE … DROP COLUMN of a column a virtual column that stays reads, naming every virtual
     * column that reads it (live-verified): "Cannot drop column 'I' because virtual column 'J' depends on it", or
     * "because virtual columns 'J', 'K' depend on it", on the prefix's next line. A virtual column that reads
     * another that reads the column is not named.
     *
     * @param table    the table
     * @param dropping the names of the columns the statement drops
     */
    static void refuseDroppedReads(final Table table, final Set<String> dropping) {
        for (final TableColumn dropped : table.columnsView()) {
            if (!dropping.contains(dropped.getName())) {
                continue;
            }
            final List<String> readers = new ArrayList<>();
            for (final TableColumn column : table.columnsView()) {
                if (column.isVirtual() && !dropping.contains(column.getName()) && reads(table, column, dropped)) {
                    readers.add("'" + column.getName() + "'");
                }
            }
            if (!readers.isEmpty()) {
                throw new RuntimeException(SqlCompilationError.of("Cannot drop column '" + dropped.getName()
                    + "' because virtual column" + (readers.size() > 1 ? "s " + String.join(", ", readers)
                    + " depend" : " " + readers.get(0) + " depends") + " on it"));
            }
        }
    }

    /** Whether a virtual column's expression reads a column of its table. */
    private static boolean reads(final Table table, final TableColumn virtual, final TableColumn column) {
        for (final String read : VirtualColumnUsage.columnsRead(
                ExpressionEvaluator.parse(virtual.getVirtualExpression()))) {
            if (table.hasColumn(read) && table.getColumn(read).getName().equals(column.getName())) {
                return true;
            }
        }
        return false;
    }

    /** Where a place within the expression's text stands in the statement: the body's start composed with it. */
    private static SourcePosition inBody(final Token start, final SourcePosition within) {
        final SourcePosition displaced = ExpressionSource.beginNested(new SourcePosition(start.getLine(),
            start.getCharPositionInLine()));
        try {
            return ExpressionSource.resolve(within);
        } finally {
            ExpressionSource.end(displaced);
        }
    }

    /** Where the column's name stands in the statement. */
    private static SourcePosition nameOf(final FrostlakeParser.ColumnDefContext definition) {
        final Token name = definition.columnDefName().getStart();
        return ExpressionSource.place(name.getLine(), name.getCharPositionInLine());
    }

    /** Refuses a named or numbered bind the expression still holds: no value was given for it (live-verified). */
    private static void refuseUnsetBinds(final ParseTree node) {
        if (node instanceof FrostlakeParser.BindVarExprContext) {
            final Token colon = ((FrostlakeParser.BindVarExprContext) node).getStart();
            final SourcePosition at = ExpressionSource.place(colon.getLine(), colon.getCharPositionInLine());
            throw new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
                "Bind variable " + node.getText() + " not set."));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            refuseUnsetBinds(node.getChild(i));
        }
    }

    /** The table a column definition belongs to: the last part of the name its CREATE or ALTER gives it. */
    private static String tableName(final ParseTree node, final QueryExecutor executor) {
        for (ParseTree at = node; at != null; at = at.getParent()) {
            FrostlakeParser.ObjectNameContext name = null;
            if (at instanceof FrostlakeParser.CreateStatementContext) {
                name = ((FrostlakeParser.CreateStatementContext) at).objectName();
            } else if (at instanceof FrostlakeParser.AlterStatementContext) {
                name = ((FrostlakeParser.AlterStatementContext) at).objectName();
            }
            if (name != null) {
                final String[] parts = executor.resolveObjectNameParts(name);
                return parts[parts.length - 1];
            }
        }
        return "DUMMY";
    }

    private static RuntimeException usage(final SourcePosition at, final String refused, final TableColumn column) {
        return new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            " Invalid usage of " + refused + " in " + column.getName() + " virtual column definition."));
    }

    /** Each function name an expression writes, by its canonical name, as the expression first writes it. */
    private static void functionSpellings(final ParseTree node, final Map<String, String> into) {
        if (node instanceof FrostlakeParser.FunctionNameContext) {
            final String written = node.getText();
            final String canonical = SqlIdentifiers.canonicalText(written).toUpperCase(Locale.ROOT);
            if (!into.containsKey(canonical)) {
                into.put(canonical, written);
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            functionSpellings(node.getChild(i), into);
        }
    }

    /** Whether an expression holds a {@code ?}, which no expression tree is built over. */
    private static boolean holdsPositionalBind(final ParseTree node) {
        if (node instanceof FrostlakeParser.PositionalBindExprContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsPositionalBind(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static String told(final Expression parsed, final String written, final Table relation,
                               final QueryExecutor executor) {
        try {
            final ExpressionEvaluatorVisitor printer = new ExpressionEvaluatorVisitor(relation, null,
                executor.getFunctionRegistry(), executor.getCatalog());
            return printer.strictToldColumnDefaultText(parsed);
        } catch (final RuntimeException unprintable) {
            return written;
        }
    }
}

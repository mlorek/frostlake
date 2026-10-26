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

import dev.frostlake.executor.expressions.ColumnReferenceExpression;
import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.executor.expressions.FunctionCallExpression;
import dev.frostlake.executor.expressions.LiteralExpression;
import dev.frostlake.executor.expressions.LiteralType;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.Database;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.types.DataType;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Builds the operators EXPLAIN lists for a statement, arranged as the account arranges them (live-verified).
 *
 * <ul>
 *   <li>A SELECT reads its FROM list, then a Filter for its WHERE, an Aggregate for a GROUP BY, a DISTINCT or an
 *       aggregate in its select list (a GroupingSets for ROLLUP, CUBE and GROUPING SETS), a Filter for a HAVING — under
 *       the Aggregate for a condition that holds no aggregate — a WindowFunction, a Filter for a QUALIFY, and a Sort, a
 *       Limit or one SortWithLimit. A view and a common table expression read as the query they stand for.</li>
 *   <li>A join lists first the side that holds fewer rows. When both hold as many, an inner join lists the right
 *       one first, a FULL JOIN the left one, and a LEFT or RIGHT JOIN the side whose rows are not all kept. A
 *       condition that equates the two sides makes an InnerJoin, with a JoinFilter over the second side; any other
 *       makes a CartesianJoin, as tables written with commas and no such condition in the WHERE do. An outer join
 *       that keeps its second side filters the first, and one that keeps its first side filters the second above its
 *       JoinFilter.</li>
 *   <li>UNION ALL is one UnionAll over its operands and UNION an Aggregate over one, each operand aggregated first
 *       when there are three or more; INTERSECT is a SemiJoin and EXCEPT an AntiJoin of the aggregated operands, under
 *       two Aggregates.</li>
 *   <li>A sampled source stands under an operator the plan numbers and does not list, two of them for a sample of a
 *       fixed number of rows; FLATTEN and SPLIT_TO_TABLE are a Flatten over what stands before them.</li>
 *   <li>A DML statement is a Result naming the counts it answers, over the write, over what it reads: an UPDATE or a
 *       DELETE its target joined with its other tables, a MERGE its source joined with its target by the clauses it
 *       carries, a COPY an ExternalScan of the stage it loads or the Unload of what it reads.</li>
 *   <li>A CREATE TABLE … AS SELECT and a materialized view are a bare Result over the CreateTableAsSelect of what their
 *       query reads; any other statement an EXPLAIN takes is a bare Result.</li>
 * </ul>
 *
 * <p>Each operator lists the expressions it computes as the account resolves them, spelled by
 * {@link PlanExpressionText}: the Result the columns the query answers, a star as the columns it stands for and a
 * bare NULL left out; a scan the columns the statement reads of its table, in the table's order, and none, with no
 * partition assigned, when it reads none; a Filter its conditions, those of a derived table's and of the query over it
 * as one; a join its keys and its other conditions over both sides, and an outer join's Filter the keys it checks; an
 * Aggregate the aggregate calls it computes and the keys it groups by, a GroupingSets its aggregate calls alone; a
 * WindowFunction its windows; a Sort its keys, a Limit its row count and its offset; a union's Result each column over
 * its operands, {@code UNION_ALL(ET.A, EU.A)}. A LIMIT 0 reads a Generator of no rows, and the Result then lists
 * nothing (live-verified).
 *
 * <p>What the account's optimizer does beyond that is not reproduced: conditions stay above the join they are written
 * over, three and more tables join in the order written, a query the account answers from metadata still reads its
 * table, IN and EXISTS are no joins, a filter or a join over empty tables is no Generator, a condition the table's
 * statistics prove is kept, a Sort or a ranking window straight over a scan lists its table once, and an ORDER BY over
 * a union names the union's columns.
 */
final class PlanBuilder {

    private static final List<String> NONE = Collections.<String>emptyList();

    /** The ranking functions whose window reads its scan twice (see readTwiceForWindows). */
    private static final Set<String> SCAN_TWICE_RANKINGS = Set.of("ROW_NUMBER", "RANK", "DENSE_RANK");

    /** The offset functions whose window reads its scan twice when it partitions nothing (see readTwiceForWindows). */
    private static final Set<String> SCAN_TWICE_OFFSETS = Set.of("LAG", "LEAD");

    /** How deep views may stand in views before a plan stops reading them. */
    private static final int DEEPEST_VIEW = 32;

    private final QueryExecutor executor;

    /** The FROM list item each table reference stands for, from when its SELECT is entered until it is planned. */
    private final Map<FrostlakeParser.TableReferenceContext, PlanFromItem> pending = new IdentityHashMap<>();

    /** What each SELECT gathers while it is planned. */
    private final Map<FrostlakeParser.SelectClauseContext, PlanSelect> selects = new IdentityHashMap<>();

    /** The scans whose columns the plan resolves names to. */
    private final Set<PlanNode> resolvedScans = Collections.newSetFromMap(new IdentityHashMap<PlanNode, Boolean>());

    /** Every expression printed for the statement. */
    private final List<PlanExpressionText> printers = new ArrayList<>();
    /** The RecursiveWithClause of each recursive common table expression whose term is being planned, by its name. */
    private final Map<String, PlanNode> recursions = new HashMap<>();

    /** Whether a select item kept its written text, so the columns the statement reads are not all known. */
    private boolean itemsWritten;

    /** The database and schema the names of a view's body are read in, while that body is planned. */
    private String[] scope;

    private int viewDepth;

    PlanBuilder(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * A query's plan: a Result, computing the columns the query answers, over what the query reads; its scans list
     * the columns read.
     *
     * @param query the query
     * @return the Result
     */
    PlanNode query(final FrostlakeParser.SelectStatementContext query) {
        final PlanNode read = queryPlan(query, new HashMap<String, FrostlakeParser.SelectStatementContext>());
        final List<String> items = new ArrayList<>();
        final List<PlanColumn> answered = read.getAnswered();
        if (answered == null) {
            final FrostlakeParser.SelectClauseContext select = query.selectOperand(0).selectClause();
            if (select != null) {
                for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
                    items.add(ParseTreeText.getOriginalText(item));
                }
            }
        } else if (!readsNothing(read)) {
            for (final PlanColumn column : answered) {
                column.markReads();
                if (!column.isConstantNull()) {
                    items.add(column.getText());
                }
            }
        }
        listReads(read);
        return new PlanNode("Result", NONE, null, items).over(read);
    }

    /** Whether a query reads nothing: it is limited to no row, which the account plans as a Generator of none. */
    private static boolean readsNothing(final PlanNode read) {
        if (!"Limit".equals(read.getOperation()) && !"SortWithLimit".equals(read.getOperation())
                || read.getChildren().size() != 1) {
            return false;
        }
        final PlanNode under = read.getChildren().get(0);
        return "Generator".equals(under.getOperation()) && Long.valueOf(0).equals(under.getRows());
    }

    /**
     * Lists, on each scan of a plan that lists none yet, the columns of its table the statement reads, in the
     * table's order, and assigns no partition to a scan that reads none (live-verified). Where a name the plan does not
     * resolve leaves the columns read unknown, no scan lists them.
     */
    private void listReads(final PlanNode plan) {
        if (itemsWritten) {
            return;
        }
        for (final PlanExpressionText printer : printers) {
            if (printer.isUnresolved()) {
                return;
            }
        }
        final List<PlanNode> scans = new ArrayList<>();
        collectScans(plan, scans);
        for (final PlanNode scan : scans) {
            final Table table = scan.getExpressions().isEmpty() && resolvedScans.contains(scan) ? scannedTable(scan)
                : null;
            if (table == null) {
                continue;
            }
            for (final TableColumn column : table.getColumns()) {
                if (scan.getReadColumns().contains(column.getName())) {
                    scan.with(SqlIdentifiers.spellCanonical(column.getName()));
                }
            }
            // The columns a Sort's keys read lead a scan the Sort reads with no row limit, in the table's order too
            // (see PlanNode#readForSort).
            final List<String> sorted = new ArrayList<>();
            for (final TableColumn column : table.getColumns()) {
                if (scan.getSortColumns().contains(column.getName())) {
                    sorted.add(SqlIdentifiers.spellCanonical(column.getName()));
                }
            }
            for (int i = sorted.size() - 1; i >= 0 && !scan.getExpressions().isEmpty(); i--) {
                scan.first(sorted.get(i));
            }
            if (scan.getExpressions().isEmpty()) {
                scan.unread();
            }
        }
    }

    /**
     * A CREATE TABLE … AS SELECT's plan, and a materialized view's: a bare Result over the CreateTableAsSelect that
     * names the object, over what the query reads, whose own select list is no operator of the plan. A materialized
     * view's scans read each partition's name first (live-verified).
     *
     * @param created      the object, named in full
     * @param query        the query it is made from
     * @param materialized whether it is a materialized view
     * @return the Result
     */
    PlanNode created(final String created, final FrostlakeParser.SelectStatementContext query,
                     final boolean materialized) {
        final PlanNode write = new PlanNode("CreateTableAsSelect", NONE, null, Arrays.asList(created));
        for (final PlanNode read : query(query).getChildren()) {
            if (materialized) {
                final List<PlanNode> scans = new ArrayList<>();
                collectScans(read, scans);
                for (final PlanNode scan : scans) {
                    scan.first("METADATA$PARTITION_NAME");
                }
            }
            write.over(read);
        }
        return bareResult().over(write);
    }

    /**
     * The plan of a statement that reads nothing: a Result with no expressions.
     *
     * @return the Result
     */
    static PlanNode bareResult() {
        return new PlanNode("Result", NONE, null, NONE);
    }

    private static void collectScans(final PlanNode node, final List<PlanNode> into) {
        if ("TableScan".equals(node.getOperation())) {
            into.add(node);
        }
        for (final PlanNode child : node.getChildren()) {
            collectScans(child, into);
        }
    }

    /** The base table a scan reads, or null when it reads none the catalog holds. */
    private Table scannedTable(final PlanNode scan) {
        // A scan a Sort reads with no row limit lists its table twice (see PlanNode#readForSort).
        if (scan.getObjects().size() != (scan.isCountedTwice() ? 2 : 1)) {
            return null;
        }
        try {
            return executor.getCatalog().resolveTable(scan.getObjects().get(0));
        } catch (final RuntimeException unresolved) {
            return null;
        }
    }

    /** The name a scan's relation goes by in its plan: its alias, else its table's own name. */
    private static String relationName(final PlanNode scan) {
        if (scan.getAlias() != null) {
            return SqlIdentifiers.spellCanonical(scan.getAlias());
        }
        final String[] parts = QualifiedName.parse(scan.getObjects().get(0)).parts();
        return SqlIdentifiers.spellCanonical(parts[parts.length - 1]);
    }

    /**
     * The columns a planned FROM item answers: a table's, named by the relation, {@code ET.A}; a derived table's, a
     * view's or a common table expression's, as its query answers them and under the names a column list gives them.
     * Null for any other source, whose columns the plan does not read.
     */
    private List<PlanColumn> columnsOf(final PlanNode plan, final FrostlakeParser.TableReferenceContext reference) {
        List<PlanColumn> columns = null;
        if (plan.getAnswered() != null) {
            columns = new ArrayList<>(plan.getAnswered());
        } else if ("TableScan".equals(plan.getOperation())) {
            final Table table = scannedTable(plan);
            if (table != null) {
                resolvedScans.add(plan);
                columns = new ArrayList<>();
                for (final TableColumn column : table.getColumns()) {
                    columns.add(PlanColumn.scanned(column.getName(), relationName(plan) + "."
                        + SqlIdentifiers.spellCanonical(column.getName()), column.getDataType(),
                        new PlanRead(plan, column.getName())));
                }
            }
        }
        final FrostlakeParser.IdentifierListContext renamed = reference.identifierList();
        if (columns != null && renamed != null && renamed.identifier().size() == columns.size()) {
            for (int i = 0; i < columns.size(); i++) {
                columns.set(i, columns.get(i).named(ParseTreeText.getIdentifier(renamed.identifier(i))));
            }
        }
        return columns;
    }

    /**
     * Enters the relations of a FROM list into a SELECT's names, each awaiting its plan; a parenthesized group's own
     * relations stand in it.
     */
    private void awaitItems(final ParserRuleContext list, final PlanScope scope) {
        for (int i = 0; i < list.getChildCount(); i++) {
            final ParseTree child = list.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                awaitItem((FrostlakeParser.TableReferenceContext) child, scope);
            } else if (child instanceof FrostlakeParser.JoinClauseContext) {
                awaitItem(((FrostlakeParser.JoinClauseContext) child).tableReference(), scope);
            }
        }
    }

    private void awaitItem(final FrostlakeParser.TableReferenceContext reference, final PlanScope scope) {
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source.tableReference() != null) {
            awaitItems(source, scope);
            return;
        }
        final String alias = aliasOf(reference);
        final String key;
        if (alias != null) {
            key = alias.toUpperCase(Locale.ROOT);
        } else if (source.tableQualifiedName() != null) {
            final String[] parts = QualifiedName.parse(ParseTreeText.getQualifiedName(source.tableQualifiedName()))
                .parts();
            key = parts[parts.length - 1].toUpperCase(Locale.ROOT);
        } else {
            key = "";
        }
        final PlanFromItem item = new PlanFromItem(key);
        scope.add(item);
        pending.put(reference, item);
    }

    /**
     * The columns a SELECT answers, its select list printed in its names: an expression as the plan spells it, a star
     * as the columns it stands for, less what an EXCLUDE names and under what a RENAME names them. An item the plan
     * cannot resolve keeps its written text.
     */
    private List<PlanColumn> selected(final FrostlakeParser.SelectClauseContext select, final PlanSelect state) {
        final List<PlanColumn> columns = new ArrayList<>();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (item instanceof FrostlakeParser.ExprItemContext) {
                columns.add(itemColumn((FrostlakeParser.ExprItemContext) item, state));
                continue;
            }
            final List<PlanColumn> starred = starred(item, state.getScope());
            if (starred != null) {
                columns.addAll(starred);
            } else {
                columns.add(writtenColumn(item, null));
            }
        }
        state.getScope().select(columns);
        return columns;
    }

    private PlanColumn itemColumn(final FrostlakeParser.ExprItemContext item, final PlanSelect state) {
        String alias = null;
        if (item.aliasName() != null) {
            alias = ParseTreeText.getIdentifier(item.aliasName());
        } else if (item.identifier() != null) {
            alias = ParseTreeText.getIdentifier(item.identifier());
        } else if (item.bareAliasWord() != null) {
            alias = item.bareAliasWord().getText().toUpperCase(Locale.ROOT);
        }
        final Expression expression = parsed(item.booleanExpr());
        if (expression == null) {
            return writtenColumn(item, alias);
        }
        final PlanExpressionText printer = printer(state.getScope());
        final String text = printer.spell(expression);
        state.collect(printer);
        String name = alias;
        DataType type = null;
        if (expression instanceof ColumnReferenceExpression) {
            final PlanColumn column = state.getScope().resolve((ColumnReferenceExpression) expression);
            name = name != null ? name : ((ColumnReferenceExpression) expression).getColumnName();
            type = column == null ? null : column.getType();
        }
        return new PlanColumn(name, text, type, printer.getReads(), printer.getWindows(),
            expression instanceof LiteralExpression && ((LiteralExpression) expression).getType() == LiteralType.NULL);
    }

    private PlanColumn writtenColumn(final FrostlakeParser.SelectItemContext item, final String name) {
        itemsWritten = true;
        return new PlanColumn(name, ParseTreeText.getOriginalText(item), null, Collections.<PlanRead>emptyList(),
            Collections.<String>emptyList(), false);
    }

    /**
     * The columns a star item stands for: every relation's, or the one its qualifier names, less those its EXCLUDE
     * names and under the names its RENAME gives. Null for an item that is no star, for a star over a relation whose
     * columns the plan does not read, and for one an ILIKE or a REPLACE shapes, which keep their written text.
     */
    private static List<PlanColumn> starred(final FrostlakeParser.SelectItemContext item, final PlanScope scope) {
        final List<FrostlakeParser.StarModifierContext> modifiers;
        final List<PlanFromItem> relations = new ArrayList<>();
        if (item instanceof FrostlakeParser.StarItemContext) {
            modifiers = ((FrostlakeParser.StarItemContext) item).starModifier();
            relations.addAll(scope.getItems());
        } else if (item instanceof FrostlakeParser.QualifiedStarItemContext) {
            final FrostlakeParser.QualifiedStarItemContext qualified = (FrostlakeParser.QualifiedStarItemContext) item;
            final String[] parts = ParseTreeText.qualifiedNameParts(qualified.starQualifiedName());
            final PlanFromItem relation = scope.item(parts[parts.length - 1]);
            if (relation == null) {
                return null;
            }
            modifiers = qualified.starModifier();
            relations.add(relation);
        } else {
            return null;
        }
        final Set<String> excluded = new LinkedHashSet<>();
        final Map<String, String> renamed = new HashMap<>();
        for (final FrostlakeParser.StarModifierContext modifier : modifiers) {
            if (modifier.ILIKE() != null || modifier.REPLACE() != null) {
                return null;
            }
            for (final FrostlakeParser.ExcludedColumnContext column : modifier.excludedColumn()) {
                if (column.identifier() != null) {
                    excluded.add(ParseTreeText.getIdentifier(column.identifier()));
                }
            }
            for (final FrostlakeParser.StarRenameItemContext rename : modifier.starRenameItem()) {
                renamed.put(ParseTreeText.getIdentifier(rename.identifier(0)),
                    ParseTreeText.getIdentifier(rename.identifier(1)));
            }
        }
        final List<PlanColumn> columns = new ArrayList<>();
        for (final PlanFromItem relation : relations) {
            if (relation.getColumns() == null) {
                return null;
            }
            for (final PlanColumn column : relation.getColumns()) {
                if (!excluded.contains(column.getName())) {
                    final String name = renamed.get(column.getName());
                    columns.add(name == null ? column : column.named(name));
                }
            }
        }
        return columns;
    }

    /** An expression's tree read as an expression; null where it cannot be. */
    private static Expression parsed(final ParserRuleContext tree) {
        try {
            return ExpressionEvaluator.parse(ParseTreeText.getOriginalText(tree));
        } catch (final RuntimeException unreadable) {
            return null;
        }
    }

    private PlanExpressionText printer(final PlanScope scope) {
        final PlanExpressionText printer = new PlanExpressionText(scope, executor.getFunctionRegistry(),
            identifierCalls());
        printers.add(printer);
        return printer;
    }

    /** A call of a procedure's SYSTEM$REFERENCE token as the plan writes it (see ProcedureTokenCalls). */
    private IdentifierCallText identifierCalls() {
        return new IdentifierCallText() {
            @Override
            public String spell(final FunctionCallExpression call) {
                final Object name;
                try {
                    name = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(),
                        executor).evaluate(call.getNameExpression(), null);
                } catch (final RuntimeException unresolved) {
                    return null;
                }
                return name == null ? null
                    : ProcedureTokenCalls.planText(executor, name.toString(), call.getArguments().size());
            }
        };
    }

    /**
     * An expression of a SELECT as the plan spells it, the columns it reads recorded on their scans — those a nested
     * query reads of the SELECT's relations included — and its aggregates and windows gathered.
     */
    private String text(final ParserRuleContext tree, final PlanSelect state) {
        final Expression expression = parsed(tree);
        if (expression == null) {
            return ParseTreeText.getOriginalText(tree);
        }
        final PlanExpressionText printer = printer(state.getScope());
        final String text = printer.spell(expression);
        state.collect(printer);
        markReads(printer.getReads());
        markCorrelated(tree, state.getScope());
        return text;
    }

    /**
     * Conditions as the plan spells them, one text each; a BETWEEN is the two comparisons it stands for, each a
     * condition of its own.
     */
    private List<String> texts(final List<FrostlakeParser.BooleanExprContext> conditions, final PlanSelect state) {
        final List<String> texts = new ArrayList<>();
        for (final FrostlakeParser.BooleanExprContext condition : conditions) {
            final Expression expression = parsed(condition);
            if (expression == null) {
                texts.add(ParseTreeText.getOriginalText(condition));
                continue;
            }
            final PlanExpressionText printer = printer(state.getScope());
            texts.addAll(printer.conjuncts(expression));
            state.collect(printer);
            markReads(printer.getReads());
            markCorrelated(condition, state.getScope());
        }
        return texts;
    }

    private static void markReads(final List<PlanRead> reads) {
        for (final PlanRead read : reads) {
            read.mark();
        }
    }

    /**
     * Records the columns a nested query of a tree reads of a SELECT's relations by naming them with their
     * qualifier, as a correlated condition does.
     */
    private static void markCorrelated(final ParseTree tree, final PlanScope scope) {
        if (tree instanceof FrostlakeParser.SelectStatementContext) {
            final Set<String> own = new HashSet<>();
            ownRelations(tree, own);
            correlatedReads(tree, scope, own);
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            markCorrelated(tree.getChild(i), scope);
        }
    }

    private static void ownRelations(final ParseTree tree, final Set<String> into) {
        if (tree instanceof FrostlakeParser.TableReferenceContext) {
            final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) tree;
            final String alias = aliasOf(reference);
            if (alias != null) {
                into.add(alias.toUpperCase(Locale.ROOT));
            } else if (reference.tableSource().tableQualifiedName() != null) {
                final String[] parts = QualifiedName.parse(ParseTreeText.getQualifiedName(
                    reference.tableSource().tableQualifiedName())).parts();
                into.add(parts[parts.length - 1].toUpperCase(Locale.ROOT));
            }
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            ownRelations(tree.getChild(i), into);
        }
    }

    private static void correlatedReads(final ParseTree tree, final PlanScope scope, final Set<String> own) {
        if (tree instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) tree).qualifiedName());
            if (parts.length >= 2 && !own.contains(parts[parts.length - 2].toUpperCase(Locale.ROOT))) {
                final PlanFromItem item = scope.item(parts[parts.length - 2]);
                final PlanColumn column = item == null ? null : item.column(parts[parts.length - 1]);
                if (column != null) {
                    column.markReads();
                }
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            correlatedReads(tree.getChild(i), scope, own);
        }
    }

    /** The position a key written as a whole number names, counted from one; null for any other key. */
    private static Integer positionOf(final ParserRuleContext key) {
        final Expression expression = parsed(key);
        if (!(expression instanceof LiteralExpression)
                || ((LiteralExpression) expression).getType() != LiteralType.INTEGER
                || !(((LiteralExpression) expression).getValue() instanceof Number)) {
            return null;
        }
        final long position = ((Number) ((LiteralExpression) expression).getValue()).longValue();
        return position < 1 || position > Integer.MAX_VALUE ? null : Integer.valueOf((int) position);
    }

    /**
     * A DML statement's plan: a Result naming the counts the statement answers, over the write, over what it reads.
     *
     * @param dml the statement
     * @return the Result
     */
    PlanNode dml(final FrostlakeParser.DmlStatementContext dml) {
        final Map<String, FrostlakeParser.SelectStatementContext> none = new HashMap<>();
        if (dml.insertStatement() != null) {
            final FrostlakeParser.InsertStatementContext insert = dml.insertStatement();
            final PlanNode write = new PlanNode("Insert",
                Arrays.asList(qualified(executor.resolveObjectName(insert.objectName()))), null, NONE);
            write.over(insert.valueTupleList() != null ? values(insert.valueTupleList())
                : queryPlan(insert.selectStatement(), none));
            return counted(Arrays.asList("number of rows inserted")).over(write);
        }
        if (dml.multiTableInsertStatement() != null) {
            return severalInserts(dml.multiTableInsertStatement(), none);
        }
        if (dml.updateStatement() != null) {
            final FrostlakeParser.UpdateStatementContext update = dml.updateStatement();
            final String target = qualified(executor.resolveObjectName(update.objectName()));
            return counted(Arrays.asList("number of rows updated", "number of multi-joined rows updated"))
                .over(new PlanNode("Update", Arrays.asList(target), null, NONE)
                    .over(read(target, update, update.identifier(), update.whereClause(), false, none)));
        }
        if (dml.deleteStatement() != null) {
            final FrostlakeParser.DeleteStatementContext delete = dml.deleteStatement();
            final String target = qualified(executor.resolveObjectName(delete.objectName()));
            return counted(Arrays.asList("number of rows deleted"))
                .over(new PlanNode("Delete", Arrays.asList(target), null, NONE)
                    .over(read(target, delete, delete.identifier(), delete.whereClause(), true, none)));
        }
        if (dml.mergeStatement() != null) {
            return merged(dml.mergeStatement(), none);
        }
        return copied(dml.copyIntoStatement(), none);
    }

    /**
     * What a query reads: its operands as its set operators join them, then a Sort for an ORDER BY, a Limit for a
     * LIMIT, a FETCH or a TOP, and one SortWithLimit for both. A query with no FROM is neither sorted nor limited, and
     * a Sort over what is sorted already replaces that Sort.
     */
    private PlanNode queryPlan(final FrostlakeParser.SelectStatementContext query,
                               final Map<String, FrostlakeParser.SelectStatementContext> outer) {
        final Map<String, FrostlakeParser.SelectStatementContext> named = new HashMap<>(outer);
        if (query.withClause() != null) {
            for (final FrostlakeParser.CteDefinitionContext cte : query.withClause().cteDefinition()) {
                named.put(cte.nameStartPart().getText().toUpperCase(Locale.ROOT), cte.selectStatement());
            }
        }
        final PlanNode plan = setPlan(query, named);
        final FrostlakeParser.SelectClauseContext only =
            query.selectOperand().size() == 1 ? query.selectOperand(0).selectClause() : null;
        if (only != null && only.FROM() == null) {
            return plan;
        }
        final boolean limited = query.limitClause() != null || query.fetchClause() != null
            || only != null && only.topClause() != null;
        final Long count = limited ? limitOf(query, only) : null;
        final Long kept = limited ? keptRows(count, plan) : null;
        final List<String> limits = limited ? limitTexts(count, offsetOf(query)) : NONE;
        // A query limited to no row reads nothing, a Generator of none (live-verified).
        final boolean empty = count != null && count.longValue() == 0;
        if (query.orderByClause() != null) {
            final PlanNode sorted = "Sort".equals(plan.getOperation()) ? plan.getChildren().get(0) : plan;
            final List<String> keys = sortKeys(query.orderByClause(), only, plan.getAnswered());
            final List<String> texts = new ArrayList<>();
            if (limited) {
                texts.add("sortKey: [" + String.join(", ", keys) + "]");
                texts.addAll(limits);
            } else {
                texts.addAll(keys);
            }
            if (sorted.isScan()) {
                if (limited) {
                    sorted.readOnce();
                } else {
                    sorted.readForSort(sortKeyColumns(query.orderByClause(), only, sorted));
                }
            }
            return new PlanNode(limited ? "SortWithLimit" : "Sort", NONE, null, texts)
                .over(empty ? nothingRead() : sorted).named(plan.getNames()).holding(kept)
                .answering(plan.getAnswered());
        }
        return limited ? new PlanNode("Limit", NONE, null, limits).over(empty ? nothingRead() : plan)
            .named(plan.getNames()).holding(kept).answering(plan.getAnswered()) : plan;
    }

    /**
     * The columns of a scan that an ORDER BY's keys read, in the order the scan reads them: a key's own columns, the
     * select item a position names and the item an alias names.
     */
    private static List<String> sortKeyColumns(final FrostlakeParser.OrderByClauseContext orderBy,
                                               final FrostlakeParser.SelectClauseContext only, final PlanNode scan) {
        final Set<String> named = new HashSet<>();
        for (final FrostlakeParser.OrderItemContext item : orderBy.orderItem()) {
            final ParseTree key = keyItem(item.booleanExpr(), only);
            collectColumns(key, named);
        }
        final List<String> columns = new ArrayList<>();
        for (final String read : scan.getReadColumns()) {
            if (named.contains(read)) {
                columns.add(read);
            }
        }
        return columns;
    }

    /** What an ORDER BY key reads: the select item a position or an alias names, else the key itself. */
    private static ParseTree keyItem(final FrostlakeParser.BooleanExprContext key,
                                     final FrostlakeParser.SelectClauseContext only) {
        if (only == null || only.selectList() == null) {
            return key;
        }
        final List<FrostlakeParser.SelectItemContext> items = only.selectList().selectItem();
        final FrostlakeParser.ExpressionContext value = key instanceof FrostlakeParser.ValueExprContext
            ? ((FrostlakeParser.ValueExprContext) key).expression() : null;
        if (value instanceof FrostlakeParser.LiteralExprContext
                && ((FrostlakeParser.LiteralExprContext) value).literal().INTEGER_LITERAL() != null) {
            final int position = Integer.parseInt(
                ((FrostlakeParser.LiteralExprContext) value).literal().INTEGER_LITERAL().getText());
            return position >= 1 && position <= items.size() ? items.get(position - 1) : key;
        }
        if (value instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) value).qualifiedName());
            for (final FrostlakeParser.SelectItemContext item : items) {
                final String alias = SelectItemAccessors.getItemAlias(item);
                if (parts.length == 1 && parts[0].equals(alias)) {
                    return item;
                }
            }
        }
        return key;
    }

    /** The columns a parse tree names, each by the canonical last part of its name. */
    private static void collectColumns(final ParseTree node, final Set<String> into) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            into.add(parts[parts.length - 1]);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectColumns(node.getChild(i), into);
        }
    }

    /**
     * The keys of an ORDER BY as a Sort lists them, each with its direction and its NULLS placement: a position as
     * the select item it names, any other key as the plan spells it, an alias read as its item. The ORDER BY of a set
     * operation names the columns the operation answers.
     */
    private List<String> sortKeys(final FrostlakeParser.OrderByClauseContext orderBy,
                                  final FrostlakeParser.SelectClauseContext only, final List<PlanColumn> answered) {
        PlanSelect state = only == null ? null : selects.get(only);
        if (state == null) {
            final PlanScope scope = new PlanScope(null);
            scope.select(answered == null ? Collections.<PlanColumn>emptyList() : answered);
            state = new PlanSelect(scope);
        }
        final List<String> keys = new ArrayList<>();
        if (orderBy.allWord != null) {
            final Boolean nullsFirst = orderBy.NULLS() == null ? null : Boolean.valueOf(orderBy.FIRST() != null);
            for (final PlanColumn column : state.getScope().getSelected()) {
                column.markReads();
                keys.add(PlanExpressionText.sortKey(column.getText(), orderBy.DESC() == null, nullsFirst));
            }
        } else {
            for (final FrostlakeParser.OrderItemContext item : orderBy.orderItem()) {
                final Integer position = positionOf(item.booleanExpr());
                final PlanColumn column = position == null ? null : state.getScope().selectedAt(position.intValue());
                final String text;
                if (column != null) {
                    column.markReads();
                    text = column.getText();
                } else {
                    text = text(item.booleanExpr(), state);
                }
                keys.add(PlanExpressionText.sortKey(text, item.DESC() == null,
                    item.NULLS() == null ? null : Boolean.valueOf(item.FIRST() != null)));
            }
        }
        state.render();
        return keys;
    }

    /** What a Limit lists: its row count and its offset, each where it is written as a number. */
    private static List<String> limitTexts(final Long count, final Long offset) {
        final List<String> texts = new ArrayList<>();
        if (count != null) {
            texts.add("rowCount: " + count);
        }
        if (offset != null) {
            texts.add("offset: " + offset);
        }
        return texts;
    }

    /** The rows an OFFSET skips when it is written as a number; null otherwise. */
    private static Long offsetOf(final FrostlakeParser.SelectStatementContext query) {
        final ParserRuleContext clause = query.limitClause() != null ? query.limitClause() : query.fetchClause();
        if (clause == null) {
            return null;
        }
        for (int i = 0; i + 1 < clause.getChildCount(); i++) {
            final ParseTree child = clause.getChild(i);
            if (child instanceof TerminalNode
                    && ((TerminalNode) child).getSymbol().getType() == FrostlakeParser.OFFSET) {
                return numberOf(clause.getChild(i + 1));
            }
        }
        return null;
    }

    /** What a query limited to no row reads: a Generator of none. */
    private static PlanNode nothingRead() {
        return new PlanNode("Generator", NONE, null, Arrays.asList("0")).holding(Long.valueOf(0));
    }

    /** The rows a LIMIT, a FETCH or a TOP asks for when it is written as a number; null otherwise. */
    private static Long limitOf(final FrostlakeParser.SelectStatementContext query,
                                final FrostlakeParser.SelectClauseContext only) {
        ParseTree count = null;
        if (query.limitClause() != null) {
            count = query.limitClause().getChild(1);
        } else if (query.fetchClause() != null) {
            final FrostlakeParser.FetchClauseContext fetch = query.fetchClause();
            boolean fetching = false;
            for (int i = 0; i < fetch.getChildCount() && count == null; i++) {
                final ParseTree child = fetch.getChild(i);
                if (child == fetch.FETCH()) {
                    fetching = true;
                } else if (fetching && child != fetch.FIRST() && child != fetch.NEXT()) {
                    count = child;
                }
            }
        } else if (only != null && only.topClause() != null) {
            count = only.topClause().INTEGER_LITERAL();
        }
        return numberOf(count);
    }

    private static Long numberOf(final ParseTree written) {
        if (!(written instanceof TerminalNode)
                || ((TerminalNode) written).getSymbol().getType() != FrostlakeParser.INTEGER_LITERAL) {
            return null;
        }
        try {
            return Long.valueOf(written.getText());
        } catch (final NumberFormatException beyond) {
            return null;
        }
    }

    /** The rows a limit keeps of a plan's: the fewer of the two, as far as they are known. */
    private static Long keptRows(final Long limit, final PlanNode plan) {
        final Long rows = rowsOf(plan);
        if (limit == null || rows == null) {
            return limit == null ? rows : limit;
        }
        return Long.valueOf(Math.min(limit.longValue(), rows.longValue()));
    }

    /**
     * A query's operands under its set operators. INTERSECT joins its two operands first; the other operators are read
     * from the left, a run of UNION ALL, or of UNION, as one operator over all its operands.
     */
    private PlanNode setPlan(final FrostlakeParser.SelectStatementContext query,
                             final Map<String, FrostlakeParser.SelectStatementContext> named) {
        final List<FrostlakeParser.SelectOperandContext> operands = query.selectOperand();
        final List<FrostlakeParser.SetOperatorContext> operators = query.setOperator();
        final boolean several = operands.size() > 1;
        final List<PlanNode> terms = new ArrayList<>();
        final List<FrostlakeParser.SetOperatorContext> between = new ArrayList<>();
        PlanNode term = operandPlan(operands.get(0), named, several);
        for (int i = 0; i < operators.size(); i++) {
            final PlanNode next = operandPlan(operands.get(i + 1), named, true);
            if (operators.get(i).INTERSECT() != null) {
                term = intersected(term, next);
            } else {
                terms.add(term);
                between.add(operators.get(i));
                term = next;
            }
        }
        terms.add(term);
        List<PlanNode> run = new ArrayList<>();
        run.add(terms.get(0));
        boolean all = false;
        boolean distinct = false;
        for (int i = 0; i < between.size(); i++) {
            final FrostlakeParser.SetOperatorContext operator = between.get(i);
            final PlanNode next = terms.get(i + 1);
            final boolean union = operator.UNION() != null;
            final boolean keepsAll = union && operator.ALL() != null;
            if (union && (keepsAll ? all : distinct)) {
                run.add(next);
                continue;
            }
            final PlanNode before = united(run, all, distinct);
            run = new ArrayList<>();
            if (union) {
                run.add(before);
                run.add(next);
            } else {
                run.add(excepted(before, next));
            }
            all = keepsAll;
            distinct = union && !keepsAll;
        }
        return united(run, all, distinct);
    }

    /**
     * A run of operands under one union: as they are for UNION ALL, under an Aggregate for UNION. The union answers
     * each column over its operands, {@code UNION_ALL(ET.A, EU.A)}, which a UNION's Aggregate groups by
     * (live-verified).
     */
    private static PlanNode united(final List<PlanNode> run, final boolean all, final boolean distinct) {
        if (run.size() == 1 || !all && !distinct) {
            return run.get(0);
        }
        final List<PlanColumn> answered = unionColumns(run);
        final PlanNode union = operator("UnionAll");
        for (final PlanNode operand : run) {
            final boolean aggregated = distinct && run.size() > 2 && !"Aggregate".equals(operand.getOperation());
            union.over(aggregated ? grouped(operand.getAnswered(), operand) : operand);
        }
        return (distinct ? grouped(answered, union) : union).answering(answered);
    }

    /** The columns a union answers, each over its operands'; null where an operand's are not known. */
    private static List<PlanColumn> unionColumns(final List<PlanNode> run) {
        final List<PlanColumn> first = run.get(0).getAnswered();
        for (final PlanNode operand : run) {
            if (operand.getAnswered() == null || first == null || operand.getAnswered().size() != first.size()) {
                return null;
            }
        }
        final List<PlanColumn> columns = new ArrayList<>();
        for (int i = 0; i < first.size(); i++) {
            final List<String> texts = new ArrayList<>();
            final List<PlanRead> reads = new ArrayList<>();
            for (final PlanNode operand : run) {
                texts.add(operand.getAnswered().get(i).getText());
                reads.addAll(operand.getAnswered().get(i).getReads());
            }
            columns.add(new PlanColumn(first.get(i).getName(), "UNION_ALL(" + String.join(", ", texts) + ")",
                first.get(i).getType(), reads, Collections.<String>emptyList(), false));
        }
        return columns;
    }

    /** An Aggregate over a plan that groups by the given columns, as a set operation's aggregates do. */
    private static PlanNode grouped(final List<PlanColumn> keys, final PlanNode plan) {
        final PlanNode aggregate = operator("Aggregate").over(plan);
        if (keys != null && !keys.isEmpty()) {
            final List<String> texts = new ArrayList<>();
            for (final PlanColumn key : keys) {
                key.markReads();
                texts.add(key.getText());
            }
            aggregate.with("groupKeys: [" + String.join(", ", texts) + "]");
        }
        return aggregate;
    }

    /** What the join of two set operands lists: each column of the first equal to the second's. */
    private static List<String> operandKeys(final PlanNode first, final PlanNode second) {
        final List<PlanColumn> one = first.getAnswered();
        final List<PlanColumn> other = second.getAnswered();
        if (one == null || other == null || one.size() != other.size() || one.isEmpty()) {
            return NONE;
        }
        final StringBuilder keys = new StringBuilder();
        for (int i = 0; i < one.size(); i++) {
            keys.append(i > 0 ? " AND " : "").append('(').append(one.get(i).getText()).append(" = ")
                .append(other.get(i).getText()).append(')');
        }
        return Collections.singletonList("joinKey: " + keys);
    }

    /** INTERSECT: a SemiJoin of the two operands, each aggregated, the one that holds fewer rows first. */
    private static PlanNode intersected(final PlanNode left, final PlanNode right) {
        final boolean leftFirst = holdsFewer(left, right);
        final PlanNode first = leftFirst ? left : right;
        final PlanNode second = leftFirst ? right : left;
        final List<String> keys = operandKeys(first, second);
        return grouped(left.getAnswered(), grouped(left.getAnswered(), operator("SemiJoin").expressed(keys)
            .over(grouped(first.getAnswered(), first))
            .over(grouped(second.getAnswered(), joinFiltered(second, keys))))).answering(left.getAnswered());
    }

    /** EXCEPT: an AntiJoin of the two operands, each aggregated, the one that holds fewer rows first. */
    private static PlanNode excepted(final PlanNode kept, final PlanNode taken) {
        final PlanNode join = operator("AntiJoin");
        if (holdsFewer(kept, taken)) {
            final List<String> keys = operandKeys(kept, taken);
            join.expressed(keys).over(grouped(kept.getAnswered(), kept))
                .over(grouped(taken.getAnswered(), joinFiltered(taken, keys)));
        } else {
            join.expressed(operandKeys(taken, kept)).over(grouped(taken.getAnswered(), taken))
                .over(grouped(kept.getAnswered(), kept));
        }
        return grouped(kept.getAnswered(), grouped(kept.getAnswered(), join)).answering(kept.getAnswered());
    }

    /**
     * What one SELECT reads: its FROM list, a Filter for what its WHERE holds beyond the conditions that join the
     * list, an Aggregate, a Filter for a HAVING, a WindowFunction and a Filter for a QUALIFY. A SELECT with no FROM
     * reads a Generator of one row, or an empty ValuesClause where it calls a procedure's token. The operators list
     * what they compute, and the plan answers the select list.
     */
    private PlanNode operandPlan(final FrostlakeParser.SelectOperandContext operand,
                                 final Map<String, FrostlakeParser.SelectStatementContext> named,
                                 final boolean several) {
        if (operand.selectStatement() != null) {
            return queryPlan(operand.selectStatement(), named);
        }
        final FrostlakeParser.SelectClauseContext select = operand.selectClause();
        final PlanSelect state = new PlanSelect(new PlanScope(null));
        selects.put(select, state);
        // A sequence read is a Getnextval of its own under the Result, over the rows the FROM and the WHERE leave, or
        // over an empty ValuesClause in place of the Generator a select with no FROM reads (live-verified).
        final List<String> sequences = new ArrayList<>();
        sequenceReads(select.selectList(), sequences);
        state.getScope().readSequences(sequences);
        if (select.FROM() == null) {
            if (!sequences.isEmpty()) {
                return nextvals(sequences, new PlanNode("ValuesClause", NONE, null, Arrays.asList("()")))
                    .answering(selected(select, state));
            }
            if (ProcedureTokenCalls.holdsCall(executor, select.selectList())) {
                return new PlanNode("ValuesClause", NONE, null, Arrays.asList("()")).answering(selected(select, state));
            }
            return new PlanNode("Generator", NONE, null, Arrays.asList("1")).holding(Long.valueOf(1))
                .answering(selected(select, state));
        }
        awaitItems(select.tableExpression(), state.getScope());
        final List<FrostlakeParser.BooleanExprContext> conditions = new ArrayList<>();
        if (select.whereClause() != null) {
            conjuncts(select.whereClause().booleanExpr(), conditions);
        }
        PlanNode plan = listPlan(null, select.tableExpression(), conditions, named, state.getScope());
        final List<PlanColumn> answered = selected(select, state);
        if (!conditions.isEmpty()) {
            plan = filtered(plan, texts(conditions, state));
        }
        plan = nextvals(sequences, plan);
        final List<FrostlakeParser.BooleanExprContext> grouped = new ArrayList<>();
        final List<FrostlakeParser.BooleanExprContext> plain = new ArrayList<>();
        if (select.havingClause() != null) {
            final List<FrostlakeParser.BooleanExprContext> having = new ArrayList<>();
            conjuncts(select.havingClause().booleanExpr(), having);
            final Set<String> aggregated = aggregatedAliases(select);
            for (final FrostlakeParser.BooleanExprContext condition : having) {
                final Set<String> read = new LinkedHashSet<>();
                columnsOf(condition, read);
                (aggregates(condition) || meets(read, aggregated) ? grouped : plain).add(condition);
            }
        }
        if (!plain.isEmpty()) {
            plan = filtered(plan, texts(plain, state));
        }
        if (select.groupByClause() != null || select.DISTINCT() != null || !grouped.isEmpty()
                || aggregates(select.selectList()) || aggregates(select.qualifyClause())) {
            plan = operator(groupsBySets(select.groupByClause()) ? "GroupingSets" : "Aggregate").over(plan);
            state.aggregatedBy(plan);
            groupKeys(select, state, answered);
        }
        if (!grouped.isEmpty()) {
            plan = operator("Filter").filtering(texts(grouped, state)).over(plan);
        }
        PlanNode windowedScan = null;
        if (computesAWindow(select.selectList()) || computesAWindow(select.qualifyClause())) {
            windowedScan = plan.isScan() ? plan : null;
            plan = operator("WindowFunction").over(plan);
            state.windowedBy(plan);
        }
        if (select.qualifyClause() != null) {
            final List<FrostlakeParser.BooleanExprContext> qualifying = new ArrayList<>();
            conjuncts(select.qualifyClause().booleanExpr(), qualifying);
            plan = operator("Filter").filtering(texts(qualifying, state)).over(plan);
        }
        state.render();
        if (windowedScan != null) {
            readTwiceForWindows(select, windowedScan);
        }
        if (several && select.topClause() != null) {
            final Long count = numberOf(select.topClause().INTEGER_LITERAL());
            plan = new PlanNode("Limit", NONE, null, limitTexts(count, null)).over(plan).holding(keptRows(count, plan));
        }
        return plan.answering(answered);
    }

    /** Collects the sequences a select list reads NEXTVAL of, one for each read in the order written. */
    private static void sequenceReads(final ParseTree node, final List<String> found) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            if (parts.length > 1 && "NEXTVAL".equalsIgnoreCase(parts[parts.length - 1])) {
                found.add(parts[parts.length - 2].toUpperCase(Locale.ROOT));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            sequenceReads(node.getChild(i), found);
        }
    }

    /** A Getnextval for each sequence read over the plan, the first read lowest. */
    private static PlanNode nextvals(final List<String> sequences, final PlanNode under) {
        PlanNode plan = under;
        for (final String sequence : sequences) {
            plan = new PlanNode("Getnextval", NONE, null, Arrays.asList(SqlIdentifiers.spellCanonical(sequence)))
                .over(plan);
        }
        return plan;
    }

    /**
     * Gathers the keys a SELECT's Aggregate groups by: those of its GROUP BY, a position or an alias read as the
     * select item it names, every item that aggregates nothing for GROUP BY ALL, and for a DISTINCT its select items.
     * A ROLLUP, a CUBE and GROUPING SETS list no key; the columns they read are read all the same.
     */
    private void groupKeys(final FrostlakeParser.SelectClauseContext select, final PlanSelect state,
                           final List<PlanColumn> answered) {
        final FrostlakeParser.GroupByClauseContext groupBy = select.groupByClause();
        if (groupBy == null) {
            if (select.DISTINCT() != null) {
                for (final PlanColumn column : answered) {
                    column.markReads();
                    state.groupBy(column.getText());
                }
            }
            return;
        }
        if (groupBy.ALL() != null) {
            for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
                if (item instanceof FrostlakeParser.ExprItemContext) {
                    if (!aggregates(item)) {
                        state.groupBy(text(((FrostlakeParser.ExprItemContext) item).booleanExpr(), state));
                    }
                    continue;
                }
                final List<PlanColumn> starred = starred(item, state.getScope());
                for (final PlanColumn column : starred == null ? Collections.<PlanColumn>emptyList() : starred) {
                    column.markReads();
                    state.groupBy(column.getText());
                }
            }
            return;
        }
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            if (element.booleanExpr() == null) {
                readKeys(element, state);
                continue;
            }
            final Integer position = positionOf(element.booleanExpr());
            final PlanColumn column = position == null ? null : state.getScope().selectedAt(position.intValue());
            if (column != null) {
                column.markReads();
                state.groupBy(column.getText());
            } else {
                state.groupBy(text(element.booleanExpr(), state));
            }
        }
    }

    /** Records the columns the keys of a ROLLUP, a CUBE or GROUPING SETS read. */
    private void readKeys(final ParseTree tree, final PlanSelect state) {
        for (int i = 0; i < tree.getChildCount(); i++) {
            final ParseTree child = tree.getChild(i);
            if (child instanceof FrostlakeParser.BooleanExprContext) {
                text((FrostlakeParser.BooleanExprContext) child, state);
            } else {
                readKeys(child, state);
            }
        }
    }

    /**
     * The plan of a list of table references and joins, read from the left over what stands before it. A reference
     * after a comma joins by the conditions of the WHERE that equate it with what stands before it, which are taken
     * from the conditions then, and is a CartesianJoin without one.
     */
    private PlanNode listPlan(final PlanNode start, final ParserRuleContext list,
                              final List<FrostlakeParser.BooleanExprContext> conditions,
                              final Map<String, FrostlakeParser.SelectStatementContext> named,
                              final PlanScope scope) {
        PlanNode plan = start;
        for (int i = 0; i < list.getChildCount(); i++) {
            final ParseTree child = list.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) child;
                if (flattens(reference.tableSource())) {
                    plan = flattened(plan);
                    continue;
                }
                final PlanNode read = referencePlan(reference, named, scope);
                if (plan == null || read == null) {
                    plan = plan == null ? read : plan;
                } else {
                    plan = unconditioned(plan, read, conditions, scope);
                }
            } else if (child instanceof FrostlakeParser.JoinClauseContext) {
                plan = joined(plan, (FrostlakeParser.JoinClauseContext) child, conditions, named, scope);
            }
        }
        return plan;
    }

    /** What a JOIN makes of what stands before it and the reference it reads. */
    private PlanNode joined(final PlanNode plan, final FrostlakeParser.JoinClauseContext join,
                            final List<FrostlakeParser.BooleanExprContext> conditions,
                            final Map<String, FrostlakeParser.SelectStatementContext> named, final PlanScope scope) {
        if (flattens(join.tableReference().tableSource())) {
            return flattened(plan);
        }
        final PlanNode read = referencePlan(join.tableReference(), named, scope);
        if (plan == null || read == null) {
            return plan == null ? read : plan;
        }
        final FrostlakeParser.JoinTypeContext type = join.joinType();
        if (join.CROSS() != null || join.ON() == null && join.USING() == null && join.NATURAL() == null) {
            return unconditioned(plan, read, conditions, scope);
        }
        final PlanJoinKeys keys = new PlanJoinKeys(plan, read);
        final boolean keyed;
        if (join.ON() != null) {
            final List<FrostlakeParser.BooleanExprContext> on = new ArrayList<>();
            conjuncts(join.booleanExpr(), on);
            keyed = keyedBy(takeKeys(on, plan.getNames(), read.getNames()), on, plan, read, keys, scope);
        } else {
            keyed = true;
            sharedKeys(join, plan, read, keys, scope);
        }
        if (type != null && type.LEFT() != null) {
            return outer(plan, read, keyed, keys);
        }
        if (type != null && type.RIGHT() != null) {
            return outer(read, plan, keyed, keys);
        }
        if (type != null && type.FULL() != null) {
            final boolean leftFirst = !holdsFewer(read, plan);
            return operator("FullOuterJoin").expressed(keys.texts(leftFirst ? plan : read))
                .over(leftFirst ? plan : read).over(leftFirst ? read : plan).named(both(plan, read));
        }
        return keyed ? inner(plan, read, keys) : cartesian(plan, read, keys);
    }

    /**
     * Two sides joined with no condition of their own: an InnerJoin when a condition of the WHERE equates them, a
     * CartesianJoin otherwise. Either join takes the conditions that name columns of both sides for its own.
     */
    private PlanNode unconditioned(final PlanNode left, final PlanNode right,
                                   final List<FrostlakeParser.BooleanExprContext> conditions, final PlanScope scope) {
        final List<FrostlakeParser.BooleanExprContext> keyConditions =
            takeKeys(conditions, left.getNames(), right.getNames());
        final List<FrostlakeParser.BooleanExprContext> across = new ArrayList<>();
        for (int i = conditions.size() - 1; i >= 0; i--) {
            final Set<String> named = new LinkedHashSet<>();
            namesAColumn(conditions.get(i), named);
            if (meets(named, left.getNames()) && meets(named, right.getNames())) {
                across.add(0, conditions.remove(i));
            }
        }
        final PlanJoinKeys keys = new PlanJoinKeys(left, right);
        return keyedBy(keyConditions, across, left, right, keys, scope) ? inner(left, right, keys)
            : cartesian(left, right, keys);
    }

    /**
     * Records, as the plan spells them, the keys a join's equalities make and its other conditions over both sides —
     * a condition over one side only is not the join's — and the columns all of them read; says whether there was a
     * key.
     */
    private boolean keyedBy(final List<FrostlakeParser.BooleanExprContext> keyConditions,
                            final List<FrostlakeParser.BooleanExprContext> others, final PlanNode left,
                            final PlanNode right, final PlanJoinKeys keys, final PlanScope scope) {
        if (scope == null) {
            return !keyConditions.isEmpty();
        }
        for (final FrostlakeParser.BooleanExprContext condition : keyConditions) {
            final FrostlakeParser.ComparisonExprContext comparison = (FrostlakeParser.ComparisonExprContext)
                ((FrostlakeParser.ValueExprContext) bracketed(condition)).expression();
            final Expression one = parsed(comparison.expression(0));
            final Expression other = parsed(comparison.expression(1));
            if (one == null || other == null) {
                continue;
            }
            final PlanExpressionText onePrinter = printer(scope);
            final PlanExpressionText otherPrinter = printer(scope);
            final String oneText = onePrinter.spell(one);
            final String otherText = otherPrinter.spell(other);
            markReads(onePrinter.getReads());
            markReads(otherPrinter.getReads());
            final boolean turned = !onePrinter.getOwners().isEmpty()
                && right.getNames().containsAll(onePrinter.getOwners())
                || !otherPrinter.getOwners().isEmpty() && left.getNames().containsAll(otherPrinter.getOwners());
            keys.key(turned ? otherText : oneText, turned ? oneText : otherText);
        }
        for (final FrostlakeParser.BooleanExprContext condition : others) {
            final Expression expression = parsed(condition);
            if (expression == null) {
                continue;
            }
            final PlanExpressionText printer = printer(scope);
            final String text = printer.spell(expression);
            markReads(printer.getReads());
            if (meets(printer.getOwners(), left.getNames()) && meets(printer.getOwners(), right.getNames())) {
                keys.filter(text);
            }
        }
        return !keyConditions.isEmpty();
    }

    /** Records the keys of a USING or a NATURAL join: each column the two sides share, read on either side. */
    private static void sharedKeys(final FrostlakeParser.JoinClauseContext join, final PlanNode left,
                                   final PlanNode right, final PlanJoinKeys keys, final PlanScope scope) {
        if (scope == null) {
            return;
        }
        final List<String> shared = new ArrayList<>();
        if (join.usingColumnList() != null) {
            for (final FrostlakeParser.QualifiedNameContext column : join.usingColumnList().qualifiedName()) {
                final String[] parts = ParseTreeText.qualifiedNameParts(column);
                shared.add(parts[parts.length - 1]);
            }
        } else {
            for (final PlanFromItem item : scope.getItems()) {
                if (right.getNames().contains(item.getKey()) && item.getColumns() != null) {
                    for (final PlanColumn column : item.getColumns()) {
                        if (scope.resolveIn(left.getNames(), column.getName()) != null) {
                            shared.add(column.getName());
                        }
                    }
                }
            }
        }
        for (final String name : shared) {
            final PlanColumn one = scope.resolveIn(left.getNames(), name);
            final PlanColumn other = scope.resolveIn(right.getNames(), name);
            if (one != null && other != null) {
                one.markReads();
                other.markReads();
                keys.key(one.getText(), other.getText());
            }
        }
    }

    private static boolean meets(final Set<String> some, final Set<String> others) {
        for (final String one : some) {
            if (others.contains(one)) {
                return true;
            }
        }
        return false;
    }

    /** An InnerJoin: the side that holds fewer rows, then the other under a JoinFilter. */
    private static PlanNode inner(final PlanNode left, final PlanNode right, final PlanJoinKeys keys) {
        final boolean leftFirst = holdsFewer(left, right);
        final List<String> texts = keys.texts(leftFirst ? left : right);
        return operator("InnerJoin").expressed(texts).over(leftFirst ? left : right)
            .over(joinFiltered(leftFirst ? right : left, texts)).named(both(left, right));
    }

    private static PlanNode cartesian(final PlanNode left, final PlanNode right, final PlanJoinKeys keys) {
        final boolean leftFirst = holdsFewer(left, right) || generatorTies(right, left);
        return operator("CartesianJoin").expressed(keys.texts(leftFirst ? left : right))
            .over(leftFirst ? left : right).over(leftFirst ? right : left).named(both(left, right));
    }

    /**
     * A LeftOuterJoin, which a RIGHT JOIN is with its sides turned: the side that holds fewer rows first, and the
     * side whose rows are not all kept when both hold as many. Joined on an equality, that side is filtered when it
     * stands first, and filtered above a JoinFilter when it stands second.
     *
     * @param kept  the side whose rows are all kept
     * @param other the side whose rows are kept where they match
     * @param keyed whether the join's condition equates the two sides
     * @param keys  the join's conditions
     */
    private static PlanNode outer(final PlanNode kept, final PlanNode other, final boolean keyed,
                                  final PlanJoinKeys keys) {
        final boolean otherFirst = !holdsFewer(kept, other);
        final List<String> texts = keys.texts(otherFirst ? other : kept);
        final PlanNode join = operator("LeftOuterJoin").expressed(texts);
        if (!keyed) {
            join.over(otherFirst ? other : kept).over(otherFirst ? kept : other);
        } else if (otherFirst) {
            join.over(underFilter(other, keys.nullChecks(other))).over(kept);
        } else {
            join.over(kept).over(underFilter(joinFiltered(other, texts), keys.nullChecks(other)));
        }
        final Set<String> names = new LinkedHashSet<>(kept.getNames());
        names.addAll(other.getNames());
        return join.named(names);
    }

    /** A plan under a Filter of its own checking the given conditions; a plan that is a Filter already takes them. */
    private static PlanNode underFilter(final PlanNode plan, final List<String> checks) {
        return "Filter".equals(plan.getOperation()) ? plan.filtering(checks)
            : operator("Filter").filtering(checks).over(plan).named(plan.getNames());
    }

    /**
     * A plan under a JoinFilter listing the join's conditions, which stands under the plan's own Filters and
     * Aggregates and over each operand of a UnionAll.
     */
    private static PlanNode joinFiltered(final PlanNode plan, final List<String> keys) {
        final String operation = plan.getOperation();
        if ("UnionAll".equals(operation)
                || ("Filter".equals(operation) || "Aggregate".equals(operation)) && plan.getChildren().size() == 1) {
            final PlanNode above = plan.alike();
            for (final PlanNode child : plan.getChildren()) {
                above.over(joinFiltered(child, keys));
            }
            return above.named(plan.getNames()).answering(plan.getAnswered());
        }
        return operator("JoinFilter").expressed(keys).over(plan).named(plan.getNames());
    }

    /** A Flatten over what stands before it, or over a ValuesClause where nothing does. */
    private static PlanNode flattened(final PlanNode plan) {
        return operator("Flatten").over(plan == null ? operator("ValuesClause") : plan)
            .named(plan == null ? Collections.<String>emptySet() : plan.getNames());
    }

    private static Set<String> both(final PlanNode left, final PlanNode right) {
        final Set<String> names = new LinkedHashSet<>(left.getNames());
        names.addAll(right.getNames());
        return names;
    }

    /** Whether the first plan holds fewer rows than the second, as far as both are known. */
    private static boolean holdsFewer(final PlanNode first, final PlanNode second) {
        final Long firstRows = rowsOf(first);
        final Long secondRows = rowsOf(second);
        return firstRows != null && secondRows != null && firstRows.longValue() < secondRows.longValue();
    }

    /**
     * Whether a Generator holds as many rows as the side beside it, which then stands first: {@code FROM g1,
     * TABLE(GENERATOR(ROWCOUNT => g1.n))} over g1's one row scans g1 before the Generator of 1 (live-verified).
     */
    private static boolean generatorTies(final PlanNode generator, final PlanNode other) {
        final Long rows = rowsOf(generator);
        return "Generator".equals(generator.getOperation()) && rows != null && rows.equals(rowsOf(other));
    }

    /** The rows a plan is taken to answer: those its source holds, all of a union's, the larger side's of a join. */
    private static Long rowsOf(final PlanNode node) {
        if (node.getRows() != null) {
            return node.getRows();
        }
        long total = 0;
        long most = 0;
        for (final PlanNode child : node.getChildren()) {
            final Long rows = rowsOf(child);
            if (rows == null) {
                return null;
            }
            total += rows.longValue();
            most = Math.max(most, rows.longValue());
        }
        if (node.getChildren().isEmpty()) {
            return null;
        }
        return Long.valueOf("UnionAll".equals(node.getOperation()) ? total : most);
    }

    /**
     * What a table reference reads, under the Aggregate of a PIVOT or the Unpivot of an UNPIVOT, and under the
     * operators a SAMPLE adds, which a plan numbers and does not list: one, and two for a fixed number of rows.
     */
    private PlanNode referencePlan(final FrostlakeParser.TableReferenceContext reference,
                                   final Map<String, FrostlakeParser.SelectStatementContext> named,
                                   final PlanScope scope) {
        PlanNode plan = sourcePlan(reference, named, scope);
        if (plan == null) {
            return null;
        }
        final PlanFromItem item = pending.remove(reference);
        if (item != null && reference.pivotClause() == null && reference.unpivotClause() == null) {
            final List<PlanColumn> columns = columnsOf(plan, reference);
            if (columns != null) {
                item.bind(columns);
            }
        }
        final Set<String> names = plan.getNames();
        if (reference.pivotClause() != null) {
            plan = pivotPlan(reference.pivotClause(), plan, reference);
        } else if (reference.unpivotClause() != null) {
            plan = operator("Unpivot").over(plan);
        }
        if (reference.sampleClause() != null) {
            plan = operator("Sample").hidden().over(plan);
            if (reference.sampleClause().ROWS() != null) {
                plan = operator("Sample").hidden().over(plan);
            }
        }
        final String alias = aliasOf(reference);
        return plan.named(alias == null ? names : Collections.singleton(alias.toUpperCase(Locale.ROOT)));
    }

    /**
     * The operators a PIVOT adds over its source, as the account plans it: a Pivot alone where the source carries a
     * column the pivot keeps beside the aggregated and the FOR column; otherwise an Aggregate, over a Pivot for
     * values read as the query runs — IN (ANY) or a subquery (live-verified).
     */
    private PlanNode pivotPlan(final FrostlakeParser.PivotClauseContext pivot, final PlanNode source,
                               final FrostlakeParser.TableReferenceContext reference) {
        if (keepsColumns(pivot, source, reference)) {
            return operator("Pivot").over(source);
        }
        final boolean listed = pivot.pivotInList().pivotValueList() != null;
        return operator("Aggregate").over(listed ? source : operator("Pivot").over(source));
    }

    /** Whether a pivot's source carries a column beside the aggregated and the FOR column; false when unknown. */
    private boolean keepsColumns(final FrostlakeParser.PivotClauseContext pivot, final PlanNode source,
                                 final FrostlakeParser.TableReferenceContext reference) {
        final List<String> columns = sourceColumns(source, reference);
        final Expression argument;
        try {
            argument = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(pivot.aggregateFunction().expression()));
        } catch (final RuntimeException unreadable) {
            return false;
        }
        if (columns == null || !(argument instanceof ColumnReferenceExpression)) {
            return false;
        }
        final String aggregated = ((ColumnReferenceExpression) argument).getColumnName();
        final String forColumn = ParseTreeText.getIdentifier(pivot.pivotColumn);
        for (final String column : columns) {
            if (!column.equalsIgnoreCase(aggregated) && !column.equalsIgnoreCase(forColumn)) {
                return true;
            }
        }
        return false;
    }

    /** The columns a pivot's source answers: a table's, or a derived table's; null for any other source. */
    private List<String> sourceColumns(final PlanNode source, final FrostlakeParser.TableReferenceContext reference) {
        final List<String> names = new ArrayList<>();
        if (reference.tableSource().selectStatement() != null) {
            final List<TableColumn> shape;
            try {
                shape = executor.resolveRelationShape(
                    ParseTreeText.getOriginalText(reference.tableSource().selectStatement()), null);
            } catch (final RuntimeException unresolved) {
                return null;
            }
            if (shape == null) {
                return null;
            }
            for (final TableColumn column : shape) {
                names.add(column.getName());
            }
            return names;
        }
        final Table table = "TableScan".equals(source.getOperation()) ? scannedTable(source) : null;
        if (table == null) {
            return null;
        }
        for (final TableColumn column : table.getColumns()) {
            names.add(column.getName());
        }
        return names;
    }

    private static String aliasOf(final FrostlakeParser.TableReferenceContext reference) {
        if (reference.aliasName() != null) {
            return ParseTreeText.getIdentifier(reference.aliasName());
        }
        return reference.nonJoinKeywordIdentifier() == null ? null
            : reference.nonJoinKeywordIdentifier().getText().toUpperCase(Locale.ROOT);
    }

    /**
     * What a table source reads: a TableScan of a table, the plan of a derived table, of a view or of the common
     * table expression a name stands for, a ValuesClause, a Generator, the joins of a parenthesized group — and no
     * operator for any other source.
     */
    private PlanNode sourcePlan(final FrostlakeParser.TableReferenceContext reference,
                                final Map<String, FrostlakeParser.SelectStatementContext> named,
                                final PlanScope scope) {
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source.tableQualifiedName() != null) {
            final String written = ParseTreeText.getQualifiedName(source.tableQualifiedName());
            final String[] parts = QualifiedName.parse(written).parts();
            final Set<String> name = Collections.singleton(parts[parts.length - 1].toUpperCase(Locale.ROOT));
            final PlanNode recursion = recursions.get(written.toUpperCase(Locale.ROOT));
            if (recursion != null) {
                // The recursive term reads its own CTE: the clause it feeds back, no operator of its own.
                return new PlanNode("WithReference", NONE, null, NONE).answering(recursion.getAnswered())
                    .readingRecursion(recursion).named(name);
            }
            final FrostlakeParser.SelectStatementContext expression = named.get(written.toUpperCase(Locale.ROOT));
            if (expression != null) {
                final Map<String, FrostlakeParser.SelectStatementContext> others = new HashMap<>(named);
                others.remove(written.toUpperCase(Locale.ROOT));
                final PlanNode recursive = recursivePlan(expression, written, others);
                if (recursive != null) {
                    return recursive.named(name);
                }
                return underListedNames(queryPlan(expression, others), expression).named(name);
            }
            final PlanNode viewed = viewPlan(written);
            return viewed != null ? viewed.named(name) : scan(qualified(written), aliasOf(reference)).named(name);
        }
        if (source.selectStatement() != null) {
            return queryPlan(source.selectStatement(), named).named(Collections.<String>emptySet());
        }
        if (source.valueTupleList() != null) {
            return values(source.valueTupleList());
        }
        if (source.tableReference() != null) {
            return listPlan(null, source, new ArrayList<FrostlakeParser.BooleanExprContext>(), named, scope);
        }
        final String queried = queriedText(source);
        if (queried != null) {
            // A TO_QUERY handed its text as a literal reads as that query, planned where it stands (live-verified).
            final FrostlakeParser.SelectStatementContext inner = executor.subqueryStatement(queried);
            return inner == null ? calledPlan(queried)
                : queryPlan(inner, new HashMap<String, FrostlakeParser.SelectStatementContext>())
                    .named(Collections.<String>emptySet());
        }
        if ("GENERATOR".equals(calledName(source))) {
            final Long rows = rowCountOf(source) != null ? rowCountOf(source) : pinnedRowCount(source);
            return new PlanNode("Generator", NONE, null,
                rows == null ? NONE : Arrays.asList(rows.toString())).holding(rows);
        }
        return null;
    }

    /**
     * A recursive common table expression as the account plans it (live-verified): a RecursiveWithClause named by the
     * CTE over its anchor and its recursive term, the term reading the clause back where it reads the CTE, and the
     * CTE's columns answered under its name, {@code R.N}; an anchor with no FROM is a ValuesClause, {@code ()}. Null
     * for a CTE that does not read itself, or whose body is other than one anchor and one term after it.
     */
    private PlanNode recursivePlan(final FrostlakeParser.SelectStatementContext body, final String written,
                                   final Map<String, FrostlakeParser.SelectStatementContext> others) {
        final String key = written.toUpperCase(Locale.ROOT);
        final List<FrostlakeParser.SelectOperandContext> operands = body.selectOperand();
        if (operands.size() != 2 || RecursiveViewChain.reads(operands.get(0), key)
                || !RecursiveViewChain.reads(operands.get(1), key)) {
            return null;
        }
        final PlanNode anchor = underListedNames(operandPlan(operands.get(0), others, false), body);
        if (anchor.getAnswered() == null) {
            return null;
        }
        final String[] parts = QualifiedName.parse(written).parts();
        final String spelled = SqlIdentifiers.spellCanonical(parts[parts.length - 1]);
        final List<PlanColumn> columns = new ArrayList<>();
        for (final PlanColumn column : anchor.getAnswered()) {
            columns.add(new PlanColumn(column.getName(), column.getName() == null ? column.getText()
                : spelled + "." + SqlIdentifiers.spellCanonical(column.getName()), column.getType(),
                Collections.<PlanRead>emptyList(), Collections.<String>emptyList(), false));
        }
        final PlanNode recursion = operator("RecursiveWithClause").with(spelled).answering(columns);
        final FrostlakeParser.SelectClauseContext first = operands.get(0).selectClause();
        recursion.over(first != null && first.FROM() == null
            ? new PlanNode("ValuesClause", NONE, null, Arrays.asList("()")) : anchor);
        recursions.put(key, recursion);
        try {
            recursion.over(operandPlan(operands.get(1), others, false));
        } finally {
            recursions.remove(key);
        }
        return recursion;
    }

    /** A common table expression's plan, answering its columns under the names its column list gives them. */
    private static PlanNode underListedNames(final PlanNode plan, final FrostlakeParser.SelectStatementContext body) {
        if (!(body.getParent() instanceof FrostlakeParser.CteDefinitionContext) || plan.getAnswered() == null) {
            return plan;
        }
        final FrostlakeParser.ColumnListOptionalContext listed =
            ((FrostlakeParser.CteDefinitionContext) body.getParent()).columnListOptional();
        if (listed == null || listed.namePart().size() != plan.getAnswered().size()) {
            return plan;
        }
        final List<PlanColumn> renamed = new ArrayList<>();
        for (int i = 0; i < listed.namePart().size(); i++) {
            renamed.add(plan.getAnswered().get(i).named(ParseTreeText.namePartText(listed.namePart(i))));
        }
        return plan.answering(renamed);
    }

    /** The plan of the query a view stands for, its names read in the view's own schema; null for no view. */
    private PlanNode viewPlan(final String written) {
        final View view;
        final String[] home;
        try {
            view = executor.getCatalog().resolveView(scoped(written));
            if (view == null || view.getDefinition() == null || viewDepth >= DEEPEST_VIEW) {
                return null;
            }
            home = QualifiedName.parse(executor.getCatalog().qualifiedObjectName(scoped(written))).parts();
        } catch (final RuntimeException notAView) {
            return null;
        }
        final String[] before = scope;
        scope = home.length == 3 ? new String[] {home[0], home[1]} : before;
        viewDepth++;
        try {
            return queryPlan(executor.subqueryStatement(view.getDefinition()),
                new HashMap<String, FrostlakeParser.SelectStatementContext>());
        } catch (final RuntimeException unread) {
            return null;
        } finally {
            viewDepth--;
            scope = before;
        }
    }

    /** A name as it is read where it stands: a bare name inside a view's body belongs to the view's schema. */
    private String scoped(final String written) {
        if (scope == null) {
            return written;
        }
        final String[] parts = QualifiedName.parse(written).parts();
        if (parts.length == 1) {
            return QualifiedName.join(scope[0], scope[1], parts[0]);
        }
        return parts.length == 2 ? QualifiedName.join(scope[0], parts[0], parts[1]) : written;
    }

    /** A table's name in full, as a plan names the object it reads; the name as written when it cannot be placed. */
    private String qualified(final String written) {
        try {
            return executor.getCatalog().qualifiedObjectName(scoped(written));
        } catch (final RuntimeException unplaced) {
            return written.toUpperCase(Locale.ROOT);
        }
    }

    /** A scan of a table, with the rows the table holds. */
    private PlanNode scan(final String object, final String alias) {
        long rows;
        try {
            rows = executor.getStorageEngine().storedRowCount(object, false);
        } catch (final RuntimeException unread) {
            rows = 0;
        }
        return new PlanNode("TableScan", Arrays.asList(object), alias, NONE).scanning(rows);
    }

    private static PlanNode values(final FrostlakeParser.ValueTupleListContext tuples) {
        return new PlanNode("ValuesClause", NONE, null, Arrays.asList(ParseTreeText.getOriginalText(tuples)))
            .holding(Long.valueOf(tuples.valueTuple().size()));
    }

    private static PlanNode operator(final String operation) {
        return new PlanNode(operation, NONE, null, NONE);
    }

    private static PlanNode counted(final List<String> counts) {
        return new PlanNode("Result", NONE, null, counts);
    }

    /** A Filter over a plan; a plan that is a Filter already takes the conditions as more of its own. */
    private static PlanNode filtered(final PlanNode plan, final List<String> conditions) {
        if (plan != null && "Filter".equals(plan.getOperation())) {
            return plan.filtering(conditions);
        }
        final PlanNode filter = operator("Filter").filtering(conditions).over(plan);
        return plan == null ? filter : filter.named(plan.getNames());
    }

    /** Whether a source is a FLATTEN or a SPLIT_TO_TABLE, which a plan lists as a Flatten. */
    private static boolean flattens(final FrostlakeParser.TableSourceContext source) {
        if (source.FLATTEN() != null) {
            return true;
        }
        final String called = calledName(source);
        return "FLATTEN".equals(called) || "SPLIT_TO_TABLE".equals(called);
    }

    /** The name of the table function a source calls, upper-cased; null for a source that calls none. */
    private static String calledName(final FrostlakeParser.TableSourceContext source) {
        final ParseTree call = source.tableFunctionExpr() != null ? source.tableFunctionExpr()
            : source.TABLE() != null ? source.expression() : null;
        if (call == null) {
            return null;
        }
        for (int i = 0; i < call.getChildCount(); i++) {
            if (call.getChild(i) instanceof FrostlakeParser.FunctionNameContext) {
                return call.getChild(i).getText().toUpperCase(Locale.ROOT);
            }
        }
        return null;
    }

    /**
     * The source a TO_QUERY handed a CALL reads: an empty ValuesClause answering the call as its one column, the
     * procedure spelled with its parameters' names, {@code P_W2(X)} (live-verified); null for any other text.
     */
    private PlanNode calledPlan(final String text) {
        final FrostlakeParser.SqlScriptContext script;
        try {
            script = QueryExecutor.parsedScriptQuietly(text);
        } catch (final RuntimeException unreadable) {
            return null;
        }
        if (script.flowChain().size() != 1 || script.flowChain(0).statement().size() != 1) {
            return null;
        }
        final FrostlakeParser.StatementContext statement = script.flowChain(0).statement(0);
        if (statement.proceduralStatement() == null || statement.proceduralStatement().callStatement() == null
                || statement.proceduralStatement().callStatement().qualifiedName() == null) {
            return null;
        }
        final FrostlakeParser.CallStatementContext call = statement.proceduralStatement().callStatement();
        final String[] parts = ParseTreeText.qualifiedNameParts(call.qualifiedName());
        final Procedure procedure = calledProcedure(parts,
            call.callArguments() == null ? 0 : call.callArguments().callArgument().size());
        final String name = SqlIdentifiers.spellCanonical(procedure != null ? procedure.getName()
            : parts[parts.length - 1].toUpperCase(Locale.ROOT));
        final StringBuilder spelled = new StringBuilder(name).append('(');
        if (procedure != null) {
            for (int i = 0; i < procedure.getParameters().size(); i++) {
                final Parameter parameter = procedure.getParameters().get(i);
                spelled.append(i > 0 ? ", " : "").append(SqlIdentifiers.spellCanonical(parameter.getName()));
            }
        }
        spelled.append(')');
        return new PlanNode("ValuesClause", NONE, null, Arrays.asList("()")).answering(Collections.singletonList(
            new PlanColumn(name, spelled.toString(), null, Collections.<PlanRead>emptyList(),
                Collections.<String>emptyList(), false)));
    }

    /** The procedure a CALL's name reaches with as many parameters as it writes arguments; null for none. */
    private Procedure calledProcedure(final String[] parts, final int arguments) {
        final List<Schema> schemas = new ArrayList<>();
        if (parts.length == 1) {
            schemas.addAll(executor.searchPathSchemas());
        } else if (parts.length <= 3) {
            final String databaseName = parts.length == 3 ? parts[0] : executor.getCatalog().getCurrentDatabase();
            final Database database = databaseName == null ? null : executor.getCatalog().getDatabase(databaseName);
            final Schema schema = database == null ? null : database.getSchema(parts[parts.length - 2]);
            if (schema != null) {
                schemas.add(schema);
            }
        }
        for (final Schema schema : schemas) {
            for (final Procedure procedure : schema.getProcedureOverloads(parts[parts.length - 1])) {
                if (procedure.getParameters().size() == arguments) {
                    return procedure;
                }
            }
        }
        return null;
    }

    /** The query a TO_QUERY is handed as a string literal, first or by its SQL name, with no bind; null otherwise. */
    private static String queriedText(final FrostlakeParser.TableSourceContext source) {
        ParserRuleContext text = null;
        if (source.tableFunctionExpr() != null) {
            if (!"TO_QUERY".equals(calledName(source))) {
                return null;
            }
            final FrostlakeParser.TableFunctionExprContext call = source.tableFunctionExpr();
            text = call.expression().isEmpty() ? null : call.expression(0);
            for (final FrostlakeParser.NamedArgumentContext argument : call.namedArgument()) {
                if (!"SQL".equalsIgnoreCase(argument.variableName().getText())) {
                    return null;
                }
                text = argument.expression();
            }
        } else if (source.TABLE() != null && source.expression() != null) {
            // TABLE(TO_QUERY('…')) with its text alone reads as a call within an expression.
            final FrostlakeParser.FunctionCallExprContext call = onlyCall(source.expression());
            if (call == null || call.functionName() == null
                    || !"TO_QUERY".equalsIgnoreCase(call.functionName().getText()) || call.functionArgList() == null
                    || call.functionArgList().functionArg().size() != 1) {
                return null;
            }
            text = call.functionArgList().functionArg(0).booleanExpr();
        }
        if (text == null) {
            return null;
        }
        final Expression parsed = ExpressionEvaluator.parse(ParseTreeText.getOriginalText(text));
        return parsed instanceof LiteralExpression && ((LiteralExpression) parsed).getValue() instanceof String
            ? (String) ((LiteralExpression) parsed).getValue() : null;
    }

    /** The call a tree is, through the single children around it; null when it is anything else. */
    private static FrostlakeParser.FunctionCallExprContext onlyCall(final ParseTree tree) {
        ParseTree at = tree;
        while (at != null && !(at instanceof FrostlakeParser.FunctionCallExprContext)) {
            at = at.getChildCount() == 1 ? at.getChild(0) : null;
        }
        return (FrostlakeParser.FunctionCallExprContext) at;
    }

    /** The rows a GENERATOR is asked for by a ROWCOUNT written as a number; null otherwise. */
    private static Long rowCountOf(final ParseTree node) {
        if (node instanceof FrostlakeParser.NamedArgumentContext) {
            final FrostlakeParser.NamedArgumentContext argument = (FrostlakeParser.NamedArgumentContext) node;
            if (argument.expression() != null && "ROWCOUNT".equalsIgnoreCase(argument.variableName().getText())) {
                return numberOfText(argument.expression().getText());
            }
            return null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Long rows = rowCountOf(node.getChild(i));
            if (rows != null) {
                return rows;
            }
        }
        return null;
    }

    /**
     * The rows a GENERATOR's ROWCOUNT stands for where it reads columns of the tables beside it that the statistics pin
     * to one value: {@code FROM g1, TABLE(GENERATOR(ROWCOUNT => g1.n + 1))}, g1 holding the one row 1, is a Generator
     * of 2 (live-verified); null where it reads anything else.
     */
    private Long pinnedRowCount(final FrostlakeParser.TableSourceContext source) {
        final FrostlakeParser.ExpressionContext bound = rowCountArgument(source);
        if (bound == null || bound.getStart() == null || bound.getStop() == null || holdsQuery(bound)
                || !GeneratorBounds.foldsItsCalls(bound)) {
            return null;
        }
        final List<FrostlakeParser.QualifiedNameExprContext> columns = new ArrayList<>();
        collectColumns(bound, columns);
        if (columns.isEmpty()) {
            return null;
        }
        final StoredColumnValues stored = new StoredColumnValues(executor);
        final StringBuilder text = new StringBuilder(ParseTreeText.getOriginalText(bound));
        final int base = bound.getStart().getStartIndex();
        for (int i = columns.size() - 1; i >= 0; i--) {
            final FrostlakeParser.QualifiedNameExprContext column = columns.get(i);
            final Object value = pinnedValue(stored, column);
            if (!(value instanceof Number)) {
                return null;
            }
            text.replace(column.getStart().getStartIndex() - base, column.getStop().getStopIndex() - base + 1,
                value.toString());
        }
        try {
            final Object rows = new ExpressionEvaluator(null, executor.getFunctionRegistry(), executor.getCatalog(),
                executor).evaluate(ExpressionEvaluator.parse(text.toString()), null);
            final BigDecimal count = rows instanceof Number ? new BigDecimal(rows.toString()) : null;
            return count == null || count.signum() < 0 || count.stripTrailingZeros().scale() > 0 ? null
                : Long.valueOf(count.longValueExact());
        } catch (final RuntimeException unfolded) {
            return null;
        }
    }

    /** The one value the statistics pin a catalog table's column to, read as {@code t.c}; null for any other. */
    private Object pinnedValue(final StoredColumnValues stored, final FrostlakeParser.QualifiedNameExprContext column) {
        final String[] parts = ParseTreeText.qualifiedNameParts(column.qualifiedName());
        final FrostlakeParser.TableReferenceContext item = parts.length == 2
            ? DerivedRelationPins.fromItem(column, parts[0].toUpperCase(Locale.ROOT)) : null;
        final FrostlakeParser.TableSourceContext source = item == null ? null : item.tableSource();
        if (source == null || source.tableQualifiedName() == null || source.timeTravelClause() != null
                || ParseTreeText.qualifiedNameParts(source.tableQualifiedName()).length == 1
                && DerivedRelationPins.cteNamed(item, ParseTreeText.qualifiedNameParts(
                    source.tableQualifiedName())[0]) != null) {
            return null;
        }
        try {
            final Table table = executor.getCatalog().resolveTable(
                ParseTreeText.getQualifiedName(source.tableQualifiedName()));
            if (table == null || !table.isCatalogResident() || !table.hasColumn(parts[1])) {
                return null;
            }
            final String declared = table.getColumn(parts[1]).getName();
            return stored.holdsOneValue(table, declared) ? stored.oneValueOf(table, declared) : null;
        } catch (final RuntimeException noTable) {
            return null;
        }
    }

    /** The ROWCOUNT a table function's call names, or null. */
    private static FrostlakeParser.ExpressionContext rowCountArgument(final ParseTree node) {
        if (node instanceof FrostlakeParser.NamedArgumentContext) {
            final FrostlakeParser.NamedArgumentContext argument = (FrostlakeParser.NamedArgumentContext) node;
            return "ROWCOUNT".equalsIgnoreCase(argument.variableName().getText()) ? argument.expression() : null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.ExpressionContext found = rowCountArgument(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static boolean holdsQuery(final ParseTree node) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (holdsQuery(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static void collectColumns(final ParseTree node,
                                       final List<FrostlakeParser.QualifiedNameExprContext> into) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            into.add((FrostlakeParser.QualifiedNameExprContext) node);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collectColumns(node.getChild(i), into);
        }
    }

    private static Long numberOfText(final String written) {
        try {
            return Long.valueOf(written);
        } catch (final NumberFormatException computed) {
            return null;
        }
    }

    /** The conditions a condition joins with AND, in the order written. */
    private static void conjuncts(final FrostlakeParser.BooleanExprContext condition,
                                  final List<FrostlakeParser.BooleanExprContext> into) {
        if (condition instanceof FrostlakeParser.AndExprContext) {
            for (final FrostlakeParser.BooleanExprContext side
                    : ((FrostlakeParser.AndExprContext) condition).booleanExpr()) {
                conjuncts(side, into);
            }
            return;
        }
        final FrostlakeParser.BooleanExprContext inner = bracketed(condition);
        if (inner instanceof FrostlakeParser.AndExprContext) {
            conjuncts(inner, into);
        } else {
            into.add(condition);
        }
    }

    /** The condition a bracketed condition holds; the condition itself when it is not bracketed. */
    private static FrostlakeParser.BooleanExprContext bracketed(final FrostlakeParser.BooleanExprContext condition) {
        if (condition instanceof FrostlakeParser.ValueExprContext) {
            final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) condition).expression();
            if (value instanceof FrostlakeParser.ParenExprContext) {
                return bracketed(((FrostlakeParser.ParenExprContext) value).booleanExpr());
            }
        }
        return condition;
    }

    private static String textOf(final List<FrostlakeParser.BooleanExprContext> conditions) {
        final StringBuilder text = new StringBuilder();
        for (final FrostlakeParser.BooleanExprContext condition : conditions) {
            text.append(text.length() == 0 ? "" : " AND ").append(ParseTreeText.getOriginalText(condition));
        }
        return text.toString();
    }

    /**
     * Takes from the conditions those that equate the two sides, in the order written. A condition equates the sides
     * when it is an equality with a column on each side of it, the columns of one side named by one side's names and
     * the other's by the other's — or not named by any, which is taken to be so.
     */
    private static List<FrostlakeParser.BooleanExprContext> takeKeys(
            final List<FrostlakeParser.BooleanExprContext> conditions, final Set<String> left,
            final Set<String> right) {
        final List<FrostlakeParser.BooleanExprContext> keys = new ArrayList<>();
        for (int i = conditions.size() - 1; i >= 0; i--) {
            if (equates(bracketed(conditions.get(i)), left, right)) {
                keys.add(0, conditions.remove(i));
            }
        }
        return keys;
    }

    private static boolean equates(final FrostlakeParser.BooleanExprContext condition, final Set<String> left,
                                   final Set<String> right) {
        if (!(condition instanceof FrostlakeParser.ValueExprContext)) {
            return false;
        }
        final FrostlakeParser.ExpressionContext value = ((FrostlakeParser.ValueExprContext) condition).expression();
        if (!(value instanceof FrostlakeParser.ComparisonExprContext)) {
            return false;
        }
        final FrostlakeParser.ComparisonExprContext comparison = (FrostlakeParser.ComparisonExprContext) value;
        if (comparison.op.getType() != FrostlakeParser.EQ) {
            return false;
        }
        final Set<String> one = new LinkedHashSet<>();
        final Set<String> other = new LinkedHashSet<>();
        if (!namesAColumn(comparison.expression(0), one) || !namesAColumn(comparison.expression(1), other)) {
            return false;
        }
        if (one.isEmpty() || other.isEmpty()) {
            return true;
        }
        return left.containsAll(one) && right.containsAll(other) || right.containsAll(one) && left.containsAll(other);
    }

    /** Whether an expression names a column outside any nested query, collecting the names its columns are under. */
    private static boolean namesAColumn(final ParseTree node, final Set<String> qualifiers) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            if (parts.length > 1) {
                qualifiers.add(parts[parts.length - 2].toUpperCase(Locale.ROOT));
            }
            return true;
        }
        boolean named = false;
        for (int i = 0; i < node.getChildCount(); i++) {
            named = namesAColumn(node.getChild(i), qualifiers) || named;
        }
        return named;
    }

    /** The aliases of the select items that aggregate, upper-cased. */
    private Set<String> aggregatedAliases(final FrostlakeParser.SelectClauseContext select) {
        final Set<String> aliases = new LinkedHashSet<>();
        for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
            if (!(item instanceof FrostlakeParser.ExprItemContext) || !aggregates(item)) {
                continue;
            }
            final FrostlakeParser.ExprItemContext computed = (FrostlakeParser.ExprItemContext) item;
            if (computed.aliasName() != null) {
                aliases.add(ParseTreeText.getIdentifier(computed.aliasName()).toUpperCase(Locale.ROOT));
            } else if (computed.identifier() != null) {
                aliases.add(ParseTreeText.getIdentifier(computed.identifier()).toUpperCase(Locale.ROOT));
            }
        }
        return aliases;
    }

    /** The columns an expression names with no qualifier, upper-cased, outside any nested query. */
    private static void columnsOf(final ParseTree node, final Set<String> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final String[] parts = ParseTreeText.qualifiedNameParts(
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName());
            if (parts.length == 1) {
                into.add(parts[0].toUpperCase(Locale.ROOT));
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            columnsOf(node.getChild(i), into);
        }
    }

    private boolean aggregates(final ParseTree node) {
        return node != null && UnreadColumnPruning.containsAggregate(node, executor.getFunctionRegistry());
    }

    /** Whether a GROUP BY groups by ROLLUP, CUBE or GROUPING SETS. */
    private static boolean groupsBySets(final FrostlakeParser.GroupByClauseContext groupBy) {
        if (groupBy == null) {
            return false;
        }
        for (final FrostlakeParser.GroupByElementContext element : groupBy.groupByElement()) {
            if (element.ROLLUP() != null || element.CUBE() != null || element.GROUPING() != null) {
                return true;
            }
        }
        return false;
    }

    /** Whether an OVER clause stands under a node, outside any nested query. */
    /**
     * A WindowFunction straight over a TableScan reads it twice, as a Sort with no row limit does (see
     * {@link PlanNode#readForSort}), when the first window the query writes is a ROW_NUMBER, a RANK or a DENSE_RANK, or
     * a LAG or a LEAD with no PARTITION BY: the scan lists its table twice, the columns that window partitions and
     * orders by first, and GlobalStats counts it twice. A first window of any other function reads the scan once, the
     * windows after it whatever they are, and so does a window over a Filter (all live-verified).
     */
    private static void readTwiceForWindows(final FrostlakeParser.SelectClauseContext select, final PlanNode scan) {
        FrostlakeParser.OverClauseContext first = firstWindow(select.selectList());
        if (first == null) {
            first = firstWindow(select.qualifyClause());
        }
        if (first == null || !readsItsScanTwice(first)) {
            return;
        }
        final Set<String> named = new LinkedHashSet<>();
        if (first.partitionByClause() != null) {
            collectColumns(first.partitionByClause(), named);
        }
        if (first.orderByClause() != null) {
            collectColumns(first.orderByClause(), named);
        }
        scan.readForSort(new ArrayList<>(named));
    }

    /** The first window written under the node, outside nested queries, or null. */
    private static FrostlakeParser.OverClauseContext firstWindow(final ParseTree node) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return null;
        }
        if (node instanceof FrostlakeParser.OverClauseContext) {
            return (FrostlakeParser.OverClauseContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.OverClauseContext found = firstWindow(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Whether a window's function reads its scan twice (see {@link #readTwiceForWindows}). */
    private static boolean readsItsScanTwice(final FrostlakeParser.OverClauseContext window) {
        if (!(window.getParent() instanceof FrostlakeParser.FunctionCallExprContext)) {
            return false;
        }
        final String name = SqlIdentifiers.canonicalText(
            ((FrostlakeParser.FunctionCallExprContext) window.getParent()).functionName().getText());
        return SCAN_TWICE_RANKINGS.contains(name)
            || SCAN_TWICE_OFFSETS.contains(name) && window.partitionByClause() == null;
    }

    private static boolean computesAWindow(final ParseTree node) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.OverClauseContext) {
            return true;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (computesAWindow(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    /**
     * What an UPDATE or a DELETE reads: the other tables it names joined with its target, as with a table written
     * after them, by the conditions of its WHERE, and filtered by the rest. A DELETE of every row reads no column of
     * its target.
     */
    private PlanNode read(final String target, final ParserRuleContext statement,
                          final FrostlakeParser.IdentifierContext alias,
                          final FrostlakeParser.WhereClauseContext where, final boolean deletes,
                          final Map<String, FrostlakeParser.SelectStatementContext> named) {
        final String[] parts = QualifiedName.parse(target).parts();
        final PlanNode scan = scan(target, null).named(Collections.singleton(alias != null
            ? ParseTreeText.getIdentifier(alias).toUpperCase(Locale.ROOT)
            : parts[parts.length - 1].toUpperCase(Locale.ROOT)));
        final List<FrostlakeParser.BooleanExprContext> conditions = new ArrayList<>();
        if (where != null) {
            conjuncts(where.booleanExpr(), conditions);
        }
        final int written = conditions.size();
        final PlanNode others = listPlan(null, statement, conditions, named, null);
        if (others == null && where == null && deletes) {
            return scan.unread();
        }
        PlanNode plan = others == null ? scan : unconditioned(others, scan, conditions, null);
        if (!conditions.isEmpty()) {
            plan = filtered(plan, Collections.singletonList(conditions.size() == written
                ? ParseTreeText.getOriginalText(where.booleanExpr()) : textOf(conditions)));
        }
        return plan;
    }

    /**
     * A multi-table INSERT: one Insert into its tables, in the order written and each once, over the query it reads;
     * the Result names a count for each table when there are several.
     */
    private PlanNode severalInserts(final FrostlakeParser.MultiTableInsertStatementContext insert,
                                    final Map<String, FrostlakeParser.SelectStatementContext> named) {
        final Set<String> targets = new LinkedHashSet<>();
        targetsOf(insert, targets);
        final List<String> counts = new ArrayList<>();
        for (final String target : targets) {
            final String[] parts = QualifiedName.parse(target).parts();
            counts.add(targets.size() == 1 ? "number of rows inserted"
                : "number of rows inserted into " + parts[parts.length - 1]);
        }
        return counted(counts).over(new PlanNode("Insert", new ArrayList<>(targets), null, NONE)
            .over(queryPlan(insert.selectStatement(), named)));
    }

    private void targetsOf(final ParseTree node, final Set<String> into) {
        if (node instanceof FrostlakeParser.SelectStatementContext) {
            return;
        }
        if (node instanceof FrostlakeParser.MultiInsertIntoContext) {
            into.add(qualified(ParseTreeText.getQualifiedName(
                ((FrostlakeParser.MultiInsertIntoContext) node).qualifiedName())));
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            targetsOf(node.getChild(i), into);
        }
    }

    /**
     * A MERGE: the counts its clauses answer, the write, and its source joined with its target as with a table
     * written after it — an InnerJoin when every clause is for a match, an AntiJoin of the source with the aggregated
     * target when none is, and otherwise a LeftOuterJoin that keeps the source.
     */
    private PlanNode merged(final FrostlakeParser.MergeStatementContext merge,
                            final Map<String, FrostlakeParser.SelectStatementContext> named) {
        boolean inserts = false;
        boolean updates = false;
        boolean deletes = false;
        for (final FrostlakeParser.MergeClauseContext clause : merge.mergeClause()) {
            inserts = inserts || clause.INSERT() != null;
            updates = updates || clause.UPDATE() != null;
            deletes = deletes || clause.DELETE() != null;
        }
        final List<String> counts = new ArrayList<>();
        if (inserts) {
            counts.add("number of rows inserted");
        }
        if (updates) {
            counts.add("number of rows updated");
        }
        if (deletes) {
            counts.add("number of rows deleted");
        }
        final String object = qualified(ParseTreeText.getQualifiedName(merge.qualifiedName()));
        final PlanNode target = scan(object, null);
        final FrostlakeParser.MergeSourceContext written = merge.mergeSource();
        final PlanNode source;
        if (written instanceof FrostlakeParser.MergeSourceTableContext) {
            final String name = ParseTreeText.getQualifiedName(
                ((FrostlakeParser.MergeSourceTableContext) written).qualifiedName());
            final PlanNode viewed = viewPlan(name);
            source = viewed != null ? viewed : scan(qualified(name), null);
        } else if (written instanceof FrostlakeParser.MergeSourceSubqueryContext) {
            source = queryPlan(((FrostlakeParser.MergeSourceSubqueryContext) written).selectStatement(), named);
        } else {
            source = values(((FrostlakeParser.MergeSourceValuesContext) written).valueTupleList());
        }
        final PlanNode join;
        if (!inserts) {
            join = inner(source, target, PlanJoinKeys.none());
        } else if (updates || deletes) {
            join = outer(source, target, true, PlanJoinKeys.none());
        } else if (holdsFewer(source, target)) {
            join = operator("AntiJoin").over(source).over(operator("Aggregate").over(joinFiltered(target, NONE)));
        } else {
            join = operator("AntiJoin").over(operator("Aggregate").over(target)).over(source);
        }
        return counted(counts).over(new PlanNode("Merge", Arrays.asList(object), null, NONE).over(join));
    }

    /**
     * A COPY: into a table, an Insert over the ExternalScan of the stage it loads; into a location, the Unload of the
     * table or the query it reads.
     */
    private PlanNode copied(final FrostlakeParser.CopyIntoStatementContext copy,
                            final Map<String, FrostlakeParser.SelectStatementContext> named) {
        if (copy.qualifiedName() != null) {
            FrostlakeParser.StageRefContext stage = null;
            for (final FrostlakeParser.CopyIntoTableClauseContext clause : copy.copyIntoTableClause()) {
                if (clause.copySource() != null) {
                    stage = clause.copySource().stageRef() != null ? clause.copySource().stageRef()
                        : clause.copySource().copyTransformation() == null ? null
                            : clause.copySource().copyTransformation().stageRef();
                }
            }
            final String kind = stage == null ? null : stage.TILDE() != null ? "USER STAGE"
                : stage.PERCENT() != null ? "TABLE STAGE" : "INTERNAL STAGE";
            return counted(Arrays.asList("number of rows inserted")).over(new PlanNode("Insert",
                Arrays.asList(qualified(ParseTreeText.getQualifiedName(copy.qualifiedName()))), null, NONE)
                .over(new PlanNode("ExternalScan", NONE, null, kind == null ? NONE : Arrays.asList(kind))));
        }
        PlanNode read = null;
        for (final FrostlakeParser.CopyIntoStageClauseContext clause : copy.copyIntoStageClause()) {
            if (clause.copyTableSource() != null && clause.copyTableSource().selectStatement() != null) {
                read = queryPlan(clause.copyTableSource().selectStatement(), named);
            } else if (clause.copyTableSource() != null) {
                final String name = ParseTreeText.getQualifiedName(clause.copyTableSource().qualifiedName());
                final PlanNode viewed = viewPlan(name);
                read = viewed != null ? viewed : scan(qualified(name), null);
            }
        }
        final FrostlakeParser.StageRefContext to = copy.stageRef();
        final List<String> location = to == null ? NONE
            : to.TILDE() != null || to.PERCENT() != null ? Arrays.asList(ParseTreeText.getOriginalText(to))
                : Arrays.asList(ParseTreeText.getIdentifier(to.identifier(to.identifier().size() - 1)));
        return counted(Arrays.asList("rows_unloaded", "input_bytes", "output_bytes"))
            .over(new PlanNode("Unload", NONE, null, location).over(read));
    }
}

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

import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.View;
import dev.frostlake.parser.FrostlakeParser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
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
 * </ul>
 *
 * <p>What the account's optimizer does beyond that is not reproduced: conditions stay above the join they are written
 * over, three and more tables join in the order written, a query the account answers from metadata still reads its
 * table, IN and EXISTS are no joins, and expressions are spelled as written.
 */
final class PlanBuilder {

    private static final List<String> NONE = Collections.<String>emptyList();

    /** How deep views may stand in views before a plan stops reading them. */
    private static final int DEEPEST_VIEW = 32;

    private final QueryExecutor executor;

    /** The database and schema the names of a view's body are read in, while that body is planned. */
    private String[] scope;

    private int viewDepth;

    PlanBuilder(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * A query's plan: a Result, computing the select list as written, over what the query reads.
     *
     * @param query the query
     * @return the Result
     */
    PlanNode query(final FrostlakeParser.SelectStatementContext query) {
        final FrostlakeParser.SelectClauseContext select = query.selectOperand(0).selectClause();
        final List<String> items = new ArrayList<>();
        if (select != null) {
            for (final FrostlakeParser.SelectItemContext item : select.selectList().selectItem()) {
                items.add(ParseTreeText.getOriginalText(item));
            }
        }
        return new PlanNode("Result", NONE, null, items)
            .over(queryPlan(query, new HashMap<String, FrostlakeParser.SelectStatementContext>()));
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
        final Long kept = limited ? keptRows(limitOf(query, only), plan) : null;
        if (query.orderByClause() != null) {
            final PlanNode sorted = "Sort".equals(plan.getOperation()) ? plan.getChildren().get(0) : plan;
            return new PlanNode(limited ? "SortWithLimit" : "Sort", NONE, null,
                Arrays.asList(ParseTreeText.getOriginalText(query.orderByClause()))).over(sorted)
                .named(plan.getNames()).holding(kept);
        }
        return limited ? operator("Limit").over(plan).named(plan.getNames()).holding(kept) : plan;
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

    /** A run of operands under one union: as they are for UNION ALL, under an Aggregate for UNION. */
    private static PlanNode united(final List<PlanNode> run, final boolean all, final boolean distinct) {
        if (run.size() == 1 || !all && !distinct) {
            return run.get(0);
        }
        final PlanNode union = operator("UnionAll");
        for (final PlanNode operand : run) {
            final boolean aggregated = distinct && run.size() > 2 && !"Aggregate".equals(operand.getOperation());
            union.over(aggregated ? operator("Aggregate").over(operand) : operand);
        }
        return distinct ? operator("Aggregate").over(union) : union;
    }

    /** INTERSECT: a SemiJoin of the two operands, each aggregated, the one that holds fewer rows first. */
    private static PlanNode intersected(final PlanNode left, final PlanNode right) {
        final boolean leftFirst = holdsFewer(left, right);
        final PlanNode first = leftFirst ? left : right;
        final PlanNode second = leftFirst ? right : left;
        return operator("Aggregate").over(operator("Aggregate").over(operator("SemiJoin")
            .over(operator("Aggregate").over(first)).over(operator("Aggregate").over(joinFiltered(second)))));
    }

    /** EXCEPT: an AntiJoin of the two operands, each aggregated, the one that holds fewer rows first. */
    private static PlanNode excepted(final PlanNode kept, final PlanNode taken) {
        final PlanNode join = operator("AntiJoin");
        if (holdsFewer(kept, taken)) {
            join.over(operator("Aggregate").over(kept)).over(operator("Aggregate").over(joinFiltered(taken)));
        } else {
            join.over(operator("Aggregate").over(taken)).over(operator("Aggregate").over(kept));
        }
        return operator("Aggregate").over(operator("Aggregate").over(join));
    }

    /**
     * What one SELECT reads: its FROM list, a Filter for what its WHERE holds beyond the conditions that join the
     * list, an Aggregate, a Filter for a HAVING, a WindowFunction and a Filter for a QUALIFY. A SELECT with no FROM
     * reads a Generator of one row.
     */
    private PlanNode operandPlan(final FrostlakeParser.SelectOperandContext operand,
                                 final Map<String, FrostlakeParser.SelectStatementContext> named,
                                 final boolean several) {
        if (operand.selectStatement() != null) {
            return queryPlan(operand.selectStatement(), named);
        }
        final FrostlakeParser.SelectClauseContext select = operand.selectClause();
        if (select.FROM() == null) {
            return new PlanNode("Generator", NONE, null, Arrays.asList("1")).holding(Long.valueOf(1));
        }
        final List<FrostlakeParser.BooleanExprContext> conditions = new ArrayList<>();
        if (select.whereClause() != null) {
            conjuncts(select.whereClause().booleanExpr(), conditions);
        }
        final int written = conditions.size();
        PlanNode plan = listPlan(null, select.tableExpression(), conditions, named);
        if (!conditions.isEmpty()) {
            plan = filtered(plan, conditions.size() == written
                ? ParseTreeText.getOriginalText(select.whereClause().booleanExpr()) : textOf(conditions));
        }
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
            plan = filtered(plan, textOf(plain));
        }
        if (select.groupByClause() != null || select.DISTINCT() != null || !grouped.isEmpty()
                || aggregates(select.selectList()) || aggregates(select.qualifyClause())) {
            plan = new PlanNode(groupsBySets(select.groupByClause()) ? "GroupingSets" : "Aggregate", NONE, null,
                select.groupByClause() == null ? NONE
                    : Arrays.asList(ParseTreeText.getOriginalText(select.groupByClause()))).over(plan);
        }
        if (!grouped.isEmpty()) {
            plan = new PlanNode("Filter", NONE, null, Arrays.asList(textOf(grouped))).over(plan);
        }
        if (computesAWindow(select.selectList()) || computesAWindow(select.qualifyClause())) {
            plan = operator("WindowFunction").over(plan);
        }
        if (select.qualifyClause() != null) {
            plan = new PlanNode("Filter", NONE, null,
                Arrays.asList(ParseTreeText.getOriginalText(select.qualifyClause().booleanExpr()))).over(plan);
        }
        return several && select.topClause() != null ? operator("Limit").over(plan)
            .holding(keptRows(numberOf(select.topClause().INTEGER_LITERAL()), plan)) : plan;
    }

    /**
     * The plan of a list of table references and joins, read from the left over what stands before it. A reference
     * after a comma joins by the conditions of the WHERE that equate it with what stands before it, which are taken
     * from the conditions then, and is a CartesianJoin without one.
     */
    private PlanNode listPlan(final PlanNode start, final ParserRuleContext list,
                              final List<FrostlakeParser.BooleanExprContext> conditions,
                              final Map<String, FrostlakeParser.SelectStatementContext> named) {
        PlanNode plan = start;
        for (int i = 0; i < list.getChildCount(); i++) {
            final ParseTree child = list.getChild(i);
            if (child instanceof FrostlakeParser.TableReferenceContext) {
                final FrostlakeParser.TableReferenceContext reference = (FrostlakeParser.TableReferenceContext) child;
                if (flattens(reference.tableSource())) {
                    plan = flattened(plan);
                    continue;
                }
                final PlanNode read = referencePlan(reference, named);
                if (plan == null || read == null) {
                    plan = plan == null ? read : plan;
                } else {
                    plan = unconditioned(plan, read, conditions);
                }
            } else if (child instanceof FrostlakeParser.JoinClauseContext) {
                plan = joined(plan, (FrostlakeParser.JoinClauseContext) child, conditions, named);
            }
        }
        return plan;
    }

    /** What a JOIN makes of what stands before it and the reference it reads. */
    private PlanNode joined(final PlanNode plan, final FrostlakeParser.JoinClauseContext join,
                            final List<FrostlakeParser.BooleanExprContext> conditions,
                            final Map<String, FrostlakeParser.SelectStatementContext> named) {
        if (flattens(join.tableReference().tableSource())) {
            return flattened(plan);
        }
        final PlanNode read = referencePlan(join.tableReference(), named);
        if (plan == null || read == null) {
            return plan == null ? read : plan;
        }
        final FrostlakeParser.JoinTypeContext type = join.joinType();
        if (join.CROSS() != null || join.ON() == null && join.USING() == null && join.NATURAL() == null) {
            return unconditioned(plan, read, conditions);
        }
        boolean keyed = join.ON() == null;
        if (!keyed) {
            final List<FrostlakeParser.BooleanExprContext> on = new ArrayList<>();
            conjuncts(join.booleanExpr(), on);
            keyed = takesKeys(on, plan.getNames(), read.getNames());
        }
        if (type != null && type.LEFT() != null) {
            return outer(plan, read, keyed);
        }
        if (type != null && type.RIGHT() != null) {
            return outer(read, plan, keyed);
        }
        if (type != null && type.FULL() != null) {
            final boolean leftFirst = !holdsFewer(read, plan);
            return operator("FullOuterJoin").over(leftFirst ? plan : read).over(leftFirst ? read : plan)
                .named(both(plan, read));
        }
        return keyed ? inner(plan, read) : cartesian(plan, read);
    }

    /**
     * Two sides joined with no condition of their own: an InnerJoin when a condition of the WHERE equates them, a
     * CartesianJoin otherwise. Either join takes the conditions that name columns of both sides for its own.
     */
    private static PlanNode unconditioned(final PlanNode left, final PlanNode right,
                                          final List<FrostlakeParser.BooleanExprContext> conditions) {
        final boolean keyed = takesKeys(conditions, left.getNames(), right.getNames());
        for (int i = conditions.size() - 1; i >= 0; i--) {
            final Set<String> named = new LinkedHashSet<>();
            namesAColumn(conditions.get(i), named);
            if (meets(named, left.getNames()) && meets(named, right.getNames())) {
                conditions.remove(i);
            }
        }
        return keyed ? inner(left, right) : cartesian(left, right);
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
    private static PlanNode inner(final PlanNode left, final PlanNode right) {
        final boolean leftFirst = holdsFewer(left, right);
        return operator("InnerJoin").over(leftFirst ? left : right).over(joinFiltered(leftFirst ? right : left))
            .named(both(left, right));
    }

    private static PlanNode cartesian(final PlanNode left, final PlanNode right) {
        final boolean leftFirst = holdsFewer(left, right);
        return operator("CartesianJoin").over(leftFirst ? left : right).over(leftFirst ? right : left)
            .named(both(left, right));
    }

    /**
     * A LeftOuterJoin, which a RIGHT JOIN is with its sides turned: the side that holds fewer rows first, and the
     * side whose rows are not all kept when both hold as many. Joined on an equality, that side is filtered when it
     * stands first, and filtered above a JoinFilter when it stands second.
     *
     * @param kept  the side whose rows are all kept
     * @param other the side whose rows are kept where they match
     * @param keyed whether the join's condition equates the two sides
     */
    private static PlanNode outer(final PlanNode kept, final PlanNode other, final boolean keyed) {
        final boolean otherFirst = !holdsFewer(kept, other);
        final PlanNode join = operator("LeftOuterJoin");
        if (!keyed) {
            join.over(otherFirst ? other : kept).over(otherFirst ? kept : other);
        } else if (otherFirst) {
            join.over(underFilter(other)).over(kept);
        } else {
            join.over(kept).over(underFilter(joinFiltered(other)));
        }
        final Set<String> names = new LinkedHashSet<>(kept.getNames());
        names.addAll(other.getNames());
        return join.named(names);
    }

    /** A plan under a Filter of its own; a plan that is a Filter already stays as it is. */
    private static PlanNode underFilter(final PlanNode plan) {
        return "Filter".equals(plan.getOperation()) ? plan : operator("Filter").over(plan).named(plan.getNames());
    }

    /**
     * A plan under a JoinFilter, which stands under the plan's own Filters and Aggregates and over each operand of a
     * UnionAll.
     */
    private static PlanNode joinFiltered(final PlanNode plan) {
        final String operation = plan.getOperation();
        if ("UnionAll".equals(operation)
                || ("Filter".equals(operation) || "Aggregate".equals(operation)) && plan.getChildren().size() == 1) {
            final PlanNode above = new PlanNode(operation, NONE, null, plan.getExpressions());
            for (final PlanNode child : plan.getChildren()) {
                above.over(joinFiltered(child));
            }
            return above.named(plan.getNames());
        }
        return operator("JoinFilter").over(plan).named(plan.getNames());
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
                                   final Map<String, FrostlakeParser.SelectStatementContext> named) {
        PlanNode plan = sourcePlan(reference, named);
        if (plan == null) {
            return null;
        }
        final Set<String> names = plan.getNames();
        if (reference.pivotClause() != null) {
            final boolean listed = reference.pivotClause().pivotInList().pivotValueList() != null;
            plan = operator("Aggregate").over(listed ? plan : operator("Pivot").over(plan));
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
                                final Map<String, FrostlakeParser.SelectStatementContext> named) {
        final FrostlakeParser.TableSourceContext source = reference.tableSource();
        if (source.tableQualifiedName() != null) {
            final String written = ParseTreeText.getQualifiedName(source.tableQualifiedName());
            final String[] parts = QualifiedName.parse(written).parts();
            final Set<String> name = Collections.singleton(parts[parts.length - 1].toUpperCase(Locale.ROOT));
            final FrostlakeParser.SelectStatementContext expression = named.get(written.toUpperCase(Locale.ROOT));
            if (expression != null) {
                final Map<String, FrostlakeParser.SelectStatementContext> others = new HashMap<>(named);
                others.remove(written.toUpperCase(Locale.ROOT));
                return queryPlan(expression, others).named(name);
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
            return listPlan(null, source, new ArrayList<FrostlakeParser.BooleanExprContext>(), named);
        }
        if ("GENERATOR".equals(calledName(source))) {
            final Long rows = rowCountOf(source);
            return new PlanNode("Generator", NONE, null,
                rows == null ? NONE : Arrays.asList(rows.toString())).holding(rows);
        }
        return null;
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

    /** A Filter over a plan; a plan that is a Filter already takes the condition as one more of its own. */
    private static PlanNode filtered(final PlanNode plan, final String condition) {
        if (plan != null && "Filter".equals(plan.getOperation())) {
            return plan.with(condition);
        }
        final PlanNode filter = new PlanNode("Filter", NONE, null, Arrays.asList(condition)).over(plan);
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

    /** The rows a GENERATOR is asked for by a ROWCOUNT written as a number; null otherwise. */
    private static Long rowCountOf(final ParseTree node) {
        if (node instanceof FrostlakeParser.NamedArgumentContext) {
            final FrostlakeParser.NamedArgumentContext argument = (FrostlakeParser.NamedArgumentContext) node;
            if (argument.expression() != null && "ROWCOUNT".equalsIgnoreCase(argument.identifier().getText())) {
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
     * Takes from the conditions those that equate the two sides, and says whether there was one. A condition equates
     * the sides when it is an equality with a column on each side of it, the columns of one side named by one side's
     * names and the other's by the other's — or not named by any, which is taken to be so.
     */
    private static boolean takesKeys(final List<FrostlakeParser.BooleanExprContext> conditions,
                                     final Set<String> left, final Set<String> right) {
        boolean keyed = false;
        for (int i = conditions.size() - 1; i >= 0; i--) {
            if (equates(bracketed(conditions.get(i)), left, right)) {
                conditions.remove(i);
                keyed = true;
            }
        }
        return keyed;
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
        final PlanNode others = listPlan(null, statement, conditions, named);
        if (others == null && where == null && deletes) {
            return scan.unread();
        }
        PlanNode plan = others == null ? scan : unconditioned(others, scan, conditions);
        if (!conditions.isEmpty()) {
            plan = filtered(plan, conditions.size() == written
                ? ParseTreeText.getOriginalText(where.booleanExpr()) : textOf(conditions));
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
            join = inner(source, target);
        } else if (updates || deletes) {
            join = outer(source, target, true);
        } else if (holdsFewer(source, target)) {
            join = operator("AntiJoin").over(source).over(operator("Aggregate").over(joinFiltered(target)));
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

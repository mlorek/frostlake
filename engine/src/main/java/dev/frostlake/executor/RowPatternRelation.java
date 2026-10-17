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

import dev.frostlake.executor.operators.RowsProvider;
import dev.frostlake.executor.rowpattern.AfterMatchSkip;
import dev.frostlake.executor.rowpattern.RowPatternMatch;
import dev.frostlake.executor.rowpattern.RowPatternMatcher;
import dev.frostlake.executor.rowpattern.RowPatternNode;
import dev.frostlake.executor.rowpattern.RowPatternReader;
import dev.frostlake.executor.rowpattern.RowPatternTest;
import dev.frostlake.executor.rowpattern.RowsPerMatch;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.KeyContexts;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.BooleanType;
import dev.frostlake.types.NumericType;
import dev.frostlake.types.StringType;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A source read through MATCH_RECOGNIZE, planned as a relation of its own. The clause is answered in three steps:
 *
 * <ol>
 *   <li>a query over the source prepares the rows — the source's columns, the PARTITION BY values, each row's
 *       partition and place in it, and every symbol's definition judged for the row. A definition reads the whole
 *       partition (see {@link RowPatternExpressions}), so it is settled before any match is looked for;</li>
 *   <li>each partition's rows are matched against the pattern (see {@link RowPatternMatcher}), from the row after
 *       the last match on, or from where AFTER MATCH SKIP says;</li>
 *   <li>a query over the matched rows answers the measures, each match a window of its own.</li>
 * </ol>
 *
 * <p>ONE ROW PER MATCH answers the PARTITION BY values and the measures as the match's last row reads them; ALL
 * ROWS PER MATCH answers the source's columns and the measures for every row outside an exclusion. A match that
 * spans no row is an empty match: it answers one row, whose measures count nothing and read NULL; a row no match
 * holds is answered, when asked for, with every measure NULL (all live-verified).
 */
final class RowPatternRelation {

    private static final String MATCHED = "MATCH$ROWS";

    /** How a name none of the clause's rows answers to is refused. */
    private static final Pattern UNRESOLVED = Pattern.compile("invalid identifier '([^']+)'");
    /** A refusal that carries a place, and the function such a refusal names. */
    private static final Pattern PLACED = Pattern.compile("error line \\d+ at position -?\\d+\\n(.*)", Pattern.DOTALL);
    private static final Pattern FUNCTION = Pattern.compile("function \\[?([A-Za-z_][A-Za-z0-9_$]*)");
    /** The operator a refusal of its operands' types names. */
    private static final Pattern OPERATOR = Pattern.compile("^Invalid argument types for function '([^A-Za-z']+)'");

    private final QueryExecutor executor;

    RowPatternRelation(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Plans the relation a reference's MATCH_RECOGNIZE clause answers.
     *
     * @param reference      the reference: a source, the alias it may take, the clause and the alias after it
     * @param lateralContext the outer row's names, or null
     * @param cteResults     the common table expressions in scope, or null
     * @return the relation, its rows produced when the source stage runs
     */
    TableData plan(final FrostlakeParser.TableReferenceContext reference, final Map<String, Object> lateralContext,
                   final Map<String, ResultSet> cteResults) {
        final FrostlakeParser.MatchRecognizeClauseContext clause = reference.matchRecognizeClause();
        final RowPatternNode pattern = RowPatternReader.read(clause.rowPattern());
        final Set<String> symbols = new LinkedHashSet<>();
        pattern.collectSymbols(symbols);
        final RowsPerMatch rows = rowsPerMatch(clause.rowsPerMatch());
        if (rows == RowsPerMatch.ALL_ROWS_WITH_UNMATCHED && pattern.holdsExclusion()) {
            throw new RuntimeException("SQL compilation error: \nExclusion syntax ({-...-}) not available with"
                + " activated WITH UNMATCHED ROWS option.");
        }
        final AfterMatchSkip skip = skipOf(clause.afterMatchSkip());
        final String skipSymbol = skip == AfterMatchSkip.TO_FIRST || skip == AfterMatchSkip.TO_LAST
            ? RowPatternReader.symbolName(clause.afterMatchSkip().rowPatternSkipTarget().identifier()) : null;
        if (skipSymbol != null && !pattern.alwaysMatches(skipSymbol)) {
            throw new RuntimeException(SqlCompilationError.of("Not all paths of the pattern generate at least one"
                + " match of the AFTER MATCH SKIP pattern variable '" + skipSymbol + "'."));
        }

        // The definitions, by symbol, in the order written.
        final Map<String, FrostlakeParser.BooleanExprContext> definitions = new LinkedHashMap<>();
        for (final FrostlakeParser.RowPatternDefinitionContext definition : clause.rowPatternDefinition()) {
            final String symbol = RowPatternReader.symbolName(definition.identifier());
            if (definitions.containsKey(symbol)) {
                throw new RuntimeException("SQL execution error:\nDuplicate parameter specification - " + symbol + ".");
            }
            definitions.put(symbol, definition.booleanExpr());
        }
        final List<String> defined = new ArrayList<>(definitions.keySet());

        // The window a definition reads: the clause's partition, in its order.
        final List<String> partitionTexts = new ArrayList<>();
        for (final ParserRuleContext key : KeyContexts.keys(clause.booleanExpr())) {
            partitionTexts.add(ParseTreeText.getOriginalText(key));
        }
        final List<String> orderTexts = new ArrayList<>();
        for (final FrostlakeParser.RowPatternOrderItemContext item : clause.rowPatternOrderItem()) {
            orderTexts.add(ParseTreeText.getOriginalText(item));
        }
        final String partitionBy = partitionTexts.isEmpty() ? "" : "PARTITION BY " + String.join(", ", partitionTexts) + " ";
        final String window = partitionBy + "ORDER BY " + (orderTexts.isEmpty() ? "NULL" : String.join(", ", orderTexts));
        final RowPatternExpressions expressions =
            new RowPatternExpressions(executor.getFunctionRegistry(), symbols, window, rows.isOneRow());

        // Step 1, planned: the source's rows with their partition, their place and their definitions.
        final StringBuilder prepare = new StringBuilder("SELECT *");
        for (final String key : partitionTexts) {
            prepare.append(", ").append(key);
        }
        prepare.append(", ").append(partitionTexts.isEmpty() ? "1"
            : "DENSE_RANK() OVER (ORDER BY " + String.join(", ", partitionTexts) + ")").append(" AS MATCH$PART");
        prepare.append(", ROW_NUMBER() OVER (").append(window).append(") AS MATCH$PLACE");
        for (int i = 0; i < defined.size(); i++) {
            prepare.append(", (").append(expressions.definition(definitions.get(defined.get(i)))).append(") AS MATCH$D")
                .append(i);
        }
        prepare.append(" FROM ").append(ParseTreeText.getOriginalText(reference.tableSource()));
        if (reference.aliasName() != null) {
            prepare.append(" AS ").append(ParseTreeText.getOriginalText(reference.aliasName()));
        } else if (reference.nonJoinKeywordIdentifier() != null) {
            prepare.append(' ').append(ParseTreeText.getOriginalText(reference.nonJoinKeywordIdentifier()));
        }
        final PlannedQuery prepared = planned(prepare.toString(), lateralContext, cteResults, clause);
        final List<ResultSetColumn> preparedColumns = prepared.shape.getColumns();
        final int keys = partitionTexts.size();
        final int width = preparedColumns.size() - keys - 2 - defined.size();
        final Set<String> read = new LinkedHashSet<>();
        for (int i = 0; i < width; i++) {
            read.add(preparedColumns.get(i).getName());
        }
        expressions.readColumns(read);

        // Step 3, planned over no row, for the measures' types.
        final List<FrostlakeParser.RowPatternMeasureContext> measures = clause.rowPatternMeasure();
        final List<ResultSetColumn> matchedColumns = new ArrayList<>(preparedColumns.subList(0, width));
        matchedColumns.add(new ResultSetColumn(RowPatternExpressions.ROW, new NumericType("NUMBER", 18, 0)));
        matchedColumns.add(new ResultSetColumn(RowPatternExpressions.MATCH, new NumericType("NUMBER", 18, 0)));
        matchedColumns.add(new ResultSetColumn(RowPatternExpressions.PLACE, new NumericType("NUMBER", 18, 0)));
        matchedColumns.add(new ResultSetColumn(RowPatternExpressions.NUMBER, new NumericType("NUMBER", 18, 0)));
        matchedColumns.add(new ResultSetColumn(RowPatternExpressions.SEQUENCE, new NumericType("NUMBER", 18, 0)));
        matchedColumns.add(new ResultSetColumn(RowPatternExpressions.CLASSIFIER, new StringType("VARCHAR", 134217728)));
        matchedColumns.add(new ResultSetColumn(RowPatternExpressions.EMPTY, new BooleanType()));
        final String measured;
        final List<ResultSetColumn> measureColumns = new ArrayList<>();
        if (measures.isEmpty()) {
            measured = null;
        } else {
            final StringBuilder query = new StringBuilder("SELECT ").append(RowPatternExpressions.ROW);
            for (int i = 0; i < measures.size(); i++) {
                query.append(", (").append(expressions.measure(measures.get(i).booleanExpr())).append(") AS MATCH$M")
                    .append(i);
            }
            measured = query.append(" FROM ").append(MATCHED).toString();
            final List<ResultSetColumn> typed = planned(measured, lateralContext,
                withMatched(cteResults, new ResultSet(matchedColumns, new ArrayList<Row>())), clause).shape.getColumns();
            for (int i = 0; i < measures.size(); i++) {
                final ResultSetColumn column = typed.get(i + 1);
                measureColumns.add(new ResultSetColumn(ParseTreeText.getIdentifier(measures.get(i).aliasName()),
                    column.getDataType(), null, column.getStaticType(), true));
            }
        }

        // The relation's own columns.
        final List<ResultSetColumn> columns = new ArrayList<>();
        if (rows.isOneRow()) {
            columns.addAll(preparedColumns.subList(width, width + keys));
        } else {
            columns.addAll(preparedColumns.subList(0, width));
        }
        columns.addAll(measureColumns);
        if (columns.isEmpty()) {
            final Token opens = clause.MATCH_RECOGNIZE_OPEN().getSymbol();
            throw new RuntimeException(SqlCompilationError.at(opens.getLine(), opens.getCharPositionInLine(),
                "SELECT with no columns"));
        }
        if (rows.isOneRow()) {
            final Set<String> named = new LinkedHashSet<>();
            for (final ResultSetColumn column : columns) {
                if (!named.add(column.getName().toUpperCase(Locale.ROOT))) {
                    throw new RuntimeException(SqlCompilationError.of("ambiguous column name '" + column.getName() + "'"));
                }
            }
        }
        final String alias = reference.pivotAlias() == null ? null
            : ParseTreeText.getIdentifier(reference.pivotAlias().identifier());
        // The alias may rename the relation's columns, first to last: … ) m (k).
        if (reference.pivotAlias() != null && reference.pivotAlias().identifierList() != null) {
            final List<FrostlakeParser.IdentifierContext> renamed = reference.pivotAlias().identifierList().identifier();
            for (int i = 0; i < renamed.size() && i < columns.size(); i++) {
                final ResultSetColumn column = columns.get(i);
                columns.set(i, new ResultSetColumn(ParseTreeText.getIdentifier(renamed.get(i)), column.getDataType(),
                    null, column.getStaticType(), true));
            }
        }
        final Table relation = executor.resultSetToTable(new ResultSet(columns, new ArrayList<Row>()),
            alias != null ? alias : "values");
        relation.setRelationStatistics(null);

        final RowsProvider answered = new RowsProvider() {
            @Override
            public List<Row> rows() {
                return answer(prepared, width, keys, defined, pattern, rows, skip, skipSymbol, matchedColumns,
                    measured, measures.size(), lateralContext, cteResults, clause);
            }

            @Override
            public String describe() {
                return "MATCH_RECOGNIZE{" + prepared.description + "}";
            }
        };
        return new TableData(relation, answered, alias, "MATCH_RECOGNIZE[" + (alias != null ? alias : "values") + "]");
    }

    // ── the clause's options ────────────────────────────────────────────────────────

    private static RowsPerMatch rowsPerMatch(final FrostlakeParser.RowsPerMatchContext written) {
        if (written == null || written.ALL() == null) {
            return RowsPerMatch.ONE_ROW;
        }
        final FrostlakeParser.RowPatternEmptyMatchesContext empty = written.rowPatternEmptyMatches();
        if (empty == null || empty.SHOW() != null) {
            return RowsPerMatch.ALL_ROWS;
        }
        return empty.WITH() != null ? RowsPerMatch.ALL_ROWS_WITH_UNMATCHED : RowsPerMatch.ALL_ROWS_OMIT_EMPTY;
    }

    private static AfterMatchSkip skipOf(final FrostlakeParser.AfterMatchSkipContext written) {
        if (written == null || written.rowPatternSkipTarget().TO() == null) {
            return AfterMatchSkip.PAST_LAST_ROW;
        }
        final FrostlakeParser.RowPatternSkipTargetContext target = written.rowPatternSkipTarget();
        if (target.identifier() == null) {
            return AfterMatchSkip.TO_NEXT_ROW;
        }
        return target.FIRST() != null ? AfterMatchSkip.TO_FIRST : AfterMatchSkip.TO_LAST;
    }

    // ── the queries the clause runs ─────────────────────────────────────────────────

    private PlannedQuery planned(final String query, final Map<String, Object> lateralContext,
                                 final Map<String, ResultSet> cteResults,
                                 final FrostlakeParser.MatchRecognizeClauseContext clause) {
        final boolean outerBody = RelationBody.begin();
        try {
            return executor.planSelectFromContext(executor.parseSelectStatement(query), lateralContext, cteResults);
        } catch (final RuntimeException refused) {
            throw placedInClause(refused, clause);
        } finally {
            RelationBody.end(outerBody);
        }
    }

    /**
     * A name one of the clause's queries could not resolve, a call it refused and an operator refused for its
     * operands, placed where the clause itself writes it: the queries are the clause's own, and a place in them
     * is none the statement has.
     */
    private static RuntimeException placedInClause(final RuntimeException refused,
                                                   final FrostlakeParser.MatchRecognizeClauseContext clause) {
        final String message = String.valueOf(refused.getMessage());
        final Matcher unresolved = UNRESOLVED.matcher(message);
        if (unresolved.find()) {
            final Token written = firstReference(clause, unresolved.group(1));
            return written == null ? refused : new RuntimeException(SqlCompilationError.at(written.getLine(),
                written.getCharPositionInLine(), "invalid identifier '" + unresolved.group(1) + "'"));
        }
        final Matcher placed = PLACED.matcher(message);
        if (placed.find()) {
            final Matcher function = FUNCTION.matcher(placed.group(1));
            Token called = function.find() ? firstCall(clause, function.group(1)) : null;
            if (called == null) {
                // An operator refused for its operands is placed at the operator a measure or a definition writes.
                final Matcher operator = OPERATOR.matcher(placed.group(1));
                called = operator.find() ? firstOperator(clause, operator.group(1)) : null;
            }
            return called == null ? refused : new RuntimeException(SqlCompilationError.at(called.getLine(),
                called.getCharPositionInLine(), placed.group(1)));
        }
        return refused;
    }

    /** The first operator of that spelling standing between two operands of the clause, or null. */
    private static Token firstOperator(final ParseTree node, final String symbol) {
        if (node.getChildCount() == 3 && node.getChild(1) instanceof TerminalNode
                && symbol.equals(node.getChild(1).getText())
                && node.getChild(0) instanceof ParserRuleContext && node.getChild(2) instanceof ParserRuleContext) {
            return ((TerminalNode) node.getChild(1)).getSymbol();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = firstOperator(node.getChild(i), symbol);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Token firstCall(final ParseTree node, final String name) {
        if (node instanceof FrostlakeParser.FunctionCallExprContext && name.equalsIgnoreCase(
                ((FrostlakeParser.FunctionCallExprContext) node).functionName().getText())) {
            return ((FrostlakeParser.FunctionCallExprContext) node).getStart();
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = firstCall(node.getChild(i), name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Token firstReference(final ParseTree node, final String name) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            return name.equalsIgnoreCase(ParseTreeText.getOriginalText((FrostlakeParser.QualifiedNameExprContext) node))
                ? ((FrostlakeParser.QualifiedNameExprContext) node).getStart() : null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final Token found = firstReference(node.getChild(i), name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<Row> run(final PlannedQuery query) {
        final boolean outerBody = RelationBody.begin();
        try {
            return query.run().getRows();
        } finally {
            RelationBody.end(outerBody);
        }
    }

    private static Map<String, ResultSet> withMatched(final Map<String, ResultSet> cteResults, final ResultSet matched) {
        final Map<String, ResultSet> scope = cteResults == null ? new HashMap<String, ResultSet>()
            : new HashMap<>(cteResults);
        scope.put(MATCHED, matched);
        return scope;
    }

    // ── answering ───────────────────────────────────────────────────────────────────

    private List<Row> answer(final PlannedQuery prepared, final int width, final int keys, final List<String> defined,
                             final RowPatternNode pattern, final RowsPerMatch rows, final AfterMatchSkip skip,
                             final String skipSymbol, final List<ResultSetColumn> matchedColumns, final String measured,
                             final int measures, final Map<String, Object> lateralContext,
                             final Map<String, ResultSet> cteResults,
                             final FrostlakeParser.MatchRecognizeClauseContext clause) {
        // Step 1: the prepared rows, by partition, in the partition's order.
        final Map<Long, List<Row>> partitions = new LinkedHashMap<>();
        for (final Row row : run(prepared)) {
            final Long partition = Long.valueOf(((Number) row.getValue(width + keys)).longValue());
            List<Row> members = partitions.get(partition);
            if (members == null) {
                members = new ArrayList<>();
                partitions.put(partition, members);
            }
            members.add(row);
        }
        final Comparator<Row> byPlace = new Comparator<Row>() {
            @Override
            public int compare(final Row left, final Row right) {
                return Long.compare(((Number) left.getValue(width + keys + 1)).longValue(),
                    ((Number) right.getValue(width + keys + 1)).longValue());
            }
        };

        // Step 2: the matches. Each matched row is kept with the prepared row it came from.
        final List<Row> matched = new ArrayList<>();
        final List<Row> sources = new ArrayList<>();
        final List<Boolean> shown = new ArrayList<>();
        final List<Row> unmatched = new ArrayList<>();
        long matchId = 0;
        for (final List<Row> members : partitions.values()) {
            Collections.sort(members, byPlace);
            final RowPatternTest test = new RowPatternTest() {
                @Override
                public boolean holds(final String symbol, final int row) {
                    final int definition = defined.indexOf(symbol);
                    // A symbol with no definition is read for every row.
                    return definition < 0 || holdsTrue(members.get(row).getValue(width + keys + 2 + definition));
                }
            };
            final RowPatternMatcher matcher = new RowPatternMatcher(pattern, members.size(), test);
            final boolean[] held = new boolean[members.size()];
            long number = 0;
            int at = 0;
            while (at < members.size()) {
                final RowPatternMatch match = matcher.matchAt(at);
                if (match == null) {
                    at++;
                    continue;
                }
                number++;
                matchId++;
                if (match.size() == 0) {
                    held[at] = true;
                    if (rows != RowsPerMatch.ALL_ROWS_OMIT_EMPTY) {
                        final Row source = members.get(at);
                        matched.add(matchedRow(source, width, rows.isOneRow(), matched.size(), matchId, 1,
                            rows.isOneRow() ? null : Long.valueOf(number), rows.isOneRow() ? null : Long.valueOf(1),
                            null, true));
                        sources.add(source);
                        shown.add(Boolean.TRUE);
                    }
                    at++;
                    continue;
                }
                long sequence = 0;
                for (int i = 0; i < match.size(); i++) {
                    held[match.getStart() + i] = true;
                    final boolean excluded = match.isExcluded(i);
                    if (!excluded) {
                        sequence++;
                    }
                    final Row source = members.get(match.getStart() + i);
                    matched.add(matchedRow(source, width, false, matched.size(), matchId, i + 1, Long.valueOf(number),
                        excluded ? null : Long.valueOf(sequence), match.symbolAt(i), false));
                    sources.add(source);
                    // ONE ROW PER MATCH answers the match as its last row reads it, excluded or not.
                    shown.add(Boolean.valueOf(rows.isOneRow() ? i == match.size() - 1 : !excluded));
                }
                at = next(match, skip, skipSymbol);
            }
            if (rows == RowsPerMatch.ALL_ROWS_WITH_UNMATCHED) {
                for (int i = 0; i < held.length; i++) {
                    if (!held[i]) {
                        unmatched.add(members.get(i));
                    }
                }
            }
        }

        // Step 3: the measures, each match a window of its own.
        final Map<Long, Row> values = new HashMap<>();
        if (measured != null && !matched.isEmpty()) {
            final PlannedQuery query = planned(measured, lateralContext,
                withMatched(cteResults, new ResultSet(matchedColumns, matched)), clause);
            for (final Row row : run(query)) {
                values.put(Long.valueOf(((Number) row.getValue(0)).longValue()), row);
            }
        }
        final List<Row> answer = new ArrayList<>();
        for (int i = 0; i < matched.size(); i++) {
            if (!shown.get(i).booleanValue()) {
                continue;
            }
            final List<Object> cells = new ArrayList<>();
            final Row source = sources.get(i);
            if (rows.isOneRow()) {
                for (int c = width; c < width + keys; c++) {
                    cells.add(source.getValue(c));
                }
            } else {
                for (int c = 0; c < width; c++) {
                    cells.add(source.getValue(c));
                }
            }
            final Row measuredRow = values.get(Long.valueOf(i));
            for (int m = 0; m < measures; m++) {
                cells.add(measuredRow == null ? null : measuredRow.getValue(m + 1));
            }
            answer.add(new Row(cells));
        }
        for (final Row source : unmatched) {
            final List<Object> cells = new ArrayList<>();
            for (int c = 0; c < width; c++) {
                cells.add(source.getValue(c));
            }
            for (int m = 0; m < measures; m++) {
                cells.add(null);
            }
            answer.add(new Row(cells));
        }
        return answer;
    }

    /** The row the measures read for one row of a match: the source's columns, then the match's own. */
    private static Row matchedRow(final Row source, final int width, final boolean blank, final long row,
                                  final long match, final long place, final Long number, final Long sequence,
                                  final String classifier, final boolean empty) {
        final List<Object> cells = new ArrayList<>();
        for (int c = 0; c < width; c++) {
            cells.add(blank ? null : source.getValue(c));
        }
        cells.add(Long.valueOf(row));
        cells.add(Long.valueOf(match));
        cells.add(Long.valueOf(place));
        cells.add(number);
        cells.add(sequence);
        cells.add(classifier);
        cells.add(Boolean.valueOf(empty));
        return new Row(cells);
    }

    /** Where the next match is looked for. */
    private static int next(final RowPatternMatch match, final AfterMatchSkip skip, final String skipSymbol) {
        if (skip == AfterMatchSkip.PAST_LAST_ROW) {
            return match.getEnd();
        }
        if (skip == AfterMatchSkip.TO_NEXT_ROW) {
            return match.getStart() + 1;
        }
        final int row = match.rowOf(skipSymbol, skip == AfterMatchSkip.TO_FIRST);
        if (row <= match.getStart()) {
            throw new RuntimeException("AFTER MATCH SKIP TO variable '" + skipSymbol + "' points to the beginning of"
                + " the current match, which will cause an infinite loop");
        }
        return row;
    }

    /** Whether a definition's value reads as true: TRUE, or a number that is not zero. */
    private static boolean holdsTrue(final Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value).booleanValue();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).signum() != 0;
        }
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0;
        }
        return false;
    }
}

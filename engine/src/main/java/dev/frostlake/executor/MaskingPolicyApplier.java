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

import dev.frostlake.executor.expressions.Expression;
import dev.frostlake.metastore.PolicyReferences;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.SqlCharStream;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.NumericType;
import dev.frostlake.values.ExactValues;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * Column-masking and row-access-policy query stage extracted from {@link QueryExecutor}. Masking
 * replaces each masked column's value with the policy's answer as a table's rows enter the query;
 * row-access policies filter the input rows through the policy predicate. NEITHER has a
 * role that bypasses it: the account applies both bodies to every role, ACCOUNTADMIN included, and a
 * body that means to exempt a role says so ITSELF through CURRENT_ROLE(). The catalog and (nullable)
 * security manager are read live from the owning
 * executor so the existing null-checks are preserved; row filtering delegates back to the executor's
 * WHERE-filter helper.
 */
final class MaskingPolicyApplier {

    private final QueryExecutor executor;

    MaskingPolicyApplier(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * Whether a relation holds a column a masking policy is attached to, so that its rows are masked as a query
     * reads them.
     *
     * @param relation the relation
     * @return whether any of its columns is masked
     */
    boolean masks(final Table relation) {
        if (executor.getSecurityManager() == null) {
            return false;
        }
        for (final TableColumn column : relation.columnsView()) {
            if (column.hasMaskingPolicy()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A table's rows as a query reads them: each column a masking policy is attached to holds the policy's answer
     * for the reading role, the policy evaluated over the row as stored. Every role is masked; a body that exempts
     * one names it, as a CURRENT_ROLE() body does.
     *
     * <p>The rows are masked where they enter the query, not by rewriting the references the query writes, so every
     * reference reads the masked value (documented: the policy applies wherever the column appears in the query:
     * projections, JOIN predicates, WHERE predicates, ORDER BY and GROUP BY). That covers the column however it is
     * spelled, quoted or not, qualified or not, in any expression, and through a view, a subquery, a CTE, a UDF's
     * arguments, OBJECT_CONSTRUCT(*) or an unload. The rewrite this replaces read every quoted reference raw, and
     * every clause but the select list.
     *
     * <p>A policy body that reads the very table it masks reads it as stored: masking it there too would never end.
     *
     * @param rows     the rows as stored, laid out in {@code relation}'s columns
     * @param relation the relation the rows are laid out in: the table itself, or a stream's or a CHANGES clause's
     *                 relation, which carry the table's own columns first
     * @param owner    the table the masked columns belong to, which their attachments are read for
     * @return the rows with every masked column masked; {@code rows} itself when no column is
     */
    List<Row> maskRows(final List<Row> rows, final Table relation, final Table owner) {
        if (rows.isEmpty() || !masks(relation) || MASKING.get().contains(owner)) {
            return rows;
        }
        final List<TableColumn> columns = relation.getColumns();
        final List<Integer> slots = new ArrayList<>();
        final List<Expression> bodies = new ArrayList<>();
        final List<DeferredFault> unreadable = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            final TableColumn column = columns.get(i);
            if (column.hasMaskingPolicy()) {
                slots.add(i);
                // An attachment whose policy is gone refuses every read of the column, and only of the column: the
                // cells hold the refusal, which reading one raises (DeferredFault).
                Expression body = null;
                DeferredFault refusal = null;
                try {
                    body = ExpressionEvaluator.parse(maskingPolicyBody(column, owner));
                } catch (final RuntimeException gone) {
                    refusal = new DeferredFault(gone);
                }
                bodies.add(body);
                unreadable.add(refusal);
            }
        }
        final ExpressionEvaluator evaluator = new ExpressionEvaluator(relation, executor.getFunctionRegistry(),
            executor.getCatalog(), executor);
        MASKING.get().add(owner);
        try {
            final List<Row> masked = new ArrayList<>(rows.size());
            for (final Row row : rows) {
                final List<Object> values = new ArrayList<>(row.getValues());
                for (int k = 0; k < slots.size(); k++) {
                    final int slot = slots.get(k);
                    if (slot < values.size()) {
                        values.set(slot, unreadable.get(k) != null ? unreadable.get(k)
                            : maskedValue(evaluator, bodies.get(k), row, columns.get(slot)));
                    }
                }
                masked.add(new Row(values));
            }
            return masked;
        } finally {
            MASKING.get().remove(owner);
        }
    }

    /**
     * The policy's answer for one row, or the fault its body raised there, held in the cell so that only a read of
     * the masked column raises it, as an unreadable cell of a FROM relation does. The stored value never takes its
     * place.
     */
    private static Object maskedValue(final ExpressionEvaluator evaluator, final Expression body, final Row row,
                                      final TableColumn column) {
        try {
            return inColumnCarrier(evaluator.evaluate(body, row), column);
        } catch (final SecurityException refused) {
            throw refused;
        } catch (final RuntimeException failed) {
            return new DeferredFault(failed);
        }
    }

    /** The tables whose rows this thread is masking right now, which a policy body reading them reads as stored. */
    private static final ThreadLocal<Set<Table>> MASKING = new ThreadLocal<Set<Table>>() {
        @Override
        protected Set<Table> initialValue() {
            return Collections.newSetFromMap(new IdentityHashMap<Table, Boolean>());
        }
    };

    /**
     * A masked value in the carrier its column holds, as every stored value is: a scale-0 exact column's Long, a scaled
     * one's BigDecimal at its scale. Anything else is kept as the policy answered it.
     */
    private static Object inColumnCarrier(final Object value, final TableColumn column) {
        final DataType type = column.getDataType();
        if (!(value instanceof Number) || !(type instanceof NumericType) || NumericType.isApproximate(type)
                || ExactValues.isCarrier(value, (NumericType) type)) {
            return value;
        }
        try {
            return ExactValues.written(value, (NumericType) type);
        } catch (final RuntimeException keepAnswered) {
            return value;
        }
    }

    /**
     * The masking policy's body, its first parameter bound to the masked reference. The policy is the one the
     * column's attachment names in full, wherever the session is. An attachment whose policy is gone refuses the
     * query rather than returning the column unmasked.
     */
    private String maskingPolicyBody(final TableColumn column, final Table table) {
        final MaskingPolicy policy = PolicyReferences.maskingPolicy(executor.getCatalog(),
            column.getMaskingPolicyName(), table);
        // The first parameter is the column itself; a conditional policy's further ones are the columns its USING
        // names, in order. One USING left out binds NULL, which no condition reads as permission, rather than
        // whichever column happens to share the parameter's name.
        final String columnExpr = SqlIdentifiers.spellCanonicalEscaped(column.getName());
        final List<String> using = column.getMaskingPolicyArguments();
        final List<String> names = new ArrayList<>();
        final List<String> bound = new ArrayList<>();
        for (int i = 0; i < policy.getParameters().size(); i++) {
            names.add(policy.getParameters().get(i).getName());
            bound.add(i == 0 ? columnExpr : i < using.size() ? SqlIdentifiers.spellCanonicalEscaped(using.get(i)) : "NULL");
        }
        String body = SqlIdentifierSubstitution.substituteParameters(policy.getBody(), names, bound);
        // Resolve tag-context functions against the column/table being masked.
        body = rewriteCurrentTagFunctions(body, table.getName(), columnExpr);
        return "(" + body + ")";
    }

    /** Rewrite SYSTEM$GET_TAG_ON_CURRENT_COLUMN/TABLE in a masking body into concrete GET_TAG calls for the masked column/table. */
    private String rewriteCurrentTagFunctions(final String body, final String tableName, final String columnName) {
        String out = rewriteTagFn(body, "GET_TAG_ON_CURRENT_COLUMN", tableName + "." + columnName, "COLUMN");
        out = rewriteTagFn(out, "GET_TAG_ON_CURRENT_TABLE", tableName, "TABLE");
        return out;
    }

    /**
     * Locates {@code SYSTEM$<fnName>('<tag>')} by TOKEN shape (word, '(', string literal, ')') and
     * splices the rewritten call over the original character span — a regex over the body text broke
     * on an escaped quote inside the tag literal ({@code 'o''brien_tag'}) and on a comment before
     * the argument, both of which the lexer handles for free.
     */
    static String rewriteTagFn(final String body, final String fnName, final String objectName, final String domain) {
        return rewriteTagFn(body, fnName, objectName, domain, null, null);
    }

    /**
     * {@link #rewriteTagFn(String, String, String, String)}, each call read for the object, and for {@code fallback}
     * when the object carries no value of the tag: {@code COALESCE(SYSTEM$GET_TAG(…), SYSTEM$GET_TAG(…))}.
     *
     * @param body           the policy's body
     * @param fnName         the tag function's name after SYSTEM$
     * @param objectName     the object the call reads
     * @param domain         its domain
     * @param fallback       the object read when the first holds no value, or null
     * @param fallbackDomain its domain
     * @return the body rewritten
     */
    static String rewriteTagFn(final String body, final String fnName, final String objectName, final String domain,
                               final String fallback, final String fallbackDomain) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(body));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> toks = stream.getTokens();
        final String target = "SYSTEM$" + fnName;
        final StringBuilder out = new StringBuilder(body.length());
        int cursor = 0;
        for (int i = 0; i < toks.size(); i++) {
            final Token t = toks.get(i);
            if (t.getType() == Token.EOF) {
                break;
            }
            if (!SqlTokens.isWord(t) || !t.getText().equalsIgnoreCase(target)) {
                continue;
            }
            if (i + 3 >= toks.size()
                    || toks.get(i + 1).getType() != FrostlakeLexer.LPAREN
                    || toks.get(i + 2).getType() != FrostlakeLexer.STRING_LITERAL
                    || toks.get(i + 3).getType() != FrostlakeLexer.RPAREN) {
                continue;
            }
            final String tagArg = toks.get(i + 2).getText();
            out.append(body, cursor, t.getStartIndex());
            final String own = "SYSTEM$GET_TAG(" + tagArg + ", '" + objectName + "', '" + domain + "')";
            out.append(fallback == null ? own : "COALESCE(" + own + ", SYSTEM$GET_TAG(" + tagArg + ", '" + fallback
                + "', '" + fallbackDomain + "'))");
            cursor = toks.get(i + 3).getStopIndex() + 1;
            i += 3;
        }
        out.append(body, cursor, body.length());
        return out.toString();
    }

    /**
     * Apply row access policy filtering: evaluate the policy predicate for each row,
     * keeping only rows where the predicate returns true. Every role is filtered.
     *
     * <p>The policy is the one the attachment names in full, wherever the session is. An attachment whose policy
     * is gone, or a body that fails to evaluate, refuses the query: returning the rows unfiltered would hand out
     * exactly what the policy withholds.
     */
    List<Row> applyRowAccessPolicy(final List<Row> rows, final Table table) {
        return applyRowAccessPolicy(rows, table, table);
    }

    /**
     * {@link #applyRowAccessPolicy(List, Table)} with the policy one relation carries, over rows of another shape
     * that holds its columns: a stream's or a CHANGES clause's rows filtered by their table's policy.
     *
     * @param rows  the rows
     * @param table the relation carrying the policy
     * @param shape the relation the rows are rows of
     * @return the rows the policy keeps, the same row objects
     */
    List<Row> applyRowAccessPolicy(final List<Row> rows, final Table table, final Table shape) {
        if (!table.hasRowAccessPolicy()) return rows;
        final SecurityManager securityManager = executor.getSecurityManager();
        if (securityManager == null) return rows;

        final RowAccessPolicy policy = PolicyReferences.rowAccessPolicy(executor.getCatalog(),
            table.getRowAccessPolicyName(), table);

        // Build the predicate by binding each parameter to the column the attachment names, in one pass, so a column
        // bound to one parameter is never read again for the next.
        final List<String> policyCols = table.getRowAccessPolicyColumns();
        final List<Parameter> params = policy.getParameters();
        final List<String> names = new ArrayList<>();
        final List<String> bound = new ArrayList<>();
        for (int i = 0; i < Math.min(params.size(), policyCols.size()); i++) {
            names.add(params.get(i).getName());
            bound.add(policyCols.get(i));
        }
        // A row access policy reads a tag of the table it protects as a masking policy does.
        final String body = rewriteTagFn(
            SqlIdentifierSubstitution.substituteParameters(policy.getBody(), names, bound),
            "GET_TAG_ON_CURRENT_TABLE", table.getName(), "TABLE");
        return executor.filterRows(rows, shape, body);
    }
}

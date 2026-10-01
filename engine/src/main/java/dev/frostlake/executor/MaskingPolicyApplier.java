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

import dev.frostlake.metastore.Catalog;
import dev.frostlake.metastore.QualifiedName;
import dev.frostlake.metastore.model.MaskingPolicy;
import dev.frostlake.metastore.model.Parameter;
import dev.frostlake.metastore.model.RowAccessPolicy;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.security.SecurityManager;
import dev.frostlake.storage.Row;
import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Column-masking and row-access-policy query stage extracted from {@link QueryExecutor}. Masking
 * rewrites each projection expression that references a masked column, wrapping the reference in the
 * policy body; row-access policies filter the input rows through the policy predicate. NEITHER has a
 * role that bypasses it: the account applies both bodies to every role, ACCOUNTADMIN included, and a
 * body that means to exempt a role says so ITSELF through CURRENT_ROLE(). The catalog and (nullable)
 * security manager are read live from the owning
 * executor so the existing null-checks are preserved; row filtering delegates back to the executor's
 * WHERE-filter helper.
 */
final class MaskingPolicyApplier {

    private static final Logger logger = LoggerFactory.getLogger(MaskingPolicyApplier.class);

    private final QueryExecutor executor;

    MaskingPolicyApplier(final QueryExecutor executor) {
        this.executor = executor;
    }

    /**
     * For each projection expression that is a direct reference to a masked column,
     * wrap it with the masking policy body — substituting the policy parameter with the column name.
     * Every role is masked; a body that exempts one names it, as a CURRENT_ROLE() body does.
     */
    List<String> applyMaskingPolicies(final List<String> exprs, final Table table) {
        final SecurityManager securityManager = executor.getSecurityManager();
        if (securityManager == null) return exprs;

        // Collect the table's masked columns once; the common case (none) costs one scan.
        final List<TableColumn> maskedColumns = new ArrayList<>();
        for (final TableColumn col : table.getColumns()) {
            if (col.hasMaskingPolicy()) {
                maskedColumns.add(col);
            }
        }
        if (maskedColumns.isEmpty()) return exprs;

        // Rewrite every REFERENCE to a masked column inside each projection expression — not just
        // expressions that consist solely of the column name. Without this, wrapping the column in
        // any expression (UPPER(ssn), ssn || '', ssn + 0) returned raw, unmasked data.
        final List<String> result = new ArrayList<>(exprs.size());
        for (final String expr : exprs) {
            String rewritten = expr;
            for (final TableColumn col : maskedColumns) {
                rewritten = rewriteMaskedColumnReferences(rewritten, col, table);
            }
            result.add(rewritten);
        }
        return result;
    }

    /**
     * Replace each reference to {@code column} inside {@code expr} with its masking-policy body
     * (the policy parameter bound to the reference text). Handles bare ({@code ssn}) and
     * alias-qualified ({@code e.ssn}) references; skips string literals and same-named function
     * calls ({@code ssn(...)}). A qualified reference whose column part differs is copied verbatim
     * so its parts are never re-matched as bare names.
     */
    private String rewriteMaskedColumnReferences(final String expr, final TableColumn column, final Table table) {
        final String name = column.getName();
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(expr));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        final List<Token> toks = stream.getTokens();   // structural tokens only (WS / comments are skipped)

        final StringBuilder out = new StringBuilder(expr.length());
        int cursor = 0;
        for (int i = 0; i < toks.size(); i++) {
            final Token t = toks.get(i);
            if (t.getType() == Token.EOF) {
                break;
            }
            if (!SqlTokens.isWord(t)) {
                continue;   // string literals, quoted identifiers, numbers, operators — never a column ref
            }

            // Qualified reference: word '.' word — mask when the column part is the masked column.
            if (i + 2 < toks.size() && toks.get(i + 1).getType() == FrostlakeLexer.DOT
                    && SqlTokens.isWord(toks.get(i + 2))
                    && adjacent(t, toks.get(i + 1)) && adjacent(toks.get(i + 1), toks.get(i + 2))) {
                final Token col = toks.get(i + 2);
                if (col.getText().equalsIgnoreCase(name) && !isFunctionCall(toks, i + 2)) {
                    final String reference = expr.substring(t.getStartIndex(), col.getStopIndex() + 1);
                    final String body = resolveMaskingPolicyBody(column.getMaskingPolicyName(), reference, table.getName());
                    out.append(expr, cursor, t.getStartIndex());
                    out.append(body != null ? body : reference);
                    cursor = col.getStopIndex() + 1;
                }
                i += 2;   // the qualified reference is consumed whether or not it was rewritten
                continue;
            }

            // Bare reference: a lone identifier equal to the masked column (and not a function name).
            if (t.getText().equalsIgnoreCase(name) && !isFunctionCall(toks, i)) {
                final String body = resolveMaskingPolicyBody(column.getMaskingPolicyName(), t.getText(), table.getName());
                out.append(expr, cursor, t.getStartIndex());
                out.append(body != null ? body : t.getText());
                cursor = t.getStopIndex() + 1;
            }
        }
        out.append(expr, cursor, expr.length());
        return out.toString();
    }

    /** Whether token {@code b} starts immediately after token {@code a} (no intervening characters). */
    private static boolean adjacent(final Token a, final Token b) {
        return b.getStartIndex() == a.getStopIndex() + 1;
    }

    /** Whether the identifier at {@code idx} is immediately followed by '(' — i.e. it names a function. */
    private static boolean isFunctionCall(final List<Token> toks, final int idx) {
        final int next = idx + 1;
        return next < toks.size() && toks.get(next).getType() == FrostlakeLexer.LPAREN
            && adjacent(toks.get(idx), toks.get(next));
    }

    private String resolveMaskingPolicyBody(final String policyQualifiedName, final String columnExpr, final String tableName) {
        final Catalog catalog = executor.getCatalog();
        try {
            final String[] parts = QualifiedName.parse(policyQualifiedName).parts();
            final Schema schema = parts.length == 1 ? catalog.getDatabase(catalog.getCurrentDatabase())
                    .getSchema(catalog.getCurrentSchema())
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            final String pname = parts[parts.length - 1].toUpperCase();
            final MaskingPolicy policy = schema.getMaskingPolicy(pname);
            if (policy == null) return null;
            // Substitute the first parameter name with the actual column expression
            String body = policy.getBody();
            if (!policy.getParameters().isEmpty()) {
                final String paramName = policy.getParameters().get(0).getName();
                body = SqlIdentifierSubstitution.substitute(body, paramName, columnExpr);
            }
            // Resolve tag-context functions against the column/table being masked.
            body = rewriteCurrentTagFunctions(body, tableName, columnExpr);
            return "(" + body + ")";
        } catch (final Exception e) {
            logger.debug("Could not resolve masking policy {}: {}", policyQualifiedName, e.getMessage());
            return null;
        }
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
    private String rewriteTagFn(final String body, final String fnName, final String objectName, final String domain) {
        final FrostlakeLexer lexer = new FrostlakeLexer(CharStreams.fromString(body));
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
            out.append("SYSTEM$GET_TAG(").append(tagArg).append(", '").append(objectName)
                .append("', '").append(domain).append("')");
            cursor = toks.get(i + 3).getStopIndex() + 1;
            i += 3;
        }
        out.append(body, cursor, body.length());
        return out.toString();
    }

    /**
     * Apply row access policy filtering: evaluate the policy predicate for each row,
     * keeping only rows where the predicate returns true. Every role is filtered.
     */
    List<Row> applyRowAccessPolicy(final List<Row> rows, final Table table) {
        if (!table.hasRowAccessPolicy()) return rows;
        final SecurityManager securityManager = executor.getSecurityManager();
        if (securityManager == null) return rows;

        final Catalog catalog = executor.getCatalog();
        try {
            final String[] parts = table.getRowAccessPolicyName().split("\\.");
            final Schema schema = parts.length == 1 ? catalog.getDatabase(catalog.getCurrentDatabase())
                    .getSchema(catalog.getCurrentSchema())
                : parts.length == 2 ? catalog.getDatabase(catalog.getCurrentDatabase()).getSchema(parts[0])
                : catalog.getDatabase(parts[0]).getSchema(parts[1]);
            final String pname = parts[parts.length - 1].toUpperCase();
            final RowAccessPolicy policy = schema.getRowAccessPolicy(pname);
            if (policy == null) return rows;

            // Build predicate by substituting policy parameters with actual column names
            String body = policy.getBody();
            final List<String> policyCols = table.getRowAccessPolicyColumns();
            final List<Parameter> params = policy.getParameters();
            for (int i = 0; i < Math.min(params.size(), policyCols.size()); i++) {
                final String paramName = params.get(i).getName();
                final String colRef = policyCols.get(i);
                body = SqlIdentifierSubstitution.substitute(body, paramName, colRef);
            }

            final String predicate = body;
            return executor.filterRows(rows, table, predicate);
        } catch (final Exception e) {
            logger.warn("Row access policy evaluation failed for table {}: {}", table.getName(), e.getMessage());
            return rows;
        }
    }
}

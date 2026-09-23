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
import dev.frostlake.types.StringType;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.List;

/**
 * The relation a SELECT with no FROM reads: DUAL, one row whose one column COLUMN1 holds NULL. A plain star
 * expands to COLUMN1, so {@code SELECT *} answers one NULL, and {@code COLUMN1}, {@code DUAL.COLUMN1},
 * {@code DUAL.*} and {@code $1} read it. It shadows an outer COLUMN1 in a FROM-less subquery and a
 * same-named item in WHERE and in the items after it, and an aggregate query that reads it outside an
 * aggregate is refused as "[DUAL.COLUMN1] is not a valid group by expression". A star written as a function
 * argument sees none of it, qualified or filtered: {@code SELECT *, HASH(*)} is refused as {@code HASH()} and
 * {@code SELECT COUNT(fz.*)} as {@code COUNT()} (live-verified).
 */
public final class FromlessDual extends Table {

    /** The relation's name, which a qualifier reaches it by. */
    public static final String NAME = "DUAL";
    /** Its one column. */
    public static final String COLUMN = "COLUMN1";

    public FromlessDual() {
        this(true);
    }

    private FromlessDual(final boolean read) {
        super(NAME, columns(read), false);
    }

    /**
     * DUAL as a FROM-less select that never reads it is evaluated over: no column at all, since nothing at its
     * level names one.
     *
     * @return the columnless DUAL
     */
    public static FromlessDual unread() {
        return new FromlessDual(false);
    }

    private static List<TableColumn> columns(final boolean read) {
        final List<TableColumn> columns = new ArrayList<>();
        if (read) {
            columns.add(new TableColumn(COLUMN, StringType.VARCHAR, true, null, false, false, false));
        }
        return columns;
    }

    /**
     * Whether a FROM-less select reads DUAL at its own level: a plain star item, a {@code DUAL.*} item, or a
     * name reaching COLUMN1 ({@code COLUMN1}, {@code DUAL.<name>}, {@code $n}) in its items, WHERE, GROUP BY,
     * HAVING, QUALIFY or ORDER BY. A query nested inside reads a DUAL of its own.
     *
     * @param stmtCtx the statement the select clause belongs to, or null
     * @param ctx     the FROM-less select clause
     * @return true when the select reads DUAL
     */
    public static boolean isRead(final FrostlakeParser.SelectStatementContext stmtCtx,
                                 final FrostlakeParser.SelectClauseContext ctx) {
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            if (SelectItemAccessors.isStarItem(item)) {
                return true;
            }
            if (SelectItemAccessors.isQualifiedStarItem(item)
                    && namesDual(((FrostlakeParser.QualifiedStarItemContext) item).starQualifiedName())) {
                return true;
            }
        }
        if (readsIn(ctx.selectList()) || readsIn(ParseTreeText.getWhereClause(ctx)) || readsIn(ctx.groupByClause())
                || readsIn(ctx.havingClause()) || readsIn(ctx.qualifyClause())) {
            return true;
        }
        return stmtCtx != null && readsIn(stmtCtx.orderByClause());
    }

    /**
     * Whether a star's qualifier names DUAL: one part, DUAL unquoted or {@code "DUAL"}. {@code "dual".*} and
     * {@code PUBLIC.DUAL.*} name no object (live-verified).
     *
     * @param qualifier the star's qualifier
     * @return true for DUAL
     */
    public static boolean namesDual(final FrostlakeParser.StarQualifiedNameContext qualifier) {
        final String[] parts = ParseTreeText.qualifiedNameParts(qualifier);
        return parts.length == 1 && NAME.equals(parts[0]);
    }

    private static boolean readsIn(final ParseTree node) {
        if (node == null || node instanceof FrostlakeParser.SelectStatementContext) {
            return false;
        }
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName();
            if (readsName(ParseTreeText.qualifiedNameParts(name))) {
                return true;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readsIn(node.getChild(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean readsName(final String[] parts) {
        if (parts.length == 1) {
            return COLUMN.equals(parts[0]) || parts[0].matches("\\$[0-9]+");
        }
        return parts.length == 2 && NAME.equals(parts[0]);
    }
}

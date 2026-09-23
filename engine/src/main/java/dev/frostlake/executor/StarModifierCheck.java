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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The refusals of a star's EXCLUDE, RENAME and REPLACE lists, judged against the columns the star reads once
 * its relations are known and before any name of the query is. A list naming a column twice is "duplicate column
 * name 'X'", a name no column of the star carries is "column 'X' does not exist", and a name two of the star's
 * columns carry is "ambiguous column name 'X'". Names match exactly, so {@code EXCLUDE ("id")} over a column ID
 * is "column 'id' does not exist". The modifiers are read in written order: a column an EXCLUDE dropped or an
 * ILIKE filtered out no longer exists for the ones after it (live-verified).
 */
final class StarModifierCheck {

    private StarModifierCheck() {
    }

    /**
     * Whether a column is one a star expands to. A staged file's METADATA$FILENAME and METADATA$FILE_ROW_NUMBER
     * resolve by name but never expand from a star, and neither does the right-hand copy of a USING or NATURAL
     * join's key; a stream's metadata columns do.
     *
     * @param column the column
     * @return true when a star reads it
     */
    static boolean starVisible(final TableColumn column) {
        final String key = column.getName().toUpperCase();
        return !key.equals("METADATA$FILENAME") && !key.equals("METADATA$FILE_ROW_NUMBER")
            && !column.isHiddenFromStar();
    }

    /**
     * Refuses the first modifier of a plain star item, or of an unqualified braced star over a FROM clause, that
     * names no column, a column twice, or an ambiguous one.
     *
     * @param ctx      the select clause
     * @param relation the relation the clause reads, its FROM clause's relations merged
     */
    static void reject(final FrostlakeParser.SelectClauseContext ctx, final Table relation) {
        reject(ctx, relation, null);
    }

    /**
     * As {@link #reject(FrostlakeParser.SelectClauseContext, Table)}, and a QUALIFIED star's modifiers too — plain
     * or braced — each judged against the columns of the one relation its qualifier names: {@code fz.* EXCLUDE
     * nosuch} and {@code {fz.* EXCLUDE nosuch}} are "column 'NOSUCH' does not exist", and so is {@code a.* EXCLUDE
     * v} where only the other side of the join carries V (live-verified). A qualifier naming no relation is left
     * to its own refusal.
     *
     * @param ctx       the select clause
     * @param relation  the relation the clause reads, its FROM clause's relations merged
     * @param relations the FROM clause's relations by the key each registered, or null
     */
    static void reject(final FrostlakeParser.SelectClauseContext ctx, final Table relation,
                       final FromClauseRelations relations) {
        for (final FrostlakeParser.SelectItemContext item : ctx.selectList().selectItem()) {
            final boolean braced = SelectItemAccessors.isObjectStarItem(item) && ctx.tableExpression() != null
                && SelectItemAccessors.getItemQualifierParts(item) == null;
            if (SelectItemAccessors.isStarItem(item) || braced) {
                rejectModifiers(SelectItemAccessors.getStarModifiers(item), relation);
            } else if (relations != null && (SelectItemAccessors.isQualifiedStarItem(item)
                    || SelectItemAccessors.isObjectStarItem(item) && ctx.tableExpression() != null)) {
                final Table named = namedRelation(SelectItemAccessors.getItemQualifierParts(item), relations);
                if (named != null) {
                    rejectModifiers(SelectItemAccessors.getStarModifiers(item), named);
                }
            }
        }
    }

    /** The relation a star's qualifier names among the FROM clause's relations, or null. */
    private static Table namedRelation(final String[] written, final FromClauseRelations relations) {
        for (final Map.Entry<String, Table> entry : relations.entrySet()) {
            if (QueryExecutor.starQualifierMatches(written, entry.getKey(), entry.getValue(),
                    relations.isAlias(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static void rejectModifiers(final List<FrostlakeParser.StarModifierContext> modifiers,
                                        final Table relation) {
        if (modifiers.isEmpty()) {
            return;
        }
        final List<String> available = new ArrayList<>();
        for (final TableColumn column : relation.getColumns()) {
            if (starVisible(column)) {
                available.add(column.getName());
            }
        }
        for (final FrostlakeParser.StarModifierContext modifier : modifiers) {
            if (modifier.ILIKE() != null) {
                final String literal = modifier.STRING_LITERAL().getText();
                final Pattern pattern = Pattern.compile(
                    QueryExecutor.ilikeToRegex(literal.substring(1, literal.length() - 1)), Pattern.CASE_INSENSITIVE);
                final List<String> matching = new ArrayList<>();
                for (final String name : available) {
                    if (pattern.matcher(name).matches()) {
                        matching.add(name);
                    }
                }
                available.retainAll(matching);
                continue;
            }
            final List<String> names = namesOf(modifier);
            final Set<String> seen = new HashSet<>();
            for (final String name : names) {
                if (!seen.add(name)) {
                    throw new RuntimeException(SqlCompilationError.of("duplicate column name '" + name + "'"));
                }
            }
            for (final String name : names) {
                int carriers = 0;
                for (final String column : available) {
                    if (column.equals(name)) {
                        carriers++;
                    }
                }
                if (carriers == 0) {
                    throw new RuntimeException(SqlCompilationError.of("column '" + name + "' does not exist"));
                }
                if (carriers > 1) {
                    throw new RuntimeException(SqlCompilationError.of("ambiguous column name '" + name + "'"));
                }
            }
            if (modifier.EXCLUDE() != null) {
                available.removeAll(names);
            }
        }
    }

    /** The canonical column names one EXCLUDE, RENAME or REPLACE modifier names, in written order. */
    private static List<String> namesOf(final FrostlakeParser.StarModifierContext modifier) {
        final List<String> names = new ArrayList<>();
        if (modifier.EXCLUDE() != null) {
            for (final FrostlakeParser.ExcludedColumnContext id : modifier.excludedColumn()) {
                names.add(SelectItemAccessors.excludedName(id));
            }
        } else if (modifier.RENAME() != null) {
            for (final FrostlakeParser.StarRenameItemContext rename : modifier.starRenameItem()) {
                names.add(ParseTreeText.getIdentifier(rename.identifier(0)));
            }
        } else if (modifier.REPLACE() != null) {
            for (final FrostlakeParser.StarReplaceItemContext replace : modifier.starReplaceItem()) {
                names.add(ParseTreeText.getIdentifier(replace.identifier()));
            }
        }
        return names;
    }
}

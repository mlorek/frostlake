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
import dev.frostlake.executor.expressions.FunctionCallExpression;
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
 * A star written as a function ARGUMENT — {@code COUNT(*)}, {@code HASH_AGG(t.*)},
 * {@code COUNT(* EXCLUDE (a))}, {@code COUNT(* ILIKE 'a%')} — and what it stands for: the in-scope
 * columns it expands to, so that the call is then an ordinary multi-argument one (live-verified: a
 * qualified star expands to that relation's columns only, an EXCLUDE drops the named columns, an ILIKE
 * keeps the matching ones, and {@code HASH_AGG(t.*)} equals {@code HASH_AGG(t.a, t.b, t.c)}).
 *
 * <p>The three refusals are the account's: a qualifier naming no relation in scope is "Object 'X' does
 * not exist or not authorized.", an EXCLUDE naming no column is "column 'X' does not exist", and one
 * naming a column twice is "duplicate column name 'X'". A star that expands to nothing is left to the
 * caller's arity rule, which spells "not enough arguments for function [COUNT()], expected 1, got 0".
 *
 * <p>The one star this does NOT expand is the bare, unqualified, unmodified {@code *} under COUNT,
 * which counts rows; every other shape — a qualifier, an EXCLUDE, an ILIKE, another aggregate — is
 * the written-out list, and a COUNT over that list counts the rows in which every column is non-NULL.
 */
public final class StarArgument {

    private final String qualifier;
    private final List<String> excludes;
    private final String ilike;

    /**
     * @param qualifier the relation a qualified star names, canonical upper-cased, or null
     * @param excludes the EXCLUDE names, canonical upper-cased, in written order (duplicates kept)
     * @param ilike the ILIKE pattern between its quotes, or null
     */
    public StarArgument(final String qualifier, final List<String> excludes, final String ilike) {
        this.qualifier = qualifier;
        this.excludes = excludes;
        this.ilike = ilike;
    }

    /** The star of a star-call parse tree ({@code COUNT(t.* EXCLUDE (a))}). */
    public static StarArgument of(final FrostlakeParser.FunctionCallStarExprContext starCall) {
        return new StarArgument(qualifierOf(starCall.starQualifiedName()),
            excludesOf(starCall.starArgumentModifier()), ilikeOf(starCall.starArgumentModifier()));
    }

    /** The star of a star function argument, or null when the argument is not a star. */
    public static StarArgument of(final FrostlakeParser.FunctionArgContext arg) {
        if (arg == null || arg.STAR() == null) {
            return null;
        }
        return new StarArgument(qualifierOf(arg.starQualifiedName()),
            excludesOf(arg.starArgumentModifier()), ilikeOf(arg.starArgumentModifier()));
    }

    /** The star a star-shaped call node carries. */
    public static StarArgument of(final FunctionCallExpression call) {
        return new StarArgument(call.getStarQualifier(), call.getStarExcludes(), call.getStarIlike());
    }

    /** The star a window call's sole argument is, or null when the call's argument is not a star. */
    public static StarArgument ofWindowCall(final FrostlakeParser.FunctionCallExprContext call) {
        if (call.functionArgList() == null || call.functionArgList().functionArg().size() != 1) {
            return null;
        }
        return of(call.functionArgList().functionArg(0));
    }

    public String getQualifier() {
        return qualifier;
    }

    /** Whether this is the bare {@code *} — no qualifier and no filter — that COUNT counts rows by. */
    public boolean isBare() {
        return qualifier == null && excludes.isEmpty() && ilike == null;
    }

    /**
     * The columns this star stands for, as argument texts.
     *
     * @param table the source relation
     * @param aliasToTable the FROM clause's alias map, or null for a single relation
     * @param allTables every relation in scope in FROM order, or null for a single relation
     * @param qualified whether to spell each column as {@code RELATION.COLUMN} (the plain path's
     *                  texts and every echo) or as the bare column name (a single relation's row)
     * @return the argument texts, in relation order then column order
     */
    public List<String> expand(final Table table, final Map<String, Table> aliasToTable,
                               final List<Table> allTables, final boolean qualified) {
        final List<String> texts = new ArrayList<>();
        for (final String[] column : columnsOf(table, aliasToTable, allTables)) {
            texts.add(qualified ? column[0] + "." + column[1] : column[1]);
        }
        return texts;
    }

    /**
     * The columns this star names, as references — see {@link #expand} — qualified by the relation each
     * belongs to when {@code qualified}, which a join needs to tell its sides apart.
     */
    public List<ColumnReferenceExpression> expandReferences(final Table table, final Map<String, Table> aliasToTable,
                                                          final List<Table> allTables, final boolean qualified) {
        final List<ColumnReferenceExpression> references = new ArrayList<>();
        for (final String[] column : columnsOf(table, aliasToTable, allTables)) {
            references.add(qualified ? new ColumnReferenceExpression(column[0], column[1])
                : new ColumnReferenceExpression(column[1]));
        }
        return references;
    }

    /** Each column the star names, as its relation's spelling and its own name. */
    private List<String[]> columnsOf(final Table table, final Map<String, Table> aliasToTable,
                                     final List<Table> allTables) {
        final List<Table> sources = new ArrayList<>();
        if (allTables == null || allTables.isEmpty()) {
            if (table != null) {
                sources.add(table);
            }
        } else {
            sources.addAll(allTables);
        }
        final List<Table> chosen = qualifier == null ? sources : matching(sources, aliasToTable);
        final Set<String> excluded = new HashSet<>();
        for (final String name : excludes) {
            if (!excluded.add(name)) {
                throw new RuntimeException(SqlCompilationError.of("duplicate column name '" + name + "'"));
            }
            if (!hasColumn(chosen, name)) {
                throw new RuntimeException(SqlCompilationError.of("column '" + name + "' does not exist"));
            }
        }
        final Pattern pattern = ilike == null ? null
            : Pattern.compile(ilikeToRegex(ilike), Pattern.CASE_INSENSITIVE);
        final List<String[]> columns = new ArrayList<>();
        for (final Table source : chosen) {
            final String spelledAs = spelling(source, aliasToTable);
            for (final TableColumn column : source.getColumns()) {
                final String name = column.getName();
                if (excluded.contains(name.toUpperCase()) || column.isHiddenFromStar()
                        || (pattern != null && !pattern.matcher(name).matches())) {
                    continue;
                }
                columns.add(new String[] {spelledAs, name});
            }
        }
        return columns;
    }

    /** How a relation is reached in the query: its alias when it has one, else its own name. */
    private static String spelling(final Table source, final Map<String, Table> aliasToTable) {
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                if (entry.getValue() == source) {
                    return entry.getKey();
                }
            }
        }
        return source.getName();
    }

    /** The relations the qualifier names — by alias or by name — or the account's refusal. */
    private List<Table> matching(final List<Table> sources, final Map<String, Table> aliasToTable) {
        final List<Table> chosen = new ArrayList<>();
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                if (qualifier.equalsIgnoreCase(entry.getKey()) && !chosen.contains(entry.getValue())) {
                    chosen.add(entry.getValue());
                }
            }
        }
        if (chosen.isEmpty()) {
            for (final Table source : sources) {
                if (source.getName() != null && qualifier.equalsIgnoreCase(source.getName())
                        && !aliasedAway(source, aliasToTable)) {
                    chosen.add(source);
                }
            }
        }
        if (chosen.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + qualifier + "' does not exist or not authorized."));
        }
        return chosen;
    }

    /** Whether a relation is reachable only through an alias, which hides its own name (live). */
    private static boolean aliasedAway(final Table source, final Map<String, Table> aliasToTable) {
        if (aliasToTable == null) {
            return false;
        }
        for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
            if (entry.getValue() == source) {
                return !entry.getKey().equalsIgnoreCase(source.getName());
            }
        }
        return false;
    }

    private static boolean hasColumn(final List<Table> sources, final String name) {
        for (final Table source : sources) {
            for (final TableColumn column : source.getColumns()) {
                if (column.getName().equalsIgnoreCase(name)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String qualifierOf(final FrostlakeParser.StarQualifiedNameContext name) {
        if (name == null) {
            return null;
        }
        // The LAST part names the relation, as a column's qualifier does: COUNT(db.sch.t.*) and
        // COUNT(t.*) resolve alike (live-verified).
        final String[] parts = ParseTreeText.qualifiedNameParts(name);
        return parts[parts.length - 1].toUpperCase();
    }

    private static List<String> excludesOf(final List<FrostlakeParser.StarArgumentModifierContext> modifiers) {
        final List<String> excludes = new ArrayList<>();
        for (final FrostlakeParser.StarArgumentModifierContext modifier : modifiers) {
            if (modifier.EXCLUDE() == null) {
                continue;
            }
            for (final FrostlakeParser.IdentifierContext id : modifier.identifier()) {
                excludes.add(SqlIdentifiers.canonical(id).toUpperCase());
            }
        }
        return excludes;
    }

    private static String ilikeOf(final List<FrostlakeParser.StarArgumentModifierContext> modifiers) {
        for (final FrostlakeParser.StarArgumentModifierContext modifier : modifiers) {
            if (modifier.ILIKE() != null) {
                final String written = modifier.STRING_LITERAL().getText();
                return written.substring(1, written.length() - 1);
            }
        }
        return null;
    }

    private static String ilikeToRegex(final String pattern) {
        final StringBuilder regex = new StringBuilder();
        for (int i = 0; i < pattern.length(); i++) {
            final char c = pattern.charAt(i);
            if (c == '%') {
                regex.append(".*");
            } else if (c == '_') {
                regex.append('.');
            } else if ("[](){}+*?.^$|\\".indexOf(c) >= 0) {
                regex.append('\\').append(c);
            } else {
                regex.append(c);
            }
        }
        return regex.toString();
    }
}

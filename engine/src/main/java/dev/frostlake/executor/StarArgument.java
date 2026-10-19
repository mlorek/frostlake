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
import java.util.Collections;
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
 * <p>The four refusals are the account's: a qualifier naming no relation in scope is "Object 'X' does
 * not exist or not authorized.", an EXCLUDE naming no column is "column 'X' does not exist", one
 * naming a column twice is "duplicate column name 'X'", and one naming a column more than one relation of
 * an unqualified star holds — a USING key aside — is "ambiguous column name 'X'". With no FROM the star
 * stands for no column, whatever it qualifies or filters, and none of them is raised. A star that expands to
 * nothing is left to the caller's arity rule, which spells "not enough arguments for function [COUNT()],
 * expected 1, got 0".
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
     * @param excludes the EXCLUDE names in their canonical spelling (an unquoted name upper-cased, a quoted
     *     one as written), in written order (duplicates kept); they match a column's name EXACTLY, so
     *     {@code EXCLUDE ("x")} leaves a column named {@code X} alone
     * @param ilike the ILIKE pattern between its quotes, or null
     */
    public StarArgument(final String qualifier, final List<String> excludes, final String ilike) {
        this.qualifier = qualifier;
        this.excludes = excludes;
        this.ilike = ilike;
    }

    /** The star of a star-call parse tree ({@code COUNT(t.* EXCLUDE (a))}). */
    public static StarArgument of(final FrostlakeParser.FunctionCallStarExprContext starCall) {
        final List<FrostlakeParser.StarArgumentModifierContext> modifiers = modifiers(starCall.starArgumentModifier());
        return new StarArgument(qualifierOf(starCall.starQualifiedName()), excludesOf(modifiers), ilikeOf(modifiers));
    }

    /**
     * A star's modifier as the list the readers take: none, or the one a star argument holds at most.
     *
     * @param modifier the modifier written, or null
     * @return the list
     */
    public static List<FrostlakeParser.StarArgumentModifierContext> modifiers(
            final FrostlakeParser.StarArgumentModifierContext modifier) {
        return modifier == null ? Collections.<FrostlakeParser.StarArgumentModifierContext>emptyList()
            : Collections.singletonList(modifier);
    }

    /** The star of a star function argument, or null when the argument is not a star. */
    public static StarArgument of(final FrostlakeParser.FunctionArgContext arg) {
        if (arg == null || arg.STAR() == null) {
            return null;
        }
        return new StarArgument(qualifierOf(arg.starQualifiedName()),
            excludesOf(modifiers(arg.starArgumentModifier())), ilikeOf(modifiers(arg.starArgumentModifier())));
    }

    /** The star written first among a named call's positional arguments ({@code SEARCH(*, 'q', SEARCH_MODE => …)}). */
    public static StarArgument of(final FrostlakeParser.MixedStarArgumentContext arg) {
        final List<FrostlakeParser.StarArgumentModifierContext> modifiers = modifiers(arg.starArgumentModifier());
        return new StarArgument(qualifierOf(arg.starQualifiedName()), excludesOf(modifiers), ilikeOf(modifiers));
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

    /** The EXCLUDE names, in their canonical spelling. */
    public List<String> getExcludes() {
        return excludes;
    }

    /** The ILIKE pattern between its quotes, or null. */
    public String getIlike() {
        return ilike;
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
        if (sources.size() == 1 && sources.get(0) instanceof FromlessDual) {
            return new ArrayList<>();
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
            if (qualifier == null && holders(chosen, name) > 1 && !joinKey(table, name)) {
                // An unqualified star over relations that each hold the name cannot tell which it drops.
                throw new RuntimeException(SqlCompilationError.of("ambiguous column name '" + name + "'"));
            }
        }
        final Pattern pattern = ilike == null ? null : ilikePattern(ilike);
        final List<String[]> columns = new ArrayList<>();
        for (final Table source : chosen) {
            final String spelledAs = spelling(source, aliasToTable);
            for (final TableColumn column : source.getColumns()) {
                final String name = column.getName();
                if (excluded.contains(name) || column.isHiddenFromStar()
                        || (pattern != null && !pattern.matcher(name).matches())) {
                    continue;
                }
                columns.add(new String[] {spelledAs, name});
            }
        }
        return columns;
    }

    /** How many of the relations hold a column of exactly this name. */
    private static int holders(final List<Table> relations, final String name) {
        int held = 0;
        for (final Table relation : relations) {
            for (final TableColumn column : relation.getColumns()) {
                if (column.getName().equals(name)) {
                    held++;
                    break;
                }
            }
        }
        return held;
    }

    /** Whether the name is a key a USING or NATURAL join merged, which the relations hold as one column. */
    private static boolean joinKey(final Table table, final String name) {
        final List<String> keys = table == null ? null : table.getJoinKeyNames();
        return keys != null && keys.contains(name);
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

    /**
     * The relations the qualifier names — by alias or by name, each by its canonical name exactly, as a star item's
     * qualifier names one: a quoted part names itself, an unquoted one its upper-cased self (live-verified) — or the
     * account's refusal, which echoes the qualifier quoted where its name needs it.
     */
    private List<Table> matching(final List<Table> sources, final Map<String, Table> aliasToTable) {
        final List<Table> chosen = new ArrayList<>();
        if (aliasToTable != null) {
            for (final Map.Entry<String, Table> entry : aliasToTable.entrySet()) {
                if (qualifier.equals(entry.getKey()) && !chosen.contains(entry.getValue())) {
                    chosen.add(entry.getValue());
                }
            }
        }
        if (chosen.isEmpty()) {
            for (final Table source : sources) {
                if (source.getName() != null && qualifier.equals(source.getName())
                        && !aliasedAway(source, aliasToTable)) {
                    chosen.add(source);
                }
            }
        }
        if (chosen.isEmpty()) {
            throw new RuntimeException(SqlCompilationError.of(
                "Object '" + SqlIdentifiers.spellCanonical(qualifier) + "' does not exist or not authorized."));
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
                return !entry.getKey().equals(source.getName());
            }
        }
        return false;
    }

    private static boolean hasColumn(final List<Table> sources, final String name) {
        for (final Table source : sources) {
            for (final TableColumn column : source.getColumns()) {
                if (column.getName().equals(name)) {
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
        return parts[parts.length - 1];
    }

    private static List<String> excludesOf(final List<FrostlakeParser.StarArgumentModifierContext> modifiers) {
        final List<String> excludes = new ArrayList<>();
        for (final FrostlakeParser.StarArgumentModifierContext modifier : modifiers) {
            if (modifier.EXCLUDE() == null) {
                continue;
            }
            for (final FrostlakeParser.ExcludedColumnContext id : modifier.excludedColumn()) {
                excludes.add(SelectItemAccessors.excludedName(id));
            }
        }
        return excludes;
    }

    private static String ilikeOf(final List<FrostlakeParser.StarArgumentModifierContext> modifiers) {
        for (final FrostlakeParser.StarArgumentModifierContext modifier : modifiers) {
            if (modifier.ILIKE() != null) {
                return ParseTreeText.stringBody(modifier.stringText());
            }
        }
        return null;
    }

    /**
     * The test an ILIKE pattern puts a column's name to.
     *
     * @param ilike the pattern between its quotes
     * @return the pattern, matching whole names whatever their case
     */
    public static Pattern ilikePattern(final String ilike) {
        return Pattern.compile(ilikeToRegex(ilike), Pattern.CASE_INSENSITIVE);
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

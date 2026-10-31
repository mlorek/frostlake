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

package dev.frostlake.metastore;

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlTokens;
import dev.frostlake.metastore.model.DefaultValueExpression;
import dev.frostlake.metastore.model.ForeignKeyConstraint;
import dev.frostlake.metastore.model.MaterializedView;
import dev.frostlake.metastore.model.Schema;
import dev.frostlake.metastore.model.Table;
import dev.frostlake.metastore.model.TableColumn;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.SqlCharStream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.Token;

/**
 * The references a clone's objects hold to other objects by name: a foreign key's parent table, a column DEFAULT's
 * sequence, a materialized view's base table. Each is pointed at what Snowflake's documentation says the clone
 * references:
 * <ul>
 *   <li>when the database or schema holding both tables is cloned, "the cloned table with the foreign key
 *       references the primary key in the other cloned table"; when they are in separate databases or schemas,
 *       it "references the primary key in the source table";</li>
 *   <li>when the container holding "both the table and sequence is cloned, the cloned table references the cloned
 *       sequence. Otherwise, the cloned table references the source sequence", which is also what a table cloned by
 *       itself does;</li>
 *   <li>when a materialized view and its base table are cloned in one operation, "the cloned materialized view
 *       refers to the cloned base table"; when only the view is cloned, it refers to the original one.</li>
 * </ul>
 *
 * <p>A reference is read relative to the object holding it: a bare name in its own schema, a two-part one in its
 * own database. A reference that already reads the right object from the clone, as a bare name to an object
 * cloned alongside does, is left as written. One that would read the wrong object is re-spelled, keeping as few
 * parts as still read correctly.
 */
public final class CloneReferences {

    private CloneReferences() {
    }

    /**
     * Points the references of a database or schema clone's objects at what the clone references.
     *
     * @param scope the clone's schemas
     * @param from  what was cloned, in canonical parts: {db} or {db, schema}
     * @param to    the clone, in as many parts
     */
    public static void repoint(final List<Schema> scope, final String[] from, final String[] to) {
        for (final Schema clone : scope) {
            if (clone.getDatabaseName() == null) {
                continue;
            }
            final String[] cloneHome = {clone.getDatabaseName(), clone.getName()};
            final String[] sourceHome = swapped(cloneHome, to, from);
            for (final Table table : clone.getTables()) {
                repointTable(table, sourceHome, cloneHome, from, to);
            }
            for (final MaterializedView view : clone.getMaterializedViews()) {
                repointView(view, sourceHome, cloneHome, from, to);
            }
        }
    }

    /**
     * Keeps a table cloned by itself referencing what its source references: nothing was cloned with it, so a
     * name that would read another object from the clone's schema is spelled out in full.
     *
     * @param clone      the cloned table
     * @param sourceHome {db, schema} of the source table
     * @param cloneHome  {db, schema} of the clone
     */
    public static void keepForTableClone(final Table clone, final String[] sourceHome, final String[] cloneHome) {
        repointTable(clone, sourceHome, cloneHome, null, null);
    }

    private static void repointTable(final Table table, final String[] sourceHome, final String[] cloneHome,
                                     final String[] from, final String[] to) {
        for (final ForeignKeyConstraint foreignKey : table.getForeignKeys()) {
            final String[] parent = respelled(QualifiedName.parse(foreignKey.getReferencedTable()).parts(),
                sourceHome, cloneHome, from, to);
            if (parent != null) {
                table.replaceForeignKey(foreignKey, foreignKey.referencing(QualifiedName.join(parent)));
            }
        }
        for (final TableColumn column : table.getColumns()) {
            if (column.getReferencedTable() != null) {
                final String[] parent = respelled(QualifiedName.parse(column.getReferencedTable()).parts(),
                    sourceHome, cloneHome, from, to);
                if (parent != null) {
                    column.setReferencedTable(QualifiedName.join(parent));
                }
            }
            if (column.getDefaultValue() instanceof DefaultValueExpression) {
                final DefaultValueExpression stored = (DefaultValueExpression) column.getDefaultValue();
                final String text = stored.getExpressionText();
                final String rewritten = withSequencesRespelled(text, sourceHome, cloneHome, from, to);
                if (!rewritten.equals(text)) {
                    final String told = stored.toldTextOrNull();
                    column.setDefaultValue(new DefaultValueExpression(rewritten,
                        told == null ? null : withSequencesRespelled(told, sourceHome, cloneHome, from, to)));
                }
            }
        }
    }

    /**
     * A cloned materialized view's base table: the clone's copy when the table was cloned with it. Both the source it
     * is listed with and the query it is read through are pointed there.
     */
    private static void repointView(final MaterializedView view, final String[] sourceHome, final String[] cloneHome,
                                    final String[] from, final String[] to) {
        if (view.getSourceDatabase() == null || view.getSourceSchema() == null || view.getSourceTable() == null) {
            return;
        }
        final String[] base = {view.getSourceDatabase(), view.getSourceSchema(), view.getSourceTable()};
        if (!under(base, from)) {
            return;
        }
        final String[] cloned = swapped(base, from, to);
        view.setSource(cloned[0], cloned[1], cloned[2]);
        view.setDefinition(withTableRespelled(view.getDefinition(), base, sourceHome, cloneHome, from, to));
    }

    /**
     * The spelling a cloned object's reference takes so that, read from the clone, it names the object the clone
     * references: the clone's own copy when the object lies under {@code from}, the source's object otherwise.
     *
     * @param written    the reference as written, in canonical parts
     * @param sourceHome {db, schema} of the source object holding the reference
     * @param cloneHome  {db, schema} of the clone holding it
     * @param from       what was cloned, or null when only the object itself was
     * @param to         the clone of {@code from}, or null
     * @return the parts to write, or null when the written ones already read the right object
     */
    static String[] respelled(final String[] written, final String[] sourceHome, final String[] cloneHome,
                              final String[] from, final String[] to) {
        final String[] source = completed(written, sourceHome);
        if (source == null) {
            return null;
        }
        final String[] intended = under(source, from) ? swapped(source, from, to) : source;
        if (Arrays.equals(completed(written, cloneHome), intended)) {
            return null;
        }
        final String[] same = Arrays.copyOfRange(intended, intended.length - written.length, intended.length);
        return Arrays.equals(completed(same, cloneHome), intended) ? same : intended;
    }

    /** A reference read where its holder lives: a bare name in its schema, two parts in its database. */
    private static String[] completed(final String[] parts, final String[] home) {
        if (parts.length == 3) {
            return parts.clone();
        }
        if (parts.length == 2) {
            return new String[] {home[0], parts[0], parts[1]};
        }
        if (parts.length == 1) {
            return new String[] {home[0], home[1], parts[0]};
        }
        return null;
    }

    /** Whether a full name lies under {@code prefix}: in that database, or in that schema. */
    private static boolean under(final String[] full, final String[] prefix) {
        if (prefix == null) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (!full[i].equals(prefix[i])) {
                return false;
            }
        }
        return true;
    }

    /** {@code parts} with its leading {@code from} replaced by {@code to}. */
    private static String[] swapped(final String[] parts, final String[] from, final String[] to) {
        final String[] swapped = parts.clone();
        System.arraycopy(to, 0, swapped, 0, Math.min(to.length, Math.min(from.length, parts.length)));
        return swapped;
    }

    /**
     * A DEFAULT's text with each {@code [db.][schema.]seq.NEXTVAL} re-spelled for the clone. A DEFAULT reads its
     * sequence where its table lives, so the reference is completed the same way a foreign key's is.
     */
    static String withSequencesRespelled(final String text, final String[] sourceHome, final String[] cloneHome,
                                         final String[] from, final String[] to) {
        final List<Token> tokens = tokens(text);
        final StringBuilder out = new StringBuilder(text.length());
        int cursor = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if (!isName(tokens.get(i)) || precededByDot(tokens, i)) {
                continue;
            }
            final int end = chainEnd(tokens, i);
            final int parts = (end - i) / 2 + 1;
            final Token last = tokens.get(end);
            if (parts >= 2 && parts <= 4 && SqlTokens.isWord(last) && "NEXTVAL".equalsIgnoreCase(last.getText())) {
                final int sequenceEnd = end - 2;
                final String[] respelled = respelled(chainParts(tokens, i, sequenceEnd), sourceHome, cloneHome,
                    from, to);
                if (respelled != null) {
                    out.append(text, cursor, tokens.get(i).getStartIndex());
                    out.append(spelled(respelled));
                    cursor = tokens.get(sequenceEnd).getStopIndex() + 1;
                }
            }
            i = end;
        }
        out.append(text, cursor, text.length());
        return out.toString();
    }

    /**
     * A materialized view's query with each reference to its base table re-spelled for the clone: a name that,
     * read as a table where the view lives, is {@code base}. A longer chain that starts with the table's name, a
     * column qualified with it, has that start re-spelled.
     */
    static String withTableRespelled(final String text, final String[] base, final String[] sourceHome,
                                     final String[] cloneHome, final String[] from, final String[] to) {
        final List<Token> tokens = tokens(text);
        final StringBuilder out = new StringBuilder(text.length());
        int cursor = 0;
        for (int i = 0; i < tokens.size(); i++) {
            if (!isName(tokens.get(i)) || precededByDot(tokens, i)) {
                continue;
            }
            final int end = chainEnd(tokens, i);
            final int parts = (end - i) / 2 + 1;
            for (int length = Math.min(3, parts); length >= 2; length--) {
                final int tableEnd = i + 2 * (length - 1);
                final String[] written = chainParts(tokens, i, tableEnd);
                if (Arrays.equals(completed(written, sourceHome), base)) {
                    final String[] respelled = respelled(written, sourceHome, cloneHome, from, to);
                    if (respelled != null) {
                        out.append(text, cursor, tokens.get(i).getStartIndex());
                        out.append(spelled(respelled));
                        cursor = tokens.get(tableEnd).getStopIndex() + 1;
                    }
                    break;
                }
            }
            i = end;
        }
        out.append(text, cursor, text.length());
        return out.toString();
    }

    private static List<Token> tokens(final String text) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(text));
        lexer.removeErrorListeners();
        final CommonTokenStream stream = new CommonTokenStream(lexer);
        stream.fill();
        return stream.getTokens();
    }

    private static boolean isName(final Token token) {
        return token.getType() != Token.EOF
            && (SqlTokens.isWord(token) || token.getType() == FrostlakeLexer.QUOTED_IDENTIFIER);
    }

    private static boolean precededByDot(final List<Token> tokens, final int i) {
        return i > 0 && tokens.get(i - 1).getType() == FrostlakeLexer.DOT;
    }

    /** The index of the last name of the dotted chain starting at {@code start}. */
    private static int chainEnd(final List<Token> tokens, final int start) {
        int end = start;
        while (end + 2 < tokens.size() && tokens.get(end + 1).getType() == FrostlakeLexer.DOT
                && isName(tokens.get(end + 2))) {
            end += 2;
        }
        return end;
    }

    /** The canonical names of the chain's tokens from {@code start} to {@code end}, dots skipped. */
    private static String[] chainParts(final List<Token> tokens, final int start, final int end) {
        final List<String> parts = new ArrayList<>();
        for (int i = start; i <= end; i += 2) {
            parts.add(SqlIdentifiers.canonicalTextParts(tokens.get(i).getText())[0]);
        }
        return parts.toArray(new String[0]);
    }

    /** Canonical parts as SQL text that reads back to them. */
    private static String spelled(final String[] parts) {
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            out.append(i > 0 ? "." : "").append(SqlIdentifiers.spellCanonicalEscaped(parts[i]));
        }
        return out.toString();
    }
}

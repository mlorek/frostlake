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
package dev.frostlake.metastore.model;

import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.metastore.Catalog;

/**
 * A column {@code DEFAULT} that draws from a sequence, {@code DEFAULT seq.NEXTVAL}. The column is bound to the
 * sequence itself rather than to the text that named it (live-verified): renaming or moving the sequence does not
 * break the column, and every surface names the sequence in full under its current name —
 * {@code DB.SCHEMA.SEQ.NEXTVAL}, a quoted name with its quotes. Once the sequence is dropped the column has
 * nothing to draw from, and a sequence created later under the same name is another sequence.
 *
 * <p>A clone of a schema or a database carries clones of its sequences, and a cloned table that drew from one of
 * them draws from that sequence's clone; a table cloned on its own, or one whose sequence lies outside what was
 * cloned, keeps drawing from the sequence it was bound to.
 */
public final class SequenceDefault extends DefaultValueExpression {

    /** What a describing surface prints for a sequence that is gone. */
    private static final String GONE = "[sequence cannot be found or accessed]";

    private final Sequence sequence;

    /**
     * @param sequence the sequence the column draws from
     */
    public SequenceDefault(final Sequence sequence) {
        super(null);
        this.sequence = sequence;
    }

    /**
     * @return the sequence the column is bound to, whether or not it still exists
     */
    public Sequence getSequence() {
        return sequence;
    }

    /**
     * The sequence, when it still stands where the catalog can reach it.
     *
     * @param catalog the catalog
     * @return the sequence, or null once it — or the schema or database holding it — is dropped
     */
    public Sequence sequenceIn(final Catalog catalog) {
        final Schema home = sequence.getHome();
        if (home == null || catalog == null) {
            return null;
        }
        try {
            return catalog.getDatabase(home.getDatabaseName()).getSchema(home.getName()) == home
                && home.hasSequenceExact(sequence.getName()) && home.getSequence(sequence.getName()) == sequence
                ? sequence : null;
        } catch (final RuntimeException dropped) {
            return null;
        }
    }

    /**
     * The refusal of a statement that needs the default once the sequence is gone.
     *
     * @param table  the table's full name
     * @param column the column's name
     * @return the refusal
     */
    public static RuntimeException lost(final String table, final String column) {
        return new RuntimeException("SQL compilation error:\nSequence used as a default value in table '" + table
            + "' column '" + column + "' was not found or could not be accessed.");
    }

    /** The sequence's NEXTVAL under its current full name, or the gone-sequence marker. */
    @Override
    public String getExpressionText() {
        final Schema home = sequence.getHome();
        if (home == null) {
            return GONE;
        }
        return SqlIdentifiers.spellCanonical(home.getDatabaseName()) + "." + SqlIdentifiers.spellCanonical(home.getName())
            + "." + SqlIdentifiers.spellCanonical(sequence.getName()) + ".NEXTVAL";
    }

    @Override
    public String toString() {
        return getExpressionText();
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof SequenceDefault && ((SequenceDefault) other).sequence == sequence;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(sequence);
    }
}

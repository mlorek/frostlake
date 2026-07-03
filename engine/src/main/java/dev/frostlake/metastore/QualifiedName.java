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

/**
 * An object's qualified name as its ordered identifier parts ({@code db.schema.name}).
 *
 * <p>Prefer building it from the parse tree — {@code QualifiedName.of(qualifiedNameParts(ctx))} — so the
 * structure comes straight from the grammar's identifier list. {@link #parse(String)} is the fallback
 * for a name that is only ever available as a flattened / runtime string (a stored policy name, a
 * {@code GET_DDL} argument value, a DML table name resolved at execution time): the caller has already
 * decided it is a plain dotted name with no parse tree to read, so splitting it on {@code '.'} is a
 * value-level operation, not a re-derivation of syntax. Consolidating every qualified-name split into
 * this one method keeps that boundary explicit.
 */
public final class QualifiedName {

    private final String[] parts;

    private QualifiedName(final String[] parts) {
        this.parts = parts;
    }

    /** Build from already-separated identifier parts (e.g. the parse tree's identifier list). */
    public static QualifiedName of(final String... parts) {
        return new QualifiedName(parts.clone());
    }

    /** Split a flattened dotted name into its parts — for a name available only as a string. */
    public static QualifiedName parse(final String dotted) {
        return new QualifiedName(dotted.split("\\."));
    }

    /** Number of parts (1 = name, 2 = schema.name, 3 = db.schema.name). */
    public int size() {
        return parts.length;
    }

    /** The i-th part (0-based). */
    public String part(final int i) {
        return parts[i];
    }

    /** The last part — the object's own (unqualified) name. */
    public String last() {
        return parts[parts.length - 1];
    }

    /** A defensive copy of the parts. */
    public String[] parts() {
        return parts.clone();
    }

    @Override
    public String toString() {
        return String.join(".", parts);
    }
}

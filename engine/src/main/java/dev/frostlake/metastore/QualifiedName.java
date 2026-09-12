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

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Split a flattened dotted name into its parts — for a name available only as a string. A dot inside
     * double quotes belongs to its part: {@code db.s."a.b"} is three parts, the last one {@code a.b}, which
     * is how {@link #join} spells a part that holds a dot. A quoted part without a dot is kept as written,
     * quotes and all, exactly as a plain split always kept it.
     */
    public static QualifiedName parse(final String dotted) {
        if (dotted.indexOf('"') < 0) {
            return new QualifiedName(dotted.split("\\.", -1));
        }
        final List<String> parts = new ArrayList<>();
        final StringBuilder part = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < dotted.length(); i++) {
            final char c = dotted.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                part.append(c);
            } else if (c == '.' && !quoted) {
                parts.add(unquoteDotted(part.toString()));
                part.setLength(0);
            } else {
                part.append(c);
            }
        }
        parts.add(unquoteDotted(part.toString()));
        return new QualifiedName(parts.toArray(new String[0]));
    }

    /** A quoted part that holds a dot, read back to its name; any other part as written. */
    private static String unquoteDotted(final String part) {
        if (part.length() > 1 && part.charAt(0) == '"' && part.charAt(part.length() - 1) == '"'
                && part.indexOf('.') >= 0) {
            return part.substring(1, part.length() - 1).replace("\"\"", "\"");
        }
        return part;
    }

    /**
     * Join parts into the dotted form {@link #parse} reads back to the same parts: a part holding a dot
     * is quoted (an inner quote doubled), so {@code "a.b"} is never mistaken for a schema {@code a} and a
     * table {@code b}.
     */
    public static String join(final String... parts) {
        final StringBuilder joined = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                joined.append('.');
            }
            final String part = String.valueOf(parts[i]);
            if (part.indexOf('.') >= 0) {
                joined.append('"').append(part.replace("\"", "\"\"")).append('"');
            } else {
                joined.append(part);
            }
        }
        return joined.toString();
    }

    /**
     * The row-storage key of a relation: its parts as written, joined as {@link #join} joins them. Every
     * site that creates, finds, drops or snapshots a relation's rows builds its key here, so they agree
     * for a name that holds a dot too.
     *
     * <p>The parts arrive CANONICAL - an unquoted name folded to upper case by the parser, a quoted one
     * verbatim - so the key is not folded again here: two relations whose names differ only in case are
     * two relations, and folding would give them one store between them.
     */
    public static String key(final String... parts) {
        return join(parts);
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
        return join(parts);
    }
}

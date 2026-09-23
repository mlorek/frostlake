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

package dev.frostlake.executor.commands;

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import java.util.Locale;
import org.antlr.v4.runtime.Token;

/**
 * An object kind a SHOW scope names by its word or words, {@code IN <KIND> [<name>]}, beside the database, schema
 * and table scopes: live reads the words as a scope kind rather than as the name of a class or a schema, and the
 * name after them as a word, a quoted or qualified name or an IDENTIFIER() reference. Frostlake models none of
 * these objects, so every such scope is refused, each listing with live's own answer.
 *
 * <p>VIEW and PIPE are kinds with or without a name after them; the other single words only with one — alone, the
 * word names a schema, as any other word does. A two-word kind is one as soon as its second word is read; FAILOVER
 * GROUP and REPLICATION GROUP need no name, APPLICATION PACKAGE and COMPUTE POOL do.
 */
enum ShowScopeKind {
    VIEW,
    PIPE,
    SERVICE,
    APPLICATION,
    CLASS,
    ORGANIZATION,
    CONNECTION,
    APPLICATION_PACKAGE,
    COMPUTE_POOL,
    FAILOVER_GROUP,
    REPLICATION_GROUP;

    /** Whether the word is a kind even with no name written after it. */
    boolean isKindAlone() {
        return this == VIEW || this == PIPE;
    }

    /** Whether the kind is spelled with two words, its second one read before the name. */
    boolean isTwoWord() {
        return this == APPLICATION_PACKAGE || this == COMPUTE_POOL || this == FAILOVER_GROUP
            || this == REPLICATION_GROUP;
    }

    /** Whether a name must follow the kind's words. */
    boolean needsName() {
        return this == APPLICATION_PACKAGE || this == COMPUTE_POOL;
    }

    /** The kind as live's refusals spell it, its words upper-case: {@code FAILOVER GROUP}. */
    String spelling() {
        return name().replace('_', ' ');
    }

    /**
     * The kind a scope names, or null: its name is one unquoted word spelling a kind, and it is a kind in that
     * position — with a name after it, or VIEW and PIPE alone.
     *
     * @param name     the scope's name as parsed, or null
     * @param instance the name written after it, or null
     * @return the kind, or null
     */
    static ShowScopeKind of(final FrostlakeParser.ObjectNameContext name,
                            final FrostlakeParser.ShowInstanceNameContext instance) {
        if (name == null) {
            return null;
        }
        final FrostlakeParser.QualifiedNameContext written = name.qualifiedName();
        if (written == null || !written.DOT().isEmpty() || !written.namePart().isEmpty()) {
            return null;
        }
        final Token word = written.getStart();
        if (word.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            return null;
        }
        final String upper = word.getText().toUpperCase(Locale.ROOT);
        if (instance != null && instance.showKindWord != null) {
            return twoWord(upper);
        }
        for (final ShowScopeKind kind : values()) {
            if (!kind.isTwoWord() && kind.name().equals(upper)) {
                return instance != null || kind.isKindAlone() ? kind : null;
            }
        }
        return null;
    }

    /** The two-word kind a scope's first word opens, its second word read. */
    private static ShowScopeKind twoWord(final String first) {
        if ("APPLICATION".equals(first)) {
            return APPLICATION_PACKAGE;
        }
        if ("COMPUTE".equals(first)) {
            return COMPUTE_POOL;
        }
        return "FAILOVER".equals(first) ? FAILOVER_GROUP : REPLICATION_GROUP;
    }
}

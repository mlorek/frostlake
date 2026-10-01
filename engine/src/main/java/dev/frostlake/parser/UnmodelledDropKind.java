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

package dev.frostlake.parser;

import org.antlr.v4.runtime.Token;

/**
 * The object kinds live drops that Frostlake has no statements for. Live's lexer keeps each word as a keyword, so
 * the statement reaches Frostlake as {@code DROP <class> <instance>} — a two-word kind with its second word after
 * the class — and it finds nothing: the name resolves the way any object's does, then {@code DROP ALERT a} answers
 * {@code Alert 'DB.PUBLIC.A' does not exist or not authorized.} and {@code DROP ALERT IF EXISTS a} the ordinary
 * already-dropped status (live-verified). A listing and a failover or replication group belong to the account, so
 * their names have one part, and both group kinds are named as a replication group.
 */
public enum UnmodelledDropKind {

    ALERT("Alert", true),
    DATASET("Dataset", true),
    EXTERNAL_TABLE("External table", true),
    FAILOVER_GROUP("Replication group", false),
    LISTING("Data exchange listing", false),
    MODEL("Model", true),
    REPLICATION_GROUP("Replication group", false);

    private final String noun;
    private final boolean schemaObject;

    UnmodelledDropKind(final String noun, final boolean schemaObject) {
        this.noun = noun;
        this.schemaObject = schemaObject;
    }

    /**
     * The kind as live names it in its missing-object sentence.
     *
     * @return the noun, sentence-cased
     */
    public String noun() {
        return noun;
    }

    /**
     * Whether an object of this kind lives in a schema, rather than in the account.
     *
     * @return true for a schema object
     */
    public boolean isSchemaObject() {
        return schemaObject;
    }

    /**
     * The kind an unquoted word names, or null.
     *
     * @param word the token
     * @return the kind, or null
     */
    public static UnmodelledDropKind of(final Token word) {
        if (word == null || word.getType() != FrostlakeLexer.IDENTIFIER) {
            return null;
        }
        for (final UnmodelledDropKind kind : values()) {
            if (kind.name().equalsIgnoreCase(word.getText())) {
                return kind;
            }
        }
        return null;
    }

    /**
     * The kind a class name names when it is one such word, or null.
     *
     * @param name the class name a DROP writes
     * @return the kind, or null
     */
    public static UnmodelledDropKind of(final FrostlakeParser.ClassNameContext name) {
        return name.IDENTIFIER() == null ? null : of(name.IDENTIFIER().getSymbol());
    }

    /**
     * The kind a class drop names — one word, or two when the grammar read a kind's second word — or null.
     *
     * @param drop the statement
     * @return the kind, or null
     */
    public static UnmodelledDropKind of(final FrostlakeParser.DropClassStatementContext drop) {
        if (drop.dropKindWord == null) {
            return of(drop.className());
        }
        final String first = drop.className().getStart().getText();
        if ("EXTERNAL".equalsIgnoreCase(first)) {
            return EXTERNAL_TABLE;
        }
        return "FAILOVER".equalsIgnoreCase(first) ? FAILOVER_GROUP : REPLICATION_GROUP;
    }
}

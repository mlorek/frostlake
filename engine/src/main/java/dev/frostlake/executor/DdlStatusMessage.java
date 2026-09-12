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

import dev.frostlake.parser.FrostlakeParser;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * The sentence a statement that produces no rows of its own answers with. Live returns it as a single
 * row of one VARCHAR column named {@code status} — but only through {@code executeQuery}: through
 * {@code Statement.execute} the driver reports no result set at all and an update count of 0, which is
 * why this had to be measured on a raw connection rather than through the test harness.
 *
 * <pre>
 *   CREATE TABLE t (a INT)          Table T successfully created.
 *   CREATE OR REPLACE VIEW v AS …   View V successfully created.
 *   CREATE TABLE "lower" (a INT)    Table lower successfully created.
 *   DROP TABLE t                    T successfully dropped.
 *   ALTER SESSION SET TIMEZONE = …  Statement executed successfully.
 * </pre>
 *
 * <p>★ ONLY CREATE NAMES THE KIND. A DROP says just the name, and everything else — USE, ALTER (session
 * or object), TRUNCATE, COMMENT, GRANT — is the one flat sentence. So the vocabulary is small, and its
 * shape is per STATEMENT rather than per object.
 *
 * <p>★ THE KIND WORDS ARE SENTENCE-CASE AND TWO OF THEM ARE SURPRISES: a STAGE is "Stage area", and a
 * PROCEDURE is "Function". A multi-word kind capitalises only its first word — "File format",
 * "Materialized view", "Masking policy". TEMPORARY and TRANSIENT do not appear at all: both are "Table".
 *
 * <p>★ THE NAME IS THE BARE, CANONICAL ONE — never qualified, upper-cased when written unquoted and kept
 * verbatim when quoted.
 */
public final class DdlStatusMessage {

    /** The sentence every statement without a kind-specific wording gets. */
    public static final String EXECUTED = "Statement executed successfully.";

    /** Words that may sit between CREATE/DROP and the object's kind, and are never part of it. */
    private static final Set<String> MODIFIERS = new HashSet<>();

    static {
        MODIFIERS.add("OR");
        MODIFIERS.add("REPLACE");
        MODIFIERS.add("TEMPORARY");
        MODIFIERS.add("TEMP");
        MODIFIERS.add("TRANSIENT");
        MODIFIERS.add("VOLATILE");
        MODIFIERS.add("SECURE");
        MODIFIERS.add("LOCAL");
        MODIFIERS.add("GLOBAL");
        MODIFIERS.add("IF");
        MODIFIERS.add("NOT");
        MODIFIERS.add("EXISTS");
        MODIFIERS.add("RECURSIVE");
    }

    private DdlStatusMessage() {
    }

    /**
     * The status sentence for one statement.
     *
     * @param statement the parsed statement
     * @return live's sentence for it
     */
    public static String forStatement(final FrostlakeParser.StatementContext statement) {
        return forStatement(statement, null);
    }

    /**
     * The status sentence for one statement, given the conditional branch its handler reported.
     *
     * <p>★ THE TWO CONDITIONAL SENTENCES DEPEND ON WHAT THE CATALOG HELD BEFORE THE STATEMENT RAN
     * (live-verified across table, view, stage, schema and sequence — the wording is kind-blind):
     *
     * <pre>
     *   CREATE … IF NOT EXISTS, object present    T2 already exists, statement succeeded.
     *   DROP … IF EXISTS, object absent           Drop statement executed successfully
     *                                             (NOSUCHTABLE already dropped).
     * </pre>
     *
     * <p>The name is the written identifier canonicalised — the DROP form upper-cases even a name
     * that never existed, and a quoted one keeps its case — so it comes off the tree, not off a
     * catalog lookup. CREATE OR REPLACE over an existing object never says "already exists": it is
     * the ordinary created sentence.
     *
     * @param statement the parsed statement
     * @param branch the conditional branch the handler took, or null when it took neither
     * @return live's sentence for it
     */
    public static String forStatement(final FrostlakeParser.StatementContext statement,
                                      final ConditionalDdlBranch branch) {
        if (statement != null && branch != null) {
            final String name = objectName(statement);
            if (name != null) {
                if (branch == ConditionalDdlBranch.CREATE_SKIPPED) {
                    return name + " already exists, statement succeeded.";
                }
                return "Drop statement executed successfully (" + name + " already dropped).";
            }
        }
        if (statement == null) {
            return EXECUTED;
        }
        final int leading = statement.getStart().getType();
        if (leading != FrostlakeParser.CREATE && leading != FrostlakeParser.DROP) {
            return EXECUTED;
        }
        final String name = objectName(statement);
        if (name == null) {
            return EXECUTED;
        }
        if (leading == FrostlakeParser.DROP) {
            return name + " successfully dropped.";
        }
        final String kind = kindPhrase(statement);
        return kind == null ? EXECUTED : kind + " " + name + " successfully created.";
    }

    /**
     * The object's own name — the last part of the first qualified name the statement writes, canonical.
     *
     * @param statement the parsed statement
     * @return the name, or null when the statement names nothing
     */
    private static String objectName(final FrostlakeParser.StatementContext statement) {
        // The object's qualified name is the first one in the tree — its kind words come before it, and any
        // other name the statement writes (a CLONE source, a CTAS body, a view's query) after it — and the
        // object is its LAST part, read from the parse tree so a quoted part keeps its dots: live answers
        // `CREATE TABLE db.s."a.b"` with "Table a.b successfully created." (live-verified).
        final FrostlakeParser.QualifiedNameContext qualified = firstQualifiedName(statement);
        if (qualified != null) {
            final String[] parts = ParseTreeText.qualifiedNameParts(qualified);
            return parts[parts.length - 1];
        }
        final FrostlakeParser.IdentifierContext id = firstIdentifier(statement);
        return id == null ? null : SqlIdentifiers.canonical(id);
    }

    private static FrostlakeParser.QualifiedNameContext firstQualifiedName(final ParseTree node) {
        if (node instanceof FrostlakeParser.QualifiedNameContext) {
            return (FrostlakeParser.QualifiedNameContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.QualifiedNameContext found = firstQualifiedName(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static FrostlakeParser.IdentifierContext firstIdentifier(final ParseTree node) {
        if (node instanceof FrostlakeParser.IdentifierContext) {
            return (FrostlakeParser.IdentifierContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.IdentifierContext found = firstIdentifier(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /**
     * The kind words between CREATE and the object's name, in live's sentence case. A stage is a "Stage
     * area" and a procedure is a "Function" — both measured, neither derivable.
     *
     * @param statement the parsed statement
     * @return the kind phrase, or null when the statement names no kind
     */
    private static String kindPhrase(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.IdentifierContext name = firstIdentifier(statement);
        final StringBuilder phrase = new StringBuilder();
        collectKindWords(statement, name, phrase);
        final String words = phrase.toString().trim();
        if (words.isEmpty()) {
            return null;
        }
        if ("STAGE".equals(words)) {
            return "Stage area";
        }
        if ("PROCEDURE".equals(words)) {
            return "Function";
        }
        return words.charAt(0) + words.substring(1).toLowerCase(Locale.ROOT);
    }

    /** Walk the statement's own tokens up to the name, keeping the ones that are not modifiers. */
    private static boolean collectKindWords(final ParseTree node, final ParseTree stopAt,
                                            final StringBuilder phrase) {
        if (node == stopAt) {
            return true;
        }
        if (node instanceof TerminalNode) {
            final String text = node.getText().toUpperCase(Locale.ROOT);
            if (!"CREATE".equals(text) && !MODIFIERS.contains(text) && text.length() > 0
                    && Character.isLetter(text.charAt(0))) {
                if (phrase.length() > 0) {
                    phrase.append(' ');
                }
                phrase.append(text);
            }
            return false;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            if (collectKindWords(node.getChild(i), stopAt, phrase)) {
                return true;
            }
        }
        return false;
    }
}

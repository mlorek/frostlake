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
        // CREATE OR ALTER is a CREATE: the sentence names the object's kind, never the ALTER half.
        MODIFIERS.add("ALTER");
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
        // An event table and an Iceberg table are created as a "Table".
        MODIFIERS.add("EVENT");
        MODIFIERS.add("ICEBERG");
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
        return forStatement(statement, null, null);
    }

    /**
     * The IDENTIFIER() reference a statement names its object with, or null when the statement writes the name
     * itself. The sentence then carries the reference's value, which only the executor can read — a variable's
     * among them — so the caller resolves it: live answers {@code CREATE TABLE IDENTIFIER($tn) (a INT)} with
     * "Table T6 successfully created." when {@code $tn} holds 't6'. The word IDENTIFIER written as the object's
     * own name is such a node too, and resolves to that word.
     *
     * @param statement the parsed statement
     * @return the reference, or null
     */
    public static FrostlakeParser.ObjectNameContext identifierReference(final FrostlakeParser.StatementContext statement) {
        final FrostlakeParser.ObjectNameContext name = firstObjectName(statement);
        return name != null && name.qualifiedName() == null ? name : null;
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
     * @param referencedName the object's name read from its {@link #identifierReference}, or null when the
     *                       statement writes the name
     * @return live's sentence for it
     */
    public static String forStatement(final FrostlakeParser.StatementContext statement,
                                      final ConditionalDdlBranch branch, final String referencedName) {
        if (branch == ConditionalDdlBranch.ALTERED_IN_PLACE) {
            // A CREATE OR ALTER that found its table altered it, and answers as an ALTER does (live-verified).
            return EXECUTED;
        }
        if (statement != null && branch != null) {
            final String name = referencedName != null ? referencedName : objectName(statement);
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
        if (leading == FrostlakeParser.UNDROP) {
            return undropped(statement, referencedName);
        }
        if (leading != FrostlakeParser.CREATE && leading != FrostlakeParser.DROP) {
            return EXECUTED;
        }
        final String name = referencedName != null ? referencedName : objectName(statement);
        if (name == null) {
            return EXECUTED;
        }
        if (leading == FrostlakeParser.DROP) {
            return name + " successfully dropped.";
        }
        final String kind = kindPhrase(statement);
        return kind == null ? EXECUTED : kind + " " + name + " successfully created.";
    }

    /** An UNDROP's sentence: {@code <Kind> <NAME> successfully restored.}, the kind being the word after UNDROP. */
    private static String undropped(final FrostlakeParser.StatementContext statement, final String referencedName) {
        final String name = referencedName != null ? referencedName : objectName(statement);
        final ParseTree kindWord = statement.getChildCount() > 0 ? undropKind(statement) : null;
        if (name == null || kindWord == null) {
            return EXECUTED;
        }
        final String word = kindWord.getText().toUpperCase(Locale.ROOT);
        return word.charAt(0) + word.substring(1).toLowerCase(Locale.ROOT) + " " + name + " successfully restored.";
    }

    /** The token after UNDROP, found in the statement's first terminal chain. */
    private static ParseTree undropKind(final ParseTree node) {
        if (node instanceof FrostlakeParser.UndropStatementContext) {
            return node.getChildCount() > 1 ? node.getChild(1) : null;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final ParseTree found = undropKind(node.getChild(i));
            if (found != null) {
                return found;
            }
        }
        return null;
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

    private static FrostlakeParser.ObjectNameContext firstObjectName(final ParseTree node) {
        if (node instanceof FrostlakeParser.ObjectNameContext) {
            return (FrostlakeParser.ObjectNameContext) node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            final FrostlakeParser.ObjectNameContext found = firstObjectName(node.getChild(i));
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
        final ParseTree reference = identifierReference(statement);
        final ParseTree name = reference != null ? reference : firstIdentifier(statement);
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
        if ("IMAGE REPOSITORY".equals(words)) {
            return "Image Repository";
        }
        if ("ARTIFACT REPOSITORY".equals(words)) {
            return "Artifact Repository";
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

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

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.executor.SqlIdentifiers;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.parser.FrostlakeParser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * The value a task property is given, read off its parse tree: a quoted string (single-quoted or between
 * {@code $$}), a number, a boolean, a name, or a parenthesised list of constants.
 *
 * <p>A parenthesised list is one value to the account, spelled {@code TOK_CONSTANT_LIST}: a string parameter
 * holds that word, and a parameter of another type refuses it by that word.
 */
final class TaskPropertyValue {

    /** How the account spells a parenthesised list given as a value. */
    static final String CONSTANT_LIST = "TOK_CONSTANT_LIST";

    private final String text;
    private final String written;
    private final boolean quoted;
    private final boolean numeric;
    private final boolean bool;
    private final boolean list;
    /** A name's parts, each as written (a quoted part with its quotes), or null for any other value. */
    private final List<String> nameParts;
    /** A name's parts folded the way identifiers fold, or null for any other value. */
    private final List<String> canonicalParts;
    /** The content of a list's string items in order, for the one refusal that echoes them. */
    private final List<String> listItems;

    private TaskPropertyValue(final String text, final String written, final boolean quoted, final boolean numeric,
                              final boolean bool, final boolean list, final List<String> nameParts,
                              final List<String> canonicalParts, final List<String> listItems) {
        this.text = text;
        this.written = written;
        this.quoted = quoted;
        this.numeric = numeric;
        this.bool = bool;
        this.list = list;
        this.nameParts = nameParts;
        this.canonicalParts = canonicalParts;
        this.listItems = listItems;
    }

    /**
     * The value of a property as the parse tree holds it.
     *
     * @param ctx the value's parse tree
     * @return the value
     */
    static TaskPropertyValue of(final FrostlakeParser.TaskPropertyValueContext ctx) {
        if (ctx.LPAREN() != null) {
            final List<String> items = new ArrayList<>();
            for (final FrostlakeParser.TaskListItemContext item : ctx.taskListItem()) {
                items.add(item.STRING_LITERAL() != null ? SqlStringLiterals.decode(item.STRING_LITERAL().getText())
                    : item.getText());
            }
            return new TaskPropertyValue(CONSTANT_LIST, CONSTANT_LIST, false, false, false, true, null, null, items);
        }
        if (ctx.STRING_LITERAL() != null) {
            return quoted(ctx.STRING_LITERAL().getText());
        }
        if (ctx.DOLLAR_QUOTED_STRING() != null) {
            return dollarQuoted(ctx.DOLLAR_QUOTED_STRING().getText());
        }
        if (ctx.booleanValue() != null) {
            return bool(ctx.booleanValue().getText());
        }
        if (ctx.qualifiedName() != null) {
            return name(ctx.qualifiedName());
        }
        return number(ctx.getText());
    }

    /** A single-quoted string, from its token text. */
    static TaskPropertyValue quoted(final String token) {
        return new TaskPropertyValue(SqlStringLiterals.decode(token), token, true, false, false, false, null, null,
            null);
    }

    /** A string between {@code $$}, from its token text. */
    static TaskPropertyValue dollarQuoted(final String token) {
        final String content = token.length() >= 4 ? token.substring(2, token.length() - 2) : "";
        return new TaskPropertyValue(content, token, true, false, false, false, null, null, null);
    }

    /** A number, from its text. */
    static TaskPropertyValue number(final String written) {
        return new TaskPropertyValue(written, written, false, true, false, false, null, null, null);
    }

    /** TRUE or FALSE, from its text. */
    static TaskPropertyValue bool(final String written) {
        return new TaskPropertyValue(written.toLowerCase(Locale.ROOT), written, false, false, true, false, null,
            null, null);
    }

    /**
     * A name already resolved to the object it names, such as a warehouse read from a session variable.
     *
     * @param name the name, canonical
     * @return the value
     */
    static TaskPropertyValue resolvedName(final String name) {
        final List<String> parts = new ArrayList<>();
        parts.add(name);
        return new TaskPropertyValue(name, name, false, false, false, false, parts, parts, null);
    }

    /** A name written as one identifier. */
    static TaskPropertyValue name(final FrostlakeParser.IdentifierContext identifier) {
        final List<String> parts = new ArrayList<>();
        parts.add(identifier.getText());
        final List<String> canonical = new ArrayList<>();
        canonical.add(SqlIdentifiers.canonical(identifier));
        return new TaskPropertyValue(canonical.get(0), identifier.getText(), false, false, false, false, parts,
            canonical, null);
    }

    /** A name, qualified or not. */
    static TaskPropertyValue name(final FrostlakeParser.QualifiedNameContext name) {
        final List<String> parts = new ArrayList<>();
        parts.add(name.nameStartPart().getText());
        for (final FrostlakeParser.NamePartContext part : name.namePart()) {
            parts.add(part.getText());
        }
        final List<String> canonical = new ArrayList<>(Arrays.asList(ParseTreeText.qualifiedNameParts(name)));
        // One identifier folds as a name does; a dotted name is kept as written.
        final String text = parts.size() == 1 ? canonical.get(0) : name.getText();
        return new TaskPropertyValue(text, name.getText(), false, false, false, false, parts, canonical, null);
    }

    /**
     * The value as a parameter holds it: a string's content, a boolean in lower case, a lone name folded and a
     * dotted one as written, a list as {@value #CONSTANT_LIST}.
     */
    String text() {
        return text;
    }

    /**
     * The value as a level parameter holds it: a name keeps the case it is written in, and a quoted name loses
     * its quotes.
     */
    String unfoldedText() {
        if (nameParts == null || nameParts.size() != 1) {
            return text;
        }
        final String part = nameParts.get(0);
        return part.startsWith("\"") ? canonicalParts.get(0) : part;
    }

    /** The value exactly as the statement writes it, quotes included. */
    String written() {
        return written;
    }

    /** Whether the value is a quoted string. */
    boolean isQuoted() {
        return quoted;
    }

    /** Whether the value is a number. */
    boolean isNumeric() {
        return numeric;
    }

    /** Whether the value is TRUE or FALSE. */
    boolean isBoolean() {
        return bool;
    }

    /** Whether the value is a parenthesised list. */
    boolean isList() {
        return list;
    }

    /** Whether the value is a name. */
    boolean isName() {
        return nameParts != null;
    }

    /** A name's parts as written, each quoted part with its quotes; null for any other value. */
    List<String> nameParts() {
        return nameParts;
    }

    /** A name's parts folded as identifiers fold; null for any other value. */
    List<String> canonicalParts() {
        return canonicalParts;
    }

    /** A list's items, a string item as its content; null for any other value. */
    List<String> listItems() {
        return listItems;
    }
}

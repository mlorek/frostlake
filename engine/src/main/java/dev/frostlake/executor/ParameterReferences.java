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

import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Where a routine's SQL body reads its parameters, read off the body's parse tree: a parameter is a value, so it
 * stands only where a column could be read by its bare name. A function, a table, a common table expression, an
 * alias, a named argument's name, a path's field, a lambda's own parameter, a date part, a type and a name written
 * with its qualifier are none of them a parameter, however they are spelled; a column of the parameter's name
 * gives way to it wherever it is written bare (live-verified):
 *
 * <pre>
 *   f(upper VARCHAR)   AS 'upper(upper)'                                   the call stays, its argument binds
 *   f(people NUMBER)   AS 'SELECT COUNT(*) FROM people'                    the table
 *   f(v NUMBER)        AS 'SELECT w FROM (SELECT 10 AS v, 20 AS w)'        the alias
 *   f(input VARCHAR)   AS 'SELECT … FLATTEN(input =&gt; PARSE_JSON(input))'   the name stays, the value binds
 *   f(id NUMBER)       AS 'SELECT MAX(id) FROM people'                     the parameter, not the column
 *   f(p NUMBER)        AS 'SELECT COUNT(*) FROM people p WHERE p.id &gt; p'   the alias, the column, the parameter
 *   f(g NUMBER)        AS 'SELECT id FROM people GROUP BY g'               the position the value names
 * </pre>
 *
 * <p>Every parameter is bound in ONE pass over the body as written, so the text one parameter is bound to is
 * never read again for another: a parameter named {@code number} leaves the {@code ::NUMBER(10,2)} of another's
 * value alone. A body the grammar does not read falls back to {@link SqlIdentifierSubstitution}, which binds by
 * the body's tokens.
 */
public final class ParameterReferences {

    /** What a body the grammar does not read is remembered as. */
    private static final int[][] UNREADABLE = new int[0][];

    /** The references of bodies already read, by the parameters' names and the body. */
    private static final BoundedParseCache<int[][]> READ = new BoundedParseCache<int[][]>(512, 8192);

    private ParameterReferences() {
    }

    /**
     * {@code body} with every reference to a parameter replaced by what the parameter is bound to.
     *
     * @param body         the routine's body: a query or an expression
     * @param names        the parameters' canonical names
     * @param replacements what each is bound to, index-aligned
     * @return the bound body
     */
    public static String bound(final String body, final List<String> names, final List<String> replacements) {
        return bound(body, names, replacements, null);
    }

    /**
     * {@link #bound(String, List, List)}, recording each replacement made as {@code {start, end, length, which}}:
     * the replaced name's first and one-past-last character in {@code body}, the length of the text put there,
     * and the parameter's index.
     *
     * @param body         the routine's body: a query or an expression
     * @param names        the parameters' canonical names
     * @param replacements what each is bound to, index-aligned
     * @param replaced     receives one entry per replacement made, in source order; may be null
     * @return the bound body
     */
    public static String bound(final String body, final List<String> names, final List<String> replacements,
                               final List<int[]> replaced) {
        if (names.isEmpty()) {
            return body;
        }
        final int[][] references = in(body, names);
        if (references == UNREADABLE) {
            return boundByTokens(body, names, replacements, replaced);
        }
        final StringBuilder result = new StringBuilder(body.length() + 16 * references.length);
        int cursor = 0;
        for (final int[] reference : references) {
            final String replacement = replacements.get(reference[2]);
            result.append(body, cursor, reference[0]).append(replacement);
            cursor = reference[1];
            if (replaced != null) {
                replaced.add(new int[] {reference[0], reference[1], replacement.length(), reference[2]});
            }
        }
        return result.append(body, cursor, body.length()).toString();
    }

    /** A body the grammar does not read, bound by its tokens. */
    private static String boundByTokens(final String body, final List<String> names,
                                        final List<String> replacements, final List<int[]> replaced) {
        if (replaced == null) {
            return SqlIdentifierSubstitution.substituteParameters(body, names, replacements);
        }
        final Map<String, String> byName = new HashMap<String, String>();
        final Map<String, Integer> indexes = new HashMap<String, Integer>();
        for (int i = 0; i < names.size(); i++) {
            byName.put(names.get(i).toUpperCase(Locale.ROOT), replacements.get(i));
            indexes.put(names.get(i).toUpperCase(Locale.ROOT), i);
        }
        final List<int[]> made = new ArrayList<int[]>();
        final String text = SqlIdentifierSubstitution.substituteAll(body, byName, made);
        for (final int[] one : made) {
            final Integer which = indexes.get(body.substring(one[0], one[1]).toUpperCase(Locale.ROOT));
            replaced.add(new int[] {one[0], one[1], one[2], which == null ? 0 : which});
        }
        return text;
    }

    /**
     * The references of {@code body} to the parameters {@code names}, each {@code {start, end, which}} in source
     * order, or {@link #UNREADABLE} for a body the grammar reads neither as a query nor as an expression.
     */
    private static int[][] in(final String body, final List<String> names) {
        final String key = String.join("\u0001", names) + "\u0000" + body;
        final int[][] known = READ.get(key);
        if (known != null) {
            return known;
        }
        final ParseTree tree = parsed(body);
        final int[][] references;
        if (tree == null) {
            references = UNREADABLE;
        } else {
            // A parameter is named by its name in upper case however it was declared: f("x" DATE) is read by x
            // and by "X", and "x" is no parameter's (live-verified).
            final List<String> folded = new ArrayList<String>();
            for (final String name : names) {
                folded.add(name.toUpperCase(Locale.ROOT));
            }
            final List<int[]> found = new ArrayList<int[]>();
            collect(tree, folded, found);
            references = found.toArray(new int[found.size()][]);
        }
        READ.put(key, references);
        return references;
    }

    /**
     * Whether an expression body reads one of the parameters inside a subquery of its own, where the account binds the
     * parameter ahead of the subquery's columns: {@code (SELECT MAX(val) FROM val)} reads the parameter VAL, not the
     * table's column (live-verified). A body the grammar does not read reads none.
     *
     * @param body  the body: an expression
     * @param names the parameters' canonical names
     * @return whether a subquery of the body reads a parameter
     */
    public static boolean readInSubquery(final String body, final List<String> names) {
        final ParseTree tree = parsed(body);
        if (tree == null || names.isEmpty()) {
            return false;
        }
        final List<String> folded = new ArrayList<String>();
        for (final String name : names) {
            folded.add(name.toUpperCase(Locale.ROOT));
        }
        return readUnder(tree, folded, false);
    }

    private static boolean readUnder(final ParseTree node, final List<String> names, final boolean inSubquery) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final List<int[]> found = new ArrayList<int[]>();
            collect(node, names, found);
            return inSubquery && !found.isEmpty();
        }
        final boolean below = inSubquery || node instanceof FrostlakeParser.SelectStatementContext;
        for (int i = 0; i < node.getChildCount(); i++) {
            if (readUnder(node.getChild(i), names, below)) {
                return true;
            }
        }
        return false;
    }

    /** The body's parse tree: a query's where it is one, an expression's otherwise, null where it is neither. */
    private static ParseTree parsed(final String body) {
        try {
            final FrostlakeParser parser = parserOf(body);
            final ParserRuleContext script = parser.sqlScript();
            if (parser.getCurrentToken().getType() == Token.EOF) {
                return script;
            }
        } catch (final RuntimeException notAStatement) {
            // an expression, then
        }
        try {
            final FrostlakeParser parser = parserOf(body);
            final ParserRuleContext expression = parser.booleanExpr();
            if (parser.getCurrentToken().getType() == Token.EOF) {
                return expression;
            }
        } catch (final RuntimeException notAnExpression) {
            // neither
        }
        return null;
    }

    private static FrostlakeParser parserOf(final String body) {
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(body));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        return parser;
    }

    /** Adds every bare name under {@code node} that is read as a value and is one of {@code names}. */
    private static void collect(final ParseTree node, final List<String> names, final List<int[]> found) {
        if (node instanceof FrostlakeParser.QualifiedNameExprContext) {
            final FrostlakeParser.QualifiedNameContext name =
                ((FrostlakeParser.QualifiedNameExprContext) node).qualifiedName();
            if (name.getChildCount() == 1) {
                final String[] parts = SqlIdentifiers.canonicalTextParts(name.getText());
                final int which = parts.length == 1 ? names.indexOf(parts[0]) : -1;
                if (which >= 0) {
                    found.add(new int[] {name.getStart().getStartIndex(), name.getStop().getStopIndex() + 1, which});
                }
            }
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            collect(node.getChild(i), names, found);
        }
    }
}

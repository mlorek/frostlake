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
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.executor.SqlStringLiterals;
import dev.frostlake.metastore.model.Tag;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.security.SecurityManager;
import java.util.List;
import java.util.Locale;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * The value a tag is set to, in {@code SET TAG t = <value>} and in a creation-time {@code TAG (t = <value>)} list.
 * Live reads more than a string there and judges what it read while the statement compiles: every value of the
 * statement in the order written, before the object, the tag or its ALLOWED_VALUES is looked up (live-verified).
 *
 * <pre>
 *   'v', $$v$$                    the string
 *   X'00'                         the literal as written, X'00'
 *   DATE, date, TAG, USER         a word live's lexer keeps as a keyword: the word as written
 *   "xY"                          the quoted name's own text, xY
 *   a.b, "a".b, a.b.c.d           the path as written
 *   $sv                           a text session variable's value; one no SET defined is refused at its position
 *   1, - 1, 1.5e3, TRUE, true     invalid value [1] ([-1], [1.5e3], [TRUE], [true]) for parameter 'tagValue'
 *   (…), ()                       invalid value [TOK_CONSTANT_LIST] for parameter 'tagValue'
 *   IDENTIFIER('x')               invalid value [TOK_OBJECT_LITERAL] for parameter 'tagValue'
 *   NULL, x, CURRENT_DATE, :b     Unsupported value data type for tag TG. Only string is supported.
 * </pre>
 *
 * <p>A numeric session variable is an unsupported data type too, and the sentence names the tag by its last part
 * as it resolves, unquoted ({@code "Tq"} is {@code Tq}). A word is a keyword when Frostlake's own lexer keeps it
 * as one, except the context functions, which live reads as calls; PUBLIC and VALUE are keywords to live's lexer
 * only.
 */
public final class TagValues {

    /** Words live reads as calls of a context function wherever a value stands, never as a keyword. */
    private static final String[] CONTEXT_FUNCTIONS = {
        "CURRENT_DATE", "CURRENT_ROLE", "CURRENT_TIME", "CURRENT_TIMESTAMP", "CURRENT_USER", "LOCALTIME",
        "LOCALTIMESTAMP", "SYSDATE"
    };

    /** Words live's lexer keeps as keywords where Frostlake's reads a plain name. */
    private static final String[] LIVE_ONLY_KEYWORDS = {"PUBLIC", "VALUE"};

    private TagValues() {
    }

    /**
     * Judge every tag value a statement writes, in the order written, and refuse the first that sets no tag.
     *
     * @param node          the statement, or any part of it
     * @param queryExecutor the executor whose session variables a {@code $name} value reads
     */
    public static void requireAll(final ParseTree node, final QueryExecutor queryExecutor) {
        if (node instanceof FrostlakeParser.TagAssignContext) {
            final FrostlakeParser.TagAssignContext assign = (FrostlakeParser.TagAssignContext) node;
            text(assign.qualifiedName(), assign.tagValue(), queryExecutor);
            return;
        }
        if (node instanceof FrostlakeParser.TagAssignmentContext) {
            final FrostlakeParser.TagAssignmentContext assignment = (FrostlakeParser.TagAssignmentContext) node;
            text(assignment.qualifiedName(), assignment.tagValue(), queryExecutor);
            return;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            requireAll(node.getChild(i), queryExecutor);
        }
    }

    /**
     * The text a tag is set to, or the refusal of a value that sets none.
     *
     * @param tag           the tag's name as written, which the unsupported-type sentence names
     * @param value         the value as written
     * @param queryExecutor the executor whose session variables a {@code $name} value reads
     * @return the text
     */
    public static String text(final FrostlakeParser.QualifiedNameContext tag,
                              final FrostlakeParser.TagValueContext value, final QueryExecutor queryExecutor) {
        if (value.STRING_LITERAL() != null) {
            return SqlStringLiterals.decode(value.STRING_LITERAL().getText());
        }
        if (value.DOLLAR_QUOTED_STRING() != null) {
            final String quoted = value.DOLLAR_QUOTED_STRING().getText();
            return quoted.substring(2, quoted.length() - 2);
        }
        if (value.HEX_LITERAL() != null) {
            return value.HEX_LITERAL().getText();
        }
        if (value.INTEGER_LITERAL() != null || value.FLOAT_LITERAL() != null || value.TRUE() != null
                || value.FALSE() != null) {
            throw invalid(value.getText());
        }
        if (value.KW_IDENTIFIER_REF() != null) {
            throw invalid("TOK_OBJECT_LITERAL");
        }
        if (value.LPAREN() != null) {
            throw invalid("TOK_CONSTANT_LIST");
        }
        if (value.SESSION_VAR_REF() != null) {
            final Object variable = sessionVariable(value.SESSION_VAR_REF().getSymbol(), queryExecutor);
            if (variable instanceof String) {
                return (String) variable;
            }
            throw unsupported(tag);
        }
        if (value.identifier().isEmpty() || value.COLON() != null) {
            throw unsupported(tag);
        }
        if (!value.DOT().isEmpty()) {
            return ParseTreeText.getOriginalText(value);
        }
        final Token word = value.identifier(0).getStart();
        if (word.getType() == FrostlakeLexer.QUOTED_IDENTIFIER) {
            return ParseTreeText.getIdentifier(value.identifier(0));
        }
        if (isKeyword(word)) {
            return word.getText();
        }
        throw unsupported(tag);
    }

    /** Whether live reads a word as one of its keywords, which a tag takes as its text. */
    private static boolean isKeyword(final Token word) {
        final String upper = word.getText().toUpperCase(Locale.ROOT);
        if (word.getType() == FrostlakeLexer.IDENTIFIER) {
            return contains(LIVE_ONLY_KEYWORDS, upper);
        }
        return !contains(CONTEXT_FUNCTIONS, upper);
    }

    private static boolean contains(final String[] words, final String upper) {
        for (final String word : words) {
            if (word.equals(upper)) {
                return true;
            }
        }
        return false;
    }

    /** The value of the session variable a value reads; one no SET defined is refused at its own position. */
    private static Object sessionVariable(final Token token, final QueryExecutor queryExecutor) {
        final String name = token.getText().substring(1).toUpperCase(Locale.ROOT);
        final SecurityManager security = queryExecutor.getSecurityManager();
        final boolean session = security != null && security.getSessionContext() != null;
        final boolean defined = session ? security.getSessionContext().isSessionVariable(name)
            : queryExecutor.getSessionVariables().containsKey(name);
        if (!defined) {
            throw new RuntimeException(SqlCompilationError.at(token.getLine(), token.getCharPositionInLine(),
                "Session variable '$" + name + "' does not exist"));
        }
        return session ? security.getSessionContext().getSessionVariable(name)
            : queryExecutor.getSessionVariables().get(name);
    }

    /**
     * Refuse a value outside a tag's ALLOWED_VALUES, in live's words — which carry no compilation-error prefix.
     *
     * @param tag   the tag
     * @param value the text the tag would be set to
     */
    public static void requireAllowed(final Tag tag, final String value) {
        final List<String> allowed = tag.getAllowedValues();
        if (!allowed.isEmpty() && !allowed.contains(value)) {
            throw new RuntimeException("Value '" + value + "' is not allowed by the specified allowed_values for tag '"
                + tag.getName() + "'.");
        }
    }

    private static RuntimeException invalid(final String rendered) {
        return new RuntimeException(SqlCompilationError.invalidValueForParameter(rendered, "tagValue"));
    }

    /** The unsupported-type refusal, naming the tag by its last part as it resolves. */
    private static RuntimeException unsupported(final FrostlakeParser.QualifiedNameContext tag) {
        final String[] parts = ParseTreeText.qualifiedNameParts(tag);
        return new RuntimeException(SqlCompilationError.inline("Unsupported value data type for tag "
            + parts[parts.length - 1] + ". Only string is supported."));
    }
}

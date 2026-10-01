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

import dev.frostlake.executor.IntegerLiteralRange;
import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

/**
 * Every DECLARED TYPE in a scripting block, and every integer literal too wide to read, judged BEFORE the block
 * runs — because that is when live judges them — in the order they are written, ahead of every other refusal
 * the block compile makes: an unnamed bind, a name declared twice and a name that resolves to nothing all wait
 * for this pass (live-verified).
 *
 * <pre>
 *   BEGIN LET v VARCHAR(0) := 'x'; RETURN v; END
 *       SQL compilation error: error line 2 at position 16
 *       Invalid character length: 0. Must be between 1 and 134,217,728.
 * </pre>
 *
 * <p>★ THE WRAPPER IS THE TELL. Frostlake reached the same refusal while EXECUTING the DECLARE, so the
 * scripting layer saw an exception escaping a statement and reported it as one —
 * "Uncaught exception of type 'STATEMENT_ERROR' … : &lt;the right sentence&gt;". Live never gets there: the
 * block does not compile, so there is no statement to have failed.
 *
 * <p>★ THE WHOLE BLOCK IS CHECKED, REACHABLE OR NOT — measured: a bad width inside {@code IF (FALSE)}
 * is still refused, and an EXCEPTION handler cannot catch it because nothing has run yet. That is what
 * makes this a walk over the parse tree rather than a check on the statement about to execute.
 *
 * <p>★ IT IS THE TYPES ONLY. An unknown COLUMN in an unreachable branch is ACCEPTED live
 * ({@code IF (FALSE) THEN LET n INT := (SELECT nosuchcol FROM t)}), so the compile pass this mirrors
 * does not resolve names inside a subquery — the two halves are not the same rule and are measured
 * apart.
 *
 * <p>★ A LITERAL IS READ WHERE THE LITERAL READER READS IT: a value in an expression, a LIMIT, OFFSET, FETCH or
 * TOP count, a SAMPLE size or seed, a VECTOR's dimension and an exception's code. A type's parameters are the
 * type's to judge, in the type's own sentence ({@code NUMBER(<39 digits>, 0)} is an invalid precision), and a
 * property's value is the property's. An exception code written with its sign is read with it, by a reader
 * that places nothing: {@code EXCEPTION (-<39 digits>, 'm')} is refused at line 0, position 0, echoing the
 * sign (all live-verified).
 */
final class ScriptTypeCompiler {

    private ScriptTypeCompiler() {
    }

    /**
     * Judge every declared type and every integer literal under {@code node}, in the order written. A type and
     * its parameters are SIBLINGS in the grammar ({@code dataTypeName typeParameters?}), which is why the pair is
     * read off the parse tree rather than by re-reading the block's text.
     *
     * @param node the block, or any node within it
     */
    static void validateDeclaredTypes(final ParseTree node) {
        if (node == null) {
            return;
        }
        if (node instanceof TerminalNode) {
            rejectWideLiteral((TerminalNode) node);
            return;
        }
        if (node instanceof FrostlakeParser.DataTypeNameContext) {
            // A VECTOR's dimension is read as a literal before the type itself is judged.
            for (int i = 0; i < node.getChildCount(); i++) {
                if (node.getChild(i) instanceof TerminalNode) {
                    rejectWideLiteral((TerminalNode) node.getChild(i));
                }
            }
            DataTypeParser.parse((FrostlakeParser.DataTypeNameContext) node, parametersOf(node));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            validateDeclaredTypes(node.getChild(i));
        }
    }

    /** Refuse an integer literal too wide to read, where the literal reader reads it (see the class comment). */
    private static void rejectWideLiteral(final TerminalNode node) {
        final Token token = node.getSymbol();
        if (token.getType() != FrostlakeLexer.INTEGER_LITERAL || !IntegerLiteralRange.isOutOfRange(token.getText())) {
            return;
        }
        final ParseTree holder = node.getParent();
        if (holder instanceof FrostlakeParser.DeclarationItemContext
                && ((FrostlakeParser.DeclarationItemContext) holder).MINUS() != null) {
            throw new RuntimeException(SqlCompilationError.PREFIX + " Error line 0 at position 0\n"
                + IntegerLiteralRange.sentence("-" + token.getText()));
        }
        if (holder instanceof FrostlakeParser.LiteralContext
                || holder instanceof FrostlakeParser.LimitClauseContext
                || holder instanceof FrostlakeParser.FetchClauseContext
                || holder instanceof FrostlakeParser.TopClauseContext
                || holder instanceof FrostlakeParser.SampleClauseContext
                || holder instanceof FrostlakeParser.SampleSeedContext
                || holder instanceof FrostlakeParser.DataTypeNameContext
                || holder instanceof FrostlakeParser.DeclarationItemContext) {
            IntegerLiteralRange.reject(token);
        }
    }

    /** The {@code typeParameters} written after this type, or null when it carries none. */
    private static FrostlakeParser.TypeParametersContext parametersOf(final ParseTree typeName) {
        if (!(typeName instanceof ParserRuleContext)) {
            return null;
        }
        final ParserRuleContext parent = ((ParserRuleContext) typeName).getParent();
        if (parent == null) {
            return null;
        }
        for (int i = 0; i < parent.getChildCount() - 1; i++) {
            if (parent.getChild(i) == typeName
                    && parent.getChild(i + 1) instanceof FrostlakeParser.TypeParametersContext) {
                return (FrostlakeParser.TypeParametersContext) parent.getChild(i + 1);
            }
        }
        return null;
    }
}

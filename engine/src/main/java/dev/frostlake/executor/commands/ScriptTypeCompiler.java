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

import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Every DECLARED TYPE in a scripting block, judged BEFORE the block runs — because that is when live
 * judges it.
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
 */
final class ScriptTypeCompiler {

    private ScriptTypeCompiler() {
    }

    /**
     * Judge every declared type under {@code node}. A type and its parameters are SIBLINGS in the
     * grammar ({@code dataTypeName typeParameters?}), which is why the pair is read off the parse tree
     * rather than by re-reading the block's text.
     *
     * @param node the block, or any node within it
     */
    static void validateDeclaredTypes(final ParseTree node) {
        if (node == null) {
            return;
        }
        if (node instanceof FrostlakeParser.DataTypeNameContext) {
            DataTypeParser.parse((FrostlakeParser.DataTypeNameContext) node, parametersOf(node));
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            validateDeclaredTypes(node.getChild(i));
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

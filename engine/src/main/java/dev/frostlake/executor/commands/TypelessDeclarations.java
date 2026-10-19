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

import dev.frostlake.executor.SqlCompilationError;
import dev.frostlake.parser.FrostlakeParser;

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A variable a DECLARE section declares by its name alone, {@code DECLARE q;}. The account reads the declaration
 * and refuses it while the block compiles — {@code variable 'Q' must have either a type or an initializer to infer
 * a type from} — at the name, upper-cased unless it is written in quotes. The refusal comes with the declared
 * types, in the order the block is written, after the block's syntax and before any of its names is looked up; a
 * procedure's and a function's body are refused as they are created (all live-verified). A LET of a name alone is
 * read where the text is parsed (see {@link dev.frostlake.parser.TypelessLetReading}).
 */
final class TypelessDeclarations {

    private TypelessDeclarations() {
    }

    /** Refuse {@code node} when it declares a variable by its name alone. */
    static void requireTypeOrValue(final ParseTree node) {
        if (node instanceof FrostlakeParser.UntypedDeclarationItemContext) {
            final FrostlakeParser.UntypedDeclarationItemContext item =
                (FrostlakeParser.UntypedDeclarationItemContext) node;
            if (item.booleanExpr() == null) {
                throw refusal(item.identifier(), item.identifier().getStart());
            }
        }
    }

    /** Refuse the first variable under {@code node}, in the order written, that is declared by its name alone. */
    static void requireTypeOrValueThroughout(final ParseTree node) {
        requireTypeOrValue(node);
        for (int i = 0; i < node.getChildCount(); i++) {
            requireTypeOrValueThroughout(node.getChild(i));
        }
    }

    private static RuntimeException refusal(final ParseTree name, final Token at) {
        return new RuntimeException(SqlCompilationError.at(at.getLine(), at.getCharPositionInLine(),
            " variable '" + ScriptingNameValidator.canonical(name.getText())
                + "' must have either a type or an initializer to infer a type from"));
    }
}

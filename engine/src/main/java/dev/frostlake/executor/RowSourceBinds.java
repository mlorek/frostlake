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

import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;

import java.util.Locale;

/**
 * A RESULTSET or a cursor that a statement the block runs binds: a bind nothing sets, which refuses the statement
 * when it compiles, whether or not a row reaches it (live-verified):
 *
 * <pre>
 *   SELECT COUNT(*) INTO :n FROM t1 WHERE a = :rs      error line 1 at position 34
 *                                                      Bind variable :rs not set.
 *   INSERT INTO t1 SELECT * FROM TABLE(:rs)            error line 0 at position -1
 *                                                      Bind variable for object "rs" AS rs not set
 * </pre>
 *
 * <p>A value is refused at its colon, a SELECT … INTO's counted in its text without the INTO clause (see
 * {@link SelectIntoText}); TABLE('s operand is refused nowhere, named as an object of the name as written and by
 * the name itself. The block's own expressions refuse such a bind while the block compiles instead (see
 * ScriptingNameValidator).
 */
public final class RowSourceBinds {

    private RowSourceBinds() {
    }

    /**
     * Refuse a statement the running block hands over for the first RESULTSET or cursor it binds; a block or a task's
     * statement it holds binds names of its own.
     *
     * @param tree  the statement's parse
     * @param block the running block
     */
    public static void requireNone(final ParseTree tree, final ProceduralExecutor block) {
        if (tree instanceof FrostlakeParser.BeginEndBlockContext || tree instanceof FrostlakeParser.TaskBodyContext) {
            return;
        }
        if (tree instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) tree;
            final String written = bind.variableName() == null ? null : bind.variableName().getText();
            if (written != null && block.holdsRowSource(written)) {
                if (bind.getParent() instanceof FrostlakeParser.TableSourceContext) {
                    throw asTableOperand(written);
                }
                final Token colon = bind.getStart();
                throw asValue(colon.getLine(), colon.getCharPositionInLine(), written);
            }
            return;
        }
        for (int i = 0; i < tree.getChildCount(); i++) {
            requireNone(tree.getChild(i), block);
        }
    }

    /**
     * The refusal of such a bind written as a value.
     *
     * @param line    the colon's line in the text that compiles
     * @param column  the colon's column there
     * @param written the name as written
     * @return the refusal
     */
    public static RuntimeException asValue(final int line, final int column, final String written) {
        return new RuntimeException(SqlCompilationError.at(line, column, "Bind variable :" + written + " not set."));
    }

    /**
     * The refusal of such a bind written as TABLE('s operand.
     *
     * @param written the name as written
     * @return the refusal
     */
    public static RuntimeException asTableOperand(final String written) {
        final String object = written.startsWith("\"") || written.equals(written.toUpperCase(Locale.ROOT))
            ? written : "\"" + written + "\"";
        return new RuntimeException(SqlCompilationError.at(0, -1,
            "Bind variable for object " + object + " AS " + written + " not set"));
    }
}

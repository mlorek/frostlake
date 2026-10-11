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

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * A statement of a block cannot bind an infinity: {@code INSERT INTO t VALUES (:x)}, {@code SELECT … INTO … WHERE id
 * < :x}, an UPDATE, a DELETE, {@code CALL p(:x)} and a RESULTSET's query over a FLOAT variable holding one are
 * refused while the statement compiles, {@code Invalid bind value (inf) for type (REAL).}, whatever the statement
 * would do with the value and whether or not a row would read it. A NaN binds, and so does an infinity in a block's
 * own expression — {@code RETURN (SELECT :x)} — and in what EXECUTE IMMEDIATE is USING (all live-verified).
 */
public final class InfiniteBindRefusal {

    private InfiniteBindRefusal() {
    }

    /**
     * The refusal of a bound value, or null for a value a statement binds.
     *
     * @param value the value of a variable a statement binds
     * @return the refusal for an infinity, or null
     */
    static RuntimeException of(final Object value) {
        if (!(value instanceof Double || value instanceof Float) || !Double.isInfinite(((Number) value).doubleValue())) {
            return null;
        }
        return new RuntimeException(SqlCompilationError.of("Invalid bind value ("
            + (((Number) value).doubleValue() > 0 ? "inf" : "-inf") + ") for type (REAL)."));
    }

    /**
     * Refuse a statement that binds a variable holding an infinity.
     *
     * @param statement the statement as parsed in its block
     * @param scope     the block's variables
     */
    public static void requireFiniteBinds(final ParseTree statement, final ProceduralExecutor scope) {
        if (statement instanceof FrostlakeParser.BeginEndBlockContext) {
            // A block inside the statement binds when its own statements run.
            return;
        }
        if (statement instanceof FrostlakeParser.BindVarExprContext) {
            final FrostlakeParser.BindVarExprContext bind = (FrostlakeParser.BindVarExprContext) statement;
            if (bind.identifier() != null && scope.hasVariable(bind.identifier().getText())) {
                final RuntimeException refusal = of(scope.getVariable(bind.identifier().getText()));
                if (refusal != null) {
                    throw refusal;
                }
            }
            return;
        }
        for (int i = 0; i < statement.getChildCount(); i++) {
            requireFiniteBinds(statement.getChild(i), scope);
        }
    }
}

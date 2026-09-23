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

package dev.frostlake.executor.expressions;

import dev.frostlake.executor.DeclarationTypes;
import dev.frostlake.executor.ExpressionEvaluator;
import dev.frostlake.executor.QueryExecutor;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;
import dev.frostlake.types.UuidType;
import dev.frostlake.types.WidthlessStringType;

import java.util.HashMap;
import java.util.Map;

/**
 * SYSTEM$TYPEOF inside a block's own expression — a RETURN, a LET or an assignment's value, a condition. There
 * every name the block declares is a parameter of its declared type, whatever it holds: a number is tagged by
 * its declared width and a text has no width of its own, and a bare name reads the variable as its bound form
 * does. Live-verified:
 *
 * <pre>
 *   LET x := 1;                      SYSTEM$TYPEOF(:x), SYSTEM$TYPEOF(x)   NUMBER(38,0)[SB16]
 *   FOR i IN 1 TO 2                  SYSTEM$TYPEOF(:i)                      NUMBER(9,0)[SB4]
 *   DECLARE x NUMBER(10,4) := 1.7777 SYSTEM$TYPEOF(:x)                      NUMBER(10,4)[SB8]
 *   DECLARE s VARCHAR(10)            SYSTEM$TYPEOF(:s)                      VARCHAR[LOB]
 *   after an INSERT                  SYSTEM$TYPEOF(SQLROWCOUNT)             VARCHAR[LOB]
 *   a cursor record                  SYSTEM$TYPEOF(rec.a)                   NUMBER(38,0)[SB16]
 * </pre>
 *
 * <p>A statement inside the block binds the value instead: {@code (SELECT SYSTEM$TYPEOF(:x))} over a
 * NUMBER(10,4) holding 1.7777 is {@code [SB2]}, and a scalar subquery in the expression is such a statement.
 */
public final class ScriptTypeOf {

    private ScriptTypeOf() {
    }

    /**
     * The description of {@code typed} over the block's names.
     *
     * @param typed         the argument, never evaluated
     * @param nameTypes     the declared type of every name in scope, keyed as the resolver folds them
     * @param queryExecutor the executor whose functions and catalog type the argument
     * @return what SYSTEM$TYPEOF answers
     */
    public static String describe(final Expression typed, final Map<String, DataType> nameTypes,
                                  final QueryExecutor queryExecutor) {
        final Map<String, DataType> parameters = new HashMap<String, DataType>();
        for (final Map.Entry<String, DataType> name : nameTypes.entrySet()) {
            final DataType type = name.getValue();
            parameters.put(name.getKey(), type instanceof StringType && !(type instanceof UuidType)
                ? WidthlessStringType.WIDTHLESS : type);
        }
        final ExpressionEvaluator typer = DeclarationTypes.scriptTyper(parameters, queryExecutor);
        typer.setBindsAsParameters(true);
        return typer.describeTypeOf(typed);
    }
}

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

package dev.frostlake.http.rest.resource;

import dev.frostlake.executor.ParseTreeText;
import dev.frostlake.parser.FrostlakeLexer;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.parser.SqlCharStream;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.misc.ParseCancellationException;

import java.util.ArrayList;
import java.util.List;

/**
 * The defaults a routine's arguments declare, read from the routine's DDL: the one place an account tells them
 * in SQL. The statement is parsed and each parameter's default taken from its place in the tree.
 */
final class RoutineArgumentDefaults {

    private RoutineArgumentDefaults() {
    }

    /**
     * The default of each argument, in order.
     *
     * @param ddl the routine's CREATE statement
     * @return each argument's default as the statement spells it; null for an argument that declares none or
     *         declares NULL, and no entries at all for a statement that is no routine's
     */
    static List<String> of(final String ddl) {
        final List<String> defaults = new ArrayList<>();
        if (ddl == null) {
            return defaults;
        }
        final FrostlakeLexer lexer = new FrostlakeLexer(SqlCharStream.of(ddl));
        lexer.removeErrorListeners();
        final FrostlakeParser parser = new FrostlakeParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.setErrorHandler(new BailErrorStrategy());
        final FrostlakeParser.CreateStatementContext created;
        try {
            created = parser.createStatement();
        } catch (final ParseCancellationException notARoutine) {
            return defaults;
        }
        if (created.parameterList() == null) {
            return defaults;
        }
        for (final FrostlakeParser.ParameterDefContext parameter : created.parameterList().parameterDef()) {
            final String written = parameter.expression() == null ? null
                : ParseTreeText.getOriginalText(parameter.expression()).trim();
            defaults.add(written == null || "NULL".equalsIgnoreCase(written) ? null : written);
        }
        return defaults;
    }
}

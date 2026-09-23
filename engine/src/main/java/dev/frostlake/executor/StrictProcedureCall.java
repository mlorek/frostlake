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

import dev.frostlake.metastore.model.NullHandling;
import dev.frostlake.metastore.model.Procedure;
import dev.frostlake.metastore.model.UdfLanguage;

import java.util.List;

/**
 * A CALL of a procedure that declares RETURNS NULL ON NULL INPUT (or STRICT) with a NULL argument, a defaulted one
 * included, runs no body (live-verified): a SQL procedure's CALL is refused with {@link #SQL_REFUSAL}, whatever it
 * returns, a Python one that returns a table is refused naming its handler (see {@link #tableRefusal}), and a
 * scalar JavaScript or Python one answers NULL. A Java or Scala procedure runs as if the clause were not there. A
 * VARIANT holding a JSON null is a value, not a NULL.
 */
final class StrictProcedureCall {

    /** The refusal a SQL procedure's CALL gets, which carries no compilation-error prefix. */
    static final String SQL_REFUSAL = "NULL result in a non-nullable column";

    private StrictProcedureCall() {
    }

    /** Whether this CALL runs no body: the procedure is null-strict in a language that honours it, and gets a NULL. */
    static boolean skipsBody(final Procedure procedure, final List<Object> arguments) {
        final UdfLanguage language = procedure.getUdfLanguage();
        if (NullHandling.fromString(procedure.getNullHandling()) != NullHandling.RETURNS_NULL_ON_NULL_INPUT
                || language == UdfLanguage.JAVA || language == UdfLanguage.SCALA
                || language == UdfLanguage.JAVASCRIPT && procedure.returnsTable()) {
            return false;
        }
        for (final Object argument : arguments) {
            if (argument == null) {
                return true;
            }
        }
        return false;
    }

    /** The refusal a table-returning Python procedure's skipped CALL gets. */
    static String tableRefusal(final Procedure procedure) {
        return "NULL result in a non-nullable column of type TEXT in function " + procedure.getName()
            + " with handler " + procedure.getHandler();
    }
}

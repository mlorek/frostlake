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

import dev.frostlake.executor.expressions.SourcePosition;
import dev.frostlake.metastore.model.Integration;
import dev.frostlake.parser.FrostlakeParser;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.types.ArrayType;
import dev.frostlake.types.DataType;
import dev.frostlake.types.ObjectType;
import dev.frostlake.types.SqlTypeNames;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code CALL SYSTEM$SEND_EMAIL(integration, addresses, subject, body[, mime type])}, the account's procedure that
 * sends a message through an email notification integration. It is a procedure written in JavaScript, so its refusals
 * carry that body's frame (all live-verified over an integration that is not there):
 *
 * <pre>
 *   ('nosuch_int', 'a@b.c', 's', 'b')     Execution error in store procedure SYSTEM$SEND_EMAIL: / SQL compilation
 *                                         error: / Integration 'NOSUCH_INT' does not exist or not authorized. /
 *                                         At Snowflake.execute, line 3 position 24
 *   a NULL among them                     … Unsupported type for binding argument 2undefined …
 *   ('x', 'y', 'z'), ()                   error line 0 at position -1 / Invalid argument types for function
 *                                         'SYSTEM$SEND_EMAIL': (VARCHAR(1), VARCHAR(1), VARCHAR(1))
 *   six arguments                         error line 0 at position -1 / too many arguments for function
 *                                         [SYSTEM$SEND_EMAIL(…)] expected 5, got 6
 * </pre>
 *
 * Its arguments are texts: a number or a BOOLEAN is passed as its text, an ARRAY or an OBJECT is no argument of it.
 * The integration is named as an identifier is, upper-cased unless written between double quotes. As a SELECT it is no
 * function. An integration that exists is refused all the same: no message leaves this engine.
 */
final class SendEmailProcedure {

    /** The procedure's name. */
    static final String NAME = "SYSTEM$SEND_EMAIL";

    private static final int FEWEST = 4;
    private static final int MOST = 5;
    private static final String LEAD = "Execution error in store procedure " + NAME + ":\n";
    private static final String FRAME = "\nAt Snowflake.execute, line 3 position 24";

    private SendEmailProcedure() {
    }

    /**
     * Whether a CALL names the procedure with positional arguments only, which is the form this answers.
     *
     * @param ctx the CALL
     * @return whether it is such a CALL
     */
    static boolean calls(final FrostlakeParser.CallStatementContext ctx) {
        if (ctx.systemFunctionName() == null
                || !NAME.equals(ctx.systemFunctionName().getText().toUpperCase(Locale.ROOT))) {
            return false;
        }
        if (ctx.callArguments() != null) {
            for (final FrostlakeParser.CallArgumentContext argument : ctx.callArguments().callArgument()) {
                if (CallArgumentTypes.isNamed(argument)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Run the CALL: its arguments are judged, then bound as texts, and the integration looked up.
     *
     * @param executor the executor that reads the arguments
     * @param ctx      the CALL
     * @return never; every path is a refusal here
     */
    static ResultSet call(final QueryExecutor executor, final FrostlakeParser.CallStatementContext ctx) {
        final List<String> written = new ArrayList<>();
        if (ctx.callArguments() != null) {
            for (final FrostlakeParser.CallArgumentContext argument : ctx.callArguments().callArgument()) {
                written.add(ParseTreeText.getOriginalText(argument));
            }
        }
        if (written.size() > MOST) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "too many arguments for function [" + NAME + "("
                + String.join(", ", written) + ")] expected " + MOST + ", got " + written.size()));
        }
        final List<DataType> types = new ArrayList<>();
        final List<Object> values = new ArrayList<>();
        if (!written.isEmpty()) {
            final List<ResultSet> read = executor.executeFromUnrecorded("SELECT " + String.join(", ", written),
                new SourcePosition(1, 0));
            final ResultSet row = read.isEmpty() ? null : read.get(0);
            for (int i = 0; i < written.size(); i++) {
                types.add(row == null || row.getColumns().size() <= i ? null : row.getColumns().get(i).getDataType());
                values.add(row == null || row.getRowCount() == 0 ? null : row.getRows().get(0).getValue(i));
            }
        }
        boolean fits = written.size() >= FEWEST;
        final List<String> spelled = new ArrayList<>();
        for (final DataType type : types) {
            fits = fits && !(type instanceof ArrayType) && !(type instanceof ObjectType);
            spelled.add(type == null ? "NULL" : SqlTypeNames.refusalSpelling(type));
        }
        if (!fits) {
            throw new RuntimeException(SqlCompilationError.at(0, -1, "Invalid argument types for function '" + NAME
                + "': (" + String.join(", ", spelled) + ")"));
        }
        for (final Object value : values) {
            if (value == null) {
                throw new RuntimeException(LEAD + "Unsupported type for binding argument 2undefined" + FRAME);
            }
        }
        final String integration = identifier(textOf(values.get(0)));
        final Integration found = executor.getCatalog().getIntegrations().find(integration);
        if (found == null) {
            throw new RuntimeException(LEAD + "SQL compilation error:\nIntegration '" + integration
                + "' does not exist or not authorized." + FRAME);
        }
        throw new RuntimeException(LEAD + "Integration '" + integration + "' sends no email from this engine." + FRAME);
    }

    /** A bound value's text, as the body's binding hands it on. */
    private static String textOf(final Object value) {
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        return String.valueOf(value);
    }

    /** A text read as an identifier: as written between double quotes, upper-cased otherwise. */
    private static String identifier(final String text) {
        if (text.length() >= 2 && text.startsWith("\"") && text.endsWith("\"")) {
            return text.substring(1, text.length() - 1).replace("\"\"", "\"");
        }
        return text.toUpperCase(Locale.ROOT);
    }
}

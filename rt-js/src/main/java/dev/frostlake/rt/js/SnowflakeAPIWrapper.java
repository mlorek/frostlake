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

package dev.frostlake.rt.js;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.jdbc.JdbcMarshaling;
import dev.frostlake.storage.ResultSet;
import org.graalvm.polyglot.HostAccess;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What a JavaScript procedure's {@code snowflake} object runs its statements through. The handler never holds
 * this object: the invoker does, and the {@code snowflake} methods it gives the handler call only the members
 * exported here (see {@code JavaScriptHandler}). A call answers a host object for the script-side API object to
 * wrap, or the led text of a failure for it to throw (see {@link JavaScriptApiFailures}).
 */
public class SnowflakeAPIWrapper {

    private static final String EMPTY_ARGUMENT = "empty argument passed";
    /** The sentence a {@code binds} that is no array is refused with (live-verified). */
    private static final String INVALID_BINDS = "Invalid binds argument";

    private final DatabaseEngine engine;
    private final String body;
    private final JavaScriptApiFailures failures = new JavaScriptApiFailures();

    /**
     * @param engine the engine the procedure's statements run on
     * @param body   the handler's body as written, which the places of its API calls are counted in
     */
    public SnowflakeAPIWrapper(final DatabaseEngine engine, final String body) {
        this.engine = engine;
        this.body = body;
    }

    /**
     * {@code snowflake.execute}: runs a statement with its {@code binds}, which a {@code binds} that is no array is
     * refused for as createStatement refuses it (live-verified).
     *
     * @param options the handler's options object
     * @return the rows, a {@link JavaScriptResultSet}, or a led failure
     */
    @HostAccess.Export
    public Object execute(final Object options) {
        final String misused = misusedTextType(options);
        if (misused != null) {
            return failures.misuse(invalidType(misused));
        }
        final String sqlText = sqlTextOf(options);
        if (sqlText == null) {
            return failures.misuse(EMPTY_ARGUMENT);
        }
        final Object bindsObj = ((Map<?, ?>) options).get("binds");
        if (bindsObj != null && !(bindsObj instanceof List)) {
            return failures.misuse(INVALID_BINDS);
        }
        final List<?> binds = (List<?>) bindsObj;
        try {
            final ResultSet rs = engine.executeQuery(binds == null || binds.isEmpty() ? sqlText
                : JdbcMarshaling.substitutePlaceholders(sqlText, new ArrayList<Object>(binds)));
            return new JavaScriptResultSet(rs, failures, engine.getExecutor().getResultCache().getLastQueryId());
        } catch (final RuntimeException failed) {
            return failures.statement(failed);
        }
    }

    /**
     * {@code snowflake.createStatement}: the two-step form, whose statement's {@code execute()} runs the SQL with
     * its {@code binds}.
     *
     * @param options the handler's options object
     * @return the statement, a {@link SnowflakeStatement}, or a led failure
     */
    @HostAccess.Export
    public Object createStatement(final Object options) {
        final String misused = misusedTextType(options);
        if (misused != null) {
            return failures.misuse(invalidType(misused));
        }
        final String sqlText = sqlTextOf(options);
        if (sqlText == null) {
            return failures.misuse(EMPTY_ARGUMENT);
        }
        final List<Object> binds = new ArrayList<>();
        final Object bindsObj = ((Map<?, ?>) options).get("binds");
        if (bindsObj instanceof List) {
            binds.addAll((List<?>) bindsObj);
        } else if (bindsObj != null) {
            return failures.misuse(INVALID_BINDS);
        }
        return new SnowflakeStatement(engine, sqlText, binds, failures);
    }

    /**
     * Where a call of the API stands in the handler's body, as an account's error names it (live-verified):
     * {@code Snowflake.execute, line 1 position 11} — the line of the method's name, the first being 1, and the
     * name's column on it, the first being 0.
     *
     * @param method the API's method, {@code Snowflake.execute}
     * @param line   the line of the evaluated source the call's expression starts on
     * @param column the column it starts at there, the first being 1
     * @return the place
     */
    @HostAccess.Export
    public String place(final String method, final int line, final int column) {
        final String[] lines = body.split("\n", -1);
        final int bodyLine = Math.max(1, Math.min(line - JavaScriptHandler.BODY_FIRST_LINE + 1, lines.length));
        int start = 0;
        for (int i = 0; i < bodyLine - 1; i++) {
            start += lines[i].length() + 1;
        }
        start = Math.min(start + Math.max(column - 1, 0), body.length());
        final String name = method.substring(method.indexOf('.') + 1);
        // The call's expression starts at its receiver; the method's name is written after it.
        final int named = body.indexOf(name, start);
        final int at = named >= 0 ? named : start;
        int onLine = 1;
        int lineStart = 0;
        for (int i = 0; i < lines.length - 1 && lineStart + lines[i].length() < at; i++) {
            lineStart += lines[i].length() + 1;
            onLine++;
        }
        return method + ", line " + onLine + " position " + (at - lineStart);
    }

    /** What the statement that failed with a sentence failed with, the last such; null when none did. */
    Throwable causeOf(final String message) {
        return failures.causeOf(message);
    }

    /**
     * The JavaScript type of a {@code sqlText} that is no text, which the account names in its refusal
     * ({@code Argument with invalid type number passed}, for a boolean, an object and an array alike;
     * live-verified), or null for a text or none.
     */
    private static String misusedTextType(final Object options) {
        if (!(options instanceof Map)) {
            return null;
        }
        final Object text = ((Map<?, ?>) options).get("sqlText");
        if (text == null || text instanceof String) {
            return null;
        }
        if (text instanceof Number) {
            return "number";
        }
        return text instanceof Boolean ? "boolean" : "object";
    }

    private static String invalidType(final String type) {
        return "Argument with invalid type " + type + " passed";
    }

    private static String sqlTextOf(final Object options) {
        if (!(options instanceof Map)) {
            return null;
        }
        final Map<?, ?> optMap = (Map<?, ?>) options;
        Object sqlObj = optMap.get("sqlText");
        if (sqlObj == null) {
            sqlObj = optMap.get("sql");
        }
        return sqlObj == null ? null : String.valueOf(sqlObj);
    }
}

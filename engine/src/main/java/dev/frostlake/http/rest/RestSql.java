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

package dev.frostlake.http.rest;

import dev.frostlake.ConcurrentDatabaseEngine;
import dev.frostlake.ExecutionResult;
import dev.frostlake.http.SessionContext;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The SQL a REST request runs, in the session serving it. Every endpoint is a translation into the statements
 * the engine already answers, so the REST surface inherits the SQL path's behaviour and its refusals; this class
 * runs those statements and turns a refusal into the status the API answers for it.
 *
 * <p>The engine carries no Snowflake error numbers, so the status and the {@code code} come from the refusal's
 * family, recognised by its sentence: an object that does not exist is {@code 404} with {@code 002003}, one that
 * already exists {@code 409} with {@code 002002}, a syntax error {@code 400} with {@code 001003}, missing
 * privileges {@code 403} with {@code 003001}. Any other refusal of the statement is a {@code 400} without a code.
 * The message is the statement's own, as the account relays it: see {@link #message}.
 */
public final class RestSql {

    /** The account's code for an object that does not exist or is not authorized. */
    public static final String CODE_DOES_NOT_EXIST = "002003";
    /** The account's code for an object that already exists. */
    public static final String CODE_ALREADY_EXISTS = "002002";
    /** The account's code for a syntax error. */
    public static final String CODE_SYNTAX_ERROR = "001003";
    /** The account's code for insufficient privileges. */
    public static final String CODE_INSUFFICIENT_PRIVILEGES = "003001";
    /** The account's code for an UNDROP of an object that was never dropped or was purged. */
    public static final String CODE_NOT_RESTORABLE = "090025";
    /** The account's code for a resource constraint that GENERATION sets instead. */
    public static final String CODE_RESOURCE_CONSTRAINT = "000682";
    /** The account's code for a warehouse action its state does not allow. */
    public static final String CODE_INVALID_STATE = "090064";
    /** The account's code for a property value of the wrong kind ({@code invalid type of property}). */
    public static final String CODE_INVALID_PROPERTY_TYPE = "001421";
    /** The account's code for a property value outside its domain ({@code invalid value … for property}). */
    public static final String CODE_INVALID_PROPERTY_VALUE = "001422";
    /** The account's code for a task RETRY LAST whose last graph run had no failures. */
    public static final String CODE_RETRY_NO_FAILURES = "091456";
    /** The account's code for a task RETRY LAST with no graph run to retry. */
    public static final String CODE_RETRY_NO_RUN = "091457";
    /** The account's code for EXECUTE TASK on a task that is not the root of its graph. */
    public static final String CODE_NON_ROOT_TASK = "091435";
    /** The account's code for a tag conflict rule the tag does not take. */
    public static final String CODE_ON_CONFLICT = "391892";
    /** The prefix a compilation error's message starts with. */
    public static final String COMPILATION_ERROR = "SQL compilation error:";

    private final ConcurrentDatabaseEngine engine;
    private final SessionContext session;

    /**
     * @param engine the engine
     * @param session the session the request's statements run in
     */
    public RestSql(final ConcurrentDatabaseEngine engine, final SessionContext session) {
        this.engine = engine;
        this.session = session;
    }

    /** The session the statements run in. */
    public SessionContext session() {
        return session;
    }

    /** The engine the statements run on. */
    public ConcurrentDatabaseEngine engine() {
        return engine;
    }

    /**
     * Runs one statement.
     *
     * @return its result sets
     * @throws RestException the refusal, mapped to its status, when the statement fails
     */
    public List<ResultSet> run(final String sql) {
        final ExecutionResult result;
        try {
            result = engine.execute(sql, session);
        } catch (final RestException refusal) {
            throw refusal;
        } catch (final RuntimeException failure) {
            throw refusal(failure.getMessage() != null ? failure.getMessage() : failure.toString());
        }
        if (!result.isSuccess()) {
            throw refusal(result.getErrorMessage());
        }
        return result.getResultSets() == null ? Collections.<ResultSet>emptyList() : result.getResultSets();
    }

    /** Runs one statement and answers its first result set, or an empty one when it produced none. */
    public ResultSet query(final String sql) {
        final List<ResultSet> sets = run(sql);
        if (sets.isEmpty()) {
            return new ResultSet(new ArrayList<>(), new ArrayList<Row>());
        }
        return sets.get(0);
    }

    /**
     * Runs one statement and answers the sentence it reports — the first cell of its first row, which is the
     * {@code status} a DDL statement answers — or {@link RestResponse#DEFAULT_STATUS} when it reports none.
     */
    public String status(final String sql) {
        final ResultSet set = query(sql);
        if (set.getRows().isEmpty() || set.getColumns().isEmpty()) {
            return RestResponse.DEFAULT_STATUS;
        }
        final Object first = set.getRows().get(0).getValue(0);
        return first == null ? RestResponse.DEFAULT_STATUS : first.toString();
    }

    /** Runs one statement and answers {@code 200} with the sentence it reports. */
    public RestResponse action(final String sql) {
        return RestResponse.success(status(sql));
    }

    /**
     * Rows of a SHOW listing whose {@code name} column is exactly the given name. A listing filtered with
     * {@code LIKE} matches case-insensitively and treats {@code _} and {@code %} as wildcards, so the exact
     * match is made here, on the resolved name.
     *
     * @param showSql the SHOW statement, usually carrying a {@code LIKE} of the name
     * @param name the name the rows must carry
     */
    public List<RestRow> showNamed(final String showSql, final RestIdentifier name) {
        final ResultSet set = query(showSql);
        final List<RestRow> matches = new ArrayList<>();
        for (final Row row : set.getRows()) {
            final RestRow view = new RestRow(set, row);
            if (name.name().equals(view.string("name"))) {
                matches.add(view);
            }
        }
        return matches;
    }

    /** Every row of a SHOW listing. */
    public List<RestRow> show(final String showSql) {
        final ResultSet set = query(showSql);
        final List<RestRow> rows = new ArrayList<>();
        for (final Row row : set.getRows()) {
            rows.add(new RestRow(set, row));
        }
        return rows;
    }

    /**
     * A SQL string literal holding the value: quotes doubled and backslashes escaped, as the engine's string
     * literal reads them back.
     */
    public static String literal(final String value) {
        return "'" + value.replace("\\", "\\\\").replace("'", "''") + "'";
    }

    /**
     * The API's answer for a statement the engine refused, chosen from the refusal's family.
     *
     * @param message the engine's error message
     */
    public static RestException refusal(final String message) {
        if (message == null || message.isEmpty()) {
            return new RestException(500, "Internal error", null);
        }
        final String relayed = message(message);
        if (message.contains("does not exist or not authorized") || message.contains("does not exist")) {
            return new RestException(404, relayed, CODE_DOES_NOT_EXIST);
        }
        if (message.contains("already exists")) {
            return new RestException(409, relayed, CODE_ALREADY_EXISTS);
        }
        if (message.contains("syntax error")) {
            return new RestException(400, relayed, CODE_SYNTAX_ERROR);
        }
        if (message.contains("Insufficient privileges")) {
            return new RestException(403, relayed, CODE_INSUFFICIENT_PRIVILEGES);
        }
        if (message.contains("did not exist or was purged")) {
            return new RestException(400, relayed, CODE_NOT_RESTORABLE);
        }
        if (message.startsWith("Cannot set resource constraint") || message.startsWith("Cannot unset resource constraint")) {
            return new RestException(400, relayed, CODE_RESOURCE_CONSTRAINT);
        }
        if (message.startsWith("Invalid state.")) {
            return new RestException(400, relayed, CODE_INVALID_STATE);
        }
        if (message.contains("invalid type of property")) {
            return new RestException(400, relayed, CODE_INVALID_PROPERTY_TYPE);
        }
        if (message.contains("invalid value") && message.contains("for property")) {
            return new RestException(400, relayed, CODE_INVALID_PROPERTY_VALUE);
        }
        if (message.startsWith("Cannot perform retry: no suitable run")) {
            return new RestException(400, relayed, CODE_RETRY_NO_RUN);
        }
        if (message.startsWith("Cannot perform retry: run (")) {
            return new RestException(400, relayed, CODE_RETRY_NO_FAILURES);
        }
        if (message.startsWith("Execute task cannot be called on non-root task")) {
            return new RestException(400, relayed, CODE_NON_ROOT_TASK);
        }
        if (message.startsWith("Invalid on_conflict strategy")) {
            return new RestException(400, relayed, CODE_ON_CONFLICT);
        }
        return new RestException(400, relayed, null);
    }

    /**
     * A statement's error message as the REST API relays it: the {@code SQL compilation error:} prefix removed —
     * keeping the line break that follows it — and the whole message in lower case. The account answers
     * {@code "\nobject 'w1' already exists."} for the statement's {@code SQL compilation error:\nObject 'W1' already
     * exists.}, and {@code "invalid state. warehouse 'w1' cannot be suspended."} for an execution error.
     */
    public static String message(final String sqlMessage) {
        // The prefix is matched in any case: a few refusals spell it "SQL Compilation error:".
        final String body = sqlMessage.regionMatches(true, 0, COMPILATION_ERROR, 0, COMPILATION_ERROR.length())
            ? sqlMessage.substring(COMPILATION_ERROR.length()) : sqlMessage;
        return body.toLowerCase(Locale.ROOT);
    }
}

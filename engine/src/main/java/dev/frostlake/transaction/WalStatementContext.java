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


package dev.frostlake.transaction;

import dev.frostlake.metastore.CounterDraw;
import dev.frostlake.metastore.StatementAnswers;
import dev.frostlake.storage.RecordedResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What a logged statement ran under: the session that ran it, its user, primary role and active roles, its current
 * database and schema, its variables and parameters, the query ID it answered with, the values it drew from counters,
 * the results it read by RESULT_SCAN and from the query-history listings, what its identities answered (see
 * {@link StatementAnswers}), whether the work it ran in sessions of their own went to records of their own, and how
 * many records the log took between the statement and its own record, and which of its variables a SET gave a
 * DECFLOAT, whose decimals carry no type of their own. Recovery replays each session of the log in a
 * session of its own, every statement put back into its context and in the place it ran: one session's USE, SET,
 * ALTER SESSION or USE ROLE never reaches another's statements, a variable a session held before a checkpoint is
 * there again, a replayed statement draws, reads and answers again what it first did, and a statement of a
 * transaction committed after other sessions' work reads what it read then, not what they committed meanwhile.
 */
public final class WalStatementContext {

    private final String session;
    private final String user;
    private final String role;
    private final List<String> activeRoles;
    private final String database;
    private final String schema;
    private final String queryId;
    private final List<CounterDraw> draws;
    private final Map<String, Object> variables;
    private final Map<String, Object> parameters;
    private final Set<String> unkeptVariables;
    private final Set<String> unkeptParameters;
    private final List<RecordedResult> results;
    private final Map<String, List<String>> answers;
    private final boolean runsLoggedApart;
    private final long readMark;
    private final int lag;
    private final Set<String> decfloatVariables;
    private final Map<String, Integer> variablePrecisions;

    /**
     * A context that carries no session state, read results or place: one from a log before v5.
     *
     * @param session     the session that ran the statement, unique across the engine's lifetimes
     * @param user        its user, or null
     * @param role        its primary role, or null
     * @param activeRoles its active roles
     * @param database    its current database, or null
     * @param schema      its current schema, or null
     * @param queryId     the query ID the statement answered with, or null
     * @param draws       what it drew from counters, in order
     */
    public WalStatementContext(final String session, final String user, final String role,
                               final List<String> activeRoles, final String database, final String schema,
                               final String queryId, final List<CounterDraw> draws) {
        this(session, user, role, activeRoles, database, schema, queryId, draws, null, null,
            Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<RecordedResult>emptyList(),
            -1L, 0);
    }

    /**
     * @param session          the session that ran the statement, unique across the engine's lifetimes
     * @param user             its user, or null
     * @param role             its primary role, or null
     * @param activeRoles      its active roles
     * @param database         its current database, or null
     * @param schema           its current schema, or null
     * @param queryId          the query ID the statement answered with, or null
     * @param draws            what it drew from counters, in order
     * @param variables        the session's variables as the statement started, or null when not carried
     * @param parameters       the parameters the session set as the statement started, or null when not carried
     * @param unkeptVariables  the variables the session held whose values the log could not keep
     * @param unkeptParameters the parameters the session set whose values the log could not keep
     * @param results          the results it read by RESULT_SCAN, in order, a null for one the log could not keep
     * @param readMark         how many records the log had taken when the statement ended, or -1 when unknown
     * @param lag              how many records the log took between the statement's end and its own record
     */
    public WalStatementContext(final String session, final String user, final String role,
                               final List<String> activeRoles, final String database, final String schema,
                               final String queryId, final List<CounterDraw> draws,
                               final Map<String, Object> variables, final Map<String, Object> parameters,
                               final Set<String> unkeptVariables, final Set<String> unkeptParameters,
                               final List<RecordedResult> results, final long readMark, final int lag) {
        this(session, user, role, activeRoles, database, schema, queryId, draws, variables, parameters,
            unkeptVariables, unkeptParameters, results, Collections.<String, List<String>>emptyMap(), false, readMark,
            lag);
    }

    /**
     * @param session          the session that ran the statement, unique across the engine's lifetimes
     * @param user             its user, or null
     * @param role             its primary role, or null
     * @param activeRoles      its active roles
     * @param database         its current database, or null
     * @param schema           its current schema, or null
     * @param queryId          the query ID the statement answered with, or null
     * @param draws            what it drew from counters, in order
     * @param variables        the session's variables as the statement started, or null when not carried
     * @param parameters       the parameters the session set as the statement started, or null when not carried
     * @param unkeptVariables  the variables the session held whose values the log could not keep
     * @param unkeptParameters the parameters the session set whose values the log could not keep
     * @param results          the results it read, in order, a null for one the log could not keep
     * @param answers          what its identities answered, by identity, each identity's values in the order met
     * @param runsLoggedApart  whether the work it ran in sessions of their own went to records of their own
     * @param readMark         how many records the log had taken when the statement ended, or -1 when unknown
     * @param lag              how many records the log took between the statement's end and its own record
     */
    public WalStatementContext(final String session, final String user, final String role,
                               final List<String> activeRoles, final String database, final String schema,
                               final String queryId, final List<CounterDraw> draws,
                               final Map<String, Object> variables, final Map<String, Object> parameters,
                               final Set<String> unkeptVariables, final Set<String> unkeptParameters,
                               final List<RecordedResult> results, final Map<String, List<String>> answers,
                               final boolean runsLoggedApart, final long readMark, final int lag) {
        this(session, user, role, activeRoles, database, schema, queryId, draws, variables, parameters, unkeptVariables,
            unkeptParameters, results, answers, runsLoggedApart, readMark, lag, null, null);
    }

    private WalStatementContext(final String session, final String user, final String role,
                                final List<String> activeRoles, final String database, final String schema,
                                final String queryId, final List<CounterDraw> draws,
                                final Map<String, Object> variables, final Map<String, Object> parameters,
                                final Set<String> unkeptVariables, final Set<String> unkeptParameters,
                                final List<RecordedResult> results, final Map<String, List<String>> answers,
                                final boolean runsLoggedApart, final long readMark, final int lag,
                                final Set<String> decfloatVariables, final Map<String, Integer> variablePrecisions) {
        this.session = session;
        this.user = user;
        this.role = role;
        this.activeRoles = new ArrayList<String>(activeRoles);
        this.database = database;
        this.schema = schema;
        this.queryId = queryId;
        this.draws = new ArrayList<CounterDraw>(draws);
        this.variables = variables == null ? null : new LinkedHashMap<String, Object>(variables);
        this.parameters = parameters == null ? null : new HashMap<String, Object>(parameters);
        this.unkeptVariables = new HashSet<String>(unkeptVariables);
        this.unkeptParameters = new HashSet<String>(unkeptParameters);
        this.results = new ArrayList<RecordedResult>(results);
        this.answers = new LinkedHashMap<String, List<String>>();
        for (final Map.Entry<String, List<String>> identity : answers.entrySet()) {
            this.answers.put(identity.getKey(), new ArrayList<String>(identity.getValue()));
        }
        this.runsLoggedApart = runsLoggedApart;
        this.readMark = readMark;
        this.lag = lag;
        this.decfloatVariables = decfloatVariables == null ? null : new HashSet<String>(decfloatVariables);
        this.variablePrecisions = variablePrecisions == null ? null
            : new HashMap<String, Integer>(variablePrecisions);
    }

    /**
     * This context with the variables a SET gave a DECFLOAT named.
     *
     * @param names the variables' upper-case names, or null where they are not known
     * @return the context
     */
    public WalStatementContext withDecfloatVariables(final Set<String> names) {
        return new WalStatementContext(session, user, role, activeRoles, database, schema, queryId, draws,
            variables, parameters, unkeptVariables, unkeptParameters, results, answers, runsLoggedApart, readMark, lag,
            names, variablePrecisions);
    }

    /**
     * This context with the precisions a SET gave time and timestamp variables.
     *
     * @param precisions the precisions, by the variables' upper-case names, or null where they are not known
     * @return the context
     */
    public WalStatementContext withVariablePrecisions(final Map<String, Integer> precisions) {
        return new WalStatementContext(session, user, role, activeRoles, database, schema, queryId, draws,
            variables, parameters, unkeptVariables, unkeptParameters, results, answers, runsLoggedApart, readMark, lag,
            decfloatVariables, precisions);
    }

    /**
     * This context once its statement has run: the query ID it answered with and what it drew.
     *
     * @param answeredWith the statement's query ID, or null
     * @param drawn        what it drew from counters
     * @return the finished context
     */
    public WalStatementContext finished(final String answeredWith, final List<CounterDraw> drawn) {
        return finished(answeredWith, drawn, Collections.<RecordedResult>emptyList(), readMark);
    }

    /**
     * This context once its statement has run: the query ID it answered with, what it drew, what it read by
     * RESULT_SCAN and how many records the log had taken when it ended.
     *
     * @param answeredWith the statement's query ID, or null
     * @param drawn        what it drew from counters
     * @param read         the results it read by RESULT_SCAN
     * @param endMark      how many records the log had taken when it ended
     * @return the finished context
     */
    public WalStatementContext finished(final String answeredWith, final List<CounterDraw> drawn,
                                        final List<RecordedResult> read, final long endMark) {
        return finished(answeredWith, drawn, read, answers, runsLoggedApart, endMark);
    }

    /**
     * This context once its statement has run: the query ID it answered with, what it drew, what it read, what its
     * identities answered, whether the work it ran in sessions of their own went to records of their own, and how
     * many records the log had taken when it ended.
     *
     * @param answeredWith the statement's query ID, or null
     * @param drawn        what it drew from counters
     * @param read         the results it read
     * @param answered     what its identities answered
     * @param apart        whether the work it ran in sessions of their own went to records of their own
     * @param endMark      how many records the log had taken when it ended
     * @return the finished context
     */
    public WalStatementContext finished(final String answeredWith, final List<CounterDraw> drawn,
                                        final List<RecordedResult> read, final Map<String, List<String>> answered,
                                        final boolean apart, final long endMark) {
        return new WalStatementContext(session, user, role, activeRoles, database, schema, answeredWith, drawn,
            variables, parameters, unkeptVariables, unkeptParameters, read, answered, apart, endMark, lag,
            decfloatVariables, variablePrecisions);
    }

    /**
     * This context in the record the log takes it into: how many records came between its statement and that one.
     *
     * @param taken how many records the log had taken when it took this one's
     * @return the context with its lag
     */
    public WalStatementContext loggedAfter(final long taken) {
        final long between = readMark < 0 ? 0 : Math.max(0L, taken - readMark);
        return new WalStatementContext(session, user, role, activeRoles, database, schema, queryId, draws,
            variables, parameters, unkeptVariables, unkeptParameters, results, answers, runsLoggedApart, readMark,
            (int) Math.min(Integer.MAX_VALUE, between), decfloatVariables, variablePrecisions);
    }

    public String getSession() {
        return session;
    }

    public String getUser() {
        return user;
    }

    public String getRole() {
        return role;
    }

    public List<String> getActiveRoles() {
        return new ArrayList<String>(activeRoles);
    }

    public String getDatabase() {
        return database;
    }

    public String getSchema() {
        return schema;
    }

    public String getQueryId() {
        return queryId;
    }

    public List<CounterDraw> getDraws() {
        return new ArrayList<CounterDraw>(draws);
    }

    /** Whether the context carries its session's variables and parameters: one from a log before v5 does not. */
    public boolean carriesSessionState() {
        return variables != null && parameters != null;
    }

    /** The session's variables as the statement started, by upper-case name, or null when not carried. */
    public Map<String, Object> getVariables() {
        return variables == null ? null : new LinkedHashMap<String, Object>(variables);
    }

    /** The parameters the session had set as the statement started, by upper-case name, or null when not carried. */
    public Map<String, Object> getParameters() {
        return parameters == null ? null : new HashMap<String, Object>(parameters);
    }

    /** The variables the session held whose values the log could not keep: the replay leaves them as it has them. */
    public Set<String> getUnkeptVariables() {
        return new HashSet<String>(unkeptVariables);
    }

    /** The parameters the session set whose values the log could not keep: the replay leaves them as it has them. */
    public Set<String> getUnkeptParameters() {
        return new HashSet<String>(unkeptParameters);
    }

    /**
     * The results the statement read by RESULT_SCAN and from the query-history listings, in order; a null stands for
     * one the log could not keep.
     */
    public List<RecordedResult> getResults() {
        return new ArrayList<RecordedResult>(results);
    }

    /** What the statement's identities answered, by identity, each identity's values in the order it met them. */
    public Map<String, List<String>> getAnswers() {
        final Map<String, List<String>> copy = new LinkedHashMap<String, List<String>>();
        for (final Map.Entry<String, List<String>> identity : answers.entrySet()) {
            copy.put(identity.getKey(), new ArrayList<String>(identity.getValue()));
        }
        return copy;
    }

    /**
     * Whether the work the statement ran in sessions of their own went to records of their own, which replay it in
     * their places, so the statement's replay does not run it again.
     */
    public boolean isRunsLoggedApart() {
        return runsLoggedApart;
    }

    /** How many records the log had taken when the statement ended, or -1 when that is not known. */
    public long getReadMark() {
        return readMark;
    }

    /** How many records the log took between the statement's end and its own record: 0 for one logged at once. */
    public int getLag() {
        return lag;
    }

    /**
     * The variables a SET gave a DECFLOAT, by upper-case name, or null where the context does not name them: one from a
     * log before v8.
     */
    public Set<String> getDecfloatVariables() {
        return decfloatVariables == null ? null : new HashSet<String>(decfloatVariables);
    }

    /**
     * The precisions a SET gave time and timestamp variables, by upper-case name, or null where the context does not
     * name them: one from a log before v9.
     */
    public Map<String, Integer> getVariablePrecisions() {
        return variablePrecisions == null ? null : new HashMap<String, Integer>(variablePrecisions);
    }
}

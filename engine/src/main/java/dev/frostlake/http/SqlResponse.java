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

package dev.frostlake.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.frostlake.executor.commands.FailureCode;
import dev.frostlake.storage.ResultSet;

import java.util.ArrayList;
import java.util.List;

/**
 * HTTP response object for SQL execution
 */
public class SqlResponse {
    private boolean success;
    private String sessionId;
    private String errorMessage;
    private List<ResultSetData> resultSets;
    private long executionTimeMs;
    // True when the server had to start the session this request ran in: no sessionId was sent, or the
    // one sent named no live session (idle-expired, released, or from before a restart). The context a
    // client set up earlier — USE, variables, ALTER SESSION — is then gone.
    private boolean newSession;
    // Whether this answer said anything about newSession. A client reading an answer from a server that
    // predates the field sees it unset: such a server neither understands requireSession nor releases a
    // session on DELETE.
    private boolean newSessionAnswered;
    // The session's CLIENT_SESSION_KEEP_ALIVE and CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY, when it has set
    // them: the driver keeps the session alive by them. Absent when the session sets none.
    private Boolean clientSessionKeepAlive;
    private Integer clientSessionKeepAliveHeartbeatFrequency;
    // TRUE once the session has set CLIENT_RESULT_COLUMN_CASE_INSENSITIVE to TRUE: the driver then matches a column
    // label ignoring case. Absent otherwise.
    private Boolean clientResultColumnCaseInsensitive;
    // The zone the session's TIMEZONE names once the request has run, its own or inherited: what the driver reads a
    // DATE's midnight and a wall clock of no zone in.
    private String timezone;
    // The SQLSTATE and vendor code of a failed statement, where the account's are known (see FailureCode#ofClient):
    // a canceled statement's 57014, with 604 for a cancel or a query timeout and 630 for the session's
    // STATEMENT_TIMEOUT_IN_SECONDS, a missing table's 42S02 with 2003.
    private String sqlState;
    private Integer errorCode;
    // Where the session stands once the request has run; absent from an answer that concerns no session's statement.
    private SessionNamespace namespace;

    public SqlResponse() {
        this.resultSets = new ArrayList<>();
    }

    public SqlResponse(final boolean success, final String sessionId) {
        this.success = success;
        this.sessionId = sessionId;
        this.resultSets = new ArrayList<>();
    }

    public static SqlResponse success(final String sessionId, final List<ResultSet> resultSets, final long executionTimeMs) {
        final SqlResponse response = new SqlResponse(true, sessionId);
        response.setExecutionTimeMs(executionTimeMs);

        for (final ResultSet rs : resultSets) {
            response.addResultSet(ResultSetData.from(rs));
        }

        return response;
    }

    public static SqlResponse error(final String sessionId, final String errorMessage) {
        return refused(sessionId, errorMessage, FailureCode.ofClientMessage(errorMessage));
    }

    /**
     * A statement's failure: its sentence, with the code and the state its exception carries.
     *
     * @param sessionId    the session the statement ran in
     * @param errorMessage the sentence
     * @param failure      what the statement failed with
     * @return the answer
     */
    public static SqlResponse error(final String sessionId, final String errorMessage, final Throwable failure) {
        return refused(sessionId, errorMessage, FailureCode.ofClient(failure));
    }

    private static SqlResponse refused(final String sessionId, final String errorMessage, final FailureCode family) {
        final SqlResponse response = new SqlResponse(false, sessionId);
        response.setErrorMessage(errorMessage);
        if (family != null) {
            response.setSqlState(family.state());
            response.setErrorCode(Integer.valueOf(family.code()));
        }
        return response;
    }

    public void addResultSet(final ResultSetData data) {
        this.resultSets.add(data);
    }

    // Getters and setters

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(final boolean success) {
        this.success = success;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(final String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public List<ResultSetData> getResultSets() {
        return resultSets;
    }

    public void setResultSets(final List<ResultSetData> resultSets) {
        this.resultSets = resultSets;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(final long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public boolean isNewSession() {
        return newSession;
    }

    public void setNewSession(final boolean newSession) {
        this.newSession = newSession;
        this.newSessionAnswered = true;
    }

    /**
     * Whether the answer carried newSession at all — false for one read from a server that predates the
     * field. Not a bean property, so it never crosses the wire itself.
     */
    public boolean carriesNewSession() {
        return newSessionAnswered;
    }

    /** The session's CLIENT_SESSION_KEEP_ALIVE, or null when the session has not set it. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getClientSessionKeepAlive() {
        return clientSessionKeepAlive;
    }

    public void setClientSessionKeepAlive(final Boolean clientSessionKeepAlive) {
        this.clientSessionKeepAlive = clientSessionKeepAlive;
    }

    /** The session's CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY in seconds, or null when it has not set it. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getClientSessionKeepAliveHeartbeatFrequency() {
        return clientSessionKeepAliveHeartbeatFrequency;
    }

    public void setClientSessionKeepAliveHeartbeatFrequency(final Integer clientSessionKeepAliveHeartbeatFrequency) {
        this.clientSessionKeepAliveHeartbeatFrequency = clientSessionKeepAliveHeartbeatFrequency;
    }

    /** The zone the session's TIMEZONE names once the request has run, or null where none was reported. */
    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(final String timezone) {
        this.timezone = timezone;
    }

    /** TRUE when the session has set CLIENT_RESULT_COLUMN_CASE_INSENSITIVE to TRUE, or null. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getClientResultColumnCaseInsensitive() {
        return clientResultColumnCaseInsensitive;
    }

    public void setClientResultColumnCaseInsensitive(final Boolean clientResultColumnCaseInsensitive) {
        this.clientResultColumnCaseInsensitive = clientResultColumnCaseInsensitive;
    }

    /** The failed statement's SQLSTATE, or null when the answer carries none. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getSqlState() {
        return sqlState;
    }

    public void setSqlState(final String sqlState) {
        this.sqlState = sqlState;
    }

    /** The failed statement's vendor code, or null when the answer carries none. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(final Integer errorCode) {
        this.errorCode = errorCode;
    }

    /** The session's current database, schema and user once the request has run, or null when the answer has none. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public SessionNamespace getNamespace() {
        return namespace;
    }

    public void setNamespace(final SessionNamespace namespace) {
        this.namespace = namespace;
    }

    /**
     * Reports where the session stands and as whom: its current database and schema and its user, which a driver's
     * {@code getCatalog}, {@code getSchema} and {@code getUserName} answer with.
     *
     * @param session the session the request ran in
     * @param user    the session's user, as CURRENT_USER() names it
     */
    public void reportNamespace(final SessionContext session, final String user) {
        this.namespace = new SessionNamespace(session.getCurrentDatabase(), session.getCurrentSchema(), user);
    }

    /**
     * Reports what the session keeps itself alive by: its own CLIENT_SESSION_KEEP_ALIVE and heartbeat frequency, as
     * ALTER SESSION set them. A value no driver could read is left out, as one the session never set is.
     *
     * @param session the session the statement ran in
     */
    public void reportKeepAlive(final SessionContext session) {
        final Object keepAlive = session.getSettings().getParameter("CLIENT_SESSION_KEEP_ALIVE");
        if (keepAlive instanceof Boolean) {
            this.clientSessionKeepAlive = (Boolean) keepAlive;
        } else if (keepAlive != null && ("TRUE".equalsIgnoreCase(keepAlive.toString().trim())
                || "FALSE".equalsIgnoreCase(keepAlive.toString().trim()))) {
            this.clientSessionKeepAlive = Boolean.valueOf(keepAlive.toString().trim());
        }
        final Object frequency = session.getSettings().getParameter("CLIENT_SESSION_KEEP_ALIVE_HEARTBEAT_FREQUENCY");
        if (frequency instanceof Number) {
            this.clientSessionKeepAliveHeartbeatFrequency = ((Number) frequency).intValue();
        } else if (frequency != null) {
            try {
                this.clientSessionKeepAliveHeartbeatFrequency = Integer.valueOf(frequency.toString().trim());
            } catch (final NumberFormatException unreadable) {
                // Left out: the driver keeps the frequency it had.
            }
        }
    }

    /**
     * Reports that the session has set CLIENT_RESULT_COLUMN_CASE_INSENSITIVE to TRUE, after which the driver matches
     * a column label ignoring case for the rest of the connection; while it has not, nothing is reported.
     *
     * @param session the session the statement ran in
     */
    public void reportResultColumnCase(final SessionContext session) {
        if (session.getSettings().resultColumnsCaseInsensitive()) {
            this.clientResultColumnCaseInsensitive = Boolean.TRUE;
        }
    }

    /**
     * Reports the zone the session's TIMEZONE names once the request has run — its own, its user's or the account's —
     * which the driver reads a DATE's midnight and a wall clock of no zone in, as the account's driver reads them in
     * the zone its result names.
     *
     * @param zone the zone's name, or null where the session names none
     */
    public void reportTimezone(final String zone) {
        this.timezone = zone;
    }

}

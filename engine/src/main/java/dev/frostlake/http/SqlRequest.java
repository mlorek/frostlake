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

/**
 * HTTP request object for SQL execution
 */
public class SqlRequest {
    private String sql;
    private String sessionId;
    // The client's autocommit mode, or null when the request declares none. The session's AUTOCOMMIT
    // follows a declared mode whenever it changes — see SessionContext.followClientAutoCommit.
    private Boolean autoCommit;
    // When true, a sessionId the server holds no session under is refused (HTTP 404, success:false, the
    // statement not run) instead of starting a fresh session under it.
    private boolean requireSession;
    // How many statements this request declares, or null when it declares none. The account's driver
    // sends the count with the statement; here it is optional, and the session's MULTI_STATEMENT_COUNT
    // answers for a request that omits it. Zero means any number.
    private Integer multiStatementCount;

    public SqlRequest() {
    }

    public SqlRequest(final String sql, final String sessionId) {
        this.sql = sql;
        this.sessionId = sessionId;
    }

    public String getSql() {
        return sql;
    }

    public void setSql(final String sql) {
        this.sql = sql;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * The autocommit mode this request declares, or null when it declares none. A null is left out of
     * the JSON, so a client that never sets the mode sends no field at all: the server then leaves the
     * session's setting as {@code ALTER SESSION SET AUTOCOMMIT} left it.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Boolean getAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(final Boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    /**
     * The statement count this request declares, or null when it declares none. A null is left out of
     * the JSON, so a client that never sets it sends no field at all and inherits the session's
     * MULTI_STATEMENT_COUNT.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Integer getMultiStatementCount() {
        return multiStatementCount;
    }

    public void setMultiStatementCount(final Integer multiStatementCount) {
        this.multiStatementCount = multiStatementCount;
    }

    public boolean isRequireSession() {
        return requireSession;
    }

    public void setRequireSession(final boolean requireSession) {
        this.requireSession = requireSession;
    }
}

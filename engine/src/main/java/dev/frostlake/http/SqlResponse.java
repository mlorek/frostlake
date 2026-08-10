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
        final SqlResponse response = new SqlResponse(false, sessionId);
        response.setErrorMessage(errorMessage);
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

}

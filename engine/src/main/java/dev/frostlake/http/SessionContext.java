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

import dev.frostlake.executor.StatementCount;
import dev.frostlake.security.SessionSettings;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-session context tracking user state
 * Each HTTP session has its own database/schema context
 */
public class SessionContext {
    private final String sessionId;
    private final long createdAt;
    private final AtomicLong lastAccessTime;

    private volatile String currentDatabase;
    private volatile String currentSchema;
    private volatile boolean autoCommit;
    // The autocommit mode the session's client last declared on a request, or null before one did.
    private Boolean clientAutoCommit;
    private volatile boolean inTransaction;
    // How many statements a request of this session may hold when it declares no count of its own.
    // It starts at 1, as the account's does, and follows an ALTER SESSION that moves it.
    private volatile int multiStatementCount = 1;
    private volatile Long transactionId;

    // Session-specific variables (for stored procedures)
    private final Map<String, Object> sessionVariables;
    // The parameters ALTER SESSION sets and the variables SET defines, this session's own.
    private final SessionSettings settings = new SessionSettings();

    public SessionContext() {
        this(UUID.randomUUID().toString(), "SNOWFLAKE", "PUBLIC");
    }

    public SessionContext(final String sessionId) {
        this(sessionId, "SNOWFLAKE", "PUBLIC");
    }

    /** A session starting where the server's configured defaults point. */
    public SessionContext(final String sessionId, final String defaultDatabase,
                          final String defaultSchema) {
        this.sessionId = sessionId;
        this.createdAt = System.currentTimeMillis();
        this.lastAccessTime = new AtomicLong(System.currentTimeMillis());
        this.currentDatabase = defaultDatabase;
        this.currentSchema = defaultSchema;
        this.autoCommit = true;
        this.inTransaction = false;
        this.sessionVariables = new HashMap<>();
    }

    /** This session's parameters and variables, which the engine reads while one of its requests runs. */
    public SessionSettings getSettings() {
        return settings;
    }

    public void touch() {
        lastAccessTime.set(System.currentTimeMillis());
    }

    public boolean isExpired(final long timeoutMs) {
        return (System.currentTimeMillis() - lastAccessTime.get()) > timeoutMs;
    }

    // Getters and setters

    public String getSessionId() {
        return sessionId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public long getLastAccessTime() {
        return lastAccessTime.get();
    }

    public String getCurrentDatabase() {
        return currentDatabase;
    }

    public void setCurrentDatabase(final String currentDatabase) {
        this.currentDatabase = currentDatabase;
    }

    public String getCurrentSchema() {
        return currentSchema;
    }

    public void setCurrentSchema(final String currentSchema) {
        this.currentSchema = currentSchema;
    }

    /** How many statements a request may hold when it declares no count of its own; 0 means any. */
    public int getMultiStatementCount() {
        return multiStatementCount;
    }

    /**
     * Follow an exact {@code ALTER SESSION SET/UNSET MULTI_STATEMENT_COUNT} this session just ran, so
     * the next request that declares no count of its own is gated by what the client asked for. Any
     * other statement leaves the value alone.
     */
    public void followStatementCount(final String sql) {
        final Integer assigned = StatementCount.assignedCount(sql);
        if (assigned != null) {
            multiStatementCount = assigned.intValue();
        }
    }

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(final boolean autoCommit) {
        this.autoCommit = autoCommit;
    }

    /**
     * Follow the autocommit mode a request declares. A declared mode that differs from the one the
     * session's previous declaring request carried — or the first one the session sees — becomes the
     * session's AUTOCOMMIT setting, exactly as {@code ALTER SESSION SET AUTOCOMMIT} would set it. A
     * request that declares nothing, or repeats the mode already declared, leaves the setting where it
     * stands: a client that sends its mode on every request switches the session exactly when that mode
     * switches, and an {@code ALTER SESSION SET AUTOCOMMIT} it runs in between stays in force.
     *
     * @param declared the request's autocommit mode, or null when it declares none
     */
    public synchronized void followClientAutoCommit(final Boolean declared) {
        if (declared == null || declared.equals(clientAutoCommit)) {
            return;
        }
        clientAutoCommit = declared;
        autoCommit = declared.booleanValue();
    }

    public boolean isInTransaction() {
        return inTransaction;
    }

    public void setInTransaction(final boolean inTransaction) {
        this.inTransaction = inTransaction;
    }

    public Long getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(final Long transactionId) {
        this.transactionId = transactionId;
    }

    public synchronized Object getVariable(final String name) {
        return sessionVariables.get(name);
    }

    public synchronized void setVariable(final String name, final Object value) {
        sessionVariables.put(name, value);
    }

    public synchronized void clearVariables() {
        sessionVariables.clear();
    }
}

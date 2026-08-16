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
    private volatile boolean inTransaction;
    private volatile Long transactionId;

    // Session-specific variables (for stored procedures)
    private final Map<String, Object> sessionVariables;

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

    public boolean isAutoCommit() {
        return autoCommit;
    }

    public void setAutoCommit(final boolean autoCommit) {
        this.autoCommit = autoCommit;
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

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

import java.util.ArrayList;
import java.util.List;

/**
 * What a logged statement ran under: the session that ran it, its user, primary role and active roles, its current
 * database and schema, the query ID it answered with, and the values it drew from counters. Recovery replays each
 * session of the log in a session of its own, every statement put back into its context, so one session's USE,
 * SET, ALTER SESSION or USE ROLE never reaches another's statements, and a replayed statement draws again what it
 * first drew.
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

    /**
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
        this.session = session;
        this.user = user;
        this.role = role;
        this.activeRoles = new ArrayList<String>(activeRoles);
        this.database = database;
        this.schema = schema;
        this.queryId = queryId;
        this.draws = new ArrayList<CounterDraw>(draws);
    }

    /**
     * This context once its statement has run: the query ID it answered with and what it drew.
     *
     * @param answeredWith the statement's query ID, or null
     * @param drawn        what it drew from counters
     * @return the finished context
     */
    public WalStatementContext finished(final String answeredWith, final List<CounterDraw> drawn) {
        return new WalStatementContext(session, user, role, activeRoles, database, schema, answeredWith, drawn);
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
}

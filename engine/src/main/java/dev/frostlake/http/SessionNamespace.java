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

/**
 * Where a session stands once a request has run, and as whom: its current database and schema, each null when the
 * session has none, and its user as {@code CURRENT_USER()} names it. The account reports where its session stands
 * with every statement's answer, and its driver's {@code getCatalog} and {@code getSchema} read it from there; its
 * {@code getUserName} names the user the session runs as.
 */
public class SessionNamespace {

    private String database;
    private String schema;
    private String user;

    /** An empty namespace, for the JSON reader. */
    public SessionNamespace() {
    }

    /**
     * A session's namespace.
     *
     * @param database its current database, or null
     * @param schema   its current schema, or null
     * @param user     its user, or null
     */
    public SessionNamespace(final String database, final String schema, final String user) {
        this.database = database;
        this.schema = schema;
        this.user = user;
    }

    /**
     * The session's current database.
     *
     * @return its name, or null when the session has none
     */
    public String getDatabase() {
        return database;
    }

    public void setDatabase(final String database) {
        this.database = database;
    }

    /**
     * The session's current schema.
     *
     * @return its name, or null when the session has none
     */
    public String getSchema() {
        return schema;
    }

    public void setSchema(final String schema) {
        this.schema = schema;
    }

    /**
     * The session's user.
     *
     * @return its name, as CURRENT_USER() names it, or null when the answer does not say
     */
    public String getUser() {
        return user;
    }

    public void setUser(final String user) {
        this.user = user;
    }
}

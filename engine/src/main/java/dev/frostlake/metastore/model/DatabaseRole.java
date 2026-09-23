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

package dev.frostlake.metastore.model;

import java.time.LocalDateTime;

/**
 * A database role: a role that lives in one database and is named {@code <database>.<role>}. It holds privileges
 * and other database roles the way an account role does, and is granted to account roles, users and other
 * database roles of its database.
 */
public class DatabaseRole extends Role {

    private final String database;

    /**
     * @param database the database the role lives in
     * @param name the role's name within the database
     */
    public DatabaseRole(final String database, final String name) {
        super(name);
        this.database = database;
    }

    /**
     * A database role created at a given moment, as a restored snapshot brings it back.
     *
     * @param database the database the role lives in
     * @param name the role's name within the database
     * @param createdTime when it was created, on the host's wall clock
     */
    public DatabaseRole(final String database, final String name, final LocalDateTime createdTime) {
        super(name, createdTime);
        this.database = database;
    }

    /** The database the role lives in. */
    public String getDatabase() {
        return database;
    }

    /** The role's name qualified by its database, {@code DB.ROLE}. */
    public String getQualifiedName() {
        return database + "." + getName();
    }
}

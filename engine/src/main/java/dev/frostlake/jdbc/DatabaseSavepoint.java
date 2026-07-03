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

package dev.frostlake.jdbc;

import java.sql.SQLException;
import java.sql.Savepoint;

/**
 * JDBC Savepoint implementation for Frostlake SQL Engine
 */
public class DatabaseSavepoint implements Savepoint {
    private final String name;
    private final int id;
    private final boolean isNamed;

    /**
     * Create a named savepoint
     */
    public DatabaseSavepoint(final String name) {
        this.name = name;
        this.id = 0;
        this.isNamed = true;
    }

    /**
     * Create an unnamed savepoint with an auto-generated ID
     */
    public DatabaseSavepoint(final int id) {
        this.name = null;
        this.id = id;
        this.isNamed = false;
    }

    @Override
    public int getSavepointId() throws SQLException {
        if (isNamed) {
            throw new SQLException("Cannot get ID for named savepoint");
        }
        return id;
    }

    @Override
    public String getSavepointName() throws SQLException {
        if (!isNamed) {
            throw new SQLException("Cannot get name for unnamed savepoint");
        }
        return name;
    }

    /**
     * Get the internal name used for SQL commands
     * For named savepoints, returns the name
     * For unnamed savepoints, returns an auto-generated name
     */
    public String getInternalName() {
        if (isNamed) {
            return name;
        } else {
            return "SAVEPOINT_" + id;
        }
    }

    public boolean isNamed() {
        return isNamed;
    }

    @Override
    public String toString() {
        if (isNamed) {
            return "Savepoint{name='" + name + "'}";
        } else {
            return "Savepoint{id=" + id + "}";
        }
    }
}

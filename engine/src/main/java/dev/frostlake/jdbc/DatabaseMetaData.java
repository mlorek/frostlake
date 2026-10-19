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

/**
 * The HTTP transport's database metadata: {@link AbstractDatabaseMetaData}, with the URL the connection was opened on
 * and the user its session runs as.
 */
public class DatabaseMetaData extends AbstractDatabaseMetaData {

    private final DatabaseConnection connection;

    public DatabaseMetaData(final DatabaseConnection connection, final HttpClient httpClient) {
        super(connection);
        this.connection = connection;
    }

    @Override
    public String getURL() throws SQLException {
        return connection.jdbcUrl();
    }

    @Override
    public String getUserName() throws SQLException {
        return connection.userName();
    }
}

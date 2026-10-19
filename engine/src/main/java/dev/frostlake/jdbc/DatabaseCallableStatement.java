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

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * The HTTP transport's callable statement: its prepared statement, with the callable surface of
 * {@link FrostlakeCallableStatement}.
 */
public class DatabaseCallableStatement extends DatabasePreparedStatement implements FrostlakeCallableStatement {

    private final CallParameters call;

    public DatabaseCallableStatement(final DatabaseConnection connection, final HttpClient httpClient,
                                     final String sql) {
        super(connection, httpClient, CallParameters.positional(sql));
        this.call = new CallParameters(sql);
    }

    @Override
    public CallParameters callParameters() {
        return call;
    }

    @Override
    public boolean execute() throws SQLException {
        final boolean rows = super.execute();
        call.capture(getResultGrid());
        return rows;
    }

    @Override
    public ResultSet executeQuery() throws SQLException {
        final ResultSet rows = super.executeQuery();
        call.capture(getResultGrid());
        return rows;
    }
}

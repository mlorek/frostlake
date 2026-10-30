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
import java.sql.Statement;

/**
 * Puts a session on the results the account's driver asks for by default, ARROW, for a test of what that driver
 * reads off them: the live harness's sessions ask for JSON results, on which the account's driver reads a FLOAT by the
 * server's text of it instead (see {@link FloatColumnReads}). Frostlake's own drivers read every result the default
 * way, so on Frostlake the setting changes no read.
 */
final class DefaultResultFormat {

    private DefaultResultFormat() {
    }

    /**
     * Sets the session's result format to the driver's default.
     *
     * @param statement a statement of the session
     * @throws SQLException when the session refuses the setting
     */
    static void use(final Statement statement) throws SQLException {
        statement.execute("ALTER SESSION SET JDBC_QUERY_RESULT_FORMAT = 'ARROW'");
    }
}

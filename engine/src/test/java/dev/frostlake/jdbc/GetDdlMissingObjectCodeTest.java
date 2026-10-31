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

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * GET_DDL of an object it does not find reaches the client's driver with error 2003 and SQLSTATE 02000 whatever
 * the kind, where the same sentence of a missing table or view raised by a query or a DESCRIBE carries 42S02 (all
 * live-verified).
 */
public class GetDdlMissingObjectCodeTest extends BaseJdbcTest {

    /** Each statement fails with the code and the state beside it, read as {@code "code state"}. */
    private void assertFamilies(final String[][] cases) throws SQLException {
        for (final String[] failing : cases) {
            try (final Statement s = connection.createStatement()) {
                s.execute(failing[0]);
                fail("no failure: " + failing[0]);
            } catch (final SQLException refused) {
                assertEquals(failing[1], refused.getErrorCode() + " " + refused.getSQLState(), failing[0]);
            }
        }
    }

    @Test
    public void missingObjectsCarryGetDdlsPair() throws SQLException {
        assertFamilies(new String[][] {
            {"SELECT GET_DDL('TABLE', 'no_such_table_675')", "2003 02000"},
            {"SELECT GET_DDL('VIEW', 'no_such_view_675')", "2003 02000"},
            {"SELECT GET_DDL('SEQUENCE', 'no_such_seq_675')", "2003 02000"},
            {"SELECT GET_DDL('TABLE', 'no_such_schema_675.t')", "2003 02000"},
            {"SELECT * FROM no_such_table_675", "2003 42S02"},
            {"DESCRIBE TABLE no_such_table_675", "2003 42S02"},
        });
    }
}

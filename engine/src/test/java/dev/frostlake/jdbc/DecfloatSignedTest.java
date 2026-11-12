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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ResultSetMetaData.isSigned answers true for a DECFLOAT column, which the driver reports as Types.DECIMAL, as it does
 * for a NUMBER: the account's driver decides isSigned by the type code. It answered false. Unmeasured on live.
 */
public class DecfloatSignedTest extends BaseJdbcTest {

    @Test
    public void aDecfloatColumnIsSigned() throws SQLException {
        try (ResultSet rs = statement.executeQuery("SELECT -1.5::DECFLOAT AS d, -1.5::NUMBER(5,1) AS n")) {
            final ResultSetMetaData columns = rs.getMetaData();
            assertEquals(Types.DECIMAL, columns.getColumnType(1));
            assertTrue(columns.isSigned(1), "DECFLOAT");
            assertTrue(columns.isSigned(2), "NUMBER(5,1)");
        }
    }
}

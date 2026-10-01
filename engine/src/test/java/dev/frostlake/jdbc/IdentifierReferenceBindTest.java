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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * An IDENTIFIER() reference over the driver: a bind variable names the object in every position a string
 * does — a FROM clause, a select list, a function name, CREATE, INSERT and DROP — and a DDL statement's status
 * sentence names the object the reference resolves to, canonical and unqualified, whether a string, a
 * dollar-quoted string or a variable wrote it. The word IDENTIFIER written as a table's own name is that name.
 * Live-verified.
 */
public class IdentifierReferenceBindTest extends BaseJdbcTest {

    /** The first row's first cell of a prepared statement run with one bound string. */
    private String bound(final String sql, final String value) throws SQLException {
        try (PreparedStatement prepared = connection.prepareStatement(sql)) {
            prepared.setString(1, value);
            try (ResultSet rs = prepared.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        }
    }

    private void boundUpdate(final String sql, final String value) throws SQLException {
        try (PreparedStatement prepared = connection.prepareStatement(sql)) {
            prepared.setString(1, value);
            prepared.execute();
        }
    }

    /** The status sentence a statement answers with. */
    private String status(final String sql) throws SQLException {
        try (ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    @Test
    public void aBindVariableNamesTheObject() throws SQLException {
        statement.execute("CREATE OR REPLACE TABLE t1 (a INT)");
        statement.execute("INSERT INTO t1 VALUES (7)");
        assertEquals("1", bound("SELECT COUNT(*) FROM IDENTIFIER(?)", "T1"));
        assertEquals("1", bound("SELECT COUNT(*) FROM IDENTIFIER(?)", "t1"));
        assertEquals("1", bound("SELECT COUNT(*) FROM IDENTIFIER( ? )", "T1"));
        assertEquals("7", bound("SELECT IDENTIFIER(?) FROM t1", "A"));
        assertEquals("A", bound("SELECT IDENTIFIER(?)('a')", "UPPER"));
        boundUpdate("CREATE OR REPLACE TABLE IDENTIFIER(?) (a INT)", "T3");
        boundUpdate("INSERT INTO IDENTIFIER(?) VALUES (1)", "T3");
        assertEquals("1", bound("SELECT COUNT(*) FROM IDENTIFIER(?)", "t3"));
        boundUpdate("DROP TABLE IDENTIFIER(?)", "T3");
        assertEquals("0", bound("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = ?", "T3"));
    }

    @Test
    public void theStatusSentenceNamesTheReferencedObject() throws SQLException {
        assertEquals("Table T3 successfully created.", status("CREATE OR REPLACE TABLE IDENTIFIER('t3') (a INT)"));
        assertEquals("Table t4 successfully created.", status("CREATE OR REPLACE TABLE IDENTIFIER('\"t4\"') (a INT)"));
        assertEquals("Table T5 successfully created.",
            status("CREATE OR REPLACE TABLE IDENTIFIER('TEST_DB.PUBLIC.t5') (a INT)"));
        statement.execute("SET tn = 't6'");
        assertEquals("Table T6 successfully created.", status("CREATE OR REPLACE TABLE IDENTIFIER($tn) (a INT)"));
        assertEquals("T3 successfully dropped.", status("DROP TABLE IDENTIFIER('t3')"));
        assertEquals("T6 already exists, statement succeeded.",
            status("CREATE TABLE IF NOT EXISTS IDENTIFIER('t6') (a INT)"));
        assertEquals("Drop statement executed successfully (NOSUCH already dropped).",
            status("DROP TABLE IF EXISTS IDENTIFIER('nosuch')"));
        assertEquals("Table IDENTIFIER successfully created.", status("CREATE OR REPLACE TABLE identifier (a INT)"));
        assertEquals("IDENTIFIER successfully dropped.", status("DROP TABLE identifier"));
        assertEquals("Table T7 successfully created.", status("CREATE OR REPLACE TABLE IDENTIFIER('t7') AS SELECT 1 AS a"));
        assertEquals("Table T8 successfully created.", status("CREATE OR REPLACE TABLE IDENTIFIER($$t8$$) (a INT)"));
        assertEquals("T6 successfully dropped.", status("DROP TABLE IF EXISTS IDENTIFIER($tn)"));
        assertEquals("Statement executed successfully.", status("USE SCHEMA IDENTIFIER('PUBLIC')"));
    }
}

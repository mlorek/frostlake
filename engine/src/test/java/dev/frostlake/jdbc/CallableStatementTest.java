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
import java.math.BigDecimal;
import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CallableStatementTest extends BaseJdbcTest {

    private static final String OUT_PARAMETERS =
        "OUT-parameter registration on a CALL is a Frostlake driver surface: the Snowflake driver models "
        + "a procedure's return value as a result set, not as a registered OUT parameter";

    private static final String NAMED_PARAMETERS =
        "binding :name parameters in a plain SELECT is a Frostlake driver surface; the Snowflake driver "
        + "does not substitute named parameters outside a CALL";

    @Test
    public void testCallableStatementCreation() throws SQLException {
        final CallableStatement cstmt = connection.prepareCall("CALL test_proc(?)");
        assertNotNull(cstmt);
        assertFalse(cstmt.isClosed());
        cstmt.close();
        assertTrue(cstmt.isClosed());
    }

    @Test
    public void testCallFunctionWithIntegerParameter() throws SQLException {
        // Test with simple arithmetic expression
        final CallableStatement cstmt = connection.prepareCall("SELECT ? * 2");
        cstmt.setInt(1, 5);

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(10, rs.getInt(1));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testCallFunctionWithStringParameter() throws SQLException {
        // Test with string concatenation
        final CallableStatement cstmt = connection.prepareCall("SELECT ?");
        cstmt.setString(1, "Hello, World");

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals("Hello, World", rs.getString(1));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testCallFunctionWithMultipleParameters() throws SQLException {
        // Test with simple addition
        final CallableStatement cstmt = connection.prepareCall("SELECT ? + ?");
        cstmt.setInt(1, 10);
        cstmt.setInt(2, 20);

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(30, rs.getInt(1));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testRegisterOutParameter() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), OUT_PARAMETERS);
        // Create a procedure that returns a value
        statement.execute("CREATE PROCEDURE get_constant() RETURNS INTEGER AS 'begin RETURN 42; end;'");

        final CallableStatement cstmt = connection.prepareCall("CALL get_constant()");
        cstmt.registerOutParameter(1, Types.INTEGER);

        cstmt.execute();

        // Note: In this implementation, OUT parameters would need to be extracted from the result
        // The implementation supports registering OUT parameters but extraction depends on result set

        cstmt.close();
    }

    @Test
    public void testSetAndGetIntegerParameter() throws SQLException {
        final CallableStatement cstmt = connection.prepareCall("SELECT ?");
        cstmt.setInt(1, 100);

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(100, rs.getInt(1));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testSetAndGetStringParameter() throws SQLException {
        final CallableStatement cstmt = connection.prepareCall("SELECT ?");
        cstmt.setString(1, "test_string");

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals("test_string", rs.getString(1));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testSetAndGetBooleanParameter() throws SQLException {
        final CallableStatement cstmt = connection.prepareCall("SELECT ?");
        cstmt.setBoolean(1, true);

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertTrue(rs.getBoolean(1));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testSetAndGetDoubleParameter() throws SQLException {
        final CallableStatement cstmt = connection.prepareCall("SELECT ?");
        cstmt.setDouble(1, 3.14159);

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(3.14159, rs.getDouble(1), 0.00001);

        rs.close();
        cstmt.close();
    }

    @Test
    public void testSetAndGetBigDecimalParameter() throws SQLException {
        final CallableStatement cstmt = connection.prepareCall("SELECT ?");
        final BigDecimal value = new BigDecimal("12345.6789");
        cstmt.setBigDecimal(1, value);

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(0, value.compareTo(rs.getBigDecimal(1)));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testNamedParameters() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), NAMED_PARAMETERS);
        // Test named parameter parsing
        final CallableStatement cstmt = connection.prepareCall("SELECT :param1, :param2");

        // Set parameters by name
        cstmt.setString("param1", "first");
        cstmt.setString("param2", "second");

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals("first", rs.getString(1));
        assertEquals("second", rs.getString(2));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testNamedParameterNotFound() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), NAMED_PARAMETERS);
        final CallableStatement cstmt = connection.prepareCall("SELECT :param1");

        // Try to set a parameter that doesn't exist
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws Throwable {
                cstmt.setString("nonexistent", "value");
                
            }
        });

        cstmt.close();
    }

    @Test
    public void testRegisterOutParameterWithScale() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), OUT_PARAMETERS);
        final CallableStatement cstmt = connection.prepareCall("CALL test_proc(?)");
        cstmt.registerOutParameter(1, Types.DECIMAL, 2);

        // Should not throw exception
        cstmt.close();
    }

    @Test
    public void testRegisterOutParameterWithTypeName() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), OUT_PARAMETERS);
        final CallableStatement cstmt = connection.prepareCall("CALL test_proc(?)");
        cstmt.registerOutParameter(1, Types.VARCHAR, "VARCHAR");

        // Should not throw exception
        cstmt.close();
    }

    @Test
    public void testRegisterNamedOutParameter() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), OUT_PARAMETERS);
        final CallableStatement cstmt = connection.prepareCall("CALL test_proc(:result)");
        cstmt.registerOutParameter("result", Types.INTEGER);

        // Should not throw exception
        cstmt.close();
    }

    @Test
    public void testSetNullParameter() throws SQLException {
        final CallableStatement cstmt = connection.prepareCall("SELECT ?");
        cstmt.setNull(1, Types.VARCHAR);

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(null, rs.getString(1));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testSetNullNamedParameter() throws SQLException {
        Assumptions.assumeFalse(isLiveSnowflake(), NAMED_PARAMETERS);
        final CallableStatement cstmt = connection.prepareCall("SELECT :param");
        cstmt.setNull("param", Types.INTEGER);

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(null, rs.getObject(1));

        rs.close();
        cstmt.close();
    }

    @Test
    public void testCallProcedureWithComplexLogic() throws SQLException {
        // Create a procedure with conditional logic
        final String procBody = "BEGIN IF (x > 10) THEN RETURN 'HIGH'; ELSE RETURN 'LOW'; END IF; END;";
        statement.execute("CREATE PROCEDURE check_threshold(x INTEGER) RETURNS VARCHAR AS $$" + procBody + "$$");

        final CallableStatement cstmt = connection.prepareCall("CALL check_threshold(?)");
        cstmt.setInt(1, 15);

        final boolean hasResults = cstmt.execute();
        // Procedure execution should complete

        cstmt.close();
    }

    @Test
    public void testCallFunctionWithNoParameters() throws SQLException {
        // Test with constant value
        final CallableStatement cstmt = connection.prepareCall("SELECT 3.14159");

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(3.14159, rs.getDouble(1), 0.00001);

        rs.close();
        cstmt.close();
    }

    @Test
    public void testExecuteVsExecuteQuery() throws SQLException {
        // Test both execute() and executeQuery() methods
        final CallableStatement cstmt = connection.prepareCall("SELECT ? * 3");
        cstmt.setInt(1, 4);

        // Use executeQuery
        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(12, rs.getInt(1));
        rs.close();

        // Use execute
        cstmt.setInt(1, 5);
        final boolean hasResultSet = cstmt.execute();
        assertTrue(hasResultSet);

        final ResultSet rs2 = cstmt.getResultSet();
        assertTrue(rs2.next());
        assertEquals(15, rs2.getInt(1));
        rs2.close();

        cstmt.close();
    }

    @Test
    public void testMultipleExecutions() throws SQLException {
        // Test executing the same CallableStatement multiple times
        final CallableStatement cstmt = connection.prepareCall("SELECT ? * ?");

        // First execution
        cstmt.setInt(1, 3);
        cstmt.setInt(2, 3);
        final ResultSet rs1 = cstmt.executeQuery();
        assertTrue(rs1.next());
        assertEquals(9, rs1.getInt(1));
        rs1.close();

        // Second execution
        cstmt.setInt(1, 4);
        cstmt.setInt(2, 4);
        final ResultSet rs2 = cstmt.executeQuery();
        assertTrue(rs2.next());
        assertEquals(16, rs2.getInt(1));
        rs2.close();

        // Third execution
        cstmt.setInt(1, 5);
        cstmt.setInt(2, 5);
        final ResultSet rs3 = cstmt.executeQuery();
        assertTrue(rs3.next());
        assertEquals(25, rs3.getInt(1));
        rs3.close();

        cstmt.close();
    }

    @Test
    public void testCallableStatementInheritsFromPreparedStatement() throws SQLException {
        // Verify CallableStatement can use PreparedStatement methods
        statement.execute("CREATE TABLE test_table (id INTEGER, name VARCHAR)");
        statement.execute("INSERT INTO test_table VALUES (1, 'Alice'), (2, 'Bob')");

        final CallableStatement cstmt = connection.prepareCall("SELECT * FROM test_table WHERE id = ?");
        cstmt.setInt(1, 1);

        final ResultSet rs = cstmt.executeQuery();
        assertTrue(rs.next());
        assertEquals(1, rs.getInt("id"));
        assertEquals("Alice", rs.getString("name"));
        assertFalse(rs.next());

        rs.close();
        cstmt.close();
        statement.execute("DROP TABLE test_table");
    }
}

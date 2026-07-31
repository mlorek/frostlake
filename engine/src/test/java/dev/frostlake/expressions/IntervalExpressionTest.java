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

package dev.frostlake.expressions;

import dev.frostlake.BaseJdbcTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Snowflake interval literals, live-verified: the quoted-string form ({@code INTERVAL '10 days'},
 * plural units and comma-separated parts INSIDE the string, a bare number meaning seconds) and the
 * {@code INTERVAL '<n>' <singular-unit>} form. An unquoted amount is a syntax error, and a PLURAL
 * word after the string is a column alias, not a unit — {@code INTERVAL '10' DAYS} adds ten
 * SECONDS. Intervals apply in date/time arithmetic.
 */
public class IntervalExpressionTest extends BaseJdbcTest {

    private String one(final String sql) throws SQLException {
        final ResultSet rs = statement.executeQuery(sql);
        assertTrue(rs.next());
        return rs.getString(1);
    }

    @Test
    public void quotedStringFormAppliesInArithmetic() throws SQLException {
        assertEquals("2024-01-11", one("SELECT ('2024-01-01'::DATE + INTERVAL '10 days')::DATE"));
        assertEquals("2026-01-01", one("SELECT ('2024-01-01'::DATE + INTERVAL '2 years')::DATE"),
            "plural units are accepted INSIDE the quoted string");
        assertEquals("2024-01-01 01:30:00",
            one("SELECT TO_CHAR('2024-01-01 00:00:00'::TIMESTAMP + INTERVAL '90 minutes', 'YYYY-MM-DD HH24:MI:SS')"));
    }

    @Test
    public void multiPartQuotedIntervalAppliesEachPartInOrder() throws SQLException {
        assertEquals("2024-01-02 02:00:00",
            one("SELECT TO_CHAR('2024-01-01'::DATE + INTERVAL '1 day, 2 hours', 'YYYY-MM-DD HH24:MI:SS')"));
    }

    @Test
    public void bareQuotedNumberMeansSeconds() throws SQLException {
        assertEquals("2024-01-01 00:00:10",
            one("SELECT TO_CHAR('2024-01-01'::DATE + INTERVAL '10', 'YYYY-MM-DD HH24:MI:SS')"));
    }

    @Test
    public void singularUnitSuffixIsAccepted() throws SQLException {
        assertEquals("2024-01-11", one("SELECT ('2024-01-01'::DATE + INTERVAL '10' DAY)::DATE"));
        assertEquals("2025-01-01", one("SELECT ('2024-01-01'::DATE + INTERVAL '1' YEAR)::DATE"));
        assertEquals("2024-01-01 05:00:00",
            one("SELECT TO_CHAR('2024-01-01'::DATE + INTERVAL '5' HOUR, 'YYYY-MM-DD HH24:MI:SS')"));
    }

    @Test
    public void pluralWordAfterTheStringIsAnAliasNotAUnit() throws SQLException {
        // Live-verified: INTERVAL '10' DAYS is ten SECONDS with the column aliased DAYS.
        final ResultSet rs = statement.executeQuery(
            "SELECT TO_CHAR('2024-01-01'::DATE + INTERVAL '10', 'YYYY-MM-DD HH24:MI:SS') DAYS");
        assertTrue(rs.next());
        assertEquals("2024-01-01 00:00:10", rs.getString(1));
        assertEquals("DAYS", rs.getMetaData().getColumnLabel(1).toUpperCase());
    }

    @Test
    public void unquotedAmountIsASyntaxErrorLikeSnowflake() {
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.executeQuery("SELECT CURRENT_DATE + INTERVAL 10 DAY");
            }
        });
    }

    @Test
    public void standaloneIntervalIsRejected() {
        // Live-verified: an INTERVAL literal only exists as an operand of date arithmetic. Projected on
        // its own — with or without an alias — Snowflake answers "interval literal is not supported in
        // this form".
        final SQLException bare = assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.executeQuery("SELECT INTERVAL '1 day'");
            }
        });
        assertTrue(bare.getMessage().contains("interval literal is not supported in this form"),
            "unexpected: " + bare.getMessage());
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.executeQuery("SELECT INTERVAL '1 day' AS d");
            }
        });
        // Only the UNIT-LESS literal is "not supported in this form"; the multi-part spelling is the
        // same shape and fails the same way.
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.executeQuery("SELECT INTERVAL '2 hours'");
            }
        });
        assertThrows(SQLException.class, new Executable() {
            @Override
            public void execute() throws SQLException {
                statement.executeQuery("SELECT INTERVAL '1 day, 2 hours'");
            }
        });
    }

    @Test
    public void unitSuffixedIntervalProjectsAsAValue() throws SQLException {
        // Live-verified: the <amount> <singular-unit> spelling is a first-class value —
        // SYSTEM$TYPEOF(INTERVAL '1' DAY) is "INTERVAL DAY(9)[SB16]", it aliases, and it survives
        // TO_VARCHAR. (The Snowflake JDBC driver cannot RENDER an interval result column, "No enum
        // constant … INTERVAL_DAY_TIME", which is a client limitation, not a server rejection.)
        assertIntervalProjects("SELECT INTERVAL '1' DAY");
        assertIntervalProjects("SELECT INTERVAL '2' HOUR");
        assertIntervalProjects("SELECT INTERVAL '1' YEAR");
        assertIntervalProjects("SELECT INTERVAL '1' DAY AS d");
    }

    private void assertIntervalProjects(final String sql) throws SQLException {
        try (final java.sql.ResultSet rs = statement.executeQuery(sql)) {
            assertTrue(rs.next(), "expected a row from: " + sql);
            assertNotNull(rs.getObject(1), "expected an interval value from: " + sql);
        }
    }

    @Test
    public void subtractionAndDefaultsUseTheSameForms() throws SQLException {
        assertEquals("2023-12-31", one("SELECT ('2024-01-01'::DATE - INTERVAL '1 day')::DATE"));
        statement.execute(
            "CREATE TABLE interval_default_t (id INTEGER, d DATE DEFAULT '2024-01-01'::DATE + INTERVAL '30 days')");
        statement.execute("INSERT INTO interval_default_t (id) VALUES (1)");
        assertEquals("2024-01-31", one("SELECT d::DATE FROM interval_default_t"));
    }
}

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
import dev.frostlake.LiveSnowflake;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A SYSTEM$REFERENCE token is its own session's (live-verified): a SESSION reference reads its object in the session
 * that made it and in no other, nor after that session ends, where the other session's own reference still reads its
 * object; a PERSISTENT one a CALL's arguments make ends with the call, in every session.
 */
public class ReferenceTokensAcrossSessionsTest extends BaseJdbcTest {

    private static final String READER = """
        CREATE OR REPLACE PROCEDURE pr_id(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS
        $$ BEGIN LET n NUMBER := (SELECT COUNT(*) FROM IDENTIFIER(:r)); RETURN n; END $$""";

    private static final String KEEPER = """
        CREATE OR REPLACE PROCEDURE pr_keep(r VARCHAR) RETURNS NUMBER LANGUAGE SQL AS
        $$ BEGIN INSERT INTO refs VALUES (:r); LET n NUMBER := (SELECT COUNT(*) FROM IDENTIFIER(:r)); RETURN n;
        END $$""";

    private static final String MISSING = "REFUSED Object '<REF>' does not exist or not authorized. Your primary role"
        + " <ROLE> must have at least one privilege granted on TABLE <REF>.";

    private static final String MISSING_IN_CALL = "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at"
        + " position 23 : SQL compilation error: Object '<REF>' does not exist or not authorized. This executable runs"
        + " with owner's rights. The owner role <ROLE> must have at least one privilege granted on TABLE <REF>.";

    /** Another session on the same account or engine as the test's own connection, in the test's schema. */
    private Connection another() throws SQLException {
        final Connection opened = LiveSnowflake.enabled() ? LiveSnowflake.open()
            : new DirectConnection(sharedEngine);
        try (Statement use = opened.createStatement()) {
            use.execute("USE SCHEMA test_db.public");
        }
        return opened;
    }

    /**
     * What a statement answers: a query's or a call's first cell, OK for any other statement that runs, or its
     * refusal with the session's role and any token masked.
     */
    private static String answer(final Statement on, final String sql) {
        try {
            if (!sql.startsWith("SELECT") && !sql.startsWith("CALL")) {
                on.execute(sql);
                return "OK";
            }
            try (ResultSet rs = on.executeQuery(sql)) {
                return rs.next() ? masked(rs.getString(1)) : "no rows";
            }
        } catch (final SQLException refused) {
            final String message = refused.getMessage().replace('\n', ' ').trim()
                .replaceAll("role \\S+ must", "role <ROLE> must");
            final String compilation = "SQL compilation error: ";
            return "REFUSED " + masked(message.startsWith(compilation) ? message.substring(compilation.length())
                : message).trim();
        }
    }

    private static String masked(final String text) {
        return text == null ? null : text.replaceAll("ENT_REF_[A-Z_]+_\\d{16}_[0-9A-F]{40}", "<REF>");
    }

    /** The first cell a query answers, as it stands. */
    private static String cell(final Statement on, final String sql) throws SQLException {
        try (ResultSet rs = on.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }

    private final List<String> expected = new ArrayList<>();
    private final List<String> answered = new ArrayList<>();

    /** Runs each step in its session, keeping what it answers beside what it should, for {@link #verify}. */
    private void steps(final Statement a, final Statement b, final String[][] steps) {
        for (final String[] step : steps) {
            final Statement on = "A".equals(step[0]) ? a : "B".equals(step[0]) ? b : statement;
            expected.add(step[0] + ": " + masked(step[1]) + " => " + step[2]);
            answered.add(step[0] + ": " + masked(step[1]) + " => " + answer(on, step[1]));
        }
    }

    /** Compares every answer at once, so one run shows each that differs. */
    private void verify() {
        assertEquals(String.join("\n", expected), String.join("\n", answered));
    }

    @Test
    public void aSessionReferenceIsItsSessionsOwn() throws SQLException {
        try (Connection first = another(); Connection second = another();
             Statement a = first.createStatement(); Statement b = second.createStatement()) {
            a.execute("CREATE OR REPLACE TABLE tsr (a INT)");
            a.execute("INSERT INTO tsr VALUES (1), (2)");
            a.execute("CREATE OR REPLACE TABLE refs (r VARCHAR)");
            a.execute(READER);
            a.execute(KEEPER);
            a.execute("SET stok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT'))");
            final String session = cell(a, "SELECT $stok");
            steps(a, b, new String[][] {
                {"A", "SELECT COUNT(*) FROM IDENTIFIER('" + session + "')", "2"},
                {"B", "SELECT COUNT(*) FROM IDENTIFIER('" + session + "')", MISSING},
                {"B", "CALL pr_id('" + session + "')", MISSING_IN_CALL},
                {"C", "SELECT COUNT(*) FROM IDENTIFIER('" + session + "')", MISSING},
                {"A", "CALL pr_keep(SYSTEM$REFERENCE('TABLE', 'tsr', 'PERSISTENT', 'SELECT'))", "2"},
            });
            final String persistent = cell(a, "SELECT MAX(r) FROM refs");
            steps(a, b, new String[][] {
                {"A", "SELECT COUNT(*) FROM IDENTIFIER('" + persistent + "')", MISSING},
                {"B", "SELECT COUNT(*) FROM IDENTIFIER('" + persistent + "')", MISSING},
                {"B", "CALL pr_id('" + persistent + "')", MISSING_IN_CALL},
                {"B", "SET btok = (SELECT SYSTEM$REFERENCE('TABLE', 'tsr', 'SESSION', 'SELECT'))", "OK"},
                {"B", "SELECT COUNT(*) FROM IDENTIFIER($btok)", "2"},
            });
            final String other = cell(b, "SELECT $btok");
            first.close();
            steps(b, b, new String[][] {
                {"B", "SELECT COUNT(*) FROM IDENTIFIER('" + session + "')", MISSING},
                {"B", "SELECT COUNT(*) FROM IDENTIFIER('" + other + "')", "2"},
            });
        }
        verify();
    }
}

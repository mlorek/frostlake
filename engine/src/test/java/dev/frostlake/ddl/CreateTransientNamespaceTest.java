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
package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.storage.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TRANSIENT on a DATABASE and on a SCHEMA — the modifier, and everything that must not lie about it.
 *
 * <p>★ ACCEPTING THE WORD IS THE SMALL HALF. A modifier that parses and is then dropped on the floor
 * trades a syntax error for a silent lie: the object reports itself permanent everywhere afterwards.
 * So the cells below are mostly READ-BACK cells, and they read the fact from BOTH metadata surfaces,
 * which spell it differently on purpose — SHOW puts the word TRANSIENT in an {@code options} column
 * that is otherwise empty, INFORMATION_SCHEMA answers YES/NO in {@code IS_TRANSIENT}.
 *
 * <p>★ TRANSIENCE IS INHERITED, NOT COPIED. Every schema of a transient database is transient,
 * PUBLIC included — so the flag cannot be just a record of what the CREATE said. A permanent schema
 * inside a transient database is not a thing that exists.
 *
 * <p>★ THE MODIFIER VOCABULARY IS NOT THE TABLE'S. A DATABASE takes TRANSIENT and nothing else:
 * {@code CREATE TEMPORARY DATABASE} is a syntax error on the word DATABASE. A SCHEMA parses three
 * more spellings — TEMPORARY, TEMP, VOLATILE — and refuses each with a sentence that quotes the pair,
 * which is a different kind of answer from a syntax error and proves the word was read. LOCAL and
 * GLOBAL are the boundary: they are syntax errors before SCHEMA, though the TABLE grammar takes them.
 */
public class CreateTransientNamespaceTest extends BaseDatabaseTest {

    /** One scalar, as text, or the refusal — the shape shared with the rest of the DDL tests. */
    private String answer(final String sql) {
        try {
            final ResultSet rs = engine.executeQuery(sql);
            rs.next();
            return String.valueOf(rs.getValue(0));
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private void run(final String sql) {
        engine.execute(sql);
    }

    /**
     * One named column of a SHOW row, read through RAW JDBC when live.
     *
     * <p>The live harness rewrites every non-SELECT result into a one-cell count, and SHOW is a
     * non-SELECT, so its columns are invisible through the normal path — the same blind spot the DDL
     * status row had to work around.
     */
    private String showCell(final String sql, final String column) {
        if (LiveSnowflake.enabled()) {
            try {
                final Connection connection = LiveSnowflake.shared();
                final Statement st = connection.createStatement();
                final java.sql.ResultSet rs = st.executeQuery(sql);
                String value = "<no row>";
                if (rs.next()) {
                    value = String.valueOf(rs.getString(column));
                }
                rs.close();
                st.close();
                return value;
            } catch (final SQLException e) {
                return "REFUSED " + String.valueOf(e.getMessage()).replace('\n', '|');
            }
        }
        final ResultSet rs = engine.executeQuery(sql);
        if (rs.getRows().isEmpty()) {
            return "<no row>";
        }
        for (int c = 0; c < rs.getColumns().size(); c++) {
            if (rs.getColumns().get(c).getName().equalsIgnoreCase(column)) {
                return String.valueOf(rs.getRows().get(0).getValue(c));
            }
        }
        return "<no column>";
    }

    /** Live is stateful: anything created outside test_db has to go, or a rerun collides. */
    @AfterEach
    public void dropWhatWasCreated() {
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
        engine.execute("DROP DATABASE IF EXISTS tn_db");
        engine.execute("DROP DATABASE IF EXISTS tn_db_clone");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
    }

    /** ★ A transient SCHEMA is created, activated, and says so on both surfaces. */
    @Test
    public void atransientSchemaIsCreatedAndReportsItself() {
        run("CREATE TRANSIENT SCHEMA tn_s");
        assertEquals("TN_S", answer("SELECT CURRENT_SCHEMA()"),
            "a transient schema is activated like any other");
        assertEquals("TRANSIENT", showCell("SHOW SCHEMAS LIKE 'TN_S'", "options"),
            "SHOW spells transience as a WORD in the options column");
        assertEquals("YES", answer(
            "SELECT IS_TRANSIENT FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'TN_S'"),
            "and INFORMATION_SCHEMA spells the same fact YES/NO");
    }

    /** The permanent schema beside it — the cell that keeps the flag from being always-on. */
    @Test
    public void apermanentSchemaSaysNothingThere() {
        run("CREATE SCHEMA tn_p");
        assertEquals("", showCell("SHOW SCHEMAS LIKE 'TN_P'", "options"),
            "a permanent object leaves options EMPTY rather than naming a kind");
        assertEquals("NO", answer(
            "SELECT IS_TRANSIENT FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME = 'TN_P'"));
    }

    /** ★ A transient DATABASE, and the schemas it brings with it. */
    @Test
    public void atransientDatabaseMakesItsSchemasTransient() {
        run("CREATE TRANSIENT DATABASE tn_db");
        assertEquals("TN_DB", answer("SELECT CURRENT_DATABASE()"));
        assertEquals("PUBLIC", answer("SELECT CURRENT_SCHEMA()"),
            "a new database activates with PUBLIC current");
        assertEquals("TRANSIENT", showCell("SHOW DATABASES LIKE 'TN_DB'", "options"));
        assertEquals("TRANSIENT", showCell("SHOW SCHEMAS LIKE 'PUBLIC'", "options"),
            "★ PUBLIC came with the database and is transient too, though no statement said so");
        run("CREATE SCHEMA tn_inner");
        assertEquals("TRANSIENT", showCell("SHOW SCHEMAS LIKE 'TN_INNER'", "options"),
            "★ and a schema created inside INHERITS it — the modifier is not just what was written");
    }

    /** CLONE carries the modifier as the statement writes it. */
    @Test
    public void aclonedTransientKeepsTheModifier() {
        run("CREATE TRANSIENT DATABASE tn_db");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
        run("CREATE TRANSIENT DATABASE tn_db_clone CLONE tn_db");
        assertEquals("TRANSIENT", showCell("SHOW DATABASES LIKE 'TN_DB_CLONE'", "options"));
    }

    /**
     * ★ THE MODIFIER IS A RULE, NOT A LABEL: transient storage keeps at most one day.
     *
     * <p>This is the cell that makes the flag matter — the same ALTER succeeds or refuses depending
     * only on how the object was created, and on a schema, only on how its DATABASE was created. The
     * refusal is the bracketed invalid-value shape shared with every other out-of-range parameter,
     * echoing the number bare and naming neither the object nor the limit.
     */
    @Test
    public void atransientObjectIsCappedAtOneDayOfHistory() {
        run("CREATE TRANSIENT SCHEMA tn_s");
        run("ALTER SCHEMA tn_s SET DATA_RETENTION_TIME_IN_DAYS = 1");
        run("ALTER SCHEMA tn_s SET DATA_RETENTION_TIME_IN_DAYS = 0");
        assertEquals("SQL compilation error:|invalid value [2] for parameter"
            + " 'DATA_RETENTION_TIME_IN_DAYS'",
            answer("ALTER SCHEMA tn_s SET DATA_RETENTION_TIME_IN_DAYS = 2"),
            "two days is more history than transient storage keeps");
        assertEquals("SQL compilation error:|invalid value [2] for parameter"
            + " 'DATA_RETENTION_TIME_IN_DAYS'",
            answer("CREATE TRANSIENT SCHEMA tn_s2 DATA_RETENTION_TIME_IN_DAYS = 2"),
            "and the CREATE refuses the same value it would refuse on an ALTER");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
        // ★ The permanent schema beside it takes exactly what the transient one refused.
        run("CREATE SCHEMA tn_p DATA_RETENTION_TIME_IN_DAYS = 2");
        engine.execute("USE DATABASE test_db");
        engine.execute("USE SCHEMA test_schema");
    }

    /** ★ A transient DATABASE caps the schemas it contains, which never said TRANSIENT themselves. */
    @Test
    public void thecapReachesAnInheritedSchema() {
        run("CREATE TRANSIENT DATABASE tn_db");
        run("CREATE SCHEMA tn_inner");
        assertEquals("SQL compilation error:|invalid value [2] for parameter"
            + " 'DATA_RETENTION_TIME_IN_DAYS'",
            answer("ALTER SCHEMA tn_inner SET DATA_RETENTION_TIME_IN_DAYS = 2"),
            "the schema inherited transience, so it inherited the limit that comes with it");
        assertEquals("SQL compilation error:|invalid value [2] for parameter"
            + " 'DATA_RETENTION_TIME_IN_DAYS'",
            answer("ALTER DATABASE tn_db SET DATA_RETENTION_TIME_IN_DAYS = 2"));
    }

    /**
     * ★ The three spellings a SCHEMA READS but does not support — a refusal, not a syntax error.
     */
    @Test
    public void thethreeUnsupportedSchemaModifiers() {
        assertEquals("Unsupported feature 'TEMPORARY SCHEMA'.",
            answer("CREATE TEMPORARY SCHEMA tn_x"),
            "the sentence quotes the PAIR, which a parser that never read the word could not do");
        assertEquals("Unsupported feature 'TEMP SCHEMA'.", answer("CREATE TEMP SCHEMA tn_x"),
            "and it echoes the spelling as written — TEMP is not normalised to TEMPORARY");
        assertEquals("Unsupported feature 'VOLATILE SCHEMA'.", answer("CREATE VOLATILE SCHEMA tn_x"));
    }

    /** ★ The boundary: what is a syntax error instead, on both sides of the SCHEMA/DATABASE split. */
    @Test
    public void themodifiersThatAreSyntaxErrorsInstead() {
        assertEquals("SQL compilation error:|syntax error line 1 at position 17 unexpected 'DATABASE'.",
            answer("CREATE TEMPORARY DATABASE tn_x"),
            "★ a DATABASE takes TRANSIENT and nothing else, so the error lands on DATABASE itself");
        assertEquals("SQL compilation error:|syntax error line 1 at position 23 unexpected 'SCHEMA'.",
            answer("CREATE LOCAL TEMPORARY SCHEMA tn_x"),
            "LOCAL is a table modifier: before SCHEMA it does not parse at all");
        assertEquals("SQL compilation error:|syntax error line 1 at position 24 unexpected 'SCHEMA'.",
            answer("CREATE GLOBAL TEMPORARY SCHEMA tn_x"));
    }
}

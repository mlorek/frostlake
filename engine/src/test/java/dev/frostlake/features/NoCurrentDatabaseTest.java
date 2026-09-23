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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A session whose current database was dropped has none: CURRENT_DATABASE() and CURRENT_SCHEMA() are NULL.
 * A statement that creates, changes or drops an object of the current schema is refused naming what it
 * does, with no compilation-error prefix:
 *
 * <pre>
 *   Cannot perform CREATE TABLE. This session does not have a current database. Call 'USE DATABASE', or use a qualified name.
 * </pre>
 *
 * A bare name that a query or a DML statement looks up simply misses. A schema-qualified one is refused
 * naming the lookup, and a fully qualified one works as it always does. Every cell is live-verified.
 */
public class NoCurrentDatabaseTest extends BaseDatabaseTest {

    private static final String ACCEPTED = "<accepted>";
    private static final String UNCAUGHT = "Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position ";

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE test_db.test_schema.src (a INT)");
        engine.execute("CREATE TABLE test_db.test_schema.other (a INT)");
        engine.execute("CREATE TABLE test_db.test_schema.upd (a INT)");
        engine.execute("INSERT INTO test_db.test_schema.upd VALUES (1)");
        engine.execute("CREATE VIEW test_db.test_schema.v AS SELECT 1 AS a");
        engine.execute("CREATE OR REPLACE DATABASE ncd_idle");
        engine.execute("DROP DATABASE ncd_idle");
    }

    /** What the statement answered: its refusal, newlines shown as '|', or {@link #ACCEPTED}. */
    private String outcome(final String sql) {
        try {
            engine.executeQuery(sql);
            return ACCEPTED;
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private String firstRow(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final StringBuilder sb = new StringBuilder();
        for (final Object value : row.getValues()) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(value);
        }
        return sb.toString();
    }

    /** Each {statement, outcome} pair, every mismatch reported at once. */
    private void assertOutcomes(final String[][] cells) {
        final List<String> wrong = new ArrayList<String>();
        for (final String[] cell : cells) {
            final String expected = hinted(cell[1]);
            final String got = outcome(cell[0]);
            if (!expected.equals(got)) {
                wrong.add(cell[0] + "\n    expected: " + expected + "\n    got:      " + got);
            }
        }
        assertTrue(wrong.isEmpty(), String.join("\n", wrong));
    }

    private static String cannotPerform(final String kind) {
        return "Cannot perform " + kind + ". This session does not have a current database."
            + " Call 'USE DATABASE', or use a qualified name.";
    }

    private static String[] refused(final String sql, final String kind) {
        return new String[] {sql, cannotPerform(kind)};
    }

    private static String[] missing(final String sql, final String kind, final String name) {
        return new String[] {sql, "SQL compilation error:|" + kind + " '" + name + "' does not exist or not authorized."};
    }

    private static String[] answered(final String sql, final String outcome) {
        return new String[] {sql, outcome};
    }

    @Test
    public void theSessionHasNoCurrentDatabase() {
        assertEquals("null | null", firstRow("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"));
        assertEquals("1", firstRow("SELECT 1"));
    }

    /** A CREATE names the object's words; a temporary object glues TEMP to the first of them. */
    @Test
    public void aCreateIsRefusedNamingItsObject() {
        assertOutcomes(new String[][] {
            refused("CREATE TABLE t_after_drop (a INT)", "CREATE TABLE"),
            refused("CREATE OR REPLACE TABLE t376 (a INT)", "CREATE TABLE"),
            refused("CREATE TABLE t376 AS SELECT 1 AS a", "CREATE TABLE"),
            refused("CREATE TRANSIENT TABLE t1 (a INT)", "CREATE TABLE"),
            refused("CREATE TABLE IF NOT EXISTS t376 (a INT)", "CREATE TABLE"),
            refused("CREATE TABLE PUBLIC.t9 (a INT)", "CREATE TABLE"),
            refused("CREATE TABLE t5 LIKE test_db.test_schema.src", "CREATE TABLE"),
            refused("CREATE TABLE t6 CLONE no_such_db.PUBLIC.t", "CREATE TABLE"),
            refused("CREATE TEMPORARY TABLE t376 (a INT)", "CREATE TEMPTABLE"),
            refused("CREATE LOCAL TEMPORARY TABLE t2 (a INT)", "CREATE TEMPTABLE"),
            refused("CREATE GLOBAL TEMPORARY TABLE t3 (a INT)", "CREATE TEMPTABLE"),
            refused("CREATE VOLATILE TABLE t4 (a INT)", "CREATE TEMPTABLE"),
            refused("CREATE OR REPLACE TEMP TABLE tt (a INT)", "CREATE TEMPTABLE"),
            refused("CREATE HYBRID TABLE ht (a INT PRIMARY KEY)", "CREATE HYBRID TABLE"),
            refused("CREATE VIEW v376 AS SELECT 1 AS a", "CREATE VIEW"),
            refused("CREATE SECURE VIEW sv AS SELECT 1 AS a", "CREATE VIEW"),
            refused("CREATE TEMPORARY VIEW tv AS SELECT 1 AS a", "CREATE TEMPVIEW"),
            refused("CREATE MATERIALIZED VIEW mv AS SELECT 1 AS a", "CREATE MATERIALIZED VIEW"),
            refused("CREATE SECURE MATERIALIZED VIEW smv AS SELECT 1 AS a", "CREATE MATERIALIZED VIEW"),
            refused("CREATE DYNAMIC TABLE dt TARGET_LAG = '1 minute' WAREHOUSE = COMPUTE_WH AS SELECT 1 AS a",
                "CREATE DYNAMIC TABLE"),
            refused("CREATE SCHEMA s376", "CREATE SCHEMA"),
            refused("CREATE SEQUENCE seq376", "CREATE SEQUENCE"),
            refused("CREATE SEQUENCE PUBLIC.sq", "CREATE SEQUENCE"),
            refused("CREATE STAGE st376", "CREATE STAGE"),
            refused("CREATE TEMPORARY STAGE st", "CREATE TEMPSTAGE"),
            refused("CREATE FILE FORMAT ff376 TYPE = CSV", "CREATE FILE FORMAT"),
            refused("CREATE TEMPORARY FILE FORMAT ff TYPE = CSV", "CREATE TEMPFILE FORMAT"),
            refused("CREATE FUNCTION f376() RETURNS INT AS '1'", "CREATE FUNCTION"),
            refused("CREATE SECURE FUNCTION sf() RETURNS INT AS '1'", "CREATE FUNCTION"),
            refused("CREATE TEMPORARY FUNCTION tf() RETURNS INT AS '1'", "CREATE TEMPFUNCTION"),
            refused("CREATE PROCEDURE p376() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$",
                "CREATE PROCEDURE"),
            refused("CREATE TEMPORARY PROCEDURE tp() RETURNS INT LANGUAGE SQL AS $$ BEGIN RETURN 1; END; $$",
                "CREATE TEMPPROCEDURE"),
            refused("CREATE STREAM s376 ON TABLE t", "CREATE STREAM"),
            refused("CREATE TASK tk376 AS SELECT 1", "CREATE TASK"),
            refused("CREATE PIPE pp AS COPY INTO t FROM @st", "CREATE PIPE"),
            refused("CREATE TAG tg", "CREATE TAG"),
            refused("CREATE MASKING POLICY mp AS (v VARCHAR) RETURNS VARCHAR -> v", "CREATE MASKING POLICY"),
            refused("CREATE ROW ACCESS POLICY rp AS (v VARCHAR) RETURNS BOOLEAN -> TRUE", "CREATE ROW ACCESS POLICY"),
            refused("CREATE PROJECTION POLICY pp AS () RETURNS PROJECTION_CONSTRAINT"
                + " -> PROJECTION_CONSTRAINT(ALLOW => true)", "CREATE PROJECTION POLICY"),
            refused("CREATE AGGREGATION POLICY ap AS () RETURNS AGGREGATION_CONSTRAINT"
                + " -> NO_AGGREGATION_CONSTRAINT()", "CREATE AGGREGATION POLICY"),
            refused("CREATE JOIN POLICY jp AS () RETURNS JOIN_CONSTRAINT"
                + " -> JOIN_CONSTRAINT(JOIN_REQUIRED => FALSE)", "CREATE JOIN POLICY"),
            refused("UNDROP TABLE t", "CREATE TABLE"),
            refused("UNDROP TABLE PUBLIC.t3", "CREATE TABLE"),
            refused("UNDROP SCHEMA s9", "CREATE SCHEMA"),
            refused("UNDROP TAG tg", "CREATE TAG"),
        });
    }

    /** An ALTER is an ALTER, a RENAME, or the name live manages the action under. */
    @Test
    public void anAlterIsRefusedNamingWhatItDoes() {
        final String operate = "ALTER DYNAMIC TABLE OPERATE PROPERTY";
        final String rowAccess = "ALTER MANAGE ROW ACCESS POLICY";
        assertOutcomes(new String[][] {
            refused("ALTER TABLE t ADD COLUMN b INT", "ALTER"),
            refused("ALTER TABLE PUBLIC.t ADD COLUMN b INT", "ALTER"),
            refused("ALTER TABLE IF EXISTS t SET COMMENT = 'x'", "ALTER"),
            refused("ALTER TABLE t ADD COLUMN IF NOT EXISTS b INT", "ALTER"),
            refused("ALTER TABLE t DROP COLUMN a", "ALTER"),
            refused("ALTER TABLE t DROP COLUMN IF EXISTS a", "ALTER"),
            refused("ALTER TABLE t ALTER COLUMN a SET NOT NULL", "ALTER"),
            refused("ALTER TABLE t ALTER COLUMN a SET DATA TYPE INT", "ALTER"),
            refused("ALTER TABLE t MODIFY COLUMN a COMMENT 'x'", "ALTER"),
            refused("ALTER TABLE t CLUSTER BY (a)", "ALTER"),
            refused("ALTER TABLE t DROP CLUSTERING KEY", "ALTER"),
            refused("ALTER TABLE t SUSPEND RECLUSTER", "ALTER"),
            refused("ALTER TABLE t UNSET COMMENT", "ALTER"),
            refused("ALTER TABLE t SET DATA_RETENTION_TIME_IN_DAYS = 1", "ALTER"),
            refused("ALTER TABLE t SET CHANGE_TRACKING = TRUE", "ALTER"),
            refused("ALTER TABLE t ADD CONSTRAINT pk PRIMARY KEY (a)", "ALTER"),
            refused("ALTER TABLE t DROP CONSTRAINT pk", "ALTER"),
            refused("ALTER TABLE t DROP PRIMARY KEY", "ALTER"),
            refused("ALTER TABLE t DROP UNIQUE (a)", "ALTER"),
            refused("ALTER TABLE t DROP SEARCH OPTIMIZATION", "ALTER"),
            refused("ALTER TABLE PUBLIC.t RENAME COLUMN a TO b", "ALTER"),
            refused("ALTER TABLE t RENAME TO t2", "RENAME"),
            refused("ALTER TABLE t SWAP WITH t2", "RENAME"),
            refused("ALTER VIEW v RENAME TO v2", "RENAME"),
            refused("ALTER SCHEMA s RENAME TO s2", "RENAME"),
            refused("ALTER MATERIALIZED VIEW mv RENAME TO mv2", "RENAME"),
            refused("ALTER STAGE st RENAME TO st2", "RENAME"),
            refused("ALTER FILE FORMAT ff RENAME TO ff2", "RENAME"),
            refused("ALTER FUNCTION f() RENAME TO f2", "RENAME"),
            refused("ALTER PROCEDURE p() RENAME TO p2", "RENAME"),
            refused("ALTER TAG tg RENAME TO tg2", "RENAME"),
            refused("ALTER MASKING POLICY mp RENAME TO mp2", "RENAME"),
            refused("ALTER SEQUENCE sq SET INCREMENT = 2", "ALTER"),
            refused("ALTER MATERIALIZED VIEW mv SUSPEND", "ALTER"),
            refused("ALTER DYNAMIC TABLE dt SUSPEND", operate),
            refused("ALTER DYNAMIC TABLE dt RESUME", operate),
            refused("ALTER DYNAMIC TABLE dt REFRESH", operate),
            refused("ALTER DYNAMIC TABLE dt SET TARGET_LAG = '5 minutes'", operate),
            refused("ALTER DYNAMIC TABLE dt SET WAREHOUSE = w", operate),
            refused("ALTER DYNAMIC TABLE dt SET COMMENT = 'x'", "ALTER"),
            refused("ALTER DYNAMIC TABLE dt SET DATA_RETENTION_TIME_IN_DAYS = 1", "ALTER"),
            refused("ALTER TASK tk SUSPEND", "OPERATE"),
            refused("ALTER TASK tk RESUME", "RESOLVE"),
            refused("ALTER TASK tk SET SCHEDULE = '5 MINUTE'", "ALTER"),
            refused("ALTER TASK tk UNSET COMMENT", "ALTER"),
            refused("ALTER TASK tk MODIFY AS SELECT 1", "ALTER"),
            refused("ALTER STREAM s SET COMMENT = 'x'", "ALTER"),
            refused("ALTER STREAM s UNSET COMMENT", "ALTER"),
            refused("ALTER PIPE pp SET PIPE_EXECUTION_PAUSED = TRUE", "ALTER"),
            refused("ALTER PIPE pp REFRESH", "ALTER"),
            refused("ALTER STAGE st SET COMMENT = 'x'", "ALTER"),
            refused("ALTER FUNCTION f() SET COMMENT = 'x'", "ALTER"),
            refused("ALTER VIEW v SET COMMENT = 'x'", "ALTER"),
            refused("ALTER SCHEMA s SET DATA_RETENTION_TIME_IN_DAYS = 1", "ALTER"),
            refused("ALTER TABLE t SET TAG tg = 'x'", "ALTER SET TAG"),
            refused("ALTER TABLE t UNSET TAG tg", "ALTER UNSET TAG"),
            refused("ALTER TABLE t MODIFY COLUMN a SET TAG tg = 'x'", "ALTER SET TAG"),
            refused("ALTER TABLE t MODIFY COLUMN a UNSET TAG tg", "ALTER UNSET TAG"),
            refused("ALTER VIEW v SET TAG tg = 'x'", "ALTER SET TAG"),
            refused("ALTER VIEW v UNSET TAG tg", "ALTER UNSET TAG"),
            refused("ALTER SCHEMA s SET TAG tg = 'x'", "ALTER SET TAG"),
            refused("ALTER SCHEMA s UNSET TAG tg", "ALTER UNSET TAG"),
            refused("ALTER TABLE t ADD ROW ACCESS POLICY rp ON (a)", rowAccess),
            refused("ALTER TABLE t DROP ROW ACCESS POLICY rp", rowAccess),
            refused("ALTER TABLE t DROP ALL ROW ACCESS POLICIES", rowAccess),
            refused("ALTER VIEW v ADD ROW ACCESS POLICY rp ON (a)", rowAccess),
            refused("ALTER VIEW v DROP ALL ROW ACCESS POLICIES", rowAccess),
            refused("ALTER TABLE t ALTER COLUMN a SET MASKING POLICY mp", "ALTER SET MASKING POLICY"),
            refused("ALTER TABLE t MODIFY COLUMN a SET MASKING POLICY mp", "ALTER SET MASKING POLICY"),
            refused("ALTER TABLE t ALTER COLUMN a UNSET MASKING POLICY", "ALTER UNSET MASKING POLICY"),
            refused("ALTER TABLE t ALTER COLUMN a SET PROJECTION POLICY pp", "ALTER MANAGE PROJECTION POLICY"),
            refused("ALTER TABLE t ALTER COLUMN a UNSET PROJECTION POLICY", "ALTER MANAGE PROJECTION POLICY"),
            refused("ALTER TABLE t SET AGGREGATION POLICY ap", "ALTER MANAGE AGGREGATION POLICY"),
            refused("ALTER TABLE t UNSET AGGREGATION POLICY", "ALTER MANAGE AGGREGATION POLICY"),
            refused("ALTER TABLE t SET JOIN POLICY jp", "ALTER MANAGE JOIN POLICY"),
            refused("ALTER TABLE t UNSET JOIN POLICY", "ALTER MANAGE JOIN POLICY"),
            refused("ALTER TABLE t SET CONTACT STEWARD = c", "ALTER MANAGE CONTACT"),
            refused("ALTER TABLE t UNSET CONTACT STEWARD", "ALTER MANAGE CONTACT"),
            refused("ALTER TABLE t ADD DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)",
                "ALTER MANAGE DATA METRIC FUNCTION SELECT CHECK"),
            refused("ALTER TABLE t DROP DATA METRIC FUNCTION SNOWFLAKE.CORE.NULL_COUNT ON (a)",
                "ALTER MANAGE DATA METRIC FUNCTION SELECT CHECK"),
            refused("ALTER TABLE t ADD SEARCH OPTIMIZATION", "CREATE SEARCH INDEX"),
            refused("COMMENT ON TABLE t IS 'x'", "ALTER"),
            refused("COMMENT IF EXISTS ON TABLE t IS 'x'", "ALTER"),
            refused("COMMENT ON COLUMN t.a IS 'x'", "ALTER"),
            refused("COMMENT ON COLUMN PUBLIC.t.a IS 'x'", "ALTER"),
        });
    }

    /** DROP and TRUNCATE name themselves, IF EXISTS notwithstanding. */
    @Test
    public void aDropOrATruncateIsRefusedNamingItsVerb() {
        assertOutcomes(new String[][] {
            refused("TRUNCATE TABLE t", "TRUNCATE"),
            refused("TRUNCATE t", "TRUNCATE"),
            refused("TRUNCATE TABLE IF EXISTS PUBLIC.t", "TRUNCATE"),
            refused("DROP TABLE t2", "DROP"),
            refused("DROP TABLE IF EXISTS t376", "DROP"),
            refused("DROP VIEW v376", "DROP"),
            refused("DROP SCHEMA s376", "DROP"),
            refused("DROP SCHEMA IF EXISTS s9", "DROP"),
            refused("DROP FUNCTION f()", "DROP"),
            refused("DROP PROCEDURE p()", "DROP"),
            refused("DROP SEQUENCE sq", "DROP"),
            refused("DROP STAGE st", "DROP"),
            refused("DROP FILE FORMAT ff", "DROP"),
            refused("DROP MATERIALIZED VIEW mv", "DROP"),
            refused("DROP DYNAMIC TABLE dt", "DROP"),
            refused("DROP STREAM s", "DROP"),
            refused("DROP TASK tk", "DROP"),
            refused("DROP PIPE pp", "DROP"),
            refused("DROP TAG tg", "DROP"),
            refused("DROP MASKING POLICY mp", "DROP"),
            refused("DROP ROW ACCESS POLICY rp", "DROP"),
        });
    }

    /** RENAME COLUMN and RENAME CONSTRAINT look a bare table name up, and miss it. */
    @Test
    public void renamingAColumnLooksItsTableUp() {
        assertOutcomes(new String[][] {
            missing("ALTER TABLE t RENAME COLUMN a TO b", "Table", "T"),
            missing("ALTER TABLE t RENAME CONSTRAINT c1 TO c2", "Table", "T"),
        });
    }

    /** A bare name that a query or a DML statement looks up names nothing, and misses as any name would. */
    @Test
    public void aBareNameALookupMakesMissesAsAnyNameWould() {
        assertOutcomes(new String[][] {
            missing("SELECT * FROM no_such_table", "Object", "NO_SUCH_TABLE"),
            missing("SELECT * FROM t", "Object", "T"),
            missing("UPDATE t SET a = 1", "Object", "T"),
            missing("DELETE FROM t", "Object", "T"),
            missing("MERGE INTO t USING (SELECT 1 AS a) s ON t.a = s.a WHEN MATCHED THEN DELETE", "Object", "T"),
            missing("INSERT INTO t VALUES (1)", "Table", "T"),
            missing("DESC TABLE t", "Table", "T"),
            missing("DESCRIBE TABLE t TYPE = STAGE", "Table", "T"),
            missing("DESCRIBE VIEW v376", "View", "V376"),
            missing("CREATE TABLE test_db.test_schema.t7 LIKE t", "Object", "T"),
            missing("CREATE TABLE test_db.test_schema.t8 CLONE t", "Object", "T"),
            missing("CREATE TABLE test_db.test_schema.t4 (a INT) AS SELECT * FROM t", "Object", "T"),
            answered("COPY INTO t FROM @st", "SQL compilation error:|Table 'T' does not exist"),
            answered("CALL p376()", "SQL compilation error:|Unknown function P376."),
            answered("SELECT f376()", "SQL compilation error:|Unknown function F376."),
            answered("SELECT seq376.NEXTVAL",
                "SQL compilation error: error line 1 at position 7|invalid identifier 'SEQ376.NEXTVAL'"),
        });
    }

    /** A schema-qualified name needs the database, and the refusal names the lookup, not the statement. */
    @Test
    public void aSchemaQualifiedLookupIsRefusedNamingTheLookup() {
        assertOutcomes(new String[][] {
            refused("SELECT * FROM PUBLIC.t2", "SELECT"),
            refused("SELECT COUNT(*) FROM PUBLIC.t", "SELECT"),
            refused("SELECT * FROM (SELECT * FROM PUBLIC.t)", "SELECT"),
            refused("WITH c AS (SELECT * FROM PUBLIC.t) SELECT * FROM c", "SELECT"),
            refused("SELECT * FROM test_db.test_schema.src, PUBLIC.u", "SELECT"),
            refused("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE 1 = 0", "SELECT"),
            refused("UPDATE PUBLIC.t SET a = 1", "SELECT"),
            refused("DELETE FROM PUBLIC.t", "SELECT"),
            refused("MERGE INTO PUBLIC.t USING (SELECT 1 AS a) s ON t.a = s.a WHEN MATCHED THEN DELETE", "SELECT"),
            refused("UPDATE test_db.test_schema.upd SET a = (SELECT MAX(a) FROM PUBLIC.t)", "SELECT"),
            refused("DELETE FROM test_db.test_schema.src USING PUBLIC.u WHERE src.a = u.a", "SELECT"),
            refused("MERGE INTO test_db.test_schema.src USING PUBLIC.u ON src.a = u.a WHEN MATCHED THEN DELETE",
                "SELECT"),
            refused("INSERT INTO PUBLIC.t VALUES (1)", "INSERT"),
            refused("INSERT INTO PUBLIC.t (a) VALUES (1)", "INSERT"),
            refused("INSERT INTO PUBLIC.t SELECT 1", "INSERT"),
            refused("INSERT INTO PUBLIC.t SELECT * FROM test_db.test_schema.src", "INSERT"),
            refused("INSERT ALL INTO PUBLIC.t SELECT 1", "INSERT"),
            refused("INSERT INTO test_db.test_schema.src SELECT * FROM PUBLIC.t", "SELECT"),
            refused("CREATE TABLE test_db.test_schema.t5 AS SELECT * FROM PUBLIC.t", "SELECT"),
            refused("CREATE TABLE test_db.test_schema.t6 CLONE PUBLIC.t", "CLONE"),
            refused("CREATE TABLE test_db.test_schema.t8 LIKE PUBLIC.t", "DUPLICATE"),
            refused("DESC TABLE PUBLIC.t", "DESCRIBE"),
            refused("DESCRIBE VIEW PUBLIC.v", "DESCRIBE"),
            refused("ALTER TABLE test_db.test_schema.src RENAME TO src3", "CREATE TABLE"),
            refused("ALTER TABLE test_db.test_schema.src RENAME TO PUBLIC.t9", "CREATE TABLE"),
            refused("ALTER VIEW test_db.test_schema.v RENAME TO v4", "CREATE VIEW"),
            refused("ALTER TABLE test_db.test_schema.src SWAP WITH other", "RENAME"),
            answered("CALL PUBLIC.p()", "SQL compilation error:|Unknown user-defined function PUBLIC.P."),
            answered("SELECT PUBLIC.f376()", "SQL compilation error:|Unknown user-defined function PUBLIC.F376."),
            answered("SELECT PUBLIC.seq.NEXTVAL",
                "SQL compilation error: error line 1 at position 7|invalid identifier 'PUBLIC.SEQ.NEXTVAL'"),
            answered("COPY INTO PUBLIC.t FROM @st", "SQL compilation error:|Table 'PUBLIC.T' does not exist"),
            answered("USE SCHEMA PUBLIC", "SQL compilation error:|Object does not exist, or operation cannot be performed."),
            answered("SHOW TABLES IN SCHEMA PUBLIC",
                "SQL compilation error:|Object does not exist, or operation cannot be performed."),
        });
    }

    /** A fully qualified name places itself, and every statement works as it would anywhere. */
    @Test
    public void aFullyQualifiedNameNeedsNoCurrentDatabase() {
        assertOutcomes(new String[][] {
            missing("SELECT * FROM test_db.test_schema.nosuch", "Object", "TEST_DB.TEST_SCHEMA.NOSUCH"),
            answered("CREATE TABLE no_such_db.PUBLIC.t (a INT)",
                "SQL compilation error:|Database 'NO_SUCH_DB' does not exist or not authorized."),
            answered("CREATE TABLE test_db.test_schema.t2 (a INT)", ACCEPTED),
            answered("CREATE TABLE test_db.test_schema.t3 AS SELECT 1 AS a", ACCEPTED),
            answered("CREATE TABLE test_db.test_schema.t10 CLONE test_db.test_schema.src", ACCEPTED),
            answered("CREATE TABLE test_db.test_schema.t11 LIKE test_db.test_schema.src", ACCEPTED),
            answered("CREATE SCHEMA test_db.s2", ACCEPTED),
            answered("CREATE SEQUENCE test_db.test_schema.sq9", ACCEPTED),
            answered("INSERT INTO test_db.test_schema.src SELECT * FROM test_db.test_schema.t3", ACCEPTED),
            answered("TRUNCATE TABLE test_db.test_schema.t3", ACCEPTED),
            answered("COMMENT ON TABLE test_db.test_schema.src IS 'x'", ACCEPTED),
            answered("COMMENT ON COLUMN test_db.test_schema.src.a IS 'x'", ACCEPTED),
            answered("ALTER TABLE test_db.test_schema.src ALTER COLUMN a COMMENT 'y'", ACCEPTED),
            answered("ALTER SCHEMA test_db.s2 SET COMMENT = 'x'", ACCEPTED),
            answered("ALTER TABLE test_db.test_schema.src SWAP WITH test_db.test_schema.other", ACCEPTED),
            answered("ALTER TABLE test_db.test_schema.t10 RENAME TO test_db.test_schema.t12", ACCEPTED),
            answered("DROP TABLE test_db.test_schema.t12", ACCEPTED),
            answered("DROP DATABASE IF EXISTS ncd_nosuch", ACCEPTED),
        });
        assertEquals("1", firstRow("SELECT COUNT(*) FROM test_db.test_schema.other"), "the swap took the row");
    }

    /** A script's statements are named the same, inside the uncaught-exception envelope. */
    @Test
    public void aScriptSeesTheSameSentences() {
        assertOutcomes(new String[][] {
            refused("EXECUTE IMMEDIATE 'CREATE TABLE t (a INT)'", "CREATE TABLE"),
            answered("EXECUTE IMMEDIATE $$ BEGIN CREATE TABLE t (a INT); END; $$",
                UNCAUGHT + "7 : " + cannotPerform("CREATE TABLE")),
            answered("EXECUTE IMMEDIATE $$ BEGIN SELECT * FROM PUBLIC.t; END; $$",
                UNCAUGHT + "7 : " + cannotPerform("SELECT")),
            answered("EXECUTE IMMEDIATE $$ DECLARE c INT; BEGIN SELECT COUNT(*) INTO :c FROM t; RETURN c; END; $$",
                UNCAUGHT + "22 : SQL compilation error:|Object 'T' does not exist or not authorized."),
        });
    }

    /** Dropping the current SCHEMA is different: the session falls back to PUBLIC and carries on. */
    @Test
    public void droppingTheCurrentSchemaFallsBackToPublic() {
        engine.execute("USE DATABASE test_db");
        engine.execute("CREATE OR REPLACE SCHEMA s_idle");
        engine.execute("DROP SCHEMA s_idle");
        assertEquals("TEST_DB | PUBLIC", firstRow("SELECT CURRENT_DATABASE(), CURRENT_SCHEMA()"));
        engine.execute("CREATE TABLE t_after_sdrop (a INT)");
        assertEquals("0", firstRow("SELECT COUNT(*) FROM t_after_sdrop"));
    }
}

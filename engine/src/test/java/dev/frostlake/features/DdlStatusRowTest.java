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

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.LiveSnowflake;
import dev.frostlake.storage.ResultSet;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

/**
 * A statement that produces no rows of its own still ANSWERS ONE: a VARCHAR column named {@code status}
 * carrying a sentence. Frostlake returned nothing at all, so a client reading the first result set of a
 * CREATE saw an empty set where a real account hands back "Table T successfully created."
 *
 * <p>★ THIS TEST OPENS ITS OWN CONNECTION IN LIVE MODE, deliberately. BaseDatabaseTest's live harness
 * REWRITES every non-SELECT result into a one-cell count named from the first lexer token, so asking it
 * would compare Frostlake against the harness rather than against Snowflake — the live-rewrite blind spot
 * this surface sits in. The sentences below were read off a raw JDBC connection, and that is how they
 * are re-checked.
 *
 * <p>★ ONLY CREATE NAMES THE KIND, and two of the kind words are surprises: a STAGE is a "Stage area"
 * and a PROCEDURE is a "Function". DROP names no kind at all, and everything else — USE, ALTER, TRUNCATE
 * — is one flat sentence.
 *
 * <p>★ THE SPLIT BETWEEN execute AND executeQuery IS LIVE'S OWN: through {@code Statement.execute} the
 * driver reports no result set and an update count of 0 for the same DDL. Only the query path
 * materialises the row, which is the path this asserts.
 */
public class DdlStatusRowTest extends BaseDatabaseTest {

    /** The status column's name and its single value, from whichever engine is under test. */
    private String statusOf(final String sql) {
        if (LiveSnowflake.enabled()) {
            return liveStatusOf(sql);
        }
        final ResultSet rs = engine.executeQuery(sql);
        return rs.getColumns().get(0).getName() + ": " + String.valueOf(rs.getRows().get(0).getValue(0));
    }

    private String liveStatusOf(final String sql) {
        try {
            final Connection connection = LiveSnowflake.shared();
            final Statement st = connection.createStatement();
            final java.sql.ResultSet rs = st.executeQuery(sql);
            final String column = rs.getMetaData().getColumnName(1);
            rs.next();
            final String value = rs.getString(1);
            rs.close();
            st.close();
            return column + ": " + value;
        } catch (final SQLException e) {
            throw new IllegalStateException(sql + " failed on live: " + e.getMessage(), e);
        }
    }

    @Test
    public void aCreateNamesTheKindAndTheObject() {
        assertEquals("status: Table DS_T successfully created.",
            statusOf("CREATE OR REPLACE TABLE ds_t (a INT)"));
        assertEquals("status: View DS_V successfully created.",
            statusOf("CREATE OR REPLACE VIEW ds_v AS SELECT * FROM ds_t"));
        assertEquals("status: Sequence DS_SQ successfully created.",
            statusOf("CREATE OR REPLACE SEQUENCE ds_sq"));
        // A stage is a "Stage area" — two words, and neither of them derivable.
        assertEquals("status: Stage area DS_STG successfully created.",
            statusOf("CREATE OR REPLACE STAGE ds_stg"));
        assertEquals("status: File format DS_FF successfully created.",
            statusOf("CREATE OR REPLACE FILE FORMAT ds_ff TYPE = CSV"));
    }

    @Test
    public void aQuotedNameKeepsItsCaseAndAnUnquotedOneIsUpperCased() {
        assertEquals("status: Table ds_lower successfully created.",
            statusOf("CREATE OR REPLACE TABLE \"ds_lower\" (a INT)"));
    }

    @Test
    public void temporaryTransientAndCtasAreAllJustTables() {
        assertEquals("status: Table DS_TRN successfully created.",
            statusOf("CREATE OR REPLACE TRANSIENT TABLE ds_trn (a INT)"));
        assertEquals("status: Table DS_CTAS successfully created.",
            statusOf("CREATE OR REPLACE TABLE ds_ctas AS SELECT 1 AS a"));
    }

    @Test
    public void aDropNamesNoKind() {
        engine.execute("CREATE OR REPLACE TABLE ds_gone (a INT)");
        if (LiveSnowflake.enabled()) {
            liveStatusOf("CREATE OR REPLACE TABLE ds_gone (a INT)");
        }
        assertEquals("status: DS_GONE successfully dropped.", statusOf("DROP TABLE ds_gone"));
    }

    @Test
    public void everythingElseIsOneFlatSentence() {
        engine.execute("CREATE OR REPLACE TABLE ds_alter (a INT)");
        if (LiveSnowflake.enabled()) {
            liveStatusOf("CREATE OR REPLACE TABLE ds_alter (a INT)");
        }
        assertEquals("status: Statement executed successfully.",
            statusOf("ALTER TABLE ds_alter ADD COLUMN b VARCHAR"));
        assertEquals("status: Statement executed successfully.", statusOf("TRUNCATE TABLE ds_alter"));
        assertEquals("status: Statement executed successfully.",
            statusOf("ALTER SESSION SET TIMEZONE = 'UTC'"));
        assertEquals("status: Statement executed successfully.",
            statusOf("ALTER SESSION UNSET TIMEZONE"));
    }

    @Test
    public void aDmlStatementKeepsItsOwnCountColumn() {
        engine.execute("CREATE OR REPLACE TABLE ds_dml (a INT)");
        if (LiveSnowflake.enabled()) {
            liveStatusOf("CREATE OR REPLACE TABLE ds_dml (a INT)");
        }
        assertEquals("number of rows inserted: 2", statusOf("INSERT INTO ds_dml VALUES (1), (2)"));
    }

    /**
     * A qualified name reports its LAST part — the object's own name, canonical — whichever parts lead it:
     * a quoted last part keeps its case and its dots, and the conditional sentences name the same part.
     */
    @Test
    public void aQualifiedNameReportsTheObjectsOwnName() {
        assertEquals("status: Table DS_Q3 successfully created.",
            statusOf("CREATE OR REPLACE TABLE test_db.test_schema.ds_q3 (a INT)"));
        assertEquals("status: Table DS_Q2 successfully created.",
            statusOf("CREATE OR REPLACE TABLE test_schema.ds_q2 (a INT)"));
        assertEquals("status: Table lower successfully created.",
            statusOf("CREATE OR REPLACE TABLE test_db.test_schema.\"lower\" (a INT)"));
        assertEquals("status: View DS_QV successfully created.",
            statusOf("CREATE OR REPLACE VIEW test_db.test_schema.ds_qv AS SELECT 1 AS x"));
        assertEquals("status: Sequence DS_QS successfully created.",
            statusOf("CREATE OR REPLACE SEQUENCE test_schema.ds_qs"));
        assertEquals("status: DS_Q3 already exists, statement succeeded.",
            statusOf("CREATE TABLE IF NOT EXISTS test_db.test_schema.ds_q3 (a INT)"));
        assertEquals("status: Drop statement executed successfully (DS_NOSUCH already dropped).",
            statusOf("DROP TABLE IF EXISTS test_db.test_schema.ds_nosuch"));
        assertEquals("status: lower successfully dropped.", statusOf("DROP TABLE test_db.test_schema.\"lower\""));
        // Last, because a new schema becomes the current one.
        assertEquals("status: Function DS_QF successfully created.",
            statusOf("CREATE OR REPLACE FUNCTION test_db.test_schema.ds_qf() RETURNS INT AS '1'"));
        assertEquals("status: DS_QF successfully dropped.", statusOf("DROP FUNCTION test_db.test_schema.ds_qf()"));
        assertEquals("status: Stage area DS_QST successfully created.",
            statusOf("CREATE OR REPLACE STAGE test_db.test_schema.ds_qst"));
        assertEquals("status: DS_QST successfully dropped.", statusOf("DROP STAGE test_db.test_schema.ds_qst"));
        assertEquals("status: File format DS_QFF successfully created.",
            statusOf("CREATE OR REPLACE FILE FORMAT test_db.test_schema.ds_qff TYPE = CSV"));
        assertEquals("status: DS_QFF successfully dropped.",
            statusOf("DROP FILE FORMAT test_db.test_schema.ds_qff"));
        assertEquals("status: Table DS_QC successfully created.",
            statusOf("CREATE OR REPLACE TABLE test_db.test_schema.ds_qc CLONE test_db.test_schema.ds_q3"),
            "a CLONE's source is a later name");
        assertEquals("status: Table DS_QS2 successfully created.",
            statusOf("CREATE OR REPLACE TABLE test_db.test_schema.ds_qs2 AS SELECT a FROM test_db.test_schema.ds_q3"),
            "and so is a CTAS body's");
        assertEquals("status: DS_QV already exists, statement succeeded.",
            statusOf("CREATE VIEW IF NOT EXISTS test_db.test_schema.ds_qv AS SELECT 1 AS a"));
        assertEquals("status: Schema DS_QSCH successfully created.", statusOf("CREATE SCHEMA test_db.ds_qsch"));
    }
}

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

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CREATE TABLE … CLONE copies the whole table definition, and LIKE copies its shape. Both were hand-written partial
 * copies that rebuilt the columns from scratch. A clone of a protected table showed the values its masking policy
 * hides and the rows its row access policy filters, restarted its IDENTITY at START, and lost its tags, foreign keys,
 * CHECK constraints, search optimization, retention and change tracking. LIKE lost the IDENTITY definition and the
 * constraints the same way.
 */
public class TableCloneKeepsDefinitionTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE tc_db");
        engine.execute("USE SCHEMA tc_db.PUBLIC");
        engine.execute("CREATE MASKING POLICY mp AS (v STRING) RETURNS STRING -> '***'");
        engine.execute("CREATE ROW ACCESS POLICY rap AS (r STRING) RETURNS BOOLEAN -> r <> 'r2'");
        engine.execute("CREATE TAG t_pii");
        engine.execute("CREATE TABLE parent (id INT NOT NULL, CONSTRAINT pk_parent PRIMARY KEY (id))");
        engine.execute("CREATE TABLE t1 (id INT IDENTITY(10, 3), p_id INT, secret STRING WITH MASKING POLICY mp,"
            + " r STRING, CONSTRAINT fk_p FOREIGN KEY (p_id) REFERENCES parent (id), CONSTRAINT ck CHECK (p_id > 0))"
            + " DATA_RETENTION_TIME_IN_DAYS = 3 CHANGE_TRACKING = TRUE CLUSTER BY (r)");
        engine.execute("ALTER TABLE t1 ADD ROW ACCESS POLICY rap ON (r)");
        engine.execute("ALTER TABLE t1 SET TAG t_pii = 'yes'");
        engine.execute("ALTER TABLE t1 ADD SEARCH OPTIMIZATION ON EQUALITY(r)");
        engine.execute("INSERT INTO t1 (p_id, secret, r) VALUES (1, 's1', 'r1'), (1, 's2', 'r2')");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

    /** A query's rows, each one's values joined by '|', and the rows joined by " ; ". */
    private String rows(final String sql) {
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            out.append(out.length() > 0 ? " ; " : "");
            for (int i = 0; i < row.size(); i++) {
                out.append(i > 0 ? "|" : "").append(row.getValue(i));
            }
        }
        return out.toString();
    }

    private String refusal(final String sql) {
        try {
            engine.execute(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage());
        }
    }

    /** One column of SHOW TABLES for a table. */
    private String shown(final String table, final String column) {
        final ResultSet listed = engine.executeQuery("SHOW TABLES LIKE '" + table + "'");
        assertTrue(listed.next(), table + " is listed");
        return String.valueOf(listed.getValue(column));
    }

    @Test
    public void aCloneKeepsPoliciesIdentityConstraintsAndProperties() {
        engine.execute("CREATE TABLE t1c CLONE t1");
        engine.execute("INSERT INTO t1c (p_id, secret, r) VALUES (1, 's3', 'r3')");
        assertEquals("10|1|***|r1 ; 16|1|***|r3", rows("SELECT * FROM t1c ORDER BY id"),
            "masked, the hidden row filtered, and the IDENTITY sequence continued");
        final String ddl = rows("SELECT GET_DDL('TABLE', 't1c')");
        assertTrue(ddl.contains("autoincrement start 10 increment 3"), ddl);
        assertTrue(ddl.contains("WITH MASKING POLICY TC_DB.PUBLIC.MP"), ddl);
        assertTrue(ddl.contains("ROW ACCESS POLICY TC_DB.PUBLIC.RAP ON (R)"), ddl);
        assertTrue(ddl.contains("foreign key (P_ID) references TC_DB.PUBLIC.PARENT(ID)"), ddl);
        assertTrue(ddl.contains("constraint CK check (p_id > 0)"), ddl);
        assertTrue(ddl.contains("T_PII='yes'"), ddl);
        assertTrue(refusal("INSERT INTO t1c (p_id, secret, r) VALUES (-1, 'x', 'x')").contains("CHECK constraint CK"));
        assertEquals("3", shown("T1C", "retention_time"));
        assertEquals("ON", shown("T1C", "change_tracking"));
        assertEquals("ON", shown("T1C", "search_optimization"));
        assertEquals("OFF", shown("T1C", "automatic_clustering"), "a clone suspends automatic clustering (documented)");
    }

    @Test
    public void likeCopiesTheShapeButNoPolicyOrRows() {
        engine.execute("CREATE TABLE t1l LIKE t1");
        engine.execute("INSERT INTO t1l (p_id, secret, r) VALUES (1, 's3', 'r2')");
        assertEquals("10|1|s3|r2", rows("SELECT * FROM t1l"), "no masking, no row access, IDENTITY from START");
        final String ddl = rows("SELECT GET_DDL('TABLE', 't1l')");
        assertTrue(ddl.contains("autoincrement start 10 increment 3"), ddl);
        assertTrue(ddl.contains("foreign key (P_ID) references TC_DB.PUBLIC.PARENT(ID)"), ddl);
        assertTrue(ddl.contains("constraint CK check (p_id > 0)"), ddl);
        assertTrue(ddl.contains("T_PII='yes'"), ddl);
        assertFalse(ddl.contains("POLICY"), ddl);
        assertTrue(refusal("INSERT INTO t1l (p_id, secret, r) VALUES (-1, 'x', 'x')").contains("CHECK constraint CK"));
        assertEquals("FK_P", rows("SELECT CONSTRAINT_NAME FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS"
            + " WHERE TABLE_NAME = 'T1L'"));
    }
}

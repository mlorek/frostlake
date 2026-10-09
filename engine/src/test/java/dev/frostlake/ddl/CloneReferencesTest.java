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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A clone's foreign keys, sequence defaults and materialized views refer to what the documentation says they do.
 * The clone's own copy is used when the referenced object was cloned with them, and the source's object otherwise.
 * A clone used to keep every reference as written: one spelled with the source database's name went on reading
 * the source's sequence, parent table and base table. A table cloned into another schema read same-named objects
 * there instead of its source's.
 */
public class CloneReferencesTest {

    private DatabaseEngine engine;

    @BeforeEach
    public void setUp() {
        engine = new DatabaseEngine();
        engine.execute("CREATE DATABASE d1");
        engine.execute("CREATE SCHEMA d1.other");
        engine.execute("CREATE SCHEMA d1.s");
        engine.execute("CREATE SEQUENCE seq START 100");
        engine.execute("CREATE SEQUENCE d1.other.oseq START 500");
        engine.execute("CREATE TABLE parent (id INT NOT NULL, CONSTRAINT pk_parent PRIMARY KEY (id))");
        engine.execute("CREATE TABLE d1.other.oparent (id INT NOT NULL PRIMARY KEY)");
        engine.execute("CREATE TABLE child (id INT DEFAULT seq.NEXTVAL, p_id INT, o_id INT DEFAULT other.oseq.NEXTVAL,"
            + " CONSTRAINT fk_c FOREIGN KEY (p_id) REFERENCES parent(id),"
            + " CONSTRAINT fk_o FOREIGN KEY (o_id) REFERENCES other.oparent(id))");
        engine.execute("CREATE TABLE qualified (id INT DEFAULT d1.s.seq.NEXTVAL, p_id INT,"
            + " CONSTRAINT fk_q FOREIGN KEY (p_id) REFERENCES d1.s.parent(id))");
        engine.execute("CREATE TABLE base (a INT)");
        engine.execute("INSERT INTO base VALUES (1), (2)");
        engine.execute("CREATE MATERIALIZED VIEW mvq AS SELECT base.a FROM d1.s.base");
    }

    @AfterEach
    public void tearDown() {
        engine.shutdown();
    }

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

    /** A listing's rows, each one's columns joined by '.', in sorted order: SHOW lists tables in no fixed order. */
    private String listed(final String sql, final String... columns) {
        final ResultSet listing = engine.executeQuery(sql);
        final List<String> entries = new ArrayList<>();
        while (listing.next()) {
            final StringBuilder entry = new StringBuilder();
            for (int i = 0; i < columns.length; i++) {
                entry.append(i > 0 ? "." : "").append(listing.getValue(columns[i]));
            }
            entries.add(entry.toString());
        }
        Collections.sort(entries);
        return String.join(" ; ", entries);
    }

    private String ddl(final String table) {
        return rows("SELECT GET_DDL('TABLE', '" + table + "')");
    }

    @Test
    public void aDatabaseCloneRefersToItsOwnCopies() {
        engine.execute("CREATE DATABASE d2 CLONE d1");

        assertTrue(ddl("d2.s.qualified").contains("DEFAULT D2.S.SEQ.NEXTVAL"), ddl("d2.s.qualified"));
        engine.execute("INSERT INTO d2.s.qualified (p_id) VALUES (1)");
        assertEquals("100", rows("SELECT id FROM d2.s.qualified"), "the clone's own sequence");
        assertEquals("100", rows("SELECT d1.s.seq.NEXTVAL"), "the source's sequence is untouched");

        assertEquals("D2.OTHER.OPARENT.FK_O ; D2.S.PARENT.FK_C ; D2.S.PARENT.FK_Q",
            listed("SHOW IMPORTED KEYS IN SCHEMA d2.s", "pk_database_name", "pk_schema_name", "pk_table_name",
                "fk_name"));

        engine.execute("INSERT INTO d2.s.base VALUES (3)");
        assertEquals("D2.S.BASE", listed("SHOW MATERIALIZED VIEWS IN SCHEMA d2.s", "source_database_name",
            "source_schema_name", "source_table_name"));
        assertEquals("1 ; 2 ; 3", rows("SELECT a FROM d2.s.mvq ORDER BY a"), "the clone's base table");
        assertEquals("1 ; 2", rows("SELECT a FROM d1.s.mvq ORDER BY a"), "the source's view is untouched");
    }

    @Test
    public void aSchemaCloneKeepsReferencesToWhatItLeftBehind() {
        engine.execute("CREATE SCHEMA d1.s2 CLONE d1.s");
        assertTrue(ddl("d1.s2.qualified").contains("DEFAULT D1.S2.SEQ.NEXTVAL"), ddl("d1.s2.qualified"));
        assertTrue(ddl("d1.s2.child").contains("DEFAULT D1.OTHER.OSEQ.NEXTVAL"), "another schema's sequence stays");

        engine.execute("CREATE DATABASE d3");
        engine.execute("CREATE SCHEMA d3.sx CLONE d1.s");
        final String child = ddl("d3.sx.child");
        assertTrue(child.contains("DEFAULT D3.SX.SEQ.NEXTVAL"), child);
        assertTrue(child.contains("DEFAULT D1.OTHER.OSEQ.NEXTVAL"), child);
        assertTrue(child.contains("references D3.SX.PARENT(ID)"), child);
        assertTrue(child.contains("references D1.OTHER.OPARENT(ID)"), child);
        engine.execute("INSERT INTO d3.sx.child (p_id) VALUES (1)");
        assertEquals("100|500", rows("SELECT id, o_id FROM d3.sx.child"));
        assertEquals("501", rows("SELECT d1.other.oseq.NEXTVAL"), "the one sequence left behind was drawn from");
        assertEquals("D3.SX.BASE", listed("SHOW MATERIALIZED VIEWS IN SCHEMA d3.sx", "source_database_name",
            "source_schema_name", "source_table_name"));
    }

    @Test
    public void aTableClonedByItselfKeepsItsSourcesSequenceAndParent() {
        engine.execute("CREATE SEQUENCE d1.other.seq START 900");
        engine.execute("CREATE TABLE d1.other.cc CLONE d1.s.child");
        final String clone = ddl("d1.other.cc");
        assertTrue(clone.contains("DEFAULT D1.S.SEQ.NEXTVAL"), clone);
        assertTrue(clone.contains("references D1.S.PARENT(ID)"), clone);
        engine.execute("INSERT INTO d1.other.cc (p_id) VALUES (1)");
        assertEquals("100", rows("SELECT id FROM d1.other.cc"), "the source's sequence, not other.seq");
    }

    @Test
    public void aStoppedClonedPipeStaysStoppedThroughARestore() throws Exception {
        engine.execute("CREATE STAGE d1.s.ext_stage URL = 's3://bucket/path'");
        engine.execute("CREATE PIPE d1.s.p_auto AUTO_INGEST = TRUE AS COPY INTO d1.s.base FROM @d1.s.ext_stage");
        engine.execute("CREATE DATABASE d2 CLONE d1");
        final String stopped = "\"executionState\": \"STOPPED_CLONED\"";
        assertTrue(rows("SELECT SYSTEM$PIPE_STATUS('d2.s.p_auto')").contains(stopped));

        final DatabaseEngine restored = engine.cloneInstance();
        try {
            assertTrue(restored.executeQuery("SELECT SYSTEM$PIPE_STATUS('d2.s.p_auto')").getRows().get(0)
                .getValue(0).toString().contains(stopped), "a restore keeps the pipe stopped");
            restored.execute("ALTER PIPE d2.s.p_auto SET PIPE_EXECUTION_PAUSED = FALSE");
            assertTrue(restored.executeQuery("SELECT SYSTEM$PIPE_STATUS('d2.s.p_auto')").getRows().get(0)
                .getValue(0).toString().contains("RUNNING"), "and resuming it ends the stop");
        } finally {
            restored.shutdown();
        }
    }
}

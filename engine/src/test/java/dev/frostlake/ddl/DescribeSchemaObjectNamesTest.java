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
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DESCRIBE STREAM, TASK, PIPE and SEQUENCE name their object as a relation is named: bare, with its schema, with
 * its database and schema, quoted, or through IDENTIFIER(). The engine read a bare name only and called any other a
 * syntax error.
 *
 * <p>What the account answers when the name reaches nothing depends on the kind (live-verified): a task, a pipe
 * and a sequence are named in full whatever was written, a stream as it was written, bare for a bare name; a
 * schema or a database that is not there is refused by its own name, and a name of four parts as no object at all.
 * A schema's relations and its streams share one namespace, so a STREAM keyword over a table or a view, and a
 * relation keyword over a stream, reach an object of another kind: {@code Invalid object type: 'STREAM'}.
 */
public class DescribeSchemaObjectNamesTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE qt (id INTEGER)");
        engine.execute("CREATE VIEW qv AS SELECT id FROM qt");
        engine.execute("CREATE STREAM qst ON TABLE qt");
        engine.execute("CREATE SEQUENCE qseq START = 5 INCREMENT = 2 COMMENT = 'sq'");
        engine.execute("CREATE TASK qtask SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("CREATE STAGE qstage");
        engine.execute("CREATE PIPE qpipe AS COPY INTO qt FROM @qstage");
        engine.execute("CREATE SCHEMA other_schema");
        engine.execute("CREATE SEQUENCE other_schema.oseq");
        engine.execute("CREATE TABLE other_schema.ot (id INTEGER)");
        engine.execute("CREATE STREAM other_schema.ost ON TABLE other_schema.ot");
        engine.execute("CREATE TASK other_schema.otask SCHEDULE = '60 MINUTE' AS SELECT 1");
        engine.execute("USE SCHEMA test_db.test_schema");
    }

    /** The named column of the one row a DESCRIBE answers, with its schema: {@code SCHEMA.NAME}. */
    private String described(final String sql) {
        final ResultSet result = engine.executeQuery(sql);
        assertEquals(1, result.getRows().size(), sql);
        final Row row = result.getRows().get(0);
        return row.getValue(result.getColumnIndex("schema_name")) + "." + row.getValue(result.getColumnIndex("name"));
    }

    /** A refusal's own sentence: the message up to the privilege hint, which names the session's role. */
    private String refusal(final String sql) {
        try {
            engine.executeQuery(sql);
            return "not refused";
        } catch (final RuntimeException refused) {
            final String message = String.valueOf(refused.getMessage()).replace('\n', ' ');
            final int hint = message.indexOf(" Your primary role");
            return hint < 0 ? message : message.substring(0, hint);
        }
    }

    @Test
    public void aQualifiedNameIsReadWhereItSays() {
        for (final String[] kind : new String[][] {
            {"STREAM", "qst", "ost"}, {"TASK", "qtask", "otask"}, {"SEQUENCE", "qseq", "oseq"}}) {
            final String own = "TEST_SCHEMA." + kind[1].toUpperCase(Locale.ROOT);
            assertEquals(own, described("DESCRIBE " + kind[0] + " " + kind[1]));
            assertEquals(own, described("DESCRIBE " + kind[0] + " test_schema." + kind[1]));
            assertEquals(own, described("DESC " + kind[0] + " test_db.test_schema." + kind[1]));
            assertEquals(own, described("DESCRIBE " + kind[0] + " \"TEST_DB\".\"TEST_SCHEMA\".\""
                + kind[1].toUpperCase(Locale.ROOT) + "\""));
            assertEquals(own, described("DESCRIBE " + kind[0] + " IDENTIFIER('test_db.test_schema." + kind[1] + "')"));
            assertEquals("OTHER_SCHEMA." + kind[2].toUpperCase(Locale.ROOT),
                described("DESCRIBE " + kind[0] + " other_schema." + kind[2]));
        }
        assertEquals("TEST_SCHEMA.QPIPE", described("DESCRIBE PIPE qpipe"));
        assertEquals("TEST_SCHEMA.QPIPE", described("DESCRIBE PIPE test_schema.qpipe"));
        assertEquals("TEST_SCHEMA.QPIPE", described("DESC PIPE test_db.test_schema.qpipe"));
        assertEquals("TEST_SCHEMA.QPIPE", described("DESCRIBE PIPE IDENTIFIER('test_db.test_schema.qpipe')"));
    }

    @Test
    public void aMissingObjectIsNamedInFullAndAStreamAsItWasWritten() {
        for (final String kind : new String[] {"Task", "Pipe", "Sequence"}) {
            final String keyword = kind.toUpperCase(Locale.ROOT);
            final String full = "SQL compilation error: " + kind
                + " 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.";
            assertEquals(full, refusal("DESCRIBE " + keyword + " nosuch"));
            assertEquals(full, refusal("DESCRIBE " + keyword + " test_schema.nosuch"));
            assertEquals(full, refusal("DESCRIBE " + keyword + " test_db.test_schema.nosuch"));
            assertEquals("SQL compilation error: " + kind + " 'TEST_DB.TEST_SCHEMA.QT' does not exist or not"
                + " authorized.", refusal("DESCRIBE " + keyword + " qt"), "a table is no " + kind);
        }
        assertEquals("SQL compilation error: Stream 'NOSUCH' does not exist or not authorized.",
            refusal("DESCRIBE STREAM nosuch"));
        assertEquals("SQL compilation error: Stream '\"noSuch\"' does not exist or not authorized.",
            refusal("DESCRIBE STREAM \"noSuch\""));
        assertEquals("SQL compilation error: Stream 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.",
            refusal("DESCRIBE STREAM test_schema.nosuch"));
        assertEquals("SQL compilation error: Stream 'TEST_DB.TEST_SCHEMA.NOSUCH' does not exist or not authorized.",
            refusal("DESCRIBE STREAM test_db.test_schema.nosuch"));
        assertEquals("SQL compilation error: Stream 'QSEQ' does not exist or not authorized.",
            refusal("DESCRIBE STREAM qseq"), "a sequence is no stream, and no relation either");
    }

    @Test
    public void aMissingSchemaOrDatabaseIsRefusedByItsOwnName() {
        for (final String keyword : new String[] {"STREAM", "TASK", "PIPE", "SEQUENCE"}) {
            assertEquals("SQL compilation error: Schema 'TEST_DB.NOSUCHSCHEMA' does not exist or not authorized.",
                refusal("DESCRIBE " + keyword + " test_db.nosuchschema.nosuch"), keyword);
            assertEquals("SQL compilation error: Database 'NOSUCHDB' does not exist or not authorized.",
                refusal("DESCRIBE " + keyword + " nosuchdb.test_schema.nosuch"), keyword);
            assertEquals("SQL compilation error: Object does not exist, or operation cannot be performed.",
                refusal("DESCRIBE " + keyword + " a.b.c.d"), keyword);
        }
    }

    @Test
    public void aStreamAndARelationAreToldApartByKind() {
        assertEquals("SQL compilation error: Invalid object type: 'STREAM'", refusal("DESCRIBE STREAM qt"));
        assertEquals("SQL compilation error: Invalid object type: 'STREAM'", refusal("DESCRIBE STREAM qv"));
        assertEquals("SQL compilation error: Invalid object type: 'STREAM'",
            refusal("DESCRIBE STREAM test_db.test_schema.qt"));
        assertEquals("SQL compilation error: Invalid object type: 'STREAM'", refusal("DESCRIBE STREAM test_schema.qv"));
        assertEquals("SQL compilation error: Invalid object type: 'TABLE'", refusal("DESCRIBE TABLE qst"));
        assertEquals("SQL compilation error: Invalid object type: 'TABLE'",
            refusal("DESCRIBE TABLE test_db.test_schema.qst"));
        assertEquals("SQL compilation error: Invalid object type: 'VIEW'", refusal("DESCRIBE VIEW qst"));
        assertEquals("SQL compilation error: Invalid object type: 'MATERIALIZED_VIEW'",
            refusal("DESCRIBE MATERIALIZED VIEW qst"));
        assertEquals("SQL compilation error: Invalid object type: 'DYNAMIC_TABLE'",
            refusal("DESCRIBE DYNAMIC TABLE qst"));
        // The other kinds live apart: a relation keyword over a sequence or a task finds nothing.
        assertEquals("SQL compilation error: Table 'QSEQ' does not exist or not authorized.",
            refusal("DESCRIBE TABLE qseq"));
        assertEquals("SQL compilation error: Table 'QTASK' does not exist or not authorized.",
            refusal("DESCRIBE TABLE qtask"));
    }
}

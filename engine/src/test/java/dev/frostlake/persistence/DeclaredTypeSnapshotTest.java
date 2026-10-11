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

package dev.frostlake.persistence;

import dev.frostlake.DatabaseEngine;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Every declared type survives an engine clone and a checkpoint round trip: a routine's parameters, its return
 * type and the columns it RETURNS TABLE of, and a table's columns. A snapshot that kept only the type NAME
 * restored NUMBER(8,4) as NUMBER(38,0) — so a SQL UDF given '7.5' for a NUMBER(8,4) parameter answered 8 in
 * every engine the test harness cloned from its migrated template — VARCHAR(10) as VARCHAR, TIMESTAMP_NTZ(3) as
 * TIMESTAMP_NTZ(9), a structured ARRAY or OBJECT as the plain one, and a VECTOR or a GEOGRAPHY column as text.
 */
public class DeclaredTypeSnapshotTest {

    private static final String[] TYPES = {
        "NUMBER(8,4)", "VARCHAR(10)", "TIMESTAMP_NTZ(3)", "TIMESTAMP_TZ(0)", "TIME(3)", "BINARY(8)",
        "ARRAY(NUMBER(38,0))", "OBJECT(a NUMBER(38,0))",
    };

    private Path dir;

    @AfterEach
    public void cleanup() {
        if (dir != null) {
            deleteRecursively(dir.toFile());
        }
    }

    @Test
    public void routineAndColumnTypesSurviveACloneAndACheckpoint() throws Exception {
        final DatabaseEngine original = new DatabaseEngine();
        original.execute("CREATE DATABASE d");
        original.execute("USE SCHEMA d.PUBLIC");
        original.execute("CREATE TABLE t (id INT)");
        for (int i = 0; i < TYPES.length; i++) {
            final String type = TYPES[i];
            original.execute("ALTER TABLE t ADD COLUMN c" + i + " " + type);
            original.execute("CREATE FUNCTION f" + i + "(x " + type + ") RETURNS " + type + " AS $$ x $$");
            original.execute("CREATE FUNCTION tf" + i + "() RETURNS TABLE (c " + type + ") AS $$ SELECT NULL::"
                + type + " $$");
            original.execute("CREATE PROCEDURE p" + i + "(x " + type + ") RETURNS " + type
                + " LANGUAGE SQL AS $$ BEGIN RETURN x; END $$");
            original.execute("CREATE PROCEDURE pt" + i + "() RETURNS TABLE (c " + type
                + ") LANGUAGE SQL AS $$ DECLARE r RESULTSET DEFAULT (SELECT NULL::" + type
                + " AS c); BEGIN RETURN TABLE(r); END $$");
        }
        original.execute("ALTER TABLE t ADD COLUMN v VECTOR(FLOAT, 3)");
        original.execute("ALTER TABLE t ADD COLUMN g GEOGRAPHY");
        final List<String> declared = declaredTypes(original);

        final DatabaseEngine clone = original.cloneInstance();
        assertEquals(declared, declaredTypes(clone));
        // A text converts to the parameter's NUMBER(8,4), scale included (live-verified).
        assertEquals("7.5000", scalar(clone, "SELECT d.PUBLIC.f0('7.5')::VARCHAR"));

        dir = Files.createTempDirectory("declared_type_snapshot_");
        original.checkpointStateTo(dir);
        final DatabaseEngine restored = new DatabaseEngine();
        restored.restoreStateFrom(dir);
        assertEquals(declared, declaredTypes(restored));
        assertEquals("7.5000", scalar(restored, "SELECT d.PUBLIC.f0('7.5')::VARCHAR"));
    }

    /** Each column's type and each routine's DDL signature, in a fixed order. */
    private static List<String> declaredTypes(final DatabaseEngine engine) {
        final List<String> out = new ArrayList<>();
        for (final Row row : engine.executeQuery("SELECT COLUMN_NAME || ' ' || DATA_TYPE"
                + " || COALESCE('(' || NUMERIC_PRECISION || ',' || NUMERIC_SCALE || ')', '')"
                + " || COALESCE('[' || CHARACTER_MAXIMUM_LENGTH || ']', '')"
                + " || COALESCE('{' || DATETIME_PRECISION || '}', '')"
                + " FROM d.INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'T' ORDER BY ORDINAL_POSITION").getRows()) {
            out.add(String.valueOf(row.getValue(0)));
        }
        for (int i = 0; i < TYPES.length; i++) {
            // A routine is named by its parameter types without their widths, except that a structured type keeps
            // its shape: f(ARRAY) names a plain ARRAY overload, not f(x ARRAY(NUMBER)).
            final String type = TYPES[i];
            final String sig = type.startsWith("ARRAY(") || type.startsWith("OBJECT(") ? type
                : type.replaceAll("\\(.*", "");
            out.add(signature(engine, "FUNCTION", "f" + i + "(" + sig + ")"));
            out.add(signature(engine, "FUNCTION", "tf" + i + "()"));
            out.add(signature(engine, "PROCEDURE", "p" + i + "(" + sig + ")"));
            out.add(signature(engine, "PROCEDURE", "pt" + i + "()"));
        }
        return out;
    }

    /** A routine's GET_DDL up to its body: the parameters, the return type and any returned columns. */
    private static String signature(final DatabaseEngine engine, final String kind, final String routine) {
        final String ddl = scalar(engine, "SELECT GET_DDL('" + kind + "', 'd.PUBLIC." + routine + "')");
        final int language = ddl.indexOf(" LANGUAGE ");
        return language < 0 ? ddl : ddl.substring(0, language);
    }

    private static String scalar(final DatabaseEngine engine, final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    private static void deleteRecursively(final File file) {
        final File[] children = file.listFiles();
        if (children != null) {
            for (final File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}

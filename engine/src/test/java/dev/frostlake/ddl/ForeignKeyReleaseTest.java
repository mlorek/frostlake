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
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A key that goes takes the foreign keys other tables hold on it, and only those: DROP PRIMARY KEY, DROP UNIQUE,
 * DROP CONSTRAINT and DROP COLUMN alike, CASCADE or not; under RESTRICT a referenced key is refused. UNDROP TABLE does
 * not bring back the foreign keys its DROP took (live-verified).
 */
public class ForeignKeyReleaseTest extends BaseDatabaseTest {

    private String refusal(final String sql) {
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        return refused.getMessage();
    }

    /** The foreign keys a table imports, as parent column / child column pairs. */
    private String imported(final String table) {
        engine.execute("SHOW IMPORTED KEYS IN TABLE " + table);
        final StringBuilder out = new StringBuilder();
        for (final Row row : engine.executeQuery("SELECT \"pk_table_name\", \"pk_column_name\", \"fk_column_name\""
                + " FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) ORDER BY 1, 2, 3").getRows()) {
            out.append(out.length() > 0 ? " | " : "").append(row.getValue(0)).append('.').append(row.getValue(1))
                .append('>').append(row.getValue(2));
        }
        return out.toString();
    }

    @Test
    public void droppingThePrimaryKeyKeepsTheForeignKeysOnAUniqueKey() {
        engine.execute("CREATE TABLE p (pid INT PRIMARY KEY, code VARCHAR UNIQUE)");
        engine.execute("CREATE TABLE c (pid INT REFERENCES p (pid), code VARCHAR REFERENCES p (code))");
        engine.execute("ALTER TABLE p DROP PRIMARY KEY");
        assertEquals("P.CODE>CODE", imported("c"));
    }

    @Test
    public void aKeyDroppedByNameOrByItsColumnTakesItsForeignKeys() {
        engine.execute("CREATE TABLE p1 (pid INT CONSTRAINT pk_p1 PRIMARY KEY)");
        engine.execute("CREATE TABLE c1 (pid INT CONSTRAINT fk_c1 REFERENCES p1 (pid))");
        engine.execute("ALTER TABLE p1 DROP CONSTRAINT pk_p1");
        assertEquals("", imported("c1"));
        engine.execute("CREATE TABLE p2 (pid INT PRIMARY KEY, x INT)");
        engine.execute("CREATE TABLE c2 (pid INT REFERENCES p2 (pid))");
        engine.execute("ALTER TABLE p2 DROP COLUMN pid");
        assertEquals("", imported("c2"));
        engine.execute("CREATE TABLE p5 (pid INT PRIMARY KEY, code VARCHAR UNIQUE)");
        engine.execute("CREATE TABLE c5 (code VARCHAR REFERENCES p5 (code))");
        engine.execute("ALTER TABLE p5 DROP UNIQUE (code)");
        assertEquals("", imported("c5"));
    }

    @Test
    public void restrictRefusesAReferencedKeyAndCascadeTakesItsForeignKeys() {
        engine.execute("CREATE TABLE p4 (pid INT CONSTRAINT pk_p4 PRIMARY KEY)");
        engine.execute("CREATE TABLE c4 (pid INT REFERENCES p4 (pid))");
        assertEquals("SQL compilation error:\ncannot drop constraint because of dependencies",
            refusal("ALTER TABLE p4 DROP CONSTRAINT pk_p4 RESTRICT"));
        assertEquals("P4.PID>PID", imported("c4"));
        engine.execute("ALTER TABLE p4 DROP CONSTRAINT pk_p4 CASCADE");
        assertEquals("", imported("c4"));
        assertEquals("SQL compilation error:\nconstraint 'X' does not exist",
            refusal("ALTER TABLE p4 DROP CONSTRAINT x CASCADE"));
        engine.execute("CREATE TABLE p6 (pid INT PRIMARY KEY)");
        engine.execute("ALTER TABLE p6 DROP PRIMARY KEY CASCADE");
    }

    @Test
    public void undropBringsBackNoForeignKey() {
        engine.execute("CREATE TABLE p3 (pid INT PRIMARY KEY)");
        engine.execute("CREATE TABLE c3 (pid INT REFERENCES p3 (pid))");
        engine.execute("DROP TABLE p3");
        assertEquals("", imported("c3"));
        engine.execute("UNDROP TABLE p3");
        assertEquals("", imported("c3"));
    }
}

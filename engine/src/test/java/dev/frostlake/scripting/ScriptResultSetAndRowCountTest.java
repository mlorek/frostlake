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

package dev.frostlake.scripting;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Snowflake Scripting RESULTSET / cursor plumbing: {@code SQLROWCOUNT} after a DML, {@code
 * RESULTSET_FROM_CURSOR(<cursor>)}, and {@code rs := (SELECT …)} RESULTSET assignment.
 */
public class ScriptResultSetAndRowCountTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE invoices (id NUMBER, price NUMBER)");
        engine.execute("INSERT INTO invoices VALUES (1, 10), (2, 20), (3, 30), (4, 40), (5, 50)");
        engine.execute("CREATE TABLE t (a NUMBER)");
    }

    private long ret(final String block) {
        return ((Number) engine.executeQuery(block).getRows().get(0).getValue(0)).longValue();
    }

    @Test
    public void sqlRowCountReflectsLastDml() {
        assertEquals(3L, ret("""
            BEGIN
              INSERT INTO t VALUES (100), (200), (300);
              RETURN SQLROWCOUNT;
            END;"""));
    }

    @Test
    public void sqlRowCountAfterDelete() {
        assertEquals(2L, ret("""
            BEGIN
              DELETE FROM invoices WHERE price < 30;
              RETURN SQLROWCOUNT;
            END;"""));
    }

    @Test
    public void resultsetFromCursor() {
        // price > 15 → {20,30,40,50} = 4 rows returned as a table.
        final ResultSet rs = engine.executeQuery("""
            DECLARE c1 CURSOR FOR SELECT price FROM invoices WHERE price > 15;
            BEGIN
              OPEN c1;
              RETURN TABLE(RESULTSET_FROM_CURSOR(c1));
            END;""");
        assertEquals(4, rs.getRows().size());
    }

    @Test
    public void resultsetAssignmentFromSelect() {
        // rs := (SELECT price FROM invoices); FOR-iterate → sum 150.
        assertEquals(150L, ret("""
            DECLARE rs RESULTSET;
            BEGIN
              LET total INTEGER := 0;
              rs := (SELECT price FROM invoices);
              FOR r IN rs DO
                total := total + r.price;
              END FOR;
              RETURN total;
            END;"""));
    }
}

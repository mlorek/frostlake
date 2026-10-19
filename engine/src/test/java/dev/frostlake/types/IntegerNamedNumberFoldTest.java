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

package dev.frostlake.types;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exact numerics fold by family, whatever they are called. A function's registry type keeps the INTEGER name its
 * declaration was written with (DATE_PART over an identifier part, EXTRACT(epoch_millisecond ...), MAP_SIZE). The folds
 * computed a supertype only for two types NAMED NUMBER, so a UNION refused its arms with the self-contradicting
 * "expected NUMBER(38,0), got NUMBER(38,0)", and COALESCE and IFF lost their type to NULL[LOB]: a view declared
 * VARCHAR and a CTAS took its type from the data.
 */
public class IntegerNamedNumberFoldTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE n (i INT, n2 NUMBER(10,2), ts TIMESTAMP_NTZ)");
        engine.execute("INSERT INTO n VALUES (1, 2.75, '2024-05-06 07:08:09')");
    }

    private String typeOf(final String expression) {
        final String typed = String.valueOf(engine.executeQuery("SELECT SYSTEM$TYPEOF(" + expression + ") FROM n")
            .getRows().get(0).getValue(0));
        return typed.substring(0, typed.indexOf('['));
    }

    private String describeView(final String view, final String column) {
        final ResultSet rs = engine.executeQuery("DESCRIBE VIEW " + view);
        return cell(rs, soleRowWhere(rs, "name", column), "type");
    }

    @Test
    public void anIntegerNamedArmFoldsWithANumberOne() {
        final List<Row> rows = engine.executeQuery(
            "SELECT DATE_PART(year, ts) x FROM n UNION ALL SELECT i FROM n ORDER BY x").getRows();
        assertEquals(2, rows.size());
        assertEquals(0, new BigDecimal("1").compareTo(new BigDecimal(String.valueOf(rows.get(0).getValue(0)))));
        assertEquals(0, new BigDecimal("2024").compareTo(new BigDecimal(String.valueOf(rows.get(1).getValue(0)))));
        final List<Row> mixed = engine.executeQuery(
            "SELECT MAP_SIZE({'a':1}::MAP(VARCHAR, INT)) x UNION ALL SELECT 1.5 ORDER BY x").getRows();
        assertEquals(0, BigDecimal.ONE.compareTo(new BigDecimal(String.valueOf(mixed.get(0).getValue(0)))));
        engine.execute("CREATE VIEW v2 AS SELECT DATE_PART(year, ts) x FROM n UNION ALL SELECT i FROM n");
        assertEquals("NUMBER(38,0)", describeView("v2", "X"));
    }

    @Test
    public void aConditionalOverOneKeepsItsType() {
        assertEquals("NUMBER(10,2)", typeOf("COALESCE(DATE_PART(year, ts), n2)"));
        assertEquals("NUMBER(38,1)", typeOf("IFF(TRUE, EXTRACT(epoch_millisecond FROM ts), 1.5)"));
        engine.execute("CREATE VIEW v1 AS SELECT COALESCE(DATE_PART(year, ts), n2) a FROM n");
        assertEquals("NUMBER(10,2)", describeView("v1", "A"));
        // The declared type, not the data's: the same with the timestamp set and with it NULL.
        engine.execute("CREATE TABLE c1 AS SELECT COALESCE(DATE_PART(year, ts), n2) a FROM n");
        engine.execute("CREATE TABLE c2 AS SELECT COALESCE(DATE_PART(year, NULL::TIMESTAMP_NTZ), n2) a FROM n");
        assertEquals("NUMBER(10,2)", describeCell("c1", "A", "type"));
        assertEquals("NUMBER(10,2)", describeCell("c2", "A", "type"));
    }

    @Test
    public void anIdentifierDatePartIsTypedAsAQuotedOne() {
        assertEquals(typeOf("DATE_PART('year', ts)"), typeOf("DATE_PART(year, ts)"));
        assertEquals(typeOf("DATE_PART('month', ts)"), typeOf("DATE_PART(month, ts)"));
        assertEquals("NUMBER(4,0)", typeOf("DATE_PART(year, ts)"));
    }
}

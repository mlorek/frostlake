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
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A join pairs two rows whenever its equality holds, as a filter over the same rows would: a text beside a BOOLEAN,
 * a DATE, a TIMESTAMP or a TIME is read as that type, and so is a number beside a BOOLEAN, any but 0 being TRUE;
 * -0.0 equals 0.0, an accent-insensitive collation equates {@code 'café'} with {@code 'cafe'}, and a text that does
 * not read as the number beside it is refused rather than passed over (live-verified, each against the same equality
 * joined under an OR that keeps it from keying).
 */
public class JoinKeyConversionTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE kb (b BOOLEAN, d DATE, ts TIMESTAMP_NTZ, tm TIME, f FLOAT)");
        engine.execute("INSERT INTO kb SELECT TRUE, '2024-01-01'::DATE, '2024-01-01 10:00:00'::TIMESTAMP_NTZ,"
            + " '10:00:00'::TIME, -0.0::FLOAT");
        engine.execute("CREATE TABLE ks (bt VARCHAR, dt VARCHAR, tst VARCHAR, tmt VARCHAR, f FLOAT)");
        engine.execute("INSERT INTO ks SELECT 'true', '2024-01-01', '2024-01-01 10:00:00', '10:00:00', 0.0::FLOAT");
    }

    /** Every cell of the first row, joined. */
    private String row(final String sql) {
        final Row row = engine.executeQuery(sql).getRows().get(0);
        final StringBuilder out = new StringBuilder();
        for (int i = 0; i < row.getValues().size(); i++) {
            out.append(i > 0 ? "," : "").append(row.getValue(i));
        }
        return out.toString();
    }

    @Test
    public void aTextJoinsAsTheTypeBesideIt() {
        assertEquals("1", row("SELECT COUNT(*) FROM kb JOIN ks ON kb.b = ks.bt"));
        assertEquals("1", row("SELECT COUNT(*) FROM kb JOIN ks ON kb.d = ks.dt"));
        assertEquals("1", row("SELECT COUNT(*) FROM kb JOIN ks ON kb.ts = ks.tst"));
        assertEquals("1", row("SELECT COUNT(*) FROM kb JOIN ks ON kb.tm = ks.tmt"));
        assertEquals("1", row("SELECT COUNT(*) FROM kb, ks WHERE kb.d = ks.dt"));
        assertEquals("2024-01-01,2024-01-01",
            row("SELECT TO_VARCHAR(kb.d), ks.dt FROM kb LEFT JOIN ks ON kb.d = ks.dt"));
    }

    @Test
    public void aNumberJoinsABooleanAsABoolean() {
        engine.execute("CREATE TABLE kn (n INT)");
        engine.execute("INSERT INTO kn VALUES (1), (2), (0)");
        assertEquals("2", row("SELECT COUNT(*) FROM kb JOIN kn ON kb.b = kn.n"));
        assertEquals("2", row("SELECT COUNT(*) FROM kb, kn WHERE kb.b = kn.n"));
    }

    @Test
    public void minusZeroJoinsZero() {
        assertEquals("1", row("SELECT COUNT(*) FROM kb JOIN ks ON kb.f = ks.f"));
        assertEquals("1", row("SELECT COUNT(*) FROM kb JOIN ks ON kb.f = ks.f OR 1 = 0"));
    }

    @Test
    public void anAccentInsensitiveCollationJoinsAccentedText() {
        engine.execute("CREATE TABLE ca (s VARCHAR COLLATE 'en-ai')");
        engine.execute("INSERT INTO ca VALUES ('café')");
        engine.execute("CREATE TABLE cb (s VARCHAR COLLATE 'en-ai')");
        engine.execute("INSERT INTO cb VALUES ('cafe')");
        assertEquals("1", row("SELECT COUNT(*) FROM ca JOIN cb ON ca.s = cb.s"));
    }

    @Test
    public void textsInAnotherCaseDoNotJoin() {
        engine.execute("CREATE TABLE x1 (s VARCHAR)");
        engine.execute("INSERT INTO x1 VALUES ('a')");
        engine.execute("CREATE TABLE x2 (s VARCHAR)");
        engine.execute("INSERT INTO x2 VALUES ('A')");
        assertEquals("0", row("SELECT COUNT(*) FROM x1 JOIN x2 ON x1.s = x2.s"));
        assertEquals("0", row("SELECT COUNT(*) FROM x1, x2 WHERE x1.s = x2.s"));
    }

    @Test
    public void aTextThatDoesNotReadAsTheNumberIsRefused() {
        engine.execute("CREATE TABLE nk (k INT)");
        engine.execute("INSERT INTO nk VALUES (1)");
        engine.execute("CREATE TABLE tx (s VARCHAR)");
        engine.execute("INSERT INTO tx VALUES ('1'), ('1a')");
        final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT COUNT(*) FROM nk JOIN tx ON nk.k = tx.s");
            }
        });
        assertTrue(String.valueOf(refused.getMessage()).contains("Numeric value '1a' is not recognized"),
            refused.getMessage());
    }
}

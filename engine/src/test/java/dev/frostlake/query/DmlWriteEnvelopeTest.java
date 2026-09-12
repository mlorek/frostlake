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

package dev.frostlake.query;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A row-time value failure raised while a DML's SOURCE QUERY runs, and the envelope live wraps it in:
 * {@code DML operation to table T failed on column C with error: …}.
 *
 * <p>★ THE ENVELOPE BELONGS TO THE STATEMENT, NOT TO THE VALUE. The same {@code SELECT} written on its
 * own is answered with the bare sentence, so it is the write that adds the wrapper.
 *
 * <p>★ THE COLUMN IS THE TARGET OF THE PROJECTION THAT STOPPED, not the first column and not the last:
 * {@code INSERT INTO vempty SELECT va, COALESCE(va, d)} names D, the second. Where two projections
 * would both fail, live names the FIRST — which is what recording the item the evaluation stopped on
 * gives for free.
 *
 * <p>★ A CTAS ALWAYS QUALIFIES THE TABLE, even though its CREATE named it bare. An INSERT does not:
 * measured beside it in one run, so it is the statement kind that decides.
 *
 * <p>★ THE TABLE IS NAMED AS THE STATEMENT WROTE IT, upper-cased per part: bare stays bare, a schema
 * or database qualification stays (live echoes an account-qualified four-part name with all four) —
 * for INSERT, UPDATE, UPDATE … FROM and both MERGE branches. A CTAS qualifies the written name ONE
 * LEVEL UP instead: bare or schema-qualified to DB.SCHEMA.T, fully qualified to ACCOUNT.DB.SCHEMA.T.
 *
 * <p>★ A SET / VALUES value that cannot be COMPUTED is enveloped by a plain UPDATE and by a MERGE's
 * INSERT (with the source cast's own sentence), and left BARE by UPDATE … FROM, a MERGE's UPDATE and
 * a DELETE's WHERE; a value refused at the WRITE is enveloped by every one of them.
 */
public class DmlWriteEnvelopeTest extends BaseDatabaseTest {

    @BeforeEach
    public void seed() {
        engine.execute("CREATE OR REPLACE TABLE vf (va VARIANT, d DATE, n NUMBER(10,2))");
        engine.execute("INSERT INTO vf SELECT PARSE_JSON('1'), '2020-01-01', 1.00");
        engine.execute("CREATE OR REPLACE TABLE vempty (va VARIANT, d DATE)");
        engine.execute("CREATE OR REPLACE TABLE two (s NUMBER(2,0))");
        engine.execute("CREATE OR REPLACE TABLE upd (va VARIANT, d DATE)");
        engine.execute("INSERT INTO upd SELECT PARSE_JSON('1'), '2020-01-01'");
        engine.execute("CREATE OR REPLACE TABLE twok (s NUMBER(2,0), k NUMBER(2,0))");
        engine.execute("INSERT INTO twok VALUES (1, 1)");
        engine.execute("CREATE OR REPLACE TABLE kk (k NUMBER(2,0))");
        engine.execute("INSERT INTO kk VALUES (1)");
    }

    private String account() {
        final ResultSet rs = engine.executeQuery("SELECT CURRENT_ACCOUNT()");
        rs.next();
        return String.valueOf(rs.getValue(0));
    }

    /** One statement's refusal, or "ACCEPTED". */
    private String outcome(final String sql) {
        try {
            engine.execute(sql);
            return "ACCEPTED";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    private static String envelope(final String table, final String column, final String inner) {
        return "DML operation to table " + table + " failed on column " + column
            + " with error: " + inner;
    }

    private static final String BAD_CAST = "Failed to cast variant value 1 to DATE";

    @Test
    void acastFailureInsideAnInsertSelectCarriesTheEnvelope() {
        assertEquals(envelope("VEMPTY", "D", BAD_CAST),
            outcome("INSERT INTO vempty SELECT va, COALESCE(va, d) FROM vf"));
    }

    @Test
    void thetableIsNamedAsTheStatementWroteIt() {
        // Upper-cased, whatever case the statement used.
        assertEquals(envelope("VEMPTY", "D", BAD_CAST),
            outcome("INSERT INTO VeMpTy SELECT va, COALESCE(va, d) FROM vf"));
    }

    @Test
    void actasQualifiesTheTableAndNamesTheItemsAlias() {
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.VOUT", "C", BAD_CAST),
            outcome("CREATE OR REPLACE TABLE vout AS SELECT COALESCE(va, d) AS c FROM vf"));
    }

    @Test
    void actasOverAValueTooWideForItsOwnCastIsAWriteFailureToo() {
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.RCTAS", "S",
                "Number out of representable range: type FIXED[SB2](2,0){not null}, value 1000"),
            outcome("CREATE OR REPLACE TABLE rctas AS SELECT CAST(1000 AS NUMBER(2,0)) AS s"));
    }

    @Test
    void thefirstFailingProjectionNamesTheColumn() {
        engine.execute("CREATE OR REPLACE TABLE twobad (d1 DATE, d2 DATE)");
        assertEquals(envelope("TWOBAD", "D1", BAD_CAST),
            outcome("INSERT INTO twobad SELECT COALESCE(va, d), COALESCE(va, d) FROM vf"));
    }

    @Test
    void awriteFailureFromAValuesListKeepsItsOwnEnvelope() {
        assertEquals(envelope("TWO", "S",
                "Number out of representable range: type FIXED[SB1](2,0){nullable}, value 1000"),
            outcome("INSERT INTO two VALUES (1000)"));
    }

    @Test
    void abareSelectCarriesNoEnvelopeAtAll() {
        assertEquals(BAD_CAST, outcome("SELECT COALESCE(va, d) FROM vf"));
    }

    private static final String OUT_OF_RANGE_SB1 =
        "Number out of representable range: type FIXED[SB1](2,0){nullable}, value 1000";
    private static final String OUT_OF_RANGE_SB2 =
        "Number out of representable range: type FIXED[SB2](2,0){nullable}, value 1000";

    /** ★ A qualified INSERT names the table as written, upper-cased per part, even with the account. */
    @Test
    void aqualifiedInsertNamesTheTableAsWritten() {
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.VEMPTY", "D", BAD_CAST),
            outcome("INSERT INTO test_db.test_schema.vempty SELECT va, COALESCE(va, d) FROM vf"));
        assertEquals(envelope("TEST_SCHEMA.VEMPTY", "D", BAD_CAST),
            outcome("INSERT INTO test_schema.vempty SELECT va, COALESCE(va, d) FROM vf"));
        assertEquals(envelope("VEMPTY", "D", BAD_CAST),
            outcome("INSERT INTO \"VEMPTY\" SELECT va, COALESCE(va, d) FROM vf"));
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.VEMPTY", "D", BAD_CAST),
            outcome("INSERT INTO Test_Db.Test_Schema.VeMpTy SELECT va, COALESCE(va, d) FROM vf"));
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.TWO", "S", OUT_OF_RANGE_SB1),
            outcome("INSERT INTO test_db.test_schema.two VALUES (1000)"));
        assertEquals(envelope("TEST_SCHEMA.TWO", "S", OUT_OF_RANGE_SB1),
            outcome("INSERT INTO test_schema.two VALUES (1000)"));
        // An account-qualified four-part name resolves, and is echoed with all four parts.
        assertEquals(envelope(account() + ".TEST_DB.TEST_SCHEMA.TWO", "S", OUT_OF_RANGE_SB1),
            outcome("INSERT INTO " + account().toLowerCase() + ".test_db.test_schema.two VALUES (1000)"));
    }

    /** ★ A plain UPDATE envelopes a SET value it cannot compute and a value it cannot write. */
    @Test
    void anupdateWrapsItsSetFailureAndItsWrite() {
        assertEquals(envelope("UPD", "D", BAD_CAST), outcome("UPDATE upd SET d = COALESCE(va, d)"));
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.UPD", "D", BAD_CAST),
            outcome("UPDATE test_db.test_schema.upd SET d = COALESCE(va, d)"));
        assertEquals(envelope("TEST_SCHEMA.UPD", "D", BAD_CAST),
            outcome("UPDATE test_schema.upd SET d = COALESCE(va, d)"));
        assertEquals(envelope("UPD", "D", BAD_CAST), outcome("UPDATE upd u SET d = COALESCE(u.va, u.d)"));
        assertEquals(envelope("UPD", "D", BAD_CAST),
            outcome("UPDATE upd SET d = COALESCE(va, d) WHERE d IS NOT NULL"));
        assertEquals(envelope("UPD", "D", BAD_CAST), outcome("UPDATE upd SET va = va, d = COALESCE(va, d)"));
        assertEquals(envelope("TWOK", "S", OUT_OF_RANGE_SB2), outcome("UPDATE twok SET s = 1000"));
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.TWOK", "S", OUT_OF_RANGE_SB2),
            outcome("UPDATE test_db.test_schema.twok SET s = 1000 WHERE k = 1"));
        assertEquals(envelope("TWOK", "S", OUT_OF_RANGE_SB2), outcome("UPDATE twok SET s = k * 1000"));
    }

    /** ★ UPDATE … FROM leaves a SET value it cannot compute bare, and envelopes only the write. */
    @Test
    void anupdateFromLeavesTheSetFailureBareAndWrapsTheWrite() {
        assertEquals(BAD_CAST, outcome("UPDATE upd SET d = COALESCE(vf.va, vf.d) FROM vf"));
        assertEquals(BAD_CAST, outcome("UPDATE test_db.test_schema.upd SET d = COALESCE(vf.va, vf.d) FROM vf"));
        assertEquals(envelope("TWOK", "S", OUT_OF_RANGE_SB2),
            outcome("UPDATE twok SET s = 1000 FROM kk WHERE twok.k = kk.k"));
        assertEquals(envelope("TWOK", "S", OUT_OF_RANGE_SB2),
            outcome("UPDATE twok SET s = kk.k * 1000 FROM kk WHERE twok.k = kk.k"));
    }

    /** ★ A MERGE's INSERT names the target as written and carries the source cast's own sentence. */
    @Test
    void amergeInsertNamesTheTargetAsWrittenWithTheSourceCast() {
        final String merge = " t USING vf s ON t.va = s.va WHEN NOT MATCHED THEN INSERT (va, d)"
            + " VALUES (s.va, COALESCE(s.va, s.d))";
        assertEquals(envelope("VEMPTY", "D", BAD_CAST), outcome("MERGE INTO vempty" + merge));
        assertEquals(envelope("TEST_SCHEMA.VEMPTY", "D", BAD_CAST), outcome("MERGE INTO test_schema.vempty" + merge));
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.VEMPTY", "D", BAD_CAST),
            outcome("MERGE INTO test_db.test_schema.vempty" + merge));
        assertEquals(envelope("TWO", "S", OUT_OF_RANGE_SB2),
            outcome("MERGE INTO two t USING (SELECT 1 AS k) s ON t.s = s.k WHEN NOT MATCHED THEN INSERT (s) VALUES (1000)"));
        assertEquals(envelope("TWOK", "S", "Number out of representable range: type FIXED[SB2](2,0){nullable}, value 2000"),
            outcome("MERGE INTO twok t USING (SELECT 2 AS k) s ON t.k = s.k"
                + " WHEN NOT MATCHED THEN INSERT (s, k) VALUES (s.k * 1000, s.k)"));
    }

    /** ★ A MERGE's UPDATE leaves a SET value it cannot compute bare, and envelopes the write. */
    @Test
    void amergeUpdateLeavesTheSetFailureBareAndWrapsTheWrite() {
        assertEquals(BAD_CAST,
            outcome("MERGE INTO upd t USING vf s ON t.va = s.va WHEN MATCHED THEN UPDATE SET d = COALESCE(s.va, s.d)"));
        assertEquals(envelope("TWOK", "S", OUT_OF_RANGE_SB2),
            outcome("MERGE INTO twok t USING kk s ON t.k = s.k WHEN MATCHED THEN UPDATE SET s = 1000"));
        assertEquals(envelope("TWOK", "S", OUT_OF_RANGE_SB2),
            outcome("MERGE INTO twok t USING kk s ON t.k = s.k WHEN MATCHED THEN UPDATE SET s = s.k * 1000"));
    }

    /** ★ A DELETE whose WHERE fails carries no envelope: nothing was being written. */
    @Test
    void adeleteCarriesNoEnvelope() {
        assertEquals(BAD_CAST, outcome("DELETE FROM upd WHERE COALESCE(va, d) IS NULL"));
    }

    /** ★ A CTAS qualifies the written name one level up — to the account for a fully qualified one. */
    @Test
    void actasQualifiesTheWrittenNameOneLevelUp() {
        final String body = " AS SELECT va, COALESCE(va, d) AS d FROM vf";
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.VOUT", "D", BAD_CAST),
            outcome("CREATE OR REPLACE TABLE test_schema.vout" + body));
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.VOUT", "D", BAD_CAST),
            outcome("CREATE OR REPLACE TABLE \"VOUT\"" + body));
        assertEquals(envelope("TEST_DB.TEST_SCHEMA.VOUT", "D", BAD_CAST),
            outcome("CREATE OR REPLACE TEMPORARY TABLE vout" + body));
        assertEquals(envelope(account() + ".TEST_DB.TEST_SCHEMA.VOUT", "D", BAD_CAST),
            outcome("CREATE OR REPLACE TABLE test_db.test_schema.vout" + body));
        // Already at the account: an account-qualified name is named as written, the locator once.
        assertEquals(envelope(account() + ".TEST_DB.TEST_SCHEMA.VOUT", "D", BAD_CAST),
            outcome("CREATE OR REPLACE TABLE " + account().toLowerCase() + ".test_db.test_schema.vout" + body));
    }
}

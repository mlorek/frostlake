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
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the word {@code NULL} declares when it is a select item. Live gives it VARCHAR(0) — a width no
 * column can hold and no literal can produce, which is its way of saying nothing was named — where
 * Frostlake reported the 16MB placeholder every untyped item falls back to.
 *
 * <p>THE REPORTED TYPE ONLY. The static type stays undetermined, exactly as it was, and that is what
 * keeps an INSERT of a NULL into a VARIANT / ARRAY / OBJECT column working — the type check has nothing
 * concrete to compare and skips the column. Setting the static type instead is what refused fourteen
 * vendor loaders when the same width was tried on the set-operation fold, so those INSERTs are asserted
 * here too rather than assumed.
 *
 * <p>A ZERO WIDTH NEVER REACHES A STORED COLUMN. A CTAS widens it back to VARCHAR(16777216), which is
 * what live stores, and live refuses {@code CREATE TABLE t (c VARCHAR(0))} outright — so the width
 * exists only as something a query REPORTS. The CTAS cells below are the ones that matter: without the
 * widening this change would have created columns able to hold no value at all, and it also closes a
 * leak that was already there, since a conditional whose every branch is NULL has declared VARCHAR(0)
 * since before this and stored it.
 *
 * <p>NOT FIXED HERE: a FUNCTION over a NULL — {@code NULL || 'x'}, {@code UPPER(NULL)} — declares the
 * 128MB unknown length live, where Frostlake still gives 16MB. That is the string-width family rather
 * than the literal's own type, and it is tracked separately.
 */
public class NullLiteralWidthTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE nl (i INT, s VARCHAR(5))");
        engine.execute("INSERT INTO nl VALUES (1, 'x')");
    }

    /** The declared type of a query's first column. */
    private String typeOf(final String sql) {
        final DataType t = engine.executeQuery(sql).getColumns().get(0).getDataType();
        if (t instanceof StringType) {
            return t.getName() + "(" + ((StringType) t).getMaxLength() + ")";
        }
        return t == null ? "null" : t.getName();
    }

    /** What a CTAS of this select STORES for its first column. */
    private String storedType(final String select) {
        engine.execute("CREATE OR REPLACE TABLE nl_out AS " + select);
        final ResultSet rs = engine.executeQuery("DESCRIBE TABLE nl_out");
        return rs.next() ? String.valueOf(rs.getValue(1)) : "<no columns>";
    }

    /** The literal itself, however it is written. */
    @Test
    public void theWordNullDeclaresTheZeroWidth() {
        assertEquals("VARCHAR(0)", typeOf("SELECT NULL"));
        assertEquals("VARCHAR(0)", typeOf("SELECT NULL FROM nl"));
        assertEquals("VARCHAR(0)", typeOf("SELECT null FROM nl"), "the case is immaterial");
        assertEquals("VARCHAR(0)", typeOf("SELECT NULL AS c FROM nl"));
        assertEquals("VARCHAR(0)", typeOf("SELECT NULL a, NULL b FROM nl"));
    }

    /** And it survives every wrapper that carries a column's type outward. */
    @Test
    public void itSurvivesACteADerivedTableAndAView() {
        assertEquals("VARCHAR(0)", typeOf("WITH c AS (SELECT NULL x FROM nl) SELECT x FROM c"));
        assertEquals("VARCHAR(0)", typeOf("SELECT x FROM (SELECT NULL x FROM nl) s"));
        engine.execute("CREATE OR REPLACE VIEW nl_v AS SELECT NULL AS c FROM nl");
        assertEquals("VARCHAR(0)", typeOf("SELECT c FROM nl_v"));
    }

    /** A CTAS widens it back — no stored column may hold a zero width. */
    @Test
    public void aCtasWidensItToTheStoredDefault() {
        assertEquals("VARCHAR(16777216)", storedType("SELECT NULL AS c FROM nl"));
        assertEquals("VARCHAR(16777216)", storedType("SELECT NULL AS c, s FROM nl"));
        assertEquals("VARCHAR(16777216)", storedType("SELECT COALESCE(NULL, NULL) AS c FROM nl"),
            "and the conditional path, which declared the zero width before this and stored it");
    }

    /** The INSERTs the fourteen vendor loaders make — the reason the static type is left alone. */
    @Test
    public void aNullStillFeedsASemiStructuredColumn() {
        engine.execute("CREATE OR REPLACE TABLE nl_t (id INT, va VARIANT, ar ARRAY, ob OBJECT,"
            + " s VARCHAR(5))");
        engine.execute("INSERT INTO nl_t (id, va) SELECT 1, NULL FROM nl");
        engine.execute("INSERT INTO nl_t (id, ar) SELECT 2, NULL FROM nl");
        engine.execute("INSERT INTO nl_t (id, ob) SELECT 3, NULL FROM nl");
        engine.execute("INSERT INTO nl_t (id, s) SELECT 4, NULL FROM nl");
        engine.execute("INSERT INTO nl_t (id, va) VALUES (5, NULL)");
        final ResultSet rs = engine.executeQuery("SELECT COUNT(*) FROM nl_t");
        rs.next();
        assertEquals("5", String.valueOf(rs.getValue(0)));
    }

    /** A TYPED null is not this at all — it folds as the type it names. */
    @Test
    public void aTypedNullIsUnaffected() {
        assertEquals("VARCHAR(134217728)", typeOf("SELECT NULL::VARCHAR FROM nl"));
        assertEquals("NUMBER", typeOf("SELECT NULL::INT FROM nl"));
    }

    /** And a NULL beside a real value contributes nothing, which was already true. */
    @Test
    public void aNullBesideARealValueIsUnchanged() {
        assertEquals("VARCHAR(134217728)", typeOf("SELECT COALESCE(NULL, s) FROM nl"));
        assertEquals("VARCHAR(134217728)", typeOf("SELECT COALESCE(s, NULL) FROM nl"));
        assertEquals("VARCHAR(0)", typeOf("SELECT COALESCE(NULL, NULL) FROM nl"),
            "an all-NULL conditional already declared the zero width and still does");
        assertEquals("VARCHAR(0)", typeOf("SELECT IFF(i > 0, NULL, NULL) FROM nl"));
        assertEquals("VARCHAR(0)", typeOf("SELECT CASE WHEN i > 0 THEN NULL ELSE NULL END FROM nl"));
    }
}

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A UUID keeps its type where a conditional folds it beside a bare NULL, and inside a VARIANT. IFF, CASE,
 * COALESCE, NVL, DECODE, GREATEST and the rest fold a UUID and a NULL to UUID, not to the widest text.
 * TO_VARIANT, a cast to VARIANT, the array and object constructors and literals, and the members they
 * build all report UUID through TYPEOF, through extraction and through the array functions; such a member
 * is no VARCHAR to IS_VARCHAR or AS_VARCHAR, and it never equals, matches or overlaps the variant string of
 * the same text, while it still reads as that text wherever a text is asked for. Every cell is
 * live-verified.
 */
public class UuidVariantTest extends BaseDatabaseTest {

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE TABLE ut (u UUID, s VARCHAR)");
        engine.execute("INSERT INTO ut SELECT '1b4e28ba-2fa1-11d2-883f-0016d3cca427'::UUID, '1b4e28ba-2fa1-11d2-883f-0016d3cca427'");
        engine.execute("CREATE TABLE vt (v VARIANT, w VARIANT)");
        engine.execute("INSERT INTO vt SELECT TO_VARIANT(u), TO_VARIANT(s) FROM ut");
    }

    /** Every row's cells, lower-cased: a comma between cells and a bar between rows. */
    private String answer(final String sql) {
        final StringBuilder answer = new StringBuilder();
        for (final Row row : engine.executeQuery(sql).getRows()) {
            if (answer.length() > 0) {
                answer.append(" | ");
            }
            for (int i = 0; i < row.getValues().size(); i++) {
                if (i > 0) {
                    answer.append(", ");
                }
                answer.append(String.valueOf(row.getValue(i)).toLowerCase());
            }
        }
        return answer.toString();
    }

    private void assertAnswers(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void aUuidBesideABareNullFoldsToUuid() {
        assertAnswers(new String[][] {
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, u, NULL)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(IFF(FALSE, NULL, u)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN u END) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(CASE WHEN TRUE THEN u ELSE NULL END) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(u, NULL)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(NVL(u, NULL)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(NVL2(u, u, NULL)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(DECODE(1, 1, u)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(DECODE(1, 1, u, NULL)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(NULLIF(u, NULL)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(GREATEST(u, NULL)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(IFNULL(NULL, u)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(COALESCE(NULL, u)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, u, NULL::UUID)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(u) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(CASE WHEN FALSE THEN NULL ELSE u END) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(LEAST(NULL, u)) FROM ut", "uuid[sb16]"},
            {"SELECT IFF(TRUE, u, NULL) FROM ut", "1b4e28ba-2fa1-11d2-883f-0016d3cca427"},
            {"SELECT SYSTEM$TYPEOF(IFF(TRUE, u, s)) FROM ut", "uuid[sb16]"},
            {"SELECT SYSTEM$TYPEOF(x) FROM (SELECT IFF(TRUE, u, NULL) x FROM ut)", "uuid[sb16]"},
        });
    }

    @Test
    public void aUuidInsideAVariantReportsUuid() {
        assertAnswers(new String[][] {
            {"SELECT TYPEOF(TO_VARIANT(u)) FROM ut", "uuid"},
            {"SELECT TYPEOF(u::VARIANT) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_CONSTRUCT(u)[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF(OBJECT_CONSTRUCT('k', u):k) FROM ut", "uuid"},
            {"SELECT TO_VARIANT(u)::VARCHAR FROM ut", "1b4e28ba-2fa1-11d2-883f-0016d3cca427"},
            {"SELECT SYSTEM$TYPEOF(TO_VARIANT(u)) FROM ut", "variant[lob]"},
            {"SELECT IS_VARCHAR(TO_VARIANT(u)) FROM ut", "false"},
            {"SELECT TO_JSON(TO_VARIANT(u)) FROM ut", "\"1b4e28ba-2fa1-11d2-883f-0016d3cca427\""},
            {"SELECT TYPEOF(TO_VARIANT(s)) FROM ut", "varchar"},
            {"SELECT TYPEOF(v) FROM vt", "uuid"},
            {"SELECT AS_VARCHAR(TO_VARIANT(u)) FROM ut", "null"},
            {"SELECT TO_VARIANT(u) = TO_VARIANT(s) FROM ut", "false"},
            {"SELECT ARRAY_CONSTRUCT(u) FROM ut", "[\"1b4e28ba-2fa1-11d2-883f-0016d3cca427\"]"},
            {"SELECT TYPEOF(GET(ARRAY_CONSTRUCT(u), 0)) FROM ut", "uuid"},
            {"SELECT TO_VARIANT(u)::UUID FROM ut", "1b4e28ba-2fa1-11d2-883f-0016d3cca427"},
            {"SELECT SYSTEM$TYPEOF(TO_VARIANT(u)::UUID) FROM ut", "uuid[sb16]"},
            {"SELECT TYPEOF(TO_VARIANT('1b4e28ba-2fa1-11d2-883f-0016d3cca427'::UUID))", "uuid"},
            {"SELECT TYPEOF(PARSE_JSON(TO_JSON(TO_VARIANT(u)))) FROM ut", "varchar"},
            {"SELECT OBJECT_CONSTRUCT('k', u) FROM ut", "{\"k\":\"1b4e28ba-2fa1-11d2-883f-0016d3cca427\"}"},
            {"SELECT TYPEOF(v:x) FROM vt", "null"},
            {"SELECT IS_NULL_VALUE(TO_VARIANT(u)) FROM ut", "false"},
            {"SELECT TO_VARIANT(u) = v FROM ut, vt", "true"},
            {"SELECT AS_CHAR(TO_VARIANT(u)) FROM ut", "null"},
            {"SELECT TYPEOF(TO_VARIANT(UUID_STRING()))", "varchar"},
            {"SELECT TO_VARCHAR(TO_VARIANT(u)) FROM ut", "1b4e28ba-2fa1-11d2-883f-0016d3cca427"},
            {"SELECT LENGTH(TO_VARIANT(u)) FROM ut", "36"},
            {"SELECT TO_VARIANT(u) || 'x' FROM ut", "1b4e28ba-2fa1-11d2-883f-0016d3cca427x"},
            {"SELECT UPPER(TO_VARIANT(u)) FROM ut", "1b4e28ba-2fa1-11d2-883f-0016d3cca427"},
            {"SELECT TYPEOF(TO_VARIANT(TO_VARIANT(u))) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_CONSTRUCT_COMPACT(u)[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF(OBJECT_CONSTRUCT_KEEP_NULL('k', u):k) FROM ut", "uuid"},
            {"SELECT TYPEOF(OBJECT_INSERT(OBJECT_CONSTRUCT(), 'k', u):k) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_APPEND(ARRAY_CONSTRUCT(), u)[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_PREPEND(ARRAY_CONSTRUCT(), u)[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_INSERT(ARRAY_CONSTRUCT(), 0, u)[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF([u][0]) FROM ut", "uuid"},
            {"SELECT TYPEOF({'k': u}:k) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_CAT(ARRAY_CONSTRUCT(u), ARRAY_CONSTRUCT())[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_SLICE(ARRAY_CONSTRUCT(u), 0, 1)[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF(CAST(u AS VARIANT)) FROM ut", "uuid"},
            {"SELECT IS_CHAR(TO_VARIANT(u)) FROM ut", "false"},
            {"SELECT TO_VARIANT(u) = TO_VARIANT(u) FROM ut", "true"},
            {"SELECT ARRAY_CONTAINS(TO_VARIANT(s), ARRAY_CONSTRUCT(u)) FROM ut", "false"},
            {"SELECT ARRAY_CONTAINS(TO_VARIANT(u), ARRAY_CONSTRUCT(u)) FROM ut", "true"},
            {"SELECT ARRAY_POSITION(TO_VARIANT(s), ARRAY_CONSTRUCT(u)) FROM ut", "null"},
            {"SELECT TO_VARIANT(u) = s FROM ut", "true"},
            {"SELECT TO_VARIANT(u)::VARCHAR = s FROM ut", "true"},
            {"SELECT TYPEOF(ARRAY_DISTINCT(ARRAY_CONSTRUCT(u))[0]) FROM ut", "uuid"},
            {"SELECT COUNT(DISTINCT x) FROM (SELECT TO_VARIANT(u) x FROM ut UNION ALL SELECT TO_VARIANT(s) FROM ut)", "2"},
            {"SELECT TYPEOF(GET_PATH(OBJECT_CONSTRUCT('k', u), 'k')) FROM ut", "uuid"},
            {"SELECT TYPEOF(OBJECT_CONSTRUCT('k', u)['k']) FROM ut", "uuid"},
            {"SELECT AS_VARCHAR(ARRAY_CONSTRUCT(u)[0]) FROM ut", "null"},
            {"SELECT IS_VARCHAR(ARRAY_CONSTRUCT(u)[0]) FROM ut", "false"},
            {"SELECT TYPEOF(IFF(TRUE, TO_VARIANT(u), NULL)) FROM ut", "uuid"},
            {"SELECT TO_JSON(OBJECT_CONSTRUCT('k', u)) FROM ut", "{\"k\":\"1b4e28ba-2fa1-11d2-883f-0016d3cca427\"}"},
            {"SELECT SYSTEM$TYPEOF(ARRAY_CONSTRUCT(u)[0]) FROM ut", "variant[lob]"},
            {"SELECT ARRAY_CONSTRUCT(u)[0]::UUID FROM ut", "1b4e28ba-2fa1-11d2-883f-0016d3cca427"},
            {"SELECT TYPEOF(TO_VARIANT(TO_UUID(s))) FROM ut", "uuid"},
            {"SELECT TYPEOF(TO_VARIANT(TRY_TO_UUID(s))) FROM ut", "uuid"},
            {"SELECT TYPEOF(TO_VARIANT(IFF(TRUE, u, NULL))) FROM ut", "uuid"},
            {"SELECT TYPEOF(TO_VARIANT('1b4e28ba-2fa1-11d2-883f-0016d3cca427'::UUID))", "uuid"},
            {"SELECT TYPEOF(TO_VARIANT(UUID_STRING()::UUID))", "uuid"},
            {"SELECT TYPEOF(TO_VARIANT(COALESCE(u, s))) FROM ut", "uuid"},
        });
    }

    @Test
    public void aUuidMemberNeverEqualsTheStringOfItsText() {
        assertAnswers(new String[][] {
            {"SELECT ARRAY_CONTAINS(u::VARIANT, ARRAY_CONSTRUCT(u)) FROM ut", "true"},
            {"SELECT ARRAY_CONTAINS(s::VARIANT, ARRAY_CONSTRUCT(s)) FROM ut", "true"},
            {"SELECT ARRAY_CONSTRUCT(u) = ARRAY_CONSTRUCT(s) FROM ut", "false"},
            {"SELECT OBJECT_CONSTRUCT('k', u) = OBJECT_CONSTRUCT('k', s) FROM ut", "false"},
            {"SELECT v = w FROM vt", "false"},
            {"SELECT COUNT(DISTINCT v) FROM (SELECT v FROM vt UNION ALL SELECT w FROM vt)", "2"},
            {"SELECT TYPEOF(v), TYPEOF(w) FROM vt", "uuid, varchar"},
            {"SELECT v < w, v > w FROM vt", "false, true"},
            {"SELECT ARRAY_POSITION(TO_VARIANT(u), ARRAY_CONSTRUCT(s, u)) FROM ut", "1"},
            {"SELECT ARRAY_POSITION(TO_VARIANT(s), ARRAY_CONSTRUCT(u, s)) FROM ut", "1"},
            {"SELECT ARRAY_DISTINCT(ARRAY_CONSTRUCT(u, s, u)) FROM ut", "[\"1b4e28ba-2fa1-11d2-883f-0016d3cca427\",\"1b4e28ba-2fa1-11d2-883f-0016d3cca427\"]"},
            {"SELECT ARRAY_SIZE(ARRAY_DISTINCT(ARRAY_CONSTRUCT(u, s, u))) FROM ut", "2"},
            {"SELECT ARRAYS_OVERLAP(ARRAY_CONSTRUCT(u), ARRAY_CONSTRUCT(s)) FROM ut", "false"},
            {"SELECT ARRAY_INTERSECTION(ARRAY_CONSTRUCT(u), ARRAY_CONSTRUCT(s)) FROM ut", "[]"},
            {"SELECT COUNT(*) FROM vt GROUP BY v", "1"},
            {"SELECT TYPEOF(ANY_VALUE(v)) FROM vt", "uuid"},
            {"SELECT TYPEOF(x) FROM (SELECT TO_VARIANT(u) x FROM ut UNION ALL SELECT TO_VARIANT(s) FROM ut) ORDER BY 1", "uuid | varchar"},
            {"SELECT TYPEOF(OBJECT_CONSTRUCT('k', u)['k']) FROM ut", "uuid"},
            {"SELECT IS_VARCHAR(v:k) FROM (SELECT OBJECT_CONSTRUCT('k', u) v FROM ut)", "false"},
            {"SELECT TYPEOF(TO_VARIANT(x)) FROM (SELECT u x FROM ut)", "uuid"},
            {"SELECT TYPEOF(ARRAY_REVERSE(ARRAY_CONSTRUCT(u))[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_SORT(ARRAY_CONSTRUCT(u))[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_COMPACT(ARRAY_CONSTRUCT(u))[0]) FROM ut", "uuid"},
            {"SELECT TYPEOF(ARRAY_MIN(ARRAY_CONSTRUCT(u))) FROM ut", "uuid"},
            {"SELECT TYPEOF(OBJECT_PICK(OBJECT_CONSTRUCT('k', u), 'k'):k) FROM ut", "uuid"},
            {"SELECT TYPEOF(GET(v, 'x')) FROM vt", "null"},
        });
    }
}

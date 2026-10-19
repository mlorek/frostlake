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

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assumptions.assumeFalse;

/**
 * A DECFLOAT inside a VARIANT, an ARRAY or an OBJECT is the JSON string of its text whose TYPEOF is DECFLOAT
 * (live-verified): it casts back to a DECFLOAT and to text, a NUMBER, a FLOAT, a BOOLEAN and arithmetic refuse it,
 * IS_ answers FALSE and AS_ NULL, it never equals a NUMBER or a text holding the same digits yet hashes like the
 * number, and a VARIANT column written from a DECFLOAT keeps the type.
 */
public class DecfloatVariantTest extends MeasuredCellsTestSupport {

    @Test
    public void aDecfloatMemberKeepsItsType() {
        engine.execute("CREATE OR REPLACE TABLE dd (d DECFLOAT)");
        engine.execute("INSERT INTO dd SELECT 1.5 UNION ALL SELECT 1::DECFLOAT / 3");
        engine.execute("CREATE OR REPLACE TABLE dv (v VARIANT)");
        engine.execute("INSERT INTO dv SELECT TO_VARIANT(1.5::DECFLOAT)");
        engine.execute("INSERT INTO dv SELECT d FROM dd");
        assertCells(new String[][] {
            {"SELECT TYPEOF(TO_VARIANT(1.5::DECFLOAT)), TO_JSON(TO_VARIANT(1.5::DECFLOAT)), "
                + "TO_VARIANT(1.5::DECFLOAT)::VARCHAR, SYSTEM$TYPEOF(TO_VARIANT(1.5::DECFLOAT))",
                "DECFLOAT|\"1.5\"|1.5|VARIANT[LOB]"},
            {"SELECT TYPEOF(TO_VARIANT(1::DECFLOAT / 3)), TO_JSON(TO_VARIANT(1::DECFLOAT / 3)), "
                + "TO_VARIANT(1::DECFLOAT / 3)::VARCHAR",
                "DECFLOAT|\"3.3333333333333333333333333333333333333e-1\"|3.3333333333333333333333333333333333333e-1"},
            {"SELECT TO_JSON(TO_VARIANT(1e40::DECFLOAT)), TO_VARIANT(1e40::DECFLOAT)::VARCHAR",
                "\"1.0000000000000000303786028427003666891e40\"|1.0000000000000000303786028427003666891e40"},
            {"SELECT TO_JSON(TO_VARIANT(12345678901234567890123456789012345678::DECFLOAT))",
                "\"12345678901234567890123456789012345678\""},
            {"SELECT TO_JSON(TO_VARIANT(0::DECFLOAT)), TO_JSON(TO_VARIANT(-0.5::DECFLOAT)), "
                + "TO_JSON(TO_VARIANT(100::DECFLOAT))",
                "\"0\"|\"-0.5\"|\"100\""},
            {"SELECT TYPEOF((1.5::DECFLOAT)::VARIANT), TO_JSON((1.5::DECFLOAT)::VARIANT)", "DECFLOAT|\"1.5\""},
            {"SELECT TO_VARIANT(1.5::DECFLOAT)::DECFLOAT, SYSTEM$TYPEOF(TO_VARIANT(1.5::DECFLOAT)::DECFLOAT), "
                + "TO_VARIANT(1.5::DECFLOAT)::NUMBER(10,2), TO_VARIANT(1.5::DECFLOAT)::FLOAT, "
                + "TO_VARIANT(1.5::DECFLOAT)::INT",
                "REFUSED Failed to cast variant value \"1.5\" to FIXED"},
            {"SELECT TO_VARIANT(1::DECFLOAT / 3)::DECFLOAT::VARCHAR, TO_VARIANT(1::DECFLOAT / 3)::FLOAT, "
                + "SYSTEM$TYPEOF(TO_VARIANT(1::DECFLOAT / 3)::DECFLOAT)",
                "REFUSED Failed to cast variant value \"3.3333333333333333333333333333333333333e-1\" to REAL"},
            {"SELECT TO_VARIANT(1.5::DECFLOAT)::BOOLEAN", "REFUSED Failed to cast variant value \"1.5\" to BOOLEAN"},
            {"SELECT (TO_VARIANT(1.5::DECFLOAT) + 1)::VARCHAR, SYSTEM$TYPEOF(TO_VARIANT(1.5::DECFLOAT) + 1)",
                "REFUSED Failed to cast variant value \"1.5\" to REAL"},
            {"SELECT (TO_VARIANT(1::DECFLOAT / 3) * 3)::VARCHAR, SYSTEM$TYPEOF(TO_VARIANT(1::DECFLOAT / 3) * 3)",
                "REFUSED Failed to cast variant value \"3.3333333333333333333333333333333333333e-1\" to REAL"},
            {"SELECT IS_DECIMAL(TO_VARIANT(1.5::DECFLOAT)), IS_DOUBLE(TO_VARIANT(1.5::DECFLOAT)), "
                + "IS_REAL(TO_VARIANT(1.5::DECFLOAT)), IS_INTEGER(TO_VARIANT(1.5::DECFLOAT)), "
                + "IS_VARCHAR(TO_VARIANT(1.5::DECFLOAT))",
                "false|false|false|false|false"},
            {"SELECT AS_DECIMAL(TO_VARIANT(1.5::DECFLOAT)), AS_DECIMAL(TO_VARIANT(1.5::DECFLOAT), 10, 2), "
                + "AS_DOUBLE(TO_VARIANT(1.5::DECFLOAT)), AS_REAL(TO_VARIANT(1.5::DECFLOAT)), "
                + "AS_INTEGER(TO_VARIANT(1.5::DECFLOAT))",
                "null|null|null|null|null"},
            {"SELECT AS_VARCHAR(TO_VARIANT(1.5::DECFLOAT)), AS_CHAR(TO_VARIANT(1.5::DECFLOAT)), "
                + "AS_NUMBER(TO_VARIANT(1.5::DECFLOAT))",
                "null|null|null"},
            {"SELECT OBJECT_CONSTRUCT('a', 1.5::DECFLOAT)::VARCHAR, TYPEOF(OBJECT_CONSTRUCT('a', 1.5::DECFLOAT):a)",
                "{\"a\":\"1.5\"}|DECFLOAT"},
            {"SELECT OBJECT_CONSTRUCT('a', 1.5::DECFLOAT):a::VARCHAR, SYSTEM$TYPEOF(OBJECT_CONSTRUCT('a', "
                + "1.5::DECFLOAT):a)",
                "1.5|VARIANT[LOB]"},
            {"SELECT ARRAY_CONSTRUCT(1.5::DECFLOAT, 1::DECFLOAT / 3)::VARCHAR, "
                + "TYPEOF(ARRAY_CONSTRUCT(1.5::DECFLOAT)[0])",
                "[\"1.5\",\"3.3333333333333333333333333333333333333e-1\"]|DECFLOAT"},
            {"SELECT (ARRAY_CONSTRUCT(1.5::DECFLOAT)[0] + 1)::VARCHAR, "
                + "SYSTEM$TYPEOF(ARRAY_CONSTRUCT(1.5::DECFLOAT)[0] + 1)",
                "REFUSED Failed to cast variant value \"1.5\" to REAL"},
            {"SELECT ARRAY_CONTAINS(TO_VARIANT(1.5::DECFLOAT), ARRAY_CONSTRUCT(1.5::DECFLOAT)), "
                + "ARRAY_CONTAINS(1.5::VARIANT, ARRAY_CONSTRUCT(1.5::DECFLOAT))",
                "true|false"},
            {"SELECT ARRAY_CONTAINS('1.5'::VARIANT, ARRAY_CONSTRUCT(1.5::DECFLOAT))", "false"},
            {"SELECT TO_VARIANT(1.5::DECFLOAT) = TO_VARIANT(1.5), TO_VARIANT(1.5::DECFLOAT) = TO_VARIANT('1.5'), "
                + "TO_VARIANT(1.5::DECFLOAT) = 1.5",
                "false|false|false"},
            {"SELECT TYPEOF(PARSE_JSON(TO_JSON(TO_VARIANT(1.5::DECFLOAT)))), TYPEOF(PARSE_JSON('1.5'))",
                "VARCHAR|DECIMAL"},
            {"SELECT TO_ARRAY(1.5::DECFLOAT)::VARCHAR, OBJECT_INSERT(OBJECT_CONSTRUCT(), 'k', 1.5::DECFLOAT)::VARCHAR",
                "REFUSED SQL compilation error: | invalid type [TO_ARRAY(TO_DECFLOAT(1.5))] for parameter 'TO_ARRAY'"},
            {"SELECT ARRAY_APPEND(ARRAY_CONSTRUCT(), 1.5::DECFLOAT)::VARCHAR, ARRAY_SORT(ARRAY_CONSTRUCT(2::DECFLOAT, "
                + "1, 1.5::DECFLOAT))::VARCHAR",
                "[\"1.5\"]|[1,\"1.5\",\"2\"]"},
            {"SELECT HASH(TO_VARIANT(1.5::DECFLOAT)) = HASH(TO_VARIANT(1.5)), HASH(TO_VARIANT(1.5::DECFLOAT)) = "
                + "HASH(1.5::DECFLOAT)",
                "true|true"},
            {"SELECT TO_DECFLOAT(TO_VARIANT(1.5::DECFLOAT))::VARCHAR, "
                + "TRY_TO_DECFLOAT(TO_VARIANT(1.5::DECFLOAT))::VARCHAR",
                "REFUSED SQL compilation error: | Function TRY_CAST cannot be used with arguments of types VARIANT "
                + "and DECFLOAT(38)"},
            {"SELECT TYPEOF(value), value::VARCHAR FROM TABLE(FLATTEN(ARRAY_CONSTRUCT(1.5::DECFLOAT, 2)))",
                "DECFLOAT|1.5 ;; INTEGER|2"},
            {"SELECT TYPEOF(IFF(TRUE, TO_VARIANT(1.5::DECFLOAT), TO_VARIANT(1)))", "DECFLOAT"},
            {"SELECT CAST(TO_VARIANT(1e40::DECFLOAT) AS NUMBER)",
                "REFUSED Failed to cast variant value \"1.0000000000000000303786028427003666891e40\" to FIXED"},
            {"SELECT TO_VARIANT(1.5::DECFLOAT)::DECFLOAT = 1.5, TO_CHAR(TO_VARIANT(1.5::DECFLOAT))", "true|1.5"},
            {"SELECT TYPEOF(v), TO_JSON(v), v::VARCHAR FROM dv ORDER BY 3",
                "DECFLOAT|\"1.5\"|1.5 ;; DECFLOAT|\"1.5\"|1.5 ;; DECFLOAT|"
                + "\"3.3333333333333333333333333333333333333e-1\"|3.3333333333333333333333333333333333333e-1"},
            {"SELECT TYPEOF(GET(PARSE_JSON('[1.5]'), 0)), TYPEOF(TO_VARIANT(1.5))", "DECIMAL|DECIMAL"},
            {"SELECT MAX(v)::VARCHAR, MIN(v)::VARCHAR FROM dv", "1.5|3.3333333333333333333333333333333333333e-1"},
            {"SELECT TYPEOF(v:a) FROM (SELECT OBJECT_CONSTRUCT('a', d) AS v FROM dd) ORDER BY 1",
                "DECFLOAT ;; DECFLOAT"},
        });
    }

    /**
     * ARRAY_AGG and OBJECT_AGG over a DECFLOAT are an internal error on the account (an incident), which the engine
     * does not imitate: they gather DECFLOAT members, as ARRAY_CONSTRUCT and OBJECT_CONSTRUCT do.
     */
    @Test
    public void theAggregatesGatherDecfloatMembers() {
        assumeFalse(isLiveSnowflake(), "the account answers an internal error here");
        engine.execute("CREATE OR REPLACE TABLE dd (d DECFLOAT)");
        engine.execute("INSERT INTO dd SELECT 1.5 UNION ALL SELECT 1::DECFLOAT / 3");
        assertCells(new String[][] {
            {"SELECT ARRAY_AGG(d) WITHIN GROUP (ORDER BY d)::VARCHAR FROM dd",
                "[\"3.3333333333333333333333333333333333333e-1\",\"1.5\"]"},
            {"SELECT TYPEOF(ARRAY_AGG(d)[0]) FROM dd", "DECFLOAT"},
            {"SELECT OBJECT_AGG('k', d)::VARCHAR FROM (SELECT 1.5::DECFLOAT AS d)", "{\"k\":\"1.5\"}"},
        });
    }
}

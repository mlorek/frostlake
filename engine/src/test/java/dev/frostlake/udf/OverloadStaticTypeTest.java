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

package dev.frostlake.udf;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A call reaches the overload its argument's STATIC type prefers, not its value's: an ARRAY_CONSTRUCT, an ARRAY
 * column, a cast to ARRAY, an array literal and ARRAY_AGG are ARRAYs, a PARSE_JSON, a VARIANT column and a path into
 * one are VARIANTs, though all carry the same kind of value. An untyped NULL ranks the overloads in one order of its
 * own, whatever order they were declared in: ARRAY, BINARY, BOOLEAN, DATE, NUMBER, OBJECT, FLOAT, VARCHAR, TIME,
 * TIMESTAMP, VARIANT; a typed NULL follows its type. Every cell is live-verified.
 */
public class OverloadStaticTypeTest extends BaseDatabaseTest {

    /** The first row's first cell. */
    private String answer(final String sql) {
        for (final Row row : engine.executeQuery(sql).getRows()) {
            return String.valueOf(row.getValue(0));
        }
        return "no row";
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    @Test
    public void theArgumentsStaticTypeChoosesTheOverload() {
        engine.execute("CREATE FUNCTION ova(p VARIANT) RETURNS VARCHAR AS $$'v'$$");
        engine.execute("CREATE FUNCTION ova(p ARRAY) RETURNS VARCHAR AS $$'a'$$");
        engine.execute("CREATE FUNCTION ovo(p VARIANT) RETURNS VARCHAR AS $$'v'$$");
        engine.execute("CREATE FUNCTION ovo(p OBJECT) RETURNS VARCHAR AS $$'o'$$");
        engine.execute("CREATE FUNCTION oao(p ARRAY) RETURNS VARCHAR AS $$'a'$$");
        engine.execute("CREATE FUNCTION oao(p OBJECT) RETURNS VARCHAR AS $$'o'$$");
        engine.execute("CREATE TABLE tv (v VARIANT, a ARRAY, o OBJECT)");
        engine.execute("INSERT INTO tv SELECT PARSE_JSON('[1]'), ARRAY_CONSTRUCT(1), OBJECT_CONSTRUCT('a', 1)");
        assertCells(new String[][] {
            {"SELECT ova(PARSE_JSON('[1]'))", "v"},
            {"SELECT ova(ARRAY_CONSTRUCT(1))", "a"},
            {"SELECT ovo(PARSE_JSON('{\"a\":1}'))", "v"},
            {"SELECT ovo(OBJECT_CONSTRUCT('a',1))", "o"},
            {"SELECT oao(OBJECT_CONSTRUCT('a',1))", "o"},
            {"SELECT oao(ARRAY_CONSTRUCT(1))", "a"},
            {"SELECT ova(v) FROM tv", "v"},
            {"SELECT ova(a) FROM tv", "a"},
            {"SELECT ovo(o) FROM tv", "o"},
            {"SELECT oao(o) FROM tv", "o"},
            {"SELECT oao(a) FROM tv", "a"},
            {"SELECT ova(PARSE_JSON('[1]')::ARRAY)", "a"},
            {"SELECT ova(ARRAY_CONSTRUCT(1)::VARIANT)", "v"},
            {"SELECT ovo(PARSE_JSON('{\"a\":1}')::OBJECT)", "o"},
            {"SELECT oao(PARSE_JSON('{\"a\":1}')::OBJECT)", "o"},
            {"SELECT ova(ARRAY_APPEND(ARRAY_CONSTRUCT(1), 2))", "a"},
            {"SELECT ova(SPLIT('a,b', ','))", "a"},
            {"SELECT ovo(OBJECT_INSERT(OBJECT_CONSTRUCT('a',1), 'b', 2))", "o"},
            {"SELECT ova(v:x) FROM tv", "v"},
            {"SELECT ova(TO_ARRAY(1))", "a"},
            {"SELECT ovo(TO_OBJECT(PARSE_JSON('{\"a\":1}')))", "o"},
            {"SELECT ova([1,2])", "a"},
            {"SELECT ovo({'a': 1})", "o"},
            {"SELECT ova(ARRAY_AGG(1)) FROM tv", "a"},
            {"SELECT ova(IFF(TRUE, ARRAY_CONSTRUCT(1), NULL))", "a"},
            {"SELECT ova(COALESCE(a, ARRAY_CONSTRUCT())) FROM tv", "a"},
            {"SELECT ova(COALESCE(v, a)) FROM tv", "v"},
            {"SELECT ova(NULL)", "a"},
            {"SELECT oao(NULL)", "a"},
        });
    }

    @Test
    public void anUntypedNullRanksEveryTypeInOneOrder() {
        final String[][] pairs = {
            {"r01", "NUMBER", "FLOAT", "NUMBER"},
            {"r02", "VARCHAR", "NUMBER", "NUMBER"},
            {"r03", "NUMBER", "BOOLEAN", "BOOLEAN"},
            {"r04", "DATE", "NUMBER", "DATE"},
            {"r05", "NUMBER", "TIMESTAMP_NTZ", "NUMBER"},
            {"r06", "TIME", "NUMBER", "NUMBER"},
            {"r07", "NUMBER", "BINARY", "BINARY"},
            {"r08", "VARIANT", "NUMBER", "NUMBER"},
            {"r09", "NUMBER", "OBJECT", "NUMBER"},
            {"r10", "ARRAY", "NUMBER", "ARRAY"},
            {"r11", "FLOAT", "VARCHAR", "FLOAT"},
            {"r12", "BOOLEAN", "FLOAT", "BOOLEAN"},
            {"r13", "FLOAT", "DATE", "DATE"},
            {"r14", "TIMESTAMP_NTZ", "FLOAT", "FLOAT"},
            {"r15", "FLOAT", "TIME", "FLOAT"},
            {"r16", "BINARY", "FLOAT", "BINARY"},
            {"r17", "FLOAT", "VARIANT", "FLOAT"},
            {"r18", "OBJECT", "FLOAT", "OBJECT"},
            {"r19", "FLOAT", "ARRAY", "ARRAY"},
            {"r20", "BOOLEAN", "VARCHAR", "BOOLEAN"},
            {"r21", "VARCHAR", "DATE", "DATE"},
            {"r22", "TIMESTAMP_NTZ", "VARCHAR", "VARCHAR"},
            {"r23", "VARCHAR", "TIME", "VARCHAR"},
            {"r24", "BINARY", "VARCHAR", "BINARY"},
            {"r25", "VARCHAR", "VARIANT", "VARCHAR"},
            {"r26", "OBJECT", "VARCHAR", "OBJECT"},
            {"r27", "VARCHAR", "ARRAY", "ARRAY"},
            {"r28", "DATE", "BOOLEAN", "BOOLEAN"},
            {"r29", "BOOLEAN", "TIMESTAMP_NTZ", "BOOLEAN"},
            {"r30", "TIME", "BOOLEAN", "BOOLEAN"},
            {"r31", "BOOLEAN", "BINARY", "BINARY"},
            {"r32", "VARIANT", "BOOLEAN", "BOOLEAN"},
            {"r33", "BOOLEAN", "OBJECT", "BOOLEAN"},
            {"r34", "ARRAY", "BOOLEAN", "ARRAY"},
            {"r35", "DATE", "TIMESTAMP_NTZ", "DATE"},
            {"r36", "TIME", "DATE", "DATE"},
            {"r37", "DATE", "BINARY", "BINARY"},
            {"r38", "VARIANT", "DATE", "DATE"},
            {"r39", "DATE", "OBJECT", "DATE"},
            {"r40", "ARRAY", "DATE", "ARRAY"},
            {"r41", "TIMESTAMP_NTZ", "TIME", "TIME"},
            {"r42", "BINARY", "TIMESTAMP_NTZ", "BINARY"},
            {"r43", "TIMESTAMP_NTZ", "VARIANT", "TIMESTAMP_NTZ"},
            {"r44", "OBJECT", "TIMESTAMP_NTZ", "OBJECT"},
            {"r45", "TIMESTAMP_NTZ", "ARRAY", "ARRAY"},
            {"r46", "BINARY", "TIME", "BINARY"},
            {"r47", "TIME", "VARIANT", "TIME"},
            {"r48", "OBJECT", "TIME", "OBJECT"},
            {"r49", "TIME", "ARRAY", "ARRAY"},
            {"r50", "VARIANT", "BINARY", "BINARY"},
            {"r51", "BINARY", "OBJECT", "BINARY"},
            {"r52", "ARRAY", "BINARY", "ARRAY"},
            {"r53", "VARIANT", "OBJECT", "OBJECT"},
            {"r54", "ARRAY", "VARIANT", "ARRAY"},
            {"r55", "OBJECT", "ARRAY", "ARRAY"},
        };
        for (final String[] pair : pairs) {
            engine.execute("CREATE FUNCTION " + pair[0] + "(p " + pair[1] + ") RETURNS VARCHAR AS $$'" + pair[1] + "'$$");
            engine.execute("CREATE FUNCTION " + pair[0] + "(p " + pair[2] + ") RETURNS VARCHAR AS $$'" + pair[2] + "'$$");
        }
        for (final String[] pair : pairs) {
            assertEquals(pair[3], answer("SELECT " + pair[0] + "(NULL)"), pair[0] + " over " + pair[1] + ", " + pair[2]);
        }
    }

    @Test
    public void aTypedNullFollowsItsType() {
        engine.execute("CREATE FUNCTION n1(p OBJECT) RETURNS VARCHAR AS $$'o'$$");
        engine.execute("CREATE FUNCTION n1(p VARIANT) RETURNS VARCHAR AS $$'v'$$");
        engine.execute("CREATE FUNCTION n3(p NUMBER) RETURNS VARCHAR AS $$'n'$$");
        engine.execute("CREATE FUNCTION n3(p VARCHAR) RETURNS VARCHAR AS $$'s'$$");
        assertCells(new String[][] {
            {"SELECT n1(NULL)", "o"},
            {"SELECT n1(NULL::VARIANT)", "v"},
            {"SELECT n3(NULL)", "n"},
            {"SELECT n3(NULL::VARCHAR)", "s"},
        });
    }
}

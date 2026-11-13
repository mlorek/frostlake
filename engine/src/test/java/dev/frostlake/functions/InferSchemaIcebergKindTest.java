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

package dev.frostlake.functions;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * INFER_SCHEMA under {@code KIND => 'ICEBERG'}: the column types an Iceberg table takes — an integer of at most
 * eighteen digits a LONG, a REAL a DOUBLE, a timestamp TIMESTAMP_NTZ(6), a time TIME(6) — a LONG beside a decimal
 * refused, a JSON array or object refused as soon as its record is read, and a file read to its end before its
 * first conflict refuses the call.
 */
public class InferSchemaIcebergKindTest extends BaseDatabaseTest {

    private static final String ICEBERG = ", KIND => 'ICEBERG'";

    @Override
    protected void setupTest() {
        engine.execute("CREATE STAGE st");
        engine.execute("CREATE FILE FORMAT ffh TYPE = CSV PARSE_HEADER = TRUE");
        engine.execute("CREATE FILE FORMAT fj TYPE = JSON");
    }

    @Test
    public void icebergColumnsTakeTheIcebergTypes() {
        stageLocalFile("st/c/", "all.csv", """
            a,b,c,d,e,f,g,h,i,j,k
            1,x,2.5,1e2,true,2020-01-01,2020-01-01 10:00:00,10:00:00,12345678901234567890,123456789012,-3
            """);
        assertEquals(Arrays.asList("a | LONG | $1::LONG", "b | TEXT | $2::TEXT", "c | NUMBER(2, 1) | $3::NUMBER(2, 1)",
            "d | DOUBLE | $4::DOUBLE", "e | BOOLEAN | $5::BOOLEAN", "f | DATE | $6::DATE",
            "g | TIMESTAMP_NTZ(6) | $7::TIMESTAMP_NTZ(6)", "h | TIME(6) | $8::TIME(6)",
            "i | NUMBER(20, 0) | $9::NUMBER(20, 0)", "j | LONG | $10::LONG", "k | LONG | $11::LONG"),
            lines("SELECT COLUMN_NAME, TYPE, EXPRESSION FROM TABLE(INFER_SCHEMA(LOCATION => '@st/c/', "
                + "FILE_FORMAT => 'ffh'" + ICEBERG + ")) ORDER BY ORDER_ID"));
        // The standard kind keeps its own spellings.
        assertEquals(Arrays.asList("a | NUMBER(1, 0)", "b | TEXT", "c | NUMBER(2, 1)", "d | REAL", "e | BOOLEAN",
            "f | DATE", "g | TIMESTAMP_NTZ", "h | TIME", "i | NUMBER(20, 0)", "j | NUMBER(12, 0)", "k | NUMBER(1, 0)"),
            types("c", "ffh", ""));
        // An integer of eighteen digits is a LONG, one of nineteen a NUMBER whatever its value.
        stageLocalFile("st/n/", "nums.csv", """
            p9,p10,p18,p19a,p19b,p20,neg18,neg19,z0,f18,d38,dot,lead
            123456789,1234567890,123456789012345678,1234567890123456789,9999999999999999999,\
            12345678901234567890,-123456789012345678,-9223372036854775808,0,12345678901234567.5,\
            12345678901234567890123456789012345678,1.,007
            """);
        assertEquals(Arrays.asList("p9 | LONG", "p10 | LONG", "p18 | LONG", "p19a | NUMBER(19, 0)",
            "p19b | NUMBER(19, 0)", "p20 | NUMBER(20, 0)", "neg18 | LONG", "neg19 | NUMBER(19, 0)", "z0 | LONG",
            "f18 | NUMBER(18, 1)", "d38 | NUMBER(38, 0)", "dot | LONG", "lead | LONG"), types("n", "ffh", ICEBERG));
        stageLocalFile("st/js/", "scalars.json", "{\"n\":5,\"s\":\"x\",\"d\":2.5,\"f\":1e2,\"b\":true,"
            + "\"big\":12345678901234567890,\"ts\":\"2020-01-01 10:00:00\",\"t\":\"10:00:00\",\"dt\":\"2020-01-01\","
            + "\"nul\":null}\n");
        assertEquals(Arrays.asList("b | BOOLEAN | $1:b::BOOLEAN", "big | NUMBER(20, 0) | $1:big::NUMBER(20, 0)",
            "d | NUMBER(2, 1) | $1:d::NUMBER(2, 1)", "dt | DATE | $1:dt::DATE", "f | DOUBLE | $1:f::DOUBLE",
            "n | LONG | $1:n::LONG", "nul | TEXT | $1:nul::TEXT", "s | TEXT | $1:s::TEXT",
            "t | TIME(6) | $1:t::TIME(6)",
            "ts | TIMESTAMP_NTZ(6) | $1:ts::TIMESTAMP_NTZ(6)"),
            lines("SELECT COLUMN_NAME, TYPE, EXPRESSION FROM TABLE(INFER_SCHEMA(LOCATION => '@st/js/', "
                + "FILE_FORMAT => 'fj'" + ICEBERG + ")) ORDER BY ORDER_ID"));
    }

    @Test
    public void numbersMeetOnlyAsTwoLongsOrTwoDecimals() {
        final String[][] cells = {
            {"dd", "2.5\n12.25", "NUMBER(4, 2)"},
            {"bb", "1234567890123456789\n12345678901234567890", "NUMBER(20, 0)"},
            {"db", "2.5\n1234567890123456789", "NUMBER(20, 1)"},
            {"ll", "1\n123456789012345678", "LONG"},
            {"rl", "1e2\n1", "DOUBLE"},
            {"rd", "1e2\n2.5", "DOUBLE"},
            {"dts", "2020-01-01\n2020-01-01 10:00:00", "DATE"},
            {"tl", "x\n1", "TEXT"},
        };
        for (final String[] cell : cells) {
            stageLocalFile("st/" + cell[0] + "/", cell[0] + ".csv", "a\n" + cell[1] + "\n");
            assertEquals(Arrays.asList("a | " + cell[2]), types(cell[0], "ffh", ICEBERG), cell[0]);
        }
        final String[][] refused = {
            {"dl", "a\n2.5\n1\n", "long and decimal"},
            {"lb", "a\n123456789012345678\n1234567890123456789\n", "decimal and long"},
            {"bl", "a\n1234567890123456789\n1\n", "long and decimal"},
            {"tsd", "a\n2020-01-01 10:00:00\n2020-01-01\n", "DATE and TIMESTAMP_NTZ"},
            {"lt", "a\n1\nx\n", "TEXT and FIXED"},
            {"two", "a,b\n1,2.5\n2.5,1\n", "decimal and long"},
        };
        for (final String[] cell : refused) {
            stageLocalFile("st/" + cell[0] + "/", cell[0] + ".csv", cell[1]);
            assertEquals("Incompatible data types detected: " + cell[2] + ".", refusalOf(cell[0], "ffh"), cell[0]);
        }
        final String[][] json = {
            {"jv", "{\"a\":1}\n{\"a\":2.5}\n", "decimal and long"},
            {"ri", "{\"a\":1.5}\n{\"a\":1}\n", "long and decimal"},
            {"rj", "{\"a\":1e2}\n{\"a\":1}\n", "FIXED and REAL"},
            {"rk", "{\"a\":1}\n{\"a\":1e2}\n", "REAL and FIXED"},
        };
        for (final String[] cell : json) {
            stageLocalFile("st/" + cell[0] + "/", cell[0] + ".json", cell[1]);
            assertEquals("Incompatible data types detected: " + cell[2] + ".", refusalOf(cell[0], "fj"), cell[0]);
        }
        stageLocalFile("st/rl2/", "rl2.json", "{\"a\":\"2020-01-01\"}\n{\"a\":\"2020-01-01 10:00:00\"}\n");
        assertEquals(Arrays.asList("a | DATE"), types("rl2", "fj", ICEBERG));
    }

    @Test
    public void filesMeetAsTheirValuesDo() {
        stageLocalFile("st/xg/", "g1.csv", "a\n2.5\n");
        stageLocalFile("st/xg/", "g2.csv", "a\n12.25\n");
        assertEquals(Arrays.asList("a | NUMBER(4, 2)"), types("xg", "ffh", ICEBERG));
        stageLocalFile("st/xh/", "h1.csv", "a\n1\n");
        stageLocalFile("st/xh/", "h2.csv", "a\n1234567890\n");
        assertEquals(Arrays.asList("a | LONG"), types("xh", "ffh", ICEBERG));
        // Files are read side by side on the account, so a conflict between two names them in either order.
        final String[][] refused = {
            {"xa", "1", "2.5", "long", "decimal"},
            {"xc", "1", "x", "FIXED", "TEXT"},
            {"xf", "1e2", "1", "FIXED", "REAL"},
            {"xi", "2020-01-01 10:00:00", "2020-01-01", "DATE", "TIMESTAMP_NTZ"},
        };
        for (final String[] cell : refused) {
            stageLocalFile("st/" + cell[0] + "/", "f1.csv", "a\n" + cell[1] + "\n");
            stageLocalFile("st/" + cell[0] + "/", "f2.csv", "a\n" + cell[2] + "\n");
            final String refusal = refusalOf(cell[0], "ffh");
            assertTrue(refusal.equals("Incompatible data types detected: " + cell[3] + " and " + cell[4] + ".")
                || refusal.equals("Incompatible data types detected: " + cell[4] + " and " + cell[3] + "."),
                cell[0] + ": " + refusal);
        }
    }

    @Test
    public void anArrayOrAnObjectIsRefusedAsItsRecordIsRead() {
        final String[][] cells = {
            {"ic", "{\"n\":5,\"s\":\"x\",\"o\":{\"k\":1},\"a\":[1,2],\"big\":12345678901234567890}\n", "ARRAY"},
            {"ra", "{\"m\":[1],\"z\":{\"k\":1},\"a\":5}\n", "ARRAY"},
            {"oo", "{\"o\":{\"k\":1},\"n\":1}\n", "OBJECT"},
            {"qa", "{\"b\":[1]}\n{\"a\":{\"k\":1}}\n", "ARRAY"},
            {"jw", "{\"a\":{\"k\":1}}\n{\"a\":2}\n", "OBJECT"},
            {"re", "{\"a\":1}\n{\"a\":[1]}\n", "ARRAY"},
            // A conflict waits for the file's end, so an array or object after it is refused instead.
            {"rb", "{\"a\":1}\n{\"a\":2.5}\n{\"b\":{\"k\":1}}\n", "OBJECT"},
            {"rc", "{\"a\":1}\n{\"a\":\"x\"}\n{\"b\":[1]}\n", "ARRAY"},
            {"rd", "{\"b\":1}\n{\"b\":2.5,\"a\":[1]}\n", "ARRAY"},
            {"jy", "{\"a\":1,\"b\":2.5}\n{\"a\":2.5,\"b\":{\"k\":1}}\n", "OBJECT"},
        };
        for (final String[] cell : cells) {
            stageLocalFile("st/" + cell[0] + "/", cell[0] + ".json", cell[1]);
            assertEquals("INFER_SCHEMA cannot produce an Iceberg column definition for type '" + cell[2]
                + "' when KIND => 'ICEBERG'.", refusalOf(cell[0], "fj"), cell[0]);
        }
        // The standard kind reads an array beside a number as TEXT.
        stageLocalFile("st/rf/", "rf.json", "{\"a\":[1]}\n{\"a\":1}\n");
        assertEquals(Arrays.asList("a | TEXT"), types("rf", "fj", ""));
        stageLocalFile("st/rg/", "rg.json", "{\"a\":1}\n{\"a\":[1]}\n");
        assertEquals(Arrays.asList("a | TEXT"), types("rg", "fj", ""));
    }

    @Test
    public void aFileIsReadToItsEndBeforeItsFirstConflict() {
        final String[][] cells = {
            {"qb", "{\"z\":1}\n{\"z\":2.5}\n{\"a\":1}\n{\"a\":\"x\"}\n", "decimal and long"},
            {"qc", "{\"z\":1,\"a\":1}\n{\"z\":2.5}\n{\"a\":\"x\"}\n", "decimal and long"},
            {"qd", "{\"a\":1}\n{\"a\":2.5}\n{\"a\":\"x\"}\n", "decimal and long"},
            {"qe", "{\"a\":1}\n{\"a\":\"x\"}\n{\"a\":2.5}\n", "TEXT and FIXED"},
            {"qf", "{\"a\":1}\n{\"a\":2.5}\n{\"a\":3}\n{\"a\":4.5}\n", "decimal and long"},
            {"rh", "{\"z\":1}\n{\"z\":2.5,\"a\":\"x\"}\n{\"z\":3}\n", "decimal and long"},
        };
        for (final String[] cell : cells) {
            stageLocalFile("st/" + cell[0] + "/", cell[0] + ".json", cell[1]);
            assertEquals("Incompatible data types detected: " + cell[2] + ".", refusalOf(cell[0], "fj"), cell[0]);
        }
        // A malformed record after the conflict is refused instead.
        stageLocalFile("st/qg/", "qg.json", "{\"a\":1}\n{\"a\":2.5}\n{\"a\":tru\n");
        final String malformed = refusalOf("qg", "fj");
        assertTrue(malformed.startsWith("Error parsing JSON: "), malformed);
        stageLocalFile("st/qh/", "qh.csv", "a\n1\n2.5\n3,4\n");
        final String wide = refusalOf("qh", "ffh");
        assertTrue(wide.startsWith("Error with CSV header: header defined (1) columns while data contains more"
            + " columns"), wide);
    }

    /** Each column's name and type, in ORDER_ID order, of the files under a path of stage {@code st}. */
    private List<String> types(final String path, final String format, final String kind) {
        return lines("SELECT COLUMN_NAME, TYPE FROM TABLE(INFER_SCHEMA(LOCATION => '@st/" + path
            + "/', FILE_FORMAT => '" + format + "'" + kind + ")) ORDER BY ORDER_ID");
    }

    /** The refusal of an Iceberg-kind call over the files under a path of stage {@code st}. */
    private String refusalOf(final String path, final String format) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("SELECT * FROM TABLE(INFER_SCHEMA(LOCATION => '@st/" + path
                    + "/', FILE_FORMAT => '" + format + "'" + ICEBERG + "))");
            }
        }).getMessage();
    }

    /** A query's rows, each cell as text, joined by {@code " | "}. */
    private List<String> lines(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> lines = new ArrayList<String>();
        for (final Row row : rs.getRows()) {
            final StringBuilder line = new StringBuilder();
            for (int i = 0; i < rs.getColumns().size(); i++) {
                line.append(i == 0 ? "" : " | ").append(row.getValue(i));
            }
            lines.add(line.toString());
        }
        return lines;
    }
}

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
import dev.frostlake.storage.ResultSetColumn;
import dev.frostlake.storage.Row;
import dev.frostlake.types.DataType;
import dev.frostlake.types.StringType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a statement's compilation types before it refuses. The operand of a CAST is typed before the cast, so
 * an IFF over a non-boolean condition is refused as IFF inside a CAST too. The date part functions refuse a
 * text or a VARIANT argument, naming DATE_TRUNC or LAST_DAY for those two and EXTRACT for the rest. A date
 * arithmetic or truncation call over the bare word NULL has no type at all, so any cast of it compiles and it
 * projects the VARCHAR(0) a bare NULL does. A USING column missing from either side is an invalid identifier,
 * refused inside a subquery before the rest of the statement is checked. Every cell is live-verified.
 */
public class CompileTimeTypingOrderTest extends BaseDatabaseTest {

    private static final String ERROR = "SQL compilation error:|";

    @BeforeEach
    public void createTables() {
        engine.execute("CREATE OR REPLACE TABLE rt (g VARCHAR(10), v VARIANT, n NUMBER(5,0), d DATE)");
        engine.execute("CREATE OR REPLACE TABLE re (x INT)");
    }

    /** The first row's first cell, "no row", or the refusal on one line. */
    private String answer(final String sql) {
        try {
            for (final Row row : engine.executeQuery(sql).getRows()) {
                return String.valueOf(row.getValue(0));
            }
            return "no row";
        } catch (final RuntimeException refused) {
            return String.valueOf(refused.getMessage()).replace('\n', '|');
        }
    }

    /** The declared type of each column of a query. */
    private String typesOf(final String sql) {
        final StringBuilder types = new StringBuilder();
        for (final ResultSetColumn column : engine.executeQuery(sql).getColumns()) {
            final DataType type = column.getDataType();
            if (types.length() > 0) {
                types.append(", ");
            }
            types.append(type instanceof StringType ? type.getName() + "(" + ((StringType) type).getMaxLength() + ")"
                : String.valueOf(type == null ? null : type.getName()));
        }
        return types.toString();
    }

    private void assertCells(final String[][] cells) {
        for (final String[] cell : cells) {
            assertEquals(cell[1], answer(cell[0]), cell[0]);
        }
    }

    private static String iff(final int position, final String condition) {
        return "SQL compilation error: error line 1 at position " + position
            + "|Invalid argument types for function 'IFF': (" + condition + ", NUMBER(1,0), NUMBER(1,0))";
    }

    private static String unsupported(final String function, final String type) {
        return ERROR + "Function " + function + " does not support " + type + " argument type";
    }

    @Test
    public void aCastOperandIsTypedBeforeTheCast() {
        assertCells(new String[][] {
            {"SELECT CAST(IFF(g, 1, 2) AS DATE) FROM rt", iff(12, "VARCHAR(10)")},
            {"SELECT CAST(IFF(v, 1, 2) AS DATE) FROM rt", iff(12, "VARIANT")},
            {"SELECT CAST(IFF(n, 1, 2) AS DATE) FROM rt", iff(12, "NUMBER(5,0)")},
            {"SELECT CAST(IFF(g, 1, 2) AS NUMBER) FROM rt", iff(12, "VARCHAR(10)")},
            {"SELECT IFF(g, 1, 2) FROM rt", iff(7, "VARCHAR(10)")},
        });
    }

    @Test
    public void aDatePartOfATextOrAVariantIsRefused() {
        assertCells(new String[][] {
            {"SELECT DATE_TRUNC('day', g) FROM rt", unsupported("DATE_TRUNC", "VARCHAR(10)")},
            {"SELECT DATE_TRUNC('day', 'abc')", unsupported("DATE_TRUNC", "VARCHAR(3)")},
            {"SELECT DATE_TRUNC('day', v) FROM rt", unsupported("DATE_TRUNC", "VARIANT")},
            {"SELECT DATE_TRUNC('day', '2024-01-01')", unsupported("DATE_TRUNC", "VARCHAR(10)")},
            {"SELECT DATE_TRUNC('day', PARSE_JSON('\"2024-01-01\"'))", unsupported("DATE_TRUNC", "VARIANT")},
            {"SELECT LAST_DAY('2024-01-01')", unsupported("LAST_DAY", "VARCHAR(10)")},
            {"SELECT LAST_DAY(PARSE_JSON('\"2024-01-01\"'))", unsupported("LAST_DAY", "VARIANT")},
            {"SELECT DATE_TRUNC('day', g::DATE) FROM rt", "no row"},
        });
        final String[] extracting = {
            "EXTRACT(day FROM %s)", "DATE_PART(day, %s)", "DAYOFMONTH(%s)", "YEAR(%s)", "MONTHS_BETWEEN(%s, %s)",
            "HOUR(%s)", "WEEKISO(%s)",
        };
        for (final String form : extracting) {
            final String text = "SELECT " + form.replace("%s", "'2024-01-01'");
            final String variant = "SELECT " + form.replace("%s", "PARSE_JSON('\"2024-01-01\"')");
            assertEquals(unsupported("EXTRACT", "VARCHAR(10)"), answer(text), text);
            assertEquals(unsupported("EXTRACT", "VARIANT"), answer(variant), variant);
        }
    }

    @Test
    public void dateArithmeticOverAnUntypedNullHasNoType() {
        assertCells(new String[][] {
            {"SELECT CAST(DATEDIFF(day, d, NULL) AS DATE) FROM rt", "no row"},
            {"SELECT DATEDIFF(day, d, NULL)::DATE FROM rt", "no row"},
            {"SELECT CAST(DATEDIFF(day, d, NULL) AS NUMBER) FROM rt", "no row"},
            {"SELECT CAST(DATEADD(day, NULL, d) AS NUMBER) FROM rt", "no row"},
            {"SELECT CAST(DATEDIFF(day, NULL, NULL) AS DATE)", "null"},
            {"SELECT CAST(DATEDIFF(day, d, d) AS DATE) FROM rt",
                ERROR + "invalid type [CAST(DATE_DIFFDATEINDAYS(RT.D, RT.D) AS DATE)] for parameter 'TO_DATE'"},
        });
        final String source = " FROM (SELECT '2024-01-01'::DATE AS d, '10:00:00'::TIME AS t,"
            + " '2024-01-01 10:00:00'::TIMESTAMP_NTZ AS ts)";
        final String[] calls = {
            "DATEADD(day, NULL, d)", "DATEADD(day, 1, NULL)", "DATEDIFF(day, NULL, d)", "DATEDIFF(day, d, NULL)",
            "TIMEADD(hour, NULL, t)", "TIMESTAMPADD(day, NULL, ts)", "DATE_TRUNC('day', NULL)", "TIMEDIFF(day, NULL, ts)",
            "TIMESTAMPDIFF(day, d, NULL)", "DATEADD(day, NULL, NULL)",
        };
        for (final String call : calls) {
            assertEquals("NULL[LOB]", answer("SELECT SYSTEM$TYPEOF(" + call + ")" + source), call);
            assertEquals("null", answer("SELECT CAST(" + call + " AS BOOLEAN)" + source), call);
        }
        assertEquals("VARCHAR(0), VARCHAR(0), VARCHAR(0)", typesOf("SELECT DATEADD(day, NULL, d) AS a,"
            + " DATEDIFF(day, d, NULL) AS b, DATE_TRUNC('day', NULL) AS c FROM (SELECT '2024-01-01'::DATE AS d)"));
    }

    @Test
    public void aUsingColumnMissingFromASideIsAnInvalidIdentifier() {
        assertCells(new String[][] {
            {"SELECT 1 FROM rt JOIN re USING (n)", ERROR + "Invalid identifier N"},
            {"SELECT 1 FROM rt JOIN re USING (zz)", ERROR + "Invalid identifier ZZ"},
            {"SELECT 1 FROM re JOIN rt USING (n)", ERROR + "Invalid identifier N"},
            {"SELECT (SELECT 1 FROM rt JOIN re USING (n)) FROM rt", ERROR + "Invalid identifier N"},
            {"SELECT (SELECT 1 FROM rt JOIN re USING (n)), ABS(1, 2) FROM rt", ERROR + "Invalid identifier N"},
            {"SELECT ABS(1, 2), (SELECT 1 FROM rt JOIN re USING (n)) FROM rt", ERROR + "Invalid identifier N"},
        });
    }

    @Test
    public void aUsingColumnJoinsByItsExactName() {
        engine.execute("CREATE OR REPLACE TABLE nc (s VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE nc2 (t VARCHAR)");
        engine.execute("CREATE OR REPLACE TABLE qc (\"s\" VARCHAR, \"Mix\" INT)");
        assertCells(new String[][] {
            {"SELECT COUNT(*) FROM nc JOIN nc2 USING (\"s\")", ERROR + "Invalid identifier s"},
            {"SELECT COUNT(*) FROM qc JOIN qc q2 USING (s)", ERROR + "Invalid identifier S"},
            {"SELECT COUNT(*) FROM qc JOIN qc q2 USING (\"s\")", "0"},
            {"SELECT COUNT(*) FROM qc JOIN qc q2 USING (\"Mix\")", "0"},
            {"SELECT COUNT(*) FROM qc JOIN qc q2 USING (mix)", ERROR + "Invalid identifier MIX"},
            {"SELECT COUNT(*) FROM nc JOIN nc n2 USING (\"S\")", "0"},
            {"SELECT COUNT(*) FROM (SELECT 1 \"x\") a JOIN (SELECT 1 \"x\") b USING (x)", ERROR + "Invalid identifier X"},
            {"SELECT COUNT(*) FROM nc JOIN nc2 USING (s, t)", ERROR + "Invalid identifier S"},
            {"SELECT COUNT(*) FROM nc JOIN nc2 USING (t, s)", ERROR + "Invalid identifier T"},
            {"SELECT COUNT(*) FROM nc JOIN nc2 USING (nc.s)", ERROR + "Invalid identifier S"},
            {"SELECT COUNT(*) FROM nc JOIN nc n2 USING (zz.s)", "0"},
            {"SELECT COUNT(*) FROM nc JOIN nc2 USING (s) JOIN nc n3 USING (zz)", ERROR + "Invalid identifier S"},
            {"SELECT COUNT(*) FROM (SELECT 1 x) a JOIN (SELECT 1 x, 2 y) b USING (x) JOIN (SELECT 2 y) c USING (y)", "1"},
            {"WITH w AS (SELECT 1 x, 2 y) SELECT COUNT(*) FROM w JOIN (SELECT 1 x) b USING (x) JOIN (SELECT 2 y) c USING (y)",
                "1"},
            {"SELECT COUNT(*) FROM (SELECT 1 x) a JOIN (SELECT 1 x) b USING (x) RIGHT JOIN (SELECT 1 x, 3 q) c USING (q)",
                ERROR + "Invalid identifier Q"},
        });
    }
}

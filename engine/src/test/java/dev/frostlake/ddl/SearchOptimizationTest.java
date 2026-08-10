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

package dev.frostlake.ddl;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SEARCH OPTIMIZATION, recorded and reported and nothing more — no lookup structure is built and no
 * query plan changes, which is what keeps it inside the standing no-indexes rule (see docs/scope.md).
 *
 * <p>What IS modelled is everything a user can observe: the three SHOW TABLES cells, the expression
 * list DESCRIBE answers, and the numbering rules — ids are handed out once and never reused, so
 * dropping one leaves the rest as they were. The measured oddities: FULL_TEXT reads back as
 * {@code FULL_TEXT DEFAULT_ANALYZER}, and a bare ADD skips VARIANT columns that {@code EQUALITY(*)}
 * includes.
 */
public class SearchOptimizationTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE so_t (id NUMBER, txt VARCHAR, v VARIANT, d DATE)");
    }

    /** The expression list as "id:method:target:type" per row, which is what the rules are about. */
    private String expressions() {
        final ResultSet described = engine.executeQuery("DESCRIBE SEARCH OPTIMIZATION ON so_t");
        final StringBuilder out = new StringBuilder();
        for (final Row row : described.getRows()) {
            out.append(row.getValue(0)).append(':').append(row.getValue(1)).append(':')
               .append(row.getValue(2)).append(':').append(row.getValue(3)).append(' ');
        }
        return out.toString().trim();
    }

    private String cells() {
        final ResultSet tables = engine.executeQuery("SHOW TABLES LIKE 'so_t'");
        final Row row = soleRowWhere(tables, "name", "SO_T");
        return cell(tables, row, "search_optimization") + "/"
            + cell(tables, row, "search_optimization_progress") + "/"
            + cell(tables, row, "search_optimization_bytes");
    }

    @Test
    public void aTableWithoutItAnswersNoExpressions() {
        assertEquals("", expressions());
        assertEquals("OFF/null/null", cells());
    }

    /** Dropping when there is none is a no-op, in both forms. */
    @Test
    public void droppingWhenThereIsNoneIsSilent() {
        engine.execute("ALTER TABLE so_t DROP SEARCH OPTIMIZATION");
        engine.execute("ALTER TABLE so_t DROP SEARCH OPTIMIZATION ON EQUALITY(id)");
        assertEquals("OFF/null/null", cells());
    }

    @Test
    public void oneMethodTurnsTheCellsOn() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(id)");
        assertEquals("ON/100/0", cells());
        assertEquals("1:EQUALITY:ID:NUMBER(38,0)", expressions());
    }

    /** FULL_TEXT names its analyzer in the method cell. */
    @Test
    public void fullTextReadsBackWithItsAnalyzer() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON FULL_TEXT(txt)");
        assertEquals("1:FULL_TEXT DEFAULT_ANALYZER:TXT:VARCHAR(16777216)", expressions());
    }

    @Test
    public void theStarFormCoversEveryColumnVariantIncluded() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(*)");
        assertEquals("1:EQUALITY:ID:NUMBER(38,0) 2:EQUALITY:TXT:VARCHAR(16777216)"
            + " 3:EQUALITY:V:VARIANT 4:EQUALITY:D:DATE", expressions());
    }

    /** The bare ADD does NOT cover the VARIANT column the star form does. */
    @Test
    public void aBareAddSkipsVariantColumns() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION");
        assertEquals("1:EQUALITY:ID:NUMBER(38,0) 2:EQUALITY:TXT:VARCHAR(16777216)"
            + " 3:EQUALITY:D:DATE", expressions());
    }

    @Test
    public void addingTheSameExpressionTwiceAddsNothing() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(id)");
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(id)");
        assertEquals("1:EQUALITY:ID:NUMBER(38,0)", expressions());
    }

    @Test
    public void severalMethodsMayCoverOneColumn() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(txt)");
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON SUBSTRING(txt)");
        assertEquals("1:EQUALITY:TXT:VARCHAR(16777216) 2:SUBSTRING:TXT:VARCHAR(16777216)",
            expressions());
    }

    /** Numbers are never reused: dropping the middle one leaves the others where they were. */
    @Test
    public void droppingOneLeavesTheOtherNumbersAlone() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(id)");
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON SUBSTRING(txt)");
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(d)");
        engine.execute("ALTER TABLE so_t DROP SEARCH OPTIMIZATION ON SUBSTRING(txt)");
        assertEquals("1:EQUALITY:ID:NUMBER(38,0) 3:EQUALITY:D:DATE", expressions());
    }

    /** A drop may name the number instead of the expression. */
    @Test
    public void anExpressionMayBeDroppedByItsNumber() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(id)");
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(txt)");
        engine.execute("ALTER TABLE so_t DROP SEARCH OPTIMIZATION ON 1");
        assertEquals("2:EQUALITY:TXT:VARCHAR(16777216)", expressions());
    }

    @Test
    public void theBareDropClearsEverything() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(*)");
        engine.execute("ALTER TABLE so_t DROP SEARCH OPTIMIZATION");
        assertEquals("", expressions());
        assertEquals("OFF/null/null", cells());
    }

    /** With some configured, dropping one that is not is refused — positioned at the argument. */
    @Test
    public void droppingAnExpressionThatIsNotThereIsRefused() {
        engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(id)");
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE so_t DROP SEARCH OPTIMIZATION ON SUBSTRING(txt)");
            }
        });
        assertEquals("SQL compilation error: error line 1 at position 55\nExpression"
            + " SUBSTRING(SO_T.TXT) is not indexed by search optimization.", ex.getMessage());
    }

    /** GEO indexes a geography and nothing else, and the message uses live's internal alias. */
    @Test
    public void geoOverAnotherTypeIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON GEO(v)");
            }
        });
        assertEquals("SQL compilation error: error line 1 at position 48\nExpression"
            + " GEO(IDX_SRC_TABLE.V) cannot be used in search optimization.", ex.getMessage());
    }

    @Test
    public void anUnknownColumnIsRefusedUnderTheSameAlias() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute("ALTER TABLE so_t ADD SEARCH OPTIMIZATION ON EQUALITY(no_such_c)");
            }
        });
        assertEquals("SQL compilation error: error line 1 at position 53\ninvalid identifier"
            + " 'IDX_SRC_TABLE.NO_SUCH_C'", ex.getMessage());
    }

    @Test
    public void describingAMissingTableIsRefused() {
        final RuntimeException ex = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.executeQuery("DESCRIBE SEARCH OPTIMIZATION ON no_such_t");
            }
        });
        assertEquals("SQL compilation error:\nTable 'NO_SUCH_T' does not exist or not authorized.",
            ex.getMessage());
    }

    /** None of its words is reserved. */
    @Test
    public void itsWordsRemainUsableAsNames() {
        engine.execute("CREATE TABLE search (search NUMBER, optimization NUMBER, equality NUMBER)");
        engine.execute("INSERT INTO search VALUES (1, 2, 3)");
        assertEquals("1", engine.executeQuery("SELECT search FROM search")
            .getRows().get(0).getValue(0).toString());
    }
}

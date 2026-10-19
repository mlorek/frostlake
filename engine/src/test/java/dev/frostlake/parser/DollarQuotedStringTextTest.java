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

package dev.frostlake.parser;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import dev.frostlake.storage.Row;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A string written between dollar signs is read where a SHOW, a COMMENT ON and a star's ILIKE read a string, as a
 * quoted one is.
 */
public class DollarQuotedStringTextTest extends BaseDatabaseTest {

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE DQC_ONE (id INT, b BOOLEAN)");
        engine.execute("INSERT INTO DQC_ONE VALUES (5, TRUE)");
        engine.execute("CREATE OR REPLACE TABLE DQC_TWO (a INT)");
        engine.execute("CREATE OR REPLACE VIEW DQC_VIEW AS SELECT a FROM DQC_TWO");
        engine.execute("CREATE OR REPLACE SEQUENCE DQC_SEQ");
    }

    /** One column of a listing, sorted. */
    private List<String> listed(final String show, final String column) {
        final ResultSet rs = engine.executeQuery(show);
        final List<String> out = new ArrayList<>();
        for (final Row row : rs.getRows()) {
            out.add(String.valueOf(row.getValue(rs.getColumnIndex(column))));
        }
        Collections.sort(out);
        return out;
    }

    /** The names of a query's columns. */
    private List<String> columns(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        final List<String> out = new ArrayList<>();
        for (int i = 0; i < rs.getColumnCount(); i++) {
            out.add(rs.getColumns().get(i).getName());
        }
        return out;
    }

    private String scalar(final String sql) {
        return String.valueOf(engine.executeQuery(sql).getRows().get(0).getValue(0));
    }

    @Test
    public void aShowReadsItsLikeItsPrefixAndItsStartingName() {
        assertEquals(List.of("DQC_ONE", "DQC_TWO"), listed("SHOW TABLES LIKE $$DQC%$$", "name"));
        assertEquals(List.of("DQC_ONE"), listed("SHOW TERSE TABLES LIKE $$dqc_one$$", "name"));
        assertEquals(List.of("DQC_ONE", "DQC_TWO"), listed("SHOW TABLES STARTS WITH $$DQC$$", "name"));
        assertEquals(List.of("DQC_TWO"), listed("SHOW TABLES LIKE $$DQC%$$ LIMIT 1 FROM $$DQC_T$$", "name"));
        assertEquals(List.of("DQC_SEQ"), listed("SHOW SEQUENCES LIKE $$dqc%$$", "name"));
        assertEquals(List.of("DQC_VIEW"), listed("SHOW VIEWS LIKE $$dqc%$$", "name"));
        assertEquals(List.of("ID"), listed("SHOW COLUMNS LIKE $$id$$ IN TABLE DQC_ONE", "column_name"));
        assertEquals(List.of("TIMEZONE"), listed("SHOW PARAMETERS LIKE $$TIMEZONE$$", "key"));
        assertEquals(List.of(), listed("SHOW TABLES LIKE $$DQC'X$$", "name"));
        assertEquals(List.of(), listed("SHOW TABLES LIKE $$$$", "name"));
    }

    @Test
    public void aCommentOnReadsItsText() {
        engine.execute("COMMENT ON TABLE DQC_TWO IS $$it's a table$$");
        assertEquals("it's a table",
            scalar("SELECT COMMENT FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME = 'DQC_TWO'"));
        engine.execute("COMMENT ON COLUMN DQC_TWO.a IS $$a column$$");
        assertEquals("a column", describeCell("DQC_TWO", "A", "comment"));
        engine.execute("COMMENT ON VIEW DQC_VIEW IS $$a view$$");
        assertEquals(List.of("a view"), listed("SHOW VIEWS LIKE 'DQC_VIEW'", "comment"));
    }

    @Test
    public void aStarsIlikeReadsItsPattern() {
        assertEquals(List.of("B"), columns("SELECT * ILIKE $$b$$ FROM DQC_ONE"));
        assertEquals(List.of("ID", "K"), columns("SELECT * ILIKE $$%$$ RENAME b AS k FROM DQC_ONE"));
        assertEquals(List.of("B"), columns("SELECT dqc_one.* ILIKE $$b$$ FROM DQC_ONE"));
        assertEquals("1", scalar("SELECT COUNT(* ILIKE $$b$$) FROM DQC_ONE"));
        assertEquals("SQL compilation error:\nsyntax error line 1 at position 21 unexpected 'ILIKE'.",
            assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.execute("SELECT * EXCLUDE (a) ILIKE $$x$$");
                }
            }).getMessage());
    }
}

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

import dev.frostlake.query.MeasuredCellsTestSupport;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * ALTER TABLE … SET STAGE_FILE_FORMAT replaces the table's stage format whole, as a stage's SET FILE_FORMAT does: an
 * option the list leaves out is back at its default and TYPE at CSV, so an option of another type alone is refused.
 * SET STAGE_COPY_OPTIONS merges into the table's copy options instead, and UNSET names either list as an invalid
 * property. A FORMAT_NAME has to name a format that is there; a listing of a format that names one shows TYPE when
 * written, the name, then each option written beside it, a table's and a stage's alike, and those options are judged
 * by the named format's type (live-verified).
 */
public class TableStageFormatAlterTest extends MeasuredCellsTestSupport {

    /** The stage format options the first walk reads back, by name. */
    private static final String LISTED = """
        SELECT LISTAGG("property" || '=' || "property_value", ',') WITHIN GROUP (ORDER BY "property") \
        FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "parent_property" = 'STAGE_FILE_FORMAT' AND "property" IN \
        ('TYPE', 'FIELD_DELIMITER', 'SKIP_HEADER', 'COMPRESSION', 'NULL_IF', 'FORMAT_NAME', 'STRIP_OUTER_ARRAY')""";

    /** The format and copy options the second walk reads back, by group and name. */
    private static final String LISTED_WITH_COPY = """
        SELECT LISTAGG("parent_property" || ':' || "property" || '=' || "property_value", ',') WITHIN GROUP \
        (ORDER BY "parent_property", "property") FROM TABLE(RESULT_SCAN(LAST_QUERY_ID())) WHERE "property" IN \
        ('ON_ERROR', 'PURGE', 'FORCE', 'TYPE', 'FORMAT_NAME', 'SKIP_HEADER', 'FIELD_DELIMITER')""";

    @Test
    public void setReplacesTheFormatAndUnsetIsRefused() {
        assertAnswer("Table SF successfully created.",
            "CREATE OR REPLACE TABLE sf (a INT)");
        engine.executeQuery("DESCRIBE TABLE sf TYPE = STAGE");
        assertAnswer("COMPRESSION=AUTO,FIELD_DELIMITER=,,NULL_IF=[\\\\N],SKIP_HEADER=0,TYPE=CSV",
            LISTED);
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sf SET STAGE_FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|')");
        engine.executeQuery("DESCRIBE TABLE sf TYPE = STAGE");
        assertAnswer("COMPRESSION=AUTO,FIELD_DELIMITER=|,NULL_IF=[\\\\N],SKIP_HEADER=0,TYPE=CSV",
            LISTED);
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sf SET STAGE_FILE_FORMAT = (SKIP_HEADER = 1)");
        engine.executeQuery("DESCRIBE TABLE sf TYPE = STAGE");
        assertAnswer("COMPRESSION=AUTO,FIELD_DELIMITER=,,NULL_IF=[\\\\N],SKIP_HEADER=1,TYPE=CSV",
            LISTED);
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sf SET STAGE_FILE_FORMAT = (TYPE = JSON)");
        engine.executeQuery("DESCRIBE TABLE sf TYPE = STAGE");
        assertAnswer("COMPRESSION=AUTO,NULL_IF=[],STRIP_OUTER_ARRAY=false,TYPE=JSON",
            LISTED);
        assertAnswer("REFUSED SQL compilation error: | Option STRIP_OUTER_ARRAY is not valid for file format type "
            + "CSV.",
            "ALTER TABLE sf SET STAGE_FILE_FORMAT = (STRIP_OUTER_ARRAY = TRUE)");
        engine.executeQuery("DESCRIBE TABLE sf TYPE = STAGE");
        assertAnswer("COMPRESSION=AUTO,NULL_IF=[],STRIP_OUTER_ARRAY=false,TYPE=JSON",
            LISTED);
        assertAnswer("Table SF2 successfully created.",
            "CREATE OR REPLACE TABLE sf2 (a INT) STAGE_FILE_FORMAT = (TYPE = CSV FIELD_DELIMITER = '|')");
        engine.executeQuery("DESCRIBE TABLE sf2 TYPE = STAGE");
        assertAnswer("COMPRESSION=AUTO,FIELD_DELIMITER=|,NULL_IF=[\\\\N],SKIP_HEADER=0,TYPE=CSV",
            LISTED);
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sf2 SET STAGE_FILE_FORMAT = (SKIP_HEADER = 1)");
        engine.executeQuery("DESCRIBE TABLE sf2 TYPE = STAGE");
        assertAnswer("COMPRESSION=AUTO,FIELD_DELIMITER=,,NULL_IF=[\\\\N],SKIP_HEADER=1,TYPE=CSV",
            LISTED);
        assertAnswer("File format FF34 successfully created.",
            "CREATE OR REPLACE FILE FORMAT ff34 TYPE = CSV FIELD_DELIMITER = ';'");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sf2 SET STAGE_FILE_FORMAT = (FORMAT_NAME = 'ff34')");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff34|",
            "DESCRIBE TABLE sf2 TYPE = STAGE");
        assertAnswer("FORMAT_NAME=ff34",
            LISTED);
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sf2 SET STAGE_FILE_FORMAT = (TYPE = CSV COMPRESSION = SNAPPY)");
        engine.executeQuery("DESCRIBE TABLE sf2 TYPE = STAGE");
        assertAnswer("COMPRESSION=SNAPPY,FIELD_DELIMITER=,,NULL_IF=[\\\\N],SKIP_HEADER=0,TYPE=CSV",
            LISTED);
        assertAnswer("REFUSED SQL compilation error: | invalid property 'STAGE_FILE_FORMAT' for 'TABLE'",
            "ALTER TABLE sf2 UNSET STAGE_FILE_FORMAT");
        engine.executeQuery("DESCRIBE TABLE sf2 TYPE = STAGE");
        assertAnswer("COMPRESSION=SNAPPY,FIELD_DELIMITER=,,NULL_IF=[\\\\N],SKIP_HEADER=0,TYPE=CSV",
            LISTED);
    }

    @Test
    public void copyOptionsMergeAndANamedFormatHasToBeThere() {
        assertAnswer("File format FF37 successfully created.",
            "CREATE OR REPLACE FILE FORMAT ff37 TYPE = CSV FIELD_DELIMITER = ';'");
        assertAnswer("Table SC successfully created.",
            "CREATE OR REPLACE TABLE sc (a INT) STAGE_COPY_OPTIONS = (ON_ERROR = CONTINUE PURGE = TRUE)");
        engine.executeQuery("DESCRIBE TABLE sc TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=false,STAGE_COPY_OPTIONS:ON_ERROR=CONTINUE,STAGE_COPY_OPTIONS:PURGE=tr"
            + "ue,STAGE_FILE_FORMAT:FIELD_DELIMITER=,,STAGE_FILE_FORMAT:SKIP_HEADER=0,STAGE_FILE_FORMAT:TYPE=CSV",
            LISTED_WITH_COPY);
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sc SET STAGE_COPY_OPTIONS = (FORCE = TRUE)");
        engine.executeQuery("DESCRIBE TABLE sc TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=true,STAGE_COPY_OPTIONS:ON_ERROR=CONTINUE,STAGE_COPY_OPTIONS:PURGE=tru"
            + "e,STAGE_FILE_FORMAT:FIELD_DELIMITER=,,STAGE_FILE_FORMAT:SKIP_HEADER=0,STAGE_FILE_FORMAT:TYPE=CSV",
            LISTED_WITH_COPY);
        assertAnswer("REFUSED SQL compilation error: | invalid property 'STAGE_COPY_OPTIONS' for 'TABLE'",
            "ALTER TABLE sc UNSET STAGE_COPY_OPTIONS");
        engine.executeQuery("DESCRIBE TABLE sc TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=true,STAGE_COPY_OPTIONS:ON_ERROR=CONTINUE,STAGE_COPY_OPTIONS:PURGE=tru"
            + "e,STAGE_FILE_FORMAT:FIELD_DELIMITER=,,STAGE_FILE_FORMAT:SKIP_HEADER=0,STAGE_FILE_FORMAT:TYPE=CSV",
            LISTED_WITH_COPY);
        assertAnswer("REFUSED SQL compilation error: | invalid property 'STAGE_FILE_FORMAT' for 'TABLE'",
            "ALTER TABLE sc UNSET COMMENT, STAGE_FILE_FORMAT");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'STAGE_COPY_OPTIONS' for 'TABLE'",
            "ALTER TABLE sc UNSET STAGE_COPY_OPTIONS, COMMENT");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sc SET STAGE_FILE_FORMAT = (FORMAT_NAME = 'ff37' SKIP_HEADER = 1)");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff37| ;; STAGE_FILE_FORMAT|SKIP_HEADER|Integer|1|0",
            "DESCRIBE TABLE sc TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=true,STAGE_COPY_OPTIONS:ON_ERROR=CONTINUE,STAGE_COPY_OPTIONS:PURGE=tru"
            + "e,STAGE_FILE_FORMAT:FORMAT_NAME=ff37,STAGE_FILE_FORMAT:SKIP_HEADER=1",
            LISTED_WITH_COPY);
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sc SET STAGE_FILE_FORMAT = (TYPE = CSV FORMAT_NAME = 'ff37')");
        assertFormatRows("STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|FORMAT_NAME|String|ff37|",
            "DESCRIBE TABLE sc TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=true,STAGE_COPY_OPTIONS:ON_ERROR=CONTINUE,STAGE_COPY_OPTIONS:PURGE=tru"
            + "e,STAGE_FILE_FORMAT:FORMAT_NAME=ff37,STAGE_FILE_FORMAT:TYPE=CSV",
            LISTED_WITH_COPY);
        assertAnswer("REFUSED SQL compilation error: | File format 'NO_SUCH_FF37' does not exist or not authorized. "
            + "Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT NO_SUCH_FF37.",
            "ALTER TABLE sc SET STAGE_FILE_FORMAT = (FORMAT_NAME = 'no_such_ff37')");
        assertFormatRows("STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|FORMAT_NAME|String|ff37|",
            "DESCRIBE TABLE sc TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=true,STAGE_COPY_OPTIONS:ON_ERROR=CONTINUE,STAGE_COPY_OPTIONS:PURGE=tru"
            + "e,STAGE_FILE_FORMAT:FORMAT_NAME=ff37,STAGE_FILE_FORMAT:TYPE=CSV",
            LISTED_WITH_COPY);
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sc SET STAGE_FILE_FORMAT = (FORMAT_NAME = ff37)");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff37|",
            "DESCRIBE TABLE sc TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=true,STAGE_COPY_OPTIONS:ON_ERROR=CONTINUE,STAGE_COPY_OPTIONS:PURGE=tru"
            + "e,STAGE_FILE_FORMAT:FORMAT_NAME=ff37",
            LISTED_WITH_COPY);
        assertAnswer("REFUSED SQL compilation error: | File format 'TEST_DB.TEST_SCHEMA.\"ff37\"' does not exist or "
            + "not authorized. Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT "
            + "TEST_DB.TEST_SCHEMA.\"ff37\".",
            "ALTER TABLE sc SET STAGE_FILE_FORMAT = (FORMAT_NAME = 'test_db.test_schema.\"ff37\"')");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff37|",
            "DESCRIBE TABLE sc TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=true,STAGE_COPY_OPTIONS:ON_ERROR=CONTINUE,STAGE_COPY_OPTIONS:PURGE=tru"
            + "e,STAGE_FILE_FORMAT:FORMAT_NAME=ff37",
            LISTED_WITH_COPY);
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sc SET STAGE_FILE_FORMAT = (FORMAT_NAME = 'ff37')");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sc SET STAGE_COPY_OPTIONS = (PURGE = TRUE)");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff37|",
            "DESCRIBE TABLE sc TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=true,STAGE_COPY_OPTIONS:ON_ERROR=CONTINUE,STAGE_COPY_OPTIONS:PURGE=tru"
            + "e,STAGE_FILE_FORMAT:FORMAT_NAME=ff37",
            LISTED_WITH_COPY);
        assertAnswer("Table SD successfully created.",
            "CREATE OR REPLACE TABLE sd (a INT) STAGE_FILE_FORMAT = (FORMAT_NAME = 'ff37')");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff37|",
            "DESCRIBE TABLE sd TYPE = STAGE");
        assertAnswer("STAGE_COPY_OPTIONS:FORCE=false,STAGE_COPY_OPTIONS:ON_ERROR=ABORT_STATEMENT,STAGE_COPY_OPTIONS:P"
            + "URGE=false,STAGE_FILE_FORMAT:FORMAT_NAME=ff37",
            LISTED_WITH_COPY);
        assertAnswer("REFUSED SQL compilation error: | File format 'NO_SUCH_FF37' does not exist or not authorized. "
            + "Your primary role <ROLE> must have at least one privilege granted on FILE FORMAT NO_SUCH_FF37.",
            "CREATE OR REPLACE TABLE se (a INT) STAGE_FILE_FORMAT = (FORMAT_NAME = 'no_such_ff37')");
        assertAnswer("REFUSED SQL compilation error: | Table 'SE' does not exist or not authorized. Your primary "
            + "role <ROLE> must have at least one privilege granted on TABLE SE.",
            "DESCRIBE TABLE se TYPE = STAGE");
    }

    @Test
    public void optionsBesideAFormatNameAreListedAndJudgedByItsType() {
        assertAnswer("File format FF38 successfully created.",
            "CREATE OR REPLACE FILE FORMAT ff38 TYPE = CSV");
        assertAnswer("Table SG successfully created.",
            "CREATE OR REPLACE TABLE sg (a INT)");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sg SET STAGE_FILE_FORMAT = (SKIP_HEADER = 1 FORMAT_NAME = 'ff38')");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff38| ;; STAGE_FILE_FORMAT|SKIP_HEADER|Integer|1|0",
            "DESCRIBE TABLE sg TYPE = STAGE");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sg SET STAGE_FILE_FORMAT = (FORMAT_NAME = 'ff38' TYPE = CSV)");
        assertFormatRows("STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|FORMAT_NAME|String|ff38|",
            "DESCRIBE TABLE sg TYPE = STAGE");
        assertAnswer("REFUSED SQL compilation error: | Option RECORD_DELIMITER is not valid for file format type "
            + "JSON.",
            "ALTER TABLE sg SET STAGE_FILE_FORMAT = (FORMAT_NAME = 'ff38' TYPE = JSON STRIP_OUTER_ARRAY = TRUE)");
        assertFormatRows("STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|FORMAT_NAME|String|ff38|",
            "DESCRIBE TABLE sg TYPE = STAGE");
        assertAnswer("Statement executed successfully.",
            "ALTER TABLE sg SET STAGE_FILE_FORMAT = (FORMAT_NAME = 'ff38' FIELD_DELIMITER = '|' SKIP_HEADER = 2 "
                + "NULL_IF = ('a'))");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff38| ;; STAGE_FILE_FORMAT|FIELD_DELIMITER|String|||, "
            + ";; STAGE_FILE_FORMAT|SKIP_HEADER|Integer|2|0 ;; STAGE_FILE_FORMAT|NULL_IF|List|[a]|[\\\\N]",
            "DESCRIBE TABLE sg TYPE = STAGE");
        assertAnswer("REFUSED SQL compilation error: | Option STRIP_OUTER_ARRAY is not valid for file format type "
            + "CSV.",
            "ALTER TABLE sg SET STAGE_FILE_FORMAT = (FORMAT_NAME = 'ff38' STRIP_OUTER_ARRAY = TRUE)");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff38| ;; STAGE_FILE_FORMAT|FIELD_DELIMITER|String|||, "
            + ";; STAGE_FILE_FORMAT|SKIP_HEADER|Integer|2|0 ;; STAGE_FILE_FORMAT|NULL_IF|List|[a]|[\\\\N]",
            "DESCRIBE TABLE sg TYPE = STAGE");
        assertAnswer("REFUSED SQL compilation error: | Option SKIP_HEADER is not valid for file format type JSON.",
            "ALTER TABLE sg SET STAGE_FILE_FORMAT = (TYPE = JSON FORMAT_NAME = 'ff38' SKIP_HEADER = 1)");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff38| ;; STAGE_FILE_FORMAT|FIELD_DELIMITER|String|||, "
            + ";; STAGE_FILE_FORMAT|SKIP_HEADER|Integer|2|0 ;; STAGE_FILE_FORMAT|NULL_IF|List|[a]|[\\\\N]",
            "DESCRIBE TABLE sg TYPE = STAGE");
        assertAnswer("Stage area ST38 successfully created.",
            "CREATE OR REPLACE STAGE st38 FILE_FORMAT = (FORMAT_NAME = 'ff38' SKIP_HEADER = 1)");
        assertFormatRows("STAGE_FILE_FORMAT|FORMAT_NAME|String|ff38| ;; STAGE_FILE_FORMAT|SKIP_HEADER|Integer|1|0",
            "DESC STAGE st38");
        assertAnswer("Stage area ST38 successfully created.",
            "CREATE OR REPLACE STAGE st38 FILE_FORMAT = (TYPE = CSV FORMAT_NAME = 'ff38')");
        assertFormatRows("STAGE_FILE_FORMAT|TYPE|String|CSV|CSV ;; STAGE_FILE_FORMAT|FORMAT_NAME|String|ff38|",
            "DESC STAGE st38");
    }

    /** Asserts the STAGE_FILE_FORMAT rows a stage listing answers, its copy options and location aside. */
    private void assertFormatRows(final String expected, final String sql) {
        final List<String> rows = new ArrayList<>();
        for (final String row : answer(sql).split(" ;; ")) {
            if (row.startsWith("STAGE_FILE_FORMAT|") || row.startsWith("REFUSED")) {
                rows.add(row);
            }
        }
        assertEquals(expected, String.join(" ;; ", rows), sql);
    }
}

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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * TABLE(:x) over a block variable holding NULL names the object the variable's own name spells, quoted as written, as
 * IDENTIFIER(:x) does (live-verified): {@code TABLE(:n)} over a NULL n is refused as {@code Object '"n"' does not
 * exist or not authorized.}, in an assigned query and in a RESULTSET's alike, whatever the variable's type.
 */
public class TableOfNullVariableTest extends MeasuredCellsTestSupport {

    @Test
    public void theVariableNamesItself() {
        engine.execute("CREATE OR REPLACE TABLE tt955 (a INT)");
        engine.execute("INSERT INTO tt955 VALUES (1)");
        assertCells(new String[][] {
            {"DECLARE n INT; BEGIN n := (SELECT COUNT(*) FROM TABLE(:n)); RETURN n; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 26 : SQL compilation "
                + "error: | Object '\"n\"' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE \"n\"."},
            {"DECLARE v VARCHAR; n INT; BEGIN n := (SELECT COUNT(*) FROM TABLE(:v)); RETURN n; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: | Object '\"v\"' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE \"v\"."},
            {"DECLARE v VARCHAR; BEGIN LET rs RESULTSET := (SELECT * FROM TABLE(:v)); RETURN TABLE(rs); END;",
                "REFUSED Uncaught exception of type 'STATEMENT_ERROR' on line 1 at position 46 : SQL compilation "
                + "error: | Object '\"v\"' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE \"v\"."},
            {"DECLARE v VARCHAR DEFAULT 'nosuch_t955'; n INT; BEGIN n := (SELECT COUNT(*) FROM TABLE(:v)); "
                + "RETURN n; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 59 : SQL compilation "
                + "error: | Object 'NOSUCH_T955' does not exist or not authorized. Your primary role <ROLE> must "
                + "have at least one privilege granted on TABLE NOSUCH_T955."},
            {"DECLARE v VARCHAR DEFAULT 'tt955'; n INT; BEGIN n := (SELECT COUNT(*) FROM TABLE(:v)); RETURN n; END;",
                "1"},
            {"DECLARE b BOOLEAN; n INT; BEGIN n := (SELECT COUNT(*) FROM TABLE(:b)); RETURN n; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 37 : SQL compilation "
                + "error: | Object '\"b\"' does not exist or not authorized. Your primary role <ROLE> must have at "
                + "least one privilege granted on TABLE \"b\"."},
            {"DECLARE myvar VARCHAR; n INT; BEGIN n := (SELECT COUNT(*) FROM TABLE(:myvar)); RETURN n; END;",
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 41 : SQL compilation "
                + "error: | Object '\"myvar\"' does not exist or not authorized. Your primary role <ROLE> must have "
                + "at least one privilege granted on TABLE \"myvar\"."},
            {"DECLARE \"Mixed\" VARCHAR; n INT; BEGIN n := (SELECT COUNT(*) FROM TABLE(:\"Mixed\")); RETURN n; END;",
                "REFUSED SQL compilation error: | syntax error line 1 at position 72 unexpected '\"Mixed\"'."},
            {"SET sv955 = 'tt955'", "Statement executed successfully."},
            {"SELECT COUNT(*) FROM TABLE($sv955)", "1"},
            {"UNSET sv955", "Statement executed successfully."},
        });
    }
}

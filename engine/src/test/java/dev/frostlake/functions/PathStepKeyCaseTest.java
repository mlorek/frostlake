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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * A path step names a key exactly, whatever the step's spelling (live-verified): a colon step, a quoted step, a
 * bracket and a chained step find a member only when its key is written in the same case, over a parsed VARIANT, an
 * OBJECT_CONSTRUCT, an object constant, an ARRAY element and a FLATTEN value alike, as GET and GET_PATH do; the column
 * named before the colon still folds. GET_IGNORE_CASE is the one that matches a key in any case.
 */
public class PathStepKeyCaseTest extends MeasuredCellsTestSupport {

    @Test
    public void aStepMatchesAKeyOnlyInItsOwnCase() {
        engine.execute("CREATE OR REPLACE TABLE cj (v VARIANT, o OBJECT)");
        engine.execute("INSERT INTO cj SELECT PARSE_JSON('{\"id\":1}'), OBJECT_CONSTRUCT('id', 1)");
        assertCells(new String[][] {
            {"SELECT OBJECT_CONSTRUCT('id', 1):ID, OBJECT_CONSTRUCT('id', 1):id, OBJECT_CONSTRUCT('ID', 1):id, "
                + "OBJECT_CONSTRUCT('ID', 1):ID",
                "null|1|null|1"},
            {"SELECT j:ID, j:Id, j:\"ID\", j['ID'], j['id'], j:id FROM (SELECT PARSE_JSON('{\"id\":1}') AS j)",
                "null|null|null|null|1|1"},
            {"SELECT n:a.bc, n:a.Bc, n:A.Bc, n:a['bc'], n['A'] FROM (SELECT PARSE_JSON('{\"a\":{\"Bc\":1}}') AS n)",
                "null|1|null|null|null"},
            {"SELECT b:ID, b:id, b:Id, b['Id'] FROM (SELECT PARSE_JSON('{\"id\":1,\"ID\":2}') AS b)", "2|1|null|null"},
            {"SELECT v:ID, v:id, v['ID'], o:ID, o:id, o['ID'] FROM cj", "null|1|null|null|1|null"},
            {"SELECT COUNT(*) FROM cj WHERE v:ID = 1", "0"},
            {"SELECT COUNT(*) FROM cj WHERE v:id = 1", "1"},
            {"SELECT v:ID::INT, v:id::INT, TYPEOF(v:ID), TYPEOF(v:id) FROM cj", "null|1|null|INTEGER"},
            {"SELECT f.value:ID, f.value:id FROM TABLE(FLATTEN(INPUT => PARSE_JSON('[{\"id\":1}]'))) f", "null|1"},
            {"SELECT {'id': 1}:ID, {'id': 1}:id", "null|1"},
            {"SELECT ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('id', 1))[0]:ID, ARRAY_CONSTRUCT(OBJECT_CONSTRUCT('id', "
                + "1))[0]:id",
                "null|1"},
            {"SELECT OBJECT_CONSTRUCT('a', OBJECT_CONSTRUCT('b', 1)):A.B, OBJECT_CONSTRUCT('a', "
                + "OBJECT_CONSTRUCT('b', 1)):a.B",
                "null|null"},
            {"SELECT GET(PARSE_JSON('{\"id\":1}'), 'ID'), GET_PATH(PARSE_JSON('{\"id\":1}'), 'ID'), "
                + "GET(PARSE_JSON('{\"id\":1}'), 'id')",
                "null|null|1"},
            {"SELECT GET_IGNORE_CASE(PARSE_JSON('{\"id\":1}'), 'ID')", "1"},
            {"SELECT TYPEOF(GET_IGNORE_CASE(PARSE_JSON('{\"id\":1}'), 'ID'))", "INTEGER"},
            {"SELECT v:\"id\", v:\"Id\" FROM cj", "1|null"},
            {"SELECT V:id, V:ID FROM cj", "1|null"},
        });
    }
}

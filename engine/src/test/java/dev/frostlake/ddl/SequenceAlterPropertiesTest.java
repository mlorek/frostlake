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

import dev.frostlake.query.MeasuredCellsTestSupport;
import org.junit.jupiter.api.Test;

/**
 * ALTER SEQUENCE reads any property's name, set or unset, with or without SET: a name no parameter is refused as no
 * property of a sequence before the sequence is looked up, the first of a table hashed by name; a comment given a
 * value of another type is refused as a table's is; and a parameter, set or unset, is an operation the sequence does
 * not allow. Every answer is live-verified.
 */
public class SequenceAlterPropertiesTest extends MeasuredCellsTestSupport {

    private static final String NOT_ALLOWED = "REFUSED SQL access control error: | Insufficient privileges to operate "
        + "on sequence 'S973'.";

    private static String invalid(final String name) {
        return "REFUSED SQL compilation error: | invalid property '" + name + "' for 'SEQUENCE'";
    }

    @Test
    public void aNameNoParameterIsRefusedBeforeTheSequenceIsLookedUp() {
        engine.execute("CREATE OR REPLACE SEQUENCE s973");
        assertCells(new String[][] {
            {"ALTER SEQUENCE s973 SET x = 1", invalid("x")},
            {"ALTER SEQUENCE s973 UNSET x", invalid("x")},
            {"ALTER SEQUENCE s973 SET CASE = 1", invalid("CASE")},
            {"ALTER SEQUENCE s973 SET COMMENT = 'c' x = 1", invalid("x")},
            {"ALTER SEQUENCE s973 SET x = 1, INCREMENT = 2", invalid("x")},
            {"ALTER SEQUENCE s973 SET DEFAULT = 1 x = 2", invalid("x")},
            {"ALTER SEQUENCE s973 UNSET COMMENT, DEFAULT", invalid("DEFAULT")},
            {"ALTER SEQUENCE nosuch_s_w9 SET DEFAULT = 1", invalid("DEFAULT")},
            {"ALTER SEQUENCE s973 SET COMMENT = 5",
                "REFUSED SQL compilation error: | invalid value [5] for parameter 'COMMENT'"},
        });
    }

    @Test
    public void aParameterIsAnOperationTheSequenceDoesNotAllow() {
        engine.execute("CREATE OR REPLACE SEQUENCE s973");
        assertCells(new String[][] {
            {"ALTER SEQUENCE s973 SET DATA_RETENTION_TIME_IN_DAYS = 1", NOT_ALLOWED},
            {"ALTER SEQUENCE s973 SET TIMEZONE = 'UTC'", NOT_ALLOWED},
            {"ALTER SEQUENCE s973 UNSET TIMEZONE", NOT_ALLOWED},
            {"ALTER SEQUENCE s973 SET LOG_LEVEL = 'INFO'", NOT_ALLOWED},
            {"ALTER SEQUENCE s973 SET COMMENT = 'c'", "Statement executed successfully."},
            {"ALTER SEQUENCE s973 UNSET COMMENT", "Statement executed successfully."},
        });
    }
}

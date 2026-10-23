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
 * A property a stage or a file format does not take is refused named as written, at CREATE and at ALTER … SET:
 * invalid property 'foo' for 'STAGE', and for 'FILE_FORMAT' where a file format's statement writes it alone;
 * beside another option, or in a stage's format list, it is an invalid parameter (all live-verified).
 */
public class StagePropertyNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void unknownPropertiesAsWritten() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        engine.execute("CREATE OR REPLACE TABLE t40 (a INT)");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'foo' for 'STAGE'",
            "CREATE OR REPLACE STAGE s40 foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'Foo' for 'STAGE'",
            "CREATE OR REPLACE STAGE s40 Foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'foo' for 'FILE_FORMAT'",
            "CREATE OR REPLACE FILE FORMAT ff40 foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'Foo' for 'FILE_FORMAT'",
            "CREATE OR REPLACE FILE FORMAT ff40 Foo = 1");
        assertAnswer("Stage area S40 successfully created.", "CREATE OR REPLACE STAGE s40");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'foo' for 'STAGE'",
            "ALTER STAGE s40 SET foo = 1");
        assertAnswer("File format FF40 successfully created.", "CREATE OR REPLACE FILE FORMAT ff40");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'foo' for 'FILE_FORMAT'",
            "ALTER FILE FORMAT ff40 SET foo = 1");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }

    @Test
    public void formatOptionAloneOrBesideOthers() {
        assertAnswer("REFUSED SQL compilation error: | invalid property 'foo' for 'FILE_FORMAT'",
            "CREATE OR REPLACE FILE FORMAT f40 foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'foo'",
            "CREATE OR REPLACE FILE FORMAT f40 foo = 1 TYPE = CSV");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'foo'",
            "CREATE OR REPLACE FILE FORMAT f40 TYPE = CSV foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'foo'",
            "CREATE OR REPLACE FILE FORMAT f40 TYPE = JSON foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'foo'",
            "CREATE OR REPLACE FILE FORMAT f40 COMPRESSION = AUTO foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'foo'",
            "CREATE OR REPLACE FILE FORMAT f40 COMPRESSION = AUTO foo = 1 TYPE = JSON");
        assertAnswer("File format F40 successfully created.", "CREATE OR REPLACE FILE FORMAT f40 TYPE = CSV");
        assertAnswer("REFUSED SQL compilation error: | invalid property 'foo' for 'FILE_FORMAT'",
            "ALTER FILE FORMAT f40 SET foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'foo'",
            "ALTER FILE FORMAT f40 SET TYPE = CSV foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'foo'",
            "ALTER FILE FORMAT f40 SET SKIP_HEADER = 1 foo = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'foo'",
            "CREATE OR REPLACE STAGE s40 FILE_FORMAT = (foo = 1)");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter 'foo'",
            "CREATE OR REPLACE STAGE s40 FILE_FORMAT = (TYPE = CSV foo = 1)");
        assertAnswer("REFUSED SQL compilation error: | invalid property '\"foo\"' for 'FILE_FORMAT'",
            "CREATE OR REPLACE FILE FORMAT f40 \"foo\" = 1");
        assertAnswer("REFUSED SQL compilation error: | invalid parameter '\"foo\"'",
            "CREATE OR REPLACE FILE FORMAT f40 TYPE = CSV \"foo\" = 1");
    }
}

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
 * The snowpark shim's classes carry Snowpark's names and modules - snowflake.snowpark.dataframe.DataFrame,
 * snowflake.snowpark.relational_grouped_dataframe.RelationalGroupedDataFrame, the session, row, column, writer and
 * reader classes - and the package version the account installs by default (all live-verified).
 */
public class SnowparkShimNamesTest extends MeasuredCellsTestSupport {

    @Test
    public void classNames() {
        engine.execute("ALTER SESSION SET USE_CACHED_RESULT = FALSE");
        assertAnswer("Function NAMES49 successfully created.",
            "CREATE OR REPLACE PROCEDURE names49() RETURNS VARCHAR LANGUAGE PYTHON RUNTIME_VERSION='3.12' "
                + "PACKAGES=('snowflake-snowpark-python') HANDLER='main' AS $$\nimport snowflake.snowpark as "
                + "sp\nfrom snowflake.snowpark.functions import col\ndef main(session):\n    df = "
                + "session.sql('SELECT 1 a')\n    out = []\n    for o in (df, df.group_by('A'), session, "
                + "df.collect()[0], df['A'], col('a'), df.write, session.read):\n        t = type(o)\n        "
                + "out.append(t.__module__ + '.' + t.__qualname__)\n    out.append(sp.__version__)\n    "
                + "out.append(repr(df).split(' object at')[0])\n    out.append(sp.Session.__module__ + '.' + "
                + "sp.Session.__name__)\n    out.append(sp.DataFrame.__module__ + '.' + sp.DataFrame.__name__)\n    "
                + "return ' | '.join(out)\n$$");
        assertAnswer("snowflake.snowpark.dataframe.DataFrame | "
                + "snowflake.snowpark.relational_grouped_dataframe.RelationalGroupedDataFrame | "
                + "snowflake.snowpark.session.Session | snowflake.snowpark.row.Row | snowflake.snowpark.column.Column "
                + "| snowflake.snowpark.column.Column | snowflake.snowpark.dataframe_writer.DataFrameWriter | "
                + "snowflake.snowpark.dataframe_reader.DataFrameReader | 1.55.3 | "
                + "<snowflake.snowpark.dataframe.DataFrame | snowflake.snowpark.session.Session | "
                + "snowflake.snowpark.dataframe.DataFrame",
            "CALL names49()");
        engine.execute("ALTER SESSION UNSET USE_CACHED_RESULT");
    }
}

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
 * A Scala procedure declared RETURNS TABLE answers the rows of the DataFrame its handler returns. Every
 * answer is live-verified.
 */
public class ScalaTableProcedureTest extends MeasuredCellsTestSupport {

    @Test
    public void aTableProcedureAnswersItsDataFramesRows() {
        assertAnswer(
            "Function SP9 successfully created.",
            "CREATE OR REPLACE PROCEDURE sp9() RETURNS TABLE(a NUMBER) LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS $$ import "
                + "com.snowflake.snowpark_java.Session; import com.snowflake.snowpark_java.DataFrame; object H { def "
                + "run(s: Session): DataFrame = s.sql(\"SELECT 1 AS a UNION ALL SELECT 2\") } $$");
        assertAnswer(
            "1 ;; 2",
            "CALL sp9()");
    }
}

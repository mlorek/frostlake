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

package dev.frostlake.udf;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * DataFrame.schema() over geospatial columns: a GEOMETRY column is Snowpark's GeometryType and a GEOGRAPHY one
 * its GeographyType, and a field prints either by its whole class name (all live-verified).
 */
public class SnowparkGeospatialSchemaTest extends MeasuredCellsTestSupport {

    @Test
    public void geospatialColumns() {
        engine.execute("CREATE OR REPLACE PROCEDURE java_schema(q VARCHAR) RETURNS VARCHAR LANGUAGE JAVA "
            + "RUNTIME_VERSION = '11' PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.run' AS "
            + "$$\nimport com.snowflake.snowpark_java.*;\nclass H {\n  public static String run(Session s, "
            + "String q) {\n    return s.sql(q).schema().toString();\n  }\n}\n$$");
        engine.execute("CREATE OR REPLACE TABLE rg_t (g GEOMETRY, h GEOGRAPHY)");
        assertAnswer("StructType[StructField(G, GeometryType, Nullable = true), StructField(H, GeographyType, "
                + "Nullable = true)]",
            "CALL java_schema('SELECT g, h FROM rg_t')");
        assertAnswer("StructType[StructField(H, GeographyType, Nullable = true), StructField(G, GeometryType, "
                + "Nullable = true)]",
            "CALL java_schema('SELECT h, g FROM rg_t')");
    }
}

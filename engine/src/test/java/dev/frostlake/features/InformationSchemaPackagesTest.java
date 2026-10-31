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

import org.junit.jupiter.api.Test;

/**
 * INFORMATION_SCHEMA.PACKAGES lists each package a Java and a Scala routine's PACKAGES may name, at each of its
 * versions, with no runtime version (live-verified): four VARCHAR columns, the view's columns asked of a database's own
 * schema, never of the current one.
 */
public class InformationSchemaPackagesTest extends MeasuredCellsTestSupport {

    /** The Java and the Scala lists, their counts and the view's columns. */
    @Test
    public void javaAndScalaPackages() {
        assertCells(new String[][] {
            {"SELECT * FROM information_schema.packages WHERE language = 'java' ORDER BY package_name, version LIMIT 4",
                "com.snowflake:snowpark|1.10.0|java|null ;; com.snowflake:snowpark|1.11.0|java|null ;; "
                + "com.snowflake:snowpark|1.12.0|java|null ;; com.snowflake:snowpark|1.12.1|java|null"},
            {"SELECT * FROM information_schema.packages WHERE language = 'scala' ORDER BY package_name, version LIMIT "
                + "4",
                "com.snowflake:snowpark|1.10.0|scala|null ;; com.snowflake:snowpark|1.11.0|scala|null ;; "
                + "com.snowflake:snowpark|1.12.0|scala|null ;; com.snowflake:snowpark|1.12.1|scala|null"},
            {"SELECT DISTINCT runtime_version FROM information_schema.packages WHERE language = 'java' ORDER BY 1",
                "null"},
            {"SELECT SYSTEM$TYPEOF(package_name), SYSTEM$TYPEOF(version), SYSTEM$TYPEOF(language), "
                + "SYSTEM$TYPEOF(runtime_version) FROM information_schema.packages LIMIT 1",
                "VARCHAR[LOB]|VARCHAR[LOB]|VARCHAR[LOB]|VARCHAR[LOB]"},
            {"SELECT COUNT(*) FROM information_schema.packages WHERE language = 'java' AND runtime_version IS NULL",
                "54"},
            {"SELECT package_name, version FROM information_schema.packages WHERE language = 'java' ORDER BY "
                + "package_name, version",
                "com.snowflake:snowpark|1.10.0 ;; com.snowflake:snowpark|1.11.0 ;; com.snowflake:snowpark|1.12.0 ;; "
                + "com.snowflake:snowpark|1.12.1 ;; com.snowflake:snowpark|1.13.0 ;; com.snowflake:snowpark|1.13.1 ;; "
                + "com.snowflake:snowpark|1.13.2 ;; com.snowflake:snowpark|1.14.0 ;; com.snowflake:snowpark|1.15.0 ;; "
                + "com.snowflake:snowpark|1.16.0 ;; com.snowflake:snowpark|1.16.1 ;; com.snowflake:snowpark|1.16.2 ;; "
                + "com.snowflake:snowpark|1.2.0 ;; com.snowflake:snowpark|1.3.0 ;; com.snowflake:snowpark|1.4.0 ;; "
                + "com.snowflake:snowpark|1.4.1 ;; com.snowflake:snowpark|1.5.0 ;; com.snowflake:snowpark|1.6.0 ;; "
                + "com.snowflake:snowpark|1.6.2 ;; com.snowflake:snowpark|1.7.0 ;; com.snowflake:snowpark|1.7.1 ;; "
                + "com.snowflake:snowpark|1.7.2 ;; com.snowflake:snowpark|1.8.0 ;; com.snowflake:snowpark|1.9.0 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.0 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.1 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.4 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.5 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.6 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.8 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.9 ;; "
                + "com.snowflake:snowpark_2.12|1.17.0 ;; com.snowflake:snowpark_2.12|1.18.0 ;; "
                + "com.snowflake:snowpark_2.12|1.19.0 ;; com.snowflake:snowpark_2.12|1.19.1 ;; "
                + "com.snowflake:snowpark_2.12|1.20.0 ;; com.snowflake:snowpark_2.12|1.21.0 ;; "
                + "com.snowflake:snowpark_2.13|1.17.0 ;; com.snowflake:snowpark_2.13|1.18.0 ;; "
                + "com.snowflake:snowpark_2.13|1.19.0 ;; com.snowflake:snowpark_2.13|1.19.1 ;; "
                + "com.snowflake:snowpark_2.13|1.20.0 ;; com.snowflake:snowpark_2.13|1.21.0 ;; "
                + "com.snowflake:spark-core_2.12|3.5.7.0 ;; com.snowflake:spark-core_2.12|3.5.7.1 ;; "
                + "com.snowflake:spark-core_2.13|4.1.2.0 ;; com.snowflake:spark-ml_2.12|3.5.7.0 ;; "
                + "com.snowflake:spark-ml_2.12|3.5.7.1 ;; com.snowflake:spark-ml_2.13|4.1.2.0 ;; "
                + "com.snowflake:spark-streaming_2.12|3.5.7.0 ;; com.snowflake:spark-streaming_2.12|3.5.7.1 ;; "
                + "com.snowflake:spark-streaming_2.13|4.1.2.0 ;; com.snowflake:telemetry|0.0.1 ;; "
                + "com.snowflake:telemetry|0.1.0"},
            {"SHOW COLUMNS IN VIEW information_schema.packages",
                "REFUSED SQL compilation error: | Must specify the full search path starting from database for "
                + "PACKAGES"},
            {"SELECT language, COUNT(*), COUNT(DISTINCT package_name), COUNT(runtime_version), MIN(runtime_version), "
                + "MAX(runtime_version) FROM information_schema.packages WHERE language <> 'python' GROUP BY language "
                + "ORDER BY language",
                "java|54|11|0|null|null ;; scala|54|11|0|null|null"},
            {"SELECT package_name, version FROM information_schema.packages WHERE language = 'scala' ORDER BY "
                + "package_name, version",
                "com.snowflake:snowpark|1.10.0 ;; com.snowflake:snowpark|1.11.0 ;; com.snowflake:snowpark|1.12.0 ;; "
                + "com.snowflake:snowpark|1.12.1 ;; com.snowflake:snowpark|1.13.0 ;; com.snowflake:snowpark|1.13.1 ;; "
                + "com.snowflake:snowpark|1.13.2 ;; com.snowflake:snowpark|1.14.0 ;; com.snowflake:snowpark|1.15.0 ;; "
                + "com.snowflake:snowpark|1.16.0 ;; com.snowflake:snowpark|1.16.1 ;; com.snowflake:snowpark|1.16.2 ;; "
                + "com.snowflake:snowpark|1.2.0 ;; com.snowflake:snowpark|1.3.0 ;; com.snowflake:snowpark|1.4.0 ;; "
                + "com.snowflake:snowpark|1.4.1 ;; com.snowflake:snowpark|1.5.0 ;; com.snowflake:snowpark|1.6.0 ;; "
                + "com.snowflake:snowpark|1.6.2 ;; com.snowflake:snowpark|1.7.0 ;; com.snowflake:snowpark|1.7.1 ;; "
                + "com.snowflake:snowpark|1.7.2 ;; com.snowflake:snowpark|1.8.0 ;; com.snowflake:snowpark|1.9.0 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.0 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.1 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.4 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.5 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.6 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.8 ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.9 ;; "
                + "com.snowflake:snowpark_2.12|1.17.0 ;; com.snowflake:snowpark_2.12|1.18.0 ;; "
                + "com.snowflake:snowpark_2.12|1.19.0 ;; com.snowflake:snowpark_2.12|1.19.1 ;; "
                + "com.snowflake:snowpark_2.12|1.20.0 ;; com.snowflake:snowpark_2.12|1.21.0 ;; "
                + "com.snowflake:snowpark_2.13|1.17.0 ;; com.snowflake:snowpark_2.13|1.18.0 ;; "
                + "com.snowflake:snowpark_2.13|1.19.0 ;; com.snowflake:snowpark_2.13|1.19.1 ;; "
                + "com.snowflake:snowpark_2.13|1.20.0 ;; com.snowflake:snowpark_2.13|1.21.0 ;; "
                + "com.snowflake:spark-core_2.12|3.5.7.0 ;; com.snowflake:spark-core_2.12|3.5.7.1 ;; "
                + "com.snowflake:spark-core_2.13|4.1.2.0 ;; com.snowflake:spark-ml_2.12|3.5.7.0 ;; "
                + "com.snowflake:spark-ml_2.12|3.5.7.1 ;; com.snowflake:spark-ml_2.13|4.1.2.0 ;; "
                + "com.snowflake:spark-streaming_2.12|3.5.7.0 ;; com.snowflake:spark-streaming_2.12|3.5.7.1 ;; "
                + "com.snowflake:spark-streaming_2.13|4.1.2.0 ;; com.snowflake:telemetry|0.0.1 ;; "
                + "com.snowflake:telemetry|0.1.0"},
            {"SHOW COLUMNS IN VIEW test_db.information_schema.packages",
                "PACKAGES|INFORMATION_SCHEMA|PACKAGE_NAME|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":6710886"
                + "4,\"nullable\":false,\"fixed\":false}|NOT_NULL||VIEW_COLUMN||Package name|TEST_DB||null|null ;; "
                + "PACKAGES|INFORMATION_SCHEMA|VERSION|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":false,\"fixed\":false}|NOT_NULL||VIEW_COLUMN||Package version|TEST_DB||null|null ;; "
                + "PACKAGES|INFORMATION_SCHEMA|LANGUAGE|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67108864,"
                + "\"nullable\":false,\"fixed\":false}|NOT_NULL||VIEW_COLUMN||Package language|TEST_DB||null|null ;; "
                + "PACKAGES|INFORMATION_SCHEMA|RUNTIME_VERSION|{\"type\":\"TEXT\",\"length\":16777216,\"byteLength\":67"
                + "108864,\"nullable\":true,\"fixed\":false}|true||VIEW_COLUMN||Supported runtime "
                + "version|TEST_DB||null|null"},
            {"SELECT * FROM test_db.information_schema.packages WHERE language = 'java' AND package_name LIKE "
                + "'%datasource%' ORDER BY version",
                "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.0|java|null ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.1|java|null ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.4|java|null ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.5|java|null ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.6|java|null ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.8|java|null ;; "
                + "com.snowflake:snowpark-spark-datasource-extensions_2.12|3.5.7.9|java|null"},
            {"DESCRIBE VIEW test_db.information_schema.packages",
                "PACKAGE_NAME|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|Package name|null|null|null ;; "
                + "VERSION|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|Package version|null|null|null ;; "
                + "LANGUAGE|VARCHAR(16777216)|COLUMN|N|null|N|N|null|null|Package language|null|null|null ;; "
                + "RUNTIME_VERSION|VARCHAR(16777216)|COLUMN|Y|null|N|N|null|null|Supported runtime "
                + "version|null|null|null"},
        });
    }
}

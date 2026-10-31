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
 * ALTER COLUMN ... SET DATA TYPE changes a structured column to another of its kind field by field. An OBJECT's fields
 * are matched by name - reordered, added as NULLs and dropped - unless RENAME FIELDS matches them by place, when their
 * number and their types must stay; an ARRAY's element and a MAP's key and value are matched as fields named element,
 * key and value. No field turns NOT NULL. A leaf of an Iceberg table widens as its format allows - int to long, float
 * to double, a decimal's precision at its scale - and a leaf of any other table widens a NUMBER's precision at its
 * scale or a VARCHAR's length; anything else is refused naming the field's path, and RENAME FIELDS on a column of no
 * structured type is unsupported. The stored values follow the new type (live-verified).
 */
public class StructuredTypeEvolutionTest extends MeasuredCellsTestSupport {

    /** An Iceberg OBJECT's fields are reordered, added and dropped by name, and renamed by place. */
    @Test
    public void anIcebergObjectReordersAddsDropsAndRenamesItsFields() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(b INT, a INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(b INT, a INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a INT, b INT, c INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a INT, b INT, c INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a LONG, b INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a LONG, b INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(c STRING, b LONG, a INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(c STRING, b LONG, a INT) | ) |  EXTERNAL_VOLUME "
                + "= 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(x INT, y INT) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(x INT, y INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(x LONG, y INT) RENAME FIELDS",
                "REFUSED SQL compilation error: RENAME FIELDS DDL on structured type column should not change the type "
                + "of field O.a "},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a INT, b INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(x INT, y INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(x INT, y INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o OBJECT(a INT, b INT))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE OBJECT(b INT, a INT) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO OBJECT(b INT, a INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o OBJECT(a INT))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE OBJECT(a INT) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO OBJECT(a INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o OBJECT(a OBJECT(p INT)))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE OBJECT(x OBJECT(q INT)) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO OBJECT(x OBJECT(q INT)) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o OBJECT(a INT, b INT))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE OBJECT(x INT) RENAME FIELDS",
                "REFUSED SQL compilation error: RENAME FIELDS DDL on structured field O should not drop fields"},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO OBJECT(a INT, b INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o INT)",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE INT RENAME FIELDS",
                "REFUSED Unsupported feature 'RENAME'."},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO INT | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  "
                + "ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o OBJECT(a INT))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE ARRAY(INT) RENAME FIELDS",
                "REFUSED SQL compilation error: RENAME FIELDS DDL on structured type column should not change the type "
                + "of field O "},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO OBJECT(a INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
        });
    }

    /** An Iceberg leaf widens int to long, float to double and a decimal's precision at its scale. */
    @Test
    public void anIcebergLeafWidensOnlyAsItsFormatAllows() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a STRING, b INT)",
                "REFUSED SQL compilation error: cannot change column O.a from type int to string | "},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a INT, b INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a LONG))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a INT)",
                "REFUSED SQL compilation error: cannot change column O.a from type long to int | "},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a LONG) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o ARRAY(INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE ARRAY(LONG)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO ARRAY(LONG) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o ARRAY(INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE ARRAY(STRING)",
                "REFUSED SQL compilation error: cannot change column O.element from type int to string | "},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO ARRAY(INT) | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' "
                + "|  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o MAP(STRING, INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE MAP(STRING, LONG)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO MAP(STRING, LONG) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o MAP(INT, INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE MAP(LONG, INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO MAP(LONG, INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(x FLOAT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(x DOUBLE)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(x DOUBLE) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(y DECIMAL(10,2)))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(y DECIMAL(12,2))",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(y DECIMAL(12, 2)) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(y DECIMAL(10,2)))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(y DECIMAL(12,3))",
                "REFUSED SQL compilation error: cannot change column O.y from type decimal(10, 2) to decimal(12, 3) | "
                + ""},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(y DECIMAL(10, 2)) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(y DECIMAL(10,2)))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(y DECIMAL(9,2))",
                "REFUSED SQL compilation error: cannot change column O.y from type decimal(10, 2) to decimal(9, 2) | "},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(y DECIMAL(10, 2)) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE VARCHAR",
                "REFUSED SQL compilation error: cannot change column O from type OBJECT(a NUMBER(10,0)) to "
                + "VARCHAR(134217728) | "},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o OBJECT(a INT))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE OBJECT(a DECIMAL(12,0))",
                "REFUSED SQL compilation error: cannot change column O.a from type int to decimal(12, 0) | "},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO OBJECT(a INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o OBJECT(a DECIMAL(10,2)))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE OBJECT(a DECIMAL(10,3))",
                "REFUSED SQL compilation error: cannot change column O.a from type decimal(10, 2) to decimal(10, 3) | "
                + ""},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO OBJECT(a DECIMAL(10, 2)) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o OBJECT(a STRING))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE OBJECT(a BINARY)",
                "REFUSED SQL compilation error: cannot change column O.a from type string to binary | "},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO OBJECT(a STRING) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
        });
    }

    /** A nested OBJECT evolves field by field, and no field, element or value turns NOT NULL. */
    @Test
    public void anIcebergFieldDoesNotTurnNonNullable() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a OBJECT(p INT)))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a OBJECT(p LONG, q INT))",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a OBJECT(p LONG, q INT)) | ) |  EXTERNAL_VOLUME "
                + "= 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a INT NOT NULL, b INT)",
                "REFUSED SQL compilation error: DDL operation failed because it would result in a non-nullable "
                + "structured type field 'O.a' contained within a nullable object"},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a INT, b INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a INT, b INT, c INT NOT NULL)",
                "REFUSED SQL compilation error: DDL operation failed because it would result in a non-nullable "
                + "structured type field 'O.c' contained within a nullable object"},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a INT, b INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o ARRAY(INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE ARRAY(INT NOT NULL)",
                "REFUSED SQL compilation error: DDL operation failed because it would result in a non-nullable "
                + "structured type field 'O.element' contained within an array or map"},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO ARRAY(INT) | ) |  EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' "
                + "|  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT NULL",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a INT, b INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(a INT, b INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o MAP(INT, INT))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE MAP(INT, INT NOT NULL)",
                "REFUSED SQL compilation error: DDL operation failed because it would result in a non-nullable "
                + "structured type field 'O.value' contained within an array or map"},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO MAP(INT, INT) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iz786 (o OBJECT(a STRING))",
                "Table IZ786 successfully created."},
            {"ALTER ICEBERG TABLE iz786 ALTER COLUMN o SET DATA TYPE OBJECT(a STRING NOT NULL)",
                "REFUSED SQL compilation error: DDL operation failed because it would result in a non-nullable "
                + "structured type field 'O.a' contained within a nullable object"},
            {"SELECT GET_DDL('TABLE', 'iz786')",
                "create or replace ICEBERG TABLE IZ786 ( | \tO OBJECT(a STRING) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
        });
    }

    /** The stored values are reordered, filled, cut and renamed with the type. */
    @Test
    public void anIcebergColumnsValuesFollowItsType() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE ICEBERG TABLE ie783 (o OBJECT(a INT, b INT))",
                "Table IE783 successfully created."},
            {"INSERT INTO ie783 SELECT {'a': 1, 'b': 2}::OBJECT(a INT, b INT)",
                "1"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(b INT, a LONG, c STRING)",
                "Statement executed successfully."},
            {"SELECT o FROM ie783",
                "{\"b\":2,\"a\":1,\"c\":null}"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(a LONG)",
                "Statement executed successfully."},
            {"SELECT o FROM ie783",
                "{\"a\":1}"},
            {"ALTER ICEBERG TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(z LONG) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT o FROM ie783",
                "{\"z\":1}"},
            {"ALTER TABLE ie783 ALTER COLUMN o SET DATA TYPE OBJECT(z LONG, w INT)",
                "REFUSED SQL compilation error: | Unsupported data type 'LONG'."},
            {"SELECT GET_DDL('TABLE', 'ie783')",
                "create or replace ICEBERG TABLE IE783 ( | \tO OBJECT(z LONG) | ) |  EXTERNAL_VOLUME = "
                + "'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
            {"CREATE OR REPLACE ICEBERG TABLE iy786 (o OBJECT(a OBJECT(p INT, r INT)))",
                "Table IY786 successfully created."},
            {"INSERT INTO iy786 SELECT {'a': {'p': 1, 'r': 2}}::OBJECT(a OBJECT(p INT, r INT))",
                "1"},
            {"ALTER ICEBERG TABLE iy786 ALTER COLUMN o SET DATA TYPE OBJECT(a OBJECT(r LONG, s STRING))",
                "Statement executed successfully."},
            {"SELECT o FROM iy786",
                "{\"a\":{\"r\":2,\"s\":null}}"},
            {"ALTER ICEBERG TABLE iy786 ALTER COLUMN o SET DATA TYPE OBJECT(b OBJECT(u LONG, v STRING)) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT o FROM iy786",
                "{\"b\":{\"u\":2,\"v\":null}}"},
            {"SELECT GET_DDL('TABLE', 'iy786')",
                "create or replace ICEBERG TABLE IY786 ( | \tO OBJECT(b OBJECT(u LONG, v STRING)) | ) |  "
                + "EXTERNAL_VOLUME = 'SNOWFLAKE_MANAGED' |  ICEBERG_VERSION = 2 |  CATALOG = 'SNOWFLAKE';"},
        });
    }

    /**
     * A table's OBJECT evolves as an Iceberg one does, and RENAME FIELDS keeps the number of fields and their types.
     */
    @Test
    public void aTableObjectReordersAddsDropsAndRenamesItsFields() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE rt784 (o OBJECT(a INT, b INT))",
                "Table RT784 successfully created."},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE OBJECT(b INT, a INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'rt784')",
                "create or replace TABLE RT784 ( | \tO OBJECT(b NUMBER(38,0), a NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rt784 (o OBJECT(a INT, b INT))",
                "Table RT784 successfully created."},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE OBJECT(a INT, b INT, c INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'rt784')",
                "create or replace TABLE RT784 ( | \tO OBJECT(a NUMBER(38,0), b NUMBER(38,0), c NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rt784 (o OBJECT(a INT, b INT))",
                "Table RT784 successfully created."},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE OBJECT(a INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'rt784')",
                "create or replace TABLE RT784 ( | \tO OBJECT(a NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rt784 (o OBJECT(a INT, b INT))",
                "Table RT784 successfully created."},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE OBJECT(x INT, y INT) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'rt784')",
                "create or replace TABLE RT784 ( | \tO OBJECT(x NUMBER(38,0), y NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o NUMBER(10,0))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE NUMBER(10,0) RENAME FIELDS",
                "REFUSED Unsupported feature 'RENAME'."},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO NUMBER(10,0) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o NUMBER(10,0))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE NUMBER(12,0) RENAME FIELDS",
                "REFUSED Unsupported feature 'RENAME'."},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO NUMBER(10,0) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o VARIANT)",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE VARIANT RENAME FIELDS",
                "REFUSED Unsupported feature 'RENAME'."},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO VARIANT | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT)",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT RENAME FIELDS",
                "REFUSED Unsupported feature 'RENAME'."},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT | );"},
            {"CREATE OR REPLACE TABLE rx785 (o ARRAY(INT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE ARRAY(INT) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO ARRAY(NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a INT, b INT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(x INT) RENAME FIELDS",
                "REFUSED SQL compilation error: RENAME FIELDS DDL on structured field O should not drop fields"},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a NUMBER(38,0), b NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a INT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(x INT, y INT) RENAME FIELDS",
                "REFUSED SQL compilation error: RENAME FIELDS DDL on structured field O should not add fields"},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a INT, b INT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(x INT, y VARCHAR) RENAME FIELDS",
                "REFUSED SQL compilation error: RENAME FIELDS DDL on structured type column should not change the type "
                + "of field O.b "},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a NUMBER(38,0), b NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a OBJECT(p INT)))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(x OBJECT(q INT)) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(x OBJECT(q NUMBER(38,0))) | );"},
        });
    }

    /** A table's leaf widens a NUMBER's precision at its scale or a VARCHAR's length, and nothing else. */
    @Test
    public void aTableLeafWidensOnlyWhereNothingIsLost() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE rt784 (o OBJECT(a NUMBER(10,0), b INT))",
                "Table RT784 successfully created."},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE OBJECT(a NUMBER(12,0), b INT)",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'rt784')",
                "create or replace TABLE RT784 ( | \tO OBJECT(a NUMBER(12,0), b NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rt784 (o OBJECT(a INT, b INT))",
                "Table RT784 successfully created."},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE OBJECT(a VARCHAR, b INT)",
                "REFUSED SQL compilation error: cannot change column O.a from type NUMBER(38,0) to VARCHAR(16777216) | "
                + ""},
            {"SELECT GET_DDL('TABLE', 'rt784')",
                "create or replace TABLE RT784 ( | \tO OBJECT(a NUMBER(38,0), b NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rt784 (o ARRAY(VARCHAR(10)))",
                "Table RT784 successfully created."},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE ARRAY(VARCHAR(20))",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'rt784')",
                "create or replace TABLE RT784 ( | \tO ARRAY(VARCHAR(20)) | );"},
            {"CREATE OR REPLACE TABLE rt784 (o ARRAY(NUMBER(10,0)))",
                "Table RT784 successfully created."},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE ARRAY(NUMBER(12,0))",
                "Statement executed successfully."},
            {"SELECT GET_DDL('TABLE', 'rt784')",
                "create or replace TABLE RT784 ( | \tO ARRAY(NUMBER(12,0)) | );"},
            {"CREATE OR REPLACE TABLE rt784 (o MAP(VARCHAR, INT))",
                "Table RT784 successfully created."},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE MAP(VARCHAR, NUMBER(12,0))",
                "REFUSED Unsupported feature 'Decreasing the precision of structured field O.value is currently not "
                + "supported'."},
            {"SELECT GET_DDL('TABLE', 'rt784')",
                "create or replace TABLE RT784 ( | \tO MAP(VARCHAR(16777216), NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a VARCHAR(20)))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(a VARCHAR(10))",
                "REFUSED SQL compilation error: cannot change column O.a from type VARCHAR(20) to VARCHAR(10) because "
                + "reducing the byte-length of a varchar is not supported. | "},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a VARCHAR(20)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a NUMBER(10,2)))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(a NUMBER(10,3))",
                "REFUSED SQL compilation error: cannot change column O.a from type NUMBER(10,2) to NUMBER(10,3) "
                + "because changing the scale of a number is not supported. | "},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a NUMBER(10,2)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a INT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(a INT, b INT NOT NULL)",
                "REFUSED SQL compilation error: DDL operation failed because it would result in a non-nullable "
                + "structured type field 'O.b' contained within a nullable object"},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a INT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(a INT NOT NULL)",
                "REFUSED SQL compilation error: DDL operation failed because it would result in a non-nullable "
                + "structured type field 'O.a' contained within a nullable object"},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a INT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE ARRAY(INT)",
                "REFUSED SQL compilation error: cannot change column O from type OBJECT(a NUMBER(38,0)) to "
                + "ARRAY(NUMBER(38,0)) | "},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a INT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(a ARRAY(INT))",
                "REFUSED SQL compilation error: cannot change column O.a from type NUMBER(38,0) to ARRAY(NUMBER(38,0)) "
                + "| "},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a NUMBER(38,0)) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o OBJECT(a FLOAT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE OBJECT(a NUMBER(10,0))",
                "REFUSED SQL compilation error: cannot change column O.a from type FLOAT to NUMBER(10,0) | "},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO OBJECT(a FLOAT) | );"},
            {"CREATE OR REPLACE TABLE rx785 (o MAP(VARCHAR, INT))",
                "Table RX785 successfully created."},
            {"ALTER TABLE rx785 ALTER COLUMN o SET DATA TYPE MAP(VARCHAR(10), INT)",
                "REFUSED SQL compilation error: cannot change column O.key from type VARCHAR(16777216) to VARCHAR(10) "
                + "because reducing the byte-length of a varchar is not supported. | "},
            {"SELECT GET_DDL('TABLE', 'rx785')",
                "create or replace TABLE RX785 ( | \tO MAP(VARCHAR(16777216), NUMBER(38,0)) | );"},
        });
    }

    /** The stored values are reordered, filled and renamed with the type. */
    @Test
    public void aTableColumnsValuesFollowItsType() {
        assertCells(new String[][] {
            {"CREATE OR REPLACE TABLE rt784 (o OBJECT(a INT, b INT))",
                "Table RT784 successfully created."},
            {"INSERT INTO rt784 SELECT {'a': 1, 'b': 2}::OBJECT(a INT, b INT)",
                "1"},
            {"ALTER TABLE rt784 ALTER COLUMN o SET DATA TYPE OBJECT(b INT, a INT)",
                "Statement executed successfully."},
            {"SELECT o FROM rt784",
                "{\"b\":2,\"a\":1}"},
            {"CREATE OR REPLACE TABLE ry785 (o OBJECT(a INT, b VARCHAR))",
                "Table RY785 successfully created."},
            {"INSERT INTO ry785 SELECT {'a': 1, 'b': 'x'}::OBJECT(a INT, b VARCHAR)",
                "1"},
            {"ALTER TABLE ry785 ALTER COLUMN o SET DATA TYPE OBJECT(b VARCHAR, c INT, a NUMBER(38,0))",
                "Statement executed successfully."},
            {"SELECT o FROM ry785",
                "{\"b\":\"x\",\"c\":null,\"a\":1}"},
            {"ALTER TABLE ry785 ALTER COLUMN o SET DATA TYPE OBJECT(x VARCHAR, y INT, z NUMBER(38,0)) RENAME FIELDS",
                "Statement executed successfully."},
            {"SELECT o FROM ry785",
                "{\"x\":\"x\",\"y\":null,\"z\":1}"},
            {"SELECT o:x::VARCHAR, o:z FROM ry785",
                "x|1"},
        });
    }
}

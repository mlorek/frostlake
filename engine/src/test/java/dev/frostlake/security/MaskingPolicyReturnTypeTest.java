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
package dev.frostlake.security;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * DESCRIBE MASKING POLICY tells the RETURNS type as declared, in the account's own spelling (live-verified): the
 * approximate family reads FLOAT, a fixed-point number its precision and scale, a time its precision, and a text
 * or a binary the width it was written with — none when it was written with none.
 */
public class MaskingPolicyReturnTypeTest extends BaseDatabaseTest {

    private String returnTypeOf(final String declared) {
        engine.execute("CREATE OR REPLACE MASKING POLICY mp AS (val " + declared + ") RETURNS " + declared + " -> val");
        final ResultSet rs = engine.executeQuery("DESC MASKING POLICY mp");
        return String.valueOf(rs.getRows().get(0).getValue(rs.getColumnIndex("return_type")));
    }

    @Test
    public void aNumberReadsInTheAccountsSpelling() {
        assertEquals("FLOAT", returnTypeOf("FLOAT"));
        assertEquals("FLOAT", returnTypeOf("DOUBLE"));
        assertEquals("FLOAT", returnTypeOf("REAL"));
        assertEquals("FLOAT", returnTypeOf("DOUBLE PRECISION"));
        assertEquals("NUMBER(38,0)", returnTypeOf("NUMBER"));
        assertEquals("NUMBER(38,0)", returnTypeOf("INT"));
        assertEquals("NUMBER(10,2)", returnTypeOf("NUMBER(10,2)"));
        assertEquals("NUMBER(5,1)", returnTypeOf("DECIMAL(5,1)"));
    }

    @Test
    public void aTextOrABinaryKeepsTheWidthItWasWrittenWith() {
        assertEquals("VARCHAR", returnTypeOf("VARCHAR"));
        assertEquals("VARCHAR", returnTypeOf("STRING"));
        assertEquals("VARCHAR", returnTypeOf("TEXT"));
        assertEquals("VARCHAR(10)", returnTypeOf("VARCHAR(10)"));
        assertEquals("VARCHAR(16777216)", returnTypeOf("VARCHAR(16777216)"), "written out, the default width is kept");
        assertEquals("VARCHAR(3)", returnTypeOf("CHAR(3)"));
        assertEquals("VARCHAR(1)", returnTypeOf("CHAR"));
        assertEquals("BINARY", returnTypeOf("BINARY"));
        assertEquals("BINARY", returnTypeOf("VARBINARY"));
        assertEquals("BINARY(10)", returnTypeOf("BINARY(10)"));
    }

    @Test
    public void aTimeReadsWithItsPrecision() {
        assertEquals("DATE", returnTypeOf("DATE"));
        assertEquals("TIME(9)", returnTypeOf("TIME"));
        assertEquals("TIME(3)", returnTypeOf("TIME(3)"));
        assertEquals("TIMESTAMP_NTZ(9)", returnTypeOf("TIMESTAMP"));
        assertEquals("TIMESTAMP_NTZ(9)", returnTypeOf("DATETIME"));
        assertEquals("TIMESTAMP_NTZ(3)", returnTypeOf("TIMESTAMP_NTZ(3)"));
        assertEquals("TIMESTAMP_LTZ(9)", returnTypeOf("TIMESTAMP_LTZ"));
        assertEquals("TIMESTAMP_TZ(9)", returnTypeOf("TIMESTAMP_TZ"));
        assertEquals("BOOLEAN", returnTypeOf("BOOLEAN"));
        assertEquals("VARIANT", returnTypeOf("VARIANT"));
        assertEquals("ARRAY", returnTypeOf("ARRAY"));
        assertEquals("OBJECT", returnTypeOf("OBJECT"));
    }

    @Test
    public void theSignatureAndTheCreatedSentencesAreThePoliciesOwn() {
        engine.execute("CREATE OR REPLACE MASKING POLICY mp2 AS (val FLOAT, other VARCHAR(5), n NUMBER(4,1)) RETURNS"
            + " FLOAT -> val");
        final ResultSet described = engine.executeQuery("DESC MASKING POLICY mp2");
        assertEquals("(VAL FLOAT, OTHER VARCHAR, N NUMBER)",
            described.getRows().get(0).getValue(described.getColumnIndex("signature")));
        assertEquals("Masking policy MP3 successfully created.", engine.executeQuery(
            "CREATE MASKING POLICY mp3 AS (val VARCHAR) RETURNS VARCHAR -> val").getRows().get(0).getValue(0));
        assertEquals("Row access policy 'RAP3' is successfully created.", engine.executeQuery(
            "CREATE ROW ACCESS POLICY rap3 AS (v VARCHAR) RETURNS BOOLEAN -> TRUE").getRows().get(0).getValue(0),
            "a row access policy alone quotes its name");
    }
}

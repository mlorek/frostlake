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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The declared-vs-actual return-type check on scalar {@code LANGUAGE SQL} UDFs at CREATE time,
 * live-verified cell by cell: incompatibility is decided by type FAMILY (fixed-point and
 * floating-point numerics are separate families, each timestamp flavor is its own, VARIANT / ARRAY /
 * OBJECT match only themselves), parameters — precision, scale, length — never matter, an explicit
 * cast in the body changes the verdict, a single-item FROM-less SELECT body is checked like a bare
 * expression, and neither procedures nor JavaScript handlers are checked at all. The refusal is the
 * bare sentence {@code Declared return type 'X' is incompatible with actual return type 'Y'} with
 * both sides spelled with their parameters (an undeclared length spells the type's maximum).
 */
public class UdfReturnTypeCompatibilityTest extends BaseDatabaseTest {

    private void assertRefused(final String sql, final String declared, final String actual) {
        final RuntimeException e = assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        });
        assertEquals("Declared return type '" + declared + "' is incompatible with actual return type '"
            + actual + "'", e.getMessage());
    }

    @Test
    public void varcharFromCurrentTimestampIsRefused() {
        assertRefused("CREATE FUNCTION rt_a1() RETURNS VARCHAR AS 'CURRENT_TIMESTAMP'",
            "VARCHAR(134217728)", "TIMESTAMP_LTZ(9)");
    }

    @Test
    public void aCastInTheBodyChangesTheVerdict() {
        engine.execute("CREATE FUNCTION rt_a2() RETURNS VARCHAR AS 'CURRENT_TIMESTAMP::VARCHAR'");
        engine.execute("CREATE FUNCTION rt_a3() RETURNS TIMESTAMP_LTZ AS 'CURRENT_TIMESTAMP'");
    }

    @Test
    public void timestampFlavorsAreSeparateFamilies() {
        assertRefused("CREATE FUNCTION rt_a4() RETURNS TIMESTAMP_NTZ AS 'CURRENT_TIMESTAMP'",
            "TIMESTAMP_NTZ(9)", "TIMESTAMP_LTZ(9)");
        assertRefused("CREATE FUNCTION rt_a5() RETURNS DATE AS 'CURRENT_TIMESTAMP'",
            "DATE", "TIMESTAMP_LTZ(9)");
    }

    @Test
    public void theTimestampAliasSpellsItsNtzFlavor() {
        assertRefused("CREATE FUNCTION rt_d14() RETURNS TIMESTAMP AS 'CURRENT_TIMESTAMP'",
            "TIMESTAMP_NTZ(9)", "TIMESTAMP_LTZ(9)");
    }

    @Test
    public void varcharFromDateIsRefused() {
        assertRefused("CREATE FUNCTION rt_a6() RETURNS VARCHAR AS 'CURRENT_DATE'",
            "VARCHAR(134217728)", "DATE");
    }

    @Test
    public void literalsCarryTheirOwnMeasuredTypes() {
        assertRefused("CREATE FUNCTION rt_b1() RETURNS VARCHAR AS '42'",
            "VARCHAR(134217728)", "NUMBER(2,0)");
        assertRefused("CREATE FUNCTION rt_b2() RETURNS NUMBER AS '''abc'''",
            "NUMBER(38,0)", "VARCHAR(3)");
    }

    @Test
    public void narrowingInsideAFamilyIsAccepted() {
        engine.execute("CREATE FUNCTION rt_b3() RETURNS INTEGER AS '1.5'");
        engine.execute("CREATE FUNCTION rt_b4() RETURNS NUMBER(3,0) AS '12345'");
        engine.execute("CREATE FUNCTION rt_b5() RETURNS VARCHAR(5) AS '''abcdefgh'''");
    }

    @Test
    public void booleanIsItsOwnFamily() {
        assertRefused("CREATE FUNCTION rt_b6() RETURNS BOOLEAN AS '1'",
            "BOOLEAN", "NUMBER(1,0)");
        assertRefused("CREATE FUNCTION rt_b7() RETURNS VARCHAR AS 'TRUE'",
            "VARCHAR(134217728)", "BOOLEAN");
    }

    @Test
    public void variantDoesNotAbsorbOtherFamilies() {
        assertRefused("CREATE FUNCTION rt_b8() RETURNS VARIANT AS '42'",
            "VARIANT", "NUMBER(2,0)");
        assertRefused("CREATE FUNCTION rt_d6() RETURNS VARIANT AS 'ARRAY_CONSTRUCT(1)'",
            "VARIANT", "ARRAY");
        assertRefused("CREATE FUNCTION rt_d7() RETURNS OBJECT AS 'PARSE_JSON(''{}'')'",
            "OBJECT", "VARIANT");
        assertRefused("CREATE FUNCTION rt_d8() RETURNS ARRAY AS 'PARSE_JSON(''[]'')'",
            "ARRAY", "VARIANT");
        engine.execute("CREATE FUNCTION rt_d4() RETURNS VARIANT AS 'PARSE_JSON(''{}'')'");
        engine.execute("CREATE FUNCTION rt_d5() RETURNS ARRAY AS 'ARRAY_CONSTRUCT(1)'");
    }

    @Test
    public void aNullBodyIsCompatibleWithAnything() {
        engine.execute("CREATE FUNCTION rt_b9() RETURNS NUMBER AS 'NULL'");
    }

    @Test
    public void parameterReferencesCarryTheirDeclaredTypes() {
        assertRefused("CREATE FUNCTION rt_b10(x VARCHAR) RETURNS NUMBER AS 'x'",
            "NUMBER(38,0)", "VARCHAR(134217728)");
        engine.execute("CREATE FUNCTION rt_b11(x NUMBER) RETURNS NUMBER AS 'x'");
        engine.execute("CREATE FUNCTION rt_b12(x VARCHAR, y VARCHAR) RETURNS VARCHAR AS 'x || y'");
        engine.execute("CREATE FUNCTION rt_b13(x NUMBER) RETURNS NUMBER AS 'x + 1'");
    }

    @Test
    public void fixedAndFloatingPointAreSeparateFamilies() {
        assertRefused("CREATE FUNCTION rt_d1() RETURNS FLOAT AS '42'",
            "FLOAT", "NUMBER(2,0)");
        assertRefused("CREATE FUNCTION rt_d2() RETURNS NUMBER AS '1.5::FLOAT'",
            "NUMBER(38,0)", "FLOAT");
        assertRefused("CREATE FUNCTION rt_d3() RETURNS DOUBLE AS '1.5'",
            "FLOAT", "NUMBER(2,1)");
    }

    @Test
    public void timeIsItsOwnFamily() {
        engine.execute("CREATE FUNCTION rt_d9() RETURNS TIME AS 'CURRENT_TIME'");
        assertRefused("CREATE FUNCTION rt_d10() RETURNS VARCHAR AS 'CURRENT_TIME'",
            "VARCHAR(134217728)", "TIME(9)");
    }

    @Test
    public void binaryIsItsOwnFamily() {
        engine.execute("CREATE FUNCTION rt_d12() RETURNS BINARY AS 'TO_BINARY(''AB'',''HEX'')'");
        assertRefused("CREATE FUNCTION rt_d13() RETURNS VARCHAR AS 'TO_BINARY(''AB'',''HEX'')'",
            "VARCHAR(134217728)", "BINARY(67108864)");
    }

    @Test
    public void aSingleItemSelectBodyIsCheckedLikeAnExpression() {
        assertRefused("CREATE FUNCTION rt_c3() RETURNS DATE AS 'SELECT CURRENT_TIMESTAMP'",
            "DATE", "TIMESTAMP_LTZ(9)");
    }

    @Test
    public void proceduresAreNotChecked() {
        engine.execute("CREATE PROCEDURE rt_c1() RETURNS VARCHAR LANGUAGE SQL"
            + " AS 'BEGIN RETURN CURRENT_TIMESTAMP; END'");
    }

    @Test
    public void javascriptHandlersAreNotChecked() {
        engine.execute("CREATE FUNCTION rt_c2() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS 'return 42;'");
    }
}

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;
import org.junit.jupiter.api.Test;

/**
 * ZEROIFNULL CONVERTS where its twin does not. It substitutes a NUMBER for a NULL, so its answer takes
 * the type that substitution folds to — and over a family with no numeric width of its own, live
 * converts the argument to REAL rather than inventing one. NULLIFZERO, which only ever hands its own
 * argument back, keeps the argument's type untouched whatever family it is.
 *
 * <p>★ THE PAIR IS THE POINT. The two functions look symmetric and are not: over one VARIANT column
 * holding 7, live answers ZEROIFNULL 7.0 (FLOAT) and NULLIFZERO 7 (VARIANT). Frostlake used to answer
 * the argument unchanged from both.
 *
 * <p>★ THE TWO REFUSALS FALL OUT OF THE CONVERSION, and they are two different sentences because the
 * two source families spell theirs differently everywhere — a VARCHAR is named as text, a VARIANT as a
 * cast to REAL.
 */
public class ZeroIfNullConversionTest extends BaseDatabaseTest {

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new org.junit.jupiter.api.function.Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private String cellOf(final String sql) {
        final ResultSet rs = engine.executeQuery(sql);
        return String.valueOf(rs.getRows().get(0).getValue(0));
    }

    @Override
    protected void setupTest() {
        engine.execute("CREATE TABLE zc (v VARIANT, i INT, n NUMBER(10,2), f FLOAT, s VARCHAR)");
        engine.execute("INSERT INTO zc SELECT PARSE_JSON('null'), NULL, NULL, NULL, NULL");
        engine.execute("INSERT INTO zc SELECT PARSE_JSON('7'), 7, 7.25, 7.5, '7'");
    }

    @Test
    public void aVariantArgumentComesBackInTheFloatFamily() {
        assertEquals("0.0", cellOf("SELECT ZEROIFNULL(PARSE_JSON('null')) AS v"));
        assertEquals("0.0", cellOf("SELECT ZEROIFNULL(v) AS v FROM zc WHERE i IS NULL"));
        assertEquals("7.0", cellOf("SELECT ZEROIFNULL(v) AS v FROM zc WHERE i = 7"));
        assertEquals("7.5", cellOf("SELECT ZEROIFNULL(PARSE_JSON('7.5')) AS v"));
        assertEquals("FLOAT[DOUBLE]", cellOf("SELECT SYSTEM$TYPEOF(ZEROIFNULL(v)) AS v FROM zc WHERE i = 7"));
    }

    @Test
    public void aVarcharArgumentDoesTheSame() {
        assertEquals("7.0", cellOf("SELECT ZEROIFNULL(s) AS v FROM zc WHERE i = 7"));
        assertEquals("7.0", cellOf("SELECT ZEROIFNULL('7') AS v"));
        assertEquals("0.0", cellOf("SELECT ZEROIFNULL(s) AS v FROM zc WHERE i IS NULL"));
        assertEquals("FLOAT[DOUBLE]", cellOf("SELECT SYSTEM$TYPEOF(ZEROIFNULL(s)) AS v FROM zc WHERE i = 7"));
    }

    @Test
    public void aNumericArgumentKeepsItsOwnWidth() {
        assertEquals("0", cellOf("SELECT ZEROIFNULL(i) AS v FROM zc WHERE i IS NULL"));
        assertEquals("0.00", cellOf("SELECT ZEROIFNULL(n) AS v FROM zc WHERE i IS NULL"));
        assertEquals("0.0", cellOf("SELECT ZEROIFNULL(f) AS v FROM zc WHERE i IS NULL"));
        assertEquals("0.00", cellOf("SELECT ZEROIFNULL(CAST(NULL AS NUMBER(10,2))) AS v"));
        assertEquals("NUMBER(38,0)[SB1]", cellOf("SELECT SYSTEM$TYPEOF(ZEROIFNULL(i)) AS v FROM zc WHERE i = 7"));
        assertEquals("NUMBER(10,2)[SB2]", cellOf("SELECT SYSTEM$TYPEOF(ZEROIFNULL(n)) AS v FROM zc WHERE i = 7"));
        assertEquals("FLOAT[DOUBLE]", cellOf("SELECT SYSTEM$TYPEOF(ZEROIFNULL(f)) AS v FROM zc WHERE i = 7"));
    }

    @Test
    public void anUntypedNullTakesTheSubstitutedZerosOwnWidth() {
        assertEquals("0", cellOf("SELECT ZEROIFNULL(NULL) AS v"));
        assertEquals("NUMBER(2,0)[SB1]", cellOf("SELECT SYSTEM$TYPEOF(ZEROIFNULL(NULL)) AS v"));
    }

    @Test
    public void theConversionRefusesInTheSourcesOwnWords() {
        assertEquals("Numeric value 'a' is not recognized", refusalOf("SELECT ZEROIFNULL('a') AS v"));
        assertEquals("Failed to cast variant value \"a\" to REAL",
            refusalOf("SELECT ZEROIFNULL(PARSE_JSON('\"a\"')) AS v"));
        assertEquals("Failed to cast variant value [1,2] to REAL",
            refusalOf("SELECT ZEROIFNULL(PARSE_JSON('[1,2]')) AS v"));
    }

    @Test
    public void aVariantBooleanConvertsToANumber() {
        // The VARIANT carries its member through the numeric conversion; the SQL BOOLEAN has no such
        // cast at all, which is why this is a VARIANT rule and not a boolean one.
        assertEquals("1.0", cellOf("SELECT ZEROIFNULL(PARSE_JSON('true')) AS v"));
        assertEquals("1.0", cellOf("SELECT PARSE_JSON('true')::FLOAT AS v"));
        assertEquals("1", cellOf("SELECT PARSE_JSON('true')::NUMBER(3,0) AS v"));
        assertEquals("0.0", cellOf("SELECT ZEROIFNULL(PARSE_JSON('false')) AS v"));
    }

    @Test
    public void theConvertedResultCarriesItsFamilyIntoArithmetic() {
        assertEquals("1.0", cellOf("SELECT ZEROIFNULL(PARSE_JSON('null')) + 1 AS v"));
        assertEquals("0.5", cellOf("SELECT (ZEROIFNULL(PARSE_JSON('null')) + 1) / 2 AS v"));
    }

    @Test
    public void nullIfZeroHandsItsArgumentBackUnconverted() {
        assertEquals("7", cellOf("SELECT NULLIFZERO(PARSE_JSON('7')) AS v"));
        assertEquals("VARIANT[LOB]", cellOf("SELECT SYSTEM$TYPEOF(NULLIFZERO(v)) AS v FROM zc WHERE i = 7"));
        assertEquals("NUMBER(10,2)[SB2]", cellOf("SELECT SYSTEM$TYPEOF(NULLIFZERO(n)) AS v FROM zc WHERE i = 7"));
        assertEquals("BOOLEAN[SB1]", cellOf("SELECT SYSTEM$TYPEOF(NULLIFZERO(TRUE)) AS v"));
        assertNull(engine.executeQuery("SELECT NULLIFZERO(PARSE_JSON('0.0')) AS v")
            .getRows().get(0).getValue(0));
    }

    @Test
    public void theSubstitutingCONDITIONALSareNotTheSameCall() {
        // A JSON null is a VALUE, not SQL NULL, so nothing is substituted for it here — every one of
        // these answers the JSON null itself. ZEROIFNULL only reaches its zero because the conversion
        // to REAL turns that value into SQL NULL first.
        assertEquals("null", cellOf("SELECT NVL(PARSE_JSON('null'), 0) AS v"));
        assertEquals("null", cellOf("SELECT IFNULL(PARSE_JSON('null'), 0) AS v"));
        assertEquals("null", cellOf("SELECT COALESCE(PARSE_JSON('null'), 0) AS v"));
        assertEquals("null", cellOf("SELECT IFF(TRUE, PARSE_JSON('null'), 0) AS v"));
    }
    @Test
    public void aDeclaredBooleanRefusesAtCompileTimeWithThePosition() {
        engine.execute("CREATE OR REPLACE TABLE zbf (b BOOLEAN)");
        engine.execute("INSERT INTO zbf VALUES (TRUE)");
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'ZEROIFNULL': (BOOLEAN)",
            refusalOf("SELECT ZEROIFNULL(b) FROM zbf"));
        // The position is the CALL's own, not the statement's.
        assertEquals("SQL compilation error: error line 1 at position 21\n"
            + "Invalid argument types for function 'ZEROIFNULL': (BOOLEAN)",
            refusalOf("SELECT SYSTEM$TYPEOF(ZEROIFNULL(b)) FROM zbf"));
    }

    @Test
    public void everyTemporalFlavourRefusesWithItsOwnParameters() {
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'ZEROIFNULL': (DATE)",
            refusalOf("SELECT ZEROIFNULL(TO_DATE('2024-01-01'))"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'ZEROIFNULL': (TIME(9))",
            refusalOf("SELECT ZEROIFNULL(TO_TIME('12:00:00'))"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'ZEROIFNULL': (TIMESTAMP_NTZ(9))",
            refusalOf("SELECT ZEROIFNULL(CURRENT_TIMESTAMP::TIMESTAMP_NTZ)"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'ZEROIFNULL': (TIMESTAMP_LTZ(9))",
            refusalOf("SELECT ZEROIFNULL(CURRENT_TIMESTAMP::TIMESTAMP_LTZ)"));
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'ZEROIFNULL': (TIMESTAMP_TZ(9))",
            refusalOf("SELECT ZEROIFNULL(CURRENT_TIMESTAMP::TIMESTAMP_TZ)"));
    }

    @Test
    public void aDerivedBinaryIsNamedAtTheTypesNominalMaximum() {
        assertEquals("SQL compilation error: error line 1 at position 7\n"
            + "Invalid argument types for function 'ZEROIFNULL': (BINARY(67108864))",
            refusalOf("SELECT ZEROIFNULL(TO_BINARY('AB', 'HEX'))"));
    }

    @Test
    public void aVariantHoldingTheSameBooleanStillConverts() {
        assertEquals("1.0", cellOf("SELECT ZEROIFNULL(PARSE_JSON('true')) AS v"));
    }

    @Test
    public void aBooleanCastsToAnExactNumberButNotToAnApproximateOne() {
        assertEquals("1", cellOf("SELECT TRUE::NUMBER AS v"));
        assertEquals("0", cellOf("SELECT FALSE::NUMBER AS v"));
        assertEquals("SQL compilation error:\ninvalid type [CAST(TRUE AS FLOAT)]"
            + " for parameter 'TO_DOUBLE'",
            refusalOf("SELECT TRUE::FLOAT"));
    }

}

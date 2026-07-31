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

package dev.frostlake.expressions;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A computed projection reports its SEMI-STRUCTURED static type, so a direct CTAS declares the column the
 * way Snowflake does. Frostlake used to stamp every computed column VARCHAR, so
 * {@code CREATE TABLE t AS SELECT OBJECT_CONSTRUCT('k','v') AS c FROM s} declared {@code c} VARCHAR where
 * live ({@code DESC TABLE} on a real account) declares it OBJECT — while the SAME projection
 * one level down, through a derived table, was already correct.
 *
 * <p>The family restriction is a measured boundary, not caution. Every other family is recovered from the
 * VALUES by the CTAS reader — widest scale seen, BOOLEAN, temporals — so promoting their static types
 * instead REPLACES a correct value-derived type with a coarser declared one: {@code SELECT 1.5 AS c}
 * would become NUMBER(38,0) and store 2 where live declares NUMBER(2,1) and stores 1.5. A semi-structured
 * cell is not a Number/Boolean/temporal, falls through that recovery every time, and so is the one family
 * that needs the static channel. {@link #aDecimalLiteralStillKeepsItsScale()} is the guard on that.
 */
public class ProjectedColumnTypeTest extends BaseDatabaseTest {

    /** The declared type of a one-column table, read back from the catalog. */
    private String declaredType(final String table) {
        return String.valueOf(engine.executeQuery(
            "SELECT data_type FROM test_db.information_schema.columns WHERE table_name = '"
                + table.toUpperCase() + "'").getRows().get(0).getValue(0));
    }

    private void ctas(final String table, final String projection) {
        engine.execute("CREATE TABLE " + table + " AS SELECT " + projection + " AS c FROM proj_src");
    }

    private void seed() {
        engine.execute("CREATE TABLE proj_src (id INTEGER)");
        engine.execute("INSERT INTO proj_src VALUES (1)");
    }

    @Test
    public void anObjectConstructProjectionDeclaresObject() {
        seed();
        ctas("p_obj", "OBJECT_CONSTRUCT('k','v')");
        assertEquals("OBJECT", declaredType("p_obj"));
    }

    @Test
    public void anArrayConstructProjectionDeclaresArray() {
        seed();
        ctas("p_arr", "ARRAY_CONSTRUCT(1, 2)");
        assertEquals("ARRAY", declaredType("p_arr"));
    }

    @Test
    public void aParseJsonProjectionDeclaresVariant() {
        seed();
        ctas("p_var", "PARSE_JSON('{\"a\":1}')");
        assertEquals("VARIANT", declaredType("p_var"));
    }

    /** The registry is authoritative about which producer yields which type — OBJECT_KEYS gives an ARRAY. */
    @Test
    public void theReportedTypeComesFromTheProducerNotTheName() {
        seed();
        ctas("p_keys", "OBJECT_KEYS(OBJECT_CONSTRUCT('k','v'))");
        assertEquals("ARRAY", declaredType("p_keys"));
    }

    /**
     * THE GUARD. Promoting every static type — rather than the semi-structured family alone — reports a
     * bare NUMBER where the CTAS reader would have kept the scale from the value, silently storing 2.
     */
    @Test
    public void aDecimalLiteralStillKeepsItsScale() {
        seed();
        ctas("p_dec", "1.5");
        assertEquals("1.5", String.valueOf(
            engine.executeQuery("SELECT c FROM p_dec").getRows().get(0).getValue(0)));
    }

    /** Arithmetic, comparison and string projections keep the value-based recovery they already had. */
    @Test
    public void theOtherFamiliesAreUnchanged() {
        seed();
        ctas("p_num", "1 + 1");
        ctas("p_bool", "id > 0");
        ctas("p_txt", "'x' || 'y'");
        assertEquals("NUMBER", declaredType("p_num"));
        assertEquals("BOOLEAN", declaredType("p_bool"));
        assertEquals("TEXT", declaredType("p_txt"));
    }

    /** The derived-table form was already correct through #149's static channel, and stays so. */
    @Test
    public void theDerivedFormStillAgreesWithTheDirectOne() {
        seed();
        engine.execute("CREATE TABLE p_direct AS SELECT OBJECT_CONSTRUCT('k','v') AS c FROM proj_src");
        engine.execute("CREATE TABLE p_derived AS SELECT c FROM"
            + " (SELECT OBJECT_CONSTRUCT('k','v') AS c FROM proj_src)");
        assertEquals(declaredType("p_direct"), declaredType("p_derived"));
        assertEquals("OBJECT", declaredType("p_direct"));
    }

    /** Live declares the column OBJECT even when the select matches no rows — the type is static. */
    @Test
    public void anEmptySelectStillDeclaresTheSemiStructuredType() {
        seed();
        engine.execute("CREATE TABLE p_empty AS SELECT OBJECT_CONSTRUCT('k','v') AS c"
            + " FROM proj_src WHERE id < 0");
        assertEquals(0, engine.executeQuery("SELECT c FROM p_empty").getRows().size());
        assertEquals("OBJECT", declaredType("p_empty"));
    }

    /** The scale guard holds on the FROM-less path too — live stores 1.5 in a NUMBER(2,1). */
    @Test
    public void aFromLessDecimalLiteralAlsoKeepsItsScale() {
        engine.execute("CREATE TABLE n_dec AS SELECT 1.5 AS c");
        assertEquals("1.5", String.valueOf(
            engine.executeQuery("SELECT c FROM n_dec").getRows().get(0).getValue(0)));
    }

    /**
     * A FROM-less select declares the same semi-structured types the FROM-bearing form declares —
     * live measured on a real account: OBJECT / ARRAY / VARIANT, and the same through a
     * view over the FROM-less select.
     */
    @Test
    public void aFromLessSelectDeclaresTheSemiStructuredTypesToo() {
        engine.execute("CREATE TABLE n_obj AS SELECT OBJECT_CONSTRUCT('k','v') AS c");
        engine.execute("CREATE TABLE n_arr AS SELECT ARRAY_CONSTRUCT(1, 2) AS c");
        engine.execute("CREATE VIEW n_view AS SELECT OBJECT_CONSTRUCT('k','v') AS c");
        assertEquals("OBJECT", declaredType("n_obj"));
        assertEquals("ARRAY", declaredType("n_arr"));
        assertEquals("OBJECT", declaredType("n_view"));
    }
}

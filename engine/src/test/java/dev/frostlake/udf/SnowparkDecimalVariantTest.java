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
 * A Java handler's {@code Variant} made from a {@code BigDecimal} answers by the decimal's scale: at scale 0 an
 * INTEGER, at any other a DECIMAL, a whole one included and printed without its scale (live-verified).
 */
public class SnowparkDecimalVariantTest extends MeasuredCellsTestSupport {

    @Test
    public void aDecimalAnswersByItsScale() {
        engine.execute("""
            CREATE OR REPLACE FUNCTION jv(k INT) RETURNS VARIANT LANGUAGE JAVA
            PACKAGES = ('com.snowflake:snowpark:latest') HANDLER = 'H.f' AS $$
            import com.snowflake.snowpark_java.types.Variant;
            import java.math.BigDecimal;
            class H {
              public static Variant f(int k) {
                switch (k) {
                  case 1: return new Variant(new BigDecimal("1E+3"));
                  case 2: return new Variant(new BigDecimal("1.5E+3"));
                  case 3: return new Variant(new BigDecimal("-1E+3"));
                  case 4: return new Variant(new BigDecimal("1E-3"));
                  case 5: return new Variant(new BigDecimal("1000"));
                  case 6: return new Variant(new BigDecimal("1000.0"));
                  case 7: return new Variant(new BigDecimal("1E+20"));
                  default: return new Variant(BigDecimal.valueOf(25, -1));
                }
              }
            }
            $$""");
        assertAnswer("1|DECIMAL|1000 ;; 2|DECIMAL|1500 ;; 3|DECIMAL|-1000 ;; 4|DECIMAL|0.001 ;; 5|INTEGER|1000 ;; "
                + "6|DECIMAL|1000 ;; 7|DECIMAL|100000000000000000000 ;; 9|DECIMAL|250",
            "SELECT column1, TYPEOF(jv(column1)), jv(column1)::VARCHAR FROM VALUES (1), (2), (3), (4), (5), (6), "
                + "(7), (9) ORDER BY 1");
    }
}

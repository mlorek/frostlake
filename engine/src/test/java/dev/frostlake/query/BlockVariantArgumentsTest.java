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
package dev.frostlake.query;

import org.junit.jupiter.api.Test;

/**
 * A VARIANT read inside a block is a VARIANT to the call around it: SQL binds a variable by its declaration and
 * types a nested call by what its function answers, so GET(:v, 'k'), a path into a VARIANT and a variable
 * declared VARIANT reach a type predicate or a cast of a VARIANT as one, whatever the value is - a text, a
 * number, a NULL - and a variable declared VARCHAR or NUMBER is refused there as its own type. Every answer is
 * live-verified.
 */
public class BlockVariantArgumentsTest extends MeasuredCellsTestSupport {

    @Test
    public void aVariantKeepsItsTypeInsideABlock() {
        try {
            assertAnswer(
                "false",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_NULL_VALUE(GET(:o, 'a')); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_NULL_VALUE(GET(:o, 'z')); END $$");
            assertAnswer(
                "null",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_NULL_VALUE(GET(:o, 'missing')); END $$");
            assertAnswer(
                "false",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_NULL_VALUE(GET(:o, k)); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_VARCHAR(GET(:o, 'a')); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_INTEGER(GET(:o, 'n')); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_VARCHAR(:o:a); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_VARCHAR(o:a); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_INTEGER(o:n); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_BOOLEAN(o:b); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_ARRAY(o:arr); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_DECIMAL(o:d); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_OBJECT(o:o); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_OBJECT(o); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_DOUBLE(GET(o, 'd')); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_INTEGER(GET_PATH(o, 'o.k')); END $$");
            assertAnswer(
                "5",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_INTEGER(GET(:o, 'n')); END $$");
            assertAnswer(
                "5",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_INTEGER(o:n); END $$");
            assertAnswer(
                "null",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_INTEGER(GET(:o, 'a')); END $$");
            assertAnswer(
                "null",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_INTEGER(GET(:o, 'missing')); END $$");
            assertAnswer(
                "some text",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_VARCHAR(GET(:o, 'a')); END $$");
            assertAnswer(
                "null",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_VARCHAR(o:n); END $$");
            assertAnswer(
                "1.5",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_DECIMAL(o:d, 10, 1); END $$");
            assertAnswer(
                "5.0",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_DOUBLE(o:n); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_BOOLEAN(o:b); END $$");
            assertAnswer(
                "[1,2]",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_ARRAY(o:arr); END $$");
            assertAnswer(
                "{\"k\":1}",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN AS_OBJECT(o:o); END $$");
            assertAnswer(
                "null",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN STRIP_NULL_VALUE(o:z); END $$");
            assertAnswer(
                "some text",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN STRIP_NULL_VALUE(o:a); END $$");
            assertAnswer(
                "some text",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN STRIP_NULL_VALUE(GET(:o, 'a')); END $$");
            assertAnswer(
                "VARCHAR",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN TYPEOF(GET(:o, 'a')); END $$");
            assertAnswer(
                "INTEGER",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN TYPEOF(o:n); END $$");
            assertAnswer(
                "2",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN ARRAY_SIZE(o:arr); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN ARRAY_CONTAINS(GET(:o, 'a'), ARRAY_CONSTRUCT('some text')); END $$");
            assertAnswer(
                "false",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN ARRAY_CONTAINS(o:n, o:arr); END $$");
            assertAnswer(
                "false",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN IS_ARRAY(GET(:o, 'a')); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET v VARIANT := GET(:o, 'a'); RETURN IS_VARCHAR(v); END $$");
            assertAnswer(
                "5",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET v VARIANT := GET(:o, 'n'); RETURN AS_INTEGER(v); END $$");
            assertAnswer(
                "some text",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET v VARIANT := GET(:o, 'a'); RETURN AS_VARCHAR(v); END $$");
            assertAnswer(
                "null",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET v VARIANT := NULL; RETURN AS_INTEGER(v); END $$");
            assertAnswer(
                "null",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET v VARIANT := NULL; RETURN IS_INTEGER(v); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET v VARIANT := 5; RETURN IS_INTEGER(v); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET v VARIANT := 'x'; RETURN IS_VARCHAR(v); END $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 161 |  variable 'V' cannot have its type "
                    + "inferred from initializer",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET v := GET(:o, 'a'); RETURN IS_VARCHAR(v); END $$");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 161 |  variable 'V' cannot have its type "
                    + "inferred from initializer",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET v := GET(:o, 'n'); RETURN AS_INTEGER(v); END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 190 : SQL "
                    + "compilation error: error line 1 at position 0 | Invalid argument types for function "
                    + "'IS_VARCHAR': (VARCHAR(1))",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET s VARCHAR := 'x'; RETURN IS_VARCHAR(s); END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 190 : SQL "
                    + "compilation error: error line 1 at position 0 | Invalid argument types for function "
                    + "'IS_NULL_VALUE': (VARCHAR(1))",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET s VARCHAR := 'x'; RETURN IS_NULL_VALUE(s); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET n NUMBER := 5; RETURN IS_INTEGER(n); END $$");
            assertAnswer(
                "REFUSED Uncaught exception of type 'EXPRESSION_ERROR' on line 1 at position 196 : SQL "
                    + "compilation error: error line 1 at position 0 | Invalid argument types for function "
                    + "'IS_DATE': (DATE)",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET d DATE := '2024-01-01'; RETURN IS_DATE(d); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET s VARCHAR := 'x'; RETURN IS_VARCHAR(s::VARIANT); END $$");
            assertAnswer(
                "x",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET s VARCHAR := 'x'; RETURN AS_VARCHAR(TO_VARIANT(s)); END $$");
            assertAnswer(
                "value",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN IF (IS_NULL_VALUE(GET(:o, k))) THEN RETURN 'null'; ELSE RETURN 'value'; "
                    + "END IF; END $$");
            assertAnswer(
                "null",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN IF (IS_NULL_VALUE(GET(:o, 'z'))) THEN RETURN 'null'; ELSE RETURN 'value'; "
                    + "END IF; END $$");
            assertAnswer(
                "int",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET x VARCHAR := IFF(IS_INTEGER(o:n), 'int', 'other'); RETURN x; END $$");
            assertAnswer(
                "6",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN LET x := AS_INTEGER(o:n) + 1; RETURN x; END $$");
            assertAnswer(
                "SOME TEXT",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN UPPER(GET(:o, 'a')); END $$");
            assertAnswer(
                "9",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN LENGTH(o:a); END $$");
            assertAnswer(
                "true",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN GET(:o, 'a') = 'some text'; END $$");
            assertAnswer(
                "{\"k\":\"some text\"}",
                "EXECUTE IMMEDIATE $$ DECLARE o VARIANT DEFAULT PARSE_JSON('{\"a\": \"some text\", \"n\": 5, "
                    + "\"z\": null, \"d\": 1.5, \"b\": true, \"arr\": [1, 2], \"o\": {\"k\": 1}}'); k VARCHAR "
                    + "DEFAULT 'a';  BEGIN RETURN OBJECT_INSERT(OBJECT_CONSTRUCT(), 'k', GET(:o, 'a'), TRUE); END $$");
            assertAnswer(
                "Function P_TAG successfully created.",
                "CREATE OR REPLACE PROCEDURE p_tag(p_tags VARIANT) RETURNS VARCHAR LANGUAGE SQL AS $$ DECLARE "
                    + "v_key VARCHAR DEFAULT 'a'; BEGIN IF (IS_NULL_VALUE(GET(:p_tags, v_key))) THEN RETURN 'null'; "
                    + "ELSE RETURN 'value'; END IF; END $$");
            assertAnswer(
                "value",
                "CALL p_tag(OBJECT_CONSTRUCT('a', 'some text value'))");
            assertAnswer(
                "null",
                "CALL p_tag(PARSE_JSON('{\"a\": null}'))");
            assertAnswer(
                "value",
                "CALL p_tag(PARSE_JSON('{\"b\": 1}'))");
            assertAnswer(
                "P_TAG successfully dropped.",
                "DROP PROCEDURE p_tag(VARIANT)");
        } finally {
            engine.execute(
                "DROP PROCEDURE IF EXISTS p_tag(VARIANT)");
        }
    }
}

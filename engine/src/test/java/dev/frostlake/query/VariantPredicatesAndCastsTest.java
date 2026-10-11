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
 * What the type predicates and the casts of a VARIANT take, and what they answer. The IS_ predicates, and the
 * casts that answer NULL for another kind, take what converts to a VARIANT unasked - a VARIANT, an OBJECT, an
 * ARRAY, a number, a BOOLEAN, NULL - and refuse a text, a DATE, a TIME, a timestamp and a BINARY by the argument
 * types, at the call, over a literal, a column or an expression and over no rows as well. AS_INTEGER, AS_DECIMAL,
 * AS_NUMBER and the three AS_TIMESTAMP casts take a VARIANT and nothing else: any other declared type, the bare
 * word NULL included, is an invalid type for their parameter. Each timestamp predicate and cast knows its own
 * flavour only; AS_VARCHAR answers a text and nothing else; a DOUBLE in a VARIANT is no INTEGER and no DECIMAL.
 * ARRAY_FLATTEN takes an array of arrays: a NULL element makes the answer NULL, any other element that is no
 * array is refused. Every answer is live-verified.
 */
public class VariantPredicatesAndCastsTest extends MeasuredCellsTestSupport {

    @Test
    public void whatAPredicateTakes() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_ARRAY': (VARCHAR(1))",
            "SELECT IS_ARRAY('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_ARRAY': (DATE)",
            "SELECT IS_ARRAY('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_ARRAY': (TIME(9))",
            "SELECT IS_ARRAY('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_ARRAY': (TIMESTAMP_NTZ(9))",
            "SELECT IS_ARRAY('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_ARRAY(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_ARRAY': (BINARY(67108864))",
            "SELECT IS_ARRAY(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_ARRAY(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "true",
            "SELECT IS_ARRAY([1])");
        assertAnswer(
            "false",
            "SELECT IS_ARRAY(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_ARRAY(1)");
        assertAnswer(
            "null",
            "SELECT IS_ARRAY(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_ARRAY(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_ARRAY(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BINARY': (VARCHAR(1))",
            "SELECT IS_BINARY('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BINARY': (DATE)",
            "SELECT IS_BINARY('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BINARY': (TIME(9))",
            "SELECT IS_BINARY('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BINARY': (TIMESTAMP_NTZ(9))",
            "SELECT IS_BINARY('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_BINARY(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BINARY': (BINARY(67108864))",
            "SELECT IS_BINARY(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_BINARY(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_BINARY([1])");
        assertAnswer(
            "false",
            "SELECT IS_BINARY(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_BINARY(1)");
        assertAnswer(
            "null",
            "SELECT IS_BINARY(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_BINARY(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_BINARY(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BOOLEAN': (VARCHAR(1))",
            "SELECT IS_BOOLEAN('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BOOLEAN': (DATE)",
            "SELECT IS_BOOLEAN('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BOOLEAN': (TIME(9))",
            "SELECT IS_BOOLEAN('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BOOLEAN': (TIMESTAMP_NTZ(9))",
            "SELECT IS_BOOLEAN('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_BOOLEAN(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BOOLEAN': (BINARY(67108864))",
            "SELECT IS_BOOLEAN(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_BOOLEAN(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_BOOLEAN([1])");
        assertAnswer(
            "true",
            "SELECT IS_BOOLEAN(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_BOOLEAN(1)");
        assertAnswer(
            "null",
            "SELECT IS_BOOLEAN(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_BOOLEAN(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_BOOLEAN(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_CHAR': (VARCHAR(1))",
            "SELECT IS_CHAR('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_CHAR': (DATE)",
            "SELECT IS_CHAR('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_CHAR': (TIME(9))",
            "SELECT IS_CHAR('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_CHAR': (TIMESTAMP_NTZ(9))",
            "SELECT IS_CHAR('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_CHAR(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_CHAR': (BINARY(67108864))",
            "SELECT IS_CHAR(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_CHAR(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_CHAR([1])");
        assertAnswer(
            "false",
            "SELECT IS_CHAR(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_CHAR(1)");
        assertAnswer(
            "null",
            "SELECT IS_CHAR(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_CHAR(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_CHAR(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_VARCHAR': (VARCHAR(1))",
            "SELECT IS_VARCHAR('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_VARCHAR': (DATE)",
            "SELECT IS_VARCHAR('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_VARCHAR': (TIME(9))",
            "SELECT IS_VARCHAR('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_VARCHAR': (TIMESTAMP_NTZ(9))",
            "SELECT IS_VARCHAR('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_VARCHAR(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_VARCHAR': (BINARY(67108864))",
            "SELECT IS_VARCHAR(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_VARCHAR(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_VARCHAR([1])");
        assertAnswer(
            "false",
            "SELECT IS_VARCHAR(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_VARCHAR(1)");
        assertAnswer(
            "null",
            "SELECT IS_VARCHAR(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_VARCHAR(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_VARCHAR(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE': (VARCHAR(1))",
            "SELECT IS_DATE('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE': (DATE)",
            "SELECT IS_DATE('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE': (TIME(9))",
            "SELECT IS_DATE('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE': (TIMESTAMP_NTZ(9))",
            "SELECT IS_DATE('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_DATE(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE': (BINARY(67108864))",
            "SELECT IS_DATE(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_DATE(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_DATE([1])");
        assertAnswer(
            "false",
            "SELECT IS_DATE(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_DATE(1)");
        assertAnswer(
            "null",
            "SELECT IS_DATE(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_DATE(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_DATE(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE_VALUE': (VARCHAR(1))",
            "SELECT IS_DATE_VALUE('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE_VALUE': (DATE)",
            "SELECT IS_DATE_VALUE('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE_VALUE': (TIME(9))",
            "SELECT IS_DATE_VALUE('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE_VALUE': (TIMESTAMP_NTZ(9))",
            "SELECT IS_DATE_VALUE('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_DATE_VALUE(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DATE_VALUE': (BINARY(67108864))",
            "SELECT IS_DATE_VALUE(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_DATE_VALUE(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_DATE_VALUE([1])");
        assertAnswer(
            "false",
            "SELECT IS_DATE_VALUE(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_DATE_VALUE(1)");
        assertAnswer(
            "null",
            "SELECT IS_DATE_VALUE(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_DATE_VALUE(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_DATE_VALUE(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DECIMAL': (VARCHAR(1))",
            "SELECT IS_DECIMAL('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DECIMAL': (DATE)",
            "SELECT IS_DECIMAL('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DECIMAL': (TIME(9))",
            "SELECT IS_DECIMAL('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DECIMAL': (TIMESTAMP_NTZ(9))",
            "SELECT IS_DECIMAL('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_DECIMAL(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DECIMAL': (BINARY(67108864))",
            "SELECT IS_DECIMAL(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_DECIMAL(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_DECIMAL([1])");
        assertAnswer(
            "false",
            "SELECT IS_DECIMAL(TRUE)");
        assertAnswer(
            "true",
            "SELECT IS_DECIMAL(1)");
        assertAnswer(
            "null",
            "SELECT IS_DECIMAL(NULL)");
        assertAnswer(
            "true",
            "SELECT IS_DECIMAL(1.5)");
        assertAnswer(
            "true",
            "SELECT IS_DECIMAL(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DOUBLE': (VARCHAR(1))",
            "SELECT IS_DOUBLE('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DOUBLE': (DATE)",
            "SELECT IS_DOUBLE('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DOUBLE': (TIME(9))",
            "SELECT IS_DOUBLE('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DOUBLE': (TIMESTAMP_NTZ(9))",
            "SELECT IS_DOUBLE('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "true",
            "SELECT IS_DOUBLE(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_DOUBLE': (BINARY(67108864))",
            "SELECT IS_DOUBLE(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_DOUBLE(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_DOUBLE([1])");
        assertAnswer(
            "false",
            "SELECT IS_DOUBLE(TRUE)");
        assertAnswer(
            "true",
            "SELECT IS_DOUBLE(1)");
        assertAnswer(
            "null",
            "SELECT IS_DOUBLE(NULL)");
        assertAnswer(
            "true",
            "SELECT IS_DOUBLE(1.5)");
        assertAnswer(
            "true",
            "SELECT IS_DOUBLE(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_REAL': (VARCHAR(1))",
            "SELECT IS_REAL('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_REAL': (DATE)",
            "SELECT IS_REAL('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_REAL': (TIME(9))",
            "SELECT IS_REAL('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_REAL': (TIMESTAMP_NTZ(9))",
            "SELECT IS_REAL('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "true",
            "SELECT IS_REAL(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_REAL': (BINARY(67108864))",
            "SELECT IS_REAL(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_REAL(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_REAL([1])");
        assertAnswer(
            "false",
            "SELECT IS_REAL(TRUE)");
        assertAnswer(
            "true",
            "SELECT IS_REAL(1)");
        assertAnswer(
            "null",
            "SELECT IS_REAL(NULL)");
        assertAnswer(
            "true",
            "SELECT IS_REAL(1.5)");
        assertAnswer(
            "true",
            "SELECT IS_REAL(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_INTEGER': (VARCHAR(1))",
            "SELECT IS_INTEGER('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_INTEGER': (DATE)",
            "SELECT IS_INTEGER('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_INTEGER': (TIME(9))",
            "SELECT IS_INTEGER('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_INTEGER': (TIMESTAMP_NTZ(9))",
            "SELECT IS_INTEGER('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_INTEGER(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_INTEGER': (BINARY(67108864))",
            "SELECT IS_INTEGER(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_INTEGER(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_INTEGER([1])");
        assertAnswer(
            "false",
            "SELECT IS_INTEGER(TRUE)");
        assertAnswer(
            "true",
            "SELECT IS_INTEGER(1)");
        assertAnswer(
            "null",
            "SELECT IS_INTEGER(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_INTEGER(1.5)");
        assertAnswer(
            "true",
            "SELECT IS_INTEGER(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (VARCHAR(1))",
            "SELECT IS_NULL_VALUE('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (DATE)",
            "SELECT IS_NULL_VALUE('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (TIME(9))",
            "SELECT IS_NULL_VALUE('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (TIMESTAMP_NTZ(9))",
            "SELECT IS_NULL_VALUE('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_NULL_VALUE(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_NULL_VALUE': (BINARY(67108864))",
            "SELECT IS_NULL_VALUE(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_NULL_VALUE(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_NULL_VALUE([1])");
        assertAnswer(
            "false",
            "SELECT IS_NULL_VALUE(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_NULL_VALUE(1)");
        assertAnswer(
            "null",
            "SELECT IS_NULL_VALUE(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_NULL_VALUE(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_NULL_VALUE(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_OBJECT': (VARCHAR(1))",
            "SELECT IS_OBJECT('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_OBJECT': (DATE)",
            "SELECT IS_OBJECT('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_OBJECT': (TIME(9))",
            "SELECT IS_OBJECT('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_OBJECT': (TIMESTAMP_NTZ(9))",
            "SELECT IS_OBJECT('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_OBJECT(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_OBJECT': (BINARY(67108864))",
            "SELECT IS_OBJECT(TO_BINARY('00'))");
        assertAnswer(
            "true",
            "SELECT IS_OBJECT(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_OBJECT([1])");
        assertAnswer(
            "false",
            "SELECT IS_OBJECT(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_OBJECT(1)");
        assertAnswer(
            "null",
            "SELECT IS_OBJECT(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_OBJECT(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_OBJECT(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIME': (VARCHAR(1))",
            "SELECT IS_TIME('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIME': (DATE)",
            "SELECT IS_TIME('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIME': (TIME(9))",
            "SELECT IS_TIME('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIME': (TIMESTAMP_NTZ(9))",
            "SELECT IS_TIME('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_TIME(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIME': (BINARY(67108864))",
            "SELECT IS_TIME(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_TIME(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_TIME([1])");
        assertAnswer(
            "false",
            "SELECT IS_TIME(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_TIME(1)");
        assertAnswer(
            "null",
            "SELECT IS_TIME(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_TIME(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_TIME(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_NTZ': (VARCHAR(1))",
            "SELECT IS_TIMESTAMP_NTZ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_NTZ': (DATE)",
            "SELECT IS_TIMESTAMP_NTZ('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_NTZ': (TIME(9))",
            "SELECT IS_TIMESTAMP_NTZ('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_NTZ': (TIMESTAMP_NTZ(9))",
            "SELECT IS_TIMESTAMP_NTZ('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_NTZ(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_NTZ': (BINARY(67108864))",
            "SELECT IS_TIMESTAMP_NTZ(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_NTZ(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_NTZ([1])");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_NTZ(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_NTZ(1)");
        assertAnswer(
            "null",
            "SELECT IS_TIMESTAMP_NTZ(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_NTZ(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_NTZ(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_LTZ': (VARCHAR(1))",
            "SELECT IS_TIMESTAMP_LTZ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_LTZ': (DATE)",
            "SELECT IS_TIMESTAMP_LTZ('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_LTZ': (TIME(9))",
            "SELECT IS_TIMESTAMP_LTZ('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_LTZ': (TIMESTAMP_NTZ(9))",
            "SELECT IS_TIMESTAMP_LTZ('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_LTZ(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_LTZ': (BINARY(67108864))",
            "SELECT IS_TIMESTAMP_LTZ(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_LTZ(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_LTZ([1])");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_LTZ(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_LTZ(1)");
        assertAnswer(
            "null",
            "SELECT IS_TIMESTAMP_LTZ(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_LTZ(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_LTZ(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_TZ': (VARCHAR(1))",
            "SELECT IS_TIMESTAMP_TZ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_TZ': (DATE)",
            "SELECT IS_TIMESTAMP_TZ('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_TZ': (TIME(9))",
            "SELECT IS_TIMESTAMP_TZ('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_TZ': (TIMESTAMP_NTZ(9))",
            "SELECT IS_TIMESTAMP_TZ('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_TZ(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_TZ': (BINARY(67108864))",
            "SELECT IS_TIMESTAMP_TZ(TO_BINARY('00'))");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_TZ(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_TZ([1])");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_TZ(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_TZ(1)");
        assertAnswer(
            "null",
            "SELECT IS_TIMESTAMP_TZ(NULL)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_TZ(1.5)");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_TZ(TO_VARIANT(1))");
    }

    @Test
    public void whatACastTakes() {
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_ARRAY': (VARCHAR(1))",
            "SELECT AS_ARRAY('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_ARRAY': (DATE)",
            "SELECT AS_ARRAY('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_ARRAY': (TIME(9))",
            "SELECT AS_ARRAY('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_ARRAY': (TIMESTAMP_NTZ(9))",
            "SELECT AS_ARRAY('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT AS_ARRAY(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_ARRAY': (BINARY(67108864))",
            "SELECT AS_ARRAY(TO_BINARY('00'))");
        assertAnswer(
            "null",
            "SELECT AS_ARRAY(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "[1]",
            "SELECT AS_ARRAY([1])");
        assertAnswer(
            "null",
            "SELECT AS_ARRAY(TRUE)");
        assertAnswer(
            "null",
            "SELECT AS_ARRAY(1)");
        assertAnswer(
            "null",
            "SELECT AS_ARRAY(NULL)");
        assertAnswer(
            "null",
            "SELECT AS_ARRAY(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_ARRAY(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BINARY': (VARCHAR(1))",
            "SELECT AS_BINARY('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BINARY': (DATE)",
            "SELECT AS_BINARY('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BINARY': (TIME(9))",
            "SELECT AS_BINARY('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BINARY': (TIMESTAMP_NTZ(9))",
            "SELECT AS_BINARY('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT AS_BINARY(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BINARY': (BINARY(67108864))",
            "SELECT AS_BINARY(TO_BINARY('00'))");
        assertAnswer(
            "null",
            "SELECT AS_BINARY(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "null",
            "SELECT AS_BINARY([1])");
        assertAnswer(
            "null",
            "SELECT AS_BINARY(TRUE)");
        assertAnswer(
            "null",
            "SELECT AS_BINARY(1)");
        assertAnswer(
            "null",
            "SELECT AS_BINARY(NULL)");
        assertAnswer(
            "null",
            "SELECT AS_BINARY(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_BINARY(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BOOLEAN': (VARCHAR(1))",
            "SELECT AS_BOOLEAN('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BOOLEAN': (DATE)",
            "SELECT AS_BOOLEAN('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BOOLEAN': (TIME(9))",
            "SELECT AS_BOOLEAN('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BOOLEAN': (TIMESTAMP_NTZ(9))",
            "SELECT AS_BOOLEAN('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT AS_BOOLEAN(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_BOOLEAN': (BINARY(67108864))",
            "SELECT AS_BOOLEAN(TO_BINARY('00'))");
        assertAnswer(
            "null",
            "SELECT AS_BOOLEAN(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "null",
            "SELECT AS_BOOLEAN([1])");
        assertAnswer(
            "true",
            "SELECT AS_BOOLEAN(TRUE)");
        assertAnswer(
            "null",
            "SELECT AS_BOOLEAN(1)");
        assertAnswer(
            "null",
            "SELECT AS_BOOLEAN(NULL)");
        assertAnswer(
            "null",
            "SELECT AS_BOOLEAN(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_BOOLEAN(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_CHAR': (VARCHAR(1))",
            "SELECT AS_CHAR('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_CHAR': (DATE)",
            "SELECT AS_CHAR('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_CHAR': (TIME(9))",
            "SELECT AS_CHAR('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_CHAR': (TIMESTAMP_NTZ(9))",
            "SELECT AS_CHAR('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT AS_CHAR(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_CHAR': (BINARY(67108864))",
            "SELECT AS_CHAR(TO_BINARY('00'))");
        assertAnswer(
            "null",
            "SELECT AS_CHAR(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "null",
            "SELECT AS_CHAR([1])");
        assertAnswer(
            "null",
            "SELECT AS_CHAR(TRUE)");
        assertAnswer(
            "null",
            "SELECT AS_CHAR(1)");
        assertAnswer(
            "null",
            "SELECT AS_CHAR(NULL)");
        assertAnswer(
            "null",
            "SELECT AS_CHAR(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_CHAR(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_VARCHAR': (VARCHAR(1))",
            "SELECT AS_VARCHAR('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_VARCHAR': (DATE)",
            "SELECT AS_VARCHAR('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_VARCHAR': (TIME(9))",
            "SELECT AS_VARCHAR('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_VARCHAR': (TIMESTAMP_NTZ(9))",
            "SELECT AS_VARCHAR('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT AS_VARCHAR(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_VARCHAR': (BINARY(67108864))",
            "SELECT AS_VARCHAR(TO_BINARY('00'))");
        assertAnswer(
            "null",
            "SELECT AS_VARCHAR(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "null",
            "SELECT AS_VARCHAR([1])");
        assertAnswer(
            "null",
            "SELECT AS_VARCHAR(TRUE)");
        assertAnswer(
            "null",
            "SELECT AS_VARCHAR(1)");
        assertAnswer(
            "null",
            "SELECT AS_VARCHAR(NULL)");
        assertAnswer(
            "null",
            "SELECT AS_VARCHAR(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_VARCHAR(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DATE': (VARCHAR(1))",
            "SELECT AS_DATE('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DATE': (DATE)",
            "SELECT AS_DATE('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DATE': (TIME(9))",
            "SELECT AS_DATE('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DATE': (TIMESTAMP_NTZ(9))",
            "SELECT AS_DATE('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT AS_DATE(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DATE': (BINARY(67108864))",
            "SELECT AS_DATE(TO_BINARY('00'))");
        assertAnswer(
            "null",
            "SELECT AS_DATE(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "null",
            "SELECT AS_DATE([1])");
        assertAnswer(
            "null",
            "SELECT AS_DATE(TRUE)");
        assertAnswer(
            "null",
            "SELECT AS_DATE(1)");
        assertAnswer(
            "null",
            "SELECT AS_DATE(NULL)");
        assertAnswer(
            "null",
            "SELECT AS_DATE(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_DATE(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter "
                + "'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIME(9)] for parameter 'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIMESTAMP_NTZ(9)] for parameter "
                + "'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter 'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BINARY(67108864)] for parameter "
                + "'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL(TO_BINARY('00'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [OBJECT] for parameter 'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [ARRAY] for parameter 'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL([1])");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter "
                + "'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NULL] for parameter 'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter "
                + "'AS_DECIMAL(variantValue...)'",
            "SELECT AS_DECIMAL(1.5)");
        assertAnswer(
            "1",
            "SELECT AS_DECIMAL(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter 'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIME(9)] for parameter 'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIMESTAMP_NTZ(9)] for parameter "
                + "'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter 'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BINARY(67108864)] for parameter "
                + "'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER(TO_BINARY('00'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [OBJECT] for parameter 'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [ARRAY] for parameter 'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER([1])");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter "
                + "'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NULL] for parameter 'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter "
                + "'AS_NUMBER(variantValue...)'",
            "SELECT AS_NUMBER(1.5)");
        assertAnswer(
            "1",
            "SELECT AS_NUMBER(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DOUBLE': (VARCHAR(1))",
            "SELECT AS_DOUBLE('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DOUBLE': (DATE)",
            "SELECT AS_DOUBLE('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DOUBLE': (TIME(9))",
            "SELECT AS_DOUBLE('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DOUBLE': (TIMESTAMP_NTZ(9))",
            "SELECT AS_DOUBLE('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "1.5",
            "SELECT AS_DOUBLE(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_DOUBLE': (BINARY(67108864))",
            "SELECT AS_DOUBLE(TO_BINARY('00'))");
        assertAnswer(
            "null",
            "SELECT AS_DOUBLE(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "null",
            "SELECT AS_DOUBLE([1])");
        assertAnswer(
            "null",
            "SELECT AS_DOUBLE(TRUE)");
        assertAnswer(
            "1.0",
            "SELECT AS_DOUBLE(1)");
        assertAnswer(
            "null",
            "SELECT AS_DOUBLE(NULL)");
        assertAnswer(
            "1.5",
            "SELECT AS_DOUBLE(1.5)");
        assertAnswer(
            "1.0",
            "SELECT AS_DOUBLE(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_REAL': (VARCHAR(1))",
            "SELECT AS_REAL('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_REAL': (DATE)",
            "SELECT AS_REAL('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_REAL': (TIME(9))",
            "SELECT AS_REAL('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_REAL': (TIMESTAMP_NTZ(9))",
            "SELECT AS_REAL('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "1.5",
            "SELECT AS_REAL(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_REAL': (BINARY(67108864))",
            "SELECT AS_REAL(TO_BINARY('00'))");
        assertAnswer(
            "null",
            "SELECT AS_REAL(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "null",
            "SELECT AS_REAL([1])");
        assertAnswer(
            "null",
            "SELECT AS_REAL(TRUE)");
        assertAnswer(
            "1.0",
            "SELECT AS_REAL(1)");
        assertAnswer(
            "null",
            "SELECT AS_REAL(NULL)");
        assertAnswer(
            "1.5",
            "SELECT AS_REAL(1.5)");
        assertAnswer(
            "1.0",
            "SELECT AS_REAL(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter "
                + "'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIME(9)] for parameter 'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIMESTAMP_NTZ(9)] for parameter "
                + "'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter 'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BINARY(67108864)] for parameter "
                + "'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER(TO_BINARY('00'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [OBJECT] for parameter 'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [ARRAY] for parameter 'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER([1])");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter 'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter "
                + "'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NULL] for parameter 'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter "
                + "'AS_INTEGER(variantValue...)'",
            "SELECT AS_INTEGER(1.5)");
        assertAnswer(
            "1",
            "SELECT AS_INTEGER(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_OBJECT': (VARCHAR(1))",
            "SELECT AS_OBJECT('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_OBJECT': (DATE)",
            "SELECT AS_OBJECT('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_OBJECT': (TIME(9))",
            "SELECT AS_OBJECT('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_OBJECT': (TIMESTAMP_NTZ(9))",
            "SELECT AS_OBJECT('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT AS_OBJECT(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_OBJECT': (BINARY(67108864))",
            "SELECT AS_OBJECT(TO_BINARY('00'))");
        assertAnswer(
            "{\"a\":1}",
            "SELECT AS_OBJECT(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "null",
            "SELECT AS_OBJECT([1])");
        assertAnswer(
            "null",
            "SELECT AS_OBJECT(TRUE)");
        assertAnswer(
            "null",
            "SELECT AS_OBJECT(1)");
        assertAnswer(
            "null",
            "SELECT AS_OBJECT(NULL)");
        assertAnswer(
            "null",
            "SELECT AS_OBJECT(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_OBJECT(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_TIME': (VARCHAR(1))",
            "SELECT AS_TIME('a')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_TIME': (DATE)",
            "SELECT AS_TIME('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_TIME': (TIME(9))",
            "SELECT AS_TIME('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_TIME': (TIMESTAMP_NTZ(9))",
            "SELECT AS_TIME('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "null",
            "SELECT AS_TIME(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'AS_TIME': (BINARY(67108864))",
            "SELECT AS_TIME(TO_BINARY('00'))");
        assertAnswer(
            "null",
            "SELECT AS_TIME(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "null",
            "SELECT AS_TIME([1])");
        assertAnswer(
            "null",
            "SELECT AS_TIME(TRUE)");
        assertAnswer(
            "null",
            "SELECT AS_TIME(1)");
        assertAnswer(
            "null",
            "SELECT AS_TIME(NULL)");
        assertAnswer(
            "null",
            "SELECT AS_TIME(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_TIME(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [DATE] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIME(9)] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIMESTAMP_NTZ(9)] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BINARY(67108864)] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ(TO_BINARY('00'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [OBJECT] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [ARRAY] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ([1])");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NULL] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_TIMESTAMP_NTZ(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [DATE] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIME(9)] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIMESTAMP_NTZ(9)] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BINARY(67108864)] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ(TO_BINARY('00'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [OBJECT] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [ARRAY] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ([1])");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NULL] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_TIMESTAMP_LTZ(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ('a')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [DATE] for parameter 'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIME(9)] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIMESTAMP_NTZ(9)] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ('2024-01-01'::TIMESTAMP_NTZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ(1.5::FLOAT)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BINARY(67108864)] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ(TO_BINARY('00'))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [OBJECT] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [ARRAY] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ([1])");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [BOOLEAN] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ(TRUE)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NULL] for parameter 'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(2,1)] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ(1.5)");
        assertAnswer(
            "null",
            "SELECT AS_TIMESTAMP_TZ(TO_VARIANT(1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'STRIP_NULL_VALUE': (DATE)",
            "SELECT STRIP_NULL_VALUE('2024-01-01'::DATE)");
        assertAnswer(
            "1",
            "SELECT STRIP_NULL_VALUE(1)");
        assertAnswer(
            "1.5",
            "SELECT STRIP_NULL_VALUE(1.5::FLOAT)");
        assertAnswer(
            "[1]",
            "SELECT STRIP_NULL_VALUE([1])");
        assertAnswer(
            "{\"a\":1}",
            "SELECT STRIP_NULL_VALUE(OBJECT_CONSTRUCT('a', 1))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'STRIP_NULL_VALUE': (BINARY(67108864))",
            "SELECT STRIP_NULL_VALUE(TO_BINARY('00'))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'STRIP_NULL_VALUE': (TIME(9))",
            "SELECT STRIP_NULL_VALUE('10:20:30'::TIME)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[STRIP_NULL_VALUE()], expected 1, got 0",
            "SELECT STRIP_NULL_VALUE()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                + "[STRIP_NULL_VALUE(1, 2)] expected 1, got 2",
            "SELECT STRIP_NULL_VALUE(1, 2)");
    }

    @Test
    public void timestampFlavoursAndArrayElements() {
        assertAnswer(
            "true|false|false",
            "SELECT IS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_NTZ)), "
                + "IS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ)), "
                + "IS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "false|true|false",
            "SELECT IS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_NTZ)), "
                + "IS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ)), "
                + "IS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "false|false|true",
            "SELECT IS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_NTZ)), "
                + "IS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ)), "
                + "IS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "false|true|true",
            "SELECT AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_NTZ)) IS NULL, "
                + "AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ)) IS NULL, "
                + "AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)) IS NULL");
        assertAnswer(
            "true|false|true",
            "SELECT AS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_NTZ)) IS NULL, "
                + "AS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ)) IS NULL, "
                + "AS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)) IS NULL");
        assertAnswer(
            "true|true|false",
            "SELECT AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_NTZ)) IS NULL, "
                + "AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ)) IS NULL, "
                + "AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)) IS NULL");
        assertAnswer(
            "true",
            "SELECT AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)) = '2024-01-01 "
                + "00:00:00 +01:00'::TIMESTAMP_TZ");
        assertAnswer(
            "true",
            "SELECT AS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ)) = '2024-01-01 "
                + "00:00:00'::TIMESTAMP_LTZ");
        assertAnswer(
            "false|false",
            "SELECT IS_TIMESTAMP_TZ(PARSE_JSON('\"2024-01-01 00:00:00 +01:00\"')), "
                + "IS_TIMESTAMP_NTZ(PARSE_JSON('\"2024-01-01\"'))");
        assertAnswer(
            "false|false",
            "SELECT IS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01'::DATE)), "
                + "IS_DATE(TO_VARIANT('2024-01-01'::TIMESTAMP_NTZ))");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_ARRAY': (VARCHAR(1))",
            "SELECT IS_ARRAY('a')");
        assertAnswer(
            "false",
            "SELECT IS_ARRAY(TRUE)");
        assertAnswer(
            "true",
            "SELECT IS_ARRAY([1])");
        assertAnswer(
            "null",
            "SELECT IS_ARRAY(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_BOOLEAN': (VARCHAR(1))",
            "SELECT IS_BOOLEAN('a')");
        assertAnswer(
            "true",
            "SELECT IS_BOOLEAN(TRUE)");
        assertAnswer(
            "false",
            "SELECT IS_BOOLEAN(1)");
        assertAnswer(
            "true",
            "SELECT IS_INTEGER(5)");
        assertAnswer(
            "false",
            "SELECT IS_INTEGER(5.5)");
        assertAnswer(
            "false",
            "SELECT IS_INTEGER(TRUE)");
        assertAnswer(
            "null",
            "SELECT IS_INTEGER(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_INTEGER': (DATE)",
            "SELECT IS_INTEGER('2024-01-01'::DATE)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_TZ': (TIMESTAMP_TZ(9))",
            "SELECT IS_TIMESTAMP_TZ('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_TZ': (VARCHAR(1))",
            "SELECT IS_TIMESTAMP_TZ('x')");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_TZ(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_TIMESTAMP_LTZ': (TIMESTAMP_LTZ(9))",
            "SELECT IS_TIMESTAMP_LTZ(CURRENT_TIMESTAMP())");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [TIMESTAMP_TZ(9)] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ('x')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter "
                + "'AS_TIMESTAMP_TZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_TZ(1)");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter "
                + "'AS_TIMESTAMP_NTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_NTZ('x')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid type [NULL] for parameter "
                + "'AS_TIMESTAMP_LTZ(variantValue...)'",
            "SELECT AS_TIMESTAMP_LTZ(NULL)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | not enough arguments for function "
                + "[IS_TIMESTAMP_TZ()], expected 1, got 0",
            "SELECT IS_TIMESTAMP_TZ()");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'STRIP_NULL_VALUE': (VARCHAR(1))",
            "SELECT STRIP_NULL_VALUE('a')");
        assertAnswer(
            "null",
            "SELECT STRIP_NULL_VALUE(NULL)");
        assertAnswer(
            "true",
            "SELECT STRIP_NULL_VALUE(TRUE)");
        assertAnswer(
            "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
            "SELECT ARRAY_FLATTEN([[1], 2])");
        assertAnswer(
            "null",
            "SELECT ARRAY_FLATTEN([[1], NULL])");
        assertAnswer(
            "[1,[2]]",
            "SELECT ARRAY_FLATTEN([[1], [[2]]])");
        assertAnswer(
            "[]",
            "SELECT ARRAY_FLATTEN([])");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_FLATTEN': (NUMBER(1,0))",
            "SELECT ARRAY_FLATTEN(1)");
        assertAnswer(
            "[1,2]",
            "SELECT ARRAY_FLATTEN(PARSE_JSON('[[1],[2]]'))");
        assertAnswer(
            "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
            "SELECT ARRAY_FLATTEN(PARSE_JSON('[1]'))");
        assertAnswer(
            "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
            "SELECT ARRAY_FLATTEN([[1], PARSE_JSON('null')])");
        assertAnswer(
            "TIMESTAMP_NTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(AS_TIMESTAMP_NTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_NTZ)))");
        assertAnswer(
            "TIMESTAMP_LTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(AS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ)))");
    }

    @Test
    public void typedValuesAndColumns() {
        engine.execute(
            "CREATE OR REPLACE TABLE tv (s VARCHAR, d DATE, n NUMBER, f FLOAT, v VARIANT, b BINARY, bo BOOLEAN, a "
                + "ARRAY, o OBJECT, tz TIMESTAMP_TZ, c VARCHAR(10))");
        engine.execute(
            "INSERT INTO tv SELECT 'a', '2024-01-01', 1, 1.5, TO_VARIANT(1), TO_BINARY('00'), TRUE, [1], {'a': 1},"
                + " '2024-01-01 00:00:00 +01:00', 'c'");
        try {
            assertAnswer(
                "null",
                "SELECT ARRAY_FLATTEN([[1], NULL, 2])");
            assertAnswer(
                "null",
                "SELECT ARRAY_FLATTEN([2, NULL])");
            assertAnswer(
                "null",
                "SELECT ARRAY_FLATTEN([NULL])");
            assertAnswer(
                "null",
                "SELECT ARRAY_FLATTEN([NULL, [1]])");
            assertAnswer(
                "[1,2,undefined]",
                "SELECT ARRAY_FLATTEN([[1], [2, NULL]])");
            assertAnswer(
                "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
                "SELECT ARRAY_FLATTEN([[1], 'a'])");
            assertAnswer(
                "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
                "SELECT ARRAY_FLATTEN(['a'])");
            assertAnswer(
                "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
                "SELECT ARRAY_FLATTEN([{'a': 1}])");
            assertAnswer(
                "[1,[2],[3]]",
                "SELECT ARRAY_FLATTEN([[1], [[2], [3]]])");
            assertAnswer(
                "[]",
                "SELECT ARRAY_FLATTEN([[]])");
            assertAnswer(
                "[undefined]",
                "SELECT ARRAY_FLATTEN([[NULL]])");
            assertAnswer(
                "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
                "SELECT ARRAY_FLATTEN([PARSE_JSON('null')])");
            assertAnswer(
                "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
                "SELECT ARRAY_FLATTEN(PARSE_JSON('[[1], null]'))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'ARRAY_FLATTEN': (VARCHAR(1))",
                "SELECT ARRAY_FLATTEN('a')");
            assertAnswer(
                "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
                "SELECT ARRAY_FLATTEN(TO_VARIANT(1))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'ARRAY_FLATTEN': (OBJECT)",
                "SELECT ARRAY_FLATTEN({'a': 1})");
            assertAnswer(
                "null|a|123|null",
                "SELECT AS_VARCHAR(TO_VARIANT('2024-01-01'::DATE)), AS_CHAR(TO_VARIANT('a')), "
                    + "AS_VARCHAR(TO_VARIANT('123')), AS_VARCHAR(PARSE_JSON('123'))");
            assertAnswer(
                "false|true|true",
                "SELECT IS_VARCHAR(TO_VARIANT('2024-01-01'::DATE)), IS_VARCHAR(TO_VARIANT('123')), "
                    + "IS_CHAR(PARSE_JSON('\"a\"'))");
            assertAnswer(
                "false|true|false|true|true",
                "SELECT IS_DECIMAL(TO_VARIANT(1.5::FLOAT)), IS_DECIMAL(PARSE_JSON('1.5')), "
                    + "IS_DECIMAL(PARSE_JSON('1.5e0')), IS_DECIMAL(PARSE_JSON('1')), IS_DECIMAL(TO_VARIANT(1.5))");
            assertAnswer(
                "true|true|true|false|false|true",
                "SELECT IS_DOUBLE(PARSE_JSON('1')), IS_DOUBLE(PARSE_JSON('1.5e0')), IS_REAL(TO_VARIANT(1)), "
                    + "IS_INTEGER(TO_VARIANT(1.0::FLOAT)), IS_INTEGER(PARSE_JSON('1e0')), "
                    + "IS_INTEGER(TO_VARIANT(1.00))");
            assertAnswer(
                "1.5|1.0|null|1|null|null",
                "SELECT AS_DOUBLE(TO_VARIANT(1.5)), AS_DOUBLE(PARSE_JSON('1')), AS_REAL(PARSE_JSON('\"1\"')), "
                    + "AS_INTEGER(PARSE_JSON('1.0')), AS_INTEGER(PARSE_JSON('1e0')), AS_DECIMAL(PARSE_JSON('1.5e0'))");
            assertAnswer(
                "true|00|true|2024-01-01|true|10:20:30",
                "SELECT IS_BINARY(TO_VARIANT(TO_BINARY('00'))), AS_BINARY(TO_VARIANT(TO_BINARY('00'))), "
                    + "IS_DATE(TO_VARIANT('2024-01-01'::DATE)), AS_DATE(TO_VARIANT('2024-01-01'::DATE)), "
                    + "IS_TIME(TO_VARIANT('10:20:30'::TIME)), AS_TIME(TO_VARIANT('10:20:30'::TIME))");
            assertAnswer(
                "false|null|false|false|null|null",
                "SELECT IS_DATE(TO_VARIANT('2024-01-01')), AS_DATE(TO_VARIANT('2024-01-01')), "
                    + "IS_TIME(TO_VARIANT('10:20:30')), IS_BOOLEAN(TO_VARIANT('true')), "
                    + "AS_BOOLEAN(TO_VARIANT('true')), AS_BOOLEAN(TO_VARIANT(1))");
            assertAnswer(
                "1.3",
                "SELECT AS_DECIMAL(TO_VARIANT(1.25), 10, 1)");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [VARCHAR(1)] for parameter "
                    + "'AS_NUMBER(variantValue...)'",
                "SELECT AS_NUMBER('a', 10)");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [NUMBER(1,0)] for parameter "
                    + "'AS_INTEGER(variantValue...)'",
                "SELECT AS_INTEGER(1, 2)");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [NUMBER(3,2)] for parameter "
                    + "'AS_DECIMAL(variantValue...)'",
                "SELECT AS_DECIMAL(1.25, 10, 1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[AS_VARCHAR(CAST('a' AS VARIANT), 1)] expected 1, got 2",
                "SELECT AS_VARCHAR(TO_VARIANT('a'), 1)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[IS_INTEGER(CAST(1 AS VARIANT), 2)] expected 1, got 2",
                "SELECT IS_INTEGER(TO_VARIANT(1), 2)");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | too many arguments for function "
                    + "[AS_DOUBLE('a', 1)] expected 1, got 2",
                "SELECT AS_DOUBLE('a', 1)");
            assertAnswer(
                "{\"a\":1}",
                "SELECT AS_OBJECT(TO_VARIANT({'a': 1}))");
            assertAnswer(
                "[1]",
                "SELECT AS_ARRAY(TO_VARIANT([1]))");
            assertAnswer(
                "true|true|null|true",
                "SELECT IS_OBJECT(TO_VARIANT({'a': 1})), IS_ARRAY(TO_VARIANT([1])), "
                    + "IS_NULL_VALUE(TO_VARIANT(NULL)), IS_NULL_VALUE(PARSE_JSON('null'))");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (VARCHAR(16777216))",
                "SELECT IS_INTEGER(s) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (VARCHAR(10))",
                "SELECT IS_INTEGER(c) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (DATE)",
                "SELECT IS_INTEGER(d) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (TIMESTAMP_TZ(9))",
                "SELECT IS_INTEGER(tz) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (BINARY(8388608))",
                "SELECT IS_INTEGER(b) FROM tv");
            assertAnswer(
                "true|false|true|false|false|false",
                "SELECT IS_INTEGER(n), IS_INTEGER(f), IS_INTEGER(v), IS_INTEGER(bo), IS_INTEGER(a), IS_INTEGER(o) "
                    + "FROM tv");
            assertAnswer(
                "true|false|true|true|true|true|true|false",
                "SELECT IS_DECIMAL(n), IS_DECIMAL(f), IS_DOUBLE(n), IS_DOUBLE(f), IS_BOOLEAN(bo), IS_ARRAY(a), "
                    + "IS_OBJECT(o), IS_VARCHAR(v) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [NUMBER(38,0)] for parameter "
                    + "'AS_INTEGER(variantValue...)'",
                "SELECT AS_INTEGER(n) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777216)] for parameter "
                    + "'AS_INTEGER(variantValue...)'",
                "SELECT AS_INTEGER(s) FROM tv");
            assertAnswer(
                "1",
                "SELECT AS_INTEGER(v) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [TIMESTAMP_TZ(9)] for parameter "
                    + "'AS_TIMESTAMP_TZ(variantValue...)'",
                "SELECT AS_TIMESTAMP_TZ(tz) FROM tv");
            assertAnswer(
                "null",
                "SELECT AS_TIMESTAMP_TZ(v) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [FLOAT] for parameter 'AS_DECIMAL(variantValue...)'",
                "SELECT AS_DECIMAL(f) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'AS_VARCHAR': (VARCHAR(16777216))",
                "SELECT AS_VARCHAR(s) FROM tv");
            assertAnswer(
                "null|null",
                "SELECT AS_VARCHAR(n), AS_VARCHAR(v) FROM tv");
            assertAnswer(
                "1.0|1.5|true|[1]|{\"a\":1}",
                "SELECT AS_DOUBLE(n), AS_DOUBLE(f), AS_BOOLEAN(bo), AS_ARRAY(a), AS_OBJECT(o) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'STRIP_NULL_VALUE': (VARCHAR(16777216))",
                "SELECT STRIP_NULL_VALUE(s) FROM tv");
            assertAnswer(
                "1|1|[1]",
                "SELECT STRIP_NULL_VALUE(n), STRIP_NULL_VALUE(v), STRIP_NULL_VALUE(a) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [VARCHAR(16777216)] for parameter "
                    + "'AS_INTEGER(variantValue...)'",
                "SELECT AS_INTEGER(s) FROM tv WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (VARCHAR(16777216))",
                "SELECT IS_INTEGER(s) FROM tv WHERE 1 = 0");
            assertAnswer(
                "REFUSED SQL compilation error: | Object 'NOPE' does not exist or not authorized. Your primary "
                    + "role <ROLE> must have at least one privilege granted on TABLE NOPE.",
                "SELECT AS_INTEGER(1) FROM nope");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (VARCHAR(50331648))",
                "SELECT IS_INTEGER(UPPER(s)) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (VARCHAR(16777217))",
                "SELECT IS_INTEGER(s || 'a') FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: | invalid type [NUMBER(38,0)] for parameter "
                    + "'AS_INTEGER(variantValue...)'",
                "SELECT AS_INTEGER(n + 1) FROM tv");
            assertAnswer(
                "1",
                "SELECT AS_INTEGER(a[0]) FROM tv");
            assertAnswer(
                "true|true",
                "SELECT IS_INTEGER(a[0]), IS_INTEGER(o:a) FROM tv");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (DATE)",
                "SELECT IS_INTEGER(CURRENT_DATE())");
            assertAnswer(
                "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                    + "'IS_INTEGER': (VARCHAR(36))",
                "SELECT IS_INTEGER(UUID_STRING())");
        } finally {
            engine.execute(
                "DROP TABLE IF EXISTS tv");
        }
    }

    @Test
    public void arraySetFunctionsAndPredicates() {
        assertAnswer(
            "[1,2]",
            "SELECT ARRAY_EXCEPT([1, 1, 2, 3], [1, 3])");
        assertAnswer(
            "ARRAY[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_EXCEPT([1, 1, 2, 3], [1, 3]))");
        assertAnswer(
            "[1,1,3]",
            "SELECT ARRAY_INTERSECTION([1, 1, 2, 3], [1, 1, 3, 4])");
        assertAnswer(
            "ARRAY[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_INTERSECTION([1, 1, 2, 3], [1, 1, 3, 4]))");
        assertAnswer(
            "[1,2,3]",
            "SELECT ARRAY_FLATTEN([[1, 2], [3], []])");
        assertAnswer(
            "ARRAY[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_FLATTEN([[1, 2], [3], []]))");
        assertAnswer(
            "[1,3]",
            "SELECT ARRAY_REMOVE_AT([1, 2, 3], 1)");
        assertAnswer(
            "ARRAY[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_REMOVE_AT([1, 2, 3], 1))");
        assertAnswer(
            "[1,2]",
            "SELECT ARRAY_REMOVE_AT([1, 2, 3], -1)");
        assertAnswer(
            "ARRAY[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_REMOVE_AT([1, 2, 3], -1))");
        assertAnswer(
            "[1,2,3]",
            "SELECT ARRAY_REMOVE_AT([1, 2, 3], 5)");
        assertAnswer(
            "ARRAY[LOB]",
            "SELECT SYSTEM$TYPEOF(ARRAY_REMOVE_AT([1, 2, 3], 5))");
        assertAnswer(
            "true",
            "SELECT IS_ARRAY(PARSE_JSON('[1]'))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_ARRAY(PARSE_JSON('[1]')))");
        assertAnswer(
            "false",
            "SELECT IS_ARRAY(PARSE_JSON('1'))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_ARRAY(PARSE_JSON('1')))");
        assertAnswer(
            "true",
            "SELECT IS_BOOLEAN(PARSE_JSON('true'))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_BOOLEAN(PARSE_JSON('true')))");
        assertAnswer(
            "false",
            "SELECT IS_BOOLEAN(TO_VARIANT(1))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_BOOLEAN(TO_VARIANT(1)))");
        assertAnswer(
            "true",
            "SELECT IS_INTEGER(PARSE_JSON('5'))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_INTEGER(PARSE_JSON('5')))");
        assertAnswer(
            "false",
            "SELECT IS_INTEGER(PARSE_JSON('5.5'))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_INTEGER(PARSE_JSON('5.5')))");
        assertAnswer(
            "true",
            "SELECT IS_INTEGER(PARSE_JSON('5.0'))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_INTEGER(PARSE_JSON('5.0')))");
        assertAnswer(
            "true",
            "SELECT IS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)))");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01'::TIMESTAMP_NTZ))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01'::TIMESTAMP_NTZ)))");
        assertAnswer(
            "true",
            "SELECT IS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_TIMESTAMP_LTZ(TO_VARIANT('2024-01-01 00:00:00'::TIMESTAMP_LTZ)))");
        assertAnswer(
            "false",
            "SELECT IS_TIMESTAMP_LTZ(TO_VARIANT(1))");
        assertAnswer(
            "BOOLEAN[ROWINDEX]",
            "SELECT SYSTEM$TYPEOF(IS_TIMESTAMP_LTZ(TO_VARIANT(1)))");
        assertAnswer(
            "TIMESTAMP_TZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01 00:00:00 +01:00'::TIMESTAMP_TZ)))");
        assertAnswer(
            "null",
            "SELECT AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01'::TIMESTAMP_NTZ))");
        assertAnswer(
            "TIMESTAMP_TZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(AS_TIMESTAMP_TZ(TO_VARIANT('2024-01-01'::TIMESTAMP_NTZ)))");
        assertAnswer(
            "null",
            "SELECT AS_TIMESTAMP_LTZ(TO_VARIANT(1))");
        assertAnswer(
            "TIMESTAMP_LTZ(9)[SB16]",
            "SELECT SYSTEM$TYPEOF(AS_TIMESTAMP_LTZ(TO_VARIANT(1)))");
        assertAnswer(
            "null",
            "SELECT STRIP_NULL_VALUE(PARSE_JSON('null'))");
        assertAnswer(
            "VARIANT[LOB]",
            "SELECT SYSTEM$TYPEOF(STRIP_NULL_VALUE(PARSE_JSON('null')))");
        assertAnswer(
            "null",
            "SELECT STRIP_NULL_VALUE(PARSE_JSON('{\"a\":null}'):a)");
        assertAnswer(
            "VARIANT[LOB]",
            "SELECT SYSTEM$TYPEOF(STRIP_NULL_VALUE(PARSE_JSON('{\"a\":null}'):a))");
        assertAnswer(
            "1",
            "SELECT STRIP_NULL_VALUE(PARSE_JSON('1'))");
        assertAnswer(
            "VARIANT[LOB]",
            "SELECT SYSTEM$TYPEOF(STRIP_NULL_VALUE(PARSE_JSON('1')))");
        assertAnswer(
            "null",
            "SELECT ARRAY_EXCEPT([1], NULL)");
        assertAnswer(
            "REFUSED Not an array: 'Input argument to ARRAY_FLATTEN is not an array of arrays'",
            "SELECT ARRAY_FLATTEN([1, [2]])");
        assertAnswer(
            "null",
            "SELECT ARRAY_REMOVE_AT([1, 2, 3], NULL)");
        assertAnswer(
            "false",
            "SELECT IS_ARRAY(1)");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'IS_INTEGER': (VARCHAR(1))",
            "SELECT IS_INTEGER('5')");
        assertAnswer(
            "REFUSED SQL compilation error: error line 1 at position 7 | Invalid argument types for function "
                + "'ARRAY_INTERSECTION': (ARRAY, NUMBER(1,0))",
            "SELECT ARRAY_INTERSECTION([1], 1)");
        assertAnswer(
            "null",
            "SELECT ARRAY_FLATTEN(NULL)");
        assertAnswer(
            "1",
            "SELECT STRIP_NULL_VALUE(1)");
    }
}

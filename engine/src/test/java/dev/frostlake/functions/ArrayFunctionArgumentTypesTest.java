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

import dev.frostlake.BaseDatabaseTest;
import dev.frostlake.storage.ResultSet;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The array functions type their arguments while the statement compiles. A position that takes an array refuses
 * text, numbers, booleans, dates and times, binaries and objects; one that reads a value as a VARIANT refuses text,
 * dates and times and binaries; a whole-number position refuses booleans, dates and times, binaries, objects and
 * arrays. The refusal names the function and lists every argument's type, at the call (all live-verified).
 */
public class ArrayFunctionArgumentTypesTest extends BaseDatabaseTest {

    /**
     * The call with its probed position written as a question mark, the argument put there, and either the
     * argument types the refusal lists or, after an equals sign, the value answered.
     */
    private static final String CELLS = """
        ARRAY_SIZE(?) ; 'abc' ; VARCHAR(3)
        ARRAY_SIZE(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        ARRAY_SIZE(?) ; 1 ; =null
        ARRAY_SIZE(?) ; 1.5::FLOAT ; =null
        ARRAY_SIZE(?) ; TRUE ; =null
        ARRAY_SIZE(?) ; CURRENT_DATE() ; DATE
        ARRAY_SIZE(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        ARRAY_SIZE(?) ; X'00' ; BINARY(1)
        ARRAY_SIZE(?) ; TO_VARIANT(1) ; =null
        ARRAY_SIZE(?) ; OBJECT_CONSTRUCT('a', 1) ; =null
        ARRAY_SIZE(?) ; NULL ; =null
        ARRAY_SIZE(?) ; [1] ; =1
        ARRAY_SIZE(?) ; c.s ; VARCHAR(10)
        ARRAY_SIZE(?) ; c.n ; =null
        ARRAY_SIZE(?) ; c.a ; =2
        ARRAY_SIZE(?) ; c.o ; =null
        ARRAY_COMPACT(?) ; 'abc' ; VARCHAR(3)
        ARRAY_COMPACT(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        ARRAY_COMPACT(?) ; CURRENT_DATE() ; DATE
        ARRAY_COMPACT(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        ARRAY_COMPACT(?) ; X'00' ; BINARY(1)
        ARRAY_COMPACT(?) ; NULL ; =null
        ARRAY_COMPACT(?) ; [1] ; =[1]
        ARRAY_COMPACT(?) ; c.s ; VARCHAR(10)
        ARRAY_COMPACT(?) ; c.a ; =[1,2]
        ARRAY_CAT(?, [1]) ; 'abc' ; VARCHAR(3), ARRAY
        ARRAY_CAT(?, [1]) ; NULL::VARCHAR ; VARCHAR(134217728), ARRAY
        ARRAY_CAT(?, [1]) ; CURRENT_DATE() ; DATE, ARRAY
        ARRAY_CAT(?, [1]) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), ARRAY
        ARRAY_CAT(?, [1]) ; X'00' ; BINARY(1), ARRAY
        ARRAY_CAT(?, [1]) ; NULL ; =null
        ARRAY_CAT(?, [1]) ; [1] ; =[1,1]
        ARRAY_CAT(?, [1]) ; c.s ; VARCHAR(10), ARRAY
        ARRAY_CAT(?, [1]) ; c.a ; =[1,2,1]
        ARRAY_CAT([1], ?) ; 'abc' ; ARRAY, VARCHAR(3)
        ARRAY_CAT([1], ?) ; NULL::VARCHAR ; ARRAY, VARCHAR(134217728)
        ARRAY_CAT([1], ?) ; CURRENT_DATE() ; ARRAY, DATE
        ARRAY_CAT([1], ?) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9)
        ARRAY_CAT([1], ?) ; X'00' ; ARRAY, BINARY(1)
        ARRAY_CAT([1], ?) ; NULL ; =null
        ARRAY_CAT([1], ?) ; [1] ; =[1,1]
        ARRAY_CAT([1], ?) ; c.s ; ARRAY, VARCHAR(10)
        ARRAY_CAT([1], ?) ; c.a ; =[1,1,2]
        ARRAY_TO_STRING(?, ',') ; 'abc' ; VARCHAR(3), VARCHAR(1)
        ARRAY_TO_STRING(?, ',') ; NULL::VARCHAR ; VARCHAR(134217728), VARCHAR(1)
        ARRAY_TO_STRING(?, ',') ; CURRENT_DATE() ; DATE, VARCHAR(1)
        ARRAY_TO_STRING(?, ',') ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), VARCHAR(1)
        ARRAY_TO_STRING(?, ',') ; X'00' ; BINARY(1), VARCHAR(1)
        ARRAY_TO_STRING(?, ',') ; NULL ; =null
        ARRAY_TO_STRING(?, ',') ; [1] ; =1
        ARRAY_TO_STRING(?, ',') ; c.s ; VARCHAR(10), VARCHAR(1)
        ARRAY_TO_STRING(?, ',') ; c.a ; =1,2
        ARRAY_TO_STRING([1], ?) ; 'abc' ; =1
        ARRAY_TO_STRING([1], ?) ; NULL::VARCHAR ; =null
        ARRAY_TO_STRING([1], ?) ; 1 ; =1
        ARRAY_TO_STRING([1], ?) ; 1.5::FLOAT ; =1
        ARRAY_TO_STRING([1], ?) ; TRUE ; =1
        ARRAY_TO_STRING([1], ?) ; CURRENT_DATE() ; =1
        ARRAY_TO_STRING([1], ?) ; CURRENT_TIMESTAMP() ; =1
        ARRAY_TO_STRING([1], ?) ; X'00' ; ARRAY, BINARY(1)
        ARRAY_TO_STRING([1], ?) ; TO_VARIANT(1) ; =1
        ARRAY_TO_STRING([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, OBJECT
        ARRAY_TO_STRING([1], ?) ; NULL ; =null
        ARRAY_TO_STRING([1], ?) ; [1] ; ARRAY, ARRAY
        ARRAY_TO_STRING([1], ?) ; c.s ; =1
        ARRAY_TO_STRING([1], ?) ; c.n ; =1
        ARRAY_TO_STRING([1], ?) ; c.a ; ARRAY, ARRAY
        ARRAY_TO_STRING([1], ?) ; c.o ; ARRAY, OBJECT
        ARRAY_CONTAINS(1::VARIANT, ?) ; 'abc' ; VARIANT, VARCHAR(3)
        ARRAY_CONTAINS(1::VARIANT, ?) ; NULL::VARCHAR ; VARIANT, VARCHAR(134217728)
        ARRAY_CONTAINS(1::VARIANT, ?) ; 1 ; VARIANT, NUMBER(1,0)
        ARRAY_CONTAINS(1::VARIANT, ?) ; 1.5::FLOAT ; VARIANT, FLOAT
        ARRAY_CONTAINS(1::VARIANT, ?) ; TRUE ; VARIANT, BOOLEAN
        ARRAY_CONTAINS(1::VARIANT, ?) ; CURRENT_DATE() ; VARIANT, DATE
        ARRAY_CONTAINS(1::VARIANT, ?) ; CURRENT_TIMESTAMP() ; VARIANT, TIMESTAMP_LTZ(9)
        ARRAY_CONTAINS(1::VARIANT, ?) ; X'00' ; VARIANT, BINARY(1)
        ARRAY_CONTAINS(1::VARIANT, ?) ; OBJECT_CONSTRUCT('a', 1) ; VARIANT, OBJECT
        ARRAY_CONTAINS(1::VARIANT, ?) ; NULL ; =null
        ARRAY_CONTAINS(1::VARIANT, ?) ; [1] ; =true
        ARRAY_CONTAINS(1::VARIANT, ?) ; c.s ; VARIANT, VARCHAR(10)
        ARRAY_CONTAINS(1::VARIANT, ?) ; c.n ; VARIANT, NUMBER(5,0)
        ARRAY_CONTAINS(1::VARIANT, ?) ; c.a ; =true
        ARRAY_CONTAINS(1::VARIANT, ?) ; c.o ; VARIANT, OBJECT
        ARRAY_CONTAINS(?, [1]) ; 'abc' ; VARCHAR(3), ARRAY
        ARRAY_CONTAINS(?, [1]) ; NULL::VARCHAR ; VARCHAR(134217728), ARRAY
        ARRAY_CONTAINS(?, [1]) ; 1 ; =true
        ARRAY_CONTAINS(?, [1]) ; 1.5::FLOAT ; =false
        ARRAY_CONTAINS(?, [1]) ; TRUE ; =false
        ARRAY_CONTAINS(?, [1]) ; CURRENT_DATE() ; DATE, ARRAY
        ARRAY_CONTAINS(?, [1]) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), ARRAY
        ARRAY_CONTAINS(?, [1]) ; X'00' ; BINARY(1), ARRAY
        ARRAY_CONTAINS(?, [1]) ; TO_VARIANT(1) ; =true
        ARRAY_CONTAINS(?, [1]) ; OBJECT_CONSTRUCT('a', 1) ; =false
        ARRAY_CONTAINS(?, [1]) ; NULL ; =null
        ARRAY_CONTAINS(?, [1]) ; [1] ; =false
        ARRAY_CONTAINS(?, [1]) ; c.s ; VARCHAR(10), ARRAY
        ARRAY_CONTAINS(?, [1]) ; c.n ; =true
        ARRAY_CONTAINS(?, [1]) ; c.a ; =false
        ARRAY_CONTAINS(?, [1]) ; c.o ; =false
        ARRAY_POSITION(1::VARIANT, ?) ; 'abc' ; VARIANT, VARCHAR(3)
        ARRAY_POSITION(1::VARIANT, ?) ; NULL::VARCHAR ; VARIANT, VARCHAR(134217728)
        ARRAY_POSITION(1::VARIANT, ?) ; 1 ; VARIANT, NUMBER(1,0)
        ARRAY_POSITION(1::VARIANT, ?) ; 1.5::FLOAT ; VARIANT, FLOAT
        ARRAY_POSITION(1::VARIANT, ?) ; TRUE ; VARIANT, BOOLEAN
        ARRAY_POSITION(1::VARIANT, ?) ; CURRENT_DATE() ; VARIANT, DATE
        ARRAY_POSITION(1::VARIANT, ?) ; CURRENT_TIMESTAMP() ; VARIANT, TIMESTAMP_LTZ(9)
        ARRAY_POSITION(1::VARIANT, ?) ; X'00' ; VARIANT, BINARY(1)
        ARRAY_POSITION(1::VARIANT, ?) ; OBJECT_CONSTRUCT('a', 1) ; VARIANT, OBJECT
        ARRAY_POSITION(1::VARIANT, ?) ; NULL ; =null
        ARRAY_POSITION(1::VARIANT, ?) ; [1] ; =0
        ARRAY_POSITION(1::VARIANT, ?) ; c.s ; VARIANT, VARCHAR(10)
        ARRAY_POSITION(1::VARIANT, ?) ; c.n ; VARIANT, NUMBER(5,0)
        ARRAY_POSITION(1::VARIANT, ?) ; c.a ; =0
        ARRAY_POSITION(1::VARIANT, ?) ; c.o ; VARIANT, OBJECT
        ARRAY_POSITION(?, [1]) ; 'abc' ; VARCHAR(3), ARRAY
        ARRAY_POSITION(?, [1]) ; NULL::VARCHAR ; VARCHAR(134217728), ARRAY
        ARRAY_POSITION(?, [1]) ; 1 ; =0
        ARRAY_POSITION(?, [1]) ; 1.5::FLOAT ; =null
        ARRAY_POSITION(?, [1]) ; TRUE ; =null
        ARRAY_POSITION(?, [1]) ; CURRENT_DATE() ; DATE, ARRAY
        ARRAY_POSITION(?, [1]) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), ARRAY
        ARRAY_POSITION(?, [1]) ; X'00' ; BINARY(1), ARRAY
        ARRAY_POSITION(?, [1]) ; TO_VARIANT(1) ; =0
        ARRAY_POSITION(?, [1]) ; OBJECT_CONSTRUCT('a', 1) ; =null
        ARRAY_POSITION(?, [1]) ; NULL ; =null
        ARRAY_POSITION(?, [1]) ; [1] ; =null
        ARRAY_POSITION(?, [1]) ; c.s ; VARCHAR(10), ARRAY
        ARRAY_POSITION(?, [1]) ; c.n ; =0
        ARRAY_POSITION(?, [1]) ; c.a ; =null
        ARRAY_POSITION(?, [1]) ; c.o ; =null
        ARRAY_SLICE(?, 0, 1) ; 'abc' ; VARCHAR(3), NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; NULL::VARCHAR ; VARCHAR(134217728), NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; 1 ; NUMBER(1,0), NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; 1.5::FLOAT ; FLOAT, NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; TRUE ; BOOLEAN, NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; CURRENT_DATE() ; DATE, NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; X'00' ; BINARY(1), NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; NULL ; =null
        ARRAY_SLICE(?, 0, 1) ; [1] ; =[1]
        ARRAY_SLICE(?, 0, 1) ; c.s ; VARCHAR(10), NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; c.n ; NUMBER(5,0), NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE(?, 0, 1) ; c.a ; =[1]
        ARRAY_SLICE(?, 0, 1) ; c.o ; OBJECT, NUMBER(1,0), NUMBER(1,0)
        ARRAY_SLICE([1], ?, 1) ; 1 ; =[]
        ARRAY_SLICE([1], ?, 1) ; 1.5::FLOAT ; =[]
        ARRAY_SLICE([1], ?, 1) ; TRUE ; ARRAY, BOOLEAN, NUMBER(1,0)
        ARRAY_SLICE([1], ?, 1) ; CURRENT_DATE() ; ARRAY, DATE, NUMBER(1,0)
        ARRAY_SLICE([1], ?, 1) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9), NUMBER(1,0)
        ARRAY_SLICE([1], ?, 1) ; X'00' ; ARRAY, BINARY(1), NUMBER(1,0)
        ARRAY_SLICE([1], ?, 1) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, OBJECT, NUMBER(1,0)
        ARRAY_SLICE([1], ?, 1) ; [1] ; ARRAY, ARRAY, NUMBER(1,0)
        ARRAY_SLICE([1], ?, 1) ; c.n ; =[]
        ARRAY_SLICE([1], ?, 1) ; c.a ; ARRAY, ARRAY, NUMBER(1,0)
        ARRAY_SLICE([1], ?, 1) ; c.o ; ARRAY, OBJECT, NUMBER(1,0)
        ARRAY_SLICE([1], 0, ?) ; 1 ; =[1]
        ARRAY_SLICE([1], 0, ?) ; 1.5::FLOAT ; =[1]
        ARRAY_SLICE([1], 0, ?) ; TRUE ; ARRAY, NUMBER(1,0), BOOLEAN
        ARRAY_SLICE([1], 0, ?) ; CURRENT_DATE() ; ARRAY, NUMBER(1,0), DATE
        ARRAY_SLICE([1], 0, ?) ; CURRENT_TIMESTAMP() ; ARRAY, NUMBER(1,0), TIMESTAMP_LTZ(9)
        ARRAY_SLICE([1], 0, ?) ; X'00' ; ARRAY, NUMBER(1,0), BINARY(1)
        ARRAY_SLICE([1], 0, ?) ; TO_VARIANT(1) ; =[1]
        ARRAY_SLICE([1], 0, ?) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, NUMBER(1,0), OBJECT
        ARRAY_SLICE([1], 0, ?) ; [1] ; ARRAY, NUMBER(1,0), ARRAY
        ARRAY_SLICE([1], 0, ?) ; c.n ; =[1]
        ARRAY_SLICE([1], 0, ?) ; c.a ; ARRAY, NUMBER(1,0), ARRAY
        ARRAY_SLICE([1], 0, ?) ; c.o ; ARRAY, NUMBER(1,0), OBJECT
        ARRAY_APPEND(?, 1) ; 'abc' ; VARCHAR(3), NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; NULL::VARCHAR ; VARCHAR(134217728), NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; 1 ; NUMBER(1,0), NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; 1.5::FLOAT ; FLOAT, NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; TRUE ; BOOLEAN, NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; CURRENT_DATE() ; DATE, NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; X'00' ; BINARY(1), NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; NULL ; =null
        ARRAY_APPEND(?, 1) ; [1] ; =[1,1]
        ARRAY_APPEND(?, 1) ; c.s ; VARCHAR(10), NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; c.n ; NUMBER(5,0), NUMBER(1,0)
        ARRAY_APPEND(?, 1) ; c.a ; =[1,2,1]
        ARRAY_APPEND(?, 1) ; c.o ; OBJECT, NUMBER(1,0)
        ARRAY_APPEND([1], ?) ; 'abc' ; =[1,"abc"]
        ARRAY_APPEND([1], ?) ; NULL::VARCHAR ; =[1,undefined]
        ARRAY_APPEND([1], ?) ; 1 ; =[1,1]
        ARRAY_APPEND([1], ?) ; 1.5::FLOAT ; =[1,1.500000000000000e+00]
        ARRAY_APPEND([1], ?) ; TRUE ; =[1,true]
        ARRAY_APPEND([1], ?) ; '2024-01-02'::DATE ; =[1,"2024-01-02"]
        ARRAY_APPEND([1], ?) ; X'00' ; =[1,"00"]
        ARRAY_APPEND([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; =[1,{"a":1}]
        ARRAY_APPEND([1], ?) ; NULL ; =[1,undefined]
        ARRAY_APPEND([1], ?) ; [1] ; =[1,[1]]
        ARRAY_APPEND([1], ?) ; c.s ; =[1,"abc"]
        ARRAY_APPEND([1], ?) ; c.n ; =[1,1]
        ARRAY_APPEND([1], ?) ; c.a ; =[1,[1,2]]
        ARRAY_APPEND([1], ?) ; c.o ; =[1,{"k":1}]
        ARRAY_PREPEND(?, 1) ; 'abc' ; VARCHAR(3), NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; NULL::VARCHAR ; VARCHAR(134217728), NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; 1 ; NUMBER(1,0), NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; 1.5::FLOAT ; FLOAT, NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; TRUE ; BOOLEAN, NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; CURRENT_DATE() ; DATE, NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; X'00' ; BINARY(1), NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; NULL ; =null
        ARRAY_PREPEND(?, 1) ; [1] ; =[1,1]
        ARRAY_PREPEND(?, 1) ; c.s ; VARCHAR(10), NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; c.n ; NUMBER(5,0), NUMBER(1,0)
        ARRAY_PREPEND(?, 1) ; c.a ; =[1,1,2]
        ARRAY_PREPEND(?, 1) ; c.o ; OBJECT, NUMBER(1,0)
        ARRAY_PREPEND([1], ?) ; 'abc' ; =["abc",1]
        ARRAY_PREPEND([1], ?) ; NULL::VARCHAR ; =[undefined,1]
        ARRAY_PREPEND([1], ?) ; 1 ; =[1,1]
        ARRAY_PREPEND([1], ?) ; 1.5::FLOAT ; =[1.500000000000000e+00,1]
        ARRAY_PREPEND([1], ?) ; TRUE ; =[true,1]
        ARRAY_PREPEND([1], ?) ; '2024-01-02'::DATE ; =["2024-01-02",1]
        ARRAY_PREPEND([1], ?) ; X'00' ; =["00",1]
        ARRAY_PREPEND([1], ?) ; TO_VARIANT(1) ; =[1,1]
        ARRAY_PREPEND([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; =[{"a":1},1]
        ARRAY_PREPEND([1], ?) ; NULL ; =[undefined,1]
        ARRAY_PREPEND([1], ?) ; [1] ; =[[1],1]
        ARRAY_PREPEND([1], ?) ; c.s ; =["abc",1]
        ARRAY_PREPEND([1], ?) ; c.n ; =[1,1]
        ARRAY_PREPEND([1], ?) ; c.a ; =[[1,2],1]
        ARRAY_PREPEND([1], ?) ; c.o ; =[{"k":1},1]
        ARRAY_DISTINCT(?) ; 'abc' ; VARCHAR(3)
        ARRAY_DISTINCT(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        ARRAY_DISTINCT(?) ; 1 ; NUMBER(1,0)
        ARRAY_DISTINCT(?) ; 1.5::FLOAT ; FLOAT
        ARRAY_DISTINCT(?) ; TRUE ; BOOLEAN
        ARRAY_DISTINCT(?) ; CURRENT_DATE() ; DATE
        ARRAY_DISTINCT(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        ARRAY_DISTINCT(?) ; X'00' ; BINARY(1)
        ARRAY_DISTINCT(?) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT
        ARRAY_DISTINCT(?) ; NULL ; =null
        ARRAY_DISTINCT(?) ; [1] ; =[1]
        ARRAY_DISTINCT(?) ; c.s ; VARCHAR(10)
        ARRAY_DISTINCT(?) ; c.n ; NUMBER(5,0)
        ARRAY_DISTINCT(?) ; c.a ; =[1,2]
        ARRAY_DISTINCT(?) ; c.o ; OBJECT
        ARRAY_MAX(?) ; 'abc' ; VARCHAR(3)
        ARRAY_MAX(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        ARRAY_MAX(?) ; 1 ; NUMBER(1,0)
        ARRAY_MAX(?) ; 1.5::FLOAT ; FLOAT
        ARRAY_MAX(?) ; TRUE ; BOOLEAN
        ARRAY_MAX(?) ; CURRENT_DATE() ; DATE
        ARRAY_MAX(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        ARRAY_MAX(?) ; X'00' ; BINARY(1)
        ARRAY_MAX(?) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT
        ARRAY_MAX(?) ; NULL ; =null
        ARRAY_MAX(?) ; [1] ; =1
        ARRAY_MAX(?) ; c.s ; VARCHAR(10)
        ARRAY_MAX(?) ; c.n ; NUMBER(5,0)
        ARRAY_MAX(?) ; c.a ; =2
        ARRAY_MAX(?) ; c.o ; OBJECT
        ARRAY_MIN(?) ; 'abc' ; VARCHAR(3)
        ARRAY_MIN(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        ARRAY_MIN(?) ; 1 ; NUMBER(1,0)
        ARRAY_MIN(?) ; 1.5::FLOAT ; FLOAT
        ARRAY_MIN(?) ; TRUE ; BOOLEAN
        ARRAY_MIN(?) ; CURRENT_DATE() ; DATE
        ARRAY_MIN(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        ARRAY_MIN(?) ; X'00' ; BINARY(1)
        ARRAY_MIN(?) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT
        ARRAY_MIN(?) ; NULL ; =null
        ARRAY_MIN(?) ; [1] ; =1
        ARRAY_MIN(?) ; c.s ; VARCHAR(10)
        ARRAY_MIN(?) ; c.n ; NUMBER(5,0)
        ARRAY_MIN(?) ; c.a ; =1
        ARRAY_MIN(?) ; c.o ; OBJECT
        ARRAY_SORT(?) ; 'abc' ; VARCHAR(3)
        ARRAY_SORT(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        ARRAY_SORT(?) ; 1 ; NUMBER(1,0)
        ARRAY_SORT(?) ; 1.5::FLOAT ; FLOAT
        ARRAY_SORT(?) ; TRUE ; BOOLEAN
        ARRAY_SORT(?) ; CURRENT_DATE() ; DATE
        ARRAY_SORT(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        ARRAY_SORT(?) ; X'00' ; BINARY(1)
        ARRAY_SORT(?) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT
        ARRAY_SORT(?) ; NULL ; =null
        ARRAY_SORT(?) ; [1] ; =[1]
        ARRAY_SORT(?) ; c.s ; VARCHAR(10)
        ARRAY_SORT(?) ; c.n ; NUMBER(5,0)
        ARRAY_SORT(?) ; c.a ; =[1,2]
        ARRAY_SORT(?) ; c.o ; OBJECT
        ARRAY_FLATTEN(?) ; 'abc' ; VARCHAR(3)
        ARRAY_FLATTEN(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        ARRAY_FLATTEN(?) ; 1 ; NUMBER(1,0)
        ARRAY_FLATTEN(?) ; 1.5::FLOAT ; FLOAT
        ARRAY_FLATTEN(?) ; TRUE ; BOOLEAN
        ARRAY_FLATTEN(?) ; CURRENT_DATE() ; DATE
        ARRAY_FLATTEN(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        ARRAY_FLATTEN(?) ; X'00' ; BINARY(1)
        ARRAY_FLATTEN(?) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT
        ARRAY_FLATTEN(?) ; NULL ; =null
        ARRAY_FLATTEN(?) ; c.s ; VARCHAR(10)
        ARRAY_FLATTEN(?) ; c.n ; NUMBER(5,0)
        ARRAY_FLATTEN(?) ; c.o ; OBJECT
        ARRAYS_ZIP(?) ; 'abc' ; VARCHAR(3)
        ARRAYS_ZIP(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        ARRAYS_ZIP(?) ; 1 ; NUMBER(1,0)
        ARRAYS_ZIP(?) ; 1.5::FLOAT ; FLOAT
        ARRAYS_ZIP(?) ; TRUE ; BOOLEAN
        ARRAYS_ZIP(?) ; CURRENT_DATE() ; DATE
        ARRAYS_ZIP(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        ARRAYS_ZIP(?) ; X'00' ; BINARY(1)
        ARRAYS_ZIP(?) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT
        ARRAYS_ZIP(?) ; NULL ; =null
        ARRAYS_ZIP(?) ; [1] ; =[{"$1":1}]
        ARRAYS_ZIP(?) ; c.s ; VARCHAR(10)
        ARRAYS_ZIP(?) ; c.n ; NUMBER(5,0)
        ARRAYS_ZIP(?) ; c.a ; =[{"$1":1},{"$1":2}]
        ARRAYS_ZIP(?) ; c.o ; OBJECT
        ARRAYS_ZIP([1], ?) ; 'abc' ; ARRAY, VARCHAR(3)
        ARRAYS_ZIP([1], ?) ; NULL::VARCHAR ; ARRAY, VARCHAR(134217728)
        ARRAYS_ZIP([1], ?) ; 1 ; ARRAY, NUMBER(1,0)
        ARRAYS_ZIP([1], ?) ; 1.5::FLOAT ; ARRAY, FLOAT
        ARRAYS_ZIP([1], ?) ; TRUE ; ARRAY, BOOLEAN
        ARRAYS_ZIP([1], ?) ; CURRENT_DATE() ; ARRAY, DATE
        ARRAYS_ZIP([1], ?) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9)
        ARRAYS_ZIP([1], ?) ; X'00' ; ARRAY, BINARY(1)
        ARRAYS_ZIP([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, OBJECT
        ARRAYS_ZIP([1], ?) ; NULL ; =null
        ARRAYS_ZIP([1], ?) ; [1] ; =[{"$1":1,"$2":1}]
        ARRAYS_ZIP([1], ?) ; c.s ; ARRAY, VARCHAR(10)
        ARRAYS_ZIP([1], ?) ; c.n ; ARRAY, NUMBER(5,0)
        ARRAYS_ZIP([1], ?) ; c.a ; =[{"$1":1,"$2":1},{"$1":null,"$2":2}]
        ARRAYS_ZIP([1], ?) ; c.o ; ARRAY, OBJECT
        ARRAY_REVERSE(?) ; 'abc' ; VARCHAR(3)
        ARRAY_REVERSE(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        ARRAY_REVERSE(?) ; 1 ; NUMBER(1,0)
        ARRAY_REVERSE(?) ; 1.5::FLOAT ; FLOAT
        ARRAY_REVERSE(?) ; TRUE ; BOOLEAN
        ARRAY_REVERSE(?) ; CURRENT_DATE() ; DATE
        ARRAY_REVERSE(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        ARRAY_REVERSE(?) ; X'00' ; BINARY(1)
        ARRAY_REVERSE(?) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT
        ARRAY_REVERSE(?) ; NULL ; =null
        ARRAY_REVERSE(?) ; [1] ; =[1]
        ARRAY_REVERSE(?) ; c.s ; VARCHAR(10)
        ARRAY_REVERSE(?) ; c.n ; NUMBER(5,0)
        ARRAY_REVERSE(?) ; c.a ; =[2,1]
        ARRAY_REVERSE(?) ; c.o ; OBJECT
        ARRAYS_OVERLAP(?, [1]) ; 'abc' ; VARCHAR(3), ARRAY
        ARRAYS_OVERLAP(?, [1]) ; NULL::VARCHAR ; VARCHAR(134217728), ARRAY
        ARRAYS_OVERLAP(?, [1]) ; 1 ; NUMBER(1,0), ARRAY
        ARRAYS_OVERLAP(?, [1]) ; 1.5::FLOAT ; FLOAT, ARRAY
        ARRAYS_OVERLAP(?, [1]) ; TRUE ; BOOLEAN, ARRAY
        ARRAYS_OVERLAP(?, [1]) ; CURRENT_DATE() ; DATE, ARRAY
        ARRAYS_OVERLAP(?, [1]) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), ARRAY
        ARRAYS_OVERLAP(?, [1]) ; X'00' ; BINARY(1), ARRAY
        ARRAYS_OVERLAP(?, [1]) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, ARRAY
        ARRAYS_OVERLAP(?, [1]) ; NULL ; =null
        ARRAYS_OVERLAP(?, [1]) ; [1] ; =true
        ARRAYS_OVERLAP(?, [1]) ; c.s ; VARCHAR(10), ARRAY
        ARRAYS_OVERLAP(?, [1]) ; c.n ; NUMBER(5,0), ARRAY
        ARRAYS_OVERLAP(?, [1]) ; c.a ; =true
        ARRAYS_OVERLAP(?, [1]) ; c.o ; OBJECT, ARRAY
        ARRAYS_OVERLAP([1], ?) ; 'abc' ; ARRAY, VARCHAR(3)
        ARRAYS_OVERLAP([1], ?) ; NULL::VARCHAR ; ARRAY, VARCHAR(134217728)
        ARRAYS_OVERLAP([1], ?) ; 1 ; ARRAY, NUMBER(1,0)
        ARRAYS_OVERLAP([1], ?) ; 1.5::FLOAT ; ARRAY, FLOAT
        ARRAYS_OVERLAP([1], ?) ; TRUE ; ARRAY, BOOLEAN
        ARRAYS_OVERLAP([1], ?) ; CURRENT_DATE() ; ARRAY, DATE
        ARRAYS_OVERLAP([1], ?) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9)
        ARRAYS_OVERLAP([1], ?) ; X'00' ; ARRAY, BINARY(1)
        ARRAYS_OVERLAP([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, OBJECT
        ARRAYS_OVERLAP([1], ?) ; NULL ; =null
        ARRAYS_OVERLAP([1], ?) ; [1] ; =true
        ARRAYS_OVERLAP([1], ?) ; c.s ; ARRAY, VARCHAR(10)
        ARRAYS_OVERLAP([1], ?) ; c.n ; ARRAY, NUMBER(5,0)
        ARRAYS_OVERLAP([1], ?) ; c.a ; =true
        ARRAYS_OVERLAP([1], ?) ; c.o ; ARRAY, OBJECT
        ARRAY_EXCEPT(?, [1]) ; 'abc' ; VARCHAR(3), ARRAY
        ARRAY_EXCEPT(?, [1]) ; NULL::VARCHAR ; VARCHAR(134217728), ARRAY
        ARRAY_EXCEPT(?, [1]) ; 1 ; NUMBER(1,0), ARRAY
        ARRAY_EXCEPT(?, [1]) ; 1.5::FLOAT ; FLOAT, ARRAY
        ARRAY_EXCEPT(?, [1]) ; TRUE ; BOOLEAN, ARRAY
        ARRAY_EXCEPT(?, [1]) ; CURRENT_DATE() ; DATE, ARRAY
        ARRAY_EXCEPT(?, [1]) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), ARRAY
        ARRAY_EXCEPT(?, [1]) ; X'00' ; BINARY(1), ARRAY
        ARRAY_EXCEPT(?, [1]) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, ARRAY
        ARRAY_EXCEPT(?, [1]) ; NULL ; =null
        ARRAY_EXCEPT(?, [1]) ; [1] ; =[]
        ARRAY_EXCEPT(?, [1]) ; c.s ; VARCHAR(10), ARRAY
        ARRAY_EXCEPT(?, [1]) ; c.n ; NUMBER(5,0), ARRAY
        ARRAY_EXCEPT(?, [1]) ; c.a ; =[2]
        ARRAY_EXCEPT(?, [1]) ; c.o ; OBJECT, ARRAY
        ARRAY_EXCEPT([1], ?) ; 'abc' ; ARRAY, VARCHAR(3)
        ARRAY_EXCEPT([1], ?) ; NULL::VARCHAR ; ARRAY, VARCHAR(134217728)
        ARRAY_EXCEPT([1], ?) ; 1 ; ARRAY, NUMBER(1,0)
        ARRAY_EXCEPT([1], ?) ; 1.5::FLOAT ; ARRAY, FLOAT
        ARRAY_EXCEPT([1], ?) ; TRUE ; ARRAY, BOOLEAN
        ARRAY_EXCEPT([1], ?) ; CURRENT_DATE() ; ARRAY, DATE
        ARRAY_EXCEPT([1], ?) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9)
        ARRAY_EXCEPT([1], ?) ; X'00' ; ARRAY, BINARY(1)
        ARRAY_EXCEPT([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, OBJECT
        ARRAY_EXCEPT([1], ?) ; NULL ; =null
        ARRAY_EXCEPT([1], ?) ; [1] ; =[]
        ARRAY_EXCEPT([1], ?) ; c.s ; ARRAY, VARCHAR(10)
        ARRAY_EXCEPT([1], ?) ; c.n ; ARRAY, NUMBER(5,0)
        ARRAY_EXCEPT([1], ?) ; c.a ; =[]
        ARRAY_EXCEPT([1], ?) ; c.o ; ARRAY, OBJECT
        ARRAY_INTERSECTION(?, [1]) ; 'abc' ; VARCHAR(3), ARRAY
        ARRAY_INTERSECTION(?, [1]) ; NULL::VARCHAR ; VARCHAR(134217728), ARRAY
        ARRAY_INTERSECTION(?, [1]) ; 1 ; NUMBER(1,0), ARRAY
        ARRAY_INTERSECTION(?, [1]) ; 1.5::FLOAT ; FLOAT, ARRAY
        ARRAY_INTERSECTION(?, [1]) ; TRUE ; BOOLEAN, ARRAY
        ARRAY_INTERSECTION(?, [1]) ; CURRENT_DATE() ; DATE, ARRAY
        ARRAY_INTERSECTION(?, [1]) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), ARRAY
        ARRAY_INTERSECTION(?, [1]) ; X'00' ; BINARY(1), ARRAY
        ARRAY_INTERSECTION(?, [1]) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, ARRAY
        ARRAY_INTERSECTION(?, [1]) ; NULL ; =null
        ARRAY_INTERSECTION(?, [1]) ; [1] ; =[1]
        ARRAY_INTERSECTION(?, [1]) ; c.s ; VARCHAR(10), ARRAY
        ARRAY_INTERSECTION(?, [1]) ; c.n ; NUMBER(5,0), ARRAY
        ARRAY_INTERSECTION(?, [1]) ; c.a ; =[1]
        ARRAY_INTERSECTION(?, [1]) ; c.o ; OBJECT, ARRAY
        ARRAY_INTERSECTION([1], ?) ; 'abc' ; ARRAY, VARCHAR(3)
        ARRAY_INTERSECTION([1], ?) ; NULL::VARCHAR ; ARRAY, VARCHAR(134217728)
        ARRAY_INTERSECTION([1], ?) ; 1 ; ARRAY, NUMBER(1,0)
        ARRAY_INTERSECTION([1], ?) ; 1.5::FLOAT ; ARRAY, FLOAT
        ARRAY_INTERSECTION([1], ?) ; TRUE ; ARRAY, BOOLEAN
        ARRAY_INTERSECTION([1], ?) ; CURRENT_DATE() ; ARRAY, DATE
        ARRAY_INTERSECTION([1], ?) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9)
        ARRAY_INTERSECTION([1], ?) ; X'00' ; ARRAY, BINARY(1)
        ARRAY_INTERSECTION([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, OBJECT
        ARRAY_INTERSECTION([1], ?) ; NULL ; =null
        ARRAY_INTERSECTION([1], ?) ; [1] ; =[1]
        ARRAY_INTERSECTION([1], ?) ; c.s ; ARRAY, VARCHAR(10)
        ARRAY_INTERSECTION([1], ?) ; c.n ; ARRAY, NUMBER(5,0)
        ARRAY_INTERSECTION([1], ?) ; c.a ; =[1]
        ARRAY_INTERSECTION([1], ?) ; c.o ; ARRAY, OBJECT
        ARRAY_INSERT(?, 0, 1) ; 'abc' ; VARCHAR(3), NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; NULL::VARCHAR ; VARCHAR(134217728), NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; 1 ; NUMBER(1,0), NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; 1.5::FLOAT ; FLOAT, NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; TRUE ; BOOLEAN, NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; CURRENT_DATE() ; DATE, NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; X'00' ; BINARY(1), NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; NULL ; =null
        ARRAY_INSERT(?, 0, 1) ; [1] ; =[1,1]
        ARRAY_INSERT(?, 0, 1) ; c.s ; VARCHAR(10), NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; c.n ; NUMBER(5,0), NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT(?, 0, 1) ; c.a ; =[1,1,2]
        ARRAY_INSERT(?, 0, 1) ; c.o ; OBJECT, NUMBER(1,0), NUMBER(1,0)
        ARRAY_INSERT([1], ?, 1) ; NULL::VARCHAR ; =null
        ARRAY_INSERT([1], ?, 1) ; 1 ; =[1,1]
        ARRAY_INSERT([1], ?, 1) ; TRUE ; ARRAY, BOOLEAN, NUMBER(1,0)
        ARRAY_INSERT([1], ?, 1) ; CURRENT_DATE() ; ARRAY, DATE, NUMBER(1,0)
        ARRAY_INSERT([1], ?, 1) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9), NUMBER(1,0)
        ARRAY_INSERT([1], ?, 1) ; X'00' ; ARRAY, BINARY(1), NUMBER(1,0)
        ARRAY_INSERT([1], ?, 1) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, OBJECT, NUMBER(1,0)
        ARRAY_INSERT([1], ?, 1) ; NULL ; =null
        ARRAY_INSERT([1], ?, 1) ; [1] ; ARRAY, ARRAY, NUMBER(1,0)
        ARRAY_INSERT([1], ?, 1) ; c.n ; =[1,1]
        ARRAY_INSERT([1], ?, 1) ; c.a ; ARRAY, ARRAY, NUMBER(1,0)
        ARRAY_INSERT([1], ?, 1) ; c.o ; ARRAY, OBJECT, NUMBER(1,0)
        ARRAY_INSERT([1], 0, ?) ; 'abc' ; =["abc",1]
        ARRAY_INSERT([1], 0, ?) ; NULL::VARCHAR ; =[undefined,1]
        ARRAY_INSERT([1], 0, ?) ; 1 ; =[1,1]
        ARRAY_INSERT([1], 0, ?) ; 1.5::FLOAT ; =[1.500000000000000e+00,1]
        ARRAY_INSERT([1], 0, ?) ; TRUE ; =[true,1]
        ARRAY_INSERT([1], 0, ?) ; '2024-01-02'::DATE ; =["2024-01-02",1]
        ARRAY_INSERT([1], 0, ?) ; X'00' ; =["00",1]
        ARRAY_INSERT([1], 0, ?) ; TO_VARIANT(1) ; =[1,1]
        ARRAY_INSERT([1], 0, ?) ; OBJECT_CONSTRUCT('a', 1) ; =[{"a":1},1]
        ARRAY_INSERT([1], 0, ?) ; NULL ; =[undefined,1]
        ARRAY_INSERT([1], 0, ?) ; [1] ; =[[1],1]
        ARRAY_INSERT([1], 0, ?) ; c.s ; =["abc",1]
        ARRAY_INSERT([1], 0, ?) ; c.n ; =[1,1]
        ARRAY_INSERT([1], 0, ?) ; c.a ; =[[1,2],1]
        ARRAY_INSERT([1], 0, ?) ; c.o ; =[{"k":1},1]
        ARRAY_REMOVE(?, 1) ; 'abc' ; VARCHAR(3), NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; NULL::VARCHAR ; VARCHAR(134217728), NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; 1 ; NUMBER(1,0), NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; 1.5::FLOAT ; FLOAT, NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; TRUE ; BOOLEAN, NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; CURRENT_DATE() ; DATE, NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; X'00' ; BINARY(1), NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; NULL ; =null
        ARRAY_REMOVE(?, 1) ; [1] ; =[]
        ARRAY_REMOVE(?, 1) ; c.s ; VARCHAR(10), NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; c.n ; NUMBER(5,0), NUMBER(1,0)
        ARRAY_REMOVE(?, 1) ; c.a ; =[2]
        ARRAY_REMOVE(?, 1) ; c.o ; OBJECT, NUMBER(1,0)
        ARRAY_REMOVE([1], ?) ; 'abc' ; ARRAY, VARCHAR(3)
        ARRAY_REMOVE([1], ?) ; NULL::VARCHAR ; ARRAY, VARCHAR(134217728)
        ARRAY_REMOVE([1], ?) ; 1 ; =[]
        ARRAY_REMOVE([1], ?) ; 1.5::FLOAT ; =[1]
        ARRAY_REMOVE([1], ?) ; TRUE ; =[1]
        ARRAY_REMOVE([1], ?) ; CURRENT_DATE() ; ARRAY, DATE
        ARRAY_REMOVE([1], ?) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9)
        ARRAY_REMOVE([1], ?) ; X'00' ; ARRAY, BINARY(1)
        ARRAY_REMOVE([1], ?) ; TO_VARIANT(1) ; =[]
        ARRAY_REMOVE([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; =[1]
        ARRAY_REMOVE([1], ?) ; NULL ; =null
        ARRAY_REMOVE([1], ?) ; [1] ; =[1]
        ARRAY_REMOVE([1], ?) ; c.s ; ARRAY, VARCHAR(10)
        ARRAY_REMOVE([1], ?) ; c.n ; =[]
        ARRAY_REMOVE([1], ?) ; c.a ; =[1]
        ARRAY_REMOVE([1], ?) ; c.o ; =[1]
        ARRAY_REMOVE_AT(?, 0) ; 'abc' ; VARCHAR(3), NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; NULL::VARCHAR ; VARCHAR(134217728), NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; 1 ; NUMBER(1,0), NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; 1.5::FLOAT ; FLOAT, NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; TRUE ; BOOLEAN, NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; CURRENT_DATE() ; DATE, NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; X'00' ; BINARY(1), NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; NULL ; =null
        ARRAY_REMOVE_AT(?, 0) ; [1] ; =[]
        ARRAY_REMOVE_AT(?, 0) ; c.s ; VARCHAR(10), NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; c.n ; NUMBER(5,0), NUMBER(1,0)
        ARRAY_REMOVE_AT(?, 0) ; c.a ; =[2]
        ARRAY_REMOVE_AT(?, 0) ; c.o ; OBJECT, NUMBER(1,0)
        ARRAY_REMOVE_AT([1], ?) ; NULL::VARCHAR ; =null
        ARRAY_REMOVE_AT([1], ?) ; 1 ; =[1]
        ARRAY_REMOVE_AT([1], ?) ; 1.5::FLOAT ; =[1]
        ARRAY_REMOVE_AT([1], ?) ; TRUE ; ARRAY, BOOLEAN
        ARRAY_REMOVE_AT([1], ?) ; CURRENT_DATE() ; ARRAY, DATE
        ARRAY_REMOVE_AT([1], ?) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9)
        ARRAY_REMOVE_AT([1], ?) ; X'00' ; ARRAY, BINARY(1)
        ARRAY_REMOVE_AT([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, OBJECT
        ARRAY_REMOVE_AT([1], ?) ; NULL ; =null
        ARRAY_REMOVE_AT([1], ?) ; [1] ; ARRAY, ARRAY
        ARRAY_REMOVE_AT([1], ?) ; c.n ; =[1]
        ARRAY_REMOVE_AT([1], ?) ; c.a ; ARRAY, ARRAY
        ARRAY_REMOVE_AT([1], ?) ; c.o ; ARRAY, OBJECT
        IS_ARRAY(?) ; 'abc' ; VARCHAR(3)
        IS_ARRAY(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        IS_ARRAY(?) ; 1 ; =false
        IS_ARRAY(?) ; 1.5::FLOAT ; =false
        IS_ARRAY(?) ; TRUE ; =false
        IS_ARRAY(?) ; CURRENT_DATE() ; DATE
        IS_ARRAY(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        IS_ARRAY(?) ; X'00' ; BINARY(1)
        IS_ARRAY(?) ; TO_VARIANT(1) ; =false
        IS_ARRAY(?) ; OBJECT_CONSTRUCT('a', 1) ; =false
        IS_ARRAY(?) ; NULL ; =null
        IS_ARRAY(?) ; [1] ; =true
        IS_ARRAY(?) ; c.s ; VARCHAR(10)
        IS_ARRAY(?) ; c.n ; =false
        IS_ARRAY(?) ; c.a ; =true
        IS_ARRAY(?) ; c.o ; =false
        AS_ARRAY(?) ; 'abc' ; VARCHAR(3)
        AS_ARRAY(?) ; NULL::VARCHAR ; VARCHAR(134217728)
        AS_ARRAY(?) ; 1 ; =null
        AS_ARRAY(?) ; 1.5::FLOAT ; =null
        AS_ARRAY(?) ; TRUE ; =null
        AS_ARRAY(?) ; CURRENT_DATE() ; DATE
        AS_ARRAY(?) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9)
        AS_ARRAY(?) ; X'00' ; BINARY(1)
        AS_ARRAY(?) ; TO_VARIANT(1) ; =null
        AS_ARRAY(?) ; OBJECT_CONSTRUCT('a', 1) ; =null
        AS_ARRAY(?) ; NULL ; =null
        AS_ARRAY(?) ; [1] ; =[1]
        AS_ARRAY(?) ; c.s ; VARCHAR(10)
        AS_ARRAY(?) ; c.n ; =null
        AS_ARRAY(?) ; c.a ; =[1,2]
        AS_ARRAY(?) ; c.o ; =null
        ARRAY_GENERATE_RANGE(?, 3) ; NULL::VARCHAR ; =null
        ARRAY_GENERATE_RANGE(?, 3) ; 1 ; =[1,2]
        ARRAY_GENERATE_RANGE(?, 3) ; TRUE ; BOOLEAN, NUMBER(1,0)
        ARRAY_GENERATE_RANGE(?, 3) ; CURRENT_DATE() ; DATE, NUMBER(1,0)
        ARRAY_GENERATE_RANGE(?, 3) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), NUMBER(1,0)
        ARRAY_GENERATE_RANGE(?, 3) ; X'00' ; BINARY(1), NUMBER(1,0)
        ARRAY_GENERATE_RANGE(?, 3) ; OBJECT_CONSTRUCT('a', 1) ; OBJECT, NUMBER(1,0)
        ARRAY_GENERATE_RANGE(?, 3) ; NULL ; =null
        ARRAY_GENERATE_RANGE(?, 3) ; [1] ; ARRAY, NUMBER(1,0)
        ARRAY_GENERATE_RANGE(?, 3) ; c.n ; =[1,2]
        ARRAY_GENERATE_RANGE(?, 3) ; c.a ; ARRAY, NUMBER(1,0)
        ARRAY_GENERATE_RANGE(?, 3) ; c.o ; OBJECT, NUMBER(1,0)
        ARRAY_GENERATE_RANGE(0, ?) ; NULL::VARCHAR ; =null
        ARRAY_GENERATE_RANGE(0, ?) ; 1 ; =[0]
        ARRAY_GENERATE_RANGE(0, ?) ; TRUE ; NUMBER(1,0), BOOLEAN
        ARRAY_GENERATE_RANGE(0, ?) ; CURRENT_DATE() ; NUMBER(1,0), DATE
        ARRAY_GENERATE_RANGE(0, ?) ; CURRENT_TIMESTAMP() ; NUMBER(1,0), TIMESTAMP_LTZ(9)
        ARRAY_GENERATE_RANGE(0, ?) ; X'00' ; NUMBER(1,0), BINARY(1)
        ARRAY_GENERATE_RANGE(0, ?) ; OBJECT_CONSTRUCT('a', 1) ; NUMBER(1,0), OBJECT
        ARRAY_GENERATE_RANGE(0, ?) ; NULL ; =null
        ARRAY_GENERATE_RANGE(0, ?) ; [1] ; NUMBER(1,0), ARRAY
        ARRAY_GENERATE_RANGE(0, ?) ; c.n ; =[0]
        ARRAY_GENERATE_RANGE(0, ?) ; c.a ; NUMBER(1,0), ARRAY
        ARRAY_GENERATE_RANGE(0, ?) ; c.o ; NUMBER(1,0), OBJECT
        ARRAY_CONSTRUCT_COMPACT(?) ; 'abc' ; =["abc"]
        ARRAY_CONSTRUCT_COMPACT(?) ; NULL::VARCHAR ; =[]
        ARRAY_CONSTRUCT_COMPACT(?) ; 1 ; =[1]
        ARRAY_CONSTRUCT_COMPACT(?) ; 1.5::FLOAT ; =[1.500000000000000e+00]
        ARRAY_CONSTRUCT_COMPACT(?) ; TRUE ; =[true]
        ARRAY_CONSTRUCT_COMPACT(?) ; '2024-01-02'::DATE ; =["2024-01-02"]
        ARRAY_CONSTRUCT_COMPACT(?) ; X'00' ; =["00"]
        ARRAY_CONSTRUCT_COMPACT(?) ; TO_VARIANT(1) ; =[1]
        ARRAY_CONSTRUCT_COMPACT(?) ; OBJECT_CONSTRUCT('a', 1) ; =[{"a":1}]
        ARRAY_CONSTRUCT_COMPACT(?) ; NULL ; =[]
        ARRAY_CONSTRUCT_COMPACT(?) ; [1] ; =[[1]]
        ARRAY_CONSTRUCT_COMPACT(?) ; c.s ; =["abc"]
        ARRAY_CONSTRUCT_COMPACT(?) ; c.n ; =[1]
        ARRAY_CONSTRUCT_COMPACT(?) ; c.a ; =[[1,2]]
        ARRAY_CONSTRUCT_COMPACT(?) ; c.o ; =[{"k":1}]
        ARRAY_SORT([1], ?) ; 1 ; =[1]
        ARRAY_SORT([1], ?) ; 1.5::FLOAT ; =[1]
        ARRAY_SORT([1], ?) ; TRUE ; =[1]
        ARRAY_SORT([1], ?) ; CURRENT_DATE() ; ARRAY, DATE
        ARRAY_SORT([1], ?) ; CURRENT_TIMESTAMP() ; ARRAY, TIMESTAMP_LTZ(9)
        ARRAY_SORT([1], ?) ; X'00' ; ARRAY, BINARY(1)
        ARRAY_SORT([1], ?) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, OBJECT
        ARRAY_SORT([1], ?) ; [1] ; ARRAY, ARRAY
        ARRAY_SORT([1], ?) ; c.n ; =[1]
        ARRAY_SORT([1], ?) ; c.a ; ARRAY, ARRAY
        ARRAY_SORT([1], ?) ; c.o ; ARRAY, OBJECT
        ARRAY_SORT([1], TRUE, ?) ; 1 ; =[1]
        ARRAY_SORT([1], TRUE, ?) ; 1.5::FLOAT ; =[1]
        ARRAY_SORT([1], TRUE, ?) ; TRUE ; =[1]
        ARRAY_SORT([1], TRUE, ?) ; CURRENT_DATE() ; ARRAY, BOOLEAN, DATE
        ARRAY_SORT([1], TRUE, ?) ; CURRENT_TIMESTAMP() ; ARRAY, BOOLEAN, TIMESTAMP_LTZ(9)
        ARRAY_SORT([1], TRUE, ?) ; X'00' ; ARRAY, BOOLEAN, BINARY(1)
        ARRAY_SORT([1], TRUE, ?) ; OBJECT_CONSTRUCT('a', 1) ; ARRAY, BOOLEAN, OBJECT
        ARRAY_SORT([1], TRUE, ?) ; [1] ; ARRAY, BOOLEAN, ARRAY
        ARRAY_SORT([1], TRUE, ?) ; c.n ; =[1]
        ARRAY_SORT([1], TRUE, ?) ; c.a ; ARRAY, BOOLEAN, ARRAY
        ARRAY_SORT([1], TRUE, ?) ; c.o ; ARRAY, BOOLEAN, OBJECT
        ARRAY_TO_STRING(?, NULL) ; 'abc' ; VARCHAR(3), NULL
        ARRAY_TO_STRING(?, NULL) ; NULL::VARCHAR ; VARCHAR(134217728), NULL
        ARRAY_TO_STRING(?, NULL) ; 1 ; =null
        ARRAY_TO_STRING(?, NULL) ; 1.5::FLOAT ; =null
        ARRAY_TO_STRING(?, NULL) ; TRUE ; =null
        ARRAY_TO_STRING(?, NULL) ; CURRENT_DATE() ; DATE, NULL
        ARRAY_TO_STRING(?, NULL) ; CURRENT_TIMESTAMP() ; TIMESTAMP_LTZ(9), NULL
        ARRAY_TO_STRING(?, NULL) ; X'00' ; BINARY(1), NULL
        ARRAY_TO_STRING(?, NULL) ; TO_VARIANT(1) ; =null
        ARRAY_TO_STRING(?, NULL) ; OBJECT_CONSTRUCT('a', 1) ; =null
        ARRAY_TO_STRING(?, NULL) ; NULL ; =null
        ARRAY_TO_STRING(?, NULL) ; [1] ; =null
        ARRAY_TO_STRING(?, NULL) ; c.s ; VARCHAR(10), NULL
        ARRAY_TO_STRING(?, NULL) ; c.n ; =null
        ARRAY_TO_STRING(?, NULL) ; c.a ; =null
        ARRAY_TO_STRING(?, NULL) ; c.o ; =null
        """;

    @Override
    protected void setupTest() {
        engine.execute("CREATE OR REPLACE TABLE c (s VARCHAR(10), n NUMBER(5,0), a ARRAY, v VARIANT, o OBJECT)");
        engine.execute("INSERT INTO c SELECT 'abc', 1, [1, 2], TO_VARIANT('x'), OBJECT_CONSTRUCT('k', 1)");
    }

    private String refusal(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }, sql).getMessage();
    }

    @Test
    public void eachPositionRefusesTheFamiliesItDoesNotTake() {
        for (final String line : CELLS.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            final String[] cell = line.trim().split(" ; ", 3);
            final String call = cell[0].replace("?", cell[1]);
            final String sql = "SELECT " + call + (cell[1].startsWith("c.") ? " FROM c" : "");
            if (cell[2].startsWith("=")) {
                final ResultSet rs = engine.executeQuery(sql);
                assertTrue(rs.next(), sql);
                assertEquals(cell[2].substring(1), String.valueOf(rs.getValue(0)), sql);
            } else {
                final String function = cell[0].substring(0, cell[0].indexOf('('));
                assertEquals("SQL compilation error: error line 1 at position 7\nInvalid argument types for function '"
                    + function + "': (" + cell[2] + ")", refusal(sql), sql);
            }
        }
    }
}

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
 * Which of two overloads a call runs when one of its arguments prefers the first and another the second: the
 * one whose conversions weigh least over all the arguments together, not the one the first argument prefers.
 * A NUMBER read as a FLOAT weighs less than one read as a BOOLEAN, a VARIANT or a text; a text read as a
 * BOOLEAN less than one read as a FLOAT, and that less than one read as a NUMBER; a FLOAT read as a NUMBER
 * weighs most of all. Overloads that weigh alike - {@code f(a VARCHAR, b VARCHAR)} and {@code f(a FLOAT, b
 * NUMBER)} for {@code (1, 'x')} - are told apart by how the call is written: by position the first in the
 * order of the signatures runs, by name the call is refused as ambiguous. Every answer is live-verified.
 */
public class OverloadConversionWeightTest extends MeasuredCellsTestSupport {

    @Test
    public void pairsWhoseArgumentsDisagree() {
        assertAnswer(
            "Function K1 successfully created.",
            "CREATE OR REPLACE FUNCTION k1(a FLOAT, b VARCHAR) RETURNS VARCHAR AS '''float-var'''");
        assertAnswer(
            "Function K1 successfully created.",
            "CREATE OR REPLACE FUNCTION k1(a VARCHAR, b NUMBER) RETURNS VARCHAR AS '''var-num'''");
        assertAnswer(
            "float-var",
            "SELECT k1(a => 'x', b => 'y')");
        assertAnswer(
            "float-var",
            "SELECT k1('x', 'y')");
        assertAnswer(
            "Function K2 successfully created.",
            "CREATE OR REPLACE FUNCTION k2(a NUMBER, b FLOAT) RETURNS VARCHAR AS '''num-float'''");
        assertAnswer(
            "Function K2 successfully created.",
            "CREATE OR REPLACE FUNCTION k2(a BOOLEAN, b NUMBER) RETURNS VARCHAR AS '''bool-num'''");
        assertAnswer(
            "num-float",
            "SELECT k2(a => 1, b => 2)");
        assertAnswer(
            "num-float",
            "SELECT k2(1, 2)");
        assertAnswer(
            "Function K3 successfully created.",
            "CREATE OR REPLACE FUNCTION k3(a VARCHAR, b FLOAT) RETURNS VARCHAR AS '''var-float'''");
        assertAnswer(
            "Function K3 successfully created.",
            "CREATE OR REPLACE FUNCTION k3(a VARCHAR, b BOOLEAN) RETURNS VARCHAR AS '''var-bool'''");
        assertAnswer(
            "var-bool",
            "SELECT k3(a => 'x', b => 'y')");
        assertAnswer(
            "var-bool",
            "SELECT k3('x', 'y')");
        assertAnswer(
            "var-float",
            "SELECT k3(a => 'x', b => 1)");
        assertAnswer(
            "var-float",
            "SELECT k3('x', 1)");
        assertAnswer(
            "Function K4 successfully created.",
            "CREATE OR REPLACE FUNCTION k4(a BOOLEAN, b VARCHAR) RETURNS VARCHAR AS '''bool-var'''");
        assertAnswer(
            "Function K4 successfully created.",
            "CREATE OR REPLACE FUNCTION k4(a FLOAT, b NUMBER) RETURNS VARCHAR AS '''float-num'''");
        assertAnswer(
            "bool-var",
            "SELECT k4(a => 1, b => 'x')");
        assertAnswer(
            "bool-var",
            "SELECT k4(1, 'x')");
        assertAnswer(
            "Function K5 successfully created.",
            "CREATE OR REPLACE FUNCTION k5(a VARIANT, b VARCHAR) RETURNS VARCHAR AS '''variant-var'''");
        assertAnswer(
            "Function K5 successfully created.",
            "CREATE OR REPLACE FUNCTION k5(a FLOAT, b NUMBER) RETURNS VARCHAR AS '''float-num'''");
        assertAnswer(
            "variant-var",
            "SELECT k5(a => 1, b => 'x')");
        assertAnswer(
            "variant-var",
            "SELECT k5(1, 'x')");
        assertAnswer(
            "Function K6 successfully created.",
            "CREATE OR REPLACE FUNCTION k6(a VARCHAR, b VARCHAR) RETURNS VARCHAR AS '''var-var'''");
        assertAnswer(
            "Function K6 successfully created.",
            "CREATE OR REPLACE FUNCTION k6(a FLOAT, b NUMBER) RETURNS VARCHAR AS '''float-num'''");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of K6 are resolved in prepare state.",
            "SELECT k6(a => 1, b => 'x')");
        assertAnswer(
            "float-num",
            "SELECT k6(1, 'x')");
        assertAnswer(
            "Function K7 successfully created.",
            "CREATE OR REPLACE FUNCTION k7(a VARIANT, b NUMBER) RETURNS VARCHAR AS '''variant-num'''");
        assertAnswer(
            "Function K7 successfully created.",
            "CREATE OR REPLACE FUNCTION k7(a FLOAT, b FLOAT) RETURNS VARCHAR AS '''float-float'''");
        assertAnswer(
            "float-float",
            "SELECT k7(a => 'x', b => 1)");
        assertAnswer(
            "float-float",
            "SELECT k7('x', 1)");
        assertAnswer(
            "Function K8 successfully created.",
            "CREATE OR REPLACE FUNCTION k8(a BOOLEAN, b NUMBER) RETURNS VARCHAR AS '''bool-num'''");
        assertAnswer(
            "Function K8 successfully created.",
            "CREATE OR REPLACE FUNCTION k8(a FLOAT, b FLOAT) RETURNS VARCHAR AS '''float-float'''");
        assertAnswer(
            "bool-num",
            "SELECT k8(a => 'x', b => 1)");
        assertAnswer(
            "bool-num",
            "SELECT k8('x', 1)");
        assertAnswer(
            "Function K9 successfully created.",
            "CREATE OR REPLACE FUNCTION k9(a BOOLEAN, b VARCHAR) RETURNS VARCHAR AS '''bool-var'''");
        assertAnswer(
            "Function K9 successfully created.",
            "CREATE OR REPLACE FUNCTION k9(a FLOAT, b NUMBER) RETURNS VARCHAR AS '''float-num'''");
        assertAnswer(
            "bool-var",
            "SELECT k9(a => 1.5::FLOAT, b => 'x')");
        assertAnswer(
            "bool-var",
            "SELECT k9(1.5::FLOAT, 'x')");
        assertAnswer(
            "Function K10 successfully created.",
            "CREATE OR REPLACE FUNCTION k10(a VARCHAR, b VARCHAR) RETURNS VARCHAR AS '''var-var'''");
        assertAnswer(
            "Function K10 successfully created.",
            "CREATE OR REPLACE FUNCTION k10(a FLOAT, b NUMBER) RETURNS VARCHAR AS '''float-num'''");
        assertAnswer(
            "float-num",
            "SELECT k10(a => 1.5::FLOAT, b => 'x')");
        assertAnswer(
            "float-num",
            "SELECT k10(1.5::FLOAT, 'x')");
        assertAnswer(
            "Function K11 successfully created.",
            "CREATE OR REPLACE FUNCTION k11(a NUMBER, b VARCHAR) RETURNS VARCHAR AS '''num-var'''");
        assertAnswer(
            "Function K11 successfully created.",
            "CREATE OR REPLACE FUNCTION k11(a FLOAT, b NUMBER) RETURNS VARCHAR AS '''float-num'''");
        assertAnswer(
            "float-num",
            "SELECT k11(a => 1.5::FLOAT, b => 'x')");
        assertAnswer(
            "float-num",
            "SELECT k11(1.5::FLOAT, 'x')");
    }

    @Test
    public void booleansDatesVariantsAndThreeArguments() {
        assertAnswer(
            "Function K12 successfully created.",
            "CREATE OR REPLACE FUNCTION k12(a VARCHAR, b VARCHAR) RETURNS VARCHAR AS '''var-var'''");
        assertAnswer(
            "Function K12 successfully created.",
            "CREATE OR REPLACE FUNCTION k12(a BOOLEAN, b NUMBER) RETURNS VARCHAR AS '''bool-num'''");
        assertAnswer(
            "var-var",
            "SELECT k12(a => TRUE, b => 'x')");
        assertAnswer(
            "var-var",
            "SELECT k12(TRUE, 'x')");
        assertAnswer(
            "Function K13 successfully created.",
            "CREATE OR REPLACE FUNCTION k13(a FLOAT, b VARCHAR) RETURNS VARCHAR AS '''float-var'''");
        assertAnswer(
            "Function K13 successfully created.",
            "CREATE OR REPLACE FUNCTION k13(a BOOLEAN, b NUMBER) RETURNS VARCHAR AS '''bool-num'''");
        assertAnswer(
            "bool-num",
            "SELECT k13(a => TRUE, b => 'x')");
        assertAnswer(
            "bool-num",
            "SELECT k13(TRUE, 'x')");
        assertAnswer(
            "Function K14 successfully created.",
            "CREATE OR REPLACE FUNCTION k14(a TIMESTAMP_NTZ, b VARCHAR) RETURNS VARCHAR AS '''ts-var'''");
        assertAnswer(
            "Function K14 successfully created.",
            "CREATE OR REPLACE FUNCTION k14(a DATE, b NUMBER) RETURNS VARCHAR AS '''date-num'''");
        assertAnswer(
            "ts-var",
            "SELECT k14(a => '2024-01-01'::DATE, b => 'x')");
        assertAnswer(
            "ts-var",
            "SELECT k14('2024-01-01'::DATE, 'x')");
        assertAnswer(
            "Function K15 successfully created.",
            "CREATE OR REPLACE FUNCTION k15(a VARCHAR, b VARCHAR) RETURNS VARCHAR AS '''var-var'''");
        assertAnswer(
            "Function K15 successfully created.",
            "CREATE OR REPLACE FUNCTION k15(a DATE, b NUMBER) RETURNS VARCHAR AS '''date-num'''");
        assertAnswer(
            "date-num",
            "SELECT k15(a => '2024-01-01'::DATE, b => 'x')");
        assertAnswer(
            "date-num",
            "SELECT k15('2024-01-01'::DATE, 'x')");
        assertAnswer(
            "Function K16 successfully created.",
            "CREATE OR REPLACE FUNCTION k16(a DATE, b NUMBER) RETURNS VARCHAR AS '''date-num'''");
        assertAnswer(
            "Function K16 successfully created.",
            "CREATE OR REPLACE FUNCTION k16(a VARCHAR, b FLOAT) RETURNS VARCHAR AS '''var-float'''");
        assertAnswer(
            "var-float",
            "SELECT k16(a => '2024-01-01', b => 1)");
        assertAnswer(
            "var-float",
            "SELECT k16('2024-01-01', 1)");
        assertAnswer(
            "Function K17 successfully created.",
            "CREATE OR REPLACE FUNCTION k17(a VARIANT, b VARCHAR) RETURNS VARCHAR AS '''variant-var'''");
        assertAnswer(
            "Function K17 successfully created.",
            "CREATE OR REPLACE FUNCTION k17(a FLOAT, b NUMBER) RETURNS VARCHAR AS '''float-num'''");
        assertAnswer(
            "variant-var",
            "SELECT k17(a => 1.5::FLOAT, b => 'x')");
        assertAnswer(
            "variant-var",
            "SELECT k17(1.5::FLOAT, 'x')");
        assertAnswer(
            "Function K18 successfully created.",
            "CREATE OR REPLACE FUNCTION k18(a VARIANT, b VARCHAR) RETURNS VARCHAR AS '''variant-var'''");
        assertAnswer(
            "Function K18 successfully created.",
            "CREATE OR REPLACE FUNCTION k18(a BOOLEAN, b NUMBER) RETURNS VARCHAR AS '''bool-num'''");
        assertAnswer(
            "variant-var",
            "SELECT k18(a => TRUE, b => 'x')");
        assertAnswer(
            "variant-var",
            "SELECT k18(TRUE, 'x')");
        assertAnswer(
            "Function K19 successfully created.",
            "CREATE OR REPLACE FUNCTION k19(a NUMBER, b NUMBER) RETURNS VARCHAR AS '''num-num'''");
        assertAnswer(
            "Function K19 successfully created.",
            "CREATE OR REPLACE FUNCTION k19(a FLOAT, b FLOAT) RETURNS VARCHAR AS '''float-float'''");
        assertAnswer(
            "float-float",
            "SELECT k19(a => 1, b => 1.5::FLOAT)");
        assertAnswer(
            "float-float",
            "SELECT k19(1, 1.5::FLOAT)");
        assertAnswer(
            "float-float",
            "SELECT k19(a => 1.5::FLOAT, b => 1)");
        assertAnswer(
            "float-float",
            "SELECT k19(1.5::FLOAT, 1)");
        assertAnswer(
            "REFUSED SQL compilation error: | More than one function of K19 are resolved in prepare state.",
            "SELECT k19(a => 'x', b => 1)");
        assertAnswer(
            "num-num",
            "SELECT k19('x', 1)");
        assertAnswer(
            "Function K20 successfully created.",
            "CREATE OR REPLACE FUNCTION k20(a NUMBER, b VARCHAR, c VARCHAR) RETURNS VARCHAR AS '''nvv'''");
        assertAnswer(
            "Function K20 successfully created.",
            "CREATE OR REPLACE FUNCTION k20(a VARCHAR, b NUMBER, c NUMBER) RETURNS VARCHAR AS '''vnn'''");
        assertAnswer(
            "vnn",
            "SELECT k20(a => 1, b => 2, c => 3)");
        assertAnswer(
            "vnn",
            "SELECT k20(1, 2, 3)");
        assertAnswer(
            "nvv",
            "SELECT k20(a => 'x', b => 'y', c => 'z')");
        assertAnswer(
            "nvv",
            "SELECT k20('x', 'y', 'z')");
        assertAnswer(
            "nvv",
            "SELECT k20(a => 1, b => 'y', c => 3)");
        assertAnswer(
            "nvv",
            "SELECT k20(1, 'y', 3)");
    }
}

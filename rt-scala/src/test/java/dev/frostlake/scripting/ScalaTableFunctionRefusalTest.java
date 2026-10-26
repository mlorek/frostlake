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

package dev.frostlake.scripting;

import dev.frostlake.query.MeasuredCellsTestSupport;

import org.junit.jupiter.api.Test;

/**
 * A Scala table function is held to a Java one's handler rules - a static getOutputClass, an endPartition of an
 * instance - and one that keeps them is refused for its language, Table functions not supported for language
 * 'SCALA', so none is ever created (all live-verified).
 */
public class ScalaTableFunctionRefusalTest extends MeasuredCellsTestSupport {

    @Test
    public void theJavaRulesFirst() {
        assertAnswer("REFUSED User Error Report:  | Java Stack Trace: | java.lang.NoSuchMethodError: getOutputClass | "
                + " | The getOutputClass() method must be static and return a java.lang.Class in function SC679 with "
                + "handler H679",
            "CREATE OR REPLACE FUNCTION sc679(k INT) RETURNS TABLE (x INT, y INT) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H679' AS $$\nclass H679 {\n  def getOutputClass(): Class[_] = "
                + "classOf[java.awt.Point]\n  def process(k: Int): java.util.stream.Stream[java.awt.Point] = "
                + "java.util.stream.Stream.builder[java.awt.Point]().add(new java.awt.Point(k, k + 1)).build()\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SC679", "SELECT * FROM TABLE(sc679(1))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SC679",
            "SELECT t.a, f.x, f.y FROM (SELECT 1 AS a UNION ALL SELECT 2) t, TABLE(sc679(t.a)) f ORDER BY 1");
        assertAnswer("REFUSED User Error Report:  | Java Stack Trace: | java.lang.NoSuchMethodError: getOutputClass | "
                + " | The getOutputClass() method must be static and return a java.lang.Class in function SC679B with "
                + "handler H679b",
            "CREATE OR REPLACE FUNCTION sc679b(k INT) RETURNS TABLE (n INT) LANGUAGE SCALA RUNTIME_VERSION = '2.12' "
                + "HANDLER = 'H679b' AS $$\nclass R679(var n: Int)\nclass H679b {\n  def getOutputClass(): Class[_] "
                + "= classOf[R679]\n  def process(k: Int): java.util.stream.Stream[R679] =\n    "
                + "java.util.stream.Stream.builder[R679]().add(new R679(k)).build()\n}\n$$");
        assertAnswer("REFUSED Optional endPartition() method must be public, non-static, and return a "
                + "java.util.stream.Stream in function SC679C with handler H679c",
            "CREATE OR REPLACE FUNCTION sc679c(k INT) RETURNS TABLE (x INT, y INT) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H679c' AS $$\nobject H679c {\n  def getOutputClass(): Class[_] = "
                + "classOf[java.awt.Point]\n  def process(k: Int): java.util.stream.Stream[java.awt.Point] = "
                + "java.util.stream.Stream.builder[java.awt.Point]().add(new java.awt.Point(k, 0)).build()\n  def "
                + "endPartition(): java.util.stream.Stream[java.awt.Point] = "
                + "java.util.stream.Stream.builder[java.awt.Point]().add(new java.awt.Point(-1, -1)).build()\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SC679C",
            "SELECT * FROM TABLE(sc679c(5)) ORDER BY 1");
        assertAnswer("REFUSED User Error Report:  | Java Stack Trace: | java.lang.NoSuchMethodError: getOutputClass | "
                + " | The getOutputClass() method must be static and return a java.lang.Class in function SC679D with "
                + "handler H679d",
            "CREATE OR REPLACE FUNCTION sc679d(k INT) RETURNS TABLE (x INT, y INT) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H679d' AS $$\nclass H679d {\n  def getOutputClass(): Class[_] = "
                + "classOf[java.awt.Point]\n  def process(k: Int): java.util.stream.Stream[java.awt.Point] = "
                + "java.util.stream.Stream.builder[java.awt.Point]().add(new java.awt.Point(k, 1)).build()\n  def "
                + "process(k: String): java.util.stream.Stream[java.awt.Point] = "
                + "java.util.stream.Stream.builder[java.awt.Point]().add(new java.awt.Point(0, 2)).build()\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SC679D",
            "SELECT * FROM TABLE(sc679d(1))");
    }

    @Test
    public void thenTheLanguage() {
        assertAnswer("REFUSED Table functions not supported for language 'SCALA'.",
            "CREATE OR REPLACE FUNCTION sc680(k INT) RETURNS TABLE (x INT, y INT) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H680' AS $$\nobject H680 {\n  def getOutputClass(): Class[_] = "
                + "classOf[java.awt.Point]\n}\nclass H680 {\n  def process(k: Int): "
                + "java.util.stream.Stream[java.awt.Point] = "
                + "java.util.stream.Stream.builder[java.awt.Point]().add(new java.awt.Point(k, k + 1)).build()\n  "
                + "def endPartition(): java.util.stream.Stream[java.awt.Point] = "
                + "java.util.stream.Stream.builder[java.awt.Point]().add(new java.awt.Point(-1, -1)).build()\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SC680",
            "SELECT * FROM TABLE(sc680(1)) ORDER BY 1");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SC680",
            "SELECT t.a, f.x, f.y FROM (SELECT 1 AS a UNION ALL SELECT 2) t, TABLE(sc680(t.a)) f ORDER BY 1, 2");
        assertAnswer("REFUSED Table functions not supported for language 'SCALA'.",
            "CREATE OR REPLACE FUNCTION sc680b(k INT) RETURNS TABLE (x INT, y INT) LANGUAGE SCALA RUNTIME_VERSION = "
                + "'2.12' HANDLER = 'H680b' AS $$\nobject H680b {\n  def getOutputClass(): Class[_] = "
                + "classOf[java.awt.Point]\n}\nclass H680b {\n  def process(k: Int): "
                + "java.util.stream.Stream[java.awt.Point] = "
                + "java.util.stream.Stream.builder[java.awt.Point]().add(new java.awt.Point(k, 2 * k)).build()\n}\n$$");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SC680B",
            "SELECT * FROM TABLE(sc680b(3))");
        assertAnswer("REFUSED SQL compilation error: | Unknown table function SC680B",
            "SELECT f.x, f.y FROM (SELECT 1 AS a UNION ALL SELECT 2) t, TABLE(sc680b(t.a) OVER (PARTITION BY t.a)) f "
                + "ORDER BY 1");
    }
}

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
 * A Python function's HANDLER is judged against the signature when CREATE runs the body — before the signature's
 * own repeated-name rule. A scalar handler must take the declared number of positional arguments (a
 * {@code *args} handler takes any), a table function's handler class must have a {@code process(self, ...)} and
 * may have an {@code __init__(self)}, and a dotted handler names a module to import. Every cell is live-verified.
 */
public class PythonHandlerSignatureTest extends BaseDatabaseTest {

    private static final String HEAD = " LANGUAGE PYTHON RUNTIME_VERSION='3.11' HANDLER='";

    private String refusalOf(final String sql) {
        return assertThrows(RuntimeException.class, new Executable() {
            @Override
            public void execute() {
                engine.execute(sql);
            }
        }).getMessage();
    }

    private static String scalar(final String signature, final String handler, final String body) {
        return "CREATE FUNCTION " + signature + " RETURNS INT" + HEAD + handler + "' AS $$\n" + body + "\n$$";
    }

    private static String table(final String signature, final String handler, final String body) {
        return "CREATE FUNCTION " + signature + " RETURNS TABLE (x INT)" + HEAD + handler + "' AS $$\n" + body + "\n$$";
    }

    @Test
    public void theHandlersArityIsJudgedBeforeARepeatedArgumentName() {
        assertEquals("Python function is defined with 1 arguments, but UDF definition contains 2 arguments in "
            + "function PA1 with handler h",
            refusalOf(scalar("pa1(a INT, a INT)", "h", "def h(a):\n    return 1")));
        assertEquals("Argument 'A' repeats in the function signature.",
            refusalOf(scalar("pa2(a INT, a INT)", "h", "def h(a, b):\n    return 1")));
        assertEquals("Could not find handler in function PA11 with handler zz",
            refusalOf(scalar("pa11(a INT, a INT)", "zz", "def h(a):\n    return 1")));
        assertEquals("Argument 'A' repeats in the function signature.",
            refusalOf(scalar("pb20(a INT, a INT)", "h", "def h(*args):\n    return 1")));
    }

    @Test
    public void positionalArgumentsCountAndVarargsTakeAny() {
        assertEquals("Python function is defined with 2 arguments, but UDF definition contains 1 arguments in "
            + "function PA7 with handler h",
            refusalOf(scalar("pa7(a INT)", "h", "def h(a, b=1):\n    return 1")));
        assertEquals("Python function is defined with 2 arguments, but UDF definition contains 1 arguments in "
            + "function PA6 with handler h",
            refusalOf(scalar("pa6(a INT)", "h", "h = lambda a, b: 1")));
        assertEquals("Python function is defined with 2 arguments, but UDF definition contains 1 arguments in "
            + "function PB18 with handler h",
            refusalOf(scalar("pb18(a INT)", "h", "class K:\n    def m(self, a):\n        return 1\nh = K().m")));
        engine.execute(scalar("pa8(a INT)", "h", "def h(*args):\n    return 1"));
        engine.execute(scalar("pa27(a INT)", "h", "def h(a, *, b):\n    return 1"));
        engine.execute(scalar("pa28(a INT)", "h", "def h(a, **kw):\n    return 1"));
        engine.execute(scalar("pa29(a INT, b INT, c INT)", "h", "def h(a, b=1, *args):\n    return 1"));
        assertEquals(1L, ((Number) engine.executeQuery("SELECT pa8(5)").getRows().get(0).getValue(0)).longValue());
    }

    @Test
    public void aHandlerThatIsNoFunctionIsRefusedByWhatPythonSays() {
        assertEquals("Python Interpreter Error:\nAttributeError: 'int' object has no attribute '__code__' in "
            + "function PA23 with handler h",
            refusalOf(scalar("pa23(a INT)", "h", "def h(a):\n    return 1\nh = 5")));
        assertEquals("Python Interpreter Error:\nAttributeError: type object 'h' has no attribute '__code__' in "
            + "function PA30 with handler h",
            refusalOf(scalar("pa30(a INT)", "h", "class h:\n    def __init__(self, a):\n        pass")));
        assertEquals("Python Interpreter Error:\nAttributeError: 'builtin_function_or_method' object has no "
            + "attribute '__code__' in function PB1 with handler math.sqrt",
            refusalOf(scalar("pb1(a INT)", "math.sqrt", "def h(a):\n    return 1")));
        assertEquals("Python Interpreter Error:\nModuleNotFoundError: No module named 'C' in function PA5 with "
            + "handler C.h",
            refusalOf(scalar("pa5(a INT)", "C.h", "class C:\n    @staticmethod\n    def h(a, b):\n        return 1")));
        assertEquals("Could not find handler in function PB3",
            refusalOf(scalar("pb3(a INT)", "", "def h(a):\n    return 1")));
    }

    @Test
    public void aTableFunctionsProcessTakesSelfAndTheArguments() {
        assertEquals("UDF definition contains 2 arguments, so the process method should be defined with 3 arguments, "
            + "including \"self\", but found 2 arguments in function PA9 with handler C",
            refusalOf(table("pa9(a INT, b INT)", "C", "class C:\n    def process(self, a):\n        yield (a,)")));
        assertEquals("UDF definition contains 1 arguments, so the process method should be defined with 2 arguments, "
            + "including \"self\", but found 3 arguments in function PB10 with handler C",
            refusalOf(table("pb10(a INT)", "C", "class C:\n    def process(self, a, b=1):\n        yield (1,)")));
        assertEquals("process should be defined with only one argument \"self\", but found 2 arguments in function "
            + "PA17 with handler C",
            refusalOf("CREATE FUNCTION pa17() RETURNS TABLE (x INT, x INT)" + HEAD + "C' AS $$\nclass C:\n"
                + "    def process(self, a):\n        yield (1, 2)\n$$"));
        assertEquals("process should have \"self\" as the first positional argument, but found argument a instead "
            + "in function PB7 with handler C",
            refusalOf(table("pb7(a INT)", "C", "class C:\n    @staticmethod\n    def process(a):\n        yield (1,)")));
        assertEquals("__init__ should be defined with only one argument \"self\", but found 2 arguments in function "
            + "PB11 with handler C",
            refusalOf(table("pb11(a INT, b INT)", "C", "class C:\n    def __init__(self, x):\n        pass\n"
                + "    def process(self, a, b):\n        yield (1,)")));
        engine.execute(table("pb5(a INT)", "C", "class C:\n    def process(self, *args):\n        yield (1,)"));
        engine.execute(table("pb16(a INT)", "C", "class C:\n    def process(self, a, **kw):\n        yield (1,)"));
        engine.execute(table("pb15()", "C", "class C:\n    def process(self):\n        yield (1,)"));
    }

    @Test
    public void aDottedHandlerImportsItsModuleByNameAndAnyFailureIsRefused() {
        assertEquals("Python Interpreter Error:\nValueError: Empty module name in function Y1 with handler .h",
            refusalOf(scalar("y1(a INT)", ".h", "def h(a):\n    return 1")));
        assertEquals("Python Interpreter Error:\nModuleNotFoundError: No module named '.' in function Y2 with handler ..h",
            refusalOf(scalar("y2(a INT)", "..h", "def h(a):\n    return 1")));
        assertEquals("Python Interpreter Error:\nValueError: Empty module name in function Y3 with handler .",
            refusalOf(scalar("y3(a INT)", ".", "def h(a):\n    return 1")));
        assertEquals("Python Interpreter Error:\nAttributeError: module 'json' has no attribute 'nosuch' in function Y4 "
            + "with handler json.nosuch", refusalOf(scalar("y4(a INT)", "json.nosuch", "def h(a):\n    return 1")));
        assertEquals("Python Interpreter Error:\nModuleNotFoundError: No module named 'a' in function Y6 with handler "
            + "a..b", refusalOf(scalar("y6(a INT)", "a..b", "def h(a):\n    return 1")));
        assertEquals("Python Interpreter Error:\nModuleNotFoundError: No module named 'h' in function Y13 with handler "
            + "h.x", refusalOf(scalar("y13(a INT)", "h.x", "def h(a):\n    return 1")));
        assertEquals("Python Interpreter Error:\nModuleNotFoundError: No module named 'json.dumps' in function Z17 with "
            + "handler json.dumps.x", refusalOf(scalar("z17(a INT)", "json.dumps.x", "x = 1")));
        assertEquals("Python Interpreter Error:\nValueError: Empty module name in function Y16 with handler .C",
            refusalOf(table("y16(a INT)", ".C", "class C:\n    def process(self, a):\n        yield (1,)")));
        assertEquals("Could not find handler in function Y14 with handler  h",
            refusalOf(scalar("y14(a INT)", " h", "def h(a):\n    return 1")));
    }

    @Test
    public void aDottedHandlerIsJudgedAndCalledThroughItsModule() {
        assertEquals("Python function is defined with 2 arguments, but UDF definition contains 1 arguments in function "
            + "Y11 with handler string.capwords",
            refusalOf("CREATE FUNCTION y11(a VARCHAR) RETURNS VARCHAR" + HEAD + "string.capwords' AS $$\nx = 1\n$$"));
        engine.execute("CREATE FUNCTION y9(a INT) RETURNS VARCHAR" + HEAD + "json.dumps' AS $$\nx = 1\n$$");
        assertEquals("5", String.valueOf(engine.executeQuery("SELECT y9(5)").getRows().get(0).getValue(0)));
        engine.execute("CREATE FUNCTION y10(a VARCHAR) RETURNS VARCHAR" + HEAD + "json.loads' AS $$\nx = 1\n$$");
        assertEquals("q", String.valueOf(engine.executeQuery("SELECT y10('\"q\"')").getRows().get(0).getValue(0)));
        engine.execute("CREATE FUNCTION y12(a VARCHAR) RETURNS VARCHAR" + HEAD + "os.path.basename' AS $$\nx = 1\n$$");
        assertEquals("c.txt",
            String.valueOf(engine.executeQuery("SELECT y12('/a/b/c.txt')").getRows().get(0).getValue(0)));
    }

    @Test
    public void aProcessMethodWithNoPositionalParameterIsStatic() {
        assertEquals("The process method cannot be static. Missing \"self\" argument in function Y17 with handler C",
            refusalOf(table("y17()", "C", "class C:\n    @staticmethod\n    def process():\n        yield (1,)")));
        assertEquals("The process method cannot be static. Missing \"self\" argument in function Y18 with handler C",
            refusalOf(table("y18(a INT)", "C", "class C:\n    @staticmethod\n    def process():\n        yield (1,)")));
        assertEquals("The process method cannot be static. Missing \"self\" argument in function Y19 with handler C",
            refusalOf(table("y19()", "C", "class C:\n    def process(*args):\n        yield (1,)")));
        assertEquals("The process method cannot be static. Missing \"self\" argument in function Y21 with handler C",
            refusalOf(table("y21(a INT)", "C", "class C:\n    def process(*, a):\n        yield (1,)")));
        assertEquals("process should have \"self\" as the first positional argument, but found argument cls instead in "
            + "function Z15 with handler C",
            refusalOf(table("z15(a INT)", "C", "class C:\n    @classmethod\n    def process(cls, a):\n        yield (1,)")));
        engine.execute(table("y22(a INT)", "C", "class C:\n    def process(self, *args):\n        yield (1,)"));
        assertEquals(1L, ((Number) engine.executeQuery("SELECT * FROM TABLE(y22(1))").getRows().get(0).getValue(0))
            .longValue());
    }

    @Test
    public void anInitWithCodeIsJudgedBeforeProcess() {
        assertEquals("The __init__ method cannot be static. Missing \"self\" argument in function Y24 with handler C",
            refusalOf(table("y24()", "C", "class C:\n    def __init__(*args):\n        pass\n"
                + "    def process(self):\n        yield (1,)")));
        assertEquals("The process method cannot be static. Missing \"self\" argument in function Y25 with handler C",
            refusalOf(table("y25()", "C", "class C:\n    def __init__(self):\n        pass\n"
                + "    @staticmethod\n    def process():\n        yield (1,)")));
        assertEquals("__init__ should be defined with only one argument \"self\", but found 2 arguments in function "
            + "Y26 with handler C", refusalOf(table("y26()", "C", "class C:\n    def __init__(self, a):\n        pass\n"
                + "    @staticmethod\n    def process():\n        yield (1,)")));
        assertEquals("__init__ definition should not include keyword arguments, but found 1 arguments in function Z1 "
            + "with handler C", refusalOf(table("z1()", "C", "class C:\n    def __init__(self, a, *, k=1):\n        pass\n"
                + "    def process(self):\n        yield (1,)")));
        assertEquals("__init__ should be defined with only one argument \"self\", but found 2 arguments in function "
            + "Z14 with handler C", refusalOf(table("z14()", "C", "class B:\n    def __init__(self, a):\n        pass\n"
                + "class C(B):\n    def process(self):\n        yield (1,)")));
        engine.execute(table("z5()", "C", "class C:\n    def __init__(self, **kw):\n        pass\n"
            + "    def process(self):\n        yield (1,)"));
        engine.execute(table("z7()", "C", "class C:\n    __init__ = 5\n    def process(self):\n        yield (1,)"));
        engine.execute(table("z9()", "C", "class C:\n    def __init__(self, *args):\n        pass\n"
            + "    def process(self):\n        yield (1,)"));
        assertEquals(1L, ((Number) engine.executeQuery("SELECT * FROM TABLE(z9())").getRows().get(0).getValue(0))
            .longValue());
    }

    @Test
    public void anEndPartitionTakesSelfAlone() {
        assertEquals("The end_partition method cannot be static. Missing \"self\" argument in function W1 with handler C",
            refusalOf(table("w1()", "C", "class C:\n    def process(self):\n        yield (1,)\n    @staticmethod\n"
                + "    def end_partition():\n        yield (2,)")));
        assertEquals("end_partition should have \"self\" as the first positional argument, but found argument this "
            + "instead in function W2 with handler C", refusalOf(table("w2()", "C", "class C:\n    def process(self):\n"
                + "        yield (1,)\n    def end_partition(this):\n        yield (2,)")));
        assertEquals("end_partition definition should not include keyword arguments, but found 1 arguments in function "
            + "W3 with handler C", refusalOf(table("w3()", "C", "class C:\n    def process(self):\n        yield (1,)\n"
                + "    def end_partition(self, *, k=1):\n        yield (2,)")));
        assertEquals("UDF definition contains 1 arguments, so the process method should be defined with 2 arguments, "
            + "including \"self\", but found 1 arguments in function W4 with handler C",
            refusalOf(table("w4(a INT)", "C", "class C:\n    def process(self):\n        yield (1,)\n"
                + "    def end_partition(self, a):\n        yield (2,)")));
        assertEquals("Python Interpreter Error:\nAttributeError: 'int' object has no attribute '__code__' in function W6 "
            + "with handler C", refusalOf(table("w6()", "C", "class C:\n    def process(self):\n        yield (1,)\n"
                + "    end_partition = 5")));
        assertEquals("end_partition should be defined with only one argument \"self\", but found 2 arguments in "
            + "function Z8 with handler C", refusalOf(table("z8()", "C", "class C:\n    def process(self):\n"
                + "        yield (1,)\n    def end_partition(self, a):\n        yield (2,)")));
        engine.execute(table("w7()", "C", "class C:\n    def process(self):\n        yield (1,)\n"
            + "    def end_partition(self, *args):\n        yield (2,)"));
    }

    @Test
    public void aHandlerIsJudgedAfterTheSchemaAndAnExistingFunction() {
        engine.execute("CREATE FUNCTION rx_2(a INT, b INT) RETURNS INT AS '1'");
        assertEquals("SQL compilation error:\nObject 'RX_2' already exists.",
            refusalOf(scalar("rx_2(a INT, a INT)", "h", "def h(a):\n    return 1")));
        assertEquals("Python function is defined with 1 arguments, but UDF definition contains 2 arguments in "
            + "function RX_2 with handler h", refusalOf(scalar("IF NOT EXISTS rx_2(a INT, a INT)", "h",
                "def h(a):\n    return 1")));
        assertEquals(hinted("SQL compilation error:\nSchema 'TEST_DB.NOSUCH_SCHEMA' does not exist or not authorized."),
            refusalOf(scalar("IF NOT EXISTS nosuch_schema.rx_7(a INT)", "h", "def h():\n    return 1")));
    }

    @Test
    public void aTableFunctionsHandlerIsAClassWithAProcessMethod() {
        assertEquals("Could not find handler class in function PB8 with handler Z",
            refusalOf(table("pb8(a INT)", "Z", "class C:\n    def process(self, a):\n        yield (1,)")));
        assertEquals("Could not find process method in function PB4 with handler C",
            refusalOf(table("pb4(a INT)", "C", "class C:\n    def end_partition(self):\n        yield (1,)")));
        assertEquals("Could not find process method in function PB6 with handler h",
            refusalOf(table("pb6(a INT)", "h", "def h(a):\n    yield (1,)")));
        assertEquals("Python Interpreter Error:\nAttributeError: 'int' object has no attribute '__code__' in "
            + "function PB9 with handler C",
            refusalOf(table("pb9(a INT)", "C", "class C:\n    process = 5")));
        assertEquals("Python Interpreter Error:\nModuleNotFoundError: No module named 'C' in function PB13 with "
            + "handler C.D",
            refusalOf(table("pb13(a INT)", "C.D", "class C:\n    def process(self, a):\n        yield (1,)")));
    }
}

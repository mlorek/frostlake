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

import dev.frostlake.BaseDatabaseTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a value that leaves a JavaScript handler uncaught is told as, and where: an error by its name and its
 * message, each only when it is a text; an object whose {@code toString} is the plain one by the function that
 * built it; a function by its source; anything else by its tag. It is placed at the {@code throw} that sent it
 * out - an error caught and thrown again at the second {@code throw}, one built into a variable at the statement
 * that throws the variable - while the frames stay those of where it arose. Every row is live-verified.
 */
public class JavaScriptThrownErrorTest extends BaseDatabaseTest {

    /** What is thrown: the body, and the sentence after {@code Uncaught }. */
    private static final String[][] VALUES = {
            {"throw foo;",
                "ReferenceError: foo is not defined in EP at 'throw foo;' position 0\nstackstrace: \nEP line: 1"},
            {"throw new Error('m');",
                "Error: m in EP at 'throw new Error('m');' position 0\nstackstrace: \nEP line: 1"},
            {"throw new TypeError('t');",
                "TypeError: t in EP at 'throw new TypeError('t');' position 0\nstackstrace: \nEP line: 1"},
            {"throw new Error(\n  'x');",
                "Error: x in EP at 'throw new Error(' position 0\nstackstrace: \nEP line: 1"},
            {"throw new\n  Error('x');",
                "Error: x in EP at 'throw new' position 0\nstackstrace: \nEP line: 1"},
            {"throw 5;",
                "5 in EP at 'throw 5;' position 0\nstackstrace: \nEP line: 1"},
            {"throw   'late';",
                "late in EP at 'throw   'late';' position 0\nstackstrace: \nEP line: 1"},
            {"throw {a: 1};",
                "#<Object> in EP at 'throw {a: 1};' position 0\nstackstrace: \nEP line: 1"},
            {"throw [1, 2];",
                "[object Array] in EP at 'throw [1, 2];' position 0\nstackstrace: \nEP line: 1"},
            {"throw function f() { };",
                "function f() { } in EP at 'throw function f() { };' position 0\nstackstrace: \nEP line: 1"},
            {"throw Symbol('s');",
                "Symbol(s) in EP at 'throw Symbol('s');' position 0\nstackstrace: \nEP line: 1"},
            {"throw new Date(0);",
                "[object Date] in EP at 'throw new Date(0);' position 0\nstackstrace: \nEP line: 1"},
            {"throw 10n;",
                "10 in EP at 'throw 10n;' position 0\nstackstrace: \nEP line: 1"},
            {"throw true;",
                "true in EP at 'throw true;' position 0\nstackstrace: \nEP line: 1"},
            {"throw /re/;",
                "[object RegExp] in EP at 'throw /re/;' position 0\nstackstrace: \nEP line: 1"},
            {"snowflake.throwUnredactedError(5);",
                "SnowflakeUserError in EP at '    throw err;' position 4\nstackstrace: \nSnowflake.throwUnredactedError line: 15\nEP line: 1"},
            {"snowflake.throwUnredactedError({a: 1});",
                "SnowflakeUserError in EP at '    throw err;' position 4\nstackstrace: \nSnowflake.throwUnredactedError line: 15\nEP line: 1"},
            {"snowflake.throwUnredactedError(null);",
                "SnowflakeUserError in EP at '    throw err;' position 4\nstackstrace: \nSnowflake.throwUnredactedError line: 15\nEP line: 1"},
            {"snowflake.throwUnredactedError(true);",
                "SnowflakeUserError in EP at '    throw err;' position 4\nstackstrace: \nSnowflake.throwUnredactedError line: 15\nEP line: 1"},
            {"snowflake.throwUnredactedError([1, 2]);",
                "SnowflakeUserError in EP at '    throw err;' position 4\nstackstrace: \nSnowflake.throwUnredactedError line: 15\nEP line: 1"},
            {"snowflake.throwUnredactedError('');",
                "SnowflakeUserError in EP at '    throw err;' position 4\nstackstrace: \nSnowflake.throwUnredactedError line: 15\nEP line: 1"},
            {"snowflake.throwUnredactedError();",
                "SnowflakeUserError in EP at '    throw err;' position 4\nstackstrace: \nSnowflake.throwUnredactedError line: 15\nEP line: 1"},
            {"snowflake.throwUnredactedError(new TypeError('in'));",
                "SnowflakeUserError in EP at '    throw err;' position 4\nstackstrace: \nSnowflake.throwUnredactedError line: 15\nEP line: 1"},
            {"var e = new Error('x'); e.message = 5; throw e;",
                "Error in EP at 'var e = new Error('x'); e.message = 5; throw e;' position 39\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.message = {a: 1}; throw e;",
                "Error in EP at 'var e = new Error('x'); e.message = {a: 1}; throw e;' position 44\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.message = new Error('in'); throw e;",
                "Error in EP at 'var e = new Error('x'); e.message = new Error('in'); throw e;' position 53\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.message = undefined; throw e;",
                "Error in EP at 'var e = new Error('x'); e.message = undefined; throw e;' position 47\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.message = null; throw e;",
                "Error in EP at 'var e = new Error('x'); e.message = null; throw e;' position 42\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.message = ''; throw e;",
                "Error in EP at 'var e = new Error('x'); e.message = ''; throw e;' position 40\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); delete e.message; throw e;",
                "Error in EP at 'var e = new Error('x'); delete e.message; throw e;' position 42\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.name = 5; throw e;",
                "x in EP at 'var e = new Error('x'); e.name = 5; throw e;' position 36\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.name = undefined; throw e;",
                "x in EP at 'var e = new Error('x'); e.name = undefined; throw e;' position 44\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.name = ''; throw e;",
                "x in EP at 'var e = new Error('x'); e.name = ''; throw e;' position 37\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.name = 'Mine'; throw e;",
                "Mine: x in EP at 'var e = new Error('x'); e.name = 'Mine'; throw e;' position 41\nstackstrace: \nEP line: 1"},
            {"var e = new Error(); e.name = ''; throw e;",
                " in EP at 'var e = new Error(); e.name = ''; throw e;' position 34\nstackstrace: \nEP line: 1"},
            {"var e = new TypeError('x'); e.name = null; throw e;",
                "x in EP at 'var e = new TypeError('x'); e.name = null; throw e;' position 43\nstackstrace: \nEP line: 1"},
            {"var e = new Error('x'); e.toString = function () { return 'custom'; }; throw e;",
                "Error: x in EP at 'var e = new Error('x'); e.toString = function () { return 'custom'; }; throw e;' position 71\nstackstrace: \nEP line: 1"},
            {"throw Object.create(Error.prototype);",
                "Error in EP at 'throw Object.create(Error.prototype);' position 0\nstackstrace: \nEP line: 1"},
            {"throw Object.create(TypeError.prototype);",
                "TypeError in EP at 'throw Object.create(TypeError.prototype);' position 0\nstackstrace: \nEP line: 1"},
            {"throw {name: 'N', message: 'm', toString: Error.prototype.toString};",
                "N: m in EP at 'throw {name: 'N', message: 'm', toString: Error.prototype.toString};' position 0\nstackstrace: \nEP line: 1"},
            {"throw {name: 'N', message: 'm'};",
                "#<Object> in EP at 'throw {name: 'N', message: 'm'};' position 0\nstackstrace: \nEP line: 1"},
            {"throw {toString: function () { return 'mine'; }};",
                "[object Object] in EP at 'throw {toString: function () { return 'mine'; }};' position 0\nstackstrace: \nEP line: 1"},
            {"throw new (class K { })();",
                "#<K> in EP at 'throw new (class K { })();' position 0\nstackstrace: \nEP line: 1"},
            {"throw new (class K extends Error { })('km');",
                "Error: km in EP at 'throw new (class K extends Error { })('km');' position 0\nstackstrace: \nEP line: 1"},
            {"throw new (class K extends Error { constructor(m) { super(m); this.name = 'KName'; } })('km');",
                "KName: km in EP at 'throw new (class K extends Error { constructor(m) { super(m); this.name = 'KName'; } })('km');' position 0\nstackstrace: \nEP line: 1"},
            {"throw Object.create(null);",
                "[object Object] in EP at 'throw Object.create(null);' position 0\nstackstrace: \nEP line: 1"},
            {"throw new Map();",
                "#<Map> in EP at 'throw new Map();' position 0\nstackstrace: \nEP line: 1"},
            {"throw new Set([1]);",
                "#<Set> in EP at 'throw new Set([1]);' position 0\nstackstrace: \nEP line: 1"},
            {"throw Math;",
                "#<Object> in EP at 'throw Math;' position 0\nstackstrace: \nEP line: 1"},
            {"throw JSON;",
                "#<Object> in EP at 'throw JSON;' position 0\nstackstrace: \nEP line: 1"},
            {"throw arguments;",
                "#<Object> in EP at 'throw arguments;' position 0\nstackstrace: \nEP line: 1"},
            {"throw new Proxy({}, {});",
                "#<Object> in EP at 'throw new Proxy({}, {});' position 0\nstackstrace: \nEP line: 1"},
            {"throw new Uint8Array(2);",
                "[object Uint8Array] in EP at 'throw new Uint8Array(2);' position 0\nstackstrace: \nEP line: 1"},
            {"throw new Number(5);",
                "[object Number] in EP at 'throw new Number(5);' position 0\nstackstrace: \nEP line: 1"},
            {"throw new String('boxed');",
                "[object String] in EP at 'throw new String('boxed');' position 0\nstackstrace: \nEP line: 1"},
            {"throw new Boolean(false);",
                "[object Boolean] in EP at 'throw new Boolean(false);' position 0\nstackstrace: \nEP line: 1"},
            {"throw snowflake;",
                "#<Snowflake> in EP at 'throw snowflake;' position 0\nstackstrace: \nEP line: 1"},
            {"throw Promise.resolve(1);",
                "#<Promise> in EP at 'throw Promise.resolve(1);' position 0\nstackstrace: \nEP line: 1"},
            {"throw () => 1;",
                "() => 1 in EP at 'throw () => 1;' position 0\nstackstrace: \nEP line: 1"},
            {"throw class Z { };",
                "class Z { } in EP at 'throw class Z { };' position 0\nstackstrace: \nEP line: 1"},
            {"throw Math.max;",
                "function max() { [native code] } in EP at 'throw Math.max;' position 0\nstackstrace: \nEP line: 1"},
            {"throw 1.5;",
                "1.5 in EP at 'throw 1.5;' position 0\nstackstrace: \nEP line: 1"},
            {"throw -0;",
                "0 in EP at 'throw -0;' position 0\nstackstrace: \nEP line: 1"},
            {"throw NaN;",
                "NaN in EP at 'throw NaN;' position 0\nstackstrace: \nEP line: 1"},
            {"throw '';",
                " in EP at 'throw '';' position 0\nstackstrace: \nEP line: 1"},
            {"throw Symbol();",
                "Symbol() in EP at 'throw Symbol();' position 0\nstackstrace: \nEP line: 1"},
            {"throw [];",
                "[object Array] in EP at 'throw [];' position 0\nstackstrace: \nEP line: 1"},
            {"throw [[1], {a: 1}];",
                "[object Array] in EP at 'throw [[1], {a: 1}];' position 0\nstackstrace: \nEP line: 1"},
            {"throw {[Symbol.toStringTag]: 'Tagged'};",
                "#<Object> in EP at 'throw {[Symbol.toStringTag]: 'Tagged'};' position 0\nstackstrace: \nEP line: 1"},
            {"throw new (function Old() { })();",
                "#<Old> in EP at 'throw new (function Old() { })();' position 0\nstackstrace: \nEP line: 1"},
            {"throw new (function () { })();",
                "[object Object] in EP at 'throw new (function () { })();' position 0\nstackstrace: \nEP line: 1"},
    };

    /** An error thrown from another place than the one it arose at. */
    private static final String[][] THROWN_AGAIN = {
            {"var e = new Error('m'); throw e;",
                "Error: m in EP at 'var e = new Error('m'); throw e;' position 24\nstackstrace: \nEP line: 1"},
            {"try { foo; } catch (e) { throw e; }",
                "ReferenceError: foo is not defined in EP at 'try { foo; } catch (e) { throw e; }' position 25\nstackstrace: \nEP line: 1"},
            {"try { foo(); } catch (e) { throw e; }",
                "ReferenceError: foo is not defined in EP at 'try { foo(); } catch (e) { throw e; }' position 27\nstackstrace: \nEP line: 1"},
            {"try { null.x; } catch (e) { throw e; }",
                "TypeError: Cannot read properties of null (reading 'x') in EP at 'try { null.x; } catch (e) { throw e; }' position 28\nstackstrace: \nEP line: 1"},
            {"try {\n  null.x;\n} catch (e) {\n  throw e;\n}",
                "TypeError: Cannot read properties of null (reading 'x') in EP at '  throw e;' position 2\nstackstrace: \nEP line: 2"},
            {"function g() {\n  null.x;\n}\ntry {\n  g();\n} catch (e) {\n  throw e;\n}",
                "TypeError: Cannot read properties of null (reading 'x') in EP at '  throw e;' position 2\nstackstrace: \ng line: 2\nEP line: 5"},
            {"var e = new Error('x');\nthrow e;",
                "Error: x in EP at 'throw e;' position 0\nstackstrace: \nEP line: 1"},
            {"function mk() {\n  return new Error('x');\n}\nvar e = mk();\nthrow e;",
                "Error: x in EP at 'throw e;' position 0\nstackstrace: \nmk line: 2\nEP line: 4"},
            {"function g() {\n  try {\n    null.x;\n  } catch (e) {\n    throw e;\n  }\n}\nreturn g();",
                "TypeError: Cannot read properties of null (reading 'x') in EP at '    throw e;' position 4\nstackstrace: \ng line: 3\nEP line: 8"},
            {"try { null.x; } catch (e) { e.message = 'changed'; throw e; }",
                "TypeError: changed in EP at 'try { null.x; } catch (e) { e.message = 'changed'; throw e; }' position 51\nstackstrace: \nEP line: 1"},
            {"try { foo; } catch (e) { throw e; } finally { }",
                "ReferenceError: foo is not defined in EP at 'try { foo; } catch (e) { throw e; } finally { }' position 25\nstackstrace: \nEP line: 1"},
            {"try { null.x; } catch (e) { if (true) { throw e; } }",
                "TypeError: Cannot read properties of null (reading 'x') in EP at 'try { null.x; } catch (e) { if (true) { throw e; } }' position 40\nstackstrace: \nEP line: 1"},
            {"try {\n  try {\n    null.x;\n  } catch (a) {\n    throw a;\n  }\n} catch (b) {\n  throw b;\n}",
                "TypeError: Cannot read properties of null (reading 'x') in EP at '  throw b;' position 2\nstackstrace: \nEP line: 3"},
            {"try { null.x; } catch (e) { throw (e); }",
                "TypeError: Cannot read properties of null (reading 'x') in EP at 'try { null.x; } catch (e) { throw (e); }' position 28\nstackstrace: \nEP line: 1"},
            {"try { null.x; } catch { throw new Error('y'); }",
                "Error: y in EP at 'try { null.x; } catch { throw new Error('y'); }' position 24\nstackstrace: \nEP line: 1"},
            {"try { throw new Error('a'); } catch (e) { throw e; }",
                "Error: a in EP at 'try { throw new Error('a'); } catch (e) { throw e; }' position 42\nstackstrace: \nEP line: 1"},
    };

    /** Each body, made a handler, is refused in the given words: what follows {@code Uncaught }. */
    private void assertUncaught(final String[][] cases) {
        for (final String[] one : cases) {
            engine.execute("CREATE OR REPLACE FUNCTION ep() RETURNS VARCHAR LANGUAGE JAVASCRIPT AS $$" + one[0] + "$$");
            final RuntimeException refused = assertThrows(RuntimeException.class, new Executable() {
                @Override
                public void execute() {
                    engine.executeQuery("SELECT ep()").getRows();
                }
            }, one[0]);
            assertTrue(String.valueOf(refused.getMessage()).contains("JavaScript execution error: Uncaught " + one[1]),
                one[0] + " -> " + refused.getMessage());
        }
    }

    @Test
    public void aThrownValueIsToldByItsKind() {
        assertUncaught(VALUES);
    }

    @Test
    public void anErrorIsPlacedAtTheThrowThatSentItOut() {
        assertUncaught(THROWN_AGAIN);
    }
}

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

import java.util.Base64;

/**
 * The class-file version of a handler a Java function imports. A function that declares a RUNTIME_VERSION
 * runs on that Java - 11, 17 or 21 - and a handler class its IMPORTS hold compiled for a newer one is refused
 * as the function is created: {@code Class files were compiled with an unsupported version of the JDK}, the
 * sentence naming Java 11 whatever the runtime. Only the handler's own class is judged: a newer class in an
 * import the handler does not name is no fault, and a body written beside such an import runs. A function that
 * declares no RUNTIME_VERSION runs on the account's default runtime, which is the account's own, so only a
 * class every runtime reads is tried there. The three jars hold one class each, {@code H11}, {@code H17} and
 * {@code H21} - {@code public static long h(long x)} answering {@code x} plus its Java level - compiled for
 * that level. Every answer is live-verified.
 */
public class ImportedHandlerClassVersionTest extends MeasuredCellsTestSupport {

    /** A jar holding the class {@code H11}, compiled for Java 11. */
    private static final String H11_JAR = """
        UEsDBAoAAAgAAC8DTF0AAAAAAAAAAAAAAAAJAAQATUVUQS1JTkYv/soAAFBLAwQUAAgICAAvA0xdAAAAAAAAAAAAAAAAFAAAAE1F
        VEEtSU5GL01BTklGRVNULk1G803My0xLLS7RDUstKs7Mz7NSMNQz4OVyLkpNLElN0XWqtFIwAoroGRooaDil5uQE56eVaPJy8XIB
        AFBLBwi9PGLMOgAAADkAAABQSwMEFAAICAgALwNMXQAAAAAAAAAAAAAAAAkAAABIMTEuY2xhc3NdTj8PwXAQfaf/aEtJrJrYWoPG
        ZBGLRESEgdh/rV+oVJsIvpXBJDH4AD6UuJbJDe/u3t29d6/34wmgj4aJEhQDqg0NOqG+FxcRJCLdBotwL6MTQR/EaXwaEhTPX2v4
        hmXAZGbS6xHUUbaRBGcWp3J+PoTyuBJhwgzteOhN/SnBXGbnYyTHcc6X+aybG6GNCvvnUQLlHzBa3LU4E2etcwfduCDYjHpBEh9V
        UfutulALCd1twhDXv12F0Snk6x9QSwcI039fIb0AAAD0AAAAUEsBAgoACgAACAAALwNMXQAAAAAAAAAAAAAAAAkABAAAAAAAAAAA
        AAAAAAAAAE1FVEEtSU5GL/7KAABQSwECFAAUAAgICAAvA0xdvTxizDoAAAA5AAAAFAAAAAAAAAAAAAAAAAArAAAATUVUQS1JTkYv
        TUFOSUZFU1QuTUZQSwECFAAUAAgICAAvA0xd039fIb0AAAD0AAAACQAAAAAAAAAAAAAAAACnAAAASDExLmNsYXNzUEsFBgAAAAAD
        AAMAtAAAAJsBAAAAAA==""";

    /** A jar holding the class {@code H17}, compiled for Java 17. */
    private static final String H17_JAR = """
        UEsDBAoAAAgAADADTF0AAAAAAAAAAAAAAAAJAAQATUVUQS1JTkYv/soAAFBLAwQUAAgICAAwA0xdAAAAAAAAAAAAAAAAFAAAAE1F
        VEEtSU5GL01BTklGRVNULk1G803My0xLLS7RDUstKs7Mz7NSMNQz4OVyLkpNLElN0XWqtFIwAoroGRooaDil5uQE56eVaPJy8XIB
        AFBLBwi9PGLMOgAAADkAAABQSwMEFAAICAgAMANMXQAAAAAAAAAAAAAAAAkAAABIMTcuY2xhc3NdTj8PwXAQfde/VCmJlcTWGojJ
        gkUi0ggDsf/KL7ShTRp8K4NJYvABfChxLZMb3t29u3vvXu/HE8AQNQsKVBOaDR0GoRqJi+geRLzrLoJIbk4EYxDG4WlEUF1vreMb
        NRMWM9Nen6CNk60kOLMwlvPzMZDpSgQHZmjPQ9f3fIK1TM7pRk7CjC/wWSczQgtF9s9CAWUfMJa4a3Amznr7DrpxQbAZjZwkPiqj
        8lttQssljGYdprj+7aqMTi5f/QBQSwcIzO8ae70AAAD0AAAAUEsBAgoACgAACAAAMANMXQAAAAAAAAAAAAAAAAkABAAAAAAAAAAA
        AAAAAAAAAE1FVEEtSU5GL/7KAABQSwECFAAUAAgICAAwA0xdvTxizDoAAAA5AAAAFAAAAAAAAAAAAAAAAAArAAAATUVUQS1JTkYv
        TUFOSUZFU1QuTUZQSwECFAAUAAgICAAwA0xdzO8ae70AAAD0AAAACQAAAAAAAAAAAAAAAACnAAAASDE3LmNsYXNzUEsFBgAAAAAD
        AAMAtAAAAJsBAAAAAA==""";

    /** A jar holding the class {@code H21}, compiled for Java 21. */
    private static final String H21_JAR = """
        UEsDBAoAAAgAADADTF0AAAAAAAAAAAAAAAAJAAQATUVUQS1JTkYv/soAAFBLAwQUAAgICAAwA0xdAAAAAAAAAAAAAAAAFAAAAE1F
        VEEtSU5GL01BTklGRVNULk1G803My0xLLS7RDUstKs7Mz7NSMNQz4OVyLkpNLElN0XWqtFIwAoroGRooaDil5uQE56eVaPJy8XIB
        AFBLBwi9PGLMOgAAADkAAABQSwMEFAAICAgAMANMXQAAAAAAAAAAAAAAAAkAAABIMjEuY2xhc3NdTs0OwWAQnNU/6q8Sjpq4lQPh
        LBKRiIhwIO5f6wttaJNGvZWDk8TBA3gosS0ne5jdnd2d2df78QQwRs1EDooBtQQNOsEKxEX0jiLc91ZuIL0zQR/6oX8eERSnvdXw
        jYYBk5nZoE9QJ9FOEqoLP5TL5OTKeCPcIzN04KEzb88J5jpKYk9O/ZTP81k3NUILBfZPIwdKP2AsctfkTJy1zh1044JQYtQzkvio
        jMpv1YaaSeh2HYa4/u0qjNVM3voAUEsHCKOOlgG+AAAA9AAAAFBLAQIKAAoAAAgAADADTF0AAAAAAAAAAAAAAAAJAAQAAAAAAAAA
        AAAAAAAAAABNRVRBLUlORi/+ygAAUEsBAhQAFAAICAgAMANMXb08Ysw6AAAAOQAAABQAAAAAAAAAAAAAAAAAKwAAAE1FVEEtSU5G
        L01BTklGRVNULk1GUEsBAhQAFAAICAgAMANMXaOOlgG+AAAA9AAAAAkAAAAAAAAAAAAAAAAApwAAAEgyMS5jbGFzc1BLBQYAAAAA
        AwADALQAAACcAQAAAAA=""";

    @Test
    public void aHandlerNewerThanItsRuntime() {
        answer(
            "CREATE OR REPLACE STAGE nf_st");
        stageLocalBytes("nf_st", "h11.jar", Base64.getMimeDecoder().decode(H11_JAR));
        stageLocalBytes("nf_st", "h17.jar", Base64.getMimeDecoder().decode(H17_JAR));
        stageLocalBytes("nf_st", "h21.jar", Base64.getMimeDecoder().decode(H21_JAR));
        assertAnswer(
            "Function NF_J11 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_j11(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H11.h' IMPORTS = "
                + "('@nf_st/h11.jar')");
        assertAnswer(
            "12",
            "SELECT nf_j11(1)");
        assertAnswer(
            "REFUSED Class files were compiled with an unsupported version of the JDK. Use the '-target' or "
                + "'-release' option when compiling your class files to target Java 11 in function NF_J17A with "
                + "handler H17.h",
            "CREATE OR REPLACE FUNCTION nf_j17a(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '11' "
                + "HANDLER = 'H17.h' IMPORTS = ('@nf_st/h17.jar')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NF_J17A.",
            "SELECT nf_j17a(1)");
        assertAnswer(
            "Function NF_J17B successfully created.",
            "CREATE OR REPLACE FUNCTION nf_j17b(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H17.h' IMPORTS = ('@nf_st/h17.jar')");
        assertAnswer(
            "18",
            "SELECT nf_j17b(1)");
        assertAnswer(
            "REFUSED Class files were compiled with an unsupported version of the JDK. Use the '-target' or "
                + "'-release' option when compiling your class files to target Java 11 in function NF_J21B with "
                + "handler H21.h",
            "CREATE OR REPLACE FUNCTION nf_j21b(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H21.h' IMPORTS = ('@nf_st/h21.jar')");
        assertAnswer(
            "REFUSED SQL compilation error: | Unknown function NF_J21B.",
            "SELECT nf_j21b(1)");
        assertAnswer(
            "Function NF_J11B successfully created.",
            "CREATE OR REPLACE FUNCTION nf_j11b(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '17' "
                + "HANDLER = 'H11.h' IMPORTS = ('@nf_st/h11.jar')");
        assertAnswer(
            "12",
            "SELECT nf_j11b(1)");
        assertAnswer(
            "Function NF_JN successfully created.",
            "CREATE OR REPLACE FUNCTION nf_jn(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'N.h' IMPORTS = "
                + "('@nf_st/h17.jar') AS $$ class N { public static long h(long x) { return x; } } $$");
        assertAnswer(
            "1",
            "SELECT nf_jn(1)");
        assertAnswer(
            "Function NF_J2 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_j2(x NUMBER) RETURNS NUMBER LANGUAGE JAVA HANDLER = 'H11.h' IMPORTS = "
                + "('@nf_st/h11.jar', '@nf_st/h17.jar')");
        assertAnswer(
            "12",
            "SELECT nf_j2(1)");
        assertAnswer(
            "Function NF_J4 successfully created.",
            "CREATE OR REPLACE FUNCTION nf_j4(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '21' "
                + "HANDLER = 'H21.h' IMPORTS = ('@nf_st/h21.jar')");
        assertAnswer(
            "REFUSED SQL compilation error: | invalid value '8' for property 'RUNTIME_VERSION'",
            "CREATE OR REPLACE FUNCTION nf_j5(x NUMBER) RETURNS NUMBER LANGUAGE JAVA RUNTIME_VERSION = '8' "
                + "HANDLER = 'H11.h' IMPORTS = ('@nf_st/h11.jar')");
    }
}

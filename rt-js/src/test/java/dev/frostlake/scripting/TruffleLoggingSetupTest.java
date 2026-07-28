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

import dev.frostlake.executor.udf.TruffleLogBridge;
import dev.frostlake.rt.js.GraalJsEngine;
import org.junit.jupiter.api.Test;

import javax.script.ScriptEngine;
import javax.script.ScriptException;

import java.util.logging.Level;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Truffle logging is configured through the polyglot embedding API: the shared JS engine is created
 * with the {@link TruffleLogBridge} installed (Truffle logs → SLF4J) and still evaluates scripts,
 * and the bridge itself accepts the full range of JUL records without blowing up.
 */
public class TruffleLoggingSetupTest {

    @Test
    public void sharedGraalJsEngineEvaluates() throws ScriptException {
        final ScriptEngine engine = GraalJsEngine.newScriptEngine();
        assertNotNull(engine, "Graal JS must be available through the shared engine");
        assertEquals(42, ((Number) engine.eval("6 * 7")).intValue());
    }

    @Test
    public void bridgeHandlesAllRecordShapes() {
        final TruffleLogBridge bridge = new TruffleLogBridge();

        final LogRecord parameterized = new LogRecord(Level.WARNING, "value is {0} of {1}");
        parameterized.setParameters(new Object[]{1, 2});
        parameterized.setLoggerName("engine");
        bridge.publish(parameterized);

        final LogRecord severe = new LogRecord(Level.SEVERE, "boom");
        severe.setThrown(new IllegalStateException("expected"));
        bridge.publish(severe);

        bridge.publish(new LogRecord(Level.FINEST, "trace-level"));
        bridge.publish(new LogRecord(Level.INFO, null));
        bridge.publish(null);

        bridge.flush();
        bridge.close();
    }
}

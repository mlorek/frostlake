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

package dev.frostlake.features;

import dev.frostlake.BaseDatabaseTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests for optional TRANSACTION keyword in BEGIN statement
 * Both "BEGIN" and "BEGIN TRANSACTION" should be syntactically valid
 * Note: These tests only verify syntax acceptance, not transaction semantics
 */
public class BeginTransactionSyntaxTest extends BaseDatabaseTest {
    private static final Logger logger = LoggerFactory.getLogger(BeginTransactionSyntaxTest.class);

    @Override
    protected void setupTest() {
        logger.info("DatabaseEngine initialized for BEGIN TRANSACTION syntax tests");
    }

    @Test
    public void testBeginSyntax() {
        logger.info("Testing BEGIN syntax is accepted");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN");
                
            }
        });

        logger.info("BEGIN syntax is valid");
    }

    @Test
    public void testBeginTransactionSyntax() {
        logger.info("Testing BEGIN TRANSACTION syntax is accepted");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN TRANSACTION");
                
            }
        });

        logger.info("BEGIN TRANSACTION syntax is valid");
    }

    @Test
    public void testBeginWithSemicolon() {
        logger.info("Testing BEGIN with semicolon");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN;");
                
            }
        });

        logger.info("BEGIN; syntax is valid");
    }

    @Test
    public void testBeginTransactionWithSemicolon() {
        logger.info("Testing BEGIN TRANSACTION with semicolon");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN TRANSACTION;");
                
            }
        });

        logger.info("BEGIN TRANSACTION; syntax is valid");
    }

    @Test
    public void testMultipleBeginStatements() {
        logger.info("Testing multiple BEGIN statements");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN");
                
            }
        });

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN TRANSACTION");
                
            }
        });

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN;");
                
            }
        });

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN TRANSACTION;");
                
            }
        });

        logger.info("Multiple BEGIN statement syntaxes are valid");
    }

    @Test
    public void testBeginUpperCase() {
        logger.info("Testing BEGIN in uppercase");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN");
                
            }
        });

        logger.info("BEGIN uppercase syntax is valid");
    }

    @Test
    public void testBeginTransactionUpperCase() {
        logger.info("Testing BEGIN TRANSACTION in uppercase");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN TRANSACTION");
                
            }
        });

        logger.info("BEGIN TRANSACTION uppercase syntax is valid");
    }

    @Test
    public void testBeginLowerCase() {
        logger.info("Testing begin in lowercase");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("begin");
                
            }
        });

        logger.info("begin lowercase syntax is valid");
    }

    @Test
    public void testBeginTransactionLowerCase() {
        logger.info("Testing begin transaction in lowercase");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("begin transaction");
                
            }
        });

        logger.info("begin transaction lowercase syntax is valid");
    }

    @Test
    public void testBeginMixedCase() {
        logger.info("Testing Begin in mixed case");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("Begin");
                
            }
        });

        logger.info("Begin mixed case syntax is valid");
    }

    @Test
    public void testBeginTransactionMixedCase() {
        logger.info("Testing Begin Transaction in mixed case");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("Begin Transaction");
                
            }
        });

        logger.info("Begin Transaction mixed case syntax is valid");
    }

    @Test
    public void testBeginWithWhitespace() {
        logger.info("Testing BEGIN with extra whitespace");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("  BEGIN  ");
                
            }
        });

        logger.info("BEGIN with whitespace syntax is valid");
    }

    @Test
    public void testBeginTransactionWithWhitespace() {
        logger.info("Testing BEGIN TRANSACTION with extra whitespace");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("  BEGIN   TRANSACTION  ");
                
            }
        });

        logger.info("BEGIN TRANSACTION with whitespace syntax is valid");
    }

    @Test
    public void testBeginSequentialCalls() {
        logger.info("Testing sequential BEGIN calls");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN");
                
            }
        });

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("BEGIN TRANSACTION");
                
            }
        });

        logger.info("Sequential BEGIN calls are syntactically valid");
    }

    @Test
    public void testBeginWithNewlines() {
        logger.info("Testing BEGIN with newlines");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("\nBEGIN\n");
                
            }
        });

        logger.info("BEGIN with newlines syntax is valid");
    }

    @Test
    public void testBeginTransactionWithNewlines() {
        logger.info("Testing BEGIN TRANSACTION with newlines");

        assertDoesNotThrow(new Executable() {
            @Override
            public void execute() throws Throwable {
                engine.execute("\nBEGIN TRANSACTION\n");
                
            }
        });

        logger.info("BEGIN TRANSACTION with newlines syntax is valid");
    }
}

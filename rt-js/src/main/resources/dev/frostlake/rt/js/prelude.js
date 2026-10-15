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

// Run once in every fresh context, with the engine's host object. It leaves the handler the world the account
// gives one: none of the script-engine globals, the account's own API objects, a console with the account's
// members, dates that name their zone in full, JSON.parse faults in the account's words, and no dynamic code.
// It hands the host the function that tells an uncaught value as the account does.
(function (host) {
    delete globalThis.Java;
    delete globalThis.context;
    delete globalThis.engine;
    delete globalThis.arguments;
    (function () {
        'use strict';

        // ---- the account's API objects -----------------------------------------------------------------------
        var getOwnPropertyDescriptor = Object.getOwnPropertyDescriptor;
        var defineProperty = Object.defineProperty;
        var getPrototypeOf = Object.getPrototypeOf;
        var objectKeys = Object.keys;
        var isArray = Array.isArray;
        var ObjectType = Object;
        var StringType = String;
        var NumberType = Number;
        var BooleanType = Boolean;

        // The error a user exception is: its message is whatever it was given, a property like any other.
        var SnowflakeUserError = class SnowflakeUserError extends Error {
            constructor(message) {
                super();
                var stack = getOwnPropertyDescriptor(this, 'stack');
                if (stack !== undefined && delete this.stack) {
                    this.message = message;
                    defineProperty(this, 'stack', stack);
                } else {
                    this.message = message;
                }
                this._isCustomizableErrorFormat = false;
                this.keepErrorMessage = false;
                this.keepStackTraceAndProc = true;
                this.keepErrorCodeFormatString = true;
            }
        };
        defineProperty(SnowflakeUserError.prototype, 'name',
            {value: 'SnowflakeUserError', writable: true, enumerable: false, configurable: true});

        var hexDigits = '0123456789abcdef';
        var base64Digits = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
        var cLoggerType = (function () {
            return function () {
            };
        })();
        var cLogger = new cLoggerType();
        cLogger.add_metric = function add_metric() {
        };
        cLogger.add_span = function add_span() {
        };
        cLogger.get_min_log_level = function get_min_log_level() {
            return 100;
        };
        cLogger.get_parent_trace = function get_parent_trace() {
            return '';
        };
        cLogger.get_trace_level = function get_trace_level() {
            return 'OFF';
        };
        cLogger.hex_to_base64 = function hex_to_base64() {
            var text = `${arguments[0]}`.toLowerCase();
            var out = '';
            for (var i = 0; i + 1 < text.length; i += 6) {
                var bytes = [];
                for (var j = i; j + 1 < text.length && j < i + 6; j += 2) {
                    bytes.push(hexDigits.indexOf(text[j]) * 16 + hexDigits.indexOf(text[j + 1]));
                }
                var bits = (bytes[0] << 16) | ((bytes[1] || 0) << 8) | (bytes[2] || 0);
                out += base64Digits[(bits >> 18) & 63] + base64Digits[(bits >> 12) & 63]
                    + (bytes.length > 1 ? base64Digits[(bits >> 6) & 63] : '=')
                    + (bytes.length > 2 ? base64Digits[bits & 63] : '=');
            }
            return out;
        };
        cLogger.ingest_event_proto = function ingest_event_proto() {
        };
        cLogger.log_proto = function log_proto() {
        };
        cLogger.now_ms = function now_ms() {
            return Date.now();
        };

        var severities = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL'];
        var SnowflakeLogger = function SnowflakeLogger() {
            var logger = this;
            this.getMinLogLevel = function () {
                return cLogger.get_min_log_level();
            };
            this.levelToSeverity = function (level) {
                return severities.indexOf(level) < 0 ? 'SEVERITY_NUMBER_UNSPECIFIED' : 'SEVERITY_NUMBER_' + level;
            };
            // A text, a number and a boolean are kept as they are, a list and a plain object member by member,
            // and anything else as the text it turns into.
            this.convertToAnyValue = function (value) {
                if (value === null || value === undefined) {
                    return {stringValue: null};
                }
                if (typeof value === 'string' || value instanceof StringType) {
                    return {stringValue: value};
                }
                if (typeof value === 'number' || typeof value === 'bigint' || value instanceof NumberType) {
                    return {doubleValue: value};
                }
                if (typeof value === 'boolean' || value instanceof BooleanType) {
                    return {boolValue: value};
                }
                if (isArray(value)) {
                    return {arrayValue: {values: logger.convertToArrayValue(value)}};
                }
                if (typeof value === 'object' && value.constructor === ObjectType) {
                    return {kvlistValue: {values: logger.convertToKvListValue(value)}};
                }
                return {stringValue: value.toString()};
            };
            this.convertToArrayValue = function (values) {
                var converted = [];
                for (var i = 0; i < values.length; i++) {
                    converted.push(logger.convertToAnyValue(values[i]));
                }
                return converted;
            };
            this.convertToKvListValue = function (object) {
                var converted = [];
                var keys = objectKeys(object);
                for (var i = 0; i < keys.length; i++) {
                    converted.push({key: keys[i], value: logger.convertToAnyValue(object[keys[i]])});
                }
                return converted;
            };
            this.handleObjectType = function (value) {
                return logger.convertToAnyValue(value);
            };
            this.populateAttributes = function (info) {
                return logger.convertToKvListValue(info);
            };
            this.toProto = function (logRecord, scope) {
                return {};
            };
            this.logHelper = function (level, message) {
            };
            this.log = function (level, message, info = {}) {
            };
            this.logException = function (exception) {
            };
            this.ingest_event_proto = function (eventType, proto) {
            };
        };
        var snowflakeLogger = new SnowflakeLogger();

        var Snowflake = function Snowflake() {
        };
        Snowflake.prototype.log = function (level, msg, info = {}) {
            snowflakeLogger.log(level, msg, info);
        };
        Snowflake.prototype.log_trace = function (msg, info = {}) {
            snowflakeLogger.log('TRACE', msg, info);
        };
        Snowflake.prototype.log_debug = function (msg, info = {}) {
            snowflakeLogger.log('DEBUG', msg, info);
        };
        Snowflake.prototype.log_info = function (msg, info = {}) {
            snowflakeLogger.log('INFO', msg, info);
        };
        Snowflake.prototype.log_warn = function (msg, info = {}) {
            snowflakeLogger.log('WARN', msg, info);
        };
        Snowflake.prototype.log_error = function (msg, info = {}) {
            snowflakeLogger.log('ERROR', msg, info);
        };
        Snowflake.prototype.log_fatal = function (msg, info = {}) {
            snowflakeLogger.log('FATAL', msg, info);
        };
        Snowflake.prototype.record_exception_as_log_event = function (exception) {
            snowflakeLogger.logException(exception);
        };
        Snowflake.prototype.ingest_event_proto = function (eventType, proto) {
            snowflakeLogger.ingest_event_proto(eventType, proto);
        };
        Snowflake.prototype.getAccountParameterValue = function (parameterName) {
            throw 'Invalid method getAccountParameterValue';
        };
        Snowflake.prototype.SnowflakeUserException = function (msg) {
            var err = new SnowflakeUserError(msg);
            err._isCustomizableErrorFormat = true;
            err.keepErrorMessage = true;
            err.keepStackTraceAndProc = false;
            err.keepErrorCodeFormatString = false;
            return err;
        };
        Snowflake.prototype.throwUnredactedError = function (msg) {
            var err = new SnowflakeUserError(msg);
            err.keepErrorMessage = true;
            throw err;
        };

        globalThis.Snowflake = Snowflake;
        globalThis.SnowflakeLogger = SnowflakeLogger;
        globalThis.snowflake = new Snowflake();
        globalThis.snowflakeLogger = snowflakeLogger;
        globalThis._c_snowflake_logger = cLogger;

        // A builtin the engine stages and the account's engine does not have.
        delete String.dedent;

        // ---- the console members the engine's own console lacks ------------------------------------------------
        console.dirxml = function dirxml() {
        };
        console.profile = function profile() {
        };
        console.profileEnd = function profileEnd() {
        };
        console.table = function table() {
        };
        console.timeStamp = function timeStamp() {
        };
        console.trace = function trace() {
        };
        // A context is a console of its own, with every member but this one.
        var consoleMembers = ['assert', 'clear', 'count', 'countReset', 'debug', 'dir', 'dirxml', 'error', 'group',
            'groupCollapsed', 'groupEnd', 'info', 'log', 'profile', 'profileEnd', 'table', 'time', 'timeEnd',
            'timeLog', 'timeStamp', 'trace', 'warn'];
        var mainConsole = console;
        var functionApply = Function.prototype.apply;
        var consoleMember = function (member) {
            return function () {
                return functionApply.call(member, mainConsole, arguments);
            };
        };
        console.context = function context(name) {
            var own = {};
            for (var i = 0; i < consoleMembers.length; i++) {
                var member = mainConsole[consoleMembers[i]];
                if (typeof member === 'function') {
                    own[consoleMembers[i]] = consoleMember(member);
                }
            }
            return own;
        };
        defineProperty(console, Symbol.toStringTag, {value: 'console', configurable: true});

        // ---- what a value that leaves the handler uncaught is told as: an error by the texts its name and its
        // message are, a plain object by the function that built it, anything else by its tag; no method of the
        // value is called for it ------------------------------------------------------------------------------
        var objectToString = Object.prototype.toString;
        var errorToString = Error.prototype.toString;
        var functionToString = Function.prototype.toString;
        var symbolToString = Symbol.prototype.toString;
        var bigIntToString = BigInt.prototype.toString;
        var ErrorType = Error;
        var isError = Error.isError || function (value) {
            return value instanceof ErrorType;
        };
        var toStringTag = Symbol.toStringTag;
        var dataProperty = function (object, key) {
            try {
                for (var holder = object; holder !== null && holder !== undefined; holder = getPrototypeOf(holder)) {
                    var property = getOwnPropertyDescriptor(holder, key);
                    if (property !== undefined) {
                        return property.value;
                    }
                }
            } catch (unreadable) {
            }
            return undefined;
        };
        host.describer(function (value) {
            if (typeof value === 'string') {
                return value;
            }
            if (typeof value === 'bigint') {
                return bigIntToString.call(value);
            }
            if (typeof value === 'symbol') {
                return symbolToString.call(value);
            }
            if (typeof value === 'function') {
                return functionToString.call(value);
            }
            if (typeof value !== 'object' || value === null) {
                return `${value}`;
            }
            var toString = dataProperty(value, 'toString');
            if (isError(value) || toString === errorToString) {
                var name = dataProperty(value, 'name');
                var message = dataProperty(value, 'message');
                name = typeof name === 'string' ? name : '';
                message = typeof message === 'string' ? message : '';
                if (name === '' || message === '') {
                    return name + message;
                }
                return name + ': ' + message;
            }
            if (toString === objectToString) {
                var builder = dataProperty(value, 'constructor');
                var builderName = typeof builder === 'function' ? dataProperty(builder, 'name') : undefined;
                if (typeof builderName === 'string' && builderName !== '') {
                    return '#<' + builderName + '>';
                }
            }
            var tag = dataProperty(value, toStringTag);
            return typeof tag === 'string' ? '[object ' + tag + ']' : objectToString.call(value);
        });

        // ---- a date names its zone in full ---------------------------------------------------------------------
        var dateToString = Date.prototype.toString;
        var dateToTimeString = Date.prototype.toTimeString;
        var dateGetTime = Date.prototype.getTime;
        var stringLastIndexOf = String.prototype.lastIndexOf;
        var stringSubstring = String.prototype.substring;
        var named = function (date, text) {
            var open = stringLastIndexOf.call(text, ' (');
            if (open < 0) {
                return text;
            }
            return stringSubstring.call(text, 0, open) + ' (' + host.zoneName(dateGetTime.call(date)) + ')';
        };
        Date.prototype.toString = function toString() {
            return named(this, dateToString.call(this));
        };
        Date.prototype.toTimeString = function toTimeString() {
            return named(this, dateToTimeString.call(this));
        };

        // ---- a formatter names its zone as the account's engine knows it ----------------------------------------
        var dateFormatResolved = Intl.DateTimeFormat.prototype.resolvedOptions;
        Intl.DateTimeFormat.prototype.resolvedOptions = function resolvedOptions() {
            var options = dateFormatResolved.call(this);
            if (typeof options.timeZone === 'string') {
                options.timeZone = host.zoneId(options.timeZone);
            }
            return options;
        };

        // ---- JSON.parse refuses a text in the account's words ---------------------------------------------------
        var jsonParse = JSON.parse;
        var SyntaxErrorType = SyntaxError;
        JSON.parse = function parse(text, reviver) {
            var source = `${text}`;
            try {
                return jsonParse(source, reviver);
            } catch (fault) {
                if (fault instanceof SyntaxErrorType) {
                    var sentence = host.jsonFault(source);
                    if (sentence !== null) {
                        throw new SyntaxErrorType(sentence);
                    }
                }
                throw fault;
            }
        };
    })();

    // ---- no dynamic code: eval and the Function constructor refuse in the account's words; the wrapper keeps the
    // constructor's prototype, so `f instanceof Function` holds ---------------------------------------------------
    var refuse = function () {
        throw new EvalError('Dynamic code evaluation disallowed');
    };
    globalThis.eval = function eval() {
        return refuse();
    };
    globalThis.Function = new Proxy(globalThis.Function, {apply: refuse, construct: refuse});
})

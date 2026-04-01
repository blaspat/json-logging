/*
 * Copyright 2025 Blasius Patrick
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.blaspat.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.LayoutBase;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Custom JSON {@link LayoutBase} for structured log output.
 *
 * <p>Output fields:
 * <ul>
 *   <li>{@code timestamp} — ISO-8601 UTC</li>
 *   <li>{@code severity} — log level as integer</li>
 *   <li>{@code message} — log message</li>
 *   <li>{@code logger} — logger name</li>
 *   <li>{@code thread} — thread name</li>
 *   <li>{@code logType} — always "JSON"</li>
 *   <li>{@code correlationId} — from MDC</li>
 *   <li>{@code stackTrace} — only on ERROR</li>
 * </ul>
 */
public class JsonLayout extends LayoutBase<ILoggingEvent> {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;

    public JsonLayout() {
        this.objectMapper = new ObjectMapper()
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET)
                .registerModule(new JavaTimeModule());
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        StringWriter writer = new StringWriter(256);
        try {
            JsonGenerator gen = objectMapper.getFactory().createGenerator(writer);

            gen.writeStartObject();
            gen.writeStringField("timestamp", ISO_FORMATTER.format(Instant.ofEpochMilli(event.getTimeStamp())));
            gen.writeNumberField("severity", event.getLevel().toInteger());
            gen.writeStringField("message", event.getFormattedMessage());
            gen.writeStringField("logger", event.getLoggerName());
            gen.writeStringField("thread", event.getThreadName());
            gen.writeStringField("logType", "JSON");
            gen.writeStringField("correlationId", MDC.get("correlationId"));

            if (event.getThrowableProxy() != null) {
                gen.writeStringField("stackTrace", buildStackTrace(event));
            }

            gen.writeEndObject();
            gen.flush();
        } catch (IOException e) {
            return "{\"message\":\"" + event.getFormattedMessage().replace("\"", "\\\"") + "\"}\n";
        }

        return writer.toString() + System.lineSeparator();
    }

    private String buildStackTrace(ILoggingEvent event) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElementProxy step : event.getThrowableProxy().getStackTraceElementProxyArray()) {
            if (sb.length() > 0) {
                sb.append("\n  ");
            }
            sb.append(step.getSTEAsString());
        }
        return sb.toString();
    }
}

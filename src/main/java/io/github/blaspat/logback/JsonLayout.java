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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.contrib.jackson.JacksonJsonFormatter;
import org.slf4j.MDC;

import java.util.Map;

public class JsonLayout extends ch.qos.logback.contrib.json.classic.JsonLayout {
    @Override
    protected void addCustomDataToJsonMap(Map<String, Object> map, ILoggingEvent event) {
        map.put("severity", event.getLevel().toInteger());
        map.put("logId", MDC.get("x-correlation-id"));
        map.put("logType", "JSON");

        if (Level.ERROR == event.getLevel()) {
            StackTraceElementProxy[] stackTraceElementProxyArray = event.getThrowableProxy().getStackTraceElementProxyArray();
            if (null != stackTraceElementProxyArray && stackTraceElementProxyArray.length > 0) {
                StringBuilder stackTraceBuilder = new StringBuilder();
                for (StackTraceElementProxy stackTraceElementProxy : stackTraceElementProxyArray) {
                    stackTraceBuilder.append(stackTraceElementProxy.toString()).append(System.lineSeparator());
                }
                map.put("stackTrace", stackTraceBuilder.toString());
            }
        }

        super.addCustomDataToJsonMap(map, event);
    }

    public JsonLayout() {
        setTimestampFormat("yyyy-MM-dd'T'HH:mm:ss.SSSX");
        setTimestampFormatTimezoneId("Asia/Jakarta");
        setAppendLineSeparator(true);
        JacksonJsonFormatter formatter = new JacksonJsonFormatter();
        formatter.setPrettyPrint(false);
        setJsonFormatter(formatter);
    }
}

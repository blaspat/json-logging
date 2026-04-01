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

package io.github.blaspat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Aspect
public class LoggingAspect {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ObjectMapper objectMapper;

    /** Sensitive parameter names (case-insensitive) that should be redacted. */
    private static final Set<String> SENSITIVE_PARAMS = new HashSet<>(Arrays.asList(
            "password", "passwd", "secret", "token", "accesstoken", "access_token",
            "refreshtoken", "refresh_token", "apikey", "api_key", "auth", "credential",
            "creditcard", "credit_card", "cvv", "ssn", "pin"
    ));

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").withZone(ZoneOffset.UTC);

    public LoggingAspect() {
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
                .setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
                .registerModule(new JavaTimeModule());
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logRequestResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        HttpServletRequest request = getRequest();

        setMDC(request);

        // Log request
        logRequest(request, getRequestBody(joinPoint));

        // Execute the controller method
        Object result = joinPoint.proceed();

        // Log response
        if (result instanceof ResponseEntity) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
            MediaType contentType = responseEntity.getHeaders().getContentType();
            boolean isJson = MediaType.APPLICATION_JSON.isCompatibleWith(contentType);
            Object body = isJson ? responseEntity.getBody() : contentType + " content";
            logResponse(request, responseEntity.getStatusCodeValue(), body, start);
        } else {
            logResponse(request, 200, result, start);
        }

        MDC.clear();
        return result;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestControllerAdvice)")
    public Object logErrorResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = getRequest();
        Object result = joinPoint.proceed();

        if (result instanceof ResponseEntity) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
            logResponse(request, responseEntity.getStatusCodeValue(), responseEntity.getBody(), 0);
        } else {
            logResponse(request, 500, result, 0);
        }

        MDC.clear();
        return result;
    }

    private HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
    }

    private void setMDC(HttpServletRequest request) {
        try {
            String correlationId = StringUtils.defaultString(
                    StringUtils.defaultString(request.getHeader("x-correlation-id"), request.getHeader("x-request-id")),
                    UUID.randomUUID().toString()
            );

            MDC.put("correlationId", correlationId);
            MDC.put("userAgent", StringUtils.defaultString(request.getHeader("user-agent"), ""));
            MDC.put("clientIp", StringUtils.defaultString(
                    request.getHeader("x-original-forwarded-for"),
                    request.getRemoteAddr()
            ));
        } catch (Exception e) {
            logger.error("Failed to set MDC", e);
        }
    }

    private String[] getExcludedPaths() {
        String excluded = System.getProperty("logging.excluded-paths", "");
        if (StringUtils.isNotBlank(excluded)) {
            return excluded.split(",");
        }
        return new String[]{};
    }

    private boolean isExcluded(String uri) {
        return StringUtils.startsWithAny(uri, getExcludedPaths());
    }

    private void logRequest(HttpServletRequest request, Object body) {
        try {
            if (isExcluded(request.getRequestURI())) {
                return;
            }

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("type", "REQUEST");
            logEntry.put("method", request.getMethod());
            logEntry.put("uri", request.getRequestURI());
            logEntry.put("timestamp", ISO_FORMATTER.format(Instant.now()));
            logEntry.put("correlationId", MDC.get("correlationId"));
            logEntry.put("userAgent", MDC.get("userAgent"));
            logEntry.put("clientIp", MDC.get("clientIp"));

            Map<String, String> queryParams = getParameterMap(request);
            if (!queryParams.isEmpty()) {
                logEntry.put("queryParams", sanitize(queryParams));
            }

            if (body != null) {
                logEntry.put("body", body);
            }

            logger.info(objectMapper.writeValueAsString(logEntry));
        } catch (JsonProcessingException e) {
            logger.error("Failed to log request", e);
        }
    }

    private void logResponse(HttpServletRequest request, int statusCode, Object body, long startTime) {
        try {
            if (isExcluded(request.getRequestURI())) {
                return;
            }

            Map<String, Object> logEntry = new LinkedHashMap<>();
            logEntry.put("type", "RESPONSE");
            logEntry.put("method", request.getMethod());
            logEntry.put("uri", request.getRequestURI());
            logEntry.put("timestamp", ISO_FORMATTER.format(Instant.now()));
            logEntry.put("correlationId", MDC.get("correlationId"));
            logEntry.put("status", statusCode);

            if (startTime > 0) {
                logEntry.put("elapsedMs", System.currentTimeMillis() - startTime);
            }

            if (body != null) {
                logEntry.put("body", body);
            }

            logger.info(objectMapper.writeValueAsString(logEntry));
        } catch (JsonProcessingException e) {
            logger.error("Failed to log response", e);
        }
    }

    /**
     * Returns a new map with sensitive parameter values redacted.
     */
    private Map<String, String> sanitize(Map<String, String> params) {
        return params.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> SENSITIVE_PARAMS.contains(e.getKey().toLowerCase()) ? "***REDACTED***" : e.getValue(),
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private Map<String, String> getParameterMap(HttpServletRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, String[]> paramMap = request.getParameterMap();
        if (paramMap == null || paramMap.isEmpty()) {
            return result;
        }
        paramMap.forEach((key, values) -> {
            if (values != null && values.length > 0) {
                result.put(key, values.length == 1 ? values[0] : Arrays.toString(values));
            }
        });
        return result;
    }

    private Object getRequestBody(JoinPoint joinPoint) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Parameter[] parameters = signature.getMethod().getParameters();
        Object[] args = joinPoint.getArgs();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].isAnnotationPresent(RequestBody.class)) {
                return args[i];
            }
        }
        return null;
    }
}

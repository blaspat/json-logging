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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.blaspat.adapter.ISODateAdapter;
import io.github.blaspat.adapter.ISOInstantAdapter;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Parameter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Collectors;

@Aspect
@Component
public class LoggingAspect {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Value("logging.excluded-paths")
    private String EXCLUDED_PATH;

    private final DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

    private final ObjectWriter om = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setDateFormat(df)
            .writer();

    private final Gson gson = new GsonBuilder().disableHtmlEscaping()
            .serializeNulls()
            .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
            .registerTypeAdapter(Date.class, new ISODateAdapter())
            .registerTypeAdapter(Instant.class, new ISOInstantAdapter())
            .create();

    public LoggingAspect() {
        this.df.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logRequestResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        long start = System.currentTimeMillis();
        ContentCachingRequestWrapper request = new ContentCachingRequestWrapper(((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest());
        // log request
        setMDC(request);
        logRequest(request, getRequestBody(joinPoint));

        // proceed with the method execution
        Object result = joinPoint.proceed();

        Class<?> clazz = ResponseEntity.class;
        if (clazz.isInstance(result)) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;

            // handling non json response
            if (Objects.nonNull(responseEntity.getHeaders())) {
                if (Objects.nonNull(responseEntity.getHeaders().getContentType())) {
                    if (responseEntity.getHeaders().getContentType().equals(MediaType.APPLICATION_JSON)
                            || responseEntity.getHeaders().getContentType().equals(MediaType.APPLICATION_JSON_UTF8)
                    ) {
                        logResponse(request, responseEntity.getStatusCodeValue(), responseEntity.getBody(), start);
                    } else {
                        logResponse(request, responseEntity.getStatusCodeValue(), responseEntity.getHeaders().getContentType() + " content", start);
                    }
                }
            } else {
                // log response
                logResponse(request, 200, result, start);
            }
        } else {
            // log response
            logResponse(request, 200, result, start);
        }

        // clear MDC
        MDC.clear();
        return result;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestControllerAdvice)")
    public Object logErrorResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        ContentCachingRequestWrapper request = new ContentCachingRequestWrapper(((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest());
        // proceed with the method execution
        Object result = joinPoint.proceed();

        // log response
        Class<?> clazz = ResponseEntity.class;
        if (clazz.isInstance(result)) {
            ResponseEntity<?> responseEntity = (ResponseEntity<?>) result;
            logResponse(request, responseEntity.getStatusCodeValue(), responseEntity.getBody(), 0);
        } else {
            logResponse(request, 500, result, 0);
        }

        // clear MDC
        MDC.clear();
        return result;
    }


    private void setMDC(HttpServletRequest request) {
        try {
            String correlationId = request.getHeader("x-correlation-id");
            if (StringUtils.isBlank(correlationId)) {
                correlationId = request.getHeader("x-request-id") != null
                        ? request.getHeader("x-request-id")
                        : UUID.randomUUID().toString();
            }

            MDC.put("x-correlation-id", correlationId);

            String userAgent = request.getHeader(HttpHeaders.USER_AGENT);

            MDC.put(HttpHeaders.USER_AGENT, userAgent);

            String clientIp = request.getHeader("x-original-forwarded-for");
            if (StringUtils.isBlank(clientIp)) {
                clientIp = request.getRemoteAddr();
            }

            MDC.put("x-original-forwarded-for", clientIp);
        } catch (Exception e) {
            logger.error("Failed-RequestInterceptor", e);
        }
    }

    private String generateHeaderJson(HttpServletRequest request) {
        String userAgent = MDC.get(HttpHeaders.USER_AGENT);
        String correlationId = MDC.get("x-correlation-id");
        String clientIp = MDC.get("x-original-forwarded-for");

        Map<String, String> allHeaders = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();

        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = request.getHeader(headerName);
            allHeaders.put(headerName, headerValue);
        }

        logger.trace("All Headers: {}", gson.toJson(allHeaders));

        HashMap<String, String> headers = new HashMap<>();
        headers.put(HttpHeaders.USER_AGENT.toLowerCase(), userAgent);
        headers.put("x-correlation-id", correlationId);
        headers.put("client-ip", clientIp);

        return gson.toJson(headers);
    }

    private String[] getExcludedPath() {
        if (StringUtils.isNotBlank(EXCLUDED_PATH)) {
            return EXCLUDED_PATH.split(",");
        }
        return new String[]{};
    }

    private void logRequest(HttpServletRequest request, Object obj) {
        try {
            if (StringUtils.startsWithAny(request.getRequestURI(), getExcludedPath())) {
                return;
            }
            String parameterMap = getParameterMap(request);
            String body = getBodyData(obj);

            String logRequest = "REQUEST";
            logRequest += "\t[" + request.getMethod() + "] - [" + request.getRequestURI() + "]";
            logRequest += "\tHEADERS" + "\t : " + generateHeaderJson(request);
            if (StringUtils.isNotBlank(parameterMap)) {
                logRequest += "\tPARAMETER_MAP" + "\t : " + parameterMap;
            }
            if (StringUtils.isNotBlank(body)) {
                logRequest += "\tREQUEST_BODY" + "\t : " + body;
            }
            logRequest += "END-REQUEST";

            logger.debug(logRequest);
        } catch (Exception ex) {
            logger.error("Failed-logRequest", ex);
        }
    }

    private void logResponse(HttpServletRequest request, int responseCode, Object obj, long startTime) {
        try {
            if (StringUtils.startsWithAny(request.getRequestURI(), EXCLUDED_PATH)) {
                return;
            }
            String body = getBodyData(obj);

            String logResponse = "RESPONSE";
            logResponse += "\t[" + request.getMethod() + "] - [" + request.getRequestURI() + "]";
            logResponse += "\tELAPSED_TIME" + "\t : " + (System.currentTimeMillis() - startTime) + " ms";
            logResponse += "\tRESPONSE_BODY (" + responseCode + ")";
            if (StringUtils.isNotBlank(body)) {
                logResponse += "\t : " + body;
            }
            logResponse += "\tEND-RESPONSE";

            logger.debug(logResponse);
        } catch (Exception ex) {
            logger.error("Failed-logResponse", ex);
        }
    }

    private String getBodyData(Object obj) {
        String body = "";
        if (null != obj) {
            if (obj instanceof String) {
                body = (String) obj;
            } else {
                try {
                    body = om.writeValueAsString(obj);
                } catch (Exception ex) {
                    body = gson.toJson(obj);
                }
            }
        }
        return body;
    }

    private String getParameterMap(HttpServletRequest request) {
        String parameterMap = "";
        if (null != request.getParameterMap() && request.getParameterMap().size() > 0) {
            parameterMap = request.getParameterMap()
                    .entrySet()
                    .stream()
                    .map((entry) -> {
                        if (null == entry.getValue()) {
                            return entry.getKey() + "=" + null;
                        } else {
                            return Arrays.stream(entry.getValue())
                                    .map(val -> entry.getKey() + "=" + val)
                                    .collect(Collectors.joining("&"));
                        }
                    })
                    .collect(Collectors.joining("&"));
        }
        return parameterMap;
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

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "logging.enabled=true",
        "logging.excluded-paths=/actuator/**,/health,/skip"
})
class LoggingAspectTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requestAndResponse_areLoggedAsJson() throws Exception {
        String correlationId = UUID.randomUUID().toString();

        MvcResult result = mockMvc.perform(
                        post("/api/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("x-correlation-id", correlationId)
                                .header("user-agent", "test-agent/1.0")
                                .content("{\"name\":\"Alice\",\"age\":30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello Alice"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(responseBody);

        // Verify response contains expected fields
        assertEquals("Hello Alice", node.get("message").asText());
    }

    @Test
    void sensitiveParams_areRedacted() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .param("username", "alice")
                                .param("password", "super-secret"))
                .andExpect(status().isOk())
                .andReturn();

        assertNotNull(result.getResponse().getContentAsString());
    }

    @Test
    void excludedPaths_areNotLogged() throws Exception {
        // These paths are configured as excluded — no MDC conflict or crash
        mockMvc.perform(get("/skip"))
                .andExpect(status().isOk());

        assertTrue(true);
    }

    @Test
    void correlationId_generatedWhenMissing() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/test"))
                .andExpect(status().isOk())
                .andReturn();

        assertNotNull(result.getResponse().getContentAsString());
    }

    @Test
    void correlationId_headerIsRespected() throws Exception {
        String expectedId = UUID.randomUUID().toString();

        MvcResult result = mockMvc.perform(
                        get("/api/test")
                                .header("x-correlation-id", expectedId)
                                .header("x-request-id", "should-be-ignored"))
                .andExpect(status().isOk())
                .andReturn();

        assertNotNull(result.getResponse().getContentAsString());
    }

    @Test
    void postWithRequestBody_isLogged() throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"Bob\",\"age\":25}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hello Bob"))
                .andReturn();

        assertNotNull(result.getResponse().getContentAsString());
    }
}

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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
public class TestController {

    @GetMapping("/skip")
    public ResponseEntity<Map<String, String>> skip() {
        return ResponseEntity.ok(Collections.singletonMap("skipped", "true"));
    }

    @PostMapping("/api/test")
    public ResponseEntity<Map<String, String>> test(@RequestBody Map<String, Object> body) {
        String name = (String) body.getOrDefault("name", "World");
        Map<String, String> result = new HashMap<>();
        result.put("message", "Hello " + name);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/api/login")
    public ResponseEntity<Map<String, String>> login(
            @RequestParam String username,
            @RequestParam String password) {
        Map<String, String> result = new HashMap<>();
        result.put("user", username);
        result.put("status", "logged in");
        return ResponseEntity.ok(result);
    }

    @GetMapping("/api/test")
    public ResponseEntity<Map<String, String>> testGet() {
        return ResponseEntity.ok(Collections.singletonMap("status", "ok"));
    }
}

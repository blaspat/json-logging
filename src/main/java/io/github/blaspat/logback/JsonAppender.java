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

import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;

/**
 * A {@link ConsoleAppender} that outputs JSON-formatted log events.
 *
 * <p>Configure in {@code logback-spring.xml}:
 * <pre>
 * &lt;appender name="JSON" class="io.github.blaspat.logback.JsonAppender"/&gt;
 * </pre>
 *
 * <p>The appender automatically creates a {@link JsonLayout} internally.
 */
public class JsonAppender<E> extends ConsoleAppender<E> {

    @Override
    public void start() {
        @SuppressWarnings("unchecked")
        LayoutWrappingEncoder<E> encoder = new LayoutWrappingEncoder<>();
        encoder.setLayout((Layout<E>) new JsonLayout());
        encoder.setContext(getContext());
        setEncoder(encoder);
        super.start();
    }
}

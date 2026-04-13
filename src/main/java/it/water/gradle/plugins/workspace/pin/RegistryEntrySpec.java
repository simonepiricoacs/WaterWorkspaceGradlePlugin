/*
 * Copyright 2024 Aristide Cittadino
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
package it.water.gradle.plugins.workspace.pin;

/**
 * Describes a single entry published into the Water property registry.
 *
 * <p>A registry entry registers a {@code value} under a {@code key}.
 * Any module that declares a property with the same {@code key} can have its
 * value automatically resolved by a configuration UI.
 *
 * <pre>
 * waterDescriptor {
 *     registry {
 *         entry('water.authentication.issuer') {
 *             value = 'water'
 *         }
 *     }
 * }
 * </pre>
 */
public class RegistryEntrySpec {

    private final String key;
    private String value = "";

    public RegistryEntrySpec(String key) {
        this.key = key;
    }

    public String getKey() { return key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
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

import groovy.json.JsonOutput;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * Gradle task that generates the {@code <artifactId>-<version>.water.json} descriptor
 * and writes it to {@code build/water/}.
 *
 * <p>The descriptor is published alongside the JAR as a standalone Maven artifact
 * with extension {@code water.json} (no classifier).
 *
 * <p>The full descriptor JSON is pre-built during configuration time (in
 * {@code afterEvaluate}) and passed as a single {@code @Input} string so that
 * Gradle's incremental-build machinery can detect changes correctly.
 */
public abstract class GenerateWaterDescriptorTask extends DefaultTask {

    /**
     * The complete descriptor JSON, pre-serialized during configuration.
     * Used as the sole {@code @Input} so Gradle re-runs the task whenever
     * the content changes.
     */
    @Input
    public abstract Property<String> getDescriptorJson();

    /** Output file: {@code build/water/<artifactId>-<version>.water.json} */
    @OutputFile
    public abstract RegularFileProperty getOutputFile();

    @TaskAction
    public void generate() throws IOException {
        File out = getOutputFile().getAsFile().get();
        out.getParentFile().mkdirs();
        Files.write(out.toPath(), getDescriptorJson().get().getBytes(StandardCharsets.UTF_8));
    }

    // -------------------------------------------------------------------------
    // Static builder — called by the plugin during afterEvaluate
    // -------------------------------------------------------------------------

    /**
     * Builds the pretty-printed JSON descriptor string from the extension data.
     */
    public static String buildDescriptorJson(
            String artifactId,
            String moduleId,
            String displayName,
            String description,
            String componentsPackage,
            String repositoryPackage,
            String entitiesPackage,
            List<OutputPinSpec> outputPins,
            List<InputPinSpec> inputPins,
            List<PinPropertySpec> moduleProperties,
            List<RegistryEntrySpec> registryEntries) {

        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put("schemaVersion", "1.0");
        descriptor.put("artifactId",    artifactId);
        descriptor.put("moduleId",      moduleId);
        descriptor.put("displayName",   displayName);
        descriptor.put("description",   description);
        descriptor.put("componentsPackage", componentsPackage != null ? componentsPackage : "");
        descriptor.put("repositoryPackage", repositoryPackage != null ? repositoryPackage : "");
        descriptor.put("entitiesPackage",   entitiesPackage   != null ? entitiesPackage   : "");

        descriptor.put("registry",    buildRegistryList(registryEntries));
        descriptor.put("properties",  buildModulePropertiesList(moduleProperties));

        Map<String, Object> pins = new LinkedHashMap<>();
        pins.put("output", buildOutputList(outputPins));
        pins.put("input",  buildInputList(inputPins));
        descriptor.put("pins", pins);

        return JsonOutput.prettyPrint(JsonOutput.toJson(descriptor));
    }

    private static List<Map<String, Object>> buildRegistryList(List<RegistryEntrySpec> registryEntries) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (RegistryEntrySpec entry : registryEntries) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key",   entry.getKey());
            m.put("value", entry.getValue());
            list.add(m);
        }
        return list;
    }

    private static List<Map<String, Object>> buildModulePropertiesList(List<PinPropertySpec> moduleProperties) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (PinPropertySpec prop : moduleProperties) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key",          prop.getKey());
            m.put("name",         prop.getName().isEmpty() ? prop.getKey() : prop.getName());
            m.put("type",         prop.getType());
            m.put("envVar",       prop.getEnvVar());
            m.put("required",     prop.isRequired());
            m.put("sensitive",    prop.isSensitive());
            m.put("defaultValue", prop.getDefaultValue());
            m.put("description",  prop.getDescription());
            list.add(m);
        }
        return list;
    }

    private static List<Map<String, Object>> buildOutputList(List<OutputPinSpec> outputPins) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (OutputPinSpec pin : outputPins) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       pin.getId());
            m.put("required", pin.isRequired());

            List<Map<String, Object>> props = new ArrayList<>();
            for (PinPropertySpec prop : pin.getProperties()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("key",          prop.getKey());
                pm.put("name",         prop.getName().isEmpty() ? prop.getKey() : prop.getName());
                pm.put("required",     prop.isRequired());
                pm.put("sensitive",    prop.isSensitive());
                pm.put("defaultValue", prop.getDefaultValue());
                pm.put("description",  prop.getDescription());
                props.add(pm);
            }
            m.put("properties", props);
            list.add(m);
        }
        return list;
    }

    private static List<Map<String, Object>> buildInputList(List<InputPinSpec> inputPins) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (InputPinSpec pin : inputPins) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id",       pin.getId());
            m.put("required", pin.isRequired());

            List<Map<String, Object>> props = new ArrayList<>();
            for (PinPropertySpec prop : pin.getProperties()) {
                Map<String, Object> pm = new LinkedHashMap<>();
                pm.put("key",          prop.getKey());
                pm.put("name",         prop.getName().isEmpty() ? prop.getKey() : prop.getName());
                pm.put("required",     prop.isRequired());
                pm.put("sensitive",    prop.isSensitive());
                pm.put("defaultValue", prop.getDefaultValue());
                pm.put("description",  prop.getDescription());
                props.add(pm);
            }
            m.put("properties", props);
            list.add(m);
        }
        return list;
    }
}

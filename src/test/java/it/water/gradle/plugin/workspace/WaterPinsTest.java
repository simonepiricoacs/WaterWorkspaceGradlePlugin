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

package it.water.gradle.plugin.workspace;

import it.water.gradle.plugins.workspace.pin.*;
import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Water Framework PIN management system.
 *
 * <p>Covers:
 * <ul>
 *   <li>Unit tests for {@link StandardPins} catalog</li>
 *   <li>Unit tests for {@link PinOutputContainer} DSL</li>
 *   <li>Unit tests for {@link PinInputContainer} DSL</li>
 *   <li>Unit tests for {@link GenerateWaterDescriptorTask#buildDescriptorJson}</li>
 *   <li>Integration tests using GradleRunner against TestPinProject</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class WaterPinsTest {

    @TempDir(cleanup = CleanupMode.ON_SUCCESS)
    File junitTmpWorkspaceDir;

    @BeforeEach
    void setup() throws IOException {
        Path testProjectResourceDirectory = Paths.get("testProjects");
        FileUtils.copyDirectoryToDirectory(testProjectResourceDirectory.toFile(), junitTmpWorkspaceDir);

        List<String> entries = GradleRunner.create()
                .withPluginClasspath()
                .getPluginClasspath()
                .stream()
                .map(File::getAbsolutePath)
                .collect(Collectors.toList());

        // Write plugin-classpath.txt so settings.gradle loads the plugin under test
        // instead of resolving it from mavenLocal (which may be stale).
        File pinProjectDir = new File(junitTmpWorkspaceDir, "testProjects/TestPinProject");
        Files.write(new File(pinProjectDir, "plugin-classpath.txt").toPath(), entries);

        File pinInheritProjectDir = new File(junitTmpWorkspaceDir, "testProjects/TestPinInheritProject");
        Files.write(new File(pinInheritProjectDir, "plugin-classpath.txt").toPath(), entries);
    }

    // =========================================================================
    // StandardPins — catalog unit tests
    // =========================================================================

    @Test
    @Order(10)
    void standardPinsCatalogContainsAllFiveEntries() {
        assertNotNull(StandardPins.get("jdbc"));
        assertNotNull(StandardPins.get("api-gateway"));
        assertNotNull(StandardPins.get("service-discovery"));
        assertNotNull(StandardPins.get("cluster-coordinator"));
        assertNotNull(StandardPins.get("authentication-issuer"));
    }

    @Test
    @Order(11)
    void standardPinsUnknownShorthandReturnsNull() {
        assertNull(StandardPins.get("not-a-real-pin"));
    }

    @Test
    @Order(12)
    void standardPinsGetReturnsCopyNotSameInstance() {
        OutputPinSpec first = StandardPins.get("jdbc");
        OutputPinSpec second = StandardPins.get("jdbc");
        assertNotSame(first, second);
    }

    @Test
    @Order(13)
    void standardPinsMutatingCopyDoesNotAffectCatalog() {
        OutputPinSpec copy1 = StandardPins.get("jdbc");
        boolean originalRequired = copy1.isRequired();
        copy1.setRequired(!originalRequired);

        OutputPinSpec copy2 = StandardPins.get("jdbc");
        assertEquals(originalRequired, copy2.isRequired(),
                "Mutating a copy must not change the catalog entry");
    }

    @Test
    @Order(14)
    void jdbcStandardPinHasCorrectIdAndProperties() {
        OutputPinSpec jdbc = StandardPins.get("jdbc");
        assertEquals("it.water.persistence.jdbc", jdbc.getId());
        assertTrue(jdbc.isRequired());
        assertFalse(jdbc.getProperties().isEmpty());

        List<PinPropertySpec> props = jdbc.getProperties();
        assertTrue(props.stream().anyMatch(p -> "db.host".equals(p.getKey())));
        assertTrue(props.stream().anyMatch(p -> "db.password".equals(p.getKey()) && p.isSensitive()));
    }

    @Test
    @Order(15)
    void apiGatewayStandardPinIsOptionalByDefault() {
        OutputPinSpec apiGw = StandardPins.get("api-gateway");
        assertEquals("it.water.api-gateway", apiGw.getId());
        assertFalse(apiGw.isRequired());
    }

    @Test
    @Order(16)
    void authenticationIssuerHasExpectedProperty() {
        OutputPinSpec issuer = StandardPins.get("authentication-issuer");
        assertEquals("it.water.integration.authentication-issuer", issuer.getId());
        assertTrue(issuer.isRequired());
        assertEquals(1, issuer.getProperties().size());
        assertEquals("water.authentication.service.issuer", issuer.getProperties().get(0).getKey());
    }

    // =========================================================================
    // PinOutputContainer — DSL unit tests
    // =========================================================================

    @Test
    @Order(20)
    void outputContainerPinAddsCustomSpec() {
        PinOutputContainer container = new PinOutputContainer();
        container.pin("it.water.custom.pin", spec ->
                spec.property("my.key", prop -> {
                    prop.setRequired(true);
                    prop.setDefaultValue("default");
                }));

        List<OutputPinSpec> pins = container.getPins();
        assertEquals(1, pins.size());
        assertEquals("it.water.custom.pin", pins.get(0).getId());
        assertEquals(1, pins.get(0).getProperties().size());
        assertEquals("my.key", pins.get(0).getProperties().get(0).getKey());
        assertEquals("default", pins.get(0).getProperties().get(0).getDefaultValue());
    }

    @Test
    @Order(21)
    void outputContainerStandardPinAddsFromCatalog() {
        PinOutputContainer container = new PinOutputContainer();
        container.standardPin("jdbc");

        assertEquals(1, container.getPins().size());
        assertEquals("it.water.persistence.jdbc", container.getPins().get(0).getId());
        assertFalse(container.getPins().get(0).getProperties().isEmpty());
    }

    @Test
    @Order(22)
    void outputContainerStandardPinWithActionExtendsProperties() {
        PinOutputContainer container = new PinOutputContainer();
        int basePropertyCount = StandardPins.get("jdbc").getProperties().size();

        container.standardPin("jdbc", spec ->
                spec.property("db.schema", prop -> {
                    prop.setRequired(false);
                    prop.setDefaultValue("public");
                }));

        OutputPinSpec pin = container.getPins().get(0);
        assertEquals(basePropertyCount + 1, pin.getProperties().size());
        assertEquals("db.schema", pin.getProperties().get(pin.getProperties().size() - 1).getKey());
    }

    @Test
    @Order(23)
    void outputContainerUnknownStandardPinThrowsGradleException() {
        PinOutputContainer container = new PinOutputContainer();
        assertThrows(GradleException.class, () -> container.standardPin("not-a-real-pin"));
    }

    @Test
    @Order(24)
    void outputContainerUnknownStandardPinWithActionThrowsGradleException() {
        PinOutputContainer container = new PinOutputContainer();
        assertThrows(GradleException.class, () -> container.standardPin("not-a-real-pin", spec -> {}));
    }

    @Test
    @Order(25)
    void outputContainerGetPinsReturnsUnmodifiableView() {
        PinOutputContainer container = new PinOutputContainer();
        container.standardPin("jdbc");
        assertThrows(UnsupportedOperationException.class,
                () -> container.getPins().add(new OutputPinSpec("extra")));
    }

    // =========================================================================
    // PinInputContainer — DSL unit tests
    // =========================================================================

    @Test
    @Order(30)
    void inputContainerPinDefaultsToRequired() {
        PinInputContainer container = new PinInputContainer();
        container.pin("it.water.some.service");

        assertEquals(1, container.getPins().size());
        assertEquals("it.water.some.service", container.getPins().get(0).getId());
        assertTrue(container.getPins().get(0).isRequired());
    }

    @Test
    @Order(31)
    void inputContainerPinWithActionAllowsRequiredOverride() {
        PinInputContainer container = new PinInputContainer();
        container.pin("it.water.some.service", spec -> spec.setRequired(false));

        assertFalse(container.getPins().get(0).isRequired());
    }

    @Test
    @Order(32)
    void inputContainerStandardPinInheritsRequiredFromCatalog() {
        PinInputContainer container = new PinInputContainer();
        container.standardPin("jdbc");          // required=true in catalog
        container.standardPin("api-gateway");   // required=false in catalog

        assertTrue(container.getPins().get(0).isRequired());
        assertFalse(container.getPins().get(1).isRequired());
    }

    @Test
    @Order(33)
    void inputContainerStandardPinWithActionOverridesRequired() {
        PinInputContainer container = new PinInputContainer();
        container.standardPin("jdbc", spec -> spec.setRequired(false));

        assertFalse(container.getPins().get(0).isRequired());
    }

    @Test
    @Order(34)
    void inputContainerUnknownStandardPinThrowsGradleException() {
        PinInputContainer container = new PinInputContainer();
        assertThrows(GradleException.class, () -> container.standardPin("bad-pin"));
    }

    @Test
    @Order(35)
    void inputContainerUnknownStandardPinWithActionThrowsGradleException() {
        PinInputContainer container = new PinInputContainer();
        assertThrows(GradleException.class, () -> container.standardPin("bad-pin", spec -> {}));
    }

    @Test
    @Order(36)
    void inputContainerGetPinsReturnsUnmodifiableView() {
        PinInputContainer container = new PinInputContainer();
        container.pin("it.water.some.service");
        assertThrows(UnsupportedOperationException.class,
                () -> container.getPins().add(new InputPinSpec("extra", true)));
    }

    // =========================================================================
    // GenerateWaterDescriptorTask.buildDescriptorJson() — unit tests
    // =========================================================================

    @Test
    @Order(40)
    void buildDescriptorJsonContainsSchemaVersion() {
        String json = GenerateWaterDescriptorTask.buildDescriptorJson(
                "it.water:test-module:1.0", "it.water.test", "Test Module", "",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(json.contains("\"schemaVersion\""));
        assertTrue(json.contains("\"1.0\""));
    }

    @Test
    @Order(41)
    void buildDescriptorJsonContainsArtifactModuleAndDisplayName() {
        String json = GenerateWaterDescriptorTask.buildDescriptorJson(
                "it.water:test-module:3.0.0", "it.water.test", "My Test Module", "",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(json.contains("it.water:test-module:3.0.0"));
        assertTrue(json.contains("it.water.test"));
        assertTrue(json.contains("My Test Module"));
    }

    @Test
    @Order(42)
    void buildDescriptorJsonWithEmptyPinsProducesOutputAndInputKeys() {
        String json = GenerateWaterDescriptorTask.buildDescriptorJson(
                "a:b:1", "it.water.m", "M", "",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(json.contains("\"output\""));
        assertTrue(json.contains("\"input\""));
    }

    @Test
    @Order(43)
    void buildDescriptorJsonOutputPinsIncludeIdAndProperties() {
        PinOutputContainer out = new PinOutputContainer();
        out.standardPin("authentication-issuer");

        String json = GenerateWaterDescriptorTask.buildDescriptorJson(
                "a:b:1", "it.water.m", "M", "",
                out.getPins(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(json.contains("it.water.integration.authentication-issuer"));
        assertTrue(json.contains("water.authentication.service.issuer"));
    }

    @Test
    @Order(44)
    void buildDescriptorJsonInputPinsIncludeIdAndRequiredFlag() {
        PinInputContainer in = new PinInputContainer();
        in.standardPin("jdbc");
        in.standardPin("api-gateway");

        String json = GenerateWaterDescriptorTask.buildDescriptorJson(
                "a:b:1", "it.water.m", "M", "",
                Collections.emptyList(), in.getPins(), Collections.emptyList(), Collections.emptyList());

        assertTrue(json.contains("it.water.persistence.jdbc"));
        assertTrue(json.contains("it.water.api-gateway"));
    }

    @Test
    @Order(45)
    void buildDescriptorJsonCustomOutputPinAppearsInJson() {
        PinOutputContainer out = new PinOutputContainer();
        out.pin("it.water.integration.custom", spec ->
                spec.property("custom.property.key", prop -> {
                    prop.setRequired(true);
                    prop.setSensitive(false);
                    prop.setDefaultValue("custom-default");
                }));

        String json = GenerateWaterDescriptorTask.buildDescriptorJson(
                "a:b:1", "it.water.m", "M", "",
                out.getPins(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());

        assertTrue(json.contains("it.water.integration.custom"));
        assertTrue(json.contains("custom.property.key"));
        assertTrue(json.contains("custom-default"));
    }

    // =========================================================================
    // ModuleRegistryContainer — DSL unit tests
    // =========================================================================

    @Test
    @Order(70)
    void registryContainerEntryAddsSpec() {
        ModuleRegistryContainer container = new ModuleRegistryContainer();
        container.entry("water.authentication.issuer", spec -> spec.setValue("water"));

        List<RegistryEntrySpec> entries = container.getEntries();
        assertEquals(1, entries.size());
        assertEquals("water.authentication.issuer", entries.get(0).getKey());
        assertEquals("water", entries.get(0).getValue());
    }

    @Test
    @Order(71)
    void registryContainerMultipleEntriesAreAllStored() {
        ModuleRegistryContainer container = new ModuleRegistryContainer();
        container.entry("key.one", spec -> spec.setValue("val1"));
        container.entry("key.two", spec -> spec.setValue("val2"));

        assertEquals(2, container.getEntries().size());
        assertEquals("key.one",  container.getEntries().get(0).getKey());
        assertEquals("key.two",  container.getEntries().get(1).getKey());
        assertEquals("val2",     container.getEntries().get(1).getValue());
    }

    @Test
    @Order(72)
    void registryContainerGetEntriesReturnsUnmodifiableView() {
        ModuleRegistryContainer container = new ModuleRegistryContainer();
        container.entry("some.key", spec -> spec.setValue("v"));
        assertThrows(UnsupportedOperationException.class,
                () -> container.getEntries().add(new RegistryEntrySpec("extra")));
    }

    @Test
    @Order(73)
    void buildDescriptorJsonRegistryEntriesAppearInJson() {
        ModuleRegistryContainer registry = new ModuleRegistryContainer();
        registry.entry("water.authentication.issuer", spec -> spec.setValue("water"));

        String json = GenerateWaterDescriptorTask.buildDescriptorJson(
                "a:b:1", "it.water.m", "M", "",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                registry.getEntries());

        assertTrue(json.contains("\"registry\""),                   "JSON must contain registry section");
        assertTrue(json.contains("water.authentication.issuer"),    "JSON must contain the registry key");
        assertTrue(json.contains("\"water\""),                      "JSON must contain the registry value");
    }

    @Test
    @Order(74)
    void buildDescriptorJsonEmptyRegistryProducesEmptyArray() {
        String json = GenerateWaterDescriptorTask.buildDescriptorJson(
                "a:b:1", "it.water.m", "M", "",
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.emptyList());

        assertTrue(json.contains("\"registry\""), "JSON must always contain the registry key");
    }

    // =========================================================================
    // Integration tests — GradleRunner against TestPinProject
    // =========================================================================

    @Test
    @Order(50)
    void integration_generateWaterDescriptorTask_producesJsonFile() {
        File pinProjectDir = new File(junitTmpWorkspaceDir, "testProjects/TestPinProject");

        BuildResult result = GradleRunner.create()
                .withProjectDir(pinProjectDir)
                .withArguments(":TestPinSubProject:generateWaterDescriptor", "--info")
                .build();

        assertNotNull(result);

        File waterDir = new File(pinProjectDir, "TestPinSubProject/build/water");
        assertTrue(waterDir.exists(), "build/water directory must exist after task execution");

        File[] jsonFiles = waterDir.listFiles((d, name) -> name.endsWith(".water.json"));
        assertNotNull(jsonFiles);
        assertEquals(1, jsonFiles.length, "Exactly one .water.json descriptor must be generated");
    }

    @Test
    @Order(51)
    void integration_generateWaterDescriptorTask_jsonContainsExpectedStructure() throws IOException {
        File pinProjectDir = new File(junitTmpWorkspaceDir, "testProjects/TestPinProject");

        GradleRunner.create()
                .withProjectDir(pinProjectDir)
                .withArguments(":TestPinSubProject:generateWaterDescriptor")
                .build();

        File waterDir = new File(pinProjectDir, "TestPinSubProject/build/water");
        File[] jsonFiles = waterDir.listFiles((d, name) -> name.endsWith(".water.json"));
        assertNotNull(jsonFiles);
        assertEquals(1, jsonFiles.length);

        String json = new String(Files.readAllBytes(jsonFiles[0].toPath()), StandardCharsets.UTF_8);

        assertTrue(json.contains("schemaVersion"),            "JSON must contain schemaVersion");
        assertTrue(json.contains("it.water.test.module"),     "JSON must contain moduleId");
        assertTrue(json.contains("Test Module"),              "JSON must contain displayName");
        assertTrue(json.contains("it.water.persistence.jdbc"),
                "JSON must contain the standardPin jdbc output");
        assertTrue(json.contains("it.water.integration.authentication-issuer"),
                "JSON must contain the custom output pin");
        assertTrue(json.contains("water.authentication.service.issuer"),
                "JSON must contain property key from custom output pin");
        // Input PINs
        assertTrue(json.contains("\"input\""),                "JSON must contain input section");
    }

    @Test
    @Order(52)
    void integration_noPinsModule_doesNotRegisterGenerateWaterDescriptorTask() {
        File pinProjectDir = new File(junitTmpWorkspaceDir, "testProjects/TestPinProject");

        BuildResult result = GradleRunner.create()
                .withProjectDir(pinProjectDir)
                .withArguments(":TestNoPinsProject:tasks", "--all")
                .build();

        assertFalse(result.getOutput().contains("generateWaterDescriptor"),
                "generateWaterDescriptor must NOT be registered for a module without waterDescriptor.moduleId");
    }

    @Test
    @Order(53)
    void integration_generateWaterDescriptorTask_isUpToDateOnSecondRun() {
        File pinProjectDir = new File(junitTmpWorkspaceDir, "testProjects/TestPinProject");

        // First run — executes the task
        GradleRunner.create()
                .withProjectDir(pinProjectDir)
                .withArguments(":TestPinSubProject:generateWaterDescriptor")
                .build();

        // Second run — should be UP-TO-DATE (no input change)
        BuildResult result = GradleRunner.create()
                .withProjectDir(pinProjectDir)
                .withArguments(":TestPinSubProject:generateWaterDescriptor")
                .build();

        TaskOutcome outcome = result.task(":TestPinSubProject:generateWaterDescriptor").getOutcome();
        assertEquals(TaskOutcome.UP_TO_DATE, outcome,
                "Task must be UP-TO-DATE on second run when inputs have not changed");
    }

    // =========================================================================
    // WaterPinsExtension.inheritsFrom — unit tests
    // =========================================================================

    @Test
    @Order(60)
    void extensionInheritsFromStoresProject() {
        Project parent = ProjectBuilder.builder().withName("parent-module").build();
        WaterPinsExtension ext = new WaterPinsExtension();

        ext.inheritsFrom(parent);

        assertEquals(1, ext.getInheritsFromProjects().size());
        assertSame(parent, ext.getInheritsFromProjects().get(0));
    }

    @Test
    @Order(61)
    void extensionInheritsFromAccumulatesMultipleProjects() {
        Project parent1 = ProjectBuilder.builder().withName("parent-one").build();
        Project parent2 = ProjectBuilder.builder().withName("parent-two").build();
        WaterPinsExtension ext = new WaterPinsExtension();

        ext.inheritsFrom(parent1);
        ext.inheritsFrom(parent2);

        assertEquals(2, ext.getInheritsFromProjects().size());
        assertSame(parent1, ext.getInheritsFromProjects().get(0));
        assertSame(parent2, ext.getInheritsFromProjects().get(1));
    }

    @Test
    @Order(62)
    void extensionGetInheritsFromProjectsReturnsUnmodifiableList() {
        Project parent = ProjectBuilder.builder().withName("parent-module").build();
        WaterPinsExtension ext = new WaterPinsExtension();
        ext.inheritsFrom(parent);

        assertThrows(UnsupportedOperationException.class,
                () -> ext.getInheritsFromProjects().add(parent));
    }

    @Test
    @Order(63)
    void extensionWithNoInheritsFromReturnsEmptyList() {
        WaterPinsExtension ext = new WaterPinsExtension();
        assertNotNull(ext.getInheritsFromProjects());
        assertTrue(ext.getInheritsFromProjects().isEmpty());
    }

    // =========================================================================
    // Integration tests — inheritsFrom (TestPinInheritProject)
    // =========================================================================

    @Test
    @Order(54)
    void integration_inheritsFrom_descriptorContainsParentOutputPins() throws IOException {
        File inheritProjectDir = new File(junitTmpWorkspaceDir, "testProjects/TestPinInheritProject");

        GradleRunner.create()
                .withProjectDir(inheritProjectDir)
                .withArguments(":TestPinSpringModule:generateWaterDescriptor")
                .build();

        File waterDir = new File(inheritProjectDir, "TestPinSpringModule/build/water");
        File[] jsonFiles = waterDir.listFiles((d, name) -> name.endsWith(".water.json"));
        assertNotNull(jsonFiles);
        assertEquals(1, jsonFiles.length);

        String json = new String(Files.readAllBytes(jsonFiles[0].toPath()), StandardCharsets.UTF_8);

        assertTrue(json.contains("it.water.test.service.spring"),
                "JSON must contain the Spring module moduleId");
        assertTrue(json.contains("it.water.integration.authentication-issuer"),
                "JSON must contain the output PIN inherited from the service module");
        assertTrue(json.contains("it.water.test.model.TestUser"),
                "JSON must contain the defaultValue inherited from the service module");
    }

    @Test
    @Order(55)
    void integration_inheritsFrom_descriptorContainsParentInputPins() throws IOException {
        File inheritProjectDir = new File(junitTmpWorkspaceDir, "testProjects/TestPinInheritProject");

        GradleRunner.create()
                .withProjectDir(inheritProjectDir)
                .withArguments(":TestPinSpringModule:generateWaterDescriptor")
                .build();

        File waterDir = new File(inheritProjectDir, "TestPinSpringModule/build/water");
        File[] jsonFiles = waterDir.listFiles((d, name) -> name.endsWith(".water.json"));
        assertNotNull(jsonFiles);
        assertEquals(1, jsonFiles.length);

        String json = new String(Files.readAllBytes(jsonFiles[0].toPath()), StandardCharsets.UTF_8);

        assertTrue(json.contains("it.water.persistence.jdbc"),
                "JSON must contain the input PIN (jdbc) inherited from the service module");
    }
}

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

import org.gradle.api.Action;
import org.gradle.api.Project;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Top-level DSL extension registered as {@code waterDescriptor} on every sub-project.
 *
 * <pre>
 * waterDescriptor {
 *     moduleId    = 'it.water.user'
 *     displayName = 'User Service'
 *
 *     output {
 *         standardPin 'authentication-issuer'
 *     }
 *
 *     input {
 *         standardPin 'jdbc'
 *     }
 * }
 * </pre>
 *
 * A module with no PINs simply omits the {@code waterDescriptor} block — no descriptor
 * will be generated or published for it.
 *
 * <p>To inherit all PINs (input and output) from another project's {@code waterDescriptor}
 * declaration use {@code inheritsFrom}:
 * <pre>
 * waterDescriptor {
 *     moduleId    = 'it.water.user.spring'
 *     displayName = 'User Service Spring'
 *     inheritsFrom project(':User-service')
 * }
 * </pre>
 * Own {@code input} / {@code output} blocks are merged on top of the inherited ones.
 */
public class WaterPinsExtension {

    private String moduleId;
    private String displayName;
    private String description;
    private final PinOutputContainer output = new PinOutputContainer();
    private final PinInputContainer input = new PinInputContainer();
    private final ModulePropertiesContainer properties = new ModulePropertiesContainer();
    private final ModuleRegistryContainer registry = new ModuleRegistryContainer();
    private final List<Project> inheritsFromProjects = new ArrayList<>();

    public void output(Action<PinOutputContainer> action) {
        action.execute(output);
    }

    public void input(Action<PinInputContainer> action) {
        action.execute(input);
    }

    public void properties(Action<ModulePropertiesContainer> action) {
        action.execute(properties);
    }

    public void registry(Action<ModuleRegistryContainer> action) {
        action.execute(registry);
    }

    /**
     * Inherit all input and output PINs declared in {@code project}'s {@code waterDescriptor} block.
     * Can be called multiple times to inherit from several projects.
     * Pins declared in own {@code input}/{@code output} blocks are merged on top.
     */
    public void inheritsFrom(Project project) {
        inheritsFromProjects.add(project);
    }

    public String getModuleId() { return moduleId; }
    public void setModuleId(String moduleId) { this.moduleId = moduleId; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public PinOutputContainer getOutput() { return output; }
    public PinInputContainer getInput() { return input; }
    public ModulePropertiesContainer getProperties() { return properties; }
    public ModuleRegistryContainer getRegistry() { return registry; }

    public List<Project> getInheritsFromProjects() { return Collections.unmodifiableList(inheritsFromProjects); }
}

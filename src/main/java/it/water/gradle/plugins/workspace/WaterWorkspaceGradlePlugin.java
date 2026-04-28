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

package it.water.gradle.plugins.workspace;

import groovy.json.JsonOutput;
import it.water.gradle.plugins.workspace.pin.GenerateWaterDescriptorTask;
import it.water.gradle.plugins.workspace.pin.InputPinSpec;
import it.water.gradle.plugins.workspace.pin.OutputPinSpec;
import it.water.gradle.plugins.workspace.pin.PinPropertySpec;
import it.water.gradle.plugins.workspace.pin.RegistryEntrySpec;
import it.water.gradle.plugins.workspace.pin.WaterPinsExtension;
import it.water.gradle.plugins.workspace.util.WaterGradleWorkspaceUtil;
import org.gradle.BuildListener;
import org.gradle.BuildResult;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.initialization.Settings;
import org.gradle.api.invocation.Gradle;
import org.gradle.api.plugins.ExtensionAware;
import org.gradle.api.plugins.ExtensionContainer;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.jvm.tasks.Jar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * @Author Aristide Cittadino
 * Water Gradle Workspace Plugin
 * This plugin allows to automatically discover water projects and configure them.
 */
public class WaterWorkspaceGradlePlugin implements Plugin<Settings>, BuildListener {
    private static Logger log = LoggerFactory.getLogger(WaterWorkspaceGradlePlugin.class);
    public static final String INCLUDE_IN_JAR_CONF = "implementationInclude";
    public static final String INCLUDE_IN_JAR_TRANSITIVE_CONF = "implementationIncludeTransitive";
    public static final String WATER_WS_EXTENSION = "WaterWorkspace";
    public static final String EXCLUDE_PROJECT_PATHS;
    public static final String BND_TOOL_DEP_NAME = "biz.aQute.bnd:biz.aQute.bnd.gradle";
    public static final String FEATURES_SRC_FILE_PATH = "src/main/resources/features-src.xml";
    public static final String FEATURES_FILE_PATH = "src/main/resources/features.xml";
    private static Properties versionsProperties;

    static {
        EXCLUDE_PROJECT_PATHS = ".*/exam/.*|.*/build/.*|.*/target/.*|.*/bin/.*|.*/src/.*";
        versionsProperties = new Properties();
        try {
            versionsProperties.load(WaterWorkspaceGradlePlugin.class.getClassLoader().getResourceAsStream("versions.properties"));
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private WaterWorskpaceExtension extension;
    // Redundant in some methods but it is needed in others
    private Settings settings;

    @Override
    public void apply(Settings settings) {
        this.settings = settings;
        this.extension = addWorkspaceExtension(settings);
        settings.getGradle().addBuildListener(this);
    }

    @Override
    public void settingsEvaluated(Settings settings) {
        log.info("Settings Evaluated searching for projects...");
        String projectPath = settings.getRootProject().getProjectDir().getPath();
        addProjectsToWorkspace(projectPath);
    }

    @Override
    public void projectsLoaded(Gradle gradle) {
        Project project = gradle.getRootProject();
        defineDefaultProperties(project);
        log.info("Adding Repo: maven central...");
        project.getBuildscript().getRepositories().add(project.getBuildscript().getRepositories().mavenCentral());
        log.info("Adding Repo: maven local...");
        project.getBuildscript().getRepositories().add(project.getBuildscript().getRepositories().mavenLocal());
        log.info("Adding Repo: gradle m2...");
        project.getBuildscript().getRepositories().add(project.getBuildscript().getRepositories().maven(mavenArtifactRepository -> mavenArtifactRepository.setUrl("https://plugins.gradle.org/m2/")));
        this.addBndGradleDep(project.getBuildscript().getDependencies());
        //adding includeInJar and includeInJarTransitive
        project.getAllprojects().forEach(childProject -> {
            // Register waterDescriptor extension on every sub-project so modules can declare descriptor
            childProject.getExtensions().create("waterDescriptor", WaterPinsExtension.class);

            if (addConfiguration(childProject, INCLUDE_IN_JAR_CONF, false)) {
                includeInJarConfiguration(childProject, INCLUDE_IN_JAR_CONF);
                extendConfiguration(childProject, "compileClasspath", INCLUDE_IN_JAR_CONF, "java");
                extendConfiguration(childProject, "runtimeClasspath", INCLUDE_IN_JAR_CONF, "java");
                extendConfiguration(childProject, "testCompileClasspath", INCLUDE_IN_JAR_CONF, "java");
                extendConfiguration(childProject, "testRuntimeClasspath", INCLUDE_IN_JAR_CONF, "java");
            }
            if (addConfiguration(childProject, INCLUDE_IN_JAR_TRANSITIVE_CONF, true))
                includeInJarConfiguration(childProject, INCLUDE_IN_JAR_TRANSITIVE_CONF);
            extendConfiguration(childProject, "compileClasspath", INCLUDE_IN_JAR_TRANSITIVE_CONF, "java");
            extendConfiguration(childProject, "runtimeClasspath", INCLUDE_IN_JAR_TRANSITIVE_CONF, "java");
            extendConfiguration(childProject, "testCompileClasspath", INCLUDE_IN_JAR_TRANSITIVE_CONF, "java");
            extendConfiguration(childProject, "testRuntimeClasspath", INCLUDE_IN_JAR_TRANSITIVE_CONF, "java");
        });
    }

    @Override
    public void projectsEvaluated(Gradle gradle) {
        Project project = gradle.getRootProject();
        addDepListTask(project);
        addKarafFeaturesTask(project, project);
        project.getAllprojects().forEach(this::addWaterDescriptorTask);
    }

    @Override
    public void buildFinished(BuildResult buildResult) {
        // Do nothing
    }

    private void addBndGradleDep(DependencyHandler dependencies) {
        log.info("Adding dependency: bndTools...");
        String bndToolsVersion = versionsProperties.getProperty("bndToolVersion");
        dependencies.add("classpath", BND_TOOL_DEP_NAME + ":" + bndToolsVersion);
    }

    private boolean addConfiguration(Project project, String configurationName, boolean transitive) {
        log.info("Adding custom dependency configuration: {}...", configurationName);
        if (project.getConfigurations().stream().noneMatch(configuration -> configuration.getName().equals(configurationName))) {
            project.getConfigurations().create(configurationName, configuration -> {
                configuration.setCanBeResolved(true);
                configuration.setCanBeConsumed(false);
                configuration.setTransitive(transitive);
            });
            log.info("Configuration {} added with properties: canBeResolved(true), canBeConsumed(false), transitive({}).", configurationName, transitive);
            return true;
        }
        log.info("Skipping adding {} because already defined inside the build gradle");
        return false;
    }

    private void includeInJarConfiguration(Project project, String configurationName) {
        project.getTasks().withType(org.gradle.api.tasks.bundling.Jar.class).configureEach(jar -> {
            log.info("Customizing jar task to include {} dependencies...", configurationName);
            Configuration config = project.getConfigurations().getByName(configurationName);
            jar.setDuplicatesStrategy(DuplicatesStrategy.INCLUDE);
            jar.setZip64(true);
            setupAnnotationMerges(jar, project, INCLUDE_IN_JAR_CONF, INCLUDE_IN_JAR_TRANSITIVE_CONF);
            jar.from((Callable<Object>) () -> config.resolve().stream().map(file -> file.isDirectory() ? project.fileTree(file) : project.zipTree(file)).toArray());
            log.info("Jar {} customization completed.", configurationName);
        });
    }

    public void setupAnnotationMerges(Jar jar, Project project, String... configNames) {
        log.info("Starting annotation check for project {}", project.getName());
        jar.doFirst(task -> {
            for (String configName : configNames) {
                for (File file : project.getConfigurations().getByName(configName).getFiles()) {
                    FileTree tree = project.zipTree(file).matching(pattern -> pattern.include("META-INF/annotations/*"));
                    tree.visit(new FileVisitor() {
                        @Override
                        public void visitDir(FileVisitDetails dirDetails) {
                            // do nothing
                        }

                        @Override
                        public void visitFile(FileVisitDetails fileDetails) {
                            String annotationsPath = project.getBuildDir() + "/classes/java/main/META-INF/annotations";
                            File destFile = new File(annotationsPath, fileDetails.getName());
                            log.info("Adding Annotation {}", destFile.getName());
                            try {
                                //load current content
                                byte[] content = Files.readAllBytes(fileDetails.getFile().toPath());
                                log.debug("Appending Water Framework Annotation {}", new String(content));
                                //append to destination
                                Files.write(destFile.toPath(), content,
                                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                            } catch (IOException e) {
                                throw new RuntimeException("Error while adding annotation: " + fileDetails.getFile(), e);
                            }
                        }
                    });
                }
            }
        });
    }

    private void extendConfiguration(Project project, String configuration, String extendFrom, String requiredPlugin) {
        if (requiredPlugin != null && !project.getPluginManager().hasPlugin(requiredPlugin)) {
            log.info("Extending with plugin {} for project {}", requiredPlugin, project.getName());
            project.getPluginManager().apply(requiredPlugin);
        }
        boolean existConf = project.getConfigurations().stream().anyMatch(conf -> conf.getName().equals(configuration));
        boolean existExtendConf = project.getConfigurations().stream().anyMatch(conf -> conf.getName().equals(extendFrom));
        if (existConf && existExtendConf) {
            log.info("Extending {} configuration with {} for project {}", configuration, extendFrom, project.getName());
            project.getConfigurations().getByName(configuration).extendsFrom(project.getConfigurations().getByName(extendFrom));
        } else {
            log.info("No configuration found {} with extension {} for project {}", configuration, extendFrom, project.getName());
        }
    }

    private WaterWorskpaceExtension addWorkspaceExtension(Settings settings) {
        ExtensionAware extensionAware = (ExtensionAware) settings.getGradle();
        ExtensionContainer extensionContainer = extensionAware.getExtensions();
        return extensionContainer.create(WATER_WS_EXTENSION, WaterWorskpaceExtension.class, settings);
    }

    private void addProjectsToWorkspace(String modulesPath) {
        try {
            List<String> projectsFound = new ArrayList<>();
            Files.walkFileTree(Paths.get(modulesPath), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) throws IOException {
                    File file = path.toFile();
                    if (path.toString().matches(EXCLUDE_PROJECT_PATHS)) {
                        return FileVisitResult.SKIP_SIBLINGS;
                    } else if (file.isFile() && file.getName().endsWith("build.gradle") && !file.getParent().equalsIgnoreCase(modulesPath)) {
                        log.info("Found build gradle project: " + file.getPath());
                        projectsFound.add(file.getPath());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            projectsFound.forEach(filePath -> {
                filePath = filePath.replace(File.separator + "build.gradle", "");
                String modulesRelativePath = transformInGradlePath(filePath.substring(filePath.indexOf(modulesPath) + modulesPath.length()));
                String module = modulesRelativePath;
                if (module.startsWith(File.separator)) module = module.substring(1);
                log.info("Before Adding project: " + module);
                includeProjectIntoWorkspace(settings, module);
            });
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    private String transformInGradlePath(String path) {
        return path.replace("\\\\", ":").replace("/", ":");
    }

    private void includeProjectIntoWorkspace(Settings settings, String projectModulePath) {
        log.info("Including {}", projectModulePath);
        settings.include(projectModulePath);
    }

    private void addDepListTask(Project rootProject) {
        Map<String, HashMap<String, Object>> depJson = new HashMap<>();
        log.info("Adding depList task...");
        rootProject.task("depList", task -> task.doFirst(innerTask -> {
            Set<Project> subProjects = rootProject.getSubprojects();
            subProjects.stream().forEach(p -> {
                String projectName = p.getGroup() + ":" + p.getName() + ":" + p.getVersion();
                if (depJson.get(projectName) == null && p.getParent() != null) {
                    @SuppressWarnings("null")
                    String parentProjectName = p.getParent().getGroup() + ":" + p.getParent().getName() + ":" + p.getParent().getVersion();
                    depJson.put(projectName, new HashMap<>());
                    depJson.get(projectName).put("parent", parentProjectName);
                    depJson.get(projectName).put("dependencies", new ArrayList<String>());
                    depJson.get(projectName).put("path", p.getBuildFile().getPath().replace("/build.gradle", ""));
                }
                p.getConfigurations().stream().forEach(conf -> conf.getDependencies().stream().forEach(it -> {
                    @SuppressWarnings("unchecked")
                    List<String> depList = (List<String>) depJson.get(projectName).get("dependencies");
                    depList.add(it.getGroup() + ":" + it.getName() + ":" + it.getVersion());
                }));
            });
            String jsonStr = groovy.json.JsonOutput.prettyPrint(JsonOutput.toJson(depJson));
            rootProject.getLogger().lifecycle("-- DEP LIST OUTPUT --");
            rootProject.getLogger().lifecycle(jsonStr);
            rootProject.getLogger().lifecycle("-- END DEP LIST OUTPUT --");
        }));
    }

    private void addKarafFeaturesTask(Project rootProject, Project project) {
        File featuresSrcFile = new File(project.getProjectDir() + File.separator + FEATURES_SRC_FILE_PATH);
        if (featuresSrcFile.exists() && !project.hasProperty("generateFeatures")) {
            addTaskToFeaturesProject(project);
        }
        if (project.getSubprojects() != null && !project.getSubprojects().isEmpty()) {
            project.getSubprojects().forEach(subproject -> addKarafFeaturesTask(rootProject, subproject));
        }
    }

    private void addTaskToFeaturesProject(Project project) {
        project.task("generateFeatures", task ->
                task.doLast(innerTask -> {
                    try {
                        String featuresSrcPath = project.getProjectDir().getAbsolutePath() + File.separator + FEATURES_SRC_FILE_PATH;
                        String featuresOutputPath = project.getProjectDir().getAbsolutePath() + File.separator + FEATURES_FILE_PATH;
                        String inputFileContent = new String(Files.readAllBytes(Paths.get(featuresSrcPath)));
                        String outputFileContent = inputFileContent;
                        Iterator<String> it = project.getProperties().keySet().iterator();
                        while (it.hasNext()) {
                            String key = it.next();
                            Object value = project.getProperties().get(key);
                            String token = "\\$\\{project." + key + "\\}";
                            if (value != null) {
                                outputFileContent = outputFileContent.replaceAll(token, value.toString());
                            }
                        }
                        Files.write(Paths.get(featuresOutputPath), outputFileContent.getBytes(StandardCharsets.UTF_8));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }));
    }

    private void addWaterDescriptorTask(Project project) {
        WaterPinsExtension ext = project.getExtensions().findByType(WaterPinsExtension.class);
        if (ext == null || ext.getModuleId() == null || ext.getModuleId().isBlank()) {
            return; // module has no waterDescriptor declaration — skip
        }

        // Collect inherited PINs, properties and registry entries first, then merge own declarations on top
        List<OutputPinSpec> outputPins = new ArrayList<>();
        List<InputPinSpec> inputPins = new ArrayList<>();
        List<PinPropertySpec> moduleProperties = new ArrayList<>();
        List<RegistryEntrySpec> registryEntries = new ArrayList<>();
        for (Project parentProject : ext.getInheritsFromProjects()) {
            WaterPinsExtension parentExt = parentProject.getExtensions().findByType(WaterPinsExtension.class);
            if (parentExt != null) {
                outputPins.addAll(parentExt.getOutput().getPins());
                inputPins.addAll(parentExt.getInput().getPins());
                moduleProperties.addAll(parentExt.getProperties().getProperties());
                registryEntries.addAll(parentExt.getRegistry().getEntries());
                log.info("Project {} inherits {} output PIN(s), {} input PIN(s), {} propert(y/ies) and {} registry entr(y/ies) from {}",
                        project.getName(),
                        parentExt.getOutput().getPins().size(),
                        parentExt.getInput().getPins().size(),
                        parentExt.getProperties().getProperties().size(),
                        parentExt.getRegistry().getEntries().size(),
                        parentProject.getName());
            }
        }
        outputPins.addAll(ext.getOutput().getPins());
        inputPins.addAll(ext.getInput().getPins());
        moduleProperties.addAll(ext.getProperties().getProperties());
        registryEntries.addAll(ext.getRegistry().getEntries());

        String artifactId = project.getGroup() + ":" + project.getName() + ":" + project.getVersion();
        String fileName   = "water-descriptor.json";

        String descriptorJson = GenerateWaterDescriptorTask.buildDescriptorJson(
                artifactId,
                ext.getModuleId(),
                ext.getDisplayName() != null ? ext.getDisplayName() : project.getName(),
                ext.getDescription() != null ? ext.getDescription() : "",
                ext.getComponentsPackage(),
                ext.getRepositoryPackage(),
                ext.getEntitiesPackage(),
                outputPins,
                inputPins,
                moduleProperties,
                registryEntries);

        TaskProvider<GenerateWaterDescriptorTask> genTask = project.getTasks()
                .register("generateWaterDescriptor", GenerateWaterDescriptorTask.class, t -> {
                    t.setGroup("water");
                    t.setDescription("Generates the Water PIN descriptor for " + project.getName());
                    t.getDescriptorJson().set(descriptorJson);
                    t.getOutputFile().set(
                            project.getLayout().getBuildDirectory().file("water/" + fileName));
                });

        // Wire as Maven publication artifact (extension water.json, no classifier)
        project.getPlugins().withType(MavenPublishPlugin.class, ignored ->
                project.getExtensions().getByType(PublishingExtension.class)
                        .getPublications()
                        .withType(MavenPublication.class, pub ->
                                pub.artifact(
                                        genTask.flatMap(GenerateWaterDescriptorTask::getOutputFile),
                                        a -> a.setExtension("water.json"))
                        )
        );

        log.info("Registered generateWaterDescriptor task for project: {}", project.getName());
    }

    private void defineDefaultProperties(Project project) {
        log.info("Updating global properties...");
        WaterGradleWorkspaceUtil.getAllDefinedVersions().stream().forEach(propertyName -> {
            log.info("Setting global Property : {}  =  {}", propertyName, WaterGradleWorkspaceUtil.getProperty(propertyName));
            project.getExtensions().getExtraProperties().set(propertyName, WaterGradleWorkspaceUtil.getProperty(propertyName));
        });
        log.info("Overriding props with workspace ones...");
        WaterGradleWorkspaceUtil.getWorkspaceDefinedVersions(project).stream().forEach(propertyName -> {
            log.info("Setting Custom Workspace Property : {} = {}", propertyName, WaterGradleWorkspaceUtil.getWorkspaceDefinedProperty(project, propertyName));
            project.getExtensions().getExtraProperties().set(propertyName, WaterGradleWorkspaceUtil.getWorkspaceDefinedProperty(project, propertyName));
        });
    }
}
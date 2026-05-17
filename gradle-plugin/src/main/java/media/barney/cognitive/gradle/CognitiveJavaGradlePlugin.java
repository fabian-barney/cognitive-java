package media.barney.cognitive.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.Collection;
import java.util.List;

public class CognitiveJavaGradlePlugin implements Plugin<Project> {

    private static final int DEFAULT_THRESHOLD = 15;

    @Override
    public void apply(Project project) {
        CognitiveJavaExtension extension = project.getExtensions().create("cognitiveJava", CognitiveJavaExtension.class);
        extension.getThreshold().convention(DEFAULT_THRESHOLD);
        extension.getAgent().convention(false);
        extension.getFormat().convention(extension.getAgent().map(agent -> agent ? "toon" : "none"));
        extension.getFailuresOnly().convention(extension.getAgent());
        extension.getOmitRedundancy().convention(extension.getAgent());
        extension.getJunit().convention(true);
        extension.getJunitReport().convention(project.getLayout().getBuildDirectory()
                .file("reports/cognitive-java/TEST-cognitive-java.xml"));

        TaskProvider<CognitiveJavaCheckTask> checkTask = project.getTasks().register(
                "cognitive-java-check",
                CognitiveJavaCheckTask.class,
                task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Runs the cognitive-java Cognitive Complexity gate.");
                    task.getAnalysisRoot().set(project.getLayout().getProjectDirectory());
                    task.getThreshold().convention(extension.getThreshold());
                    task.getAgent().convention(extension.getAgent());
                    task.getFormat().convention(taskFormatDefault(project, task, extension));
                    task.getFailuresOnly().convention(
                            taskPrimaryFlagDefault(project, task, extension, extension.getFailuresOnly()));
                    task.getOmitRedundancy().convention(
                            taskPrimaryFlagDefault(project, task, extension, extension.getOmitRedundancy()));
                    task.getOutput().convention(extension.getOutput());
                    task.getJunit().convention(extension.getJunit());
                    task.getJunitReport().convention(extension.getJunitReport());
                    task.getAnalysisMetadata().from(
                            project.getLayout().getProjectDirectory().file("settings.gradle"),
                            project.getLayout().getProjectDirectory().file("settings.gradle.kts"),
                            project.getLayout().getProjectDirectory().file("build.gradle"),
                            project.getLayout().getProjectDirectory().file("build.gradle.kts"),
                            project.getLayout().getProjectDirectory().file("gradlew"),
                            project.getLayout().getProjectDirectory().file("gradlew.bat")
                    );
                }
        );

        for (Project candidate : projectsToConfigure(project)) {
            candidate.getPluginManager().withPlugin("java", ignored -> configureJavaProject(candidate, checkTask));
        }
    }

    private Collection<Project> projectsToConfigure(Project project) {
        if (project.equals(project.getRootProject())) {
            return project.getAllprojects();
        }
        return List.of(project);
    }

    private void configureJavaProject(Project candidate, TaskProvider<CognitiveJavaCheckTask> checkTask) {
        checkTask.configure(task -> {
            task.getAnalysisSources().from(candidate.fileTree(candidate.getProjectDir(), tree ->
                    tree.include("src/main/java/**/*.java")
            ));
            task.getAnalysisMetadata().from(
                    candidate.getLayout().getProjectDirectory().file("build.gradle"),
                    candidate.getLayout().getProjectDirectory().file("build.gradle.kts")
            );
        });
    }

    private static Provider<String> taskFormatDefault(Project project,
                                                      CognitiveJavaCheckTask task,
                                                      CognitiveJavaExtension extension) {
        return project.getProviders().provider(() -> {
            boolean extensionAgent = extension.getAgent().getOrElse(false);
            boolean taskAgent = task.getAgent().getOrElse(extensionAgent);
            String extensionFormat = extension.getFormat().getOrElse(extensionAgent ? "toon" : "none");
            if (taskAgent != extensionAgent && isDefaultAgentFormat(extensionAgent, extensionFormat)) {
                return taskAgent ? "toon" : "none";
            }
            return extensionFormat;
        });
    }

    private static Provider<Boolean> taskPrimaryFlagDefault(Project project,
                                                            CognitiveJavaCheckTask task,
                                                            CognitiveJavaExtension extension,
                                                            Property<Boolean> extensionControl) {
        return project.getProviders().provider(() -> {
            boolean extensionAgent = extension.getAgent().getOrElse(false);
            boolean extensionValue = extensionControl.getOrElse(extensionAgent);
            boolean taskAgent = task.getAgent().getOrElse(extensionAgent);
            if (taskAgent != extensionAgent && extensionValue == extensionAgent) {
                return taskAgent;
            }
            return extensionValue;
        });
    }

    private static boolean isDefaultAgentFormat(boolean agent, String format) {
        return agent ? "toon".equals(format) : "none".equals(format);
    }
}

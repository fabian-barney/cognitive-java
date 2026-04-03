package media.barney.cognitivejava.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.util.Collection;
import java.util.List;

public class CognitiveJavaGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        TaskProvider<CognitiveJavaCheckTask> checkTask = project.getTasks().register(
                "cognitive-java-check",
                CognitiveJavaCheckTask.class,
                task -> {
                    task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
                    task.setDescription("Runs the cognitive-java Cognitive Complexity gate.");
                    task.getAnalysisRoot().set(project.getLayout().getProjectDirectory());
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
}

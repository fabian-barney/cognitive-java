package media.barney.cognitive.gradle;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class CognitiveJavaExtension {

    public abstract Property<Integer> getThreshold();

    public abstract Property<String> getFormat();

    public abstract Property<Boolean> getAgent();

    public abstract Property<Boolean> getFailuresOnly();

    public abstract Property<Boolean> getOmitRedundancy();

    public abstract RegularFileProperty getOutput();

    public abstract Property<Boolean> getJunit();

    public abstract RegularFileProperty getJunitReport();

    public abstract ListProperty<String> getExcludes();

    public abstract ListProperty<String> getExcludeClasses();

    public abstract ListProperty<String> getExcludeAnnotations();

    public abstract Property<Boolean> getUseDefaultExclusions();
}

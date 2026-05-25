import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.DefaultTask
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.tasks.Jar
import org.gradle.plugin.compatibility.compatibility
import org.gradle.plugins.signing.Sign
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.work.DisableCachingByDefault
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    `java-gradle-plugin`
    id("com.gradle.plugin-publish") version "2.1.1"
    id("net.ltgt.errorprone") version "5.1.0" apply false
    jacoco
    signing
}

group = "media.barney"
version = "0.5.1"

repositories {
    mavenCentral()
}

@DisableCachingByDefault(because = "Validates that the Maven-built core jar exists.")
abstract class VerifyCoreJarTask : DefaultTask() {
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val coreJar: RegularFileProperty

    @TaskAction
    fun verify() {
        val jar = coreJar.orNull?.asFile
            ?: throw GradleException("Missing configured core jar path. Run `mvn -pl core -am package` from the repository root first.")
        if (!jar.exists()) {
            throw GradleException(
                "Missing $jar. Run `mvn -pl core -am package` from the repository root first."
            )
        }
    }
}

val projectVersion = version.toString()
val coreJar = layout.projectDirectory.file("../core/target/cognitive-java-core-${projectVersion}.jar")
val junitVersion = parentPomProperty("junit.version")
val jspecifyVersion = parentPomProperty("jspecify.version")
val jtoonVersion = parentPomProperty("jtoon.version")
val jacksonVersion = parentPomProperty("jackson.version")
val errorproneVersion = parentPomProperty("errorprone.version")
val nullawayVersion = parentPomProperty("nullaway.version")
val nullawayAnnotatedPackages = parentPomProperty("nullaway.annotated.packages")
val qualityNullaway = providers.gradleProperty("qualityNullaway").map(String::toBoolean).getOrElse(false)
val gpgPrivateKey = providers.environmentVariable("MAVEN_GPG_PRIVATE_KEY")
val gpgPassphrase = providers.environmentVariable("MAVEN_GPG_PASSPHRASE")
val mavenCentralTokenUsername = providers.gradleProperty("mavenCentralTokenUsername")
    .orElse(providers.environmentVariable("MAVEN_CENTRAL_TOKEN_USERNAME"))
val mavenCentralTokenPassword = providers.gradleProperty("mavenCentralTokenPassword")
    .orElse(providers.environmentVariable("MAVEN_CENTRAL_TOKEN_PASSWORD"))

jacoco {
    toolVersion = "0.8.13"
}

if (qualityNullaway) {
    apply(plugin = "net.ltgt.errorprone")
}

val verifyCoreJar = tasks.register<VerifyCoreJarTask>("verifyCoreJar") {
    coreJar.set(layout.projectDirectory.file("../core/target/cognitive-java-core-${projectVersion}.jar"))
}

tasks.withType<JavaCompile>().configureEach {
    dependsOn(verifyCoreJar)
    options.release.set(17)
}

dependencies {
    implementation(files(coreJar.asFile))
    implementation("dev.toonformat:jtoon:$jtoonVersion")
    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonVersion"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    compileOnly("org.jspecify:jspecify:$jspecifyVersion")
    if (qualityNullaway) {
        add("errorprone", "com.google.errorprone:error_prone_core:$errorproneVersion")
        add("errorprone", "com.uber.nullaway:nullaway:$nullawayVersion")
    }
    testImplementation(platform("org.junit:junit-bom:$junitVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation(gradleTestKit())
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

if (qualityNullaway) {
    tasks.withType<JavaCompile>().configureEach {
        options.isFork = true
        options.forkOptions.jvmArgs = (options.forkOptions.jvmArgs ?: mutableListOf()) + listOf(
            "--add-exports=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED",
            "--add-exports=jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED",
            "--add-opens=jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED"
        )
        options.compilerArgs.addAll(listOf(
            "-XDcompilePolicy=simple",
            "--should-stop=ifError=FLOW",
            "-XDaddTypeAnnotationsToSymbol=true"
        ))
        options.errorprone {
            isEnabled = true
            disableAllChecks.set(true)
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", nullawayAnnotatedPackages)
        }
    }
}

fun parentPomProperty(name: String): String {
    val factory = DocumentBuilderFactory.newInstance()
    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
    factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
    factory.setXIncludeAware(false)
    factory.setExpandEntityReferences(false)
    val document = factory.newDocumentBuilder().parse(layout.projectDirectory.file("../pom.xml").asFile)
    return document.getElementsByTagName(name).item(0)?.textContent
        ?: throw GradleException("Missing parent POM property: $name")
}

tasks.withType<Test>().configureEach {
    dependsOn(verifyCoreJar)
    useJUnitPlatform()
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn(tasks.named("test"))
    reports {
        xml.required.set(true)
        csv.required.set(false)
        html.required.set(false)
    }
}

tasks.named("pluginUnderTestMetadata") {
    dependsOn(verifyCoreJar)
}

tasks.named<Jar>("jar") {
    dependsOn(verifyCoreJar)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(zipTree(coreJar))
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("cognitive-java Gradle Plugin")
            description.set("Gradle plugin exposing the cognitive-java-check verification task.")
            url.set("https://github.com/fabian-barney/cognitive-java")
            licenses {
                license {
                    name.set("Apache License 2.0")
                    url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("fabian-barney")
                    name.set("Fabian Barney")
                    url.set("https://github.com/fabian-barney")
                }
            }
            scm {
                connection.set("scm:git:https://github.com/fabian-barney/cognitive-java.git")
                developerConnection.set("scm:git:ssh://git@github.com/fabian-barney/cognitive-java.git")
                url.set("https://github.com/fabian-barney/cognitive-java")
            }
        }
    }
    if (mavenCentralTokenUsername.isPresent && mavenCentralTokenPassword.isPresent) {
        repositories {
            maven {
                name = "centralPortalOssrhStaging"
                url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
                credentials {
                    username = mavenCentralTokenUsername.get()
                    password = mavenCentralTokenPassword.get()
                }
            }
        }
    }
}

signing {
    val key = gpgPrivateKey.orNull
    if (!key.isNullOrBlank()) {
        useInMemoryPgpKeys(key, gpgPassphrase.orNull)
        sign(publishing.publications)
    }
}

tasks.withType<Sign>().configureEach {
    onlyIf { !gpgPrivateKey.orNull.isNullOrBlank() }
}

gradlePlugin {
    website.set("https://github.com/fabian-barney/cognitive-java")
    vcsUrl.set("https://github.com/fabian-barney/cognitive-java")
    plugins {
        create("cognitive-java") {
            id = "media.barney.cognitive-java"
            implementationClass = "media.barney.cognitive.gradle.CognitiveJavaGradlePlugin"
            displayName = "cognitive-java Gradle Plugin"
            description = "Registers the cognitive-java-check verification task for Gradle Java projects."
            tags.set(listOf("java", "quality", "complexity", "verification", "static-analysis"))
            compatibility {
                features {
                    configurationCache = true
                }
            }
        }
    }
}

# Dependency selection for v1

The release refresh retains a full-JDK 17 runtime floor and Java 17 bytecode.
JToon and its Jackson 3 dependencies require Java 17; the directly used Jackson 2
report APIs fit that floor. Maven APIs remain in the supported Maven 3.9 line.

## Selected versions

| Component | Version | Decision |
| --- | --- | --- |
| JToon | 2.0.4 | Current stable; freeze the resulting v1 TOON encoding |
| Jackson BOM | 2.22.3 | Current stable 2.x; preserve the existing JSON/XML renderer APIs |
| crap-java gate | 1.0.1 | Current stable release with the existing strict threshold |
| NullAway | 0.14.2 | Current stable; verify with Error Prone on JDK 25 |
| SpotBugs | 4.10.4 | Current stable engine |
| SpotBugs Maven plugin | 4.10.4.1 | Current stable integration |
| SpotBugs Gradle plugin | 6.5.11 | Current stable integration |
| Error Prone Gradle plugin | 5.1.1 | Current stable integration |
| Maven API | 3.9.16 | Latest stable supported Maven line |
| Maven Enforcer | 3.6.3 | Enforce Java 17 and Maven 3.9.0 minimums |
| Gradle wrapper | 9.8.0 | Current stable, with trusted distribution checksum |

JToon's separately named `tools.jackson` 3.x APIs can coexist with the
`com.fasterxml.jackson` 2.x report renderer. Migrating the report renderer to
Jackson 3 is deferred because it changes APIs without improving the v1 contract.

JUnit 6.1.3, JSpecify 1.0.1, Error Prone 2.50.0, JaCoCo 0.8.15, Maven Surefire
3.6.0, Javadoc 3.12.0, GPG 3.2.8, Shade 3.6.2, Invoker 3.10.1, and Central
Publishing 0.11.0 remain current compatible stable choices. Compiler 3.16.0,
Source 3.4.0, JAR 3.5.1, and Plugin Tools 3.15.2 select stable Maven 3-compatible
lines; upstream Maven 4 beta and release-candidate tooling is excluded.

Runtime/build dependency metadata and upstream license files must be checked
when changing these choices. Bundled third-party license and notice contents are
validated as part of release packaging.

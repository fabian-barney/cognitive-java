# Dependency selection for v1

The release refresh retains a full-JDK 17 runtime floor and Java 17 bytecode.
JToon and its Jackson 3 dependencies require Java 17; the directly used Jackson 2
report APIs fit that floor. Maven APIs remain in the supported Maven 3.9 line.

## Selected versions

| Component | Version | Decision |
| --- | --- | --- |
| JToon | 2.0.5 | Latest Java 17-compatible release; preserve the frozen v1 TOON encoding |
| Jackson BOM | 2.22.3 | Current stable 2.x; preserve the existing JSON/XML renderer APIs |
| crap-java gate | 1.0.1 | Current stable release with the existing strict threshold |
| NullAway | 0.14.2 | Current stable; verify with Error Prone on JDK 25 |
| SpotBugs | 4.10.4 | Validated analyzer; newer engines require quality-output verification |
| SpotBugs Maven plugin | 4.10.4.1 | Retain the validated analyzer integration pending that verification |
| SpotBugs Gradle plugin | 6.5.11 | Current stable integration |
| Error Prone Gradle plugin | 5.1.1 | Current stable integration |
| Maven API | 3.9.16 | Latest stable supported Maven line |
| Maven Enforcer | 3.6.3 | Enforce Java 17 and Maven 3.9.0 minimums |
| Gradle wrapper | 9.8.1 | Current stable, with trusted distribution checksum |
| CycloneDX Maven plugin | 2.9.3 | Generate component SBOMs using schema 1.6 |
| CycloneDX Gradle plugin | 3.4.1 | Inventory the resolved Gradle runtime dependencies |
| Maven AntRun plugin | 3.2.0 | Package the exact project license in release archives |

JToon's separately named `tools.jackson` 3.x APIs can coexist with the
`com.fasterxml.jackson` 2.x report renderer. Migrating the report renderer to
Jackson 3 is deferred because it changes APIs without improving the v1 contract.

JToon 2.0.6 through 2.0.8 and 3.0.0 ship Java 21 bytecode and are incompatible with the
Java 17 consumer floor. JToon 2.0.5 retains Java 17 bytecode and passes the
unchanged report-format contract fixtures; newer absolute versions are deferred.
The 2026-10-17 scan verified class-file major 65 in the published 2.0.8 and
3.0.0 JARs; these releases do not justify raising the public Java 17 floor.

JUnit 6.1.3, JSpecify 1.0.1, Error Prone 2.50.0, JaCoCo 0.8.15, Maven Surefire
3.6.0, Javadoc 3.12.0, GPG 3.2.8, Shade 3.6.2, and Invoker 3.10.1 remain
compatible stable choices. Central Publishing stays on the validated 0.11.0
release path; 0.11.1 and 0.12.0 require separate nonpublishing signing and
publication preflight verification. Compiler 3.16.0,
Source 3.4.0, JAR 3.5.1, and Plugin Tools 3.15.2 select stable Maven 3-compatible
lines; upstream Maven 4 beta and release-candidate tooling is excluded.

Runtime/build dependency metadata and upstream license files must be checked
when changing these choices. Bundled third-party license and notice contents are
validated as part of release packaging.

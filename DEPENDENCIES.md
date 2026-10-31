# Dependency selection for cognitive-java 2.0.0

This release baseline implements issue #118, assessed on 2026-10-17.
The previous Java 17 and v1 reporting constraints are superseded on `main`;
the published 1.0.0 contract remains documented in MIGRATING.md.

## Runtime and build compatibility

Support full JDK 21 and 25 with Java 21 bytecode. JToon 3.0.0 requires Java 21,
Jackson 3.2.4 requires Java 17, and JSpecify 1.0.1 requires Java 8. Their runtime
constraint intersection is Java 21 or newer. Error Prone requires a build JDK >=21.
Java 17 retirement is intentional project policy; it is not universally EOL.

Use Maven 3.9.16 or 3.10.0; primary CI/release builds pin 3.10.0. Maven provided
APIs remain at 3.9.16 for consumers on both maintained lines. Gradle 8.14.6 is
supported on JDK 21 and Gradle 9.8.2 on JDK 21/25. The wrapper stays on 9.8.2.
Gradle 8.14.6 fixes three published high-rated vulnerabilities. Maven 4 and its
plugin prereleases are excluded; Java 27 remains unverified.

## Selected dependencies

| Component | Version | Decision |
| --- | --- | --- |
| JToon | 3.0.0 | Current Java 21 line; review v2 TOON semantics |
| Jackson BOM / implementations | 3.2.4 | One implementation family shared with JToon |
| Jackson annotations | 2.22 | Shared upstream namespace intentionally remains com.fasterxml.jackson |
| Woodstox / stax2-api | 7.3.0 / 4.3.2 | Explicitly align Maven and Gradle XML runtimes |
| crap-java | 2.0.0 | Published Java 21 gate; retain strict thresholds |
| SpotBugs engine / Maven plugin | 4.10.5 / 4.10.5.0 | Fix valid new findings |
| SpotBugs Gradle plugin | 6.5.13 | Current integration |
| Gradle Plugin Publish | 2.2.1 | Verify signed publication locally without upload |
| CycloneDX Gradle plugin | 3.5.1 | Verify runtime inventories and schema 1.6 |
| Maven Plugin Tools / annotations | 3.16.0 | Stable Maven 3 tooling |
| Central Publishing | 0.12.0 | Verify Maven credential origins and signatures |
| Maven Dependency | 3.11.0 | Prepare complete offline CI dependency cache |
| Clean / Resources | 3.5.0 / 3.5.0 | Pin lifecycle defaults independently of installed Maven |
| Install / Deploy | 3.2.0 / 3.2.0 | Pin lifecycle defaults |
| setup-java | 6.1.0 | Immutable SHA pin; explicit credential-origin and environment-name inputs |

Jackson 2.22 is still maintained. Consolidation removes duplicate implementations
rather than responding to an EOL. Jackson 3.2.4 aligns with JToon's selected line
and the sibling crap-java migration; shared annotations are not Jackson 2 runtime
implementation classes.

Retain current JUnit 6.1.3, JSpecify 1.0.1, Error Prone 2.51.0, NullAway 0.14.3,
JaCoCo 0.8.16, Enforcer 3.7.0, Compiler 3.16.0, Surefire 3.6.1, JAR 3.5.1,
Shade 3.6.2, Antrun 3.2.0, Source 3.4.0, Javadoc 3.12.0, GPG 3.2.8,
Invoker 3.10.1, CycloneDX Maven 2.9.3, and Error Prone Gradle 5.1.1.
Fixture versions are filtered from the parent; Gradle reads the parent's JaCoCo version.
Current checkout/artifact/Gradle/attest Action pins are retained.

Exact selected licenses and dependency advisories are recorded in BUILD_TOOLING_AUDIT.md.
Runtime inventories and archive notices are verified from fresh build output.

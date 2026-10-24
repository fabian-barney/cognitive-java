# Dependency and build-tool audit

## Maintenance refresh — 2026-10-24

Jackson implementations are aligned by BOM 3.2.4 (Java 17 minimum); JToon remains
3.0.0 (Java 21 minimum). The product runtime intersection remains Java >=21.
Error Prone 2.51.0 and NullAway 0.14.3 are build-only analyzers; the former now
uses SafeRE. Gradle 9.8.2 replaces the upper tested patch and wrapper while the
8.14.6 lower boundary and Maven 3.9.16 API floor remain unchanged.
The dated baseline observations below are historical evidence. Fresh validation
and advisory observations are recorded in the linked maintenance issue.

## Issue #118 baseline — 2026-10-17

This audit records fresh cognitive-java dependency resolution, rather than inheriting sibling audit results.
The selected direct versions and runtime constraints are documented in [DEPENDENCIES.md](DEPENDENCIES.md).

## Distribution and license boundary

The core Maven runtime tree contains eight external coordinates: JToon 3.0.0, Jackson core/databind/XML
and Blackbird 3.2.3, shared Jackson annotations 2.22, Woodstox 7.3.0, and stax2-api 4.3.2.
JToon remains MIT, Jackson/Woodstox remain Apache-2.0, and stax2-api remains BSD-2-Clause.
Curated JToon and stax2-api license texts were refreshed from their exact upstream tags.
JSpecify is provided by Maven; the separately resolved Gradle runtime can include its Apache-2.0 annotations.
Check all component SBOMs and shaded archive notices during CI; Maven and Gradle inventories are verified separately.

SpotBugs remains LGPL build tooling; Saxon-HE and plugin-realm libraries remain build-only.
No new publisher or runtime dependency family is introduced. Runtime artifacts do not bundle analyzers.
The Apache, Gradle, FasterXML, Uber, Google, CycloneDX, and project upstreams remain the existing maintainers.

## Exact-version advisory observations

An OSV query for all eight exact Maven runtime coordinates returned no matches on 2026-10-17.
This is a dated database observation, not a guarantee of vulnerability absence.

A fresh Maven resolve-plugins snapshot included 276 unique coordinates, including implicit reporting tools.
Exact-version OSV queries flagged 15 coordinates retained in upstream build-tool realms:

| Retained coordinate | Version | Owning plugins |
| --- | --- | --- |
| com.fasterxml.jackson.core:jackson-core | 2.17.2 | cyclonedx-maven-plugin |
| com.fasterxml.jackson.core:jackson-databind | 2.17.2 | cyclonedx-maven-plugin |
| commons-beanutils:commons-beanutils | 1.9.4 | maven-javadoc-plugin, maven-site-plugin |
| io.airlift:aircompressor | 0.27 | maven-javadoc-plugin, maven-source-plugin |
| org.bouncycastle:bcpg-jdk18on | 1.81 | maven-gpg-plugin |
| org.bouncycastle:bcprov-jdk18on | 1.81 | maven-gpg-plugin |
| org.codehaus.plexus:plexus-utils | 3.0.24 | jacoco-maven-plugin |
| org.codehaus.plexus:plexus-utils | 3.6.0 | maven-gpg-plugin, maven-resources-plugin, maven-shade-plugin |
| org.codehaus.plexus:plexus-utils | 4.0.2 | maven-antrun-plugin, maven-clean-plugin, maven-javadoc-plugin, maven-source-plugin |
| org.cyclonedx:cyclonedx-core-java | 9.0.5 | cyclonedx-maven-plugin |
| org.eclipse.jetty:jetty-http | 9.4.58.v20250814 | maven-site-plugin |
| org.eclipse.jetty:jetty-security | 9.4.58.v20250814 | maven-site-plugin |
| org.eclipse.jetty:jetty-server | 9.4.58.v20250814 | maven-site-plugin |
| org.iq80.snappy:snappy | 0.4 | spotbugs-maven-plugin |
| org.jsoup:jsoup | 1.22.1 | maven-site-plugin |

These are retained upstream-tool risks, not findings resolved or universally declared non-applicable by this change.
The implicit Site lifecycle is outside CI/publication. Product Jackson consolidation does not replace
Jackson libraries isolated in Maven plugin realms. Avoid untrusted build inputs and reassess upstream fixes.
Gradle 8.14.5 is replaced by 8.14.6 for its three published high-rated security fixes.

## Reproduction and validation

Use patched full JDK 21/25 and Maven 3.10.0. Resolve the product graph with:

```sh
mvn -B -ntp -pl core org.apache.maven.plugins:maven-dependency-plugin:3.11.0:tree -Dscope=runtime
```

Resolve the bounded Maven tooling snapshot with:

```sh
mvn -B -ntp -N -P'!quality-gates-all,quality-gate-cognitive,quality-gate-spotbugs' \
  org.apache.maven.plugins:maven-dependency-plugin:3.11.0:resolve-plugins
```

Query [OSV](https://api.osv.dev/v1/querybatch) using Maven package names and exact resolved versions.
All report contracts, platform matrices, archive metadata, licenses/SBOMs, signatures, and reproducibility
must pass on the final PR head. Windows local Gradle execution is unavailable because daemon loopback
connections are denied; hosted CI supplies the full Gradle verification evidence.

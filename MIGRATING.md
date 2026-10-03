# Migrating from 0.7.1 to 1.0.0

Version 1.0.0 establishes the CLI and build-plugin compatibility contract.
Existing options, plugin configuration names, threshold `8`, and exit codes
`0` through `3` retain their meanings. The core Java artifact remains internal.

## Runtime and build tools

- Use a full JDK 17, 21, or 25. The parser needs `jdk.compiler` and accepts
  the running JDK's supported non-preview source syntax.
- Use Maven 3.9.0 or newer within the 3.9.x line.
- Use Gradle 8.14.x on JDK 17/21, or Gradle 9.8.x on JDK 17/21/25.
- Update CLI filenames, Maven coordinates, and the Gradle plugin version together.

Artifacts still contain Java 17-compatible bytecode.

## TOON consumers

The dependency refresh moves from JToon 1.0.9 to 2.0.4. Empty method arrays
use the canonical representation:

```toon
status: passed
threshold: 8
methods: []
```

Do not infer a table header for an empty method list. Populated lists retain
the report's existing field names and meanings, for example:

```toon
status: passed
threshold: 8
methods[1]{status,cc,method,src,lineStart,lineEnd}:
  passed,1,example,src/main/java/demo/Sample.java,4,6
```

Update TOON parser fixtures and snapshots after reviewing these encoding changes.
JSON and JUnit field meanings stay unchanged. Primary-report filtering still
does not filter the complete JUnit sidecar.

## Verify the upgrade

Exercise an empty source selection and a populated project with every report
format consumed by automation. Check exit code `0` for success, `1` for invalid
input, and `2` for a threshold violation. Confirm Maven and Gradle integration
fixtures work with the selected toolchain. Use the release verification commands
in the README when downloading the final release.

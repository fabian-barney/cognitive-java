# Security Policy

## Supported Versions

Security fixes are provided for the most recent published release only. Please upgrade to the latest release before reporting an issue that may already be fixed.

## Reporting a Vulnerability

Report suspected vulnerabilities privately through GitHub Security Advisories:

https://github.com/fabian-barney/cognitive-java/security/advisories/new

Do not open a public issue for suspected vulnerabilities.

Reports are handled on a best-effort basis. You can generally expect an acknowledgement and preliminary severity assessment within 14 days when the report includes enough information to reproduce or reason about the issue. This project does not currently run a paid bug bounty program.

## Release integrity

Download releases from Maven Central, the Gradle Plugin Portal, or this repository's
GitHub Releases. Verify the signed checksum manifests and each detached signature
using the committed public key with primary fingerprint
`E6BB1FB6EE83EEAB7B408C6B5CA409BD8EE61724`.

Follow [artifact verification](RELEASE-ARTIFACTS.md) and the [release runbook](RELEASING.md)
for provenance and SBOM verification against the repository, source commit, and release
workflow. Report a signature, digest, or provenance mismatch privately through the
advisory channel above.

Release credentials remain repository secrets and are referenced only by the protected
`release` environment's publication job. The environment has no deployment reviewer;
reviewed version-bump merges intentionally authorize automatic publication. Protected
branches, required CI, immutable `v*` tags, pinned actions, and publication checks form
the release controls. Never send private signing keys or publication tokens in a report.

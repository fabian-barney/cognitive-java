# cognitive-java Specification

## 1. Purpose

`cognitive-java` is a Cognitive Complexity analyzer for Java projects.

It shall:

- locate Java source files to analyze
- parse Java methods with the JDK compiler tree APIs
- compute Cognitive Complexity from source only
- report the worst methods first
- fail when the maximum method Cognitive Complexity exceeds `25`

`cognitive-java` is intended as a project-quality gate rather than a mutation tool.

## 2. Scope

This specification defines:

- the command-line contract
- source file selection rules
- Java method parsing behavior
- Cognitive Complexity computation
- report ordering and exit codes

This specification does not define:

- configurable thresholds through the CLI
- non-Java source support
- machine-readable report formats
- semantic type resolution beyond parsing the selected source set

## 3. Command-Line Interface

### 3.1 Supported Forms

- `cognitive-java`
- `cognitive-java --changed`
- `cognitive-java <path...>`
- `cognitive-java --help`

### 3.2 Mode Semantics

- no arguments
  Analyze all Java source files under `src/`.

- `--changed`
  Analyze changed Java source files under `src/`.

- `<path...>`
  For each explicit path:
  - if it is a file, analyze that file
  - if it is a directory, analyze all Java files under that directory's `src/` subtree

- `--help`
  Print usage text and exit successfully.

### 3.3 Invalid Usage

- Unknown options shall fail with CLI usage error.
- The tool shall print usage text on CLI usage failure.

## 4. File Selection Rules

### 4.1 Default Source Discovery

In default mode, the tool shall analyze all `.java` files under:

- `<project-root>/src/**`

### 4.2 Changed-File Discovery

In `--changed` mode, the tool shall:

- invoke `git status --porcelain --untracked-files=all`
- interpret modified, added, and untracked Java files
- retain only `.java` files under `<project-root>/src/`
- sort the resulting file list in path order

### 4.3 Explicit Paths

When explicit paths are supplied:

- file paths shall be analyzed directly
- directory paths shall be expanded to `.java` files under `<dir>/src/**`
- duplicates shall be removed
- the final list shall be sorted in path order

### 4.4 Empty Selection

If no Java files are selected after expansion and filtering:

- the tool shall print `No Java files to analyze.`
- the tool shall exit successfully

## 5. Java Method Parsing

The tool shall parse Java source using the JDK compiler tree APIs.

The parser shall identify concrete method declarations and their basic attributes, including:

- class name
- method name
- source location
- Cognitive Complexity

The parser shall not require full semantic resolution of sibling or external symbols.

### 5.1 Exclusions

The method parser shall ignore:

- constructors
- abstract methods
- methods inside anonymous classes declared within method bodies

## 6. Cognitive Complexity

The implementation shall follow the SonarSource Cognitive Complexity rules as the source of truth.

It shall count, at minimum:

- `if`, `else if`, and `else`
- `switch` statements and switch expressions
- `for`, enhanced `for`, `while`, and `do while`
- `catch`
- ternary operators
- labeled `break` and labeled `continue`
- sequences of `&&` and `||`
- each method in a resolvable recursion cycle

It shall:

- ignore method declarations themselves
- ignore `try` and `finally`
- treat `else if` and `else` as hybrid increments
- apply nesting increments to structural elements as defined by the Sonar rules
- increment lambda bodies by increasing nesting rather than by adding direct complexity

## 7. Recursion Detection

Recursion detection shall be limited to resolvable calls within the selected source set.

The implementation may resolve:

- unqualified same-class calls
- `this` and `super` calls
- explicitly class-qualified calls when the target class exists in the selected source set

The implementation shall use arity-aware method identity when building recursion cycles.

## 8. Report

The tool shall print a tabular report containing, at minimum:

- method name
- class name
- Cognitive Complexity

The report shall:

- be titled `Cognitive Complexity Report`
- be sorted by Cognitive Complexity descending

## 9. Threshold

The Cognitive Complexity threshold shall be `25`.

If the maximum method Cognitive Complexity is greater than `25`:

- the tool shall print `Cognitive Complexity threshold exceeded: <max> > 25` to stderr
- the tool shall exit with threshold-failure status

If no methods are found:

- the maximum shall be treated as `0`
- the threshold shall not be considered exceeded

## 10. Exit Codes

- `0`
  Successful analysis, including empty selection or all scores at or below threshold.

- `1`
  CLI usage error.

- `2`
  Cognitive Complexity threshold exceeded.

## 11. Error Handling

The tool shall fail fast on:

- invalid command-line usage
- unreadable source files
- parser failures that prevent analysis

## 12. Conformance

An implementation conforms to this specification if it satisfies the CLI, file selection, parsing, Cognitive Complexity computation, reporting, and exit-code rules above.

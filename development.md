# Development Guide

**There are no hard rules; these are guidelines.** The one rule that must be followed: the `main`
branch is linear, and we rebase instead of merging.

This guide is adapted from [Rainbow Gum's](https://github.com/jstachio/rainbowgum/blob/main/development.md).

## Git Workflow

Keep git commit titles under 72 chars.

PR branches done by humans should be squashed before being merged if the history is not worth
preserving or is confusing. Agents should not squash until told to.

An agent coauthors commits that make novel additions to the code base (a `Co-Authored-By`
trailer). Novel means complicated logic. It does not mean unit tests, refactoring, trivial
documentation adjustments, dependency updates and other mechanical changes. If in doubt, the
agent asks.

## Build

Always build with `./mvnw`, not a system installed `mvn`. The wrapper pins the Maven version
(`.mvn/wrapper/maven-wrapper.properties`).

```
./mvnw verify
```

`verify` is the whole check, and every warning fails it:

- javac lint (`-Xlint:all`) with warnings as errors.
- doclint on every doc comment that exists, private ones included.
- [Error Prone](https://errorprone.info) default checks.
- [NullAway](https://github.com/uber/NullAway) in JSpecify mode, for `@NullMarked` packages.
- `javadoc` on the library module: every public type and member needs a doc comment,
  with `@param` and `@return`.

The library targets Java 25 (`maven.compiler.release`): the oldest LTS that has Markdown doc
comments. The build needs JDK 25 or newer. `.sdkmanrc` names the JDK we build with.

### Modules

| Module | What it is |
|--------|------------|
| `snaphop-ical` | The library: module and package `com.snaphop.ical` |
| `test/snaphop-ical-test` | End to end tests, with ical4j and ICAL.js as oracles |
| `test/corpus` | Test data from other projects, each with its license and `UPSTREAM.md`; not a Maven module |

## Publishing

The library and its parent pom are published to SnapHop's Maven repository, as
[flylite](https://github.com/snaphop/flylite) is; Maven Central may come later. Test modules are
never installed or published.

| Repository | Deployed to | Read from |
|------------|-------------|-----------|
| Releases | `https://maven.snaphop.com/deploy/repository/public-release/` | `https://maven.snaphop.com/release` |
| Snapshots | `https://maven.snaphop.com/deploy/repository/public-snapshot/` | |

The deploy targets and their user names are in `.snaphop/settings.xml`. The password comes from
the `NEXUS_SNAPHOP_COM_PASSWORD` environment variable, a secret of the GitHub repository (the
name is older than the server, which is not Nexus).

- **A release:** `bin/release.sh` on `main`, clean and in sync with `origin/main`. It sets the
  version to `X.Y.Z`, runs `verify` with the `release` profile, commits `Release X.Y.Z`, tags
  `vX.Y.Z`, commits `Prepare X.Y.(Z+1)-SNAPSHOT`, and pushes `main` and the tag. The tag runs the
  `Deploy to SnapHop` workflow, which builds, tests and deploys that version. `NO_PUSH=true`
  stops before pushing. The `release` profile refuses a `SNAPSHOT` version or dependency, and a
  build without `-s .snaphop/settings.xml`.
- **A snapshot:** run the `Deploy to SnapHop` workflow by hand on a branch.
- **From this machine**, as a fallback: `bin/deploy.sh`, with the password in the environment.

An agent does not release, push a tag or deploy unless asked to.

## Testing

Prefer end to end tests over unit tests: read real calendar text and check what comes out.

Prefer parameterized enum data driven tests over methods, for more permutations. A sample
`.ics` file is one enum constant.

Tests belong in a test module in the `test` directory, not in the module they test, unless they
are very fast. Slow tests always go in a test module. So does any test that needs another
library.

ical4j may be used in test modules as an oracle: read the same text with both libraries and
compare. It never becomes a dependency of the library module. So may
[ICAL.js](https://github.com/kewisch/ical.js), which the test module runs on GraalJS from its
source in `test/corpus/icaljs/lib`, copied from the same commit as the ICAL.js tests there. Where
an oracle works something out differently, the test lists the file or sample with why, and checks
it still differs, so that a fix on either side is noticed.

Data copied from other projects goes in `test/corpus/<project>`, with its license and an
`UPSTREAM.md` naming the repository, commit, original paths and any changes. The test module
reads it from there, by path.

Golden strings of error messages are desirable in this library. For error messages, always
assert the complete message with `assertEquals` and a Java text block (`"""`). Do not use
`contains` or other partial-message assertions. If a message includes nondeterministic values,
replace only those values with stable placeholders before comparing the full string.

Do not write unit tests that are not end to end just to improve code coverage.

While this library does not do mutation testing, the general idea is that if code is edited,
other than a refactor, a test should fail.

## Error Handling

Error messages are part of the UI and should be very clear with lots of context: what was
wrong, the line number, and the text of the line. We do not fail silently unless there is
absolutely no other choice.

Errors are exceptions or results, never log messages:

- `IcalReader.Mode.STRICT` throws `IcalParseException`.
- `IcalReader.Mode.LAX` skips what it cannot read and reports it as an `IcalWarning`: a record
  of its parts, never just a string.

### No logging

The library does not log. A calendar parser has no business writing to a log, and a logging
dependency would be the first runtime dependency. If something ever must be logged, use the
JDK's `System.Logger`, never SLF4J or any other logging library.

## Dependencies

The library module has no runtime dependencies. JSpecify (`org.jspecify`) is the one allowed
dependency, and only at compile time: `requires static transitive org.jspecify` in
`module-info.java`, `optional` in the pom.

Integration with any other library never goes into the library module. It goes into a module
of its own.

## API Design and Code Style

Minimize API surface and allow future flexibility. Limit packages to very few, as it is hard to
encapsulate across packages. The main package has the same name as the module:
`com.snaphop.ical`.

Public types that could be confused with types in the JDK or other calendar libraries
(`Calendar`, `Event`) are prefixed with `Ical`.

Prefer interfaces over abstract classes. If an abstract adapter is worthwhile, prefix it with
`Abstract`. The prefix marks it as a skeleton for implementing a type, not a type with its own
identity, so nothing should check `instanceof` against it or take it as a parameter.

Protected methods of an abstract adapter class that a subclass implements or overrides are
prefixed with `do`. Interfaces do not need this.

Inner classes in public interfaces are public. Use top level package-private classes instead,
unless the nested type is meant to be public (a builder, a mode enum).

Most classes should be sealed or final; in other words, not extendable. Public interfaces are
sealed unless callers are meant to implement them.

Prefer putting a package-private implementation class in the same file as the public
interface it implements, rather than in its own file. Java allows multiple top level types per
file as long as only one is public. `IcalEvent.java` is the example: `DefaultIcalEvent` lives
alongside it. A separate file is fine once the implementation gets large enough that
co-locating it would hurt readability, but that should be the exception.

Prefer builders over getters and setters; builders are the only classes that get to be
mutable. A builder does not need a one to one mapping with what it builds; it can hand back
different implementations of a shared interface depending on what got configured. Builders are
preferred over static factory methods that take a pile of arguments.

Other than builders, the code is thread-safe. Results are immutable: records, sealed
interfaces, and lists from `List.copyOf`.

We use both `package-info.java` and `module-info.java`. Annotate the package with JSpecify's
`@NullMarked`, not the module, as that is not well supported.

Methods that may return null are suffixed with `OrNull`. Accessors follow modern record style
and are not prefixed with `get`.

Prefer a setting whose values name distinct modes over a boolean. `IcalReader.Mode` with
`STRICT` and `LAX` is the example, rather than a `strict(boolean)`.

Keep the existing two-space indentation and follow surrounding formatting.

### Javadoc

Doc comments are Markdown (`///`, JEP 467), not HTML `/** */`. Link with `[Type]` and
`[Type#method()]`; write code in backticks.

Public javadoc should never go into implementation details that are likely to change or are
hidden. Public javadoc should not reference package-private or private classes.

Package-private code needs no comments. When it has them, doclint checks them.

## Human specific

This is for humans: try not to write documentation targeted specifically at agents. We do not
make AGENTS.md files.

Colorful comments in code can add levity, and they make it clear an agent probably did not
write that code, but do not put them in javadoc.

We do not add license headers to source code; they add zero protection.

## Writing documentation

Never use a hyphen or dash as a parenthetical or some form of clause injection. A general rule:
if there is a space on both sides of the hyphen, an agent probably wrote it. Use multiple
sentences, colons, semicolons, parentheses, or best of all structured documentation.

Prefer structured documentation over giant paragraphs: ordered and unordered lists, and
sections.

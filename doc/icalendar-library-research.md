# iCalendar library designs and reusable test corpora

Research date: 2026-11-05. Repository baseline: `67197d0`, on the upstream
`initial-structure` branch. This report changes documentation only.

## Recommendations

For this small, immutable, dependency-free Java reader, study **ICAL.js for
layering, iCal.NET's current rewrite for temporal modeling and occurrence
evaluation, and libical for interoperability and reusable test data**.
Biweekly and dmfs/lib-recur provide useful Java comparisons.

There is no demonstrated community consensus that one implementation is the
best designed. These recommendations are my assessment of the inspected APIs,
source, tests and documented limits. Popularity and an “RFC compliant” label
are not correctness evidence.

The most useful testing discoveries are:

1. **libical has a language-neutral recurrence specification file** containing
   `RRULE`, `DTSTART`, optional `START-AT`, and exact expected `INSTANCES`.
   The inspected file contains **157 rule records**. This is the closest
   recurrence equivalent to the requested Mustache-style specification data.
2. **ICAL.js has paired input `.ics` and expected `.json` files**. I exercised
   all **21 iCalendar pairs** through its public parser and semantic round-trip
   path successfully. Its separate `ICALTester` generates rules from JSON and
   compares implementations through a command-line adapter.
3. **libical and Python's `icalendar` have concrete OSS-Fuzz integrations**.
   Python also has Hypothesis-generated round-trip tests. These offer both
   seed inputs and harness ideas.
4. **Dart and Swift both have implementations**, but none inspected should be
   adopted wholesale as a full-calendar correctness oracle. Dart's most
   interesting architecture has reproducible gaps; Swift's Foundation API is
   a recurrence facility, not an `.ics` reader.

Use copied/adapted fixtures as **test resources**, keeping provenance and the
applicable licenses. Do not add these implementations to the library's runtime.
The findings below identify reusable assets; no upstream corpus was vendored
and no new test dependencies were added by this research.

## What exists in snaphop-ical today

The [README](../README.md) and [development guide](../development.md) already
choose a small API, immutable results, `java.time`, no runtime dependencies and
public-API tests in a separate module. That direction fits the strongest designs
found here. A full editable iCalendar object model would exceed the stated
read-events-and-expand scope.

Current implementation limits matter when interpreting a passing build:

- [IcalReader](../snaphop-ical/src/main/java/com/snaphop/ical/IcalReader.java)
  currently throws `UnsupportedOperationException` when reading text.
- [SampleCalendarTest](../test/snaphop-ical-test/src/test/java/com/snaphop/ical/test/SampleCalendarTest.java)
  checks a sample with ical4j; the test comparing our reader is disabled.
- [IcalTime](../snaphop-ical/src/main/java/com/snaphop/ical/IcalTime.java) exposes
  DATE and zoned DATE-TIME. It intentionally resolves floating input in the
  reader's configured zone. Preserve the original temporal form internally
  before resolving it, even if the public result remains this convenient view.
- The `Reader` overload currently buffers the entire input with `StringWriter`.
  It is a character-stream entry point, not an incremental parser yet.

`./mvnw verify` passed with Java 27: **one active test passed and one was skipped**.
That establishes the scaffold's build, analysis and Javadoc checks, not calendar
parsing or recurrence correctness.

## Reusable tests: the closest matches to Mustache's spec files

The requested reference, [mustache/spec/specs](https://github.com/mustache/spec/tree/master/specs),
separates examples and expected behavior from a particular implementation.
No equally universal iCalendar conformance corpus was established by this
survey. There are several useful building blocks with different scopes.

Here, end-to-end means real `.ics` input through the public reader to events
and occurrences. These library suites do not establish interoperability by
launching Google Calendar, Outlook or Apple Calendar, or by exercising CalDAV.

| Source | Actual asset and shape | Reuse assessment |
|---|---|---|
| [libical recurrence specifications](https://github.com/libical/libical/blob/38b952750a89293f14866562d5e13dd07edfcf11/src/test/icalrecur_test.txt) | 157 blank-line-separated records of rule, start, optional seek start and exact occurrence strings. Related files cover non-Gregorian recurrence. | **Best ready-made recurrence data.** Import records into our test format; retain the original file and origin for traceability. |
| [libical full event data](https://github.com/libical/libical/blob/38b952750a89293f14866562d5e13dd07edfcf11/test-data/recur.txt) | Actual `VEVENT` text, descriptions and `X-EXPECT-NUMEVENTS` assertions. | Good read-and-expand seeds. Count alone is weaker than checking exact dates, times and identities. |
| [ICAL.js parser pairs](https://github.com/kewisch/ical.js/tree/127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e/test/parser) | `.ics` input paired with expected jCal JSON, exercised by a generic runner. Also includes separate vCard cases. | **Best ready-made parser data.** Select the 21 `.ics` pairs; adapt jCal expectations to our smaller model or an internal syntax projection. |
| [ICAL.js ICALTester](https://github.com/kewisch/ical.js/tree/127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e/tools/ICALTester) | `rules.json` templates, random substitution, and executable adapters returning occurrence lines. | Good differential-testing design. Generated comparisons are not reviewed golden answers. Repair and harden the harness before using it. |
| [Python icalendar test resources](https://github.com/collective/icalendar/tree/066c4ccddb8b7e917c8c97314859343163a8be56/src/icalendar/tests) | 166 `.ics` resources in the inspected test tree, organized around events, timezones, alarms and reported failures. Expectations generally live in Python tests. | Excellent real-world parser/regression inputs. Extract assertions as well as inputs; not a ready-made neutral spec by itself. |
| [iCal.NET calendar fixtures](https://github.com/ical-org/ical.net/tree/69bcfced1aad318d058e54c7bc8243df9e44bcc9/Ical.Net.Tests/Calendars) | 150 `.ics` files; typed C# assertions and an occurrence comparison helper. | Strong event/exception/DST examples. Convert expected periods into data instead of translating all the test machinery. |
| [dmfs/lib-recur tests](https://github.com/dmfs/lib-recur/tree/5ba0c83529d47e8020fa3b6411689841edca6242/src/test/java/org/dmfs/rfc5545/recur) | Java data builders, expected result assertions, rule equivalence cases and set-iterator tests. | Useful recurrence cases and metamorphic properties. Cases are embedded in Java, not standalone JSON/YAML. |
| [Dart RFC examples](https://github.com/kozw/firstfloor_calendar/blob/a15e20955c18ab6371a3089e93c71f517574f689/test/semantic/rfc5545_examples_test.dart) | Calendar/property text and expected dates inside Dart tests. | Useful examples to translate. The suite is not proof of complete calendar semantics. |
| [Swift parser fixtures](https://github.com/redryerye/iCalendarParser/tree/7cd0344720ac725995a83e1128038f2dce0e84dd/Tests/iCalendarParserTests) | XCTest assertions plus real calendar text resources. | Parser examples, not a complete recurrence specification. |

### How to import the useful data

For libical's recurrence records, inspect the
[existing runner](https://github.com/libical/libical/blob/38b952750a89293f14866562d5e13dd07edfcf11/src/test/icalrecur_test.c)
alongside the data. `START-AT` changes iteration behavior; it is not a new
`DTSTART`. Some inputs exercise legacy/compatibility behavior, so do not label
all expected results as strict RFC requirements without checking the input.

To make these end-to-end tests of snaphop-ical, wrap each suitable rule/start in
a complete calendar with a stable UID and DTSTAMP, call `IcalReader.read`, and
compare public occurrences. Keep a separate rule-level adapter only if needed
to test seeking more precisely. Partition strict-valid, lax-compatibility,
unsupported-feature and invalid-input cases explicitly.

ICAL.js's [generic parser runner](https://github.com/kewisch/ical.js/blob/127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e/test/parse_test.js)
already checks both parse output and parse/serialize/parse equality. Its own
comment correctly warns that a parser and serializer can agree on the same
mistake. Cross-library read-back is useful for the rewrite's test tooling even
though serialization is outside the proposed production reader's scope.

Do not require our public API to expose jCal just to consume its test corpus.
Keep richer syntax assertions behind a test adapter or compare only explicitly
selected public event fields. Record skipped expectations so a projection that
checks only SUMMARY cannot appear to validate an entire calendar.

### License and provenance when copying

| Asset | Evidence inspected | What to preserve |
|---|---|---|
| libical recurrence and calendar data | [REUSE.toml](https://github.com/libical/libical/blob/38b952750a89293f14866562d5e13dd07edfcf11/REUSE.toml) explicitly assigns `test-data/**` and `src/test/**.txt` the expression `LGPL-2.1-only OR MPL-2.0`. | Keep the selected licensing terms, attribution and upstream path/commit with copied data. It is not public-domain data merely because it resembles RFC examples. |
| ICAL.js fixtures and ICALTester | [Project license](https://github.com/kewisch/ical.js/blob/127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e/LICENSE), MPL-2.0; harness source also has notices. | Keep provenance and notices; track converted fixtures as derived data. |
| Python icalendar resources | [LICENSE.rst](https://github.com/collective/icalendar/blob/066c4ccddb8b7e917c8c97314859343163a8be56/LICENSE.rst), BSD-2-Clause terms. Its Google-authored fuzz harness is separately Apache-2.0. | Preserve the appropriate notice for each copied asset and inspect fixture-specific provenance. |
| iCal.NET | [license.md](https://github.com/ical-org/ical.net/blob/69bcfced1aad318d058e54c7bc8243df9e44bcc9/license.md), MIT, with a separate-license exception for `Ical.Net.Tests/contrib`. | Do not assume every test subtree has the top-level license. |
| dmfs/lib-recur | [LICENSE](https://github.com/dmfs/lib-recur/blob/5ba0c83529d47e8020fa3b6411689841edca6242/LICENSE) and [NOTICE](https://github.com/dmfs/lib-recur/blob/5ba0c83529d47e8020fa3b6411689841edca6242/NOTICE), Apache-2.0. | Keep attribution and provenance when adapting cases. |

A small `UPSTREAM.md` beside imported resources should record repository, commit,
original path, license, local case IDs and intentional changes. Keep imported
cases distinct from new project-authored examples. Public calendar fixtures can
contain names, addresses and URLs; inspect and sanitize what we redistribute,
while retaining attribution and the behavior the fixture was meant to exercise.

## Fuzzing and generative testing: verified evidence

| Implementation | Evidence | What it establishes |
|---|---|---|
| **libical** | [OSS-Fuzz project](https://github.com/google/oss-fuzz/tree/master/projects/libical), [build targets](https://github.com/google/oss-fuzz/blob/master/projects/libical/build.sh), parse and parse/normalize fuzz harnesses; checked-in `test-data/fuzz*` regressions and CMake registration. | Concrete coverage-guided parser fuzz infrastructure and replayable crash inputs. This does not certify all recurrence results. |
| **Python icalendar** | [Atheris harness](https://github.com/collective/icalendar/blob/066c4ccddb8b7e917c8c97314859343163a8be56/src/icalendar/fuzzing/ical_fuzzer.py), [CIFuzz workflow](https://github.com/collective/icalendar/blob/066c4ccddb8b7e917c8c97314859343163a8be56/.github/workflows/cifuzz.yml), [Hypothesis test](https://github.com/collective/icalendar/blob/066c4ccddb8b7e917c8c97314859343163a8be56/src/icalendar/tests/hypothesis/test_fuzzing.py). | Both malformed-input fuzzing and structured generation are implemented. Hypothesis constructs content lines/parameters and checks round-trip behavior, with up to 1,000 examples configured for that test. |
| **ICAL.js** | `ICALTester` generates random recurrence values from JSON and compares two implementations. | Concrete structured differential testing. I did not find a dedicated coverage-guided parser fuzz harness in the inspected test/tool/workflow paths. |
| **dmfs/lib-recur** | [RecurrenceEquivalenceTest](https://github.com/dmfs/lib-recur/blob/5ba0c83529d47e8020fa3b6411689841edca6242/src/test/java/org/dmfs/rfc5545/recur/RecurrenceEquivalenceTest.java) checks hand-authored equivalent rules. | Useful metamorphic testing, not evidence of coverage-guided fuzzing or unrestricted random generation. |
| **calcard** | [README](https://github.com/stalwartlabs/calcard/blob/ef896a8753113886bc1996578b158d4066979c13/README.md) advertises `fuzz_all`, `fuzz_random`, `fuzz_structured` and Miri commands. | **Documentation-only claim in this checkout.** No `fuzz/` directory or matching targets were present in the inspected tree. Do not count them as verified runnable harnesses. |
| **Dart firstfloor_calendar, Swift iCalendarParser/RRuleKit, iCal.NET** | Inspected source/tests and relevant manifests. | Example/regression tests were found; no dedicated fuzz harness was identified in those inspected paths. This is a bounded search, not proof none exists elsewhere. |

The [Python fuzzing guide](https://icalendar.readthedocs.io/en/stable/how-to/fuzz-testing.html)
also documents turning minimized fuzz failures into committed `.ics` regression
files. That workflow is worth copying: discoveries become normal tests, rather
than remaining only in a fuzzing service's artifact store.

### ICALTester needs adaptation, not blind execution

Its CLI contract is particularly useful: pass `(rrule, dtstart, occurrenceCount)`
to an executable and receive one occurrence per output line. A Java adapter can
join this comparison without shipping JS or C dependencies.

However, the inspected
[compare.js](https://github.com/kewisch/ical.js/blob/127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e/tools/ICALTester/compare.js)
and [generator](https://github.com/kewisch/ical.js/blob/127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e/tools/ICALTester/lib/ICALTester.js)
have limitations:

- Randomness uses `Math.random()` without a recorded seed or shrinker.
- One fixed start and ten occurrences do not cover long-horizon behavior.
- A child stdout handler splits each chunk independently; partial lines across
  chunks need buffering in a replacement adapter.
- Timeout handling uses different time units between JS elapsed time and the
  child-process timer. Put each implementation behind one explicit deadline.
- The JS handler's success callback does not match the surrounding Node-style
  `(error, result)` convention. Verify/fix the harness before relying on its diff.
- The example contains a start date that need not match the generated rule.
  Separate those undefined/compatibility cases from normative comparisons.

I inspected this tool but did not run it. The successful 21-fixture parser check
and the two direct API probes described later do not validate ICALTester itself.

## Cross-language design comparison

“Full” below means intended coverage of calendar parsing/modeling and related
operations, not a certification of every RFC clause.

| Language / implementation | Scope | Design worth studying | Limit for this project |
|---|---|---|---|
| JavaScript: **ICAL.js** | Calendar/vCard parsing, writing, recurrence and event exceptions | jCal syntax data, component/property wrappers, then an event layer; lazy recurrence iteration | Mutable wrappers and shared backing data are unsuitable as our public immutable API. |
| C: **libical** | Broad calendar/protocol implementation | Generic component/property/value vocabulary, iterators and extensive compatibility data | Native lifecycle/global-setting patterns should not be reproduced in Java. |
| C#: **iCal.NET** | Calendar model, serialization, recurrence, alarms | Current v6 development uses explicit temporal values, per-calendar timezone providers and ordered occurrence evaluation | v6 was advertised as prerelease when inspected. Distinguish its new code from v5 documentation. |
| Java: **biweekly** | Broad reader/writer and calendar model | Per-property codecs, raw fallbacks and parse warnings | Class-heavy mutable model; legacy `Date` representation is not our target. |
| Java: **dmfs/lib-recur** | Recurrence engine, not an `.ics` calendar reader | Composable recurrence sets, iterators and explicit RFC modes | Additional date types/runtime dependencies do not fit our no-dependency core. |
| Python: **icalendar** | Calendar parser/generator and typed properties | Generic property access plus ergonomic typed attributes; robust input corpus | Python dictionary ergonomics should not become a public `Map<String,Object>` Java API. |
| PHP: **sabre/vobject** | Calendar/vCard object model and expansion | Generic tree with a convenient facade, validation/repair and bounded event expansion | Dynamic magic access and documented recurrence subsets are not a full-RRULE oracle. |
| Rust: **calcard** | Calendar/contact parsing and conversion, recurrence | Typed value enums, explicit parser outcomes and optional format features | Wider JSCalendar/JMAP/contact scope; advertised fuzz targets were absent in the snapshot. |
| Dart: **firstfloor_calendar** | Document and semantic parsers, serializer, recurrence | Clear syntax/semantics split, raw property retention and streaming entry points | API probes found round-trip, timezone fallback and moved-exception gaps. |
| Dart: **icalendar_parser** | `.ics` parsing into structured/map-like data | Lightweight pure-Dart ingestion and custom field registration | Parsing RRULE text is not recurrence expansion or a full round-trip model. |
| Dart: **rrule** | RRULE parsing and iteration only | Small focused codec and lazy occurrence API | Documents UTC-oriented use and fixed Monday week start; not an entire calendar implementation. |
| Swift: **Foundation Calendar.RecurrenceRule** | Typed recurrence description/evaluation | Value types, explicit calendar, end conditions, gap and overlap policies | Not an `.ics` codec or an exception-set/calendar model. Defaults differ from RFC skip behavior. |
| Swift: **iCalendarParser** | Calendar parser | Small dependency-free package with preserved properties/parameters | README explicitly lists expansion and recurrence overrides as unfinished. |
| Swift: **ICalendarKit** | Encoder | Compact Swift value-oriented construction | Not a reader. Its date wrapper is UTC/instant-oriented. |
| Swift: **RRuleKit** | RRULE string bridge to Foundation | Uses Swift parse/format conventions instead of a new recurrence engine | Parser has concrete RFC coverage limits discussed below. |

### The strongest architectural references

**ICAL.js:** Its [component source](https://github.com/kewisch/ical.js/blob/127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e/lib/ical/component.js)
wraps jCal and lazily hydrates richer objects. Its
[event layer](https://github.com/kewisch/ical.js/blob/127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e/lib/ical/event.js)
associates recurrence exceptions, rather than expecting the RRULE iterator to
understand whole calendars. Borrow that separation. A parser can preserve syntax
the small public event API does not expose. Keep all such machinery private.
The [project](https://github.com/kewisch/ical.js) has no runtime dependencies;
timezone data is separately supplied. Its algorithms share libical ancestry,
so agreement between those two is not wholly independent evidence.

**iCal.NET:** The inspected
[CalDateTime](https://github.com/ical-org/ical.net/blob/69bcfced1aad318d058e54c7bc8243df9e44bcc9/Ical.Net/DataTypes/CalDateTime.cs)
stores local date, optional local time and timezone ID, instead of immediately
collapsing everything to an instant. The
[calendar evaluator](https://github.com/ical-org/ical.net/blob/69bcfced1aad318d058e54c7bc8243df9e44bcc9/Ical.Net/Calendar.cs)
removes overridden UID/RECURRENCE-ID instances before merging ordered results.
[EvaluationOptions](https://github.com/ical-org/ical.net/blob/69bcfced1aad318d058e54c7bc8243df9e44bcc9/Ical.Net/Evaluation/EvaluationOptions.cs)
can limit unmatched increments and signal exhaustion exceptionally. This is
more useful than limiting only successful matches. The
[README](https://github.com/ical-org/ical.net) identifies this as the v6 rewrite
and describes the NodaTime dependency; we can study its modeling without adding
NodaTime or another runtime here.

**libical:** Its [line parser](https://libical.github.io/libical/v4/apidocs/icalparser_8h.html)
and [recurrence iterator](https://libical.github.io/libical/v4/apidocs/icalrecur_8h.html)
are distinct facilities. The [project's own user list](https://github.com/libical/libical)
includes Cyrus, Evolution's data server and Fantastical, which is stronger
interoperability evidence than stars. It is still not an infallible oracle.

**Biweekly:** [ParseContext](https://github.com/mangstadt/biweekly/blob/84d8003858602a978cdb21e711f0c369d14018f8/src/main/java/biweekly/io/ParseContext.java)
collects warnings and defers timezone association until more of the calendar is
known. That is useful when VTIMEZONE appears after an event. Its property
“scribes” isolate codecs. Borrow small private codecs and contextual diagnostics,
not the entire public hierarchy or
[ICalDate's Date inheritance](https://github.com/mangstadt/biweekly/blob/84d8003858602a978cdb21e711f0c369d14018f8/src/main/java/biweekly/util/ICalDate.java).

**Python and PHP:** Python's [API design notes](https://icalendar.readthedocs.io/en/stable/explanation/api-design.html)
describe raw dictionary access alongside checked typed attributes. Sabre's
[calendar guide](https://sabre.io/vobject/icalendar/) and
[recurrence guide](https://sabre.io/vobject/recurrence/) show a generic tree,
validation/repair and range-based expansion. Both reinforce a narrow public
facade over richer internal data. Neither requires us to expose an editable
calendar framework.

**Rust:** calcard's [typed value enums](https://github.com/stalwartlabs/calcard/blob/ef896a8753113886bc1996578b158d4066979c13/src/icalendar/mod.rs)
and [parser outcome examples](https://github.com/stalwartlabs/calcard/blob/ef896a8753113886bc1996578b158d4066979c13/README.md)
make invalid lines and unexpected component ends explicit. Its
[manifest](https://github.com/stalwartlabs/calcard/blob/ef896a8753113886bc1996578b158d4066979c13/Cargo.toml)
separates optional features. The lesson is explicit values and errors, not
adopting its wider format stack.

## Dart: what is available and what was verified

### firstfloor_calendar 1.0.14

[Package documentation](https://pub.dev/packages/firstfloor_calendar) describes
two layers: raw `CalendarDocument`/properties and typed calendar semantics.
The inspected manifest has two runtime dependencies, `collection` and
`timezone`. Unknown properties retain raw backing data; the stream parser can
emit top-level components. This is the most relevant Dart architecture found
for studying a Java rewrite, rather than a claim that it is the best-tested
production choice.

Three public-API probes against commit `a15e209` found:

| Probe | Observed behavior | Design consequence |
|---|---|---|
| Parse and serialize `ATTENDEE;CN="Doe, Jane":mailto:jane@example.com`, then parse again | CN changes from one value `Doe, Jane` to two values `Doe` and ` Jane`; serialization omitted the needed quotes. | Raw document objects alone do not guarantee semantic round-trip safety. |
| Construct a local datetime with TZID `Research/Unregistered` | The text TZID remains, but `.native` silently uses the host's EST zone in this run. | Unknown timezone resolution must not silently depend on the process environment. |
| Master daily event, COUNT=2, with the second instance moved from 10:00 to 14:00 in a same-UID override | Calendar occurrence query returns all three starts: day one 10:00, day two 10:00, day two 14:00. | Expanding each VEVENT separately is not resolving a recurring series. |

Relevant source locations:
[parameter serialization](https://github.com/kozw/firstfloor_calendar/blob/a15e20955c18ab6371a3089e93c71f517574f689/lib/src/document/calendar_document.dart#L89),
[timezone fallback](https://github.com/kozw/firstfloor_calendar/blob/a15e20955c18ab6371a3089e93c71f517574f689/lib/src/semantic/calendar_types.dart#L286),
[calendar occurrence query](https://github.com/kozw/firstfloor_calendar/blob/a15e20955c18ab6371a3089e93c71f517574f689/lib/src/semantic/extensions/queries.dart#L46).

The existing suite ran **892 passing, one failing** in the host's Eastern zone.
The failing timezone-transition case expected `20250309T020000` but received
`20250309T030000`. Under `TZ=UTC`, **all 893 passed**. This demonstrates
environment sensitivity in this checkout; it does not prove every timezone
operation is wrong. These runs resolved `timezone` 0.11.2.

### Other Dart choices

- [icalendar_parser 2.1.0](https://pub.dev/packages/icalendar_parser): pure-Dart
  parser with custom fields and structured output. Useful for ingestion,
  but its documented property list is not evidence of expansion/exception
  handling. I did not execute it.
- [rrule 0.2.18](https://pub.dev/packages/rrule): focused rule codec and occurrence
  iteration, partially derived from rrule.js. It documents Monday-only `WKST`
  and UTC-oriented datetime handling. It cannot replace an `.ics` reader.
- [ical 0.2.2](https://pub.dev/packages/ical): generation/serialization, with
  timezone and EXDATE support explicitly absent from its feature list.
- [dart_icalendar 1.1.1](https://pub.dev/packages/dart_icalendar): advertises a
  broader model, but its own README says it is unfinished and needs more testing.

Do not confuse calendar widgets, recurrence-picker widgets or native-calendar
plugins with implementations of the iCalendar data format.

## Swift: useful APIs, separate responsibilities

### Foundation Calendar.RecurrenceRule

The accepted [SF-0009 proposal](https://github.com/swiftlang/swift-foundation/blob/main/Proposals/0009-calendar-recurrence-rule.md)
is worth reading for typed frequencies, recurrence end conditions, calendar
context and explicit invalid/repeated-time policies. It does not parse a
VCALENDAR, associate overrides, or provide a complete iCalendar codec.
The frequency model omits SECONDLY.

The [current implementation](https://github.com/swiftlang/swift-foundation/blob/main/Sources/FoundationEssentials/Calendar/RecurrenceRule.swift)
defaults to `.nextTimePreservingSmallerComponents`; `.strict` is the policy for
skipping nonexistent dates. Therefore an API that looks suitable for RFC rules
can still have different default scheduling semantics. Choose RFC behavior
deliberately rather than inheriting platform defaults. Its documented Apple
availability starts with iOS 18 and macOS 15; check any newer convenience API
separately. I did not compile or run Swift here.

### Swift packages

- [redryerye/iCalendarParser](https://github.com/redryerye/iCalendarParser)
  is a real Swift `.ics` parser with no package dependencies. Its README
  explicitly marks recurrence expansion, applying RDATE/EXDATE, overrides and
  full calendar serialization as unfinished. A source-level date wrapper
  carries a Foundation `Date`, value type and optional TZID. Do not mistake
  parsing a recurrence property for implementing its semantics.
- [swift-calendar/ICalendarKit](https://github.com/swift-calendar/icalendarkit)
  is an encoder with a VComponentKit dependency. Its
  [date wrapper](https://github.com/swift-calendar/icalendarkit/blob/389fceafc1132ef284c6215118421bc88f1bfc8c/Sources/ICalendarKit/structures/ICalendarDate.swift)
  stores `Date` plus a date-only flag and serializes in UTC. This is compact,
  but not a model to copy for floating local times and arbitrary embedded zones.
- [kubens/RRuleKit](https://github.com/kubens/RRuleKit) bridges RRULE strings to
  Foundation using parse/format APIs, with no package dependencies. It is not
  an entire calendar parser. At the inspected revision, its
  [parser](https://github.com/kubens/RRuleKit/blob/98f254d81d7ddad1258e798c9f4410f31fc3771a/Sources/RRuleKit/RecurrenceRuleRFC5545FormatStyle.swift#L376)
  requires FREQ first, has no WKST branch and rejects unsupported keys. RFC 5545
  requires consumers to accept rule parts in any order. Its formatter also
  handles an `UNTIL=TZID=...` form outside the RFC RECUR grammar. These are
  source-review findings, not executed Swift test results.

Foundation and EventKit are useful platform facilities, but should not become
the definition of our language-neutral `.ics` semantics.

## Design implications for snaphop-ical

### Keep syntax, semantic values and evaluated occurrences separate

Use a private content-line/component representation retaining name, parameters,
raw value and source position. Decode only the values needed by `IcalEvent`
and recurrence evaluation. Do not add a public class per RFC property.

Keep date-only, floating local, UTC and TZID-bearing local forms distinguishable
until evaluation. The current public `IcalTime` can remain resolved, but retaining
the source kind prevents host-zone interpretation from changing recurrence
identity or UNTIL validation. `java.time` alone cannot resolve every arbitrary
VTIMEZONE identifier: define embedded-zone, IANA lookup and unresolved-zone
behavior explicitly. No silent network lookup or host-zone fallback.

### Treat series evaluation as more than RRULE iteration

Group by UID and original RECURRENCE-ID, apply exclusions/additions and resolve
overrides before selecting results for the requested window. Include a moved
exception whose actual time enters the window even if its original time was
outside. The inverse case must not leave a ghost occurrence behind.

Preserve the original recurrence identity on `IcalOccurrence`, not only the
modified start. Keep recurrence-rule iteration independently testable, but
verify the complete read-and-expand path through the public API.

### Put limits on work, not just output

The reader already requires a window for expansion. Add implementation budgets
for input size, unfolded line size, nesting, properties, generated candidates
and unmatched recurrence increments. A finite window or `.take(10)` does not
prevent expensive searching for ten matching instances. Report budget exhaustion
as a failure/diagnostic, never a complete-looking truncated result.

Strict/lax behavior should be local to the reader configuration. In lax mode,
do not silently drop an unsupported RRULE and present the event as a verified
one-off occurrence. Return a warning and withhold unsafe expansion according
to an explicit contract. Keep logging and automatic URI/attachment fetching
out of the core, consistent with the existing design.

### Distinguish syntax preservation from byte preservation

A semantic parser may normalize case, quoting and line folding. That differs
from retaining original bytes for signatures or exact file edits. The current
reader does not need a byte-preserving editor. It does need correct parameter
decoding, line unfolding, and source locations across folded content.

Use [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545.html) to adjudicate the
specific requirements, including temporal forms, end boundaries, recurrence
sets and rule ordering. Parameter encoding also has a later update in
[RFC 6868](https://www.rfc-editor.org/rfc/rfc6868.html). A collection of matching
library outputs is supporting evidence, not a substitute for those rules.

## A small, portable specification format for our tests

Keep the existing `Sample` enum as a JUnit discovery/index mechanism, but let
each sample point to data and exact expected results. For cross-language reuse,
use an `.ics` file plus a JSON expectation. JSON support, if added, belongs only
in the test module. An initial enum plus golden text approach can avoid even
that test dependency.

Suggested layout, not files added by this change:

```text
test/snaphop-ical-test/src/test/resources/spec/
  upstream/UPSTREAM.md
  parser/
  recurrence/
  exceptions/
  timezones/
  invalid/
  fuzz-regressions/
```

This original example illustrates a public read-and-expand case:

```json
{
  "schemaVersion": 1,
  "id": "monthly-last-weekday",
  "operation": "read-and-expand",
  "input": "monthly-last-weekday.ics",
  "mode": "STRICT",
  "zone": "UTC",
  "window": {"start": "2026-01-01", "endExclusive": "2026-04-01"},
  "expected": {
    "starts": [
      "2026-01-30T09:00:00Z",
      "2026-02-27T09:00:00Z",
      "2026-03-31T09:00:00Z"
    ],
    "warnings": []
  }
}
```

Its input is a complete VEVENT with `DTSTART:20260130T090000Z` and
`RRULE:FREQ=MONTHLY;COUNT=3;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1`, enclosed in
VCALENDAR with stable UID and DTSTAMP. A production spec should also assert
end time, UID, recurrence identity and temporal kind where relevant.

Each case should name its operation, strict/lax policy, window, timezone context,
expected events/occurrences, warnings or full error message, and provenance.
Use explicit end-exclusive window semantics matching `IcalReader` today.
For malformed input, assert the complete user-facing diagnostic, including the
logical/physical line policy. Never import another library's English error
message as this project's required UI text.

Pin timezone data/environment for deterministic checks and also run a small
host-zone matrix. Keep `UTC`, `America/New_York` and a non-hour-offset zone in
that matrix. Reference-runner disagreements and unsupported cases should be
reported explicitly, not silently skipped or blessed by majority vote.

## Suggested test implementation order

1. **Public read-path goldens:** adapt selected ICAL.js/parser and real-world
   `.ics` inputs, plus strict/lax exact-message cases. Cover folded Unicode,
   quoted parameter separators and unknown extensions.
2. **Recurrence specifications:** import and classify libical's 157 records;
   wrap supported cases as full calendars. Add explicit COUNT, UNTIL, WKST,
   negative BY values, BYSETPOS and month-end assertions.
3. **Calendar semantics:** same-UID overrides, cancelled instances, RDATE/EXDATE,
   moved exceptions entering/leaving windows, all-day boundaries and DST cases.
   Reuse iCal.NET/Python examples with converted expected outputs.
4. **Differential adapters:** keep ical4j as the first test-only oracle; add
   independent JS/C/Python runners outside the library module where useful.
   ICAL.js and libical share ancestry, so they are not independent votes.
5. **Seeded structured generation:** rule-part permutations, legal folding,
   equivalent-rule transformations and window partitioning. Persist failing
   seeds, minimize inputs and turn every fixed failure into a golden case.
6. **Coverage-guided malformed input:** consider a separate test-only
   [Jazzer](https://github.com/CodeIntelligenceTesting/jazzer) module/profile.
   Jazzer provides JVM coverage-guided fuzzing; its compatibility with this
   exact Java/JUnit/Error Prone setup needs a smoke test before adoption.

The fuzz target should call the public reader and, for valid results, force
bounded occurrence evaluation. Documented parse rejection is expected;
`StackOverflowError`, indexing bugs, hangs and unexpected exceptions are not.
Keep a hard process timeout/memory budget for resource tests. A Java analogue
of Python's Hypothesis tests can start with deterministic JUnit generators;
there is no need to add a second framework just to shuffle rule parts.

Mutation-style transformations should preserve a stated invariant. For example,
case-normalizing property names or refolding content must preserve semantic
events; reordering rule parts must preserve expansion. Changing DTSTART is not
generally invariant. Unioning adjacent query windows must deduplicate by
occurrence identity and account for spanning events, rather than merely
concatenating start-time lists.

## Verification and limits of this research

- Read the current repository's README, development guide, public API sketch
  and sample test before making these recommendations.
- Downloaded source into `/tmp` and inspected pinned revisions listed below.
- Ran all 21 ICAL.js `.ics`/JSON parser pairs through parse and semantic
  round-trip assertions: **passed**. This used a small Node script calling
  library APIs, not the complete upstream Mocha/browser suite.
- Two ICAL.js public-API probes preserved quoted CN and correctly substituted
  the moved occurrence: **passed for those fixtures only**.
- Three Dart public-API probes reproduced the limitations reported above.
  Existing Dart tests: **892 pass / 1 fail in Eastern; 893 pass in UTC**.
- Repository baseline `./mvnw verify` with Java 27: **BUILD SUCCESS**, one
  active oracle test passed and one unimplemented-reader comparison skipped.
  An earlier attempt stopped before Maven because a presumed SDKMAN path did
  not exist; the successful run used the available `/tmp` JDK.
- Inspected fuzz harnesses/workflows and corpus structures; did **not** run
  OSS-Fuzz campaigns, Jazzer, ICALTester, C/C#/Python suites or Swift builds.
  No performance ranking or full RFC-conformance claim follows from these checks.
- Reviewed license declarations for the recommended corpus sources. Copying
  fixtures and implementing a runner remain separate follow-up work.
- Validated the document's local links and 39 upstream source paths against
  the downloaded trees; checked the example JSON, code fences and whitespace.

## Source snapshots

These are inspected development revisions, not a promise that each is the latest
published release. Documentation links without a commit were accessed on the
research date.

| Repository | Inspected commit |
|---|---|
| kewisch/ical.js | `127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e` |
| libical/libical | `38b952750a89293f14866562d5e13dd07edfcf11` |
| ical-org/ical.net | `69bcfced1aad318d058e54c7bc8243df9e44bcc9` |
| collective/icalendar (Python) | `066c4ccddb8b7e917c8c97314859343163a8be56` |
| mangstadt/biweekly | `84d8003858602a978cdb21e711f0c369d14018f8` |
| dmfs/lib-recur | `5ba0c83529d47e8020fa3b6411689841edca6242` |
| stalwartlabs/calcard | `ef896a8753113886bc1996578b158d4066979c13` |
| kozw/firstfloor_calendar | `a15e20955c18ab6371a3089e93c71f517574f689` |
| redryerye/iCalendarParser | `7cd0344720ac725995a83e1128038f2dce0e84dd` |
| swift-calendar/icalendarkit | `389fceafc1132ef284c6215118421bc88f1bfc8c` |
| kubens/RRuleKit | `98f254d81d7ddad1258e798c9f4410f31fc3771a` |

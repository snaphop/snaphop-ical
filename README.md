# snaphop-ical

A small, modern Java library for reading iCalendar ([RFC 5545](https://www.rfc-editor.org/rfc/rfc5545))
text: the `.ics` files and feeds that calendar apps share.

It is meant as a lighter replacement for [ical4j](https://github.com/ical4j/ical4j) when all you
need is to read events and expand repeating ones.

## Status

Early. The project structure and a sketch of the public API exist; reading does not yet.
Expect the API to change before 1.0.

## Why

ical4j works, but:

- Its API is from the 2000s: mutable objects everywhere and many public classes.
- It pulls in dependencies: `java.xml`, commons-lang3, commons-codec, threeten-extra and SLF4J.
- It logs.

snaphop-ical aims for:

- One module, `com.snaphop.ical`, with one package of the same name.
- No runtime dependencies. [JSpecify](https://jspecify.dev) annotations are used at compile time only.
- Immutable, thread-safe results built on `java.time`.
- A small public API.

## Usage (planned)

```java
IcalReader reader = IcalReader.builder()
    .zone(ZoneId.of("America/New_York")) // required
    .mode(IcalReader.Mode.LAX)
    .build();

IcalCalendar calendar = reader.read(text);
// Read once, then ask for any window of days.
for (IcalOccurrence occurrence : calendar.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))) {
  IcalEvent event = occurrence.event();
  System.out.println(occurrence.start() + " " + event.summary());
}
```

The public types:

| Type | What it is |
|------|------------|
| `IcalReader` | Reads text; configured by `IcalReader.builder()` |
| `IcalCalendar` | One `VCALENDAR`: its events, occurrences and warnings |
| `IcalEvent` | One `VEVENT` |
| `IcalRecurrence` | An event's `RRULE` |
| `IcalOccurrence` | One instance of an event in a window of days |
| `IcalTime` | A date, a date-time in a zone, or a floating date-time |
| `IcalParseException` | Text that cannot be read |

## Design principles

- **Builders configure; everything else is immutable.** Builders are the only mutable types.
  Readers, calendars, events and occurrences are immutable and thread-safe.
- **Modes, not booleans.** Strict or lax reading is `IcalReader.Mode`, not a flag.
- **No nulls by surprise.** Packages are `@NullMarked`. A method that may return null ends in
  `OrNull`, such as `IcalEvent.recurrenceOrNull()`.
- **Clear errors.** A message says what was wrong, where (the line number) and quotes the text.
  Error messages are tested as complete golden strings.
- **No logging.** See below.

### No logging

A calendar parser has no business logging. Problems are reported to the caller, who decides
what matters:

- In strict mode, text that breaks RFC 5545 throws an `IcalParseException`.
- In lax mode, what was skipped is listed in `IcalCalendar.warnings()`.

If something ever must be logged, it will use `System.Logger` from the JDK, never SLF4J or
another logging library.

## Building

Requires Java 25 or newer to build; the library targets Java 25.

```
./mvnw verify
```

See [development.md](development.md) for how the project is built, tested and designed.

## License

[Zero-Clause BSD](LICENSE) (`0BSD`): use it for anything, with no conditions, not even keeping the notice.

Test data copied from other projects, such as ICAL.js or libical, keeps its own license. It lives in its own directory under `test/`
with that license and a note of where it came from, and is never part of a published jar.

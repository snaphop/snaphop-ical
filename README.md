# snaphop-ical

A small, modern Java library for iCalendar ([RFC 5545](https://www.rfc-editor.org/rfc/rfc5545)):
the `.ics` files and feeds that calendar apps share. It reads events and works out repeating
ones, works out repeating rules on their own (as a todo app repeats tasks), and writes events
as a calendar others can subscribe to.

It is meant as a lighter replacement for [ical4j](https://github.com/ical4j/ical4j) when that is
all you need.

## Status

Experimental: the first release will be 0.1.0. It reads events and expands
repeating ones well enough to replace ical4j in
[agentic-todo](https://github.com/agentgt/agentic-todo), which reads the
calendar feeds a family subscribes to, and will repeat tasks and publish
them as a feed. Expect the API to change before 1.0.

## Usage

```java
IcalReader reader = IcalReader.builder()
    .zone(ZoneId.of("America/New_York")) // required
    .build();

IcalCalendar calendar = reader.read(text);
// Read once, then ask for any window of days.
for (IcalOccurrence o : calendar.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))) {
  // What a calendar draws, in the reader's zone.
  System.out.println(o.startDay() + " " + (o.allDay() ? "all day" : o.startTimeOrNull()) + " " + o.event().summary());
  // An id that stays the same each time the calendar is read: piano-1@2026-11-02T22:00:00Z
  String id = o.id();
}
// What lax mode left out, and where: Line 8: DTEND is before DTSTART, so DTEND is left out: "DTEND:20261102T080000"
for (IcalWarning w : calendar.warnings()) System.out.println(w.message());
```

A warning is a record of its line, problem, consequence and where it is. To handle each as it
is found and keep none, pass a consumer: `reader.read(text, w -> System.out.println(w.message()))`.

A rule on its own, as a todo app repeats a task:

```java
var rule = IcalRecurrence.parse("FREQ=WEEKLY;BYDAY=TU,FR"); // or IcalRecurrence.builder(Frequency.WEEKLY)...
var start = new IcalTime.Date(LocalDate.of(2026, 11, 3));
rule.starts(start).limit(5).toList();                       // the next five: a lazy stream
rule.nextOrNull(start, new IcalTime.Date(LocalDate.now())); // when it is due next
rule.describeOrNull(start);                                 // "Every week on Tuesday and Friday"
```

Writing a calendar:

```java
String text = IcalWriter.builder()
    .prodId("-//SnapHop//Agentic Todo//EN")
    .name("Gent family")
    .refreshInterval(Duration.ofHours(1))
    .build()
    .write(List.of(IcalEvent.builder("trash@todo.example.com", start)
        .summary("Take out the trash")
        .recurrence(rule)
        .build()));
```

The public types:

| Type | What it is |
|------|------------|
| `IcalReader` | Reads text; configured by `IcalReader.builder()`: a zone, a `Mode`, whether to include cancelled times, how often an event may repeat |
| `IcalCalendar` | One `VCALENDAR`: its name, events, occurrences and warnings |
| `IcalEvent` | One `VEVENT`, read or made with `IcalEvent.builder(uid, start)` |
| `IcalRecurrence` | An `RRULE`, read with `parse` or made with `builder`: its parts, its starts from a first start, the next after a time, and the rule in words |
| `IcalOccurrence` | One instance of an event in a window of days, with a lasting id and its days and times in the reader's zone |
| `IcalTime` | A date, a date-time in a zone, or a floating date-time |
| `IcalParseException` | Text that cannot be read |
| `IcalWarning` | Text that lax mode read in its own way: the line, the problem and what was done |
| `IcalWriter` | Writes events as a calendar; configured by `IcalWriter.builder()`: a product id, a name, a refresh interval, a stamp |

## What it reads

- **Content lines** (RFC 5545 section 3.1): folded lines, quoted parameters,
  RFC 6868 escapes, line endings of CRLF, LF or CR, a byte order mark.
- **Events**: `UID`, `SUMMARY`, `DESCRIPTION`, `LOCATION`, `STATUS`, `DTSTART`,
  `DTEND` or `DURATION`, `RRULE` (every part), `RDATE` (periods too), `EXDATE`,
  `EXRULE`, and `RECURRENCE-ID` events that change one instance: moved,
  changed or cancelled, into or out of the window.
- **Time zones**, never from the network: a zone name the JDK knows (after a
  prefix such as `/mozilla.org/.../`); a `VTIMEZONE` in the calendar, as the
  JDK zone it agrees with from 2020 to 2037, else by its own rules; a Windows
  zone name, as Outlook and Exchange write them. Times that a change to
  daylight saving time skips or repeats are placed as RFC 5545 says.
- **The calendar's name**: `X-WR-CALNAME`, or `NAME` (RFC 7986).

## What it writes

- **Events**: the same properties, and a `DTSTAMP`. A date-time in a zone has
  its `TZID` and a `VTIMEZONE` made from the JDK's rules, from a year before
  the zone's first use: each change of offset until the zone's yearly rules
  take over, then those as yearly `RRULE`s, each checked to make the JDK's
  changes until 2200. A rule's `UNTIL` is written in the form RFC 5545
  requires for the event's start.
- **The calendar's name** as `NAME` and `X-WR-CALNAME`, and how often to fetch
  it again as `REFRESH-INTERVAL` and `X-PUBLISHED-TTL`.
- **Text** escaped, without control characters, in lines folded at 75 octets of
  UTF-8 between characters, with CRLF.

What it writes it reads back in strict mode as the same events, with the same
occurrences. An `EXRULE` is not written: RFC 5545 took it out of iCalendar.

Not yet:

- Writing anything but events, or properties beyond those above.
- `VTODO`, `VJOURNAL`, `VFREEBUSY` and `VALARM`, which are skipped, and
  properties events have beyond those above (`ATTENDEE`, `ORGANIZER` and so on).
- `RECURRENCE-ID;RANGE=THISANDFUTURE`, read as changing one instance, with a
  warning; a rule with `RSCALE` other than `GREGORIAN` (RFC 7529), whose event is
  left out with a warning.

### Limits on work

A calendar is often someone else's text. An event that repeats more often than
`mostFrequent` (hourly by default) is refused, counting what its parts make it:
`FREQ=HOURLY;BYMINUTE=0,30` is every half hour. Working out one rule for one
window stops after 5 million steps (a daily rule takes a step a day); rules with
no `COUNT`, and those with a `COUNT` and no `BYxxx` parts, skip ahead to the window.
A rule that makes no start in 400 years (times its interval), the Gregorian calendar's cycle,
never will, so it is looked at no further: `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30` ends there.
How much text to read is the caller's to limit.

## How it is tested

End to end, through the public reader, in the `test` module:

- **Specifications from other projects**, in [`test/corpus`](test/corpus):
  libical's 157 recurrence records and the 136 cases of ICAL.js's
  recurrence iterator tests, each read as the event it describes. All
  but two agree; those two are listed in the test with why.
- **Oracles**: each sample is also read by [ical4j](https://github.com/ical4j/ical4j)
  and by [ICAL.js](https://github.com/kewisch/ical.js) (run on GraalJS), and
  their events and occurrences compared with ours. Where one differs, the
  test says why; each time so far it is the oracle that departs from RFC 5545
  or fails to read the text.
- **Samples** of feeds as Apple, Google, Exchange and a sports app write them,
  with daylight saving changes, moved times and the window's edges, each
  compared in full with what a calendar should show.
- **Problems** in full: the message strict mode throws and the warnings lax
  mode gives, for each kind of broken text.
- **Rules alone**: each recurrence record again through `IcalRecurrence.starts`
  and `nextOrNull`; rules in words, made from parts, and wrong, in full.
- **Writing**: a family's calendar compared in full, then read back in strict
  mode, by ical4j and by ICAL.js; each JDK zone's `VTIMEZONE` read back under
  another name, noon each day from 2025 to 2030 checked against the JDK; and
  every calendar above read, written and read again, with the same events and
  occurrences.

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
- In lax mode, what was skipped is an `IcalWarning`, kept in `IcalCalendar.warnings()` or
  handed to a consumer given to `read`.

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

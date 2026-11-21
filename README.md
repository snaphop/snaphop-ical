# snaphop-ical

Small, modern Java libraries for the calendars and contacts that apps share. Each has no
runtime dependencies.

| Library | Module | What it is |
|---------|--------|------------|
| `snaphop-ical` | `com.snaphop.ical` | iCalendar ([RFC 5545](https://www.rfc-editor.org/rfc/rfc5545)): reads events and works out repeating ones, repeats rules on their own, writes calendars. A lighter replacement for [ical4j](https://github.com/ical4j/ical4j) |
| `snaphop-vcard` | `com.snaphop.vcard` | vCard (2.1, 3.0 and 4.0): reads the contacts phones and apps export. A lighter, faster and safer replacement for [ez-vcard](https://github.com/mangstadt/ez-vcard) when reading is all you need. See [vCard](#vcard-snaphop-vcard) |

The rest of this page is iCalendar until [vCard](#vcard-snaphop-vcard).

## iCalendar: snaphop-ical

The `.ics` files and feeds that calendar apps share. It reads events and works out repeating
ones, works out repeating rules on their own (as a todo app repeats tasks), and writes events
as a calendar others can subscribe to.

### Status

Experimental, released since 0.1.0. It reads events and expands
repeating ones well enough to replace ical4j in
[agentic-todo](https://github.com/agentgt/agentic-todo), which reads the
calendar feeds a family subscribes to, and will repeat tasks and publish
them as a feed. Expect the API to change before 1.0.

### Usage

Releases are published to SnapHop's Maven repository:

```xml
<repositories>
  <repository>
    <id>snaphop-public-release</id>
    <url>https://maven.snaphop.com/release</url>
    <snapshots><enabled>false</enabled></snapshots>
  </repository>
</repositories>

<dependency>
  <groupId>com.snaphop</groupId>
  <artifactId>snaphop-ical</artifactId>
  <version>0.2.0</version>
</dependency>
```

It has no runtime dependencies. Its module is `com.snaphop.ical`.

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

### What it reads

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

### What it writes

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

#### Limits on work

A calendar is often someone else's text. An event that repeats more often than
`mostFrequent` (hourly by default) is refused, counting what its parts make it:
`FREQ=HOURLY;BYMINUTE=0,30` is every half hour. Working out one rule for one
window stops after 5 million steps (a daily rule takes a step a day); rules with
no `COUNT`, and those with a `COUNT` and no `BYxxx` parts, skip ahead to the window.
A rule that makes no start in 400 years (times its interval), the Gregorian calendar's cycle,
never will, so it is looked at no further: `FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30` ends there.
How much text to read is the caller's to limit.

### How it is tested

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

### Design principles

- **Builders configure; everything else is immutable.** Builders are the only mutable types.
  Readers, calendars, events and occurrences are immutable and thread-safe.
- **Modes, not booleans.** Strict or lax reading is `IcalReader.Mode`, not a flag.
- **No nulls by surprise.** Packages are `@NullMarked`. A method that may return null ends in
  `OrNull`, such as `IcalEvent.recurrenceOrNull()`.
- **Clear errors.** A message says what was wrong, where (the line number) and quotes the text.
  Error messages are tested as complete golden strings.
- **No logging.** See below.

#### No logging

A calendar parser has no business logging. Problems are reported to the caller, who decides
what matters:

- In strict mode, text that breaks RFC 5545 throws an `IcalParseException`.
- In lax mode, what was skipped is an `IcalWarning`, kept in `IcalCalendar.warnings()` or
  handed to a consumer given to `read`.

If something ever must be logged, it will use `System.Logger` from the JDK, never SLF4J or
another logging library.

## vCard: snaphop-vcard

Reads the contacts that phones, computers and web apps export (`.vcf` files): vCard 2.1, 3.0
and 4.0, into plain records. It was written to replace ez-vcard in
[agentic-todo](https://github.com/agentgt/agentic-todo), where a parent adds the family's
contacts from a file or the phone, and reads only: it does not write vCards yet.

```xml
<dependency>
  <groupId>com.snaphop</groupId>
  <artifactId>snaphop-vcard</artifactId>
  <version>0.2.0</version>
</dependency>
```

It has no runtime dependencies. Its module is `com.snaphop.vcard`.

```java
VcardReader reader = VcardReader.builder().build(); // lax; or .mode(VcardReader.Mode.STRICT)

VcardBook book = reader.read(Files.readAllBytes(path)); // UTF-8, or UTF-16 with its mark
for (Vcard card : book.cards()) {
  card.formattedName();   // "Maya Lucía Rivera"
  card.name().given();    // "Maya"
  card.birthdayOrNull();  // --05-03: a day, with its year when the card has one
  for (Vcard.Value phone : card.phones()) {
    phone.value();        // "555-010-0102"
    phone.types();        // [cell, voice]
    phone.label();        // "Grandma's": what someone typed on an iPhone
  }
}
for (VcardWarning w : book.warnings()) {
  w.message();            // Line 7: BDAY is not a date, so it is left out: "BDAY:2014-02-30"
  w.messageWithoutText(); // Line 7: BDAY is not a date, so it is left out
}
```

The public types:

| Type | What it is |
|------|------------|
| `VcardReader` | Reads text, a `Reader` or a file's bytes; configured by `VcardReader.builder()`: a `Mode` |
| `VcardBook` | What was read: the cards, and the warnings |
| `Vcard` | One contact: its names, emails, phones, addresses, birthday, anniversary, related people, notes, groups, kind and members, and every other property as written. `Vcard.Value`, `Vcard.Address`, `Vcard.Name` and `Vcard.Day` hold them |
| `VcardParseException` | Text that cannot be read |
| `VcardWarning` | Text that lax mode read in its own way: the line, the problem and what was done |

### What it reads

- **Content lines**: folded lines (by a space or a tab), groups (`item1.TEL`), quoted
  parameters, RFC 6868's `^` escapes in vCard 4.0, line endings of CRLF, LF or CR (and an
  iPhone's CR CR LF), a byte order mark.
- **vCard 2.1** as older Android phones, Outlook and BlackBerry write it: bare parameters
  (`TEL;CELL;PREF`), quoted-printable values with soft line breaks in their `CHARSET`, base64
  values ended by a blank line, and agents (a card inside a card).
- **Bytes**: UTF-8; UTF-16 after its byte order mark, as Windows tools write it; and, in lax
  mode, Windows-1252 when the bytes are not UTF-8, as older Outlook wrote them.
- **Values**: `FN`, `N`, `NICKNAME`, `ORG`, `TITLE`, `BDAY`, `ANNIVERSARY`, `EMAIL`, `TEL`
  (a `tel:` URI as its number), `ADR`, `URL`, `RELATED`, `NOTE`, `CATEGORIES`, `KIND`, `MEMBER`
  and `UID`, each with its types, whether it is preferred, and its label.
- **Apple's extensions**, which every iPhone and Mac export has: a label typed for a value
  (`item1.X-ABLabel:Grandma's`, or Apple's own `_$!<Mother>!$_` as `mother`), a birthday
  without its year (`BDAY;X-APPLE-OMIT-YEAR=1604:1604-05-03` is `--05-03`), related names
  (`X-ABRELATEDNAMES`), anniversaries (`X-ABDATE`), groups and their members
  (`X-ADDRESSBOOKSERVER-KIND`, `X-ADDRESSBOOKSERVER-MEMBER`), companies (`X-ABShowAs`), and
  `X-ABUID`.
- **Others' extensions**: the anniversary, spouse, manager and assistant as Outlook
  (`X-MS-*`), Evolution (`X-EVOLUTION-*`) and Thunderbird (`X-ANNIVERSARY`, `X-SPOUSE`) write them.

Everything else a card has is in `Vcard.properties()`, as written. Photos, logos, sounds and
keys are passed over without being kept or decoded, however large.

### Safe with someone's contacts

A contact is personal: a phone number, a home address, a note about a child. The reader is
built so that none of it leaks by accident.

- **It never logs**, as snaphop-ical doesn't.
- **A warning keeps the text apart.** `problem()` and `consequence()` are the reader's own words
  and property names, never the text; only `where()` quotes it. Log `messageWithoutText()`,
  and show `message()` only to whoever gave the file. `VcardParseException.problem()` is the same.
  The fuzz tests check every warning against the full list of the reader's words.
- **`toString()` shows no text**: a card is `Vcard[3.0, line 1]`.
- **Linear work and memory.** No value is read twice; nothing grows faster than the text.
  The tests read text written to hurt a reader: a 64 MB photo, a note folded a million times,
  a million quoted-printable soft breaks, 100,000 cards, 100,000 nested agents, 100,000
  parameters on one line, 10 MB of junk on one line. Each takes well under a second, and the
  test fails at three. How much text to read is the caller's to limit.
- **Only ASCII digits are numbers**, in dates and in quoted-printable's hex, not other scripts'.

### Against ez-vcard

What agentic-todo found wrong with ez-vcard 0.12.2, and what this reader does instead:

| ez-vcard | snaphop-vcard |
|----------|---------------|
| 2.4 MB as it runs: 287 classes, its own parser, and FreeMarker, which loading any card needs | 49 KB, 29 classes, nothing else |
| A file starting with a byte order mark reads as no cards, with no warning | Reads it |
| The platform's charset unless given a reader | UTF-8, UTF-16 with its mark, or Windows-1252 with a warning |
| Decodes every photo into memory | Passes over photos without keeping them |
| A day that does not exist (`2014-02-30`) vanishes, or is kept, with no warning | Reported, then left out |
| Warnings quote the whole line in one string | The text apart from the problem |
| Nulls from most getters, unannotated; mutable beans | `@NullMarked`, `OrNull` where null is possible, immutable records |
| Java 8, no `module-info` | Java 25, a named module |
| Apple's labels, years left out, related names and groups are left to the caller | Read as what they mean |

Measured by `Benchmark` (see [Building](#building)), on JDK 27 on a 20-core ARM machine:

| | snaphop-vcard | ez-vcard |
|---|---:|---:|
| 10,000 cards from the samples (24 MB): time | 96 ms | 699 ms |
| Same: memory allocated, and kept by the result | 250 MB, 74 MB | 469 MB, 90 MB |
| 500 iPhone cards with their photos (23 MB): time | 47 ms | 575 ms |
| Same: memory allocated, and kept | 20 MB, 5 MB | 121 MB, 22 MB |
| One card with a 64 MB photo | 119 ms | 1,686 ms |
| 44,800 broken texts (the fuzz tests): exceptions other than the reader's own | 0 | 1 (`DateTimeException`) |

### How it is tested

End to end, through the public reader, in `test/snaphop-vcard-test`: more than 7,700 tests.

- **Oracles on real exports.** Every sample is read by this reader, by
  [ez-vcard](https://github.com/mangstadt/ez-vcard) and by ICAL.js (which reads vCard 3.0 and
  4.0), and compared field by field: 28 files × 2 oracles × 18 fields. The files are ez-vcard's
  own exports from an iPhone, a Mac, Android, BlackBerry, Gmail, Outlook 2003 and 2007, Lotus
  Notes, Evolution and Thunderbird ([`test/corpus/ez-vcard`](test/corpus/ez-vcard)), ICAL.js's
  vCard tests, and samples written here for Apple, Google, an old Android phone, Outlook in
  Windows-1252, and vCard 4.0. Where an oracle differs, the test says why, and checks it still
  does; each time, it is an extension the oracle leaves raw, or the oracle misreading the text.
- **Generated cards**: every version × property × tricky value (commas, semicolons,
  backslashes, newlines, accents, emoji, CJK, quotes, `=`, carets, long lines) × way of writing
  types × folding, 1,815 cards. This reader, in strict mode, must read each as written; each
  oracle is checked against it, with its differences listed by rule.
- **Dates**: every form apps write a birthday or anniversary in (dates, date-times, no year,
  Apple's, partial dates, text, days that do not exist, another script's digits), in each
  version, against both oracles.
- **Transforms**: every sample written 18 other ways that mean the same (LF, CR, mixed line
  endings, folded at every character or every 3, 10 or 75 octets, with a tab, a byte order
  mark, names in lower or upper case, blank lines, UTF-8 and UTF-16 bytes, a reader that gives
  one character at a time, twice in one file) must read the same.
- **Problems** in full: the message strict mode throws, its line and problem, and lax mode's
  warnings and what it still reads, for each kind of broken text.
- **Hostile text and fuzzing**: the hostile texts above, and every sample broken 200 times in
  each of 8 ways, read in both modes, as text and as bytes.

Mutation testing is not used: the oracles and generated cases cover the parser's parts, and
none is risky enough to need it yet.

## Building

Requires Java 25 or newer to build; the library targets Java 25.

```
./mvnw verify
```

The comparison with ez-vcard above is a benchmark, not a test: it runs only when asked, and
writes `test/snaphop-vcard-test/target/benchmark.md`.

```
./mvnw -pl test/snaphop-vcard-test -am package -Dtest=Benchmark -Dbenchmark=true -Dsurefire.failIfNoSpecifiedTests=false
```

See [development.md](development.md) for how the project is built, tested and designed.

## License

[Zero-Clause BSD](LICENSE) (`0BSD`): use it for anything, with no conditions, not even keeping the notice.

Test data copied from other projects, such as ICAL.js, libical or ez-vcard, keeps its own license. It lives in its own directory under `test/`
with that license and a note of where it came from, and is never part of a published jar.

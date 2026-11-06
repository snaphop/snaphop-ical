package com.snaphop.ical;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Writes events as iCalendar (RFC 5545) text: a calendar that Google, Apple,
/// Outlook and Fastmail can subscribe to.
///
/// ```java
/// IcalWriter writer = IcalWriter.builder()
///     .prodId("-//SnapHop//Agentic Todo//EN")
///     .name("Gent family")
///     .refreshInterval(Duration.ofHours(1))
///     .build();
/// String text = writer.write(List.of(
///     IcalEvent.builder("trash@todo.example.com", new IcalTime.Date(LocalDate.of(2026, 11, 3)))
///         .summary("Take out the trash")
///         .recurrence(IcalRecurrence.parse("FREQ=WEEKLY;BYDAY=TU,FR"))
///         .build()));
/// ```
///
/// What it writes, [IcalReader] reads back in [IcalReader.Mode#STRICT] as
/// the same events, with the same occurrences:
///
/// - A date-time in a zone is written with its `TZID` and a `VTIMEZONE` for
///   it, made from the JDK's rules from a year before the zone's first use.
///   One in UTC is written with a `Z`.
/// - An end is written as `DTEND`, or as `DURATION` when the event had one
///   of whole days, which last a day on the wall clock across a change to
///   daylight saving time.
/// - Text is escaped, and lines are folded at 75 octets of UTF-8 without
///   splitting a character. Control characters other than a tab and a line
///   break, which RFC 5545 doesn't allow in text, are left out.
///
/// An `EXRULE`, which RFC 5545 took out of iCalendar, is not written.
///
/// A writer is immutable and thread-safe.
public sealed interface IcalWriter permits DefaultIcalWriter {

  /// Starts configuring a writer.
  ///
  /// @return a new builder with the defaults described on its methods
  static Builder builder() {
    return new Builder();
  }

  /// Writes a calendar of `events`.
  ///
  /// @param events the events, in the order to write them: events from an
  ///        [IcalCalendar], or made with [IcalEvent#builder(String, IcalTime)]
  /// @return the calendar text, with CRLF line endings
  String write(List<? extends IcalEvent> events);

  /// Writes a calendar of `events` to `out`.
  ///
  /// @param events the events, in the order to write them
  /// @param out where the text goes
  /// @throws IOException if `out` fails
  void write(List<? extends IcalEvent> events, Appendable out) throws IOException;

  /// Configures an [IcalWriter].
  final class Builder {

    private String prodId = "-//SnapHop//snaphop-ical//EN";
    private @Nullable String name;
    private @Nullable Duration refreshInterval;
    private @Nullable Instant stamp;

    Builder() {}

    /// Sets the `PRODID`, which names what wrote the calendar.
    ///
    /// @param prodId such as `-//SnapHop//Agentic Todo//EN`; defaults to
    ///        `-//SnapHop//snaphop-ical//EN`
    /// @return this
    public Builder prodId(String prodId) {
      this.prodId = Objects.requireNonNull(prodId, "prodId");
      return this;
    }

    /// Sets the calendar's name, which calendar apps show for a calendar
    /// subscribed to: written as `NAME` (RFC 7986) and as `X-WR-CALNAME`, as
    /// Apple and Google write it.
    ///
    /// @param name the name; by default the calendar has none
    /// @return this
    public Builder name(String name) {
      this.name = Objects.requireNonNull(name, "name");
      return this;
    }

    /// Sets how often a subscriber should fetch the calendar again: written
    /// as `REFRESH-INTERVAL` (RFC 7986) and as `X-PUBLISHED-TTL`, as Outlook
    /// reads it. Apps treat it as a suggestion.
    ///
    /// @param interval how long between fetches, a minute or more; by
    ///        default none is written
    /// @return this
    public Builder refreshInterval(Duration interval) {
      this.refreshInterval = Objects.requireNonNull(interval, "interval");
      return this;
    }

    /// Sets the `DTSTAMP` of every event: when the calendar was made.
    ///
    /// A calendar written twice from the same events is the same text only
    /// if they have the same stamp, so a writer of a feed with an `ETag`
    /// should set it, say to when the events last changed.
    ///
    /// @param stamp the time; by default, the time each calendar is written
    /// @return this
    public Builder stamp(Instant stamp) {
      this.stamp = Objects.requireNonNull(stamp, "stamp");
      return this;
    }

    /// Makes the writer.
    ///
    /// @return the writer
    /// @throws IllegalArgumentException if the refresh interval is shorter than
    ///         a minute, or the product id or name has a line break
    public IcalWriter build() {
      var interval = refreshInterval;
      if (interval != null && interval.compareTo(Duration.ofMinutes(1)) < 0) {
        throw new IllegalArgumentException("A refresh interval must be a minute or more: " + interval);
      }
      Writing.oneLine("A PRODID", prodId);
      if (name != null) Writing.oneLine("A calendar name", name);
      return new DefaultIcalWriter(prodId, name, interval, stamp);
    }
  }
}

record DefaultIcalWriter(String prodId, @Nullable String name, @Nullable Duration refreshInterval, @Nullable Instant stamp)
    implements IcalWriter {

  @Override
  public String write(List<? extends IcalEvent> events) {
    var out = new StringBuilder();
    Writing.write(this, events, out);
    return out.toString();
  }

  @Override
  public void write(List<? extends IcalEvent> events, Appendable out) throws IOException {
    out.append(write(events));
  }
}

package com.snaphop.ical;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/// Reads iCalendar (RFC 5545) text into an immutable [IcalCalendar].
///
/// A reader is immutable and thread-safe. Make one with [#builder()] and reuse it.
///
/// ```java
/// IcalReader reader = IcalReader.builder()
///     .zone(ZoneId.of("America/New_York"))
///     .build();
/// IcalCalendar calendar = reader.read(text);
/// List<IcalOccurrence> november = calendar.occurrences(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1));
/// ```
///
/// A calendar is read once and then asked for any window of days.
///
/// The reader never logs. Text it cannot read is reported according to its [Mode]:
/// as an [IcalParseException], or as an [IcalWarning], kept in
/// [IcalCalendar#warnings()] or handed to a consumer.
public sealed interface IcalReader {

  /// Reads a calendar from text, keeping its warnings in [IcalCalendar#warnings()].
  ///
  /// @param text the whole iCalendar text, starting with `BEGIN:VCALENDAR`
  /// @return the calendar read
  /// @throws IcalParseException if the text cannot be read in the reader's [Mode]
  IcalCalendar read(CharSequence text);

  /// Reads a calendar from text, handing each warning to `warnings` as it is
  /// found, in place of keeping them.
  ///
  /// Nothing holds a warning once `warnings` has it, so one that is counted
  /// or logged and dropped need not last as long as the calendar. The
  /// calendar's [IcalCalendar#warnings()] is empty.
  ///
  /// ```java
  /// var count = new int[1];
  /// var calendar = reader.read(text, w -> count[0]++);
  /// ```
  ///
  /// @param text the whole iCalendar text, starting with `BEGIN:VCALENDAR`
  /// @param warnings called with each warning, during this call, on this thread;
  ///        never called in [Mode#STRICT]
  /// @return the calendar read
  /// @throws IcalParseException if the text cannot be read in the reader's [Mode]
  IcalCalendar read(CharSequence text, Consumer<? super IcalWarning> warnings);

  /// Reads a calendar from a character stream, until its end, keeping its
  /// warnings in [IcalCalendar#warnings()].
  ///
  /// The caller closes `reader`.
  ///
  /// @param reader the iCalendar text, starting with `BEGIN:VCALENDAR`
  /// @return the calendar read
  /// @throws IOException if `reader` fails
  /// @throws IcalParseException if the text cannot be read in the reader's [Mode]
  IcalCalendar read(Reader reader) throws IOException;

  /// Starts configuring a reader.
  ///
  /// @return a new builder with the defaults described on its methods
  static Builder builder() {
    return new Builder();
  }

  /// How strictly the reader follows RFC 5545.
  enum Mode {
    /// Anything that breaks RFC 5545 throws an [IcalParseException].
    STRICT,
    /// What cannot be read is skipped and described in an [IcalWarning].
    /// Calendars from real clients often break the RFC in small ways, so this
    /// is the default. An [IcalParseException] is still thrown when the text is
    /// not a calendar at all.
    LAX
  }

  /// Whether [IcalCalendar#occurrences(LocalDate, LocalDate)] gives
  /// cancelled events and times.
  enum Cancelled {
    /// Left out: a cancelled event, or a cancelled time of a repeating event
    /// (`STATUS:CANCELLED` on its `RECURRENCE-ID` event), is not given. A
    /// calendar shows them this way, so this is the default.
    OMIT,
    /// Given like the others; check [IcalEvent#cancelled()].
    INCLUDE
  }

  /// Configures an [IcalReader].
  ///
  /// Like all builders, it is mutable and not thread-safe.
  final class Builder {

    private @Nullable ZoneId zone;
    private Mode mode = Mode.LAX;
    private Cancelled cancelled = Cancelled.OMIT;
    private IcalRecurrence.Frequency mostFrequent = IcalRecurrence.Frequency.HOURLY;

    private Builder() {}

    /// Sets the zone the calendar is read for. It is required: there is no
    /// default, because a wrong zone silently moves every time.
    ///
    /// The zone is used in two places:
    ///
    /// - the days of a window passed to [IcalCalendar#occurrences(LocalDate, LocalDate)];
    /// - [IcalTime.Floating] times, which have no zone of their own, when they
    ///   are ordered among other times or placed in a window. They stay floating
    ///   in what is read.
    ///
    /// @param zone the zone, usually where the calendar's reader lives
    /// @return this builder
    public Builder zone(ZoneId zone) {
      this.zone = Objects.requireNonNull(zone, "zone");
      return this;
    }

    /// Sets how strictly the reader follows RFC 5545. The default is [Mode#LAX].
    ///
    /// @param mode the mode
    /// @return this builder
    public Builder mode(Mode mode) {
      this.mode = Objects.requireNonNull(mode, "mode");
      return this;
    }

    /// Sets whether occurrences include cancelled events and times. The
    /// default is [Cancelled#OMIT].
    ///
    /// @param cancelled whether to leave them out
    /// @return this builder
    public Builder cancelled(Cancelled cancelled) {
      this.cancelled = Objects.requireNonNull(cancelled, "cancelled");
      return this;
    }

    /// Sets how often an event may repeat. The default is
    /// [IcalRecurrence.Frequency#HOURLY].
    ///
    /// Calendar text often comes from elsewhere, and a rule such as
    /// `FREQ=MINUTELY` makes half a million occurrences in a year. An event
    /// that repeats more often is an error in [Mode#STRICT], and is left out
    /// with a warning in [Mode#LAX]. A rule counts as repeating as often as
    /// its parts make it: `FREQ=HOURLY;BYMINUTE=0,30` is every half hour,
    /// so [IcalRecurrence.Frequency#MINUTELY].
    ///
    /// @param frequency the most frequent repeating allowed
    /// @return this builder
    public Builder mostFrequent(IcalRecurrence.Frequency frequency) {
      this.mostFrequent = Objects.requireNonNull(frequency, "frequency");
      return this;
    }

    /// Makes a reader from this configuration.
    ///
    /// @return an immutable, thread-safe reader
    /// @throws IllegalStateException if no zone was set
    public IcalReader build() {
      var z = zone;
      if (z == null) {
        throw new IllegalStateException(
            "An IcalReader needs a zone: call zone(ZoneId) with the zone the calendar is read for.");
      }
      return new DefaultIcalReader(z, mode, cancelled, mostFrequent);
    }
  }
}

record DefaultIcalReader(ZoneId zone, IcalReader.Mode mode, IcalReader.Cancelled cancelled, IcalRecurrence.Frequency mostFrequent)
    implements IcalReader {

  @Override
  public IcalCalendar read(CharSequence text) {
    return Reading.read(text, this);
  }

  @Override
  public IcalCalendar read(CharSequence text, Consumer<? super IcalWarning> warnings) {
    return Reading.read(text, this, Objects.requireNonNull(warnings, "warnings"));
  }

  @Override
  public IcalCalendar read(Reader reader) throws IOException {
    var text = new StringWriter();
    reader.transferTo(text);
    return read(text.toString());
  }
}

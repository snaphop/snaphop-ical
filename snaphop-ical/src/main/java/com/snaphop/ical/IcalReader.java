package com.snaphop.ical;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// Reads iCalendar (RFC 5545) text into an immutable [IcalCalendar].
///
/// A reader is immutable and thread-safe. Make one with [#builder()] and reuse it.
///
/// ```java
/// IcalReader reader = IcalReader.builder()
///     .zone(ZoneId.of("America/New_York"))
///     .window(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1))
///     .build();
/// IcalCalendar calendar = reader.read(text);
/// ```
///
/// The reader never logs. Text it cannot read is reported according to its [Mode]:
/// as an [IcalParseException], or as a warning in [IcalCalendar#warnings()].
public sealed interface IcalReader {

  /// Reads a calendar from text.
  ///
  /// @param text the whole iCalendar text, starting with `BEGIN:VCALENDAR`
  /// @return the calendar read
  /// @throws IcalParseException if the text cannot be read in the reader's [Mode]
  IcalCalendar read(CharSequence text);

  /// Reads a calendar from a character stream, until its end.
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
    /// What cannot be read is skipped and described in [IcalCalendar#warnings()].
    /// Calendars from real clients often break the RFC in small ways, so this
    /// is the default. An [IcalParseException] is still thrown when the text is
    /// not a calendar at all.
    LAX
  }

  /// Configures an [IcalReader].
  ///
  /// Like all builders, it is mutable and not thread-safe.
  final class Builder {

    private ZoneId zone = ZoneOffset.UTC;
    private @Nullable LocalDate windowStart;
    private @Nullable LocalDate windowEnd;
    private Mode mode = Mode.LAX;

    private Builder() {}

    /// Sets the zone for floating times and for the window.
    ///
    /// A floating time is a date-time written without a zone, meaning the same
    /// wall clock time wherever the reader is. The default is UTC.
    ///
    /// @param zone the zone that floating times and the window are in
    /// @return this builder
    public Builder zone(ZoneId zone) {
      this.zone = Objects.requireNonNull(zone, "zone");
      return this;
    }

    /// Sets the dates that repeating events are expanded within.
    ///
    /// An occurrence is in the window when any part of it falls on or after
    /// `start` and before `end`, in the reader's zone. Without a window,
    /// [IcalCalendar#occurrences()] is empty.
    ///
    /// @param start the first day of the window
    /// @param end the day after the last day of the window
    /// @return this builder
    /// @throws IllegalArgumentException if `end` is not after `start`
    public Builder window(LocalDate start, LocalDate end) {
      Objects.requireNonNull(start, "start");
      Objects.requireNonNull(end, "end");
      if (!end.isAfter(start)) {
        throw new IllegalArgumentException(
            "The window end must be after its start. start: " + start + ", end: " + end);
      }
      this.windowStart = start;
      this.windowEnd = end;
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

    /// Makes a reader from this configuration.
    ///
    /// @return an immutable, thread-safe reader
    public IcalReader build() {
      return new DefaultIcalReader(zone, windowStart, windowEnd, mode);
    }
  }
}

record DefaultIcalReader(
    ZoneId zone,
    @Nullable LocalDate windowStart,
    @Nullable LocalDate windowEnd,
    IcalReader.Mode mode)
    implements IcalReader {

  @Override
  public IcalCalendar read(CharSequence text) {
    throw new UnsupportedOperationException("Reading is not implemented yet.");
  }

  @Override
  public IcalCalendar read(Reader reader) throws IOException {
    var text = new StringWriter();
    reader.transferTo(text);
    return read(text.toString());
  }
}

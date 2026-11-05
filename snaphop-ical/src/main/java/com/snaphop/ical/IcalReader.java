package com.snaphop.ical;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
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

    private @Nullable ZoneId zone;
    private Mode mode = Mode.LAX;

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
      return new DefaultIcalReader(z, mode);
    }
  }
}

record DefaultIcalReader(ZoneId zone, IcalReader.Mode mode) implements IcalReader {

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

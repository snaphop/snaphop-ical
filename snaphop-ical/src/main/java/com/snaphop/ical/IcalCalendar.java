package com.snaphop.ical;

import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// A calendar read by an [IcalReader]: one `VCALENDAR`.
///
/// Immutable and thread-safe.
public sealed interface IcalCalendar {

  /// The calendar's name: its `X-WR-CALNAME`, as Apple and Google write it,
  /// or else its `NAME` (RFC 7986).
  ///
  /// @return the name, or null if the calendar has none
  @Nullable String nameOrNull();

  /// The events of the calendar, in the order they were written.
  ///
  /// An event that changes one instance of a repeating event has a
  /// [IcalEvent#recurrenceIdOrNull()] and is listed here too. Events that
  /// [IcalReader.Mode#LAX] left out are not.
  ///
  /// @return the events, never null
  List<IcalEvent> events();

  /// The occurrences of every event in a window of days, ordered by start.
  ///
  /// The days are in the reader's zone. An occurrence is in the window when
  /// any part of it falls on or after `from` and before `until`.
  ///
  /// Repeating events are expanded by their rules and `RDATE`s, skipping
  /// their exception dates. An instance changed by another event (with its
  /// `UID` and a `RECURRENCE-ID`) is replaced by that event, wherever it
  /// moved it. Cancelled events and instances are left out unless the
  /// reader was built with [IcalReader.Cancelled#INCLUDE].
  ///
  /// A calendar can be asked for any number of windows.
  ///
  /// @param from the first day
  /// @param until the day after the last day
  /// @return the occurrences, never null
  /// @throws IllegalArgumentException if `until` is not after `from`
  List<IcalOccurrence> occurrences(LocalDate from, LocalDate until);

  /// What [IcalReader.Mode#LAX] skipped or read in its own way, one message
  /// each, starting with its line number.
  ///
  /// @return the warnings, always empty in [IcalReader.Mode#STRICT]
  List<String> warnings();
}

record DefaultIcalCalendar(@Nullable String nameOrNull, List<IcalEvent> events, List<String> warnings, DefaultIcalReader reader)
    implements IcalCalendar {

  DefaultIcalCalendar {
    events = List.copyOf(events);
    warnings = List.copyOf(warnings);
  }

  @Override
  public List<IcalOccurrence> occurrences(LocalDate from, LocalDate until) {
    if (!until.isAfter(from)) {
      throw new IllegalArgumentException(
          "A window must end after it starts. from: " + from + ", until: " + until);
    }
    return Occurrences.of(this, from, until);
  }
}

package com.snaphop.ical;

import java.util.List;

/// A calendar read by an [IcalReader]: one `VCALENDAR`.
///
/// Immutable and thread-safe.
public sealed interface IcalCalendar {

  /// The events of the calendar, in the order they were written.
  ///
  /// An event that changes one instance of a repeating event has a
  /// [IcalEvent#recurrenceIdOrNull()] and is listed here too.
  ///
  /// @return the events, never null
  List<IcalEvent> events();

  /// The occurrences of every event within the reader's window, ordered by start.
  ///
  /// Repeating events are expanded by their rule, skipping their exception
  /// dates. An instance changed by another event is replaced by that event.
  /// Cancelled instances are kept; check [IcalEvent#cancelled()].
  ///
  /// @return the occurrences, or an empty list if the reader has no window
  /// @see IcalReader.Builder#window(java.time.LocalDate, java.time.LocalDate)
  List<IcalOccurrence> occurrences();

  /// What [IcalReader.Mode#LAX] skipped, one message each, with line numbers.
  ///
  /// @return the warnings, always empty in [IcalReader.Mode#STRICT]
  List<String> warnings();
}

record DefaultIcalCalendar(
    List<IcalEvent> events, List<IcalOccurrence> occurrences, List<String> warnings)
    implements IcalCalendar {

  DefaultIcalCalendar {
    events = List.copyOf(events);
    occurrences = List.copyOf(occurrences);
    warnings = List.copyOf(warnings);
  }
}

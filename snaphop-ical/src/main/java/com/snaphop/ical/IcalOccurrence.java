package com.snaphop.ical;

/// One instance of an event within the reader's window.
///
/// An event that does not repeat has one occurrence, if it falls in the window.
///
/// Immutable and thread-safe.
public sealed interface IcalOccurrence {

  /// The event this is an instance of.
  ///
  /// If another event changes this instance, it is that event.
  ///
  /// @return the event, never null
  IcalEvent event();

  /// When this instance starts.
  ///
  /// @return the start, never null
  IcalTime start();

  /// When this instance ends, exclusive.
  ///
  /// @return the end, never null
  IcalTime end();

  /// The start the event's rule gave this instance, before any change moved it.
  ///
  /// This identifies the instance, as `RECURRENCE-ID` does. For an event that
  /// does not repeat it is the event's start.
  ///
  /// @return the original start, never null
  IcalTime recurrenceId();
}

record DefaultIcalOccurrence(IcalEvent event, IcalTime start, IcalTime end, IcalTime recurrenceId)
    implements IcalOccurrence {}

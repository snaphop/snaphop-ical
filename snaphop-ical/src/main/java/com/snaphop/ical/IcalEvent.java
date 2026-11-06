package com.snaphop.ical;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// One `VEVENT` of a calendar.
///
/// A repeating event is one event with a rule. Its instances are in
/// [IcalCalendar#occurrences(java.time.LocalDate, java.time.LocalDate)].
///
/// Immutable and thread-safe.
public sealed interface IcalEvent {

  /// Starts making an event, to write with an [IcalWriter].
  ///
  /// ```java
  /// IcalEvent.builder("trash@todo.example.com", new IcalTime.Date(LocalDate.of(2026, 11, 3)))
  ///     .summary("Take out the trash")
  ///     .recurrence(IcalRecurrence.parse("FREQ=WEEKLY;BYDAY=TU,FR"))
  ///     .build();
  /// ```
  ///
  /// @param uid the `UID`, which identifies the event across versions of the
  ///        calendar: unique, and the same each time it is written, such as
  ///        a database id and a domain
  /// @param start the `DTSTART`: a date for an all-day event
  /// @return a builder of an event with nothing else
  static Builder builder(String uid, IcalTime start) {
    return new Builder(uid, start);
  }

  /// The event's `UID`, which identifies it across versions of the calendar.
  ///
  /// @return the uid, never null
  String uid();

  /// The event's `SUMMARY`, its title.
  ///
  /// @return the summary, or an empty string if the event has none
  String summary();

  /// The event's `DESCRIPTION`.
  ///
  /// @return the description, or an empty string if the event has none
  String description();

  /// The event's `LOCATION`.
  ///
  /// @return the location, or an empty string if the event has none
  String location();

  /// When the event starts: its `DTSTART`.
  ///
  /// For a repeating event this is the start of its first instance.
  ///
  /// @return the start, never null
  IcalTime start();

  /// When the event ends, exclusive.
  ///
  /// This is `DTEND` if the event has one. Otherwise it is the start plus the
  /// event's `DURATION`. Without either, RFC 5545 makes an event that starts on
  /// a date last that whole day, and one that starts at a date-time end when it
  /// starts.
  ///
  /// @return the end, never null
  IcalTime end();

  /// Whether the event takes whole days rather than a time of day.
  ///
  /// @return true if [#start()] is an [IcalTime.Date]
  default boolean allDay() {
    return start() instanceof IcalTime.Date;
  }

  /// The event's `STATUS`.
  ///
  /// @return the status, [Status#NONE] if the event has none
  Status status();

  /// Whether the event is cancelled.
  ///
  /// @return true if [#status()] is [Status#CANCELLED]
  default boolean cancelled() {
    return status() == Status.CANCELLED;
  }

  /// How the event repeats: its `RRULE`.
  ///
  /// @return the rule, or null if the event does not repeat by rule
  @Nullable IcalRecurrence recurrenceOrNull();

  /// Instances of a repeating event that do not happen: its `EXDATE` values.
  ///
  /// @return the exception dates, in the order written, never null
  List<IcalTime> exceptionDates();

  /// Which instance of a repeating event this event changes: its `RECURRENCE-ID`.
  ///
  /// The value is that instance's original start. The event with the same
  /// [#uid()] and a rule is the one being changed.
  ///
  /// @return the original start of the changed instance, or null if this event
  ///     does not change an instance
  @Nullable IcalTime recurrenceIdOrNull();

  /// Makes an [IcalEvent].
  final class Builder {

    private final String uid;
    private final IcalTime start;
    private String summary = "";
    private String description = "";
    private String location = "";
    private @Nullable IcalTime end;
    private Status status = Status.NONE;
    private @Nullable IcalRecurrence recurrence;
    private List<IcalTime> exceptionDates = List.of();
    private List<IcalTime> rdates = List.of();
    private @Nullable IcalTime recurrenceId;

    private Builder(String uid, IcalTime start) {
      this.uid = Objects.requireNonNull(uid, "uid");
      this.start = Objects.requireNonNull(start, "start");
    }

    /// Sets the `SUMMARY`, its title.
    ///
    /// @param summary the title, which may have line breaks
    /// @return this
    public Builder summary(String summary) {
      this.summary = Objects.requireNonNull(summary, "summary");
      return this;
    }

    /// Sets the `DESCRIPTION`.
    ///
    /// @param description the notes, which may have line breaks
    /// @return this
    public Builder description(String description) {
      this.description = Objects.requireNonNull(description, "description");
      return this;
    }

    /// Sets the `LOCATION`.
    ///
    /// @param location where it is
    /// @return this
    public Builder location(String location) {
      this.location = Objects.requireNonNull(location, "location");
      return this;
    }

    /// Sets when it ends, exclusive: the `DTEND`.
    ///
    /// @param end the same kind of time as the start, not before it. For an
    ///        all-day event, the day after its last. By default an all-day
    ///        event lasts its one day, and another ends when it starts.
    /// @return this
    public Builder end(IcalTime end) {
      this.end = Objects.requireNonNull(end, "end");
      return this;
    }

    /// Sets the `STATUS`.
    ///
    /// @param status the status; by default [Status#NONE], which writes none
    /// @return this
    public Builder status(Status status) {
      this.status = Objects.requireNonNull(status, "status");
      return this;
    }

    /// Sets how it repeats: its `RRULE`.
    ///
    /// @param recurrence the rule, with the start as its first start
    /// @return this
    public Builder recurrence(IcalRecurrence recurrence) {
      this.recurrence = Objects.requireNonNull(recurrence, "recurrence");
      return this;
    }

    /// Sets instances of a repeating event that do not happen: its
    /// `EXDATE`s, each the start of an instance.
    ///
    /// @param dates the starts left out
    /// @return this
    public Builder exceptionDates(List<IcalTime> dates) {
      this.exceptionDates = List.copyOf(dates);
      return this;
    }

    /// Sets starts it has besides its rule's: its `RDATE`s.
    ///
    /// @param dates the extra starts
    /// @return this
    public Builder rdates(List<IcalTime> dates) {
      this.rdates = List.copyOf(dates);
      return this;
    }

    /// Makes this an event that changes one instance of a repeating event
    /// with the same `UID`: its `RECURRENCE-ID`.
    ///
    /// @param recurrenceId the original start of the instance changed
    /// @return this
    public Builder recurrenceId(IcalTime recurrenceId) {
      this.recurrenceId = Objects.requireNonNull(recurrenceId, "recurrenceId");
      return this;
    }

    /// Makes the event.
    ///
    /// @return the event
    /// @throws IllegalArgumentException if the `UID` is blank or has a line
    ///         break, or the end is another kind of time than the start, or
    ///         before it, saying which
    public IcalEvent build() {
      if (uid.isBlank()) throw new IllegalArgumentException("An event's UID must not be blank");
      Writing.oneLine("A UID", uid);
      var e = end != null ? end : Times.plus(start, start instanceof IcalTime.Date ? Length.ONE_DAY : Length.ZERO, null);
      var length = switch (start) {
        case IcalTime.Date s when e instanceof IcalTime.Date d -> new Length((int) ChronoUnit.DAYS.between(s.date(), d.date()), Duration.ZERO);
        case IcalTime.DateTime s when e instanceof IcalTime.DateTime d -> new Length(0, Duration.between(s.dateTime(), d.dateTime()));
        case IcalTime.Floating s when e instanceof IcalTime.Floating d -> new Length(0, Duration.between(s.dateTime(), d.dateTime()));
        default -> throw new IllegalArgumentException(
            "An event's end is " + Writing.kind(e) + " but its start is " + Writing.kind(start) + ": " + Writing.value(e, false));
      };
      if (length.negative() || (start instanceof IcalTime.Date && length.days() == 0)) {
        throw new IllegalArgumentException("An event's end must be " + (start instanceof IcalTime.Date ? "after" : "at or after")
            + " its start " + Writing.value(start, false) + ", but is " + Writing.value(e, false));
      }
      var rules = recurrence == null ? List.<DefaultIcalRecurrence>of() : List.of((DefaultIcalRecurrence) recurrence);
      return new DefaultIcalEvent(uid, summary, description, location, start, e, status, exceptionDates, recurrenceId, null,
          length, rules, List.of(), rdates.stream().map(t -> new RDate(t, null)).toList(), 0);
    }
  }

  /// The `STATUS` values RFC 5545 allows for an event.
  enum Status {
    /// The event has no `STATUS`.
    NONE,
    /// `TENTATIVE`: the event may not happen.
    TENTATIVE,
    /// `CONFIRMED`: the event will happen.
    CONFIRMED,
    /// `CANCELLED`: the event will not happen.
    CANCELLED
  }
}

/// An `RDATE` value: a start, and an end if it was written as a period.
record RDate(IcalTime start, @Nullable IcalTime end) {}

/// An event, and what working out its occurrences needs: the zone of its
/// start, its length (exact from `DTEND`, nominal from `DURATION`), its rules,
/// and the line of its `BEGIN:VEVENT`.
record DefaultIcalEvent(
    String uid,
    String summary,
    String description,
    String location,
    IcalTime start,
    IcalTime end,
    IcalEvent.Status status,
    List<IcalTime> exceptionDates,
    @Nullable IcalTime recurrenceIdOrNull,
    @Nullable Tz tz,
    Length length,
    List<DefaultIcalRecurrence> rules,
    List<DefaultIcalRecurrence> exceptionRules,
    List<RDate> rdates,
    int line)
    implements IcalEvent {

  DefaultIcalEvent {
    exceptionDates = List.copyOf(exceptionDates);
    rules = List.copyOf(rules);
    exceptionRules = List.copyOf(exceptionRules);
    rdates = List.copyOf(rdates);
  }

  @Override
  public @Nullable IcalRecurrence recurrenceOrNull() {
    return rules.isEmpty() ? null : rules.getFirst();
  }

  /// Whether it has more than one instance by rule or `RDATE`.
  boolean repeats() {
    return !rules.isEmpty() || !rdates.isEmpty();
  }
}

/// Reads iCalendar (RFC 5545) text into immutable records.
///
/// The types, in the order a caller meets them:
///
/// 1. [IcalReader] reads text, configured by its builder.
/// 2. [IcalCalendar] is what was read: events, occurrences and warnings.
/// 3. [IcalEvent] is one `VEVENT`, with its [IcalRecurrence] rule if it repeats.
/// 4. [IcalOccurrence] is one instance of an event in a window of days.
/// 5. [IcalTime] is a date, a date-time in a zone, or a floating date-time, as
///    event times are written in iCalendar.
/// 6. [IcalParseException] reports text that cannot be read, and
///    [IcalWarning] text that lax mode read in its own way.
@org.jspecify.annotations.NullMarked
package com.snaphop.ical;

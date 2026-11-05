/// Reads iCalendar (RFC 5545) text into immutable records.
///
/// The types, in the order a caller meets them:
///
/// 1. [IcalReader] reads text, configured by its builder.
/// 2. [IcalCalendar] is what was read: events, occurrences and warnings.
/// 3. [IcalEvent] is one `VEVENT`, with its [IcalRecurrence] rule if it repeats.
/// 4. [IcalOccurrence] is one instance of an event within the reader's window.
/// 5. [IcalTime] is a date or a date-time, as event times are in iCalendar.
/// 6. [IcalParseException] reports text that cannot be read.
@org.jspecify.annotations.NullMarked
package com.snaphop.ical;

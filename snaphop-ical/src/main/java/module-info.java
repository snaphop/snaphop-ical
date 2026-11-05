/// Reads iCalendar (RFC 5545) text into immutable records.
///
/// Start with [com.snaphop.ical.IcalReader#builder()].
///
/// The module has no runtime dependencies. JSpecify nullness annotations are
/// part of the API but only needed at compile time, so the module requires them
/// as `static transitive`.
///
/// Thread safety: builders are the only mutable types. Everything a builder
/// makes, and everything read from a calendar, is immutable and thread-safe.
module com.snaphop.ical {
  exports com.snaphop.ical;

  requires static transitive org.jspecify;
}

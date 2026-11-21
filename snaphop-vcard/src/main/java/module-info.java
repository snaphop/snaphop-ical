/// Reads vCard (2.1, 3.0 and 4.0) text into immutable records: the contacts
/// phones, computers and web apps export.
///
/// Start with [com.snaphop.vcard.VcardReader#builder()].
///
/// The module has no runtime dependencies. JSpecify nullness annotations are
/// part of the API but only needed at compile time, so the module requires them
/// as `static transitive`.
///
/// Thread safety: builders are the only mutable types. Everything a builder
/// makes, and everything read from a card, is immutable and thread-safe.
module com.snaphop.vcard {
  exports com.snaphop.vcard;

  requires static transitive org.jspecify;
}

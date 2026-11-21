package com.snaphop.vcard;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/// Reads vCard text (versions 2.1, 3.0 and 4.0) into an immutable [VcardBook].
///
/// A reader is immutable and thread-safe. Make one with [#builder()] and reuse it.
///
/// ```java
/// VcardReader reader = VcardReader.builder().build();
/// VcardBook book = reader.read(Files.readAllBytes(path));
/// for (Vcard card : book.cards()) {
///   System.out.println(card.formattedName() + " " + card.emails());
/// }
/// ```
///
/// The reader never logs. Text it cannot read is reported according to its [Mode]:
/// as a [VcardParseException], or as a [VcardWarning], kept in
/// [VcardBook#warnings()] or handed to a consumer.
///
/// Work and memory are linear in the text's length: no value is read twice,
/// and photos, logos, sounds and keys are passed over without being kept or
/// decoded. How much text to read is the caller's to limit.
public sealed interface VcardReader {

  /// Reads cards from text, keeping the warnings in [VcardBook#warnings()].
  ///
  /// @param text the whole text, starting with `BEGIN:VCARD`
  /// @return the cards read
  /// @throws VcardParseException if the text cannot be read in the reader's [Mode]
  VcardBook read(CharSequence text);

  /// Reads cards from text, handing each warning to `warnings` as it is
  /// found, in place of keeping them. The book's [VcardBook#warnings()] is
  /// empty.
  ///
  /// @param text the whole text, starting with `BEGIN:VCARD`
  /// @param warnings called with each warning, during this call, on this thread;
  ///        never called in [Mode#STRICT]
  /// @return the cards read
  /// @throws VcardParseException if the text cannot be read in the reader's [Mode]
  VcardBook read(CharSequence text, Consumer<? super VcardWarning> warnings);

  /// Reads cards from a character stream, until its end, keeping the warnings
  /// in [VcardBook#warnings()].
  ///
  /// The caller closes `reader`.
  ///
  /// @param reader the text, starting with `BEGIN:VCARD`
  /// @return the cards read
  /// @throws IOException if `reader` fails
  /// @throws VcardParseException if the text cannot be read in the reader's [Mode]
  VcardBook read(Reader reader) throws IOException;

  /// Reads cards from a file's bytes, keeping the warnings in
  /// [VcardBook#warnings()].
  ///
  /// The bytes are UTF-8, after any byte order mark. A byte order mark of
  /// UTF-16 (as Windows tools write) reads them as UTF-16. Bytes that are
  /// not UTF-8 are an error in [Mode#STRICT]; in [Mode#LAX] the whole text
  /// is read as Windows-1252, as older Outlook wrote it, with a warning.
  ///
  /// @param bytes the file's bytes
  /// @return the cards read
  /// @throws VcardParseException if the text cannot be read in the reader's [Mode]
  VcardBook read(byte[] bytes);

  /// Starts configuring a reader.
  ///
  /// @return a new builder with the defaults described on its methods
  static Builder builder() {
    return new Builder();
  }

  /// How strictly the reader follows the vCard specifications.
  enum Mode {
    /// Anything that breaks the card's version of vCard throws a
    /// [VcardParseException].
    STRICT,
    /// What cannot be read is skipped and described in a [VcardWarning].
    /// Cards from real apps often break the specifications in small ways, so
    /// this is the default. A [VcardParseException] is still thrown when the
    /// text is not vCard at all.
    LAX
  }

  /// Configures a [VcardReader].
  ///
  /// Like all builders, it is mutable and not thread-safe.
  final class Builder {

    private Mode mode = Mode.LAX;

    private Builder() {}

    /// Sets how strictly the reader follows the vCard specifications. The
    /// default is [Mode#LAX].
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
    public VcardReader build() {
      return new DefaultVcardReader(mode);
    }
  }
}

record DefaultVcardReader(VcardReader.Mode mode) implements VcardReader {

  @Override
  public VcardBook read(CharSequence text) {
    var warnings = new ArrayList<VcardWarning>();
    return new DefaultVcardBook(Reading.read(text, new Problems(mode, warnings::add)), warnings);
  }

  @Override
  public VcardBook read(CharSequence text, Consumer<? super VcardWarning> warnings) {
    Objects.requireNonNull(warnings, "warnings");
    return new DefaultVcardBook(Reading.read(text, new Problems(mode, warnings)), List.of());
  }

  @Override
  public VcardBook read(Reader reader) throws IOException {
    var text = new StringWriter();
    reader.transferTo(text);
    return read(text.toString());
  }

  @Override
  public VcardBook read(byte[] bytes) {
    var warnings = new ArrayList<VcardWarning>();
    var problems = new Problems(mode, warnings::add);
    var text = Bytes.decode(bytes, problems);
    return new DefaultVcardBook(Reading.read(text, problems), warnings);
  }
}

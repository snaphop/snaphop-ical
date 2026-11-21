package com.snaphop.vcard;

/// Something [VcardReader.Mode#LAX] could not read as written, and what it did
/// instead.
///
/// ```
/// Line 7: BDAY is not a date, so it is left out: "BDAY:2014-02-30"
/// ```
///
/// is the [#message()] of
///
/// ```java
/// new VcardWarning(7, "BDAY is not a date", "it is left out", "\"BDAY:2014-02-30\"")
/// ```
///
/// [VcardReader.Mode#STRICT] throws the same problem, as a
/// [VcardParseException] with the message `Line 7: BDAY is not a date:
/// "BDAY:2014-02-30"`.
///
/// Contacts are personal: someone's phone number, address or notes. Only
/// `where` quotes the text. [#problem()] and [#consequence()] are the
/// reader's own words and property names, never the text, so they can be
/// logged or counted; `where`, and so [#message()], belong only in front of
/// whoever gave the text.
///
/// @param line the line of the text, counting from 1, as written before folded
///        lines are joined; 0 when the problem is not on one line
/// @param problem what is wrong, such as `BDAY is not a date`
/// @param consequence what the reader did about it, such as `it is left out`
/// @param where the line, quoted and cut short when long
public record VcardWarning(int line, String problem, String consequence, String where) {

  /// The warning as one line of text. It quotes the text: see above.
  ///
  /// @return the line, problem, consequence and where, as in the example above
  public String message() {
    return Problems.at(line, problem + ", so " + consequence + (where.isEmpty() ? "" : ": " + where));
  }

  /// The warning without the text it quotes, to log or count.
  ///
  /// @return the line, problem and consequence: `Line 7: BDAY is not a date, so it is left out`
  public String messageWithoutText() {
    return Problems.at(line, problem + ", so " + consequence);
  }

  /// The warning as one line of text.
  ///
  /// @return [#message()]
  @Override
  public String toString() {
    return message();
  }
}

package com.snaphop.ical;

/// Something [IcalReader.Mode#LAX] could not read as written, and what it did
/// instead.
///
/// ```
/// Line 8: DTEND is before DTSTART, so DTEND is left out: "DTEND:20261102T080000"
/// ```
///
/// is the [#message()] of
///
/// ```java
/// new IcalWarning(8, "DTEND is before DTSTART", "DTEND is left out", "\"DTEND:20261102T080000\"")
/// ```
///
/// [IcalReader.Mode#STRICT] throws the same problem, as an
/// [IcalParseException] with the message `Line 8: DTEND is before DTSTART:
/// "DTEND:20261102T080000"`.
///
/// @param line the line of the text, counting from 1, as written before folded
///        lines are joined
/// @param problem what is wrong, such as `DTEND is before DTSTART`
/// @param consequence what the reader did about it, such as `DTEND is left out`
/// @param where the line, quoted and cut short when long, or the event: its
///        title and start, such as `"Practice" at 20261121T090000`
public record IcalWarning(int line, String problem, String consequence, String where) {

  /// The warning as one line of text.
  ///
  /// @return the line, problem, consequence and where, as in the example above
  public String message() {
    return "Line " + line + ": " + problem + ", so " + consequence + ": " + where;
  }

  /// The warning as one line of text.
  ///
  /// @return [#message()]
  @Override
  public String toString() {
    return message();
  }
}

package com.snaphop.vcard;

/// Thrown when a [VcardReader] cannot read vCard text.
///
/// The message says what was wrong, on which line, and quotes the line. As
/// with a [VcardWarning], the quote is someone's personal data: log
/// [#problem()] and [#line()], not the message.
public final class VcardParseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /// The line number, as [#line()] returns it.
  private final int line;

  /// What was wrong, as [#problem()] returns it.
  private final String problem;

  VcardParseException(String message, int line, String problem) {
    super(message);
    this.line = line;
    this.problem = problem;
  }

  /// The line of the text where the problem is, counting from 1.
  ///
  /// Lines are counted as written, before folded lines are joined.
  ///
  /// @return the line number, or 0 if the problem is not on one line
  public int line() {
    return line;
  }

  /// What was wrong, in the reader's own words and property names, without
  /// the text: safe to log.
  ///
  /// @return the problem, such as `BDAY is not a date`
  public String problem() {
    return problem;
  }
}

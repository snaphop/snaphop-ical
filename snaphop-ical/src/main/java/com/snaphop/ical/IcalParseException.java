package com.snaphop.ical;

/// Thrown when an [IcalReader] cannot read calendar text.
///
/// The message says what was wrong, on which line, and quotes the line.
public final class IcalParseException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /// The line number, as [#line()] returns it.
  private final int line;

  IcalParseException(String message, int line) {
    super(message);
    this.line = line;
  }

  /// The line of the text where the problem is, counting from 1.
  ///
  /// Lines are counted as written, before folded lines are joined.
  ///
  /// @return the line number, or 0 if the problem is not on one line
  public int line() {
    return line;
  }
}

package com.snaphop.vcard;

import java.util.List;

/// What a [VcardReader] read: the cards of one text, such as an address
/// book exported to a `.vcf` file.
public sealed interface VcardBook {

  /// The cards, in order.
  ///
  /// @return the cards
  List<Vcard> cards();

  /// What lax mode read in its own way, in order. Empty when the warnings
  /// were handed to a consumer, and in strict mode.
  ///
  /// @return the warnings
  List<VcardWarning> warnings();
}

record DefaultVcardBook(List<Vcard> cards, List<VcardWarning> warnings) implements VcardBook {

  DefaultVcardBook {
    cards = List.copyOf(cards);
    warnings = List.copyOf(warnings);
  }

  @Override
  public String toString() {
    return "VcardBook[" + cards.size() + " cards, " + warnings.size() + " warnings]";
  }
}

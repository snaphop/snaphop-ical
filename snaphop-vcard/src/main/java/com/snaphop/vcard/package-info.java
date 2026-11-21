/// Reads vCard (2.1, 3.0 and 4.0) text into immutable records.
///
/// The types, in the order a caller meets them:
///
/// 1. [VcardReader] reads text, configured by its builder.
/// 2. [VcardBook] is what was read: the cards, and warnings.
/// 3. [Vcard] is one contact, with its values in plain types.
/// 4. [VcardParseException] reports text that cannot be read, and
///    [VcardWarning] text that lax mode read in its own way.
@org.jspecify.annotations.NullMarked
package com.snaphop.vcard;

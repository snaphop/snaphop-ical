package com.snaphop.vcard.test;

import com.snaphop.vcard.Vcard;
import com.snaphop.vcard.VcardBook;
import java.util.ArrayList;
import java.util.List;

/// Everything this reader gives for a card, as text: what the sample tests
/// compare in full, and the transform tests compare before and after.
final class Render {

  private Render() {}

  static String book(VcardBook book) {
    return cards(book.cards());
  }

  static String cards(List<Vcard> cards) {
    var out = new ArrayList<String>();
    for (var c : cards) out.add(card(c));
    return String.join("\n", out);
  }

  static String card(Vcard c) {
    var lines = new ArrayList<String>();
    lines.add("VCARD " + c.version().text() + " " + c.kind().name().toLowerCase(java.util.Locale.ROOT));
    add(lines, "uid", c.uid());
    add(lines, "fn", c.formattedName());
    var n = c.name();
    if (!n.equals(Vcard.Name.EMPTY)) {
      lines.add(("  n: " + esc(n.family()) + " | " + esc(n.given()) + " | " + esc(n.additional()) + " | " + esc(n.prefixes()) + " | "
          + esc(n.suffixes())).stripTrailing());
    }
    c.nicknames().forEach(v -> add(lines, "nickname", v));
    if (!c.organization().isEmpty()) add(lines, "org", String.join(" | ", c.organization()));
    c.titles().forEach(v -> add(lines, "title", v));
    if (c.birthdayOrNull() != null) add(lines, "bday", String.valueOf(c.birthdayOrNull()));
    if (c.anniversaryOrNull() != null) add(lines, "anniversary", String.valueOf(c.anniversaryOrNull()));
    c.emails().forEach(v -> add(lines, "email", value(v)));
    c.phones().forEach(v -> add(lines, "tel", value(v)));
    for (var a : c.addresses()) {
      add(lines, "adr", a.poBox() + " | " + a.extended() + " | " + a.street() + " | " + a.locality() + " | " + a.region() + " | "
          + a.postalCode() + " | " + a.country() + extras(a.types(), a.label(), a.preferred()));
    }
    c.urls().forEach(v -> add(lines, "url", value(v)));
    c.related().forEach(v -> add(lines, "related", value(v)));
    c.notes().forEach(v -> add(lines, "note", v));
    if (!c.categories().isEmpty()) add(lines, "categories", String.join(" | ", c.categories()));
    c.members().forEach(v -> add(lines, "member", v));
    return String.join("\n", lines);
  }

  private static String value(Vcard.Value v) {
    return v.value() + extras(v.types(), v.label(), v.preferred());
  }

  private static String extras(List<String> types, String label, boolean preferred) {
    var s = "";
    if (!types.isEmpty()) s += " " + types;
    if (!label.isEmpty()) s += " label=" + label;
    if (preferred) s += " preferred";
    return s;
  }

  private static void add(List<String> lines, String name, String value) {
    if (!value.isEmpty()) lines.add("  " + name + ": " + esc(value));
  }

  private static String esc(String s) {
    return s.replace("\\n", "\\\\n").replace("\n", "\\n");
  }
}

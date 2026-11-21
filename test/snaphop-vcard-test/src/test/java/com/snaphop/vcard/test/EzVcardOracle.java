package com.snaphop.vcard.test;

import static com.snaphop.vcard.test.Field.ADR;
import static com.snaphop.vcard.test.Field.ANNIVERSARY;
import static com.snaphop.vcard.test.Field.BDAY;
import static com.snaphop.vcard.test.Field.CATEGORIES;
import static com.snaphop.vcard.test.Field.EMAIL;
import static com.snaphop.vcard.test.Field.FN;
import static com.snaphop.vcard.test.Field.KIND;
import static com.snaphop.vcard.test.Field.MEMBER;
import static com.snaphop.vcard.test.Field.N;
import static com.snaphop.vcard.test.Field.NICKNAME;
import static com.snaphop.vcard.test.Field.NOTE;
import static com.snaphop.vcard.test.Field.ORG;
import static com.snaphop.vcard.test.Field.RELATED;
import static com.snaphop.vcard.test.Field.TEL;
import static com.snaphop.vcard.test.Field.TITLE;
import static com.snaphop.vcard.test.Field.UID;
import static com.snaphop.vcard.test.Field.URL;
import static com.snaphop.vcard.test.Field.VERSION;

import ezvcard.VCard;
import ezvcard.io.text.VCardReader;
import ezvcard.parameter.VCardParameter;
import ezvcard.property.DateOrTimeProperty;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/// ez-vcard 0.12.2 as an oracle: what agentic-todo used before this reader,
/// and the most used vCard library for Java. Each card rendered as [Field]s,
/// from ez-vcard's own types, without reading anything it leaves raw.
final class EzVcardOracle {

  private EzVcardOracle() {}

  static List<Field.Card> read(String text) {
    var cards = new ArrayList<Field.Card>();
    try (var reader = new VCardReader(new StringReader(text))) {
      VCard v;
      while ((v = reader.readNext()) != null) cards.add(card(v));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return cards;
  }

  private static Field.Card card(VCard v) {
    var b = new Field.Builder();
    b.add(VERSION, v.getVersion() == null ? "" : v.getVersion().getVersion());
    if (v.getUid() != null) b.add(UID, v.getUid().getValue());
    if (v.getFormattedName() != null) b.add(FN, v.getFormattedName().getValue());
    var n = v.getStructuredName();
    if (n != null) {
      b.parts(N, List.of(list(n.getFamily()), list(n.getGiven()), n.getAdditionalNames(), n.getPrefixes(), n.getSuffixes()), " ",
          List.of());
    }
    for (var nick : v.getNicknames()) nick.getValues().forEach(x -> b.add(NICKNAME, x));
    if (v.getOrganization() != null) {
      b.add(ORG, String.join("|", v.getOrganization().getValues().stream().map(Field::clean).filter(s -> !s.isEmpty()).toList()));
    }
    v.getTitles().forEach(t -> b.add(TITLE, t.getValue()));
    b.add(BDAY, day(v.getBirthday()));
    b.add(ANNIVERSARY, day(v.getAnniversary()));
    v.getEmails().forEach(e -> b.value(EMAIL, e.getValue(), types(e.getTypes())));
    for (var t : v.getTelephoneNumbers()) {
      var uri = t.getUri();
      var number = t.getText() != null ? t.getText()
          : uri == null ? null : uri.getNumber() + (uri.getExtension() == null ? "" : " ext. " + uri.getExtension());
      b.value(TEL, number, types(t.getTypes()));
    }
    for (var a : v.getAddresses()) {
      b.parts(ADR, List.of(a.getPoBoxes(), a.getExtendedAddresses(), a.getStreetAddresses(), a.getLocalities(), a.getRegions(),
          a.getPostalCodes(), a.getCountries()), "\n", types(a.getTypes()));
    }
    v.getUrls().forEach(u -> b.value(URL, u.getValue(), u.getType() == null ? List.of() : List.of(u.getType())));
    v.getRelations().forEach(r -> b.value(RELATED, r.getText() != null ? r.getText() : r.getUri(), types(r.getTypes())));
    v.getNotes().forEach(x -> b.add(NOTE, x.getValue()));
    for (var c : v.getCategoriesList()) {
      for (var x : c.getValues()) {
        var clean = Field.clean(x);
        if (!clean.isEmpty() && !b.build().get(CATEGORIES).lines().toList().contains(clean)) b.add(CATEGORIES, clean);
      }
    }
    var kind = v.getKind();
    b.add(KIND, kind == null ? "individual" : kind(kind.getValue()));
    v.getMembers().forEach(m -> b.add(MEMBER, m.getUri()));
    return b.build();
  }

  private static List<String> list(@Nullable String s) {
    return s == null ? List.of() : List.of(s);
  }

  private static List<String> types(List<? extends VCardParameter> types) {
    return types.stream().map(VCardParameter::getValue).filter(x -> x != null).toList();
  }

  private static String kind(@Nullable String value) {
    if (value == null) return "individual";
    return switch (value.toLowerCase(Locale.ROOT)) {
      case "individual", "group", "location" -> value.toLowerCase(Locale.ROOT);
      case "org" -> "organization";
      default -> "other";
    };
  }

  private static @Nullable String day(@Nullable DateOrTimeProperty p) {
    if (p == null) return null;
    Temporal t = p.getDate();
    if (t != null) {
      if (t instanceof LocalDate d) return d.toString();
      if (t instanceof OffsetDateTime o) return o.toLocalDate().toString();
      if (t instanceof Instant i) return i.atOffset(ZoneOffset.UTC).toLocalDate().toString();
      if (t instanceof java.time.LocalDateTime l) return l.toLocalDate().toString();
      return t.toString();
    }
    var partial = p.getPartialDate();
    if (partial != null && partial.getMonth() != null && partial.getDate() != null) {
      return partial.getYear() != null ? String.format("%04d-%02d-%02d", partial.getYear(), partial.getMonth(), partial.getDate())
          : String.format("--%02d-%02d", partial.getMonth(), partial.getDate());
    }
    return null;
  }
}

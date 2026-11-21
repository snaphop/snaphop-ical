package com.snaphop.vcard.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.snaphop.vcard.Vcard;
import com.snaphop.vcard.VcardReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// The samples written for this project, each read in full and compared
/// with what an address book should show: every value, its types, Apple's
/// labels, and where each card starts.
class SampleTest {

  static final VcardReader READER = VcardReader.builder().build();

  enum Expected {
    APPLE("""
        VCARD 3.0 individual
          uid: 8B1F3C2E-0D4A-4E57-9C61-2F5D7A1B9E01
          fn: Maya Lucía Rivera
          n: Rivera | Maya | Lucía |  |
          nickname: Mimi
          org: Rivera Bakery | Kitchen
          title: Head Baker
          bday: --05-03
          anniversary: 2011-06-18
          email: maya@example.com [internet, pref] preferred
          email: maya.rivera@example.org [internet] label=School
          tel: (555) 010-0101 [cell, voice, pref] preferred
          tel: 555-010-0102 label=Grandma's
          tel: 555 010 0103 [home, voice]
          adr:  |  | 12 Orchard Lane\\nApt 3 | Springfield | OR | 97477 | United States [home, pref] preferred
          url: https://bakery.example.com [pref] label=homepage preferred
          related: Daniel Rivera [pref] label=spouse preferred
          related: Sofia label=child
          related: Abuela Rosa label=Grandma
          note: Allergic to peanuts, not tree nuts.\\nPicks up Tuesdays.
          categories: Family | School
        VCARD 3.0 organization
          uid: 4C6E0B8A-6E2D-4C3B-8F71-0E9A2B3C4D5E
          fn: Springfield Pediatrics
          org: Springfield Pediatrics
          tel: 555-010-0200 [work, voice, pref] preferred
          tel: 555-010-0201 label=After hours
        VCARD 3.0 group
          uid: 9D8C7B6A-5F4E-4D3C-2B1A-0F9E8D7C6B5A
          fn: Soccer carpool
          n: Soccer carpool |  |  |  |
          member: urn:uuid:8B1F3C2E-0D4A-4E57-9C61-2F5D7A1B9E01
          member: urn:uuid:1A2B3C4D-5E6F-4A7B-8C9D-0E1F2A3B4C5D""", "1, 48, 60", "30, 10, 8"),
    GOOGLE("""
        VCARD 3.0 individual
          fn: Jordan Lee
          n: Lee | Jordan |  |  |
          nickname: Jojo
          bday: --11-21
          email: jordan.lee@example.com [internet, home]
          email: jlee@work.example.com [internet, work]
          tel: +1 555-010-0301 [cell]
          tel: +1 555-010-0302 label=Grandpa's house
          adr:  |  | 400 Pine St | Portland | OR | 97201 | US [home]
          url: https://jordan.example.com label=PROFILE
          related: Sam Lee label=sibling
          note: Soccer coach; brings oranges
          categories: myContacts | Family
        VCARD 3.0 individual
          fn: Ms. Patel
          n: Patel |  |  | Ms. |
          org: Springfield Elementary
          title: Second grade teacher
          bday: 1988-02-29
          email: patel@school.example.edu [internet, work]
          categories: myContacts""", "1, 20", "17, 8"),
    ANDROID("""
        VCARD 2.1 individual
          fn: José Muñoz
          n: Muñoz | José |  |  |
          org: Escuela Norte, S.L.
          bday: 1979-02-14
          email: jose@example.com [home]
          tel: 555-010-0401 [cell, pref] preferred
          tel: 555-010-0402 [home]
          tel: 555-010-0403 [x-abuelo]
          adr:  |  | Calle Mayor 5, 2º | Madrid |  | 28013 | España [home]
          note: Línea uno\\nLínea dos con acentos: ñ ü é, y más texto para que la línea sea larga
        VCARD 2.1 individual
          fn: Chidi Okafor
          n: Okafor | Chidi |  |  |
          email: chidi@example.com [internet, work]
          tel: 555-010-0501 [work, voice]
          categories: Neighbors""", "1, 20", "12, 6"),
    OUTLOOK("""
        VCARD 2.1 individual
          fn: Hélène Dubois
          n: Dubois | Hélène |  |  |
          org: Café Lumière | Comptabilité
          title: Gérante
          anniversary: 2005-09-17
          email: helene@example.com [pref, internet] preferred
          tel: (555) 010-0601 [work, voice]
          tel: (555) 010-0602 [cell, voice]
          adr:  |  | 12 rue de la Paix | Montréal | QC | H2X 1Y4 | Canada [work, pref] preferred
          related: Étienne Dubois [spouse]
          related: Marc Tremblay [manager]
          note: Préfère être appelée le matin.\\nDeuxième ligne – merci""", "1", "16"),
    VCARD4("""
        VCARD 4.0 individual
          uid: urn:uuid:4fbe8971-0bc3-424c-9c26-36c3e1eff6b1
          fn: Dr. Amara Nwosu
          n: Nwosu | Amara | Ifeoma Grace | Dr. |
          nickname: Ama
          nickname: Mara
          org: Salem Clinic | Pediatrics
          title: Pediatrician
          bday: --04-18
          anniversary: 2015-06-20
          email: amara@clinic.example.com [work]
          email: amara@example.com preferred
          tel: +1-555-010-0701 [voice, cell] preferred
          tel: +1-555-010-0702 ext. 34 [work]
          adr:  | Apt 2 | 7 Elm Court | Salem | OR | 97301 | USA [home]
          url: https://clinic.example.com/nwosu [work]
          related: Kwame Nwosu [spouse]
          related: urn:uuid:03a0e51f-d1aa-4385-8a53-e29025acd8af [child]
          note: Prefers text, not calls.
          categories: Doctors | Family
        VCARD 4.0 group
          uid: urn:uuid:a1b2c3d4-0000-4000-8000-000000000001
          fn: Book club
          member: urn:uuid:4fbe8971-0bc3-424c-9c26-36c3e1eff6b1
          member: mailto:reader@example.com""", "1, 24", "21, 6");

    final String cards;
    /// Where each card starts, and how many properties it keeps.
    final String lines;
    final String properties;

    Expected(String cards, String lines, String properties) {
      this.cards = cards;
      this.lines = lines;
      this.properties = properties;
    }

    Corpus.Sample sample() {
      return Corpus.Sample.valueOf(name());
    }
  }

  @ParameterizedTest
  @EnumSource(Expected.class)
  void reads(Expected e) {
    var book = READER.read(e.sample().bytes());
    assertEquals(e.cards, Render.book(book));
    assertEquals(e.lines, String.join(", ", book.cards().stream().map(c -> String.valueOf(c.line())).toList()));
    assertEquals(e.properties, String.join(", ", book.cards().stream().map(c -> String.valueOf(c.properties().size())).toList()));
  }

  /// What is not read into values stays in the properties, as written; a
  /// photo does not.
  @Test
  void properties() {
    var apple = READER.read(Corpus.Sample.APPLE.bytes()).cards().getFirst();
    assertEquals(List.of(), apple.properties().stream().filter(p -> p.name().equals("PHOTO")).toList());
    var adr = apple.properties().stream().filter(p -> p.name().equals("X-ABADR")).findFirst().orElseThrow();
    assertEquals(new Vcard.Property("item4", "X-ABADR", java.util.Map.of(), "us", 17), adr);

    var android = READER.read(Corpus.Sample.ANDROID.bytes()).cards().getFirst();
    var custom = android.properties().stream().filter(p -> p.name().equals("X-ANDROID-CUSTOM")).findFirst().orElseThrow();
    assertEquals("vnd.android.cursor.item/nickname;Pepe;1;;;;;;;;;;;;;", custom.text());
    var fn = android.properties().stream().filter(p -> p.name().equals("FN")).findFirst().orElseThrow();
    assertEquals(List.of("QUOTED-PRINTABLE"), fn.parameter("encoding"));
    assertEquals("José Muñoz", fn.value());

    var vcard4 = READER.read(Corpus.Sample.VCARD4.bytes()).cards().getFirst();
    var address = vcard4.properties().stream().filter(p -> p.name().equals("ADR")).findFirst().orElseThrow();
    assertEquals(List.of("7 Elm Court\nApt 2\nSalem, OR 97301"), address.parameter("LABEL"));
    var gender = vcard4.properties().stream().filter(p -> p.name().equals("GENDER")).findFirst().orElseThrow();
    assertEquals("F", gender.text());
  }

  /// A card's `toString` never shows its text: it could end up in a log.
  @Test
  void toStringHasNoText() {
    var card = READER.read(Corpus.Sample.APPLE.bytes()).cards().getFirst();
    assertEquals("Vcard[3.0, line 1]", card.toString());
    assertEquals("VcardBook[3 cards, 0 warnings]", READER.read(Corpus.Sample.APPLE.bytes()).toString());
  }
}

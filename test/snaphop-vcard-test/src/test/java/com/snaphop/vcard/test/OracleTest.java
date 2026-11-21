package com.snaphop.vcard.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.snaphop.vcard.VcardReader;
import com.snaphop.vcard.test.Corpus.Sample;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/// Every sample read by this reader and by each oracle, field by field:
/// every [Sample] × [Oracle] × [Field].
///
/// Where an oracle reads a field differently, [#DIFFERENCES] says why, and
/// the test checks it still does, so that a fix on either side is noticed.
class OracleTest {

  enum Oracle {
    EZ_VCARD,
    ICALJS;

    List<Field.Card> read(String text) {
      return switch (this) {
        case EZ_VCARD -> EzVcardOracle.read(text);
        case ICALJS -> IcaljsOracle.read(text);
      };
    }
  }

  static final VcardReader READER = VcardReader.builder().build();

  static String ours(Sample s, Field f) {
    return f.of(READER.read(s.text()).cards().stream().map(Field::ours).toList());
  }

  static String theirs(Sample s, Oracle o, Field f) {
    try {
      return f.of(o.read(s.text()));
    } catch (RuntimeException e) {
      return "fails: " + e.getMessage();
    }
  }

  static Stream<Arguments> cases() {
    var cases = new ArrayList<Arguments>();
    for (var s : Sample.values()) {
      for (var o : Oracle.values()) {
        for (var f : Field.values()) cases.add(Arguments.of(s, o, f));
      }
    }
    return cases.stream();
  }

  @ParameterizedTest(name = "{0} {1} {2}")
  @MethodSource("cases")
  void agrees(Sample s, Oracle o, Field f) {
    var ours = ours(s, f);
    var theirs = theirs(s, o, f);
    var reason = reason(s, o, f);
    if (reason == null) {
      assertEquals(theirs, ours);
    } else if (UNREAD.containsKey(s + " " + o)) {
      assertTrue(theirs.startsWith("fails: "), () -> "Listed as failing, but now reads it: " + reason);
    } else {
      assertNotEquals(theirs, ours, "Listed as differing, but now agrees: " + reason);
    }
  }

  static String reason(Sample s, Oracle o, Field f) {
    var whole = UNREAD.get(s + " " + o);
    if (whole != null) return whole;
    return DIFFERENCES.get(s + " " + o + " " + f);
  }

  static final String VCARD_21 = "ICAL.js reads vCard 3.0 and 4.0, not 2.1: it fails on 2.1's bare parameters"
      + " (TEL;WORK;VOICE) and quoted-printable values";
  static final String BARE_PARAMETER = "a bare parameter in a 3.0 card (PHOTO;BASE64), which ICAL.js fails on and this reader"
      + " reads as vCard 2.1 would";

  /// Samples an oracle fails to read at all.
  static final Map<String, String> UNREAD = Map.of(
      "EZ_ANDROID ICALJS", VCARD_21,
      "EZ_MS_OUTLOOK ICALJS", VCARD_21,
      "EZ_OUTLOOK_2003 ICALJS", VCARD_21,
      "EZ_OUTLOOK_2007 ICALJS", VCARD_21,
      "ANDROID ICALJS", VCARD_21,
      "OUTLOOK ICALJS", VCARD_21,
      "EZ_MAC_ADDRESS_BOOK ICALJS", BARE_PARAMETER);

  static final String ANNIVERSARY = "an anniversary as Apple (X-ABDATE labelled _$!<Anniversary>!$_), Outlook"
      + " (X-MS-ANNIVERSARY), Evolution or Thunderbird (X-EVOLUTION-ANNIVERSARY, X-ANNIVERSARY) write it, which the"
      + " oracles leave raw: they read only vCard 4's ANNIVERSARY";
  static final String RELATED = "related people as Apple (X-ABRELATEDNAMES), Outlook (X-MS-SPOUSE, X-MS-MANAGER,"
      + " X-MS-ASSISTANT) or Evolution (X-EVOLUTION-SPOUSE...) write them, which the oracles leave raw: they read only"
      + " vCard 4's RELATED";
  static final String APPLE_GROUP = "Apple's groups and companies (X-ADDRESSBOOKSERVER-KIND, X-ADDRESSBOOKSERVER-MEMBER,"
      + " X-ABShowAs:COMPANY), which the oracles leave raw: they read only vCard 4's KIND and MEMBER";
  static final String OMIT_YEAR = "a birthday without its year as Apple writes it (BDAY;X-APPLE-OMIT-YEAR=1604:1604-05-03),"
      + " which the oracles read as May 3, 1604";
  static final String APPLE_UID = "no UID but Apple's X-ABUID, which this reader takes as the card's id";
  static final String ESCAPES = "ICAL.js leaves escapes in vCard 3.0 text (\\; \\: \\\"), which RFC 2426 says to undo,"
      + " as this reader and ez-vcard do";
  static final String TEL_URI = "a tel: URI with an extension (tel:+1-418-656-9254;ext=102), which ICAL.js gives as written"
      + " and this reader and ez-vcard as the number and its extension";
  static final String CR_CR_LF = "an iPhone's CR CR LF line endings: ICAL.js reads each as two line breaks, then misreads"
      + " structured values, dates and grouped properties; this reader and ez-vcard pass over the blank lines";

  /// Fields an oracle reads differently, by `SAMPLE ORACLE FIELD`. `*`
  /// stands for both oracles.
  static final Map<String, String> DIFFERENCES = differences(
      ANNIVERSARY, "EZ_GMAIL_SINGLE * ANNIVERSARY", "EZ_GMAIL_SINGLE2 * ANNIVERSARY", "EZ_EVOLUTION * ANNIVERSARY",
      "EZ_GMAIL * ANNIVERSARY", "EZ_MS_OUTLOOK EZ_VCARD ANNIVERSARY", "EZ_OUTLOOK_2007 EZ_VCARD ANNIVERSARY",
      "EZ_THUNDERBIRD * ANNIVERSARY", "ICALJS_ESCAPE_SEMICOLON * ANNIVERSARY", "APPLE * ANNIVERSARY",
      "OUTLOOK EZ_VCARD ANNIVERSARY",
      RELATED, "EZ_GMAIL_SINGLE * RELATED", "EZ_GMAIL_SINGLE2 * RELATED", "EZ_EVOLUTION * RELATED", "EZ_GMAIL * RELATED",
      "EZ_MAC_ADDRESS_BOOK EZ_VCARD RELATED", "EZ_MS_OUTLOOK EZ_VCARD RELATED", "EZ_OUTLOOK_2007 EZ_VCARD RELATED",
      "EZ_THUNDERBIRD * RELATED", "APPLE * RELATED", "GOOGLE * RELATED", "OUTLOOK EZ_VCARD RELATED",
      APPLE_GROUP, "APPLE * KIND", "APPLE * MEMBER",
      OMIT_YEAR, "APPLE * BDAY",
      APPLE_UID, "EZ_MAC_ADDRESS_BOOK EZ_VCARD UID",
      ESCAPES, "EZ_GMAIL_SINGLE ICALJS URL", "EZ_GMAIL_SINGLE2 ICALJS URL", "EZ_GMAIL ICALJS URL", "GOOGLE ICALJS URL",
      "EZ_EVOLUTION ICALJS NOTE", "EZ_GMAIL ICALJS NOTE", "EZ_LOTUS_NOTES ICALJS NOTE", "EZ_THUNDERBIRD ICALJS NOTE",
      "GOOGLE ICALJS NOTE",
      TEL_URI, "EZ_RFC6350 ICALJS TEL", "ICALJS_VCARD_AUTHOR ICALJS TEL", "VCARD4 ICALJS TEL",
      CR_CR_LF, "EZ_IPHONE ICALJS N", "EZ_IPHONE ICALJS ORG", "EZ_IPHONE ICALJS BDAY", "EZ_IPHONE ICALJS EMAIL",
      "EZ_IPHONE ICALJS TEL", "EZ_IPHONE ICALJS ADR", "EZ_IPHONE ICALJS URL");

  /// A reason, then the keys it is for; the next reason, then its keys.
  private static Map<String, String> differences(String... reasonsAndKeys) {
    var map = new LinkedHashMap<String, String>();
    String reason = null;
    for (var s : reasonsAndKeys) {
      if (s.split(" ").length != 3) {
        reason = s;
        continue;
      }
      var keys = s.contains("*") ? Stream.of(Oracle.values()).map(o -> s.replace("*", o.name())).toList() : List.of(s);
      for (var key : keys) {
        if (map.put(key, Objects.requireNonNull(reason)) != null) throw new IllegalStateException("Listed twice: " + key);
      }
    }
    return Collections.unmodifiableMap(map);
  }
}

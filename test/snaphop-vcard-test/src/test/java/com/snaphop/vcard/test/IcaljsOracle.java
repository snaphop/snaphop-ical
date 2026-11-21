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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.FileSystem;
import org.graalvm.polyglot.io.IOAccess;

/// ICAL.js as an oracle, run on GraalJS: Mozilla's parser, which reads
/// vCard 3.0 and 4.0 into jCard (RFC 7095). Each card rendered as [Field]s
/// from its properties (`icaljs-vcard-oracle.js`), taking the first of a
/// property this reader takes one of.
final class IcaljsOracle {

  private IcaljsOracle() {}

  /// ICAL.js's own modules, shared with snaphop-ical's tests.
  static final Path LIB = Corpus.ROOT.resolve("icaljs/lib").toAbsolutePath().normalize();

  private static Context context;
  private static Value oracle;

  static synchronized List<Field.Card> read(String text) {
    if (oracle == null) start();
    var cards = oracle.getMember("read").execute(text);
    var out = new ArrayList<Field.Card>();
    for (long i = 0; i < cards.getArraySize(); i++) out.add(card(cards.getArrayElement(i)));
    return out;
  }

  private static Field.Card card(Value props) {
    var b = new Field.Builder();
    boolean fn = false, n = false, org = false, bday = false, anniversary = false, uid = false, kind = false;
    for (long i = 0; i < props.getArraySize(); i++) {
      var p = props.getArrayElement(i);
      var name = p.getMember("name").asString().toUpperCase(Locale.ROOT);
      var values = p.getMember("values");
      var types = strings(p.getMember("types"));
      var first = values.getArraySize() == 0 ? "" : text(values.getArrayElement(0));
      switch (name) {
        case "VERSION" -> b.add(VERSION, first);
        case "UID" -> {
          if (!uid) b.add(UID, first);
          uid = true;
        }
        case "FN" -> {
          if (!fn) b.add(FN, first);
          fn = true;
        }
        case "N" -> {
          if (!n) b.parts(N, parts(values.getArrayElement(0), 5), " ", List.of());
          n = true;
        }
        case "NICKNAME" -> all(values).forEach(v -> b.add(NICKNAME, v));
        case "ORG" -> {
          if (!org) {
            var parts = values.getArrayElement(0).hasArrayElements()
                ? parts(values.getArrayElement(0), 0).stream().map(l -> String.join(",", l)).toList()
                : all(values);
            b.add(ORG, String.join("|", parts.stream().map(Field::clean).filter(s -> !s.isEmpty()).toList()));
          }
          org = true;
        }
        case "TITLE" -> b.add(TITLE, first);
        case "BDAY" -> {
          if (!bday) b.add(BDAY, Field.day(first));
          bday = true;
        }
        case "ANNIVERSARY" -> {
          if (!anniversary) b.add(ANNIVERSARY, Field.day(first));
          anniversary = true;
        }
        case "EMAIL" -> b.value(EMAIL, first, types);
        case "TEL" -> b.value(TEL, first.regionMatches(true, 0, "tel:", 0, 4) ? first.substring(4) : first, types);
        case "ADR" -> b.parts(ADR, parts(values.getArrayElement(0), 7), "\n", types);
        case "URL" -> b.value(URL, first, types);
        case "RELATED" -> b.value(RELATED, first, types);
        case "NOTE" -> b.add(NOTE, first);
        case "CATEGORIES" -> {
          for (var v : all(values)) {
            var clean = Field.clean(v);
            if (!clean.isEmpty() && !b.build().get(CATEGORIES).lines().toList().contains(clean)) b.add(CATEGORIES, clean);
          }
        }
        case "KIND" -> {
          if (!kind) b.add(KIND, switch (first.toLowerCase(Locale.ROOT)) {
            case "individual", "group", "location" -> first.toLowerCase(Locale.ROOT);
            case "org" -> "organization";
            default -> "other";
          });
          kind = true;
        }
        case "MEMBER" -> b.add(MEMBER, first);
        default -> {}
      }
    }
    if (!kind) b.add(KIND, "individual");
    return b.build();
  }

  /// A structured value's parts, each a list, padded to `size`.
  private static List<List<String>> parts(Value value, int size) {
    var parts = new ArrayList<List<String>>();
    if (value.hasArrayElements()) {
      for (long i = 0; i < value.getArraySize(); i++) {
        var part = value.getArrayElement(i);
        parts.add(part.hasArrayElements() ? strings(part) : List.of(part.asString()));
      }
    } else {
      parts.add(List.of(value.asString()));
    }
    while (parts.size() < size) parts.add(List.of());
    return parts;
  }

  private static String text(Value v) {
    return v.hasArrayElements() ? String.join(";", strings(v)) : v.asString();
  }

  private static List<String> all(Value values) {
    var out = new ArrayList<String>();
    for (long i = 0; i < values.getArraySize(); i++) {
      var v = values.getArrayElement(i);
      if (v.hasArrayElements()) out.addAll(strings(v));
      else out.add(v.asString());
    }
    return out;
  }

  private static List<String> strings(Value array) {
    var out = new ArrayList<String>();
    for (long i = 0; i < array.getArraySize(); i++) {
      var v = array.getArrayElement(i);
      out.add(v.hasArrayElements() ? String.join(",", strings(v)) : v.asString());
    }
    return out;
  }

  private static void start() {
    var files = IOAccess.newBuilder().fileSystem(FileSystem.newReadOnlyFileSystem(FileSystem.newDefaultFileSystem())).build();
    context = Context.newBuilder("js").option("engine.WarnInterpreterOnly", "false").allowIO(files).build();
    var start = "import ICAL from \"" + LIB.resolve("module.js").toUri() + "\"; globalThis.ICAL = ICAL;";
    context.eval(Source.newBuilder("js", start, "start.mjs").mimeType("application/javascript+module").buildLiteral());
    oracle = context.eval(Source.create("js", resource("icaljs-vcard-oracle.js")));
  }

  private static String resource(String name) {
    try (var in = Objects.requireNonNull(IcaljsOracle.class.getResourceAsStream(name), name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}

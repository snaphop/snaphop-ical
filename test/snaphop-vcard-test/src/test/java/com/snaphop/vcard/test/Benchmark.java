package com.snaphop.vcard.test;

import com.snaphop.vcard.VcardReader;
import ezvcard.VCard;
import ezvcard.io.text.VCardReader;
import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/// This reader against ez-vcard: speed, memory, size, and what each does
/// with hostile and broken text. Not a test: it writes
/// `target/benchmark.md`, for the README. Run it alone:
///
/// ```
/// ./mvnw -pl test/snaphop-vcard-test -am test -Dtest=Benchmark -Dbenchmark=true -Dsurefire.failIfNoSpecifiedTests=false
/// ```
@EnabledIfSystemProperty(named = "benchmark", matches = "true")
class Benchmark {

  static final VcardReader OURS = VcardReader.builder().build();

  /// Reads text, giving how many cards: this reader, or ez-vcard's plain-text
  /// reader, as agentic-todo used it.
  enum Reader {
    SNAPHOP_VCARD {
      @Override
      int read(String text) {
        return OURS.read(text).cards().size();
      }

      @Override
      Object keep(String text) {
        return OURS.read(text);
      }
    },
    EZ_VCARD {
      @Override
      int read(String text) {
        return ez(text).size();
      }

      @Override
      Object keep(String text) {
        return ez(text);
      }
    };

    abstract int read(String text);

    abstract Object keep(String text);

    static List<VCard> ez(String text) {
      var cards = new ArrayList<VCard>();
      try (var reader = new VCardReader(new StringReader(text))) {
        VCard v;
        while ((v = reader.readNext()) != null) cards.add(v);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return cards;
    }
  }

  final StringBuilder out = new StringBuilder();

  @Test
  void run() throws Exception {
    out.append("JDK ").append(Runtime.version()).append(", ").append(System.getProperty("os.arch")).append(", ")
        .append(Runtime.getRuntime().availableProcessors()).append(" processors\n\n");
    size();
    speed();
    hostile();
    broken();
    Files.writeString(Path.of("target/benchmark.md"), out);
    System.out.println(out);
  }

  /// An address book of every sample's cards, again and again, without photos,
  /// and the same with the iPhone sample's photo on each card.
  static String book(boolean photos, int cards) {
    var b = new StringBuilder();
    int n = 0;
    while (n < cards) {
      for (var s : Corpus.Sample.values()) {
        if (s == Corpus.Sample.EZ_IPHONE && !photos) continue;
        if (photos && s != Corpus.Sample.EZ_IPHONE) continue;
        var text = s.text();
        b.append(text);
        if (!text.endsWith("\n")) b.append("\r\n");
        n += OURS.read(text).cards().size();
      }
    }
    return b.toString();
  }

  void size() throws Exception {
    Path ours;
    try (var files = Files.list(Path.of("../../snaphop-vcard/target"))) {
      ours = files.filter(p -> p.getFileName().toString().matches("snaphop-vcard-[0-9.]+(-SNAPSHOT)?\\.jar")).findFirst().orElseThrow();
    }
    var m2 = Path.of(System.getProperty("user.home"), ".m2/repository");
    var ez = m2.resolve("com/googlecode/ez-vcard/ez-vcard/0.12.2/ez-vcard-0.12.2.jar");
    var vinnie = m2.resolve("com/github/mangstadt/vinnie/2.0.2/vinnie-2.0.2.jar");
    var freemarker = m2.resolve("org/freemarker/freemarker/2.3.34/freemarker-2.3.34.jar");
    out.append("## Size\n\n| Jar | Bytes | Classes |\n|---|---:|---:|\n");
    row("snaphop-vcard", ours);
    row("ez-vcard 0.12.2", ez);
    row("vinnie 2.0.2 (ez-vcard's parser)", vinnie);
    row("FreeMarker 2.3.34 (needed to load any VCard)", freemarker);
    long total = Files.size(ez) + Files.size(vinnie) + Files.size(freemarker);
    out.append("| **ez-vcard, as it runs** | **").append(String.format(Locale.ROOT, "%,d", total)).append("** | **")
        .append(classes(ez) + classes(vinnie) + classes(freemarker)).append("** |\n\n");
  }

  private void row(String name, Path jar) throws IOException {
    out.append("| ").append(name).append(" | ").append(String.format(Locale.ROOT, "%,d", Files.size(jar))).append(" | ")
        .append(classes(jar)).append(" |\n");
  }

  private static long classes(Path jar) throws IOException {
    try (var z = new ZipFile(jar.toFile())) {
      return z.stream().filter(e -> e.getName().endsWith(".class")).count();
    }
  }

  void speed() {
    out.append("## Speed and memory\n\nThe median of 21 reads, after 10 to warm up. Allocated is what one read allocates;"
        + " kept is what the result holds on to, after a collection.\n\n");
    out.append("| Text | Reader | Cards | Median ms | MB allocated | MB kept |\n|---|---|---:|---:|---:|---:|\n");
    var books = List.of(
        new String[] {"10,000 cards: every sample but the iPhone's, again and again", book(false, 10_000)},
        new String[] {"500 iPhone cards, each with its photo", book(true, 500)});
    for (var book : books) {
      for (var r : Reader.values()) {
        var text = book[1];
        int cards = 0;
        for (int i = 0; i < 10; i++) cards = r.read(text);
        var times = new long[21];
        for (int i = 0; i < times.length; i++) {
          long t = System.nanoTime();
          r.read(text);
          times[i] = System.nanoTime() - t;
        }
        Arrays.sort(times);
        var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();
        r.read(text);
        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        long kept = kept(() -> r.keep(text));
        out.append(String.format(Locale.ROOT, "| %s (%.1f MB) | %s | %,d | %.1f | %.1f | %.1f |%n", book[0],
            text.length() / 1e6, r, cards, times[10] / 1e6, allocated / 1e6, kept / 1e6));
      }
    }
    out.append('\n');
  }

  private static long kept(java.util.function.Supplier<Object> read) {
    var runtime = Runtime.getRuntime();
    gc();
    long before = runtime.totalMemory() - runtime.freeMemory();
    var result = read.get();
    try {
      gc();
      long after = runtime.totalMemory() - runtime.freeMemory();
      return Math.max(0, after - before);
    } finally {
      java.lang.ref.Reference.reachabilityFence(result);
    }
  }

  private static void gc() {
    for (int i = 0; i < 3; i++) {
      System.gc();
      try {
        Thread.sleep(50);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  void hostile() throws Exception {
    out.append("## Hostile text\n\nEach of `HostileTest`'s texts, read once, stopped after 60 seconds.\n\n");
    out.append("| Text | MB | snaphop-vcard | ez-vcard |\n|---|---:|---|---|\n");
    for (var big : HostileTest.Big.values()) {
      var text = big.text();
      out.append("| ").append(big).append(" | ").append(String.format(Locale.ROOT, "%.1f", text.length() / 1e6)).append(" | ")
          .append(timed(Reader.SNAPHOP_VCARD::read, text)).append(" | ").append(timed(Reader.EZ_VCARD::read, text)).append(" |\n");
    }
    out.append('\n');
  }

  private static String timed(Function<String, Integer> read, String text) {
    ExecutorService pool = Executors.newSingleThreadExecutor();
    try {
      long t = System.nanoTime();
      var future = pool.submit(() -> read.apply(text));
      try {
        int cards = future.get(60, TimeUnit.SECONDS);
        return String.format(Locale.ROOT, "%,.0f ms, %,d cards", (System.nanoTime() - t) / 1e6, cards);
      } catch (TimeoutException e) {
        future.cancel(true);
        return "**over 60 s**";
      } catch (java.util.concurrent.ExecutionException e) {
        return "**" + e.getCause().getClass().getSimpleName() + "**";
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return "interrupted";
    } finally {
      pool.shutdownNow();
    }
  }

  void broken() {
    out.append("## Broken text\n\n`HostileTest`'s fuzzing: every sample broken ").append(HostileTest.TRIES)
        .append(" times each way, read as text. A failure is any exception but this reader's own `VcardParseException`"
            + " (\"not vCard\") or ez-vcard's `IOException`.\n\n");
    int tries = 0;
    var oursFailed = new ArrayList<String>();
    var ezFailed = new java.util.TreeMap<String, Integer>();
    for (var s : Corpus.Sample.values()) {
      for (var m : HostileTest.Mutation.values()) {
        var random = new Random((s.name() + m.name()).hashCode());
        var original = s.text().getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < HostileTest.TRIES; i++) {
          var text = new String(m.apply(original, random), StandardCharsets.UTF_8);
          tries++;
          try {
            OURS.read(text);
          } catch (com.snaphop.vcard.VcardParseException e) {
            // Not vCard at all.
          } catch (RuntimeException | StackOverflowError e) {
            oursFailed.add(e.toString());
          }
          try {
            Reader.ez(text);
          } catch (UncheckedIOException e) {
            // Its reader's own failure.
          } catch (RuntimeException | StackOverflowError e) {
            ezFailed.merge(e.getClass().getSimpleName(), 1, Integer::sum);
          }
        }
      }
    }
    out.append(String.format(Locale.ROOT, "%,d broken texts: snaphop-vcard failed on %d; ez-vcard on %d", tries, oursFailed.size(),
        ezFailed.values().stream().mapToInt(Integer::intValue).sum()));
    if (!ezFailed.isEmpty()) out.append(" (").append(ezFailed.toString().replaceAll("[{}]", "").replace("=", ": ")).append(")");
    out.append(".\n");
  }
}

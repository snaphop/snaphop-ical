package com.snaphop.ical.test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.snaphop.ical.IcalReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.fortuna.ical4j.data.CalendarBuilder;
import net.fortuna.ical4j.data.ParserException;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.VEvent;
import net.fortuna.ical4j.model.property.Summary;
import net.fortuna.ical4j.model.property.Uid;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/// Reads sample calendars with ical4j, the oracle, and with our reader.
class SampleCalendarTest {

  enum Sample {
    FAMILY(
        "family.ics",
        """
        standup@example.com Stand-up
        standup@example.com Stand-up (moved)
        trash@example.com Trash day
        dentist@example.com Dentist
        """);

    final String file;
    final String events;

    Sample(String file, String events) {
      this.file = file;
      this.events = events;
    }

    Reader open() {
      InputStream stream =
          Objects.requireNonNull(
              SampleCalendarTest.class.getResourceAsStream(file), "missing resource " + file);
      return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
  }

  @ParameterizedTest
  @EnumSource(Sample.class)
  void ical4jReadsSample(Sample sample) throws IOException, ParserException {
    assertEquals(sample.events, ical4jEvents(sample));
  }

  @Disabled("IcalReader does not read yet")
  @ParameterizedTest
  @EnumSource(Sample.class)
  void readerMatchesIcal4j(Sample sample) throws IOException, ParserException {
    var reader = IcalReader.builder().zone(java.time.ZoneId.of("America/New_York")).mode(IcalReader.Mode.STRICT).build();
    String actual;
    try (var in = sample.open()) {
      actual = lines(reader.read(in).events().stream().map(e -> e.uid() + " " + e.summary()));
    }
    assertEquals(ical4jEvents(sample), actual);
  }

  private static String ical4jEvents(Sample sample) throws IOException, ParserException {
    try (var in = sample.open()) {
      List<VEvent> events = new CalendarBuilder().build(in).getComponents(Component.VEVENT);
      return lines(events.stream().map(e -> uid(e) + " " + summary(e)));
    }
  }

  private static String uid(VEvent event) {
    return event.getUid().map(Uid::getValue).orElse("");
  }

  private static String summary(VEvent event) {
    Summary summary = event.getSummary();
    return summary == null ? "" : summary.getValue();
  }

  private static String lines(Stream<String> lines) {
    return lines.map(line -> line + "\n").collect(Collectors.joining());
  }
}

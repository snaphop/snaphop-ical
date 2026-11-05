package com.snaphop.ical.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.snaphop.ical.IcalReader;
import org.junit.jupiter.api.Test;

/// What a reader refuses to be built with, and why, in full.
class ReaderConfigTest {

  @Test
  void aReaderNeedsAZone() {
    var e = assertThrows(IllegalStateException.class, () -> IcalReader.builder().build());
    assertEquals("""
        An IcalReader needs a zone: call zone(ZoneId) with the zone the calendar is read for.""",
        e.getMessage());
  }
}

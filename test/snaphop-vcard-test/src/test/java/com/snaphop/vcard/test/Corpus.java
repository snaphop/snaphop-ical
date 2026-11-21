package com.snaphop.vcard.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/// Every vCard file the tests read: those of ez-vcard's and ICAL.js's own
/// tests (`test/corpus`), and samples written for this project, as phones
/// and apps export them.
final class Corpus {

  private Corpus() {}

  /// `test/corpus`, from the module's directory, where Maven runs the tests.
  static final Path ROOT = Path.of("../corpus");

  enum Sample {
    EZ_FULLCONTACT("ez-vcard/fullcontact.vcf"),
    EZ_GMAIL_LIST("ez-vcard/gmail-list.vcf"),
    EZ_GMAIL_SINGLE("ez-vcard/gmail-single.vcf"),
    EZ_GMAIL_SINGLE2("ez-vcard/gmail-single2.vcf"),
    EZ_ISSUE114("ez-vcard/issue114.vcf"),
    EZ_ANDROID("ez-vcard/John_Doe_ANDROID.vcf"),
    EZ_BLACK_BERRY("ez-vcard/John_Doe_BLACK_BERRY.vcf"),
    EZ_EVOLUTION("ez-vcard/John_Doe_EVOLUTION.vcf"),
    EZ_GMAIL("ez-vcard/John_Doe_GMAIL.vcf"),
    EZ_IPHONE("ez-vcard/John_Doe_IPHONE.vcf"),
    EZ_LOTUS_NOTES("ez-vcard/John_Doe_LOTUS_NOTES.vcf"),
    EZ_MAC_ADDRESS_BOOK("ez-vcard/John_Doe_MAC_ADDRESS_BOOK.vcf"),
    EZ_MS_OUTLOOK("ez-vcard/John_Doe_MS_OUTLOOK.vcf"),
    EZ_OUTLOOK_2003("ez-vcard/outlook-2003.vcf"),
    EZ_OUTLOOK_2007("ez-vcard/outlook-2007.vcf"),
    EZ_RFC2426("ez-vcard/rfc2426-example.vcf"),
    EZ_RFC6350("ez-vcard/rfc6350-example.vcf"),
    EZ_THUNDERBIRD("ez-vcard/thunderbird-MoreFunctionsForAddressBook-extension.vcf"),
    ICALJS_ESCAPE_SEMICOLON("icaljs/parser/escape_semicolon.vcf"),
    ICALJS_VCARD("icaljs/parser/vcard.vcf"),
    ICALJS_VCARD3("icaljs/parser/vcard3.vcf"),
    ICALJS_VCARD_AUTHOR("icaljs/parser/vcard_author.vcf"),
    ICALJS_VCARD_GROUPED("icaljs/parser/vcard_grouped.vcf"),
    /// An iPhone's export: Apple's labels, related names, a birthday without
    /// its year, an anniversary, a company and a group.
    APPLE("apple.vcf"),
    /// Google Contacts' export (vCard 3.0).
    GOOGLE("google.vcf"),
    /// An older Android phone's export: vCard 2.1, quoted-printable UTF-8,
    /// bare parameters, a photo ended by a blank line.
    ANDROID("android.vcf"),
    /// Outlook's vCard 2.1, in Windows-1252 bytes, not UTF-8.
    OUTLOOK("outlook.vcf", Charset.forName("windows-1252")),
    /// vCard 4.0 as RFC 6350 writes it: a group with members, relations,
    /// `tel:` URIs, dates without years, RFC 6868's escapes.
    VCARD4("vcard4.vcf");

    final String path;
    final Charset charset;

    Sample(String path) {
      this(path, StandardCharsets.UTF_8);
    }

    Sample(String path, Charset charset) {
      this.path = path;
      this.charset = charset;
    }

    boolean own() {
      return !path.contains("/");
    }

    byte[] bytes() {
      try {
        if (own()) {
          try (var in = Objects.requireNonNull(Corpus.class.getResourceAsStream(path), path)) {
            return in.readAllBytes();
          }
        }
        return Files.readAllBytes(ROOT.resolve(path));
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    String text() {
      var s = new String(bytes(), charset);
      return s.startsWith("﻿") ? s.substring(1) : s;
    }
  }
}

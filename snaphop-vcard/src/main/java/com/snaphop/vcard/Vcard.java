package com.snaphop.vcard;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.MonthDay;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/// One contact: a `VCARD`, as read by a [VcardReader].
///
/// The values a family's address book holds are read into plain types, the
/// same whichever version of vCard wrote them and whichever app: names,
/// emails, phone numbers, addresses, birthdays, the people related to them.
/// Apple's ways of writing some of them are read as what they mean:
///
/// - a label typed for a value (`item1.X-ABLabel:Grandma's`) is its [Value#label()];
/// - a birthday without its year (`BDAY;X-APPLE-OMIT-YEAR=1604:1604-05-03`)
///   has no [Day#yearOrNull()];
/// - related names (`X-ABRELATEDNAMES`) are [#related()];
/// - a group (`X-ADDRESSBOOKSERVER-KIND:group`) is [Kind#GROUP].
///
/// Text is never null: what a card does not have is `""` or an empty list.
/// Values are stripped of white space at both ends, and empty ones left out.
/// Everything else the card has is in [#properties()].
///
/// A card's `toString()` gives only its version and line, never its text,
/// so that one logged by mistake does not log someone's details.
public sealed interface Vcard {

  /// The version of vCard the card says it is.
  ///
  /// @return the version, or [Version#V3_0] when it says none it knows
  Version version();

  /// Where the card starts.
  ///
  /// @return the line of its `BEGIN:VCARD`, counting from 1, as written
  ///         before folded lines are joined
  int line();

  /// The card's own id, to know it again in a later export.
  ///
  /// @return `UID`, else Apple's `X-ABUID`, else `""`
  String uid();

  /// The name to show, as the card formats it.
  ///
  /// @return `FN`, or `""`
  String formattedName();

  /// The name in parts.
  ///
  /// @return `N`, with each part `""` when the card has none
  Name name();

  /// Nicknames.
  ///
  /// @return each value of each `NICKNAME`, in order
  List<String> nicknames();

  /// The organization, then its units: `IBM`, `Accounting`.
  ///
  /// @return the first `ORG`'s parts, without empty ones
  List<String> organization();

  /// Job titles.
  ///
  /// @return each `TITLE`, in order
  List<String> titles();

  /// The birthday.
  ///
  /// A `BDAY` that is text (`VALUE=text`), or less than a day (a year, a
  /// month, a time), is no birthday here, though it stays in
  /// [#properties()]. One that is not a date at all (`2014-02-30`) is
  /// reported.
  ///
  /// @return the first `BDAY` that is a day of the year, or null
  @Nullable Day birthdayOrNull();

  /// The wedding anniversary.
  ///
  /// @return vCard 4's `ANNIVERSARY`, else as Apple, Outlook, Evolution or
  ///         Thunderbird write one; or null
  @Nullable Day anniversaryOrNull();

  /// Email addresses.
  ///
  /// @return each `EMAIL`, in order
  List<Value> emails();

  /// Phone numbers.
  ///
  /// @return each `TEL`, in order: a `tel:` URI as its number, with any
  ///         `;ext=` as ` ext. `
  List<Value> phones();

  /// Postal addresses.
  ///
  /// @return each `ADR` that has any part, in order
  List<Address> addresses();

  /// Web addresses.
  ///
  /// @return each `URL`, in order
  List<Value> urls();

  /// People related to them (a spouse, a child, an assistant), by name or URI.
  ///
  /// @return vCard 4's `RELATED`, Apple's `X-ABRELATEDNAMES`, and the
  ///         spouse, manager and assistant as Outlook and Evolution write
  ///         them, with the relation as a type (`spouse`), in order
  List<Value> related();

  /// Notes.
  ///
  /// @return each `NOTE`, in order
  List<String> notes();

  /// The groups the card is in, as its own text names them.
  ///
  /// @return each value of each `CATEGORIES`, in order, each once
  List<String> categories();

  /// What the card is of.
  ///
  /// @return vCard 4's `KIND`, else Apple's `X-ADDRESSBOOKSERVER-KIND` and
  ///         `X-ABShowAs:COMPANY`, else [Kind#INDIVIDUAL]
  Kind kind();

  /// The members of a group: URIs, usually `urn:uuid:` and a card's `UID`.
  ///
  /// @return vCard 4's `MEMBER`, else Apple's `X-ADDRESSBOOKSERVER-MEMBER`, in order
  List<String> members();

  /// Every property of the card, as written, but for photos, logos, sounds
  /// and keys: those are passed over without being kept.
  ///
  /// @return the properties, in order
  List<Property> properties();

  /// The versions of vCard.
  enum Version {
    /// vCard 2.1 (versit, 1996): older Android phones, Outlook, BlackBerry.
    V2_1("2.1"),
    /// vCard 3.0 (RFC 2426): Apple, Google, most apps.
    V3_0("3.0"),
    /// vCard 4.0 (RFC 6350).
    V4_0("4.0");

    private final String text;

    Version(String text) {
      this.text = text;
    }

    /// The version as `VERSION` writes it.
    ///
    /// @return `2.1`, `3.0` or `4.0`
    public String text() {
      return text;
    }
  }

  /// What a card is of (vCard 4's `KIND`).
  enum Kind {
    /// A person, as most cards are.
    INDIVIDUAL,
    /// A group of other cards: see [Vcard#members()].
    GROUP,
    /// A company or other organization.
    ORGANIZATION,
    /// A place.
    LOCATION,
    /// Some other kind, named in the card's `KIND`.
    OTHER
  }

  /// A name in parts (`N`). A part with more than one value has them joined
  /// by a space.
  ///
  /// @param family the family (last) name
  /// @param given the given (first) name
  /// @param additional middle names
  /// @param prefixes such as `Mr.`
  /// @param suffixes such as `Sr.`
  record Name(String family, String given, String additional, String prefixes, String suffixes) {

    /// A name with no parts.
    public static final Name EMPTY = new Name("", "", "", "", "");

    /// Checks the parts are not null.
    public Name {
      Objects.requireNonNull(family, "family");
      Objects.requireNonNull(given, "given");
      Objects.requireNonNull(additional, "additional");
      Objects.requireNonNull(prefixes, "prefixes");
      Objects.requireNonNull(suffixes, "suffixes");
    }
  }

  /// An email, phone number, web address or related person, with what it is
  /// called.
  ///
  /// @param value the address, number or name
  /// @param types its `TYPE`s in lower case, as written (`home`, `cell`,
  ///        `pref`), including vCard 2.1's bare ones (`TEL;HOME;VOICE`)
  /// @param label the label Apple writes for it (`item1.X-ABLabel`, which is
  ///        not vCard's `LABEL`): Apple's own words (`_$!<Mother>!$_`) in lower
  ///        case (`mother`), a typed one as typed (`Grandma's`); or `""`
  /// @param preferred whether it is the one to use first: `PREF=1`, or a `pref` type
  record Value(String value, List<String> types, String label, boolean preferred) {

    /// Copies the types.
    public Value {
      Objects.requireNonNull(value, "value");
      types = List.copyOf(types);
      Objects.requireNonNull(label, "label");
    }
  }

  /// A postal address (`ADR`). A part with more than one value has them
  /// joined by a newline, as a street of several lines is.
  ///
  /// @param poBox the post office box
  /// @param extended the apartment or suite
  /// @param street the street address
  /// @param locality the city or town
  /// @param region the state or province
  /// @param postalCode the ZIP or postal code
  /// @param country the country
  /// @param types its `TYPE`s in lower case, as for [Value#types()]
  /// @param label the label Apple writes for it, as for [Value#label()]
  /// @param preferred whether it is the one to use first, as for [Value#preferred()]
  record Address(String poBox, String extended, String street, String locality, String region, String postalCode,
      String country, List<String> types, String label, boolean preferred) {

    /// Copies the types.
    public Address {
      Objects.requireNonNull(poBox, "poBox");
      Objects.requireNonNull(extended, "extended");
      Objects.requireNonNull(street, "street");
      Objects.requireNonNull(locality, "locality");
      Objects.requireNonNull(region, "region");
      Objects.requireNonNull(postalCode, "postalCode");
      Objects.requireNonNull(country, "country");
      types = List.copyOf(types);
      Objects.requireNonNull(label, "label");
    }
  }

  /// A day of the year, with its year when it is known: a birthday or an
  /// anniversary.
  ///
  /// @param monthDay the month and day; February 29 only with a leap year, or no year
  /// @param yearOrNull the year, or null when the card leaves it out
  record Day(MonthDay monthDay, @Nullable Integer yearOrNull) {

    /// Checks the day is in its year.
    ///
    /// @throws DateTimeException if the year has no such day
    public Day {
      Objects.requireNonNull(monthDay, "monthDay");
      if (yearOrNull != null && !monthDay.isValidYear(yearOrNull)) {
        throw new DateTimeException(monthDay + " is not a day of " + yearOrNull);
      }
    }

    /// The day as a date.
    ///
    /// @return the date, or null when the year is not known
    public @Nullable LocalDate dateOrNull() {
      return yearOrNull == null ? null : monthDay.atYear(yearOrNull);
    }

    /// The day as ISO 8601 and vCard 4 write it.
    ///
    /// @return `2012-06-06`, or `--06-06` without the year
    @Override
    public String toString() {
      var d = dateOrNull();
      return d != null ? d.toString() : monthDay.toString();
    }
  }

  /// A property as written, after folded lines are joined and a
  /// quoted-printable value is decoded.
  ///
  /// @param group its group, such as `item1` in `item1.TEL`, or `""`
  /// @param name its name in upper case, such as `TEL`
  /// @param parameters its parameters, by name in upper case. vCard 2.1's
  ///        bare ones are under the name they stand for: `HOME` is a `TYPE`,
  ///        `QUOTED-PRINTABLE` an `ENCODING`
  /// @param value its value, with its escapes (`\,`) still in it
  /// @param line the line it starts on, counting from 1
  record Property(String group, String name, Map<String, List<String>> parameters, String value, int line) {

    /// Copies the parameters.
    public Property {
      Objects.requireNonNull(group, "group");
      Objects.requireNonNull(name, "name");
      var copy = new java.util.LinkedHashMap<String, List<String>>();
      parameters.forEach((k, v) -> copy.put(k, List.copyOf(v)));
      parameters = java.util.Collections.unmodifiableMap(copy);
      Objects.requireNonNull(value, "value");
    }

    /// The values of one parameter.
    ///
    /// @param name the parameter's name, in any case
    /// @return its values, or an empty list
    public List<String> parameter(String name) {
      var values = parameters.get(name.toUpperCase(java.util.Locale.ROOT));
      return values == null ? List.of() : values;
    }

    /// The value as text: its escapes undone (`\n` as a newline, and a
    /// backslash before anything else as that character: `\,` is a comma),
    /// and its line breaks as `\n`.
    ///
    /// @return the text
    public String text() {
      return Text.unescape(value);
    }
  }
}

record DefaultVcard(
    Vcard.Version version,
    int line,
    String uid,
    String formattedName,
    Vcard.Name name,
    List<String> nicknames,
    List<String> organization,
    List<String> titles,
    Vcard.@Nullable Day birthdayOrNull,
    Vcard.@Nullable Day anniversaryOrNull,
    List<Vcard.Value> emails,
    List<Vcard.Value> phones,
    List<Vcard.Address> addresses,
    List<Vcard.Value> urls,
    List<Vcard.Value> related,
    List<String> notes,
    List<String> categories,
    Vcard.Kind kind,
    List<String> members,
    List<Vcard.Property> properties) implements Vcard {

  DefaultVcard {
    nicknames = List.copyOf(nicknames);
    organization = List.copyOf(organization);
    titles = List.copyOf(titles);
    emails = List.copyOf(emails);
    phones = List.copyOf(phones);
    addresses = List.copyOf(addresses);
    urls = List.copyOf(urls);
    related = List.copyOf(related);
    notes = List.copyOf(notes);
    categories = List.copyOf(categories);
    members = List.copyOf(members);
    properties = List.copyOf(properties);
  }

  /// Only the version and line: a card's text is someone's personal data,
  /// and a `toString` ends up in logs.
  @Override
  public String toString() {
    return "Vcard[" + version.text() + ", line " + line + "]";
  }
}

# ez-vcard's test files

| | |
|---|---|
| Repository | https://github.com/mangstadt/ez-vcard |
| Commit | `421dce83fc4d9fc0e7d4584ca5ff7dc0cd66c9b7` |
| License | BSD 2-Clause ([`LICENSE`](LICENSE)) |

| Here | Original path | Changes |
|------|---------------|---------|
| `*.vcf` but `issue114.vcf` | `src/test/resources/ezvcard/io/text/` | None |
| `issue114.vcf` | `src/test/resources/ezvcard/issue/` | None |

vCards as phones, mail programs and address books exported them: an
iPhone, a Mac, Android, BlackBerry, Gmail, Outlook 2003 and 2007, Lotus
Notes, Evolution, Thunderbird, FullContact, and the examples of RFC 2426 and
RFC 6350. Their line endings are kept as they were (`.gitattributes`), CR CR
LF included. The test module reads them by path, with ez-vcard 0.12.2 as an
oracle.

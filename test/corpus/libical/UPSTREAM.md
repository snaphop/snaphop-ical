# libical recurrence specifications

| | |
|---|---|
| Repository | https://github.com/libical/libical |
| Commit | `38b952750a89293f14866562d5e13dd07edfcf11` |
| Original path | `src/test/icalrecur_test.txt` |
| License | libical's `REUSE.toml` gives `src/test/**.txt` the expression `LGPL-2.1-only OR MPL-2.0`; it is used here under MPL-2.0 ([`LICENSE-MPL-2.0.txt`](LICENSE-MPL-2.0.txt)) |
| Changes | None: the file is as it was |

Each record is an `RRULE`, a `DTSTART`, sometimes a `START-AT` (where
libical's iterator starts looking), and the `INSTANCES` it expects. libical's
own runner is `src/test/icalrecur_test.c` at the same commit.

`LibicalRecurrenceTest` reads each record as the event it describes, through
the public reader. The records it reads differently from libical are listed
in that test, each with why.

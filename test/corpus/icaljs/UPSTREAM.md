# ICAL.js tests and code

| | |
|---|---|
| Repository | https://github.com/kewisch/ical.js |
| Commit | `127dcf260a8eb8dddd5050f0a9b2fc4a92e3932e` |
| License | MPL-2.0 ([`LICENSE`](LICENSE)) |

| Here | Original path | Changes |
|------|---------------|---------|
| `lib/*.js` | `lib/ical/` | None. The tests run it on GraalJS as an oracle |
| `parser/*.ics`, `parser/*.json` | `test/parser/` | None. Only the iCalendar pairs, not the vCard ones |
| `samples/*.ics` | `samples/` | None |
| `recur_iterator.txt` | `test/recur_iterator_test.js` | Derived: each `testRRULE` case written as a record like libical's `icalrecur_test.txt` |

`recur_iterator.txt` was made by [`extract-recur.js`](extract-recur.js),
which runs the test file with stand-ins for its test framework:

```
node extract-recur.js path/to/ical.js/test/recur_iterator_test.js > recur_iterator.txt
```

A record has the case's `RRULE`, its `DTSTART`, the `INSTANCES` it expects
and, when the test gives one, `MAX`: how many instances the test asked the
iterator for. A comment line before each names the suite and rule.

The code and the tests come from one commit, so the oracle is the code
those tests were written for: unreleased, newer than ICAL.js 2.2.1, the
latest release. To move to a later commit, copy `lib/ical/`, `samples/`
and `test/parser/` from it again, and make `recur_iterator.txt` again
with the command above.

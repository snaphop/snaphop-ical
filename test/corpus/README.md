# Test corpora from other projects

Test data copied from other iCalendar implementations, and the ICAL.js code
the tests run as an oracle. Each directory keeps
its project's license and an `UPSTREAM.md` saying where its files came from
and what was changed. None of it is part of a published jar: only the test
module reads it.

| Directory | From | License |
|-----------|------|---------|
| [`libical`](libical) | [libical](https://github.com/libical/libical) | MPL-2.0 (libical offers `LGPL-2.1-only OR MPL-2.0`) |
| [`icaljs`](icaljs) | [ICAL.js](https://github.com/kewisch/ical.js) | MPL-2.0 |

Data written for this project lives in the test module's own resources.

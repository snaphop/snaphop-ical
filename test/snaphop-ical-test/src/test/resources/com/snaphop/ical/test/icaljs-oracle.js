// Reads a calendar with ICAL.js, for IcaljsOracle.
//
// occurrences lists its occurrences up to a time. Changed times
// (RECURRENCE-ID) are listed on their own, so one moved into the window from
// outside it is found; the Java side picks what is in the window and leaves
// out what is cancelled.
//
// events lists its events as read.
//
// zones: VTIMEZONE text for zones the calendar names but doesn't define.
// untilUnix: stop expanding a rule after this time (seconds).
(function () {
// A time is floating when it has no zone, or names one ICAL.js doesn't know.
const time = (t) => t ? {
  date: t.isDate,
  floating: !t.zone || t.zone === ICAL.Timezone.localTimezone,
  local: t.toString(),
  unix: t.toUnixTime(),
} : null;

function read(text, zones) {
  for (const z of zones) {
    const vtz = new ICAL.Component(ICAL.parse(z)).getFirstSubcomponent('vtimezone');
    ICAL.TimezoneService.register(vtz);
  }
  let parsed = ICAL.parse(text);
  // One VCALENDAR is a component; more are a list of them.
  const roots = typeof parsed[0] === 'string' ? [parsed] : parsed;
  const events = [];
  for (const r of roots) {
    const root = new ICAL.Component(r);
    for (const vtz of root.getAllSubcomponents('vtimezone')) {
      const tzid = vtz.getFirstPropertyValue('tzid');
      if (tzid && !ICAL.TimezoneService.has(tzid)) ICAL.TimezoneService.register(vtz);
    }
    events.push(...root.getAllSubcomponents('vevent'));
  }
  return events;
}

function eventList(text, zones) {
  return read(text, zones).map((v) => {
    const e = new ICAL.Event(v);
    return {uid: String(e.uid || ''), summary: String(e.summary || ''), description: String(e.description || ''),
      location: String(e.location || ''), status: String(v.getFirstPropertyValue('status') || ''),
      start: time(e.startDate), end: time(e.endDate), rid: time(e.recurrenceId)};
  });
}

function occurrences(text, zones, untilUnix) {
  const events = read(text, zones);
  const masters = new Map();
  const changes = [];
  for (const v of events) {
    const uid = v.getFirstPropertyValue('uid');
    if (!uid) continue;
    const e = new ICAL.Event(v);
    if (e.isRecurrenceException()) changes.push(e);
    else if (!masters.has(uid)) masters.set(uid, e);
  }
  for (const c of changes) {
    const m = masters.get(c.uid);
    if (m) m.relateException(c);
  }
  const status = (e) => String(e.component.getFirstPropertyValue('status') || '');
  const rows = [];
  for (const m of masters.values()) {
    if (m.isRecurring()) {
      const it = m.iterator();
      let next;
      let n = 0;
      while ((next = it.next()) && n++ < 200000) {
        if (next.toUnixTime() > untilUnix) break;
        const d = m.getOccurrenceDetails(next);
        if (d.item !== m) continue;
        rows.push({uid: m.uid, summary: m.summary || '', status: status(m), start: time(d.startDate),
          end: time(d.endDate), rid: time(d.recurrenceId), repeats: true});
      }
    } else {
      rows.push({uid: m.uid, summary: m.summary || '', status: status(m), start: time(m.startDate),
        end: time(m.endDate), rid: time(m.startDate), repeats: false});
    }
  }
  for (const c of changes) {
    const m = masters.get(c.uid);
    rows.push({uid: c.uid, summary: c.summary || '', status: status(c), start: time(c.startDate),
      end: time(c.endDate), rid: time(c.recurrenceId), repeats: !m || m.isRecurring()});
  }
  return rows;
}

return {occurrences, events: eventList};
})()

// Runs ical.js's recur_iterator_test.js with stand-ins for its test
// framework, and writes each testRRULE case as a record like libical's
// icalrecur_test.txt. Usage: node extract-icaljs-recur.js <test file>
const fs = require('fs');
const vm = require('vm');
const file = process.argv[2];
const source = fs.readFileSync(file, 'utf8');
const suites = [];
const out = [];
function basic(t) {
  // 2012-09-01T09:00:00 -> 20120901T090000 ; 2012-09-01 -> 20120901
  return t.replace(/-/g, '').replace(/:/g, '');
}
const ctx = {
  suite(name, fn) { suites.push(name); fn(); suites.pop(); },
  setup() {}, teardown() {}, suiteSetup() {},
  test(name, fn) {
    // testRRULE registers a test; run it with a recording ICAL stand-in.
    if (!fn || !fn.__rrule) return;
  },
  assert: {},
};
ctx.test.only = ctx.test;
ctx.suite.only = ctx.suite;
// testRRULE is defined inside the file; replace its definition.
const patched = source.replace(/function testRRULE\(ruleString, options\) \{[\s\S]*?\n  \}\n  testRRULE\.only/, 
  'function testRRULE(ruleString, options) { __record(ruleString, options); }\n  testRRULE.only');
if (patched === source) throw new Error('testRRULE not found');
ctx.__record = (rule, o) => {
  const dates = o.dates || [];
  const dtStart = o.dtStart || dates[0];
  out.push('# ' + suites.slice(1).join(' / ') + ': ' + rule);
  out.push('RRULE:' + rule);
  out.push('DTSTART:' + basic(dtStart));
  if ('max' in o) out.push('MAX:' + o.max);
  if (o.noInstance) out.push('NO-INSTANCE:TRUE');
  out.push('INSTANCES:' + dates.map(basic).join(','));
  out.push('');
};
vm.runInNewContext(patched, ctx, { filename: file });
process.stdout.write(out.join('\n'));

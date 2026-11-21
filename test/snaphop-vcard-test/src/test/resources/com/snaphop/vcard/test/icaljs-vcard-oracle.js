// Reads vCards with ICAL.js, for IcaljsOracle: each card's properties as
// ICAL.js parses them (jCard), in plain arrays the Java side can walk.
//
// A property is {name, group, types, values}: its name in lower case, its
// group or '', its TYPE values, and its values, each a string or, for a
// structured value (N, ADR), an array of parts, each a string or an array.
(function () {
const str = (v) => Array.isArray(v) ? v.map(str) : String(v);

function read(text) {
  let parsed = ICAL.parse(text);
  // One VCARD is a component; more are a list of them.
  const roots = typeof parsed[0] === 'string' ? [parsed] : parsed;
  return roots.map((r) => r[1].map((p) => {
    const params = p[1] || {};
    let types = params.type === undefined ? [] : Array.isArray(params.type) ? params.type : [params.type];
    return {name: p[0], group: params.group ? String(params.group) : '', types: types.map(String),
      values: p.slice(3).map(str)};
  }));
}

return {read};
})()

// FORK (specs/fork-health): what comes back from Health Connect, and how it lands in the state.
// Pure: the plugin call and the store update live in health-read-sync.js.
//
// Rules (requirements HR-1/HR-2):
//   - nothing openGym wrote itself comes back (origin package, or an "opengym-" client id);
//   - one weight per local day, the latest reading of that day;
//   - what the owner typed in openGym always wins: a weigh-in or a body-fat value already there
//     is never overwritten. A weigh-in brought from Health Connect carries src: 'hc' and may be
//     replaced by a later reading of the same day.
import { isoOf } from '../lib/format.js'
import { emptyMeasurement } from '../lib/measurements.js'

export const OWN_PACKAGES = ['com.cezar.opengym', 'com.cezar.opengym.test', 'ch.duartesantos.opengym']
const LB_PER_KG = 2.2046226218
const round1 = n => Math.round(n * 10) / 10

/** The records that did not come from openGym. */
export function foreign(records, own = OWN_PACKAGES) {
  return (records || []).filter(r => r && !own.includes(r.origin) && !String(r.clientId || '').startsWith('opengym-'))
}

/** Keeps the latest record of each local day: [{ d, t, ...pick(record) }], oldest day first. */
function latestPerDay(records, valid, pick) {
  const byDay = new Map()
  for (const r of records || []) {
    if (!(Number(r?.t) > 0) || !valid(r)) continue
    const d = isoOf(new Date(r.t))
    const cur = byDay.get(d)
    if (!cur || r.t >= cur.t) byDay.set(d, { d, t: r.t, ...pick(r) })
  }
  return [...byDay.values()].sort((a, b) => (a.d < b.d ? -1 : 1))
}

/** Weight readings → one weigh-in per day in the profile's unit, marked as from Health Connect. */
export function dailyWeights(weights, { unit = 'kg' } = {}) {
  return latestPerDay(weights, r => Number(r.kg) > 0 && Number(r.kg) < 500,
    r => ({ w: round1(unit === 'lb' ? r.kg * LB_PER_KG : r.kg), src: 'hc' }))
}

/** Body-fat readings → one percentage per day. */
export function dailyBodyFat(records) {
  return latestPerDay(records, r => Number(r.pct) > 0 && Number(r.pct) < 100, r => ({ pct: round1(r.pct) }))
}

/** Weigh-ins into S.bodyweight (mutates S). Returns { added, updated, skipped }. */
export function mergeWeights(S, entries) {
  S.bodyweight = Array.isArray(S.bodyweight) ? S.bodyweight : []
  const out = { added: 0, updated: 0, skipped: 0 }
  for (const e of entries || []) {
    const i = S.bodyweight.findIndex(b => b.d === e.d)
    if (i < 0) { S.bodyweight.push({ d: e.d, w: e.w, t: e.t, src: 'hc' }); out.added++; continue }
    const cur = S.bodyweight[i]
    if (cur.src !== 'hc') { out.skipped++; continue }          // typed in openGym: it stays
    if (cur.w === e.w && cur.t >= e.t) { out.skipped++; continue }
    S.bodyweight[i] = { d: e.d, w: e.w, t: e.t, src: 'hc' }
    out.updated++
  }
  S.bodyweight.sort((a, b) => (a.d < b.d ? -1 : 1))
  return out
}

/** Body fat into the day's measurement, only where none was entered (mutates S). */
export function mergeBodyFat(S, daily) {
  S.measurements = Array.isArray(S.measurements) ? S.measurements : []
  const out = { added: 0, skipped: 0 }
  for (const e of daily || []) {
    const m = S.measurements.find(x => x.d === e.d)
    if (m && m.bodyFat != null) { out.skipped++; continue }
    if (m) m.bodyFat = e.pct
    else S.measurements.push({ ...emptyMeasurement(e.d, S.customMeasurements || []), t: e.t, bodyFat: e.pct })
    out.added++
  }
  if (out.added) {
    S.measurements.sort((a, b) => (a.d < b.d ? -1 : 1))
    // The field has to be on for the value to show (Measurements → which fields).
    if (Array.isArray(S.measurementEnabled) && !S.measurementEnabled.includes('bodyFat')) S.measurementEnabled = [...S.measurementEnabled, 'bodyFat']
  }
  return out
}

/** Heart-rate samples [[t, bpm], …] → { avg, max, n }, or null with too few to mean anything. */
export function summarizeHr(samples) {
  const bpm = (samples || []).map(s => Number(s?.[1])).filter(b => b >= 30 && b <= 230)
  if (bpm.length < 3) return null
  const sum = bpm.reduce((a, b) => a + b, 0)
  return { avg: Math.round(sum / bpm.length), max: Math.max(...bpm), n: bpm.length }
}

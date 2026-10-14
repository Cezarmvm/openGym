import { describe, it, expect } from 'vitest'
import { foreign, dailyWeights, dailyBodyFat, mergeWeights, mergeBodyFat, summarizeHr } from './health-read.js'

const at = (d, h, m = 0) => new Date(2026, 9, d, h, m).getTime()   // October 2026, local time
const w = (t, kg, origin = 'com.sec.android.app.shealth', clientId = '') => ({ t, kg, origin, clientId })

describe('foreign', () => {
  it('drops what openGym wrote itself, by package or by client id', () => {
    const recs = [w(at(1, 8), 66), w(at(2, 8), 67, 'com.cezar.opengym'), w(at(3, 8), 68, 'x', 'opengym-bw-2026-10-03'), w(at(4, 8), 69, 'ch.duartesantos.opengym')]
    expect(foreign(recs).map(r => r.kg)).toEqual([66])
  })
})

describe('dailyWeights', () => {
  it('keeps the latest reading of each local day, oldest day first, marked as from Health Connect', () => {
    const out = dailyWeights([w(at(10, 21), 67.2), w(at(10, 7), 66.4), w(at(9, 8), 66.6)])
    expect(out).toEqual([
      { d: '2026-10-09', t: at(9, 8), w: 66.6, src: 'hc' },
      { d: '2026-10-10', t: at(10, 21), w: 67.2, src: 'hc' },
    ])
  })

  it('converts to pounds on a lb profile and drops nonsense', () => {
    const out = dailyWeights([w(at(5, 8), 70), w(at(6, 8), 0), w(at(7, 8), 900)], { unit: 'lb' })
    expect(out).toEqual([{ d: '2026-10-05', t: at(5, 8), w: 154.3, src: 'hc' }])
  })
})

describe('mergeWeights', () => {
  it('adds days openGym does not have', () => {
    const S = { bodyweight: [{ d: '2026-10-01', w: 66, t: at(1, 8) }] }
    const r = mergeWeights(S, dailyWeights([w(at(3, 8), 66.8)]))
    expect(r).toEqual({ added: 1, updated: 0, skipped: 0 })
    expect(S.bodyweight.map(b => b.d)).toEqual(['2026-10-01', '2026-10-03'])
  })

  it('never overwrites a weigh-in typed in openGym', () => {
    const S = { bodyweight: [{ d: '2026-10-09', w: 66.6, t: at(9, 9) }] }
    expect(mergeWeights(S, dailyWeights([w(at(9, 20), 67.5)]))).toEqual({ added: 0, updated: 0, skipped: 1 })
    expect(S.bodyweight[0].w).toBe(66.6)
  })

  it('updates a weigh-in it brought earlier with a later reading of the same day', () => {
    const S = { bodyweight: [{ d: '2026-10-11', w: 66.9, t: at(11, 7), src: 'hc' }] }
    expect(mergeWeights(S, dailyWeights([w(at(11, 22), 67.4)]))).toEqual({ added: 0, updated: 1, skipped: 0 })
    expect(S.bodyweight[0]).toEqual({ d: '2026-10-11', w: 67.4, t: at(11, 22), src: 'hc' })
  })

  it('is a no-op on the same reading twice', () => {
    const S = { bodyweight: [] }
    const daily = dailyWeights([w(at(12, 8), 66)])
    mergeWeights(S, daily)
    expect(mergeWeights(S, daily)).toEqual({ added: 0, updated: 0, skipped: 1 })
    expect(S.bodyweight).toHaveLength(1)
  })
})

describe('body fat', () => {
  const fat = (t, pct) => ({ t, pct, origin: 'com.sec.android.app.shealth', clientId: '' })

  it('fills the day of an existing measurement and creates one where there is none', () => {
    const S = { measurements: [{ d: '2026-10-09', t: at(9, 12), waist: 72, bodyFat: null, other: [] }], measurementEnabled: ['waist'], customMeasurements: [] }
    const r = mergeBodyFat(S, dailyBodyFat([fat(at(9, 8), 12.04), fat(at(13, 8), 11.6)]))
    expect(r).toEqual({ added: 2, skipped: 0 })
    expect(S.measurements[0]).toMatchObject({ d: '2026-10-09', waist: 72, bodyFat: 12 })
    expect(S.measurements[1]).toMatchObject({ d: '2026-10-13', bodyFat: 11.6, waist: null })
    expect(S.measurementEnabled).toContain('bodyFat')
  })

  it('keeps a body fat entered in openGym', () => {
    const S = { measurements: [{ d: '2026-10-09', t: 1, bodyFat: 11.84, other: [] }] }
    expect(mergeBodyFat(S, dailyBodyFat([fat(at(9, 8), 13)]))).toEqual({ added: 0, skipped: 1 })
    expect(S.measurements[0].bodyFat).toBe(11.84)
  })
})

describe('summarizeHr', () => {
  it('averages the plausible samples', () => {
    expect(summarizeHr([[1, 100], [2, 120], [3, 140], [4, 250], [5, 10]])).toEqual({ avg: 120, max: 140, n: 3 })
  })
  it('says nothing with too few samples', () => {
    expect(summarizeHr([[1, 100], [2, 110]])).toBeNull()
    expect(summarizeHr(null)).toBeNull()
  })
})

import { describe, it, expect } from 'vitest'
import { onlySince } from './health-since.js'
import { planSync } from '../lib/health-connect.js'

const sess = (id, start) => ({ id: 'opengym-w-' + id, start, end: start + 3600e3, type: 'strength_training', title: id, notes: '' })
const records = {
  sessions: [sess('old', Date.UTC(2026, 2, 4, 18)), sess('new', Date.UTC(2026, 9, 15, 18))],
  weights: [{ id: 'opengym-bw-2026-10-09', time: Date.UTC(2026, 9, 9, 8), kg: 66.6 }],
}

describe('onlySince (Health Connect gets new workouts only)', () => {
  it('drops the sessions that started before Health Connect was turned on', () => {
    const out = onlySince(records, Date.UTC(2026, 9, 14))
    expect(out.sessions.map(s => s.id)).toEqual(['opengym-w-new'])
  })

  it('keeps every weigh-in', () => {
    expect(onlySince(records, Date.UTC(2026, 9, 14)).weights).toEqual(records.weights)
  })

  it('changes nothing without a start date (upstream behaviour)', () => {
    expect(onlySince(records, undefined)).toBe(records)
    expect(onlySince(records, 0)).toBe(records)
  })

  it('so the first sync after turning it on writes no old workout', () => {
    const plan = planSync(onlySince(records, Date.UTC(2026, 9, 14)), {})
    expect(plan.sessions.map(s => s.id)).toEqual(['opengym-w-new'])
    expect(plan.weights).toHaveLength(1)
  })
})

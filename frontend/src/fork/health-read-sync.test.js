import { describe, it, expect, beforeEach } from 'vitest'
import { createHealthRead, READ_FILE } from './health-read-sync.js'

const at = (d, h) => new Date(2026, 9, d, h).getTime()
const SH = 'com.sec.android.app.shealth'

let files, calls, S, plugin, clock, h
beforeEach(() => {
  files = new Map(); calls = []; clock = at(14, 9)
  S = { unit: 'kg', bodyweight: [{ d: '2026-10-09', w: 66.6, t: at(9, 9) }], measurements: [], measurementEnabled: ['waist'], customMeasurements: [] }
  plugin = {
    granted: { weight: true, bodyFat: true, history: true },
    data: {
      weights: [{ t: at(10, 7), kg: 66.9, origin: SH, clientId: '' }, { t: at(9, 8), kg: 99, origin: SH, clientId: '' }, { t: at(9, 8), kg: 66.6, origin: 'com.cezar.opengym', clientId: 'opengym-bw-2026-10-09' }],
      bodyFat: [{ t: at(10, 7), pct: 11.9, origin: SH, clientId: '' }],
    },
    status: async () => ({ status: 'available' }),
    requestPermissions: async a => { calls.push(['ask', a.kinds]); return { granted: plugin.granted } },
    readSince: async a => { calls.push(['read', a]); return plugin.data },
  }
  h = createHealthRead({
    plugin: async () => plugin,
    readJson: async n => (files.has(n) ? JSON.parse(files.get(n)) : null),
    writeJson: async (n, o) => { files.set(n, JSON.stringify(o)) },
    update: fn => fn(S),
    getState: () => S,
    now: () => clock,
  })
})

describe('off (the default)', () => {
  it('reads nothing and writes no file', async () => {
    await h.read()
    expect(calls).toEqual([])
    expect(files.has(READ_FILE)).toBe(false)
  })
})

describe('turning it on', () => {
  it('asks for weight, body fat and the history, then reads everything released', async () => {
    const r = await h.enable()
    expect(r.ok).toBe(true)
    expect(calls[0]).toEqual(['ask', ['weight', 'bodyFat', 'history']])
    expect(calls[1]).toEqual(['read', { kinds: ['weight', 'bodyFat'], since: 0 }])
    // the typed 66.6 on the 9th stays; the 10th comes in; openGym's own record is ignored
    expect(S.bodyweight).toEqual([{ d: '2026-10-09', w: 66.6, t: at(9, 9) }, { d: '2026-10-10', w: 66.9, t: at(10, 7), src: 'hc' }])
    expect(S.measurements[0]).toMatchObject({ d: '2026-10-10', bodyFat: 11.9 })
    expect(JSON.parse(files.get(READ_FILE))).toMatchObject({ on: true, lastRead: at(14, 9), lastCount: 2, error: null })
  })

  it('does not switch on when nothing was granted', async () => {
    plugin.granted = {}
    expect(await h.enable()).toEqual({ ok: false, reason: 'denied' })
    expect(calls.filter(c => c[0] === 'read')).toEqual([])
  })
})

describe('later reads', () => {
  it('ask only for what is new, with a day of overlap', async () => {
    await h.enable()
    clock = at(15, 9)
    await h.read()
    expect(calls.at(-1)).toEqual(['read', { kinds: ['weight', 'bodyFat'], since: at(13, 9) }])
  })

  it('keep a refused permission as an error without touching the log', async () => {
    await h.enable()
    plugin.readSince = async () => { throw Object.assign(new Error('no'), { code: 'permission' }) }
    const before = JSON.stringify(S)
    const st = await h.read()
    expect(st.error).toBe('permission')
    expect(JSON.stringify(S)).toBe(before)
  })

  it('stop after turning it off; what came in stays', async () => {
    await h.enable()
    await h.disable()
    const n = calls.length
    await h.read()
    expect(calls.length).toBe(n)
    expect(S.bodyweight).toHaveLength(2)
  })
})

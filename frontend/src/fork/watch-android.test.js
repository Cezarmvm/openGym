// @vitest-environment happy-dom
// FORK (specs/fork-watch M2.7): upstream's watch link (lib/watch-sync.js) on the Android phone app,
// through its own platform check, not the test seam: the snapshot goes out to the WatchBridge
// plugin (frontend/android/fork-wearbridge) and the watch's events come back, applied and then
// acknowledged only once saved.
import { beforeEach, describe, expect, it, vi } from 'vitest'

const plat = vi.hoisted(() => ({ name: 'android', bridge: null }))
vi.mock('../lib/mobile.js', async orig => ({ ...(await orig()), MOBILE: true, nativePlatform: async () => plat.name }))
vi.mock('@capacitor/core', async orig => ({ ...(await orig()), registerPlugin: name => (name === 'WatchBridge' ? plat.bridge : {}) }))
vi.mock('../lib/health-sync.js', () => ({ loadHealth: async () => ({ on: false }), onHealthChange: () => () => {} }))

const { startWatchSync, bindWatchActions, _resetWatchSync } = await import('../lib/watch-sync.js')

function bridge() {
  const listeners = {}
  return {
    listeners,
    status: vi.fn(async () => ({ supported: true, activated: true, paired: true, installed: true, reachable: true })),
    sendSnapshot: vi.fn(async () => ({ sent: true })),
    sendClips: vi.fn(async () => ({ queued: 0 })),
    dropClips: vi.fn(async () => {}),
    openOnWatch: vi.fn(async () => ({ ok: true })),
    drain: vi.fn(async () => ({ events: [] })),
    ack: vi.fn(async () => {}),
    addListener: vi.fn(async (name, cb) => { listeners[name] = cb; return { remove: () => {} } }),
  }
}

function store(initial) {
  let st = initial
  const subs = new Set()
  return {
    getState: () => st,
    setState: patch => { const prev = st; st = { ...st, ...patch }; subs.forEach(fn => fn(st, prev)) },
    subscribe: fn => { subs.add(fn); return () => subs.delete(fn) },
  }
}

const wait = ms => new Promise(r => setTimeout(r, ms))
const S0 = { unit: 'kg', restSec: 90, workouts: [], routines: [], active: null }
const active = {
  id: 'w-1', name: 'Treino A', cur: 0, d: '2026-10-15', start: Date.now(),
  entries: [{ id: '0576', target: { sets: 2, reps: 10, weight: 40 }, sets: [{ w: 40, r: 10, done: false }, { w: 40, r: 10, done: false }] }],
}

beforeEach(() => { localStorage.clear(); _resetWatchSync(); plat.name = 'android'; plat.bridge = bridge() })

describe('the watch link on Android', () => {
  it('sends the running workout to the Wear OS watch', async () => {
    const st = store({ S: S0, ready: true })
    const stop = await startWatchSync({ useStore: st, useUI: store({ timer: null }) })
    st.setState({ S: { ...S0, active } })
    await wait(600)
    const sent = plat.bridge.sendSnapshot.mock.calls.map(c => JSON.parse(c[0].json))
    expect(sent.at(-1).active).toMatchObject({ id: 'w-1', name: 'Treino A' })
    expect(sent.at(-1).active.entries[0].sets).toHaveLength(2)
    stop()
  })

  it('applies the watch\'s ticks and acknowledges them only after saving', async () => {
    const saved = []
    const st = store({ S: { ...S0, active }, ready: true, saveNow: async () => { saved.push(Date.now()) } })
    const setDone = vi.fn()
    bindWatchActions({ setDone, finish: vi.fn() })
    plat.bridge.drain = vi.fn(async () => ({ events: [{ id: 'evt-1', type: 'setDone', wid: 'w-1', exId: '0576', entryIdx: 0, setIdx: 0, w: 42.5, r: 9, at: 1 }] }))
    const stop = await startWatchSync({ useStore: st, useUI: store({ timer: null }) })
    await plat.bridge.listeners.watchEvent?.()
    await wait(50)
    expect(setDone).toHaveBeenCalledWith(0, 0, expect.objectContaining({ w: 42.5, r: 9, at: 1 }))
    expect(saved.length).toBeGreaterThan(0)
    expect(plat.bridge.ack).toHaveBeenCalledWith({ ids: ['evt-1'] })
    stop()
  })

  it('stays off on the web', async () => {
    plat.name = 'web'
    const stop = await startWatchSync({ useStore: store({ S: S0, ready: true }), useUI: store({ timer: null }) })
    expect(plat.bridge.status).not.toHaveBeenCalled()
    stop()
  })
})

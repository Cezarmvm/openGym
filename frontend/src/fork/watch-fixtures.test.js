// @vitest-environment happy-dom
// FORK (specs/fork-watch M2.5): the contract between upstream's watch link (lib/watch-sync.js) and
// the Wear OS watch (frontend/android/watch-core), checked from both ends with shared files.
//
//   snapshots/*.json   written here from upstream's own buildSnapshot/fitSnapshot, read by the
//                      Kotlin tests (FixturesTest): a field the phone changes shape of breaks them.
//   events.json        written by the Kotlin tests from the watch's own SessionCore, read here and
//                      applied with upstream's applyEvents: the phone must take every one.
//
// Run `npx vitest run src/fork/watch-fixtures.test.js` and then `./gradlew :watch-core:test`
// after every merge of upstream (FORK.md).
import { mkdirSync, readFileSync, writeFileSync, existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { buildSnapshot, fitSnapshot, applyEvents, bindWatchActions, bindWatchAppActions, _resetWatchSync, SNAPSHOT_VERSION } from '../lib/watch-sync.js'
import { settingsForWatch, readWatchPrefs } from '../lib/watch-settings.js'

const here = dirname(fileURLToPath(import.meta.url))
const FIXTURES = join(here, '../../android/watch-core/src/test/resources/fixtures')

// The owner's Treino A (exercise ids of the real routine), mid-workout.
const set = (w, r, extra = {}) => ({ w, r, done: false, ...extra })
const treinoA = () => ({
  id: 'w-treino-a', name: 'Treino A', cur: 1, d: '2026-10-15', start: 1760530000000,
  entries: [
    { id: '0576', target: { sets: 3, reps: 10, weight: 40 }, sets: [set(20, 12, { phase: 'warmup', done: true, at: 1760530100000 }), set(40, 10, { done: true, at: 1760530200000 }), set(40, 10), set(40, 10)] },
    { id: '1030', target: { sets: 3, reps: 12, weight: 25, restSec: 75 }, sets: [set(25, 12), set(25, 12), set(25, 12)] },
    { id: '2661', target: { sets: 3, reps: 12 }, sets: [set(0, 12), set(0, 12), set(0, 12)] },
    { id: '2665', target: { sets: 3, reps: 12 }, sets: [set(0, 12), set(0, 12), set(0, 12)] },
    { id: '0031', target: { sets: 4, reps: 10, weight: 30 }, sets: [set(30, 10), set(30, 10), set(30, 10), set(30, 10)] },
    { id: '0246', target: { sets: 3, reps: 12, weight: 12 }, sets: [set(12, 12), set(12, 12), set(12, 12)] },
    { id: '3040', target: { sets: 3, reps: 15 }, sets: [set(0, 15), set(0, 15), set(0, 15)] },
    { id: '0334', target: { sets: 3, reps: 10, weight: 20 }, sets: [set(20, 10), set(20, 10), set(20, 10)] },
  ],
})

const side = (w, r) => ({ w, r: r * 2, done: false, sides: { L: { w, r, done: false }, R: { w, r, done: false } } })

const CASES = {
  'no-workout': { S: { unit: 'kg', restSec: 90, workouts: [], routines: [] } },
  'treino-a': { S: { unit: 'kg', restSec: 90, workouts: [], routines: [], active: treinoA() }, timer: { endsAt: 1760530290000 } },
  'superset': {
    S: {
      unit: 'kg', restSec: 60, workouts: [], routines: [],
      active: { id: 'w-ss', name: 'Superset', cur: 0, d: '2026-10-15', entries: [
        { id: '0025', sg: 'a', target: { sets: 2, reps: 10, weight: 12 }, sets: [set(12, 10), set(12, 10)] },
        { id: '0001', sg: 'a', target: { sets: 2, reps: 12, weight: 20 }, sets: [set(20, 12), set(20, 12)] },
        { id: '0334', target: { sets: 1, reps: 15, weight: 8 }, sets: [set(8, 15)] },
      ] },
    },
  },
  'per-side-time-cardio': {
    S: {
      unit: 'lb', restSec: 90, workouts: [], routines: [],
      active: { id: 'w-mix', name: 'Mixed', cur: 0, d: '2026-10-15', entries: [
        { id: '0576', target: { sets: 2, reps: 16, weight: 20, side: true }, sets: [side(20, 8), side(20, 8)] },
        { id: '0031', target: { sets: 2, mode: 'time', sec: 45 }, sets: [{ sec: 45, done: false }, { sec: 45, done: false }] },
        { id: '0246', target: { sets: 1, mode: 'cardio' }, sets: [{ done: false }] },
      ] },
    },
  },
}

const snapshotOf = ({ S, timer = null }) => {
  const snap = buildSnapshot(S, { timer })
  snap.settings = { ...settingsForWatch(readWatchPrefs(), false), lang: 'pt', weighIn: true, lastBw: 66.6 }
  return fitSnapshot(snap)
}

beforeEach(() => { localStorage.clear(); _resetWatchSync() })

describe('snapshots for the Kotlin watch', () => {
  it('are written from upstream\'s own buildSnapshot', () => {
    mkdirSync(join(FIXTURES, 'snapshots'), { recursive: true })
    for (const [name, c] of Object.entries(CASES)) {
      const snap = snapshotOf(c)
      expect(snap.v).toBe(SNAPSHOT_VERSION)
      writeFileSync(join(FIXTURES, 'snapshots', `${name}.json`), JSON.stringify(snap, null, 2) + '\n')
    }
    const a = snapshotOf(CASES['treino-a']).active
    expect(a.entries).toHaveLength(8)
    expect(a.entries[0].sets[0].warmup).toBe(true)
    expect(a.rest).toEqual({ endsAt: 1760530290000 })
    const ss = snapshotOf(CASES.superset).active.entries
    expect(ss[0].group).toBe('a')
    expect(ss[1].group).toBe('a')
    const mix = snapshotOf(CASES['per-side-time-cardio']).active.entries
    expect(mix.map(e => e.mode)).toEqual(['reps', 'time', 'cardio'])
    expect(mix[0].perSide).toBe(true)
    expect(mix[0].sets[0].sides.L).toEqual({ w: 20, r: 8, done: false })
  })
})

describe('events from the Kotlin watch', () => {
  const file = join(FIXTURES, 'events.json')
  it.skipIf(!existsSync(file))('are all taken by upstream\'s applyEvents', () => {
    const events = JSON.parse(readFileSync(file, 'utf8'))
    expect(events.length).toBeGreaterThan(0)
    const S = CASES['treino-a'].S
    const setDone = vi.fn()
    const finish = vi.fn()
    bindWatchActions({ setDone, finish })
    bindWatchAppActions({ start: vi.fn(), patchSet: vi.fn(), weighIn: vi.fn(), openWorkout: vi.fn() })
    const done = applyEvents(events, () => ({ S }))
    expect(done).toEqual(events.map(e => e.id))
    const ticks = events.filter(e => e.type === 'setDone' && e.wid === S.active.id)
    expect(setDone).toHaveBeenCalledTimes(ticks.length)
    for (const [i, e] of ticks.entries()) {
      const [idx, si, fields] = setDone.mock.calls[i]
      expect(idx).toBe(e.entryIdx)
      expect(si).toBe(e.setIdx)
      expect(fields).toMatchObject({ w: e.w ?? null, r: e.r ?? null, at: e.at })
    }
    expect(finish).toHaveBeenCalledTimes(events.filter(e => e.type === 'finish').length)
  })
})

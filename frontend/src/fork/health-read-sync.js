// FORK (specs/fork-health): reading weight and body fat back from Health Connect. The merge rules
// are in health-read.js; this file owns the plugin, the phone's own file and when a read happens.
//
// Like upstream's opengym-health.json, opengym-health-read.json is a fact about this phone: never
// in S, so it does not sync, ride in a backup or switch on on a second phone. Off by default.
import { foreign, dailyWeights, dailyBodyFat, mergeWeights, mergeBodyFat } from './health-read.js'

export const READ_FILE = 'opengym-health-read.json'
export const KINDS = ['weight', 'bodyFat']
const DAY = 24 * 3600e3
const OFF = { on: false, kinds: { weight: true, bodyFat: true }, lastRead: 0, lastCount: 0, error: null }

/**
 * Everything with side effects is injected, so the tests play the plugin and the files in memory.
 * deps: { plugin: () => Promise<plugin|null>, readJson(name), writeJson(name, obj), update(fn), getState(), now() }
 */
export function createHealthRead(deps) {
  const now = deps.now || Date.now
  let cached = null
  let running = null
  const listeners = new Set()
  const told = st => listeners.forEach(cb => { try { cb(st) } catch { /* a listener's problem */ } })

  async function load() {
    if (cached) return cached
    const f = await deps.readJson(READ_FILE)
    cached = f && typeof f === 'object' ? { ...OFF, ...f, kinds: { ...OFF.kinds, ...(f.kinds || {}) } } : { ...OFF }
    return cached
  }
  async function save(st) { cached = st; await deps.writeJson(READ_FILE, st); told(st); return st }

  const wanted = st => KINDS.filter(k => st.kinds[k])

  /** One read: everything new since the last one (all that is released, the first time). */
  function read() {
    if (running) return running
    running = (async () => {
      const st = await load()
      if (!st.on) return st
      const p = await deps.plugin()
      if (!p) return st
      const kinds = wanted(st)
      if (!kinds.length) return st
      // A day of overlap: a scale or a watch often hands its reading over hours late.
      const since = st.lastRead > 0 ? st.lastRead - DAY : 0
      const startedAt = now()
      try {
        const got = await p.readSince({ kinds, since })
        const unit = deps.getState()?.unit === 'lb' ? 'lb' : 'kg'
        const weights = st.kinds.weight ? dailyWeights(foreign(got?.weights), { unit }) : []
        const fat = st.kinds.bodyFat ? dailyBodyFat(foreign(got?.bodyFat)) : []
        let count = 0
        if (weights.length || fat.length) {
          deps.update(s => {
            const a = mergeWeights(s, weights)
            const b = mergeBodyFat(s, fat)
            count = a.added + a.updated + b.added
          })
        }
        return await save({ ...st, lastRead: startedAt, lastCount: count, error: null })
      } catch (e) {
        return await save({ ...st, error: e?.code === 'permission' ? 'permission' : e?.code === 'unavailable' ? 'unavailable' : 'failed' })
      }
    })().finally(() => { running = null })
    return running
  }

  /** Turning it on: asks for the read permissions (and the history), then reads once. */
  async function enable(kinds = OFF.kinds) {
    const p = await deps.plugin()
    if (!p) return { ok: false, reason: 'unsupported' }
    const s = await p.status().catch(() => ({ status: 'unsupported' }))
    if (s.status !== 'available') return { ok: false, reason: s.status }
    const ask = [...KINDS.filter(k => kinds[k]), 'history']
    const res = await p.requestPermissions({ kinds: ask }).catch(() => ({ granted: {} }))
    const g = res?.granted || {}
    const allowed = { weight: !!(kinds.weight && g.weight), bodyFat: !!(kinds.bodyFat && g.bodyFat) }
    if (!allowed.weight && !allowed.bodyFat) return { ok: false, reason: 'denied' }
    const st = await load()
    await save({ ...st, on: true, kinds: allowed, error: null, history: !!g.history })
    const after = await read()
    return { ok: !after?.error, reason: after?.error || null, state: after }
  }

  /** Turning it off stops the reading; what was brought in stays, as the owner's data. */
  async function disable() {
    const st = await load()
    return save({ ...st, on: false, error: null })
  }

  function onChange(cb) { listeners.add(cb); return () => listeners.delete(cb) }

  return { load, read, enable, disable, onChange }
}

// ── The app's own instance ───────────────────────────────────────────────────────────────────────
let instance = null
let started = false

export async function healthRead() {
  if (instance) return instance
  const { isAndroid, readJsonFile, writeJsonFile } = await import('../lib/mobile.js')
  const { useStore } = await import('../store/useStore.js')
  let pluginP = null
  instance = createHealthRead({
    // Wrapped in an object: a Capacitor plugin proxy answers `then` (see lib/rest-alert.js).
    plugin: () => (pluginP || (pluginP = (async () => {
      if (!(await isAndroid())) return null
      const { registerPlugin } = await import('@capacitor/core')
      return { p: registerPlugin('HealthRead') }
    })().catch(() => null))).then(x => x?.p || null),
    readJson: readJsonFile,
    writeJson: writeJsonFile,
    update: fn => useStore.getState().update(fn, true),
    getState: () => useStore.getState().S,
  })
  return instance
}

/** At launch and on every return to the foreground, if it is on (src/main.jsx). */
export function initHealthRead() {
  if (started) return
  started = true
  import('@capacitor/app').then(({ App }) => {
    App.addListener('appStateChange', ({ isActive }) => { if (isActive) healthRead().then(h => h.read()).catch(() => {}) })
  }).catch(() => {})
  setTimeout(() => healthRead().then(h => h.read()).catch(() => {}), 3500)
}

// @vitest-environment happy-dom
// The phone app's first-start permission step (owner request, TestFlight): after "Use on this
// device" or a finished pairing, one screen explains notifications with Allow / Not now. Shown
// once per device, never for a permission already granted or one the OS will not ask for again,
// never off the phone app. Allow goes through the same request the first rest makes
// (rest-alert.js), so the first rest does not ask a second time; Not now leaves that ask alone.
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

globalThis.IS_REACT_ACT_ENVIRONMENT = true

const h = vi.hoisted(() => ({ MOBILE: true, display: 'prompt', after: 'granted', finish: vi.fn(), requests: 0, health: { status: 'unsupported', granted: false, platform: null }, healthOn: false, enable: [] }))
vi.mock('../lib/mobile.js', () => ({ get MOBILE() { return h.MOBILE }, nativePlatform: async () => (h.MOBILE ? 'ios' : null) }))
vi.mock('@capacitor/local-notifications', () => ({
  LocalNotifications: {
    checkPermissions: async () => ({ display: h.display }),
    requestPermissions: async () => { h.requests++; h.display = h.after; return { display: h.after } },
  },
}))
// Apple Health (iPhone only): lib/health-sync.js is played here; its own tests cover the writing.
vi.mock('../lib/health-sync.js', () => ({
  healthStatus: async () => h.health,
  loadHealth: async () => ({ on: h.healthOn, written: {} }),
  enableHealth: async S => { h.enable.push(S); h.healthOn = true; return { ok: true } },
}))
vi.mock('../store/useStore.js', () => {
  const snap = () => ({ finishPermSteps: h.finish, S: { unit: 'kg', workouts: [] } })
  const useStore = sel => (sel ? sel(snap()) : snap())
  useStore.getState = snap
  return { useStore }
})

const { default: PermissionSteps } = await import('./PermissionSteps.jsx')
const { pendingSteps, SEEN_KEY } = await import('../lib/onboarding-perms.js')
const { NOTIF_ASKED_KEY, _resetNotifAsk } = await import('../lib/rest-alert.js')

let host, root
beforeEach(() => {
  Object.assign(h, { MOBILE: true, display: 'prompt', after: 'granted', requests: 0, health: { status: 'unsupported', granted: false, platform: null }, healthOn: false, enable: [] })
  h.finish.mockReset()
  localStorage.clear()
  _resetNotifAsk()
  host = document.createElement('div'); document.body.appendChild(host); root = createRoot(host)
})
afterEach(() => { act(() => root.unmount()); host.remove() })

const settle = () => act(async () => { for (let i = 0; i < 6; i++) await Promise.resolve() })
const mount = async () => { act(() => root.render(<PermissionSteps />)); await settle() }
const button = text => [...host.querySelectorAll('button')].find(b => b.textContent.trim() === text)
const click = async el => { await act(async () => { el.click() }); await settle() }

describe('which steps are due', () => {
  it('notifications, while the OS would still ask', async () => {
    expect((await pendingSteps()).map(s => s.id)).toEqual(['notifications'])
  })
  it('none once granted, none once denied for good, none off the phone app', async () => {
    h.display = 'granted'; expect(await pendingSteps()).toEqual([])
    h.display = 'denied'; expect(await pendingSteps()).toEqual([])
    h.display = 'prompt'; h.MOBILE = false; expect(await pendingSteps()).toEqual([])
  })
})

describe('the screen', () => {
  it('explains notifications; Allow asks the OS once and goes on to the app', async () => {
    await mount()
    expect(host.textContent).toContain('Never miss the end of a rest')
    expect(host.textContent).toContain('Training reminders, only if you switch them on')
    expect(host.textContent).not.toMatch(/—/)
    await click(button('Allow'))
    expect(h.requests).toBe(1)
    expect(h.finish).toHaveBeenCalledTimes(1)
    // The first rest does not ask again: the answer is on record where rest-alert.js looks.
    expect(localStorage.getItem(NOTIF_ASKED_KEY)).toBe('granted')
    expect(JSON.parse(localStorage.getItem(SEEN_KEY))).toEqual(['notifications'])
  })

  it('Not now goes on without asking, and leaves the in-context ask for later', async () => {
    await mount()
    await click(button('Not now'))
    expect(h.requests).toBe(0)
    expect(h.finish).toHaveBeenCalledTimes(1)
    expect(localStorage.getItem(NOTIF_ASKED_KEY)).toBeNull()
  })

  it('is shown once per device', async () => {
    await mount()
    await click(button('Not now'))
    act(() => root.unmount()); root = createRoot(host); h.finish.mockReset()
    await mount()
    expect(host.textContent).toBe('')
    expect(h.finish).toHaveBeenCalledTimes(1)
  })

  it('with nothing to ask, renders nothing and goes straight on', async () => {
    h.display = 'granted'
    await mount()
    expect(host.textContent).toBe('')
    expect(h.finish).toHaveBeenCalledTimes(1)
  })
})

describe('Apple Health, on the iPhone', () => {
  const iphone = () => { h.display = 'granted'; h.health = { status: 'available', granted: false, platform: 'ios' } }

  it('is due on an iPhone with Apple Health while it is off; never on Android or once on', async () => {
    iphone()
    expect((await pendingSteps()).map(s => s.id)).toEqual(['appleHealth'])
    h.healthOn = true
    expect(await pendingSteps()).toEqual([])
    h.healthOn = false
    h.health = { status: 'available', granted: false, platform: 'android' }
    expect(await pendingSteps()).toEqual([])
    h.health = { status: 'unsupported', granted: false, platform: 'ios' }
    expect(await pendingSteps()).toEqual([])
  })

  it('comes after notifications', async () => {
    h.health = { status: 'available', granted: false, platform: 'ios' }
    expect((await pendingSteps()).map(s => s.id)).toEqual(['notifications', 'appleHealth'])
  })

  it('explains it; Allow asks iOS and switches the setting on', async () => {
    iphone()
    await mount()
    expect(host.textContent).toContain('Your workouts in Apple Health')
    expect(host.textContent).toContain('Heart rate per set on your Apple Watch')
    expect(host.textContent).toContain('You can change it anytime in Settings.')
    expect(host.textContent).not.toMatch(/—/)
    await click(button('Allow'))
    expect(h.enable).toHaveLength(1)
    expect(h.enable[0]).toMatchObject({ unit: 'kg' })
    expect(h.finish).toHaveBeenCalledTimes(1)
    expect(JSON.parse(localStorage.getItem(SEEN_KEY))).toEqual(['appleHealth'])
  })

  it('Not now leaves it off and goes on', async () => {
    iphone()
    await mount()
    await click(button('Not now'))
    expect(h.enable).toEqual([])
    expect(h.healthOn).toBe(false)
    expect(h.finish).toHaveBeenCalledTimes(1)
  })
})

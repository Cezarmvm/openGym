// FORK (specs/fork-watch R2): Settings → watch on Android, for the Wear OS watch. The same choices
// as upstream's views/WatchSettings.jsx (the Apple Watch page), kept in the same place
// (lib/watch-settings.js), so lib/watch-sync.js sends them with every snapshot. Only what the
// Wear OS watch does is offered: no animation, no calories, and no heart rate before M5.
import { useEffect, useState } from 'react'
import { Section, Row, Switch, SelectRow } from '../components/ui.jsx'
import { readWatchPrefs, writeWatchPrefs, onWatchPrefs } from '../lib/watch-settings.js'
import { watchStatus } from '../lib/watch-sync.js'
import { ft } from './strings.js'

export default function WearSettingsPage({ S, status = watchStatus }) {
  const lang = S?.lang
  const [prefs, setPrefs] = useState(readWatchPrefs)
  const [link, setLink] = useState(null)   // { paired, installed } from the phone, or null while asking
  useEffect(() => onWatchPrefs(() => setPrefs(readWatchPrefs())), [])
  useEffect(() => {
    let gone = false
    status().then(st => { if (!gone) setLink(st) }).catch(() => { if (!gone) setLink({ paired: false, installed: false }) })
    return () => { gone = true }
  }, [status])
  const set = patch => setPrefs(writeWatchPrefs(patch))

  const linkLine = !link ? ft('Checking…', lang)
    : !link.paired ? ft('No watch connected to this phone', lang)
    : !link.installed ? ft('openGym is not on your watch yet. Install it with the fork\'s release script.', lang)
    : ft('openGym is on your watch', lang)

  return <>
    <Section footer={linkLine}>
      <Row icon="watch" iconTint="var(--grey)" title={ft('Send workouts to the watch', lang)}>
        <Switch checked={prefs.enabled} onChange={v => set({ enabled: v })} aria-label={ft('Send workouts to the watch', lang)} />
      </Row>
    </Section>
    {prefs.enabled && <>
      <Section footer={ft('Start a workout on the phone and the watch shows it and taps once.', lang)}>
        <Row icon="bolt" iconTint="var(--green)" title={ft('Show on the watch', lang)}>
          <Switch checked={prefs.autoShow} onChange={v => set({ autoShow: v })} aria-label={ft('Show on the watch', lang)} />
        </Row>
      </Section>
      <Section title={ft('On the watch', lang)} footer={ft('Sets you tick on the watch reach this phone when you open openGym.', lang)}>
        {[['last', 'Last time', 'history'], ['elapsed', 'Time so far', 'clock'], ['next', 'Next exercise during the rest', 'list']].map(([k, title, icon]) => (
          <Row key={k} icon={icon} iconTint="var(--grey)" title={ft(title, lang)}>
            <Switch checked={!!prefs.show[k]} onChange={v => set({ show: { [k]: v } })} aria-label={ft(title, lang)} />
          </Row>
        ))}
        <SelectRow icon="timer" iconTint="var(--orange)" title={ft('Rest timer', lang)} value={prefs.show.rest}
          onChange={v => set({ show: { rest: v } })}
          options={[{ value: 'ring', label: ft('Ring', lang) }, { value: 'plain', label: ft('Countdown only', lang) }]} />
        <SelectRow icon="watch" iconTint="var(--blue)" title={ft('The bezel turns first', lang)} value={prefs.crown}
          onChange={v => set({ crown: v })}
          options={[{ value: 'reps', label: ft('Reps', lang) }, { value: 'weight', label: ft('Weight', lang) }]} />
      </Section>
      <Section>
        <Row icon="vibrate" iconTint="var(--indigo)" title={ft('Tap the wrist when a rest is over', lang)}>
          <Switch checked={prefs.restHaptics} onChange={v => set({ restHaptics: v })} aria-label={ft('Tap the wrist when a rest is over', lang)} />
        </Row>
      </Section>
    </>}
  </>
}

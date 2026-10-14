// FORK: Health Connect gets only the workouts started after it was turned on (owner's call: the
// history imported from another app must not land in Samsung Health a second time). `since` is
// kept in the phone's opengym-health.json; weigh-ins are not filtered.

/** The records lib/health-connect.js built, minus the sessions that started before `since`. */
export function onlySince(records, since) {
  if (!(since > 0)) return records
  return { ...records, sessions: (records.sessions || []).filter(r => r.start >= since) }
}

// Intentionally kept only in JavaScript module memory. VitePress reuses this
// module while navigating between documentation pages, but a full refresh
// clears it without leaving the selected database in browser storage.
let session = null

export const getTransientSqliteSession = () => session
export const setTransientSqliteSession = (nextSession) => { session = nextSession }
export const clearTransientSqliteSession = () => { session = null }

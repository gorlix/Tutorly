// Manual Jest mock for FileSessionStore - swaps the real disk-backed store
// (which would otherwise read/write the real data/session-store.json on every
// test run) for express-session's built-in in-memory MemoryStore. Picked up
// automatically wherever a test calls jest.mock('../../server_utilities/fileSessionStore').
const session = require('express-session');

module.exports = session.MemoryStore;

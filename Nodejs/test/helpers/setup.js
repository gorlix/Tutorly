// Runs once per test file (see jest.config.js setupFilesAfterEnv). Blocks all
// real network I/O except loopback traffic to the in-process supertest server -
// any route that forgets to mock a Java API call fails loudly instead of
// silently hitting a real backend.
const nock = require('nock');

beforeAll(() => {
    nock.disableNetConnect();
    nock.enableNetConnect('127.0.0.1');
});

afterEach(() => {
    nock.cleanAll();
});

afterAll(() => {
    nock.enableNetConnect();
});

// The app's own logger (server_utilities/logger.js, passwordService.js,
// javaApiService.js) is deliberately chatty in production for auditing -
// including console.error on every simulated 404/409/500 in these tests,
// which is expected noise here, not a real failure (Jest's own reporter
// still surfaces actual assertion failures independently of this).
let logSpy;
let warnSpy;
let errorSpy;

beforeEach(() => {
    logSpy = jest.spyOn(console, 'log').mockImplementation(() => {});
    warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => {});
    errorSpy = jest.spyOn(console, 'error').mockImplementation(() => {});
});

afterEach(() => {
    logSpy.mockRestore();
    warnSpy.mockRestore();
    errorSpy.mockRestore();
});

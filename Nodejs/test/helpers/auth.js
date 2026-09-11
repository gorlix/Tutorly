// Login helpers for route tests. Real login flow through POST /login and
// POST /adminLogin (bcrypt verification included, not stubbed) so tests get a
// genuine session cookie exactly as a browser would - the only thing mocked
// is the Java API response the login route itself fetches.
const { javaApi } = require('./javaApi');

// Precomputed once (bcrypt.hashSync('TestPass123!', 10)) instead of hashing
// per test - hashing is deliberately slow (bcrypt, 10 rounds) and every login
// helper call would otherwise pay that cost again.
const TEST_PASSWORD = 'TestPass123!';
const TEST_PASSWORD_HASH = '$2b$10$.mDqz.58Y1PECsa72c2tfepkiNKtoafUMXC4tNYuoZ7OXw8XOkTDe';

function tutorFixture(overrides = {}) {
    return {
        id: 1,
        username: 'test.tutor',
        password: TEST_PASSWORD_HASH,
        role: 'GENERIC',
        status: 'ACTIVE',
        anonymizedAt: null,
        mail: null,
        ...overrides
    };
}

function adminFixture(overrides = {}) {
    return {
        id: 1,
        username: 'test.admin',
        password: TEST_PASSWORD_HASH,
        mail: 'admin@example.com',
        ...overrides
    };
}

// Logs the given supertest agent in as `tutor` (cookie persists on the agent
// for subsequent requests). Intercepts the GET /api/users call authenticateTutor
// makes to find the account by username.
async function loginAsTutor(agent, tutor = tutorFixture()) {
    javaApi().get('/api/users').reply(200, [tutor]);
    return agent.post('/login').type('form').send({ username: tutor.username, password: TEST_PASSWORD });
}

// Same as loginAsTutor but for POST /adminLogin (intercepts GET /api/admins).
async function loginAsAdmin(agent, admin = adminFixture()) {
    javaApi().get('/api/admins').reply(200, [admin]);
    return agent.post('/adminLogin').type('form').send({ username: admin.username, password: TEST_PASSWORD });
}

module.exports = {
    loginAsTutor,
    loginAsAdmin,
    tutorFixture,
    adminFixture,
    TEST_PASSWORD,
    TEST_PASSWORD_HASH
};

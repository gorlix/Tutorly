const request = require('supertest');
const app = require('../helpers/testApp');
const { javaApi } = require('../helpers/javaApi');
const { loginAsTutor, loginAsAdmin, tutorFixture, adminFixture, TEST_PASSWORD } = require('../helpers/auth');

describe('GET /login', () => {
    test('renders the login page when not authenticated', async () => {
        const res = await request(app).get('/login');
        expect(res.status).toBe(200);
        expect(res.type).toBe('text/html');
    });

    test('redirects to /home when already authenticated', async () => {
        const agent = request.agent(app);
        await loginAsTutor(agent);

        const res = await agent.get('/login');
        expect(res.status).toBe(302);
        expect(res.headers.location).toBe('/home');
    });
});

describe('GET /adminLogin', () => {
    test('renders the admin login page when not authenticated', async () => {
        const res = await request(app).get('/adminLogin');
        expect(res.status).toBe(200);
        expect(res.type).toBe('text/html');
    });

    test('redirects to /admin when already an authenticated admin', async () => {
        const agent = request.agent(app);
        await loginAsAdmin(agent);

        const res = await agent.get('/adminLogin');
        expect(res.status).toBe(302);
        expect(res.headers.location).toBe('/admin');
    });
});

describe('POST /login', () => {
    test('valid credentials redirect to /home and set a session cookie', async () => {
        const agent = request.agent(app);
        const res = await loginAsTutor(agent);

        expect(res.status).toBe(302);
        expect(res.headers.location).toBe('/home');
        expect(res.headers['set-cookie'].some(c => c.startsWith('tutorly.sid='))).toBe(true);
    });

    test('unknown username re-renders the login page with an error', async () => {
        javaApi().get('/api/users').reply(200, []);

        const res = await request(app).post('/login').type('form').send({ username: 'nobody', password: 'whatever' });

        expect(res.status).toBe(200);
        expect(res.headers['set-cookie']).toBeUndefined();
    });

    test('wrong password re-renders the login page with an error', async () => {
        javaApi().get('/api/users').reply(200, [tutorFixture()]);

        const res = await request(app).post('/login').type('form').send({ username: 'test.tutor', password: 'wrong-password' });

        expect(res.status).toBe(200);
        expect(res.headers['set-cookie']).toBeUndefined();
    });

    test('a BLOCKED account is rejected even with the correct password', async () => {
        javaApi().get('/api/users').reply(200, [tutorFixture({ status: 'BLOCKED' })]);

        const res = await request(app).post('/login').type('form').send({ username: 'test.tutor', password: TEST_PASSWORD });

        expect(res.status).toBe(200);
        expect(res.headers['set-cookie']).toBeUndefined();
    });

    test('an erased account (anonymizedAt set) is rejected even with the correct password', async () => {
        javaApi().get('/api/users').reply(200, [tutorFixture({ anonymizedAt: '2026-01-01T00:00:00' })]);

        const res = await request(app).post('/login').type('form').send({ username: 'test.tutor', password: TEST_PASSWORD });

        expect(res.status).toBe(200);
        expect(res.headers['set-cookie']).toBeUndefined();
    });
});

describe('POST /adminLogin', () => {
    test('valid credentials redirect to /admin and set a session cookie', async () => {
        const agent = request.agent(app);
        const res = await loginAsAdmin(agent);

        expect(res.status).toBe(302);
        expect(res.headers.location).toBe('/admin');
        expect(res.headers['set-cookie'].some(c => c.startsWith('tutorly.sid='))).toBe(true);
    });

    test('wrong password re-renders the admin login page with an error', async () => {
        javaApi().get('/api/admins').reply(200, [adminFixture()]);

        const res = await request(app).post('/adminLogin').type('form').send({ username: 'test.admin', password: 'wrong-password' });

        expect(res.status).toBe(200);
        expect(res.headers['set-cookie']).toBeUndefined();
    });
});

describe('GET /logout', () => {
    test('destroys the tutor session and redirects to /login', async () => {
        const agent = request.agent(app);
        await loginAsTutor(agent);

        const logoutRes = await agent.get('/logout');
        expect(logoutRes.status).toBe(302);
        expect(logoutRes.headers.location).toBe('/login');

        // Session is gone - a protected route now redirects to /login instead of rendering
        const homeRes = await agent.get('/home');
        expect(homeRes.status).toBe(302);
        expect(homeRes.headers.location).toBe('/login');
    });
});

describe('POST /logout', () => {
    test('destroys the tutor session and redirects to /login', async () => {
        const agent = request.agent(app);
        await loginAsTutor(agent);

        const res = await agent.post('/logout');
        expect(res.status).toBe(302);
        expect(res.headers.location).toBe('/login');
    });
});

describe('unmatched routes', () => {
    test('fall through to the 404 handler', async () => {
        const res = await request(app).get('/this-route-does-not-exist');
        expect(res.status).toBe(404);
    });
});

describe('GET /adminLogout', () => {
    test('destroys the admin session and redirects to /adminLogin', async () => {
        const agent = request.agent(app);
        await loginAsAdmin(agent);

        const logoutRes = await agent.get('/adminLogout');
        expect(logoutRes.status).toBe(302);
        expect(logoutRes.headers.location).toBe('/adminLogin');

        const adminRes = await agent.get('/admin');
        expect(adminRes.status).toBe(302);
        expect(adminRes.headers.location).toBe('/adminLogin');
    });
});

describe('GET /api/auth/status', () => {
    test('reports unauthenticated when there is no session', async () => {
        const res = await request(app).get('/api/auth/status');
        expect(res.status).toBe(200);
        expect(res.body).toEqual({ authenticated: false });
    });

    test('reports the logged-in tutor when authenticated', async () => {
        const agent = request.agent(app);
        const tutor = tutorFixture({ id: 42, username: 'staff.member', role: 'STAFF' });
        await loginAsTutor(agent, tutor);

        const res = await agent.get('/api/auth/status');
        expect(res.status).toBe(200);
        expect(res.body).toEqual({
            authenticated: true,
            user: { id: 42, username: 'staff.member', role: 'staff' }
        });
    });
});

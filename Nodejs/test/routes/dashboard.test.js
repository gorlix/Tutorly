const request = require('supertest');
const app = require('../helpers/testApp');
const { javaApi } = require('../helpers/javaApi');
const { loginAsTutor, tutorFixture } = require('../helpers/auth');
const { lessonFixture, prenotationFixture } = require('../helpers/fixtures');

async function tutorAgent(overrides = {}) {
    const agent = request.agent(app);
    await loginAsTutor(agent, tutorFixture(overrides));
    return agent;
}

describe('GET /api/dashboard/calendar-events', () => {
    test('redirects to /login when not authenticated', async () => {
        const res = await request(app).get('/api/dashboard/calendar-events').query({ month: '2026-09' });
        expect(res.status).toBe(302);
        expect(res.headers.location).toBe('/login');
    });

    test('rejects an invalid month format with 400', async () => {
        const agent = await tutorAgent();
        javaApi().get('/api/users/1').reply(200, tutorFixture({ id: 1 }));

        const res = await agent.get('/api/dashboard/calendar-events').query({ month: '09-2026' });
        expect(res.status).toBe(400);
    });

    test('returns the dates within the month that have at least one lesson/prenotation/note', async () => {
        const agent = await tutorAgent();
        javaApi().get('/api/users/1').reply(200, tutorFixture({ id: 1 }));
        javaApi().get('/api/calendar-notes/tutor/1').reply(200, []);
        javaApi().get('/api/lessons').reply(200, [lessonFixture({ tutorId: 1, startTime: '2026-09-05T10:00:00' })]);
        javaApi().get('/api/prenotations').reply(200, [prenotationFixture({ tutorId: 1, startTime: '2026-09-20T10:00:00' })]);

        const res = await agent.get('/api/dashboard/calendar-events').query({ month: '2026-09' });
        expect(res.status).toBe(200);
        expect(res.body.dates.sort()).toEqual(['2026-09-05', '2026-09-20']);
    });

    test('a GUEST only sees events for their assigned student(s)', async () => {
        const agent = await tutorAgent({ id: 21, role: 'GUEST' });
        javaApi().get('/api/users/21').reply(200, tutorFixture({ id: 21, role: 'GUEST' }));
        javaApi().get('/api/calendar-notes/tutor/21').reply(200, []);
        javaApi().get('/api/lessons').reply(200, [
            lessonFixture({ studentId: 10, startTime: '2026-09-05T10:00:00' }),
            lessonFixture({ studentId: 99, startTime: '2026-09-06T10:00:00' })
        ]);
        javaApi().get('/api/prenotations').reply(200, []);
        javaApi().get('/api/students/guest/21').reply(200, [{ id: 10 }]);

        const res = await agent.get('/api/dashboard/calendar-events').query({ month: '2026-09' });
        expect(res.status).toBe(200);
        expect(res.body.dates).toEqual(['2026-09-05']);
    });
});

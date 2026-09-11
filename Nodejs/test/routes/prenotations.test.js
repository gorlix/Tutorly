const request = require('supertest');
const app = require('../helpers/testApp');
const { javaApi } = require('../helpers/javaApi');
const { loginAsTutor, tutorFixture } = require('../helpers/auth');
const { studentFixture, prenotationFixture } = require('../helpers/fixtures');

async function tutorAgent(overrides = {}) {
    const agent = request.agent(app);
    await loginAsTutor(agent, tutorFixture(overrides));
    return agent;
}

describe('POST /api/prenotations', () => {
    test('a GUEST account is blocked with 403', async () => {
        const agent = await tutorAgent({ role: 'GUEST' });
        const res = await agent.post('/api/prenotations').send({ studentId: 10, startTime: '10:00', endTime: '11:00' });
        expect(res.status).toBe(403);
    });

    test('creates the prenotation, defaulting tutorId to the session user', async () => {
        const agent = await tutorAgent();
        let forwardedBody;
        javaApi().post('/api/prenotations/create', body => { forwardedBody = body; return true; })
            .reply(201, (uri, body) => ({ id: 200, ...body }));
        // Fire-and-forget push lookup after the response is sent - mocked so
        // it resolves cleanly instead of hitting nock's "no match" error.
        javaApi().get('/api/students/10').reply(200, studentFixture());

        const res = await agent.post('/api/prenotations').send({
            studentId: 10,
            startTime: '2026-09-11T10:00:00',
            endTime: '2026-09-11T11:00:00'
        });

        expect(res.status).toBe(200);
        expect(forwardedBody.tutorId).toBe(1);
        expect(forwardedBody.creatorId).toBe(1);
        expect(forwardedBody.flag).toBe(false);
    });

    test('STAFF can create a prenotation for another tutor', async () => {
        const agent = await tutorAgent({ id: 1, role: 'STAFF' });
        let forwardedBody;
        javaApi().post('/api/prenotations/create', body => { forwardedBody = body; return true; })
            .reply(201, (uri, body) => ({ id: 200, ...body }));
        javaApi().get('/api/students/10').reply(200, studentFixture());

        const res = await agent.post('/api/prenotations').send({
            studentId: 10,
            tutorId: 9,
            startTime: '2026-09-11T10:00:00',
            endTime: '2026-09-11T11:00:00'
        });

        expect(res.status).toBe(200);
        expect(forwardedBody.tutorId).toBe(9);
        expect(forwardedBody.creatorId).toBe(1);
    });

    test('propagates a Java API error', async () => {
        const agent = await tutorAgent();
        javaApi().post('/api/prenotations/create').reply(400, 'Overlapping prenotation');

        const res = await agent.post('/api/prenotations').send({ studentId: 10, startTime: '2026-09-11T10:00:00', endTime: '2026-09-11T11:00:00' });
        expect(res.status).toBe(400);
    });
});

describe('PUT /api/prenotations/:id', () => {
    test('a GUEST account is blocked with 403', async () => {
        const agent = await tutorAgent({ role: 'GUEST' });
        const res = await agent.put('/api/prenotations/200').send({ studentId: 10, startTime: '2026-09-11T10:00:00', endTime: '2026-09-11T11:00:00' });
        expect(res.status).toBe(403);
    });

    test('updates the prenotation', async () => {
        const agent = await tutorAgent();
        javaApi().put('/api/prenotations/200').reply(200, prenotationFixture({ startTime: '2026-09-12T10:00:00' }));

        const res = await agent.put('/api/prenotations/200').send({
            studentId: 10,
            startTime: '2026-09-12T10:00:00',
            endTime: '2026-09-12T11:00:00'
        });
        expect(res.status).toBe(200);
        expect(res.body.startTime).toBe('2026-09-12T10:00:00');
    });
});

describe('DELETE /api/prenotations/:id', () => {
    test('deletes the prenotation', async () => {
        const agent = await tutorAgent();
        javaApi().delete('/api/prenotations/200').reply(200);

        const res = await agent.delete('/api/prenotations/200');
        expect(res.status).toBe(200);
        expect(res.body.success).toBe(true);
    });

    test('propagates a Java API error', async () => {
        const agent = await tutorAgent();
        javaApi().delete('/api/prenotations/999').reply(404, 'Prenotation not found');

        const res = await agent.delete('/api/prenotations/999');
        expect(res.status).toBe(404);
    });
});

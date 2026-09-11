const request = require('supertest');
const app = require('../helpers/testApp');
const { javaApi } = require('../helpers/javaApi');
const { loginAsTutor } = require('../helpers/auth');

async function tutorAgent() {
    const agent = request.agent(app);
    await loginAsTutor(agent);
    return agent;
}

describe('GET /api/push/vapid-public-key', () => {
    test('redirects to /login when not authenticated', async () => {
        const res = await request(app).get('/api/push/vapid-public-key');
        expect(res.status).toBe(302);
        expect(res.headers.location).toBe('/login');
    });

    test('returns the configured VAPID public key (or null)', async () => {
        const agent = await tutorAgent();
        const res = await agent.get('/api/push/vapid-public-key');
        expect(res.status).toBe(200);
        expect(res.body).toHaveProperty('publicKey');
    });
});

describe('POST /api/push/subscribe', () => {
    test('rejects a subscription missing endpoint/keys with 400', async () => {
        const agent = await tutorAgent();
        const res = await agent.post('/api/push/subscribe').send({ subscription: { endpoint: 'https://push.example.com/x' } });
        expect(res.status).toBe(400);
    });

    test('saves a valid subscription', async () => {
        const agent = await tutorAgent();
        javaApi().post('/api/push-subscriptions').reply(200, { id: 1 });

        const res = await agent.post('/api/push/subscribe').send({
            subscription: { endpoint: 'https://push.example.com/x', keys: { p256dh: 'p', auth: 'a' } }
        });
        expect(res.status).toBe(201);
        expect(res.body.success).toBe(true);
    });

    test('returns 500 when the Java API save fails', async () => {
        const agent = await tutorAgent();
        javaApi().post('/api/push-subscriptions').reply(500);

        const res = await agent.post('/api/push/subscribe').send({
            subscription: { endpoint: 'https://push.example.com/x', keys: { p256dh: 'p', auth: 'a' } }
        });
        expect(res.status).toBe(500);
    });
});

describe('POST /api/push/unsubscribe', () => {
    test('rejects a missing endpoint with 400', async () => {
        const agent = await tutorAgent();
        const res = await agent.post('/api/push/unsubscribe').send({});
        expect(res.status).toBe(400);
    });

    test('removes the subscription', async () => {
        const agent = await tutorAgent();
        javaApi().delete('/api/push-subscriptions/by-endpoint').query({ endpoint: 'https://push.example.com/x' }).reply(204);

        const res = await agent.post('/api/push/unsubscribe').send({ endpoint: 'https://push.example.com/x' });
        expect(res.status).toBe(200);
        expect(res.body.success).toBe(true);
    });
});

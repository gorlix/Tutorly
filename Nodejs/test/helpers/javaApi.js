// Returns a fresh nock scope targeting the Java backend, matching whatever
// JAVA_API_HOST/JAVA_API_PORT the app itself is configured with (from .env).
// Every route in index.js reaches the Java API either through fetchFromJavaAPI
// (server_utilities/javaApiService.js) or, for several older routes, a direct
// https.request() call built by createJavaApiRequestOptions() - both go over
// the same host:port, so intercepting at this level covers both without
// needing to mock two different module boundaries.
const nock = require('nock');
const { JAVA_API_HOST, JAVA_API_PORT } = require('../../server_utilities/config');

function javaApi() {
    return nock(`https://${JAVA_API_HOST}:${JAVA_API_PORT}`);
}

module.exports = { javaApi };

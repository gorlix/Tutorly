module.exports = {
    testEnvironment: 'node',
    rootDir: '.',
    testMatch: ['<rootDir>/test/**/*.test.js'],
    setupFilesAfterEnv: ['<rootDir>/test/helpers/setup.js'],
    testTimeout: 10000,
    verbose: true
};

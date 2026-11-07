import test from 'node:test';
import assert from 'node:assert';

// Set up mock localStorage and window environment before importing module
const mockStorage = new Map();
globalThis.localStorage = {
  getItem: (key) => mockStorage.get(key) ?? null,
  setItem: (key, val) => mockStorage.set(key, String(val)),
  removeItem: (key) => mockStorage.delete(key),
  clear: () => mockStorage.clear(),
};

const dispatchedEvents = [];
globalThis.window = {
  dispatchEvent: (event) => dispatchedEvents.push(event),
};
globalThis.CustomEvent = class CustomEvent {
  constructor(type, init = {}) {
    this.type = type;
    this.detail = init.detail;
  }
};

// Now import the module under test
const {
  default: axiosInstance,
  getAuthToken,
  getAuthExpiresAt,
  setAuthToken,
  clearAuthToken,
} = await import('./axiosInstance.js');

test('setAuthToken persists token and expiry to memory and localStorage', () => {
  clearAuthToken();
  dispatchedEvents.length = 0;

  const sampleToken = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test';
  const sampleExpiry = '2026-11-08T05:00:00Z';

  setAuthToken(sampleToken, sampleExpiry);

  assert.strictEqual(getAuthToken(), sampleToken);
  assert.strictEqual(getAuthExpiresAt(), sampleExpiry);
  assert.strictEqual(globalThis.localStorage.getItem('budget_auth_token'), sampleToken);
  assert.strictEqual(globalThis.localStorage.getItem('budget_auth_expires_at'), sampleExpiry);
});

test('clearAuthToken clears token and expiry from memory and localStorage', () => {
  setAuthToken('token123', '2026-11-08T05:00:00Z');
  clearAuthToken();

  assert.strictEqual(getAuthToken(), null);
  assert.strictEqual(getAuthExpiresAt(), null);
  assert.strictEqual(globalThis.localStorage.getItem('budget_auth_token'), null);
  assert.strictEqual(globalThis.localStorage.getItem('budget_auth_expires_at'), null);
});

test('request interceptor attaches Bearer token if present', async () => {
  setAuthToken('test-bearer-token');

  // Find request interceptor
  const requestHandler = axiosInstance.interceptors.request.handlers[0].fulfilled;
  const config = { headers: {} };
  const updatedConfig = await requestHandler(config);

  assert.strictEqual(updatedConfig.headers['Authorization'], 'Bearer test-bearer-token');
});

test('request interceptor omits Authorization if no token present', async () => {
  clearAuthToken();

  const requestHandler = axiosInstance.interceptors.request.handlers[0].fulfilled;
  const config = { headers: {} };
  const updatedConfig = await requestHandler(config);

  assert.strictEqual(updatedConfig.headers['Authorization'], undefined);
});

test('response interceptor on 401 broadcasts auth:unauthorized and clears token for regular requests', async () => {
  setAuthToken('token-to-expire');
  dispatchedEvents.length = 0;

  const errorHandler = axiosInstance.interceptors.response.handlers[0].rejected;
  const fake401 = {
    response: {
      status: 401,
      data: { message: 'Unauthorized session expired' },
    },
    config: {
      url: '/v1/transactions',
    },
  };

  await assert.rejects(
    async () => await errorHandler(fake401),
    /Unauthorized session expired/
  );

  // Assert auth was cleared
  assert.strictEqual(getAuthToken(), null);
  assert.strictEqual(globalThis.localStorage.getItem('budget_auth_token'), null);

  // Assert unauthorized event was emitted
  assert.strictEqual(dispatchedEvents.length, 1);
  assert.strictEqual(dispatchedEvents[0].type, 'auth:unauthorized');
});

test('response interceptor on 401 for /v1/tokens does not broadcast auth:unauthorized', async () => {
  setAuthToken('some-token');
  dispatchedEvents.length = 0;

  const errorHandler = axiosInstance.interceptors.response.handlers[0].rejected;
  const login401 = {
    response: {
      status: 401,
      data: { message: 'Invalid Google token' },
    },
    config: {
      url: '/v1/tokens',
    },
  };

  await assert.rejects(
    async () => await errorHandler(login401),
    /Invalid Google token/
  );

  // Should NOT dispatch auth:unauthorized for login attempts
  assert.strictEqual(dispatchedEvents.length, 0);
});

test('response interceptor formats fallback error message when response data is empty', async () => {
  const errorHandler = axiosInstance.interceptors.response.handlers[0].rejected;
  const genericError = {
    message: 'Network Timeout Error',
    config: { url: '/v1/budgets' },
  };

  await assert.rejects(
    async () => await errorHandler(genericError),
    /Network Timeout Error/
  );
});

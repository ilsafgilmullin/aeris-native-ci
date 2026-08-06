const assert = require('node:assert/strict');
const test = require('node:test');
const { resolvePushTokenChange } = require('../dist/push/pushTokenChange.js');

test('registers a non-empty native push token', () => {
  assert.deepEqual(resolvePushTokenChange('native-token'), {
    type: 'register',
    token: 'native-token',
  });
});

test('disables the installation after token invalidation', () => {
  assert.deepEqual(resolvePushTokenChange(null), { type: 'disable' });
});

test('treats an empty native token as invalidated', () => {
  assert.deepEqual(resolvePushTokenChange(''), { type: 'disable' });
});

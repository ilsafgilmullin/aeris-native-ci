const assert = require('node:assert/strict');
const test = require('node:test');
const { isCompatiblePushToken } = require('../dist/push-token-compatibility.js');

const cases = [
  ['ios', 'apns', true],
  ['ios', 'voip', true],
  ['ios', 'fcm', false],
  ['android', 'apns', false],
  ['android', 'voip', false],
  ['android', 'fcm', true],
];

for (const [platform, tokenKind, expected] of cases) {
  test(`${platform} + ${tokenKind} compatibility is ${expected}`, () => {
    assert.equal(isCompatiblePushToken(platform, tokenKind), expected);
  });
}

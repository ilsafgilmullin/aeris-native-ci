const assert = require('node:assert/strict');
const test = require('node:test');
const { createInstallationId } = require('../dist/push/installationId.js');
const {
  createInstallationIdStore,
} = require('../dist/push/installationStorageCore.js');

test('createInstallationId produces an RFC 4122 version 4 identifier', () => {
  const installationId = createInstallationId((bytes) => bytes.fill(0));

  assert.equal(installationId, '00000000-0000-4000-8000-000000000000');
  assert.match(
    installationId,
    /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/,
  );
});

test('getOrCreate reuses an existing installation id', async () => {
  let writes = 0;
  let creates = 0;
  const store = createInstallationIdStore(
    {
      async read() {
        return 'existing-id';
      },
      async write() {
        writes += 1;
      },
    },
    () => {
      creates += 1;
      return 'new-id';
    },
  );

  assert.equal(await store.getOrCreate(), 'existing-id');
  assert.equal(writes, 0);
  assert.equal(creates, 0);
});

test('parallel getOrCreate calls share one read and one write', async () => {
  let reads = 0;
  let writes = 0;
  let creates = 0;
  let stored = null;
  const store = createInstallationIdStore(
    {
      async read() {
        reads += 1;
        await new Promise((resolve) => setTimeout(resolve, 10));
        return stored;
      },
      async write(value) {
        writes += 1;
        stored = value;
      },
    },
    () => {
      creates += 1;
      return 'generated-id';
    },
  );

  const results = await Promise.all([
    store.getOrCreate(),
    store.getOrCreate(),
    store.getOrCreate(),
  ]);

  assert.deepEqual(results, ['generated-id', 'generated-id', 'generated-id']);
  assert.equal(reads, 1);
  assert.equal(writes, 1);
  assert.equal(creates, 1);
});

test('getOrCreate can retry after a failed write', async () => {
  let writeAttempts = 0;
  let createAttempts = 0;
  let stored = null;
  const store = createInstallationIdStore(
    {
      async read() {
        return stored;
      },
      async write(value) {
        writeAttempts += 1;
        if (writeAttempts === 1) throw new Error('secure storage unavailable');
        stored = value;
      },
    },
    () => {
      createAttempts += 1;
      return `generated-${createAttempts}`;
    },
  );

  await assert.rejects(store.getOrCreate(), /secure storage unavailable/);
  assert.equal(await store.getOrCreate(), 'generated-2');
  assert.equal(writeAttempts, 2);
  assert.equal(createAttempts, 2);
});

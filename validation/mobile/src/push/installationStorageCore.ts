import { createInstallationId } from './installationId';

export type InstallationIdStorageAdapter = {
  read(): Promise<string | null>;
  write(value: string): Promise<void>;
};

export type InstallationIdStore = {
  read(): Promise<string | null>;
  getOrCreate(): Promise<string>;
};

export function createInstallationIdStore(
  adapter: InstallationIdStorageAdapter,
  createId: () => string = createInstallationId,
): InstallationIdStore {
  let pending: Promise<string> | null = null;

  const read = () => adapter.read();
  const getOrCreate = (): Promise<string> => {
    if (pending) return pending;

    const task = (async () => {
      const existing = await adapter.read();
      if (existing) return existing;

      const installationId = createId();
      await adapter.write(installationId);
      return installationId;
    })();

    pending = task;
    task.then(
      () => {
        if (pending === task) pending = null;
      },
      () => {
        if (pending === task) pending = null;
      },
    );
    return task;
  };

  return { read, getOrCreate };
}

import { QueryClient } from '@tanstack/react-query';
import { createAsyncStoragePersister } from '@tanstack/query-async-storage-persister';
import type { PersistQueryClientOptions } from '@tanstack/react-query-persist-client';
import { del as idbDel, get as idbGet, set as idbSet } from 'idb-keyval';

const CACHE_KEY = 'signet-rq-cache';

export const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // На 401 не ретраим — это разлогин, а не сбой сети.
      retry: (count, err) => !String((err as Error).message).includes('401') && count < 2,
      refetchOnWindowFocus: false,
      staleTime: 30_000,
      gcTime: 30 * 60_000,
    },
  },
});

// Персистер поверх IndexedDB (idb-keyval): кэш переживает перезагрузку, приложение
// стартует мгновенно из офлайн-снимка, а сеть догружает свежие данные в фоне.
const persister = createAsyncStoragePersister({
  key: CACHE_KEY,
  throttleTime: 1000,
  storage: {
    getItem: (key) => idbGet<string>(key).then((v) => v ?? null),
    setItem: (key, value) => idbSet(key, value),
    removeItem: (key) => idbDel(key),
  },
});

export const persistOptions: Omit<PersistQueryClientOptions, 'queryClient'> = {
  persister,
  maxAge: 30 * 60_000,          // офлайн-данные старше 30 мин не показываем
  buster: 'v1',                 // сменить при несовместимом изменении формата кэша
  dehydrateOptions: {
    // На диск кладём только «серверное состояние», которое из прошлого не меняется:
    // ящики, папки, списки писем, тела, вложения. Черновики (динамика) НЕ персистим.
    shouldDehydrateQuery: (q) =>
      q.state.status === 'success' && q.queryKey[0] === 'mail' && q.queryKey[1] !== 'draft',
  },
};

/**
 * Полная очистка кэша (в памяти и на диске) — при логауте и потере авторизации,
 * чтобы почта прошлой сессии не осталась в IndexedDB на общем компьютере.
 */
export async function clearOfflineCache(): Promise<void> {
  queryClient.clear();
  await persister.removeClient();
}

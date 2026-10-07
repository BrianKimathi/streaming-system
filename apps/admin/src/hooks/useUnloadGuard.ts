import { useCallback, useEffect, useState } from 'react';

/** Asks the browser to confirm before closing or reloading the tab while `active` (e.g. an upload is running). */
export function useUnloadGuard(active: boolean) {
  useEffect(() => {
    if (!active) return undefined;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
      e.returnValue = '';
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [active]);
}

/** Tracks which child inputs are busy (uploading) so a form can block submit/navigation until all are done. */
export function useBusyKeys() {
  const [keys, setKeys] = useState<string[]>([]);
  const setBusy = useCallback((key: string, busy: boolean) => {
    setKeys((current) => {
      const has = current.includes(key);
      if (busy === has) return current;
      return busy ? [...current, key] : current.filter((k) => k !== key);
    });
  }, []);
  return { busy: keys.length > 0, setBusy };
}

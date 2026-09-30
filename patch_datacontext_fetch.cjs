const fs = require('fs');
let code = fs.readFileSync('src/context/DataContext.tsx', 'utf8');

const regex = /const doFetch = async \(\) => \{[\s\S]*?(?=fetchDevicesPromiseRef\.current = doFetch\(\);)/;
const replacement = `fetchDevicesVersion.current += 1;
    const currentVersion = fetchDevicesVersion.current;

    const doFetch = async () => {
    // 1. Try reading existing sync cache from IndexedDB / localStorage
    let cachedSync = null;
    try {
      cachedSync = await getDeviceSyncCache();
    } catch (e) {
      console.warn('[DataContext] Error reading local device sync cache:', e);
    }
    const hasValidLocalCache = !!(cachedSync && Array.isArray(cachedSync.devices) && cachedSync.devices.length > 0 && cachedSync.lastSyncTimestamp);

    // 2. Incremental Sync path (if not forcing full refresh and local data exists)
    if (!force && hasValidLocalCache && cachedSync) {
      // Hydrate memory state immediately if currently empty
      if (devicesRef.current.length === 0) {
        devicesRef.current = cachedSync.devices;
        setDevices(cachedSync.devices);
        hasLoadedDevicesRef.current = true;
        setHasLoadedDevices(true);
      }

      setLoadingDevices(true);
      try {
        const res = await api.getDevices({
          updated_after: cachedSync.lastSyncTimestamp
        });
        
        if (currentVersion !== fetchDevicesVersion.current) return devicesRef.current;

        if (res.success) {
          const newSyncTimestamp = res.last_sync_timestamp || new Date().toISOString();
          if (res.is_delta) {
            if (Array.isArray(res.data) && res.data.length > 0) {
              const baseList = devicesRef.current.length > 0 ? devicesRef.current : cachedSync.devices;
              const merged = mergeDeviceDelta(baseList, res.data);
              devicesRef.current = merged;
              hasLoadedDevicesRef.current = true;
              setDevices(merged);
              setHasLoadedDevices(true);
              await saveDeviceSyncCache(merged, newSyncTimestamp);
              return merged;
            } else {
              // Server has 0 updates -> maintain local cache with updated timestamp
              await saveDeviceSyncCache(devicesRef.current.length > 0 ? devicesRef.current : cachedSync.devices, newSyncTimestamp);
              return devicesRef.current.length > 0 ? devicesRef.current : cachedSync.devices;
            }
          } else if (Array.isArray(res.data)) {
            // Full list fallback from server
            devicesRef.current = res.data;
            hasLoadedDevicesRef.current = true;
            setDevices(res.data);
            setHasLoadedDevices(true);
            await saveDeviceSyncCache(res.data, newSyncTimestamp);
            return res.data;
          }
        }
      } catch (deltaErr: any) {
        if (currentVersion !== fetchDevicesVersion.current) return devicesRef.current;
        if (deltaErr?.status === 401 || deltaErr?.message?.includes('đăng nhập')) {
          return [];
        }
        console.warn('[DataContext] Incremental sync error, falling back to cached devices:', deltaErr);
        return devicesRef.current.length > 0 ? devicesRef.current : cachedSync.devices;
      } finally {
        if (currentVersion === fetchDevicesVersion.current) setLoadingDevices(false);
      }
      return devicesRef.current;
    }

    // 3. Full Fetch path (force === true OR no valid local cache)
    if (force) {
      devicesRef.current = [];
    }
    setLoadingDevices(true);
    try {
      const res = await api.getDevices({ limit: 1000, forceRefresh: true });
      if (currentVersion !== fetchDevicesVersion.current) return devicesRef.current;

      if (res.success && Array.isArray(res.data)) {
        const newSyncTimestamp = res.last_sync_timestamp || new Date().toISOString();
        devicesRef.current = res.data;
        hasLoadedDevicesRef.current = true;
        setDevices(res.data);
        setHasLoadedDevices(true);
        await saveDeviceSyncCache(res.data, newSyncTimestamp);
        return res.data;
      }
    } catch (err: any) {
      if (currentVersion !== fetchDevicesVersion.current) return devicesRef.current;
      if (err?.status === 401 || err?.message?.includes('đăng nhập')) {
        return [];
      }
      console.error('[DataContext] Error fetching devices full list:', err);
    } finally {
      if (currentVersion === fetchDevicesVersion.current) setLoadingDevices(false);
    }
    return devicesRef.current;
    };

    `;

code = code.replace(regex, replacement);
fs.writeFileSync('src/context/DataContext.tsx', code);

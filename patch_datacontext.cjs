const fs = require('fs');
let code = fs.readFileSync('src/context/DataContext.tsx', 'utf8');

const fetchDevicesTop = `  // Fetch Devices with Incremental / Delta Sync
  const fetchDevices = useCallback(async (force = false): Promise<Device[]> => {
    if (!getAuthToken()) {
      return [];
    }`;

const fetchDevicesNew = `  const fetchDevicesPromiseRef = useRef<Promise<Device[]> | null>(null);

  // Fetch Devices with Incremental / Delta Sync
  const fetchDevices = useCallback(async (force = false): Promise<Device[]> => {
    if (!getAuthToken()) {
      return [];
    }

    if (fetchDevicesPromiseRef.current && !force) {
      return fetchDevicesPromiseRef.current;
    }

    const doFetch = async () => {`;
code = code.replace(fetchDevicesTop, fetchDevicesNew);

// Now we need to wrap the rest of fetchDevices in doFetch
const fetchDevicesEndOrig = `      console.error('[DataContext] Error fetching devices full list:', err);
    } finally {
      setLoadingDevices(false);
    }
    return devicesRef.current;
  }, []);`;

const fetchDevicesEndNew = `      console.error('[DataContext] Error fetching devices full list:', err);
    } finally {
      setLoadingDevices(false);
    }
    return devicesRef.current;
    };

    fetchDevicesPromiseRef.current = doFetch();
    try {
      return await fetchDevicesPromiseRef.current;
    } finally {
      fetchDevicesPromiseRef.current = null;
    }
  }, []);`;

code = code.replace(fetchDevicesEndOrig, fetchDevicesEndNew);

fs.writeFileSync('src/context/DataContext.tsx', code);

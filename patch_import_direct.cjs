const fs = require('fs');
let code = fs.readFileSync('src/lib/api.ts', 'utf8');

const regex = /importDirect:\s*\([\s\S]*?body:\s*JSON\.stringify\(\{\s*rows\s*\}\)\n\s*\}\),/;
const replacement = `importDirect: async (rows: any[]) => {
    const res = await request<{
      success: boolean;
      message: string;
      report: ImportReport;
    }>('/import/direct', {
      method: 'POST',
      body: JSON.stringify({ rows })
    });
    if (res.success) {
      clearInFlightRequests('/devices');
      await invalidateCacheByPrefix('/devices');
      await invalidateCacheByPrefix('/dashboard/stats');
      await invalidateRelatedDeviceCache();
    }
    return res;
  },`;

code = code.replace(regex, replacement);
fs.writeFileSync('src/lib/api.ts', code);

const fs = require('fs');
let code = fs.readFileSync('src/lib/api.ts', 'utf8');

const regex = /const inFlightRequests = new Map<string, Promise<any>>\(\);\n\n/;
const replacement = 'const inFlightRequests = new Map<string, Promise<any>>();\n\nexport function clearInFlightRequests(prefix?: string) {\n  if (!prefix) {\n    inFlightRequests.clear();\n  } else {\n    for (const key of inFlightRequests.keys()) {\n      if (key.includes(prefix)) {\n        inFlightRequests.delete(key);\n      }\n    }\n  }\n}\n\n';
code = code.replace(regex, replacement);

const importDirectOrig = `  importDirect: async (rows: any[]) => {
    const res = await request<{
      success: boolean;
      message: string;
      report: ImportReport;
    }>('/import/direct', {
      method: 'POST',
      body: JSON.stringify({ rows })
    });
    if (res.success) {
      await invalidateCacheByPrefix('/devices');
      await invalidateCacheByPrefix('/dashboard/stats');
    }
    return res;
  },`;

const importDirectNew = `  importDirect: async (rows: any[]) => {
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

code = code.replace(importDirectOrig, importDirectNew);

fs.writeFileSync('src/lib/api.ts', code);

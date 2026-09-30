const fs = require('fs');
let code = fs.readFileSync('src/lib/api.ts', 'utf8');

const regex = /clearInFlightRequests\\('\/devices'\\);\n\s*await invalidateCacheByPrefix\\('\/devices'\\);\n\s*await invalidateCacheByPrefix\\('\/dashboard\/stats'\\);\n\s*await invalidateRelatedDeviceCache\\(\\);/;
const replacement = `clearInFlightRequests('/devices');
      await invalidateCacheByPrefix('/devices');
      await invalidateCacheByPrefix('/dashboard/stats');
      await invalidateCacheByPrefix('/feeders');
      await invalidateCacheByPrefix('/substations');`;

code = code.replace(regex, replacement);
fs.writeFileSync('src/lib/api.ts', code);

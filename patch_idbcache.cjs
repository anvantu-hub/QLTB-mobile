const fs = require('fs');
let code = fs.readFileSync('src/lib/idbCache.ts', 'utf8');

code = code.replace(/export async function invalidateRelatedDeviceCache\\(device: any\\)/, 'export async function invalidateRelatedDeviceCache(device?: any)');
fs.writeFileSync('src/lib/idbCache.ts', code);

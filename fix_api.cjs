const fs = require('fs');
let code = fs.readFileSync('src/lib/api.ts', 'utf8');

code = code.replace('await invalidateRelatedDeviceCache();', 'await invalidateCacheByPrefix(\'/feeders\');\n      await invalidateCacheByPrefix(\'/substations\');');
fs.writeFileSync('src/lib/api.ts', code);

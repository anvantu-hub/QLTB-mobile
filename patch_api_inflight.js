const fs = require('fs');
let code = fs.readFileSync('src/lib/api.ts', 'utf8');

const regex = /const inFlightRequests = new Map<string, Promise<any>>\(\);\n\n/;
const replacement = 'const inFlightRequests = new Map<string, Promise<any>>();\n\nexport function clearInFlightRequests(prefix?: string) {\n  if (!prefix) {\n    inFlightRequests.clear();\n  } else {\n    for (const key of inFlightRequests.keys()) {\n      if (key.includes(prefix)) {\n        inFlightRequests.delete(key);\n      }\n    }\n  }\n}\n\n';
code = code.replace(regex, replacement);

fs.writeFileSync('src/lib/api.ts', code);

const fs = require('fs');
let code = fs.readFileSync('src/context/DataContext.tsx', 'utf8');

const regex = /if \\(force\\) \{\n      devicesRef\.current = \[\];\n    \}/;
const replacement = `if (force) {
      devicesRef.current = [];
      setDevices([]);
      setHasLoadedDevices(false);
    }`;

code = code.replace(regex, replacement);
fs.writeFileSync('src/context/DataContext.tsx', code);

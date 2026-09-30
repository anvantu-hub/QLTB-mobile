const fs = require('fs');
let code = fs.readFileSync('src/context/DataContext.tsx', 'utf8');

if (!code.includes('const fetchDevicesVersion = useRef(0);')) {
    const fetchDevicesPromiseRef = 'const fetchDevicesPromiseRef = useRef<Promise<Device[]> | null>(null);';
    code = code.replace(fetchDevicesPromiseRef, 'const fetchDevicesPromiseRef = useRef<Promise<Device[]> | null>(null);\n  const fetchDevicesVersion = useRef(0);');
    fs.writeFileSync('src/context/DataContext.tsx', code);
}

const fs = require('fs');
let code = fs.readFileSync('src/pages/ImportPage.tsx', 'utf8');

const importAuth = `import { useAuth } from '../context/AuthContext';`;
const newImports = `import { useAuth } from '../context/AuthContext';\nimport { useDataContext } from '../context/DataContext';`;
code = code.replace(importAuth, newImports);

const authDestructure = `  const { user, hasRole } = useAuth();`;
const newDestructure = `  const { user, hasRole } = useAuth();\n  const { fetchDevices, fetchSubstations, fetchFeeders } = useDataContext();`;
code = code.replace(authDestructure, newDestructure);

fs.writeFileSync('src/pages/ImportPage.tsx', code);

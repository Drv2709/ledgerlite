// Fails the build if the TypeScript type "any" is written anywhere in src/.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';

const PATTERN = /(:\s*any\b|\bas\s+any\b|<any\b|\bany\[\]|\bany>)/;

function* walk(dir) {
  for (const name of readdirSync(dir)) {
    const path = join(dir, name);
    if (statSync(path).isDirectory()) {
      yield* walk(path);
    } else if (/\.(ts|tsx)$/.test(name)) {
      yield path;
    }
  }
}

const offenders = [];
for (const file of walk('src')) {
  readFileSync(file, 'utf8')
    .split('\n')
    .forEach((line, index) => {
      if (PATTERN.test(line)) offenders.push(`${file}:${index + 1}: ${line.trim()}`);
    });
}

if (offenders.length > 0) {
  console.error('Found "any" types:\n' + offenders.join('\n'));
  process.exit(1);
}
console.log('check:no-any OK - no "any" types in src/');

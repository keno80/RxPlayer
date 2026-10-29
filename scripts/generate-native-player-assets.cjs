// Run with Node.js and sharp available via NODE_PATH. No app build is involved.
const fs = require('node:fs');
const path = require('node:path');
const sharp = require('sharp');
const root = path.resolve(__dirname, '..');
const dir = path.join(root, 'composeApp/src/commonMain/composeResources/drawable');
const paletteText = fs.readFileSync(path.join(root, 'design/prototype-v3/cover-palettes.js'), 'utf8');
const palettes = JSON.parse(paletteText.slice(paletteText.indexOf('{')).trim().replace(/;$/, '')).covers;
const names = ['night-voyage', 'sea-letter', 'sunset-session'];

async function main() {
  fs.mkdirSync(dir, { recursive: true });
  for (const name of names) {
    await sharp(path.join(root, `design/prototype-v2/assets/${name}.svg`))
      .png().toFile(path.join(dir, `${name.replaceAll('-', '_')}.png`));
  }
  const properties = {
    primary:'primary', 'on-primary':'onPrimary', 'primary-container':'primaryContainer',
    'on-primary-container':'onPrimaryContainer', 'secondary-container':'secondaryContainer',
    'on-secondary-container':'onSecondaryContainer', 'tertiary-container':'tertiaryContainer',
    'on-tertiary-container':'onTertiaryContainer',
  };
  const lines = ['// Generated from prototype-v3/cover-palettes.js; only bundled demo images.',
    'package com.keno.rxplayer.ui', '', 'import androidx.compose.material3.ColorScheme',
    'import androidx.compose.ui.graphics.Color', '',
    'internal fun coverColors(base: ColorScheme, dark: Boolean, artwork: Int): ColorScheme = when (artwork) {'];
  names.forEach((name,index) => {
    const palette = palettes.find(p=>p.art===`${name}.svg`);
    const copy = mode=>'base.copy('+Object.entries(palette[mode]).map(([key,value])=>`${properties[key]} = Color(0xFF${value.slice(1).toUpperCase()})`).join(', ')+')';
    lines.push(`    ${index} -> if (dark) ${copy('dark')} else ${copy('light')}`);
  });
  lines.push('    else -> base', '}', '');
  fs.writeFileSync(path.join(root,'composeApp/src/commonMain/kotlin/com/keno/rxplayer/ui/CoverColors.kt'),lines.join('\n'));
}
main().catch(error=>{console.error(error); process.exitCode=1;});

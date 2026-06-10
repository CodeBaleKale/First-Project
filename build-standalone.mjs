// Builds PanPals.html — a single self-contained file (styles, data, and
// app inlined) that runs from a double-click. Re-run after editing any
// source file:  node build-standalone.mjs
import { readFileSync, writeFileSync } from "node:fs";

function inject(src, marker, replacement) {
  if (!src.includes(marker)) throw new Error(`marker not found: ${marker}`);
  // Replacer callback so "$" sequences in code aren't treated as patterns.
  return src.replace(marker, () => replacement);
}

let html = readFileSync("index.html", "utf8");
html = inject(html, '<link rel="stylesheet" href="css/styles.css">', `<style>\n${readFileSync("css/styles.css", "utf8")}</style>`);
html = inject(html, '<script src="js/data.js"></script>', `<script>\n${readFileSync("js/data.js", "utf8")}</script>`);
html = inject(html, '<script src="js/app.js"></script>', `<script>\n${readFileSync("js/app.js", "utf8")}</script>`);

writeFileSync("PanPals.html", html);
console.log(`Wrote PanPals.html (${(html.length / 1024).toFixed(1)} kB)`);

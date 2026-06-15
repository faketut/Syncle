// Copies shared world assets from the Android app's asset folder into web/public/
// so Vite can serve them at `/map_config.json` and `/room1.jpg`. Keeping a single
// source of truth (the Android assets dir) avoids drift between the two clients.
import { cp, mkdir } from "node:fs/promises";
import { existsSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const webDir = resolve(here, "..");
const assetsDir = resolve(webDir, "..", "app", "src", "main", "assets");
const publicDir = resolve(webDir, "public");

const targets = ["map_config.json", "room1.jpg"];

await mkdir(publicDir, { recursive: true });

for (const name of targets) {
  const src = resolve(assetsDir, name);
  const dst = resolve(publicDir, name);
  if (!existsSync(src)) {
    console.error(`[sync-assets] missing source: ${src}`);
    process.exit(1);
  }
  await cp(src, dst);
  console.log(`[sync-assets] ${name}`);
}

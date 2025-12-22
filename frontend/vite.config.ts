import { defineConfig } from "vite";
import solid from "vite-plugin-solid";
import { fileURLToPath } from "url";
import { dirname, resolve } from "path";

// @ts-ignore
const rootDir = dirname(fileURLToPath(import.meta.url));

export default defineConfig({
  plugins: [solid()],
  base: "/",
  build: {
    outDir: resolve(rootDir, "..", "src", "main", "resources", "static"),
    emptyOutDir: true
  }
});

#!/usr/bin/env node
"use strict";

// Prepare local sound and music EPK inputs from an existing official Minecraft
// Java 26.2 asset cache. This script does not download the audio files.

const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const crypto = require("node:crypto");
const { spawnSync } = require("node:child_process");

const VERSION = "26.2";
const MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
const REPO = path.resolve(__dirname, "..");
const LOCAL_ROOT = path.join(REPO, ".eaglefine-audio");
const INPUT_ROOT = path.join(LOCAL_ROOT, "epk-input");
const FFMPEG = process.env.FFMPEG || "ffmpeg";
const WORKERS = 4;

function fail(message) {
  console.error("[audio-restore] ERROR: " + message);
  process.exit(1);
}

function sha1(bytes) {
  return crypto.createHash("sha1").update(bytes).digest("hex");
}

function defaultAssetsRoot() {
  const home = process.env.USERPROFILE || process.env.HOME || "";
  if (process.platform === "win32") {
    const roaming = process.env.APPDATA || path.join(home, "AppData", "Roaming");
    return path.join(roaming, ".minecraft", "assets");
  }
  if (process.platform === "darwin") {
    return path.join(home, "Library", "Application Support", "minecraft", "assets");
  }
  return path.join(home, ".minecraft", "assets");
}

function readAssetsArgument() {
  const args = process.argv.slice(2);
  let root = defaultAssetsRoot();
  for (let i = 0; i < args.length; ++i) {
    if (args[i] === "--assets") {
      if (!args[i + 1]) fail("--assets requires the path to your Minecraft assets directory.");
      root = path.resolve(args[++i]);
    } else {
      fail("unknown argument: " + args[i]);
    }
  }
  return root;
}

async function getJson(url) {
  const response = await fetch(url, { headers: { "User-Agent": "EagleFine-local-audio-restorer" } });
  if (!response.ok) throw new Error("HTTP " + response.status + " fetching version metadata");
  return response.json();
}

function verifyFfmpeg() {
  const version = spawnSync(FFMPEG, ["-version"], { encoding: "utf8", windowsHide: true });
  if (version.error) {
    fail("FFmpeg was not found. Install FFmpeg 6.1.1 or newer with libopus support, then put it on PATH or set FFMPEG to its executable path.");
  }
  if (version.status !== 0) fail("FFmpeg could not start: " + (version.stderr || version.stdout || "unknown error"));
  const banner = (version.stdout || "") + "\n" + (version.stderr || "");
  const match = banner.match(/ffmpeg version\s+(?:n)?(\d+)\.(\d+)(?:\.(\d+))?/i);
  if (!match) fail("Could not identify the FFmpeg version.");
  const actual = [Number(match[1]), Number(match[2]), Number(match[3] || 0)];
  const tooOld = actual[0] < 6
    || (actual[0] === 6 && actual[1] < 1)
    || (actual[0] === 6 && actual[1] === 1 && actual[2] < 1);
  if (tooOld) fail("FFmpeg 6.1.1 or newer is required; found " + actual.join(".") + ".");
  const encoders = spawnSync(FFMPEG, ["-hide_banner", "-encoders"], { encoding: "utf8", windowsHide: true });
  const encoderText = (encoders.stdout || "") + "\n" + (encoders.stderr || "");
  if (encoders.status !== 0 || !encoderText.includes("libopus")) {
    fail("The selected FFmpeg build does not expose the libopus encoder.");
  }
  console.log("[audio-restore] Using FFmpeg " + actual.join(".") + " (" + FFMPEG + ")");
}

function safeOutputPath(root, relative) {
  if (relative.includes("\\") || relative.split("/").some(part => part === ".." || part === "." || part === "")) {
    fail("unsafe asset path in Minecraft's asset index: " + relative);
  }
  const output = path.resolve(root, ...relative.split("/"));
  const fromRoot = path.relative(root, output);
  if (fromRoot === "" || fromRoot === ".." || fromRoot.startsWith(".." + path.sep) || path.isAbsolute(fromRoot)) {
    fail("asset path escaped the output directory: " + relative);
  }
  return output;
}

async function verifyLocalObject(assetsRoot, object) {
  const source = path.join(assetsRoot, "objects", object.hash.slice(0, 2), object.hash);
  let bytes;
  try {
    bytes = await fsp.readFile(source);
  } catch (error) {
    if (error.code === "ENOENT") {
      throw new Error("missing local game asset " + object.name + ". Launch your official Minecraft Java 26.2 installation once so the launcher has downloaded its assets, or use --assets PATH to point to that install's assets directory.");
    }
    throw error;
  }
  if (bytes.length !== object.size) throw new Error("local asset size mismatch: " + object.name);
  if (sha1(bytes) !== object.hash) throw new Error("local asset SHA-1 mismatch: " + object.name);
  return source;
}

function transcodeMusic(input, output) {
  const result = spawnSync(FFMPEG, [
    "-hide_banner",
    "-loglevel", "error",
    "-y",
    "-i", input,
    "-map", "0:a:0",
    "-vn",
    "-c:a", "libopus",
    "-b:a", "128k",
    "-vbr", "on",
    "-compression_level", "10",
    "-f", "ogg",
    output
  ], { encoding: "utf8", windowsHide: true, maxBuffer: 8 * 1024 * 1024 });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error("FFmpeg failed for " + path.basename(input) + ": " + (result.stderr || "unknown error"));
  }
}

async function run() {
  verifyFfmpeg();
  const assetsRoot = readAssetsArgument();
  if (!fs.existsSync(assetsRoot) || !fs.statSync(assetsRoot).isDirectory()) {
    fail("Minecraft assets directory not found: " + assetsRoot + ". Use --assets PATH to supply your local Java Edition assets folder.");
  }

  console.log("[audio-restore] Resolving Minecraft Java " + VERSION + " asset-index metadata...");
  const manifest = await getJson(MANIFEST_URL);
  const version = (manifest.versions || []).find(entry => entry.id === VERSION);
  if (!version || !version.url) fail("Minecraft Java " + VERSION + " was not found in the official version manifest.");

  const versionInfo = await getJson(version.url);
  if (!versionInfo.assetIndex || !versionInfo.assetIndex.id) fail("The selected version has no asset-index ID.");
  const indexFile = path.join(assetsRoot, "indexes", versionInfo.assetIndex.id + ".json");
  let indexBytes;
  try {
    indexBytes = await fsp.readFile(indexFile);
  } catch (error) {
    if (error.code === "ENOENT") {
      fail("The local asset index is missing: " + indexFile + ". Launch official Minecraft Java 26.2 once, then rerun this helper.");
    }
    throw error;
  }
  if (versionInfo.assetIndex.sha1 && sha1(indexBytes) !== versionInfo.assetIndex.sha1) {
    fail("The local Minecraft asset index failed SHA-1 verification.");
  }

  const assetIndex = JSON.parse(indexBytes.toString("utf8"));
  if (!assetIndex.objects) fail("The local asset index has no object table.");
  const prefix = "minecraft/sounds/";
  const objects = Object.entries(assetIndex.objects)
    .filter(([name]) => name.startsWith(prefix) && name.toLowerCase().endsWith(".ogg"))
    .map(([name, value]) => ({
      name,
      relative: name.slice(prefix.length),
      hash: value.hash,
      size: value.size
    }))
    .filter(item => /^[0-9a-f]{40}$/i.test(item.hash) && Number.isInteger(item.size) && item.size > 0);

  const music = objects.filter(item => item.relative.startsWith("music/") || item.relative.startsWith("records/"));
  const effects = objects.filter(item => !item.relative.startsWith("music/") && !item.relative.startsWith("records/"));
  if (effects.length === 0 || music.length === 0) {
    fail("The local Java 26.2 asset index did not contain sound effects and music/record OGG files.");
  }

  await fsp.rm(INPUT_ROOT, { recursive: true, force: true });
  const effectsRoot = path.join(INPUT_ROOT, "sounds", "assets", "minecraft", "sounds");
  const musicRoot = path.join(INPUT_ROOT, "music", "assets", "minecraft", "sounds");
  await fsp.mkdir(effectsRoot, { recursive: true });
  await fsp.mkdir(musicRoot, { recursive: true });

  console.log("[audio-restore] Found " + effects.length + " effects and " + music.length + " music/record files.");
  let next = 0;
  let completed = 0;
  const workers = Array.from({ length: Math.min(WORKERS, objects.length) }, async () => {
    while (true) {
      const index = next++;
      if (index >= objects.length) return;
      const item = objects[index];
      const source = await verifyLocalObject(assetsRoot, item);

      if (item.relative.startsWith("music/") || item.relative.startsWith("records/")) {
        const destination = safeOutputPath(musicRoot, item.relative);
        await fsp.mkdir(path.dirname(destination), { recursive: true });
        transcodeMusic(source, destination);
      } else {
        const destination = safeOutputPath(effectsRoot, item.relative);
        await fsp.mkdir(path.dirname(destination), { recursive: true });
        await fsp.copyFile(source, destination);
      }

      completed++;
      if (completed % 100 === 0 || completed === objects.length) {
        console.log("[audio-restore] Prepared " + completed + "/" + objects.length + " files.");
      }
    }
  });
  await Promise.all(workers);

  console.log("[audio-restore] Prepared local audio inputs under .eaglefine-audio/epk-input.");
  console.log("[audio-restore] Build locally with:");
  console.log("  node wasm-toolchain/build-single-html.js --with-audio --with-music --output EagleFine-26.2-audio.html");
  console.log("[audio-restore] No sound files were downloaded or added to the repository. Keep the audio build local.");
}

run().catch(error => fail(error && error.stack ? error.stack : String(error)));

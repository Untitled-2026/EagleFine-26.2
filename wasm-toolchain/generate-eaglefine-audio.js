#!/usr/bin/env node
"use strict";

// Create an original, self-contained EagleFine audio pack.
// Effects are synthesized locally by this script and encoded as Ogg Vorbis.
// Music is generated locally and encoded as Ogg Opus for the browser's native decoder.
// No Minecraft installation, Mojang audio files, samples, or network downloads are used.

const fs = require("node:fs");
const fsp = require("node:fs/promises");
const path = require("node:path");
const { spawnSync } = require("node:child_process");

const ROOT = path.resolve(__dirname, "..");
const MANIFEST = path.join(ROOT, "game", "src", "main", "resources", "assets", "minecraft", "sounds.json");
const OUTPUT = path.join(ROOT, ".eaglefine-audio", "epk-input");
const WORK = path.join(ROOT, ".eaglefine-audio", "work");
const FFMPEG = process.env.FFMPEG || "ffmpeg";
const EFFECT_RATE = 22050;
const MUSIC_RATE = 32000;

const EFFECT_FAMILIES = [
  "stone_break", "stone_step", "stone_place",
  "wood_break", "wood_step", "wood_place",
  "dirt_break", "dirt_step", "dirt_place",
  "sand_break", "sand_step", "sand_place",
  "glass_break", "metal_hit", "crystal_chime",
  "soft_thud", "player_impact", "creature_impact",
  "animal_call", "creature_call", "water_splash", "water_bubble",
  "fire_crackle", "ui_click", "item_pickup", "item_use",
  "item_equip", "projectile", "explosion", "door_wood",
  "door_metal", "portal", "wind", "cave", "rain",
  "levelup", "food_eat", "armor_equip", "anvil_clang",
  "thunder", "firework"
];

const MUSIC_TRACKS = [
  { name: "overworld_1", theme: "overworld", seed: 11, duration: 58 },
  { name: "overworld_2", theme: "overworld", seed: 29, duration: 58 },
  { name: "menu", theme: "menu", seed: 47, duration: 48 },
  { name: "creative", theme: "bright", seed: 61, duration: 52 },
  { name: "underwater", theme: "underwater", seed: 73, duration: 54 },
  { name: "nether", theme: "minor", seed: 89, duration: 52 },
  { name: "end", theme: "ethereal", seed: 103, duration: 58 },
  { name: "credits", theme: "warm", seed: 127, duration: 60 },
  { name: "record_1", theme: "record", seed: 149, duration: 42 },
  { name: "record_2", theme: "record", seed: 167, duration: 42 },
  { name: "record_3", theme: "record", seed: 181, duration: 42 },
  { name: "record_4", theme: "record", seed: 199, duration: 42 }
];

function fail(message) {
  console.error("[eaglefine-audio] ERROR: " + message);
  process.exit(1);
}

function runFfmpeg(args) {
  const result = spawnSync(FFMPEG, args, {
    encoding: "utf8",
    windowsHide: true,
    maxBuffer: 8 * 1024 * 1024
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error((result.stderr || result.stdout || "FFmpeg failed").trim());
  }
}

function verifyFfmpeg() {
  const version = spawnSync(FFMPEG, ["-version"], {
    encoding: "utf8",
    windowsHide: true
  });
  if (version.error || version.status !== 0) {
    fail("FFmpeg was not found. Install FFmpeg with libvorbis and libopus support, then rerun this build.");
  }
  const encoders = spawnSync(FFMPEG, ["-hide_banner", "-encoders"], {
    encoding: "utf8",
    windowsHide: true
  });
  const available = (encoders.stdout || "") + "\n" + (encoders.stderr || "");
  if (encoders.status !== 0 || !available.includes("libvorbis") || !available.includes("libopus")) {
    fail("The selected FFmpeg build must include both the libvorbis and libopus encoders.");
  }
  const line = ((version.stdout || "").split(/\r?\n/)[0] || "FFmpeg");
  console.log("[eaglefine-audio] " + line);
}

function hashText(value) {
  let hash = 2166136261;
  for (let i = 0; i < value.length; i++) {
    hash ^= value.charCodeAt(i);
    hash = Math.imul(hash, 16777619);
  }
  return hash >>> 0;
}

function randomFor(seed) {
  let state = seed >>> 0 || 1;
  return function () {
    state ^= state << 13;
    state ^= state >>> 17;
    state ^= state << 5;
    return (state >>> 0) / 4294967296;
  };
}

function midiFrequency(note) {
  return 440 * Math.pow(2, (note - 69) / 12);
}

function writeWav(filename, samples, sampleRate, channels) {
  const frameCount = Math.floor(samples.length / channels);
  const dataBytes = frameCount * channels * 2;
  const header = Buffer.alloc(44);
  header.write("RIFF", 0);
  header.writeUInt32LE(36 + dataBytes, 4);
  header.write("WAVE", 8);
  header.write("fmt ", 12);
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20);
  header.writeUInt16LE(channels, 22);
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(sampleRate * channels * 2, 28);
  header.writeUInt16LE(channels * 2, 32);
  header.writeUInt16LE(16, 34);
  header.write("data", 36);
  header.writeUInt32LE(dataBytes, 40);
  const pcm = Buffer.allocUnsafe(dataBytes);
  for (let i = 0; i < frameCount * channels; i++) {
    const sample = Math.max(-0.98, Math.min(0.98, samples[i] || 0));
    pcm.writeInt16LE(Math.round(sample * 32767), i * 2);
  }
  fs.writeFileSync(filename, Buffer.concat([header, pcm]));
}

function synthesizeEffect(family, variant) {
  const long = family === "wind" || family === "cave" || family === "rain";
  const duration = family === "cave" ? 2.5
    : family === "wind" ? 2.1
    : family === "rain" ? 1.35
    : family === "portal" ? 0.95
    : family === "firework" || family === "explosion" || family === "thunder" ? 0.72
    : family === "glass_break" || family === "crystal_chime" || family === "anvil_clang" ? 0.55
    : family === "animal_call" || family === "creature_call" ? 0.48
    : family === "door_wood" || family === "door_metal" ? 0.58
    : family === "levelup" ? 0.46
    : 0.25;
  const count = Math.floor(EFFECT_RATE * duration);
  const data = new Float32Array(count);
  const rng = randomFor(hashText(family + ":" + variant));
  let lowNoise = 0;
  let previousNoise = 0;
  const variation = variant === 1 ? 0.96 : 1.045;

  for (let i = 0; i < count; i++) {
    const t = i / EFFECT_RATE;
    const p = t / duration;
    const noise = rng() * 2 - 1;
    lowNoise += 0.085 * (noise - lowNoise);
    const highNoise = noise - lowNoise;
    const attack = Math.min(1, t * (family === "ui_click" ? 900 : 80));
    const tail = Math.exp(-t * (long ? 1.35 : family === "glass_break" ? 4.2 : 8.5));
    const sweep = 1 - 0.55 * p;
    const base = 90 + (hashText(family) % 7) * 31;
    const phase = 2 * Math.PI * base * variation * (t - 0.18 * t * t);
    const tone = Math.sin(phase);
    const overtone = Math.sin(phase * 2.37 + 0.3);
    let value = 0;

    if (family.startsWith("stone_")) {
      value = highNoise * 0.34 * tail + tone * 0.2 * tail + overtone * 0.07 * tail;
    } else if (family.startsWith("wood_") || family === "door_wood") {
      value = highNoise * 0.24 * tail + Math.sin(2 * Math.PI * 175 * variation * t) * 0.34 * tail
        + Math.sin(2 * Math.PI * 292 * variation * t) * 0.13 * tail;
    } else if (family.startsWith("dirt_") || family.startsWith("sand_")) {
      value = highNoise * (family.startsWith("sand_") ? 0.3 : 0.23) * tail
        + tone * 0.11 * tail + Math.sin(2 * Math.PI * 62 * t) * 0.1 * tail;
    } else if (family === "glass_break") {
      value = highNoise * 0.12 * tail
        + Math.sin(2 * Math.PI * 920 * variation * t) * 0.19 * tail
        + Math.sin(2 * Math.PI * 1370 * variation * t) * 0.14 * tail
        + Math.sin(2 * Math.PI * 1810 * variation * t) * 0.09 * tail;
    } else if (family === "crystal_chime" || family === "levelup" || family === "item_pickup") {
      const rise = family === "levelup" || family === "item_pickup";
      const f = (family === "crystal_chime" ? 880 : 620) * variation * (rise ? 1 + p * 0.9 : 1);
      const env = Math.exp(-t * (family === "levelup" ? 3.8 : 5.5));
      value = Math.sin(2 * Math.PI * f * t) * 0.26 * env
        + Math.sin(2 * Math.PI * f * 2.01 * t) * 0.11 * env;
    } else if (family === "metal_hit" || family === "door_metal" || family === "armor_equip" || family === "anvil_clang") {
      const f = family === "anvil_clang" ? 390 : 610;
      value = Math.sin(2 * Math.PI * f * variation * t) * 0.24 * tail
        + Math.sin(2 * Math.PI * f * 1.73 * variation * t) * 0.16 * tail
        + Math.sin(2 * Math.PI * f * 2.62 * variation * t) * 0.08 * tail
        + highNoise * 0.11 * tail;
    } else if (family === "soft_thud" || family === "player_impact" || family === "creature_impact") {
      value = Math.sin(2 * Math.PI * (72 + 38 * sweep) * t) * 0.34 * tail
        + highNoise * 0.12 * Math.exp(-t * 21);
    } else if (family === "animal_call" || family === "creature_call") {
      const f = (family === "animal_call" ? 430 : 170) * variation * (1 + 0.35 * Math.sin(t * 8));
      value = Math.sin(2 * Math.PI * f * t) * 0.21 * tail
        + Math.sin(2 * Math.PI * f * 1.51 * t) * 0.09 * tail
        + Math.sin(2 * Math.PI * f * 2.03 * t) * 0.045 * tail
        + highNoise * 0.045 * tail;
    } else if (family === "water_splash" || family === "water_bubble") {
      const f = family === "water_bubble" ? 170 + 260 * p : 260 + 420 * p;
      value = lowNoise * 0.24 * tail + highNoise * 0.12 * Math.exp(-t * 12)
        + Math.sin(2 * Math.PI * f * t) * 0.11 * tail;
    } else if (family === "fire_crackle" || family === "rain") {
      const crackle = rng() > (family === "rain" ? 0.78 : 0.92) ? noise : noise * 0.22;
      value = crackle * (family === "rain" ? 0.19 : 0.34) * (long ? 0.8 : tail);
    } else if (family === "ui_click") {
      value = highNoise * 0.19 * Math.exp(-t * 180) + Math.sin(2 * Math.PI * 1120 * variation * t) * 0.12 * Math.exp(-t * 48);
    } else if (family === "item_use" || family === "food_eat") {
      value = highNoise * (family === "food_eat" ? 0.18 : 0.11) * Math.exp(-t * 11)
        + Math.sin(2 * Math.PI * (family === "food_eat" ? 135 : 420) * t) * 0.09 * tail;
    } else if (family === "item_equip") {
      value = Math.sin(2 * Math.PI * 780 * variation * t) * 0.12 * tail
        + highNoise * 0.16 * Math.exp(-t * 25);
    } else if (family === "projectile") {
      value = (highNoise * 0.22 + lowNoise * 0.12) * (1 - p) * attack
        + Math.sin(2 * Math.PI * (680 - 340 * p) * t) * 0.035 * tail;
    } else if (family === "explosion" || family === "firework" || family === "thunder") {
      const low = family === "thunder" ? 42 : family === "firework" ? 105 : 58;
      value = lowNoise * 0.42 * Math.exp(-t * 3.4)
        + highNoise * (family === "thunder" ? 0.12 : 0.2) * Math.exp(-t * 7)
        + Math.sin(2 * Math.PI * low * t) * 0.28 * Math.exp(-t * 4.3);
    } else if (family === "door_wood") {
      value = Math.sin(2 * Math.PI * (115 + 180 * p) * t) * 0.17 * tail
        + highNoise * 0.2 * Math.exp(-t * 5);
    } else if (family === "portal") {
      value = Math.sin(2 * Math.PI * (180 + 240 * Math.sin(t * 4)) * t) * 0.13 * tail
        + Math.sin(2 * Math.PI * 640 * t) * 0.07 * tail
        + lowNoise * 0.11 * tail;
    } else if (family === "wind" || family === "cave") {
      const f = family === "cave" ? 54 : 88;
      value = lowNoise * (family === "cave" ? 0.17 : 0.12)
        + Math.sin(2 * Math.PI * f * variation * t) * 0.075
        + Math.sin(2 * Math.PI * f * 1.51 * t) * 0.035
        + highNoise * 0.025;
    } else {
      value = highNoise * 0.18 * tail + tone * 0.12 * tail;
    }

    if (long) {
      const fadeIn = Math.min(1, t * 0.8);
      const fadeOut = Math.min(1, (duration - t) * 0.8);
      value *= fadeIn * fadeOut;
    } else {
      value *= attack;
      if (family !== "glass_break" && family !== "crystal_chime") {
        value *= Math.min(1, (duration - t) * 28);
      }
    }
    data[i] = value;
    previousNoise = noise;
  }

  let peak = 0;
  for (let i = 0; i < data.length; i++) peak = Math.max(peak, Math.abs(data[i]));
  const scale = peak > 0 ? Math.min(1.0, 0.82 / peak) : 1;
  for (let i = 0; i < data.length; i++) data[i] *= scale;
  return data;
}

function materialForEvent(event) {
  const e = event.toLowerCase();
  if (/(glass|ice|amethyst|crystal|tinted_glass|sea_lantern)/.test(e)) return "glass";
  if (/(iron|copper|gold|metal|anvil|chain|netherite|vault|copper_bulb)/.test(e)) return "metal";
  if (/(wood|log|plank|bamboo|cherry|mangrove|crimson|warped|oak|birch|spruce|jungle|acacia|dark_oak|pale_oak|bookshelf|barrel|ladder)/.test(e)) return "wood";
  if (/(sand|gravel|snow|powder_snow|concrete_powder)/.test(e)) return "sand";
  if (/(dirt|grass|mud|moss|mycelium|root|podzol|farmland|clay|soul_soil)/.test(e)) return "dirt";
  return "stone";
}

function familyForEvent(event) {
  const e = event.toLowerCase();
  if (e.startsWith("ui.")) return "ui_click";
  if (e.startsWith("ambient.cave")) return "cave";
  if (e.startsWith("ambient.") || e.includes("wind")) return "wind";
  if (e.startsWith("weather.rain") || e.includes("rain")) return "rain";
  if (e.includes("thunder") || e.includes("lightning")) return "thunder";
  if (e.includes("levelup") || e.includes("level_up") || e.includes("experience_orb")) return "levelup";
  if (e.includes("firework")) return "firework";
  if (e.includes("explosion") || e.includes("tnt")) return "explosion";
  if (e.includes("portal") || e.includes("teleport") || e.includes("end_gateway")) return "portal";
  if (e.includes("splash") || e.includes("swim") || e.includes("flop")) return "water_splash";
  if (e.includes("bubble") || e.includes("water") || e.includes("underwater")) return "water_bubble";
  if (e.includes("lava") || e.includes("fire") || e.includes("burn") || e.includes("campfire")) return "fire_crackle";
  if (e.includes("pickup")) return "item_pickup";
  if (e.includes("equip") || e.includes("armor")) return "armor_equip";
  if (e.includes("eat") || e.includes("burp") || e.includes("drink")) return "food_eat";
  if (e.includes("bow") || e.includes("crossbow") || e.includes("trident") || e.includes("arrow") || e.includes("projectile")) return "projectile";

  if (e.startsWith("block.")) {
    if (e.includes("door") || e.includes("trapdoor") || e.includes("fence_gate") || e.includes("chest") || e.includes("barrel")) {
      return e.includes("iron") || e.includes("copper") ? "door_metal" : "door_wood";
    }
    if (e.includes("anvil")) return "anvil_clang";
    if (e.includes("amethyst") || e.includes("crystal")) return "crystal_chime";
    const material = materialForEvent(e);
    if (e.endsWith(".step") || e.endsWith(".fall") || e.endsWith(".land")) return material + "_step";
    if (e.endsWith(".break") || e.endsWith(".destroy")) return material + "_break";
    if (e.endsWith(".place") || e.endsWith(".hit") || e.endsWith(".chime")) return material + "_place";
    if (e.endsWith(".open") || e.endsWith(".close") || e.endsWith(".toggle")) return "door_wood";
    return material + "_place";
  }

  if (e.startsWith("entity.player.")) {
    if (e.includes("step") || e.includes("fall") || e.includes("land")) return "stone_step";
    if (e.includes("hurt") || e.includes("death") || e.includes("attack")) return "player_impact";
    if (e.includes("swim") || e.includes("splash")) return "water_splash";
    if (e.includes("levelup")) return "levelup";
    return "soft_thud";
  }

  if (e.startsWith("entity.")) {
    if (e.includes("step") || e.includes("fall") || e.includes("land")) return "dirt_step";
    if (e.includes("hurt") || e.includes("death") || e.includes("attack") || e.includes("bite")) return "creature_impact";
    if (e.includes("shoot") || e.includes("throw") || e.includes("launch")) return "projectile";
    if (e.includes("eat") || e.includes("sniff") || e.includes("drink")) return "food_eat";
    if (/(zombie|skeleton|creeper|spider|enderman|witch|warden|phantom|ghast|blaze|drowned|husk|pillager|ravager|guardian|shulker|slime|magma_cube)/.test(e)) return "creature_call";
    if (/(cow|pig|sheep|chicken|cat|dog|wolf|horse|donkey|mule|fox|frog|goat|camel|sniffer|bee|parrot|turtle|axolotl|dolphin|fish|llama|panda|rabbit)/.test(e)) return "animal_call";
    return "creature_call";
  }

  if (e.startsWith("item.")) {
    if (e.includes("equip")) return "item_equip";
    if (e.includes("pickup")) return "item_pickup";
    if (e.includes("break")) return "glass_break";
    return "item_use";
  }

  if (e.includes("door") || e.includes("trapdoor") || e.includes("fence_gate")) return "door_wood";
  if (e.includes("anvil") || e.includes("metal")) return "metal_hit";
  if (e.includes("glass") || e.includes("crystal") || e.includes("amethyst")) return "crystal_chime";
  if (e.includes("eat") || e.includes("drink")) return "food_eat";
  return "soft_thud";
}

function musicTracksForEvent(event) {
  const e = event.toLowerCase();
  if (e.startsWith("music_disc.")) {
    const picks = ["record_1", "record_2", "record_3", "record_4"];
    return [picks[hashText(e) % picks.length]];
  }
  if (e === "music.menu") return ["menu"];
  if (e === "music.credits") return ["credits"];
  if (e === "music.creative") return ["creative", "overworld_2"];
  if (e === "music.under_water") return ["underwater"];
  if (e === "music.dragon" || e === "music.end") return ["end"];
  if (e.startsWith("music.nether")) return ["nether"];
  if (e.startsWith("music.overworld")) {
    return hashText(e) % 2 === 0 ? ["overworld_1", "overworld_2"] : ["overworld_2", "overworld_1"];
  }
  if (e === "music.game") return ["overworld_1", "overworld_2"];
  if (e.startsWith("music.")) return ["overworld_1", "overworld_2"];
  return null;
}

function createMusicSamples(track) {
  const rate = MUSIC_RATE;
  const frames = Math.floor(rate * track.duration);
  const samples = new Float32Array(frames * 2);
  const rng = randomFor(track.seed);
  const progressions = {
    overworld: [[50, 57, 62, 66], [45, 52, 57, 61], [47, 54, 59, 62], [43, 50, 55, 59]],
    menu: [[48, 55, 60, 64], [43, 50, 55, 59], [45, 52, 57, 60], [41, 48, 53, 57]],
    bright: [[55, 62, 67, 71], [50, 57, 62, 66], [52, 59, 64, 67], [47, 54, 59, 62]],
    underwater: [[45, 52, 57, 60], [41, 48, 53, 57], [43, 50, 55, 59], [40, 47, 52, 55]],
    minor: [[45, 52, 57, 60], [43, 50, 55, 58], [40, 47, 52, 55], [41, 48, 53, 57]],
    ethereal: [[50, 57, 62, 67], [46, 53, 58, 62], [43, 50, 55, 60], [45, 52, 57, 62]],
    warm: [[48, 55, 60, 64], [45, 52, 57, 60], [43, 50, 55, 59], [47, 54, 59, 62]],
    record: [[50, 57, 62, 66], [53, 60, 65, 69], [48, 55, 60, 64], [45, 52, 57, 60]]
  };
  const progression = progressions[track.theme] || progressions.overworld;
  const barDuration = track.theme === "record" ? 2.4 : 3.6;
  const melodyDuration = track.theme === "record" ? 1.35 : 2.4;
  const scale = track.theme === "minor" || track.theme === "underwater"
    ? [0, 2, 3, 7, 9, 12, 14]
    : [0, 2, 4, 7, 9, 12, 14];
  const melody = [];
  const barCount = Math.ceil(track.duration / barDuration);
  for (let bar = 0; bar < barCount * 2; bar++) {
    const chord = progression[bar % progression.length];
    for (let beat = 0; beat < (track.theme === "record" ? 2 : 3); beat++) {
      if (rng() < (track.theme === "record" ? 0.88 : 0.62)) {
        const root = chord[1] + 12;
        const offset = scale[Math.floor(rng() * scale.length)];
        const octave = rng() < 0.22 ? 12 : 0;
        melody.push({
          start: bar * barDuration / 2 + beat * (track.theme === "record" ? 0.72 : 1.08),
          note: root + offset + octave,
          length: melodyDuration * (0.65 + rng() * 0.45),
          pan: rng() * 1.2 - 0.6
        });
      }
    }
  }

  // Render the backing pad and bass in one linear pass.
  const underwater = track.theme === "underwater";
  const ethereal = track.theme === "ethereal";
  const brightness = track.theme === "bright" || track.theme === "record" ? 1.0 : 0.72;
  const barFrames = Math.max(1, Math.floor(barDuration * rate));
  for (let i = 0; i < frames; i++) {
    const t = i / rate;
    const bar = Math.floor(i / barFrames);
    const local = (i - bar * barFrames) / rate;
    const chord = progression[bar % progression.length];
    let pad = 0;
    for (let n = 0; n < chord.length; n++) {
      const f = midiFrequency(chord[n] - (underwater ? 12 : 0));
      const vibrato = 1 + 0.0015 * Math.sin(t * 1.8 + n);
      pad += Math.sin(2 * Math.PI * f * vibrato * t + n * 0.7) * (0.014 / (1 + n * 0.15));
      pad += Math.sin(2 * Math.PI * f * 2.01 * t + n) * 0.0035;
    }
    const padEnvelope = 0.72 + 0.28 * Math.sin(Math.PI * local / barDuration);
    const bassFreq = midiFrequency(chord[0] - (underwater ? 12 : 0));
    const bass = Math.sin(2 * Math.PI * bassFreq * t) * 0.025;
    const shimmer = Math.sin(2 * Math.PI * midiFrequency(chord[2] + 24) * t + Math.sin(t * 0.22)) * (ethereal ? 0.012 : 0.004);
    const pulse = track.theme === "record" ? Math.sin(2 * Math.PI * 1.4 * t) * 0.009 : 0;
    const base = pad * padEnvelope + bass + pulse;
    samples[i * 2] = base + shimmer;
    samples[i * 2 + 1] = base + shimmer * 0.82;
  }

  // Draw each melody note only across its own duration. This avoids scanning
  // every note for every sample and keeps generation close to linear time.
  for (const note of melody) {
    const first = Math.max(0, Math.floor(note.start * rate));
    const last = Math.min(frames, Math.ceil((note.start + note.length) * rate));
    const f = midiFrequency(note.note);
    const leftGain = 1 - note.pan * 0.34;
    const rightGain = 1 + note.pan * 0.34;
    const amplitude = (ethereal ? 0.105 : 0.082) * brightness;
    const decay = track.theme === "record" ? 2.9 : 2.2;
    for (let i = first; i < last; i++) {
      const age = i / rate - note.start;
      const env = Math.exp(-age * decay);
      const phase = 2 * Math.PI * f * age;
      const wave = Math.sin(phase)
        + 0.28 * Math.sin(phase * 2.0)
        + 0.09 * Math.sin(phase * 3.01);
      const amp = wave * env * amplitude;
      samples[i * 2] += amp * leftGain;
      samples[i * 2 + 1] += amp * rightGain;
    }
  }

  // Apply matching fade envelopes to the combined track.
  for (let i = 0; i < frames; i++) {
    const t = i / rate;
    const fade = Math.min(1, t / 2.0) * Math.min(1, (track.duration - t) / 2.2);
    samples[i * 2] *= fade;
    samples[i * 2 + 1] *= fade;
  }

  let peak = 0;
  for (let i = 0; i < samples.length; i++) peak = Math.max(peak, Math.abs(samples[i]));
  const scaleOut = peak > 0 ? Math.min(1.0, 0.76 / peak) : 1;
  for (let i = 0; i < samples.length; i++) samples[i] *= scaleOut;
  return samples;
}

async function main() {
  verifyFfmpeg();
  if (!fs.existsSync(MANIFEST)) {
    fail("Cannot find the built-in sounds.json at " + MANIFEST);
  }

  const original = JSON.parse(await fsp.readFile(MANIFEST, "utf8"));
  const eventNames = Object.keys(original);
  if (eventNames.length < 500) {
    fail("The built-in sound manifest looks incomplete; found only " + eventNames.length + " events.");
  }

  await fsp.rm(OUTPUT, { recursive: true, force: true });
  await fsp.rm(WORK, { recursive: true, force: true });
  const effectsInput = path.join(OUTPUT, "sounds");
  const musicInput = path.join(OUTPUT, "music");
  const effectAssets = path.join(effectsInput, "assets", "minecraft", "sounds", "eaglefine", "effects");
  const musicAssets = path.join(musicInput, "assets", "minecraft", "sounds", "music", "eaglefine");
  const workEffects = path.join(WORK, "effects");
  const workMusic = path.join(WORK, "music");
  await Promise.all([
    fsp.mkdir(effectAssets, { recursive: true }),
    fsp.mkdir(musicAssets, { recursive: true }),
    fsp.mkdir(workEffects, { recursive: true }),
    fsp.mkdir(workMusic, { recursive: true })
  ]);

  console.log("[eaglefine-audio] Synthesizing original effect library...");
  for (const family of EFFECT_FAMILIES) {
    for (let variant = 1; variant <= 2; variant++) {
      const wav = path.join(workEffects, family + "_" + variant + ".wav");
      const ogg = path.join(effectAssets, family + "_" + variant + ".ogg");
      writeWav(wav, synthesizeEffect(family, variant), EFFECT_RATE, 1);
      runFfmpeg([
        "-hide_banner", "-loglevel", "error", "-y",
        "-i", wav,
        "-map", "0:a:0", "-vn",
        "-c:a", "libvorbis", "-q:a", "3",
        "-ar", String(EFFECT_RATE), "-ac", "1",
        "-f", "ogg", ogg
      ]);
    }
  }

  console.log("[eaglefine-audio] Creating original ambient soundtrack...");
  for (const track of MUSIC_TRACKS) {
    const wav = path.join(workMusic, track.name + ".wav");
    const ogg = path.join(musicAssets, track.name + ".ogg");
    writeWav(wav, createMusicSamples(track), MUSIC_RATE, 2);
    runFfmpeg([
      "-hide_banner", "-loglevel", "error", "-y",
      "-i", wav,
      "-map", "0:a:0", "-vn",
      "-c:a", "libopus", "-b:a", "72k",
      "-vbr", "on", "-compression_level", "10",
      "-ar", "48000", "-ac", "2",
      "-application", "audio",
      "-f", "ogg", ogg
    ]);
  }

  const manifest = {};
  let effectCount = 0;
  let musicCount = 0;
  for (const event of eventNames) {
    const oldDefinition = original[event] || {};
    const musicTracks = musicTracksForEvent(event);
    let sounds;
    if (musicTracks) {
      musicCount++;
      sounds = musicTracks.map((name) => ({
        name: "music/eaglefine/" + name,
        stream: true,
        volume: event === "music.menu" ? 0.36 : 0.48,
        weight: 1
      }));
    } else {
      effectCount++;
      const family = familyForEvent(event);
      const volume = family === "wind" || family === "cave" || family === "rain" ? 0.28
        : family === "animal_call" || family === "creature_call" ? 0.62
        : family === "ui_click" || family === "item_pickup" ? 0.72
        : 0.78;
      sounds = [1, 2].map((variant) => ({
        name: "eaglefine/effects/" + family + "_" + variant,
        volume,
        pitch: variant === 1 ? 0.96 : 1.04,
        weight: 1
      }));
    }
    const definition = { replace: true, sounds };
    if (typeof oldDefinition.subtitle === "string") definition.subtitle = oldDefinition.subtitle;
    manifest[event] = definition;
  }

  const manifestPath = path.join(effectsInput, "assets", "minecraft", "sounds.json");
  await fsp.mkdir(path.dirname(manifestPath), { recursive: true });
  await fsp.writeFile(manifestPath, JSON.stringify(manifest, null, 2) + "\n", "utf8");

  const effectFiles = EFFECT_FAMILIES.length * 2;
  const musicFiles = MUSIC_TRACKS.length;
  console.log("[eaglefine-audio] Generated " + effectFiles + " effect variants for " + effectCount
    + " sound events and " + musicFiles + " original music tracks for " + musicCount + " music events.");
  console.log("[eaglefine-audio] Inputs are ready under .eaglefine-audio/epk-input.");
  console.log("[eaglefine-audio] The game build packages these files into sounds.epk and music.epk.");
}

main().catch((error) => fail(error && error.stack ? error.stack : String(error)));

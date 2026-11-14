/*__EAGLER_AUDIO_MANIFEST__*/
(function () {
  "use strict";

  var manifest = window.__eagStandaloneSoundManifest || {};
  var encoder = new TextEncoder();
  var finished = false;
  var resolveSelection;
  window.__eagAudioSelectionReady = new Promise(function (resolve) {
    resolveSelection = resolve;
  });

  function setStatus(message) {
    var node = document.getElementById("eag-audio-status");
    if (node) node.textContent = message;
  }

  function setBusy(busy) {
    var choose = document.getElementById("eag-audio-choose");
    var fallback = document.getElementById("eag-audio-fallback");
    if (choose) choose.disabled = busy;
    if (fallback) fallback.disabled = busy;
  }

  function finish(selection) {
    if (finished) return;
    finished = true;
    var overlay = document.getElementById("eag-audio-gate");
    if (overlay) overlay.style.display = "none";
    resolveSelection(selection);
  }

  function embeddedFallback(message) {
    setStatus(message || "Using the audio included in this HTML file.");
    setTimeout(function () {
      finish({ source: "embedded" });
    }, 450);
  }

  window.__eagUseEmbeddedAudio = function () {
    finish({ source: "embedded" });
  };

  function getReferences() {
    var refs = new Map();
    Object.keys(manifest).forEach(function (eventName) {
      var definition = manifest[eventName];
      var sounds = definition && Array.isArray(definition.sounds) ? definition.sounds : [];
      sounds.forEach(function (sound) {
        var type = typeof sound === "string" ? "file" : (sound && sound.type) || "file";
        var rawName = typeof sound === "string" ? sound : sound && sound.name;
        if (type !== "file" || typeof rawName !== "string" || !rawName) return;

        var colon = rawName.indexOf(":");
        var namespace = colon >= 0 ? rawName.slice(0, colon) : "minecraft";
        var soundPath = colon >= 0 ? rawName.slice(colon + 1) : rawName;
        if (!/^[a-z0-9_.-]+$/i.test(namespace)
            || !soundPath || soundPath.indexOf("\\") >= 0
            || soundPath.split("/").some(function (part) {
              return !part || part === "." || part === "..";
            })) {
          return;
        }
        if (!soundPath.endsWith(".ogg")) soundPath += ".ogg";
        var relative = namespace + "/sounds/" + soundPath;
        refs.set(relative, {
          relative: relative,
          namespace: namespace,
          soundPath: soundPath,
          isMusic: soundPath.indexOf("music/") === 0 || soundPath.indexOf("records/") === 0
        });
      });
    });
    return Array.from(refs.values());
  }

  async function optionalDirectory(parent, name) {
    try {
      return await parent.getDirectoryHandle(name);
    } catch (error) {
      return null;
    }
  }

  async function resolveAssetsFolder(selectedFolder) {
    var indexes = await optionalDirectory(selectedFolder, "indexes");
    var objects = await optionalDirectory(selectedFolder, "objects");
    if (indexes && objects) return { indexes: indexes, objects: objects };

    var assetsFolder = await optionalDirectory(selectedFolder, "assets");
    if (!assetsFolder) return null;
    indexes = await optionalDirectory(assetsFolder, "indexes");
    objects = await optionalDirectory(assetsFolder, "objects");
    if (!indexes || !objects) return null;
    return { indexes: indexes, objects: objects };
  }

  async function findCompleteIndex(indexesFolder, objectsFolder, refs) {
    var candidates = [];
    for await (var pair of indexesFolder.entries()) {
      var name = pair[0];
      var handle = pair[1];
      if (handle.kind === "file" && /^\\d+\\.json$/i.test(name)) {
        candidates.push({ name: name, handle: handle, id: Number(name.slice(0, -5)) });
      }
    }
    candidates.sort(function (a, b) {
      if (a.id === 32) return -1;
      if (b.id === 32) return 1;
      return b.id - a.id;
    });

    for (var c = 0; c < candidates.length; ++c) {
      var candidate = candidates[c];
      setStatus("Checking Minecraft asset index " + candidate.id + "…");
      var index;
      try {
        index = JSON.parse(await (await candidate.handle.getFile()).text());
      } catch (error) {
        continue;
      }
      if (!index || !index.objects) continue;

      var resolved = [];
      var valid = true;
      var prefixCache = new Map();
      async function resolveReference(ref) {
        var asset = index.objects[ref.relative];
        if (!asset || !/^[0-9a-f]{40}$/i.test(asset.hash || "")
            || !Number.isSafeInteger(asset.size) || asset.size <= 0) {
          return null;
        }
        try {
          var prefix = asset.hash.slice(0, 2);
          var prefixPromise = prefixCache.get(prefix);
          if (!prefixPromise) {
            prefixPromise = objectsFolder.getDirectoryHandle(prefix);
            prefixCache.set(prefix, prefixPromise);
          }
          var prefixHandle = await prefixPromise;
          var objectHandle = await prefixHandle.getFileHandle(asset.hash);
          var file = await objectHandle.getFile();
          if (file.size !== asset.size) return null;
          return { ref: ref, file: file, size: asset.size };
        } catch (error) {
          return null;
        }
      }

      // Resolve file handles in bounded batches to avoid thousands of serial
      // File System Access API round trips and keep the browser responsive.
      for (var offset = 0; offset < refs.length; offset += 64) {
        var batch = await Promise.all(refs.slice(offset, offset + 64).map(resolveReference));
        if (batch.some(function (item) { return item === null; })) {
          valid = false;
          break;
        }
        resolved.push.apply(resolved, batch);
        var checked = Math.min(offset + batch.length, refs.length);
        setStatus("Checking installed audio files… " + checked + " / " + refs.length);
        await new Promise(function (resolve) { setTimeout(resolve, 0); });
      }
      if (valid && resolved.length === refs.length) {
        return { id: candidate.id, files: resolved };
      }
    }
    return null;
  }
  var crcTable = null;
  function crc32(bytes) {
    if (!crcTable) {
      crcTable = new Uint32Array(256);
      for (var n = 0; n < 256; ++n) {
        var value = n;
        for (var bit = 0; bit < 8; ++bit) {
          value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1);
        }
        crcTable[n] = value >>> 0;
      }
    }
    var crc = 0xffffffff;
    for (var i = 0; i < bytes.length; ++i) {
      crc = crcTable[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
    }
    return (crc ^ 0xffffffff) >>> 0;
  }

  function intBytes(value) {
    var bytes = new Uint8Array(4);
    new DataView(bytes.buffer).setUint32(0, value >>> 0, false);
    return bytes;
  }

  function makeEPK(name, entries) {
    var parts = [];
    var fileName = encoder.encode(name + ".epk");
    var type = encoder.encode("epk/resources");
    var fileTypeKey = encoder.encode("file-type");
    if (fileName.length > 255) throw new Error("EPK archive name is too long.");

    parts.push(encoder.encode("EAGPKG$$"));
    parts.push(new Uint8Array([6]));
    parts.push(encoder.encode("ver2.0"));
    parts.push(new Uint8Array([fileName.length]));
    parts.push(fileName);
    parts.push(new Uint8Array([0, 0])); // empty archive comment

    var now = Date.now();
    var timestamp = new Uint8Array(8);
    var timestampView = new DataView(timestamp.buffer);
    timestampView.setUint32(0, Math.floor(now / 4294967296), false);
    timestampView.setUint32(4, now >>> 0, false);
    parts.push(timestamp);
    parts.push(intBytes(entries.length + 1)); // file-type HEAD plus FILE records
    parts.push(new Uint8Array([0x30])); // uncompressed EPK stream

    parts.push(encoder.encode("HEAD"));
    parts.push(new Uint8Array([fileTypeKey.length]));
    parts.push(fileTypeKey);
    parts.push(intBytes(type.length));
    parts.push(type);
    parts.push(new Uint8Array([0x3e])); // >

    entries.forEach(function (entry) {
      var nameBytes = encoder.encode(entry.name);
      if (!nameBytes.length || nameBytes.length > 255) {
        throw new Error("An audio asset path is too long for the EPK format.");
      }
      if (entry.data.length > 0xffffffff - 5) {
        throw new Error("An audio asset is too large for the EPK format.");
      }
      parts.push(encoder.encode("FILE"));
      parts.push(new Uint8Array([nameBytes.length]));
      parts.push(nameBytes);
      parts.push(intBytes(entry.data.length + 5));
      parts.push(intBytes(crc32(entry.data)));
      parts.push(entry.data);
      parts.push(encoder.encode(":>"));
    });

    parts.push(encoder.encode("END$"));
    parts.push(encoder.encode(":::YEE:>"));
    return new Blob(parts, { type: "application/octet-stream" });
  }

  async function loadMinecraftAudio(selectedFolder) {
    var folders = await resolveAssetsFolder(selectedFolder);
    if (!folders) throw new Error("Select the .minecraft folder or its assets folder.");

    var refs = getReferences();
    if (refs.length < 1000) throw new Error("The built-in sound list is incomplete.");

    var match = await findCompleteIndex(folders.indexes, folders.objects, refs);
    if (!match) {
      throw new Error("No complete Minecraft Java asset index with all required audio files was found.");
    }

    var soundEntries = [{
      name: "assets/minecraft/sounds.json",
      data: encoder.encode(JSON.stringify(manifest))
    }];
    var musicEntries = [];
    var totalBytes = match.files.reduce(function (sum, item) { return sum + item.size; }, 0);
    var totalMiB = (totalBytes / (1024 * 1024)).toFixed(1);
    setStatus("Loading " + match.files.length + " Minecraft audio files (" + totalMiB + " MiB)…");
    for (var offset = 0; offset < match.files.length; offset += 24) {
      var batch = match.files.slice(offset, offset + 24);
      var loaded = await Promise.all(batch.map(async function (item) {
        var data = new Uint8Array(await item.file.arrayBuffer());
        if (data.length !== item.size) throw new Error("An installed audio file could not be read completely.");
        return { item: item, data: data };
      }));
      loaded.forEach(function (loadedItem) {
        var item = loadedItem.item;
        var entry = {
          name: "assets/" + item.ref.relative,
          data: loadedItem.data
        };
        if (item.ref.isMusic) musicEntries.push(entry);
        else soundEntries.push(entry);
      });
      var completed = Math.min(offset + loaded.length, match.files.length);
      if (completed % 96 === 0 || completed === match.files.length) {
        setStatus("Loading Minecraft Java audio… " + completed + " / " + match.files.length
          + " (" + totalMiB + " MiB)");
        await new Promise(function (resolve) { setTimeout(resolve, 0); });
      }
    }

    setStatus("Preparing the Minecraft audio archives…");
    if (musicEntries.length === 0) throw new Error("The selected asset index did not include music files.");

    setStatus("Preparing the Minecraft audio archives…");
    var soundsBlob = makeEPK("sounds", soundEntries);
    var musicBlob = makeEPK("music", musicEntries);
    return {
      soundsUrl: URL.createObjectURL(soundsBlob),
      musicUrl: URL.createObjectURL(musicBlob),
      assetIndex: match.id,
      fileCount: match.files.length
    };
  }

  window.__eagChooseMinecraftAudio = async function () {
    if (finished) return;
    if (typeof window.showDirectoryPicker !== "function" || !window.isSecureContext) {
      embeddedFallback("This browser cannot open a local folder here. Using embedded audio.");
      return;
    }

    setBusy(true);
    setStatus("Choose your .minecraft folder or its assets folder…");
    try {
      var selectedFolder = await window.showDirectoryPicker({
        id: "eaglefine-minecraft-audio",
        mode: "read"
      });
      setStatus("Reading the Minecraft Java asset index…");
      var result = await loadMinecraftAudio(selectedFolder);
      setStatus("Minecraft Java audio loaded. Starting EagleFine…");
      setTimeout(function () {
        finish({
          source: "minecraft",
          soundsUrl: result.soundsUrl,
          musicUrl: result.musicUrl,
          assetIndex: result.assetIndex,
          fileCount: result.fileCount
        });
      }, 350);
    } catch (error) {
      var message = error && error.name === "AbortError"
        ? "Folder selection was cancelled. Using embedded audio."
        : "Minecraft audio could not be loaded: " + (error && error.message ? error.message : "unknown error") +
          ". Using embedded audio.";
      embeddedFallback(message);
    }
  };
})();
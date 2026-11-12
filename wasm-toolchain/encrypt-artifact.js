#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const crypto = require("node:crypto");
const path = require("node:path");

const MAGIC = "EAGLEFINE-ENCRYPTED-ARTIFACT-V1";
const [inputPath, outputPath, publicKeyPath] = process.argv.slice(2);

function fail(message) {
  console.error("[encrypt-artifact] ERROR: " + message);
  process.exit(1);
}

if (!inputPath || !outputPath || !publicKeyPath) {
  fail("usage: node encrypt-artifact.js <input.html> <output.enc> <public-key.pem>");
}

try {
  const input = fs.readFileSync(inputPath);
  if (input.length < 1024) fail("input HTML is unexpectedly small");

  const publicKey = fs.readFileSync(publicKeyPath);
  const aesKey = crypto.randomBytes(32);
  const iv = crypto.randomBytes(12);
  const aad = Buffer.from(MAGIC, "utf8");
  const cipher = crypto.createCipheriv("aes-256-gcm", aesKey, iv);
  cipher.setAAD(aad);
  const ciphertext = Buffer.concat([cipher.update(input), cipher.final()]);
  const tag = cipher.getAuthTag();
  const wrappedKey = crypto.publicEncrypt({
    key: publicKey,
    padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
    oaepHash: "sha256"
  }, aesKey);

  const metadata = {
    version: 1,
    algorithm: "RSA-OAEP-SHA256+A256GCM",
    plaintextBytes: input.length,
    plaintextSha256: crypto.createHash("sha256").update(input).digest("hex"),
    wrappedKey: wrappedKey.toString("base64"),
    iv: iv.toString("base64"),
    tag: tag.toString("base64")
  };

  fs.mkdirSync(path.dirname(path.resolve(outputPath)), { recursive: true });
  fs.writeFileSync(outputPath, Buffer.concat([
    Buffer.from(MAGIC + "\n", "utf8"),
    Buffer.from(JSON.stringify(metadata) + "\n", "utf8"),
    ciphertext
  ]), { mode: 0o600 });

  aesKey.fill(0);
  console.log("[encrypt-artifact] encrypted HTML bytes: " + input.length.toLocaleString());
  console.log("[encrypt-artifact] encrypted artifact bytes: " + fs.statSync(outputPath).size.toLocaleString());
} catch (error) {
  fail(error && error.message ? error.message : String(error));
}

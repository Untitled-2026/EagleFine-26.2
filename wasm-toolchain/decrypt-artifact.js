#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const crypto = require("node:crypto");

const MAGIC = "EAGLEFINE-ENCRYPTED-ARTIFACT-V1";
const [inputPath, privateKeyPath, outputPath] = process.argv.slice(2);

function fail(message) {
  console.error("[decrypt-artifact] ERROR: " + message);
  process.exit(1);
}

if (!inputPath || !privateKeyPath || !outputPath) {
  fail("usage: node decrypt-artifact.js <input.enc> <private-key.pem> <output.html>");
}

try {
  const encrypted = fs.readFileSync(inputPath);
  const firstNewline = encrypted.indexOf(0x0a);
  const secondNewline = encrypted.indexOf(0x0a, firstNewline + 1);
  if (firstNewline < 0 || secondNewline < 0) fail("encrypted artifact header is incomplete");

  const magic = encrypted.subarray(0, firstNewline).toString("utf8");
  if (magic !== MAGIC) fail("unrecognized encrypted artifact format");

  const metadata = JSON.parse(encrypted.subarray(firstNewline + 1, secondNewline).toString("utf8"));
  if (metadata.version !== 1 || metadata.algorithm !== "RSA-OAEP-SHA256+A256GCM") {
    fail("unsupported encrypted artifact version or algorithm");
  }

  const privateKey = crypto.createPrivateKey(fs.readFileSync(privateKeyPath));
  if (privateKey.asymmetricKeyType !== "rsa") fail("the supplied private key is not an RSA key");

  const aesKey = crypto.privateDecrypt({
    key: privateKey,
    padding: crypto.constants.RSA_PKCS1_OAEP_PADDING,
    oaepHash: "sha256"
  }, Buffer.from(metadata.wrappedKey, "base64"));
  if (aesKey.length !== 32) fail("decrypted AES key has an invalid length");

  const iv = Buffer.from(metadata.iv, "base64");
  const tag = Buffer.from(metadata.tag, "base64");
  if (iv.length !== 12 || tag.length !== 16) {
    aesKey.fill(0);
    fail("encrypted artifact has invalid AES-GCM parameters");
  }

  const decipher = crypto.createDecipheriv("aes-256-gcm", aesKey, iv);
  decipher.setAAD(Buffer.from(MAGIC, "utf8"));
  decipher.setAuthTag(tag);
  const ciphertext = encrypted.subarray(secondNewline + 1);
  const plaintext = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  aesKey.fill(0);

  const digest = crypto.createHash("sha256").update(plaintext).digest("hex");
  if (plaintext.length !== metadata.plaintextBytes || digest !== metadata.plaintextSha256) {
    fail("decrypted HTML failed its size or SHA-256 integrity check");
  }

  fs.writeFileSync(outputPath, plaintext, { mode: 0o600 });
  console.log("[decrypt-artifact] restored " + plaintext.length.toLocaleString() + " bytes to " + outputPath);
  console.log("[decrypt-artifact] SHA-256: " + digest);
} catch (error) {
  fail(error && error.message ? error.message : String(error));
}

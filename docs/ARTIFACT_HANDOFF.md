# Standalone HTML artifact handoff

The standalone workflow builds a silent, single-file EagleFine HTML client, then
encrypts it before uploading it as a GitHub Actions artifact.

## Why the uploaded file is encrypted

The repository is public, so an unencrypted Actions artifact would be available
to other repository readers. The workflow uses RSA-OAEP-SHA256 and AES-256-GCM
to encrypt the HTML before upload. The matching private key is supplied separately
to the intended recipient and must never be committed.

## Download and decrypt on Windows

1. Open the successful **EagleFine standalone HTML test build** run in the
   repository's Actions tab.
2. Download the artifact named `EagleFine-26.2-silent-test-encrypted` and
   extract its ZIP file.
3. Save the separately supplied `eaglefine-artifact-private.pem` beside the
   extracted `EagleFine-26.2.html.enc` file. Do not commit or share the private key.
4. Run this command from the repository folder (adjust the paths if needed):

       node wasm-toolchain/decrypt-artifact.js EagleFine-26.2.html.enc eaglefine-artifact-private.pem EagleFine-26.2.html

Node.js 22 or newer is required. The decryptor verifies the AES-GCM authentication
tag, original byte count, and SHA-256 digest.

The encrypted Actions artifact is retained for seven days. Download it before
it expires.

## Current test-build limitation

This is a **silent test build**. The checked-in workspace does not contain the
original OGG audio objects, so its sounds EPK is empty and optional music is
omitted. It is suitable for initial browser and graphics testing, but it is not
yet a complete audio-enabled release.

# Standalone HTML test build

The manual and branch-triggered workflow builds a silent, single-file EagleFine
HTML client without encrypting it.

## Build and download

1. Open the repository's **Actions** tab.
2. Select **EagleFine standalone HTML test build**.
3. Click **Run workflow** and select the `eaglefine/bootstrap` branch.
4. To make a downloadable artifact, enable **Upload unencrypted HTML artifact**.
5. Open the completed run and download
   `EagleFine-26.2-silent-test-plain`.

## Public artifact warning

This repository is public. GitHub allows anyone with read access to a repository
to download its workflow artifacts. Enabling the upload option therefore makes
the unencrypted HTML downloadable by other people too. Leave the option disabled
for a build-only run that does not upload the HTML.

The artifact is retained for seven days.

## Current test-build limitation

This is a **silent test build**. The checked-in workspace does not contain the
original OGG audio objects, so its sounds EPK is empty and optional music is
omitted. It is suitable for initial browser and graphics testing, but it is not
yet a complete audio-enabled release.

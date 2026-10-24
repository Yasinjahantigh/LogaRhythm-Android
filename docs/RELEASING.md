# Releasing

Maintainer notes for publishing a signed APK. Do not commit the keystore or any passwords.

## Secrets (GitHub → Settings → Secrets and variables → Actions)

| Secret | Value |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64 of the release keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

Encode the keystore on Linux with `base64 -w 0 logarhythm-release.keystore`.

## Publish

```bash
git tag v1.6.0
git push origin v1.6.0
```

Or run **Build and release APK** from the Actions tab with an empty version input to bump the patch version.

The workflow runs tests, builds a signed APK and attaches it to a GitHub release. It fails if a signing secret is missing, so nothing unsigned gets published.

## Verify

```bash
apksigner verify --verbose --print-certs LogaRhythm-v1.6.0.apk
```

The certificate must match the one from your keystore.

## Important

Keep the keystore and passwords outside the repository. If the key is lost, existing installs cannot be updated in place.
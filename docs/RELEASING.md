# Releasing

## 1. Create a signing key (once)

```bash
keytool -genkeypair -v \
  -keystore logarhythm-release.keystore \
  -alias logarhythm-key \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000
```

Keep the keystore file and both passwords outside the repository. Losing the key means existing installations cannot be updated in place.

## 2. Add repository secrets

In GitHub: **Settings -> Secrets and variables -> Actions -> New repository secret**.

| Secret | Value |
| :--- | :--- |
| `KEYSTORE_BASE64` | Base64 of the keystore file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | `logarhythm-key` |
| `KEY_PASSWORD` | Key password |

Encode the keystore with one of these commands:

```bash
# Linux
base64 -w 0 logarhythm-release.keystore > keystore_base64.txt
```

```powershell
# Windows PowerShell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("logarhythm-release.keystore")) | Out-File -Encoding ascii keystore_base64.txt
```

## 3. Publish a release

Create and push a tag:

```bash
git tag v1.4.0
git push origin v1.4.0
```

Alternatively, run **Build and release APK** from the Actions tab and leave the version input empty to increment the patch version automatically.

The workflow runs the unit tests, builds a signed release APK, verifies it with `apksigner` and attaches it to a GitHub release.

## 4. Verify a downloaded APK

```bash
apksigner verify --verbose --print-certs LogaRhythm-v1.4.0.apk
```

The reported certificate should match the local keystore:

```bash
keytool -list -v -keystore logarhythm-release.keystore -alias logarhythm-key
```

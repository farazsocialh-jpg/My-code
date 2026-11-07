# Release signing

The release build signs with a keystore when credentials are available, and falls back to an
unsigned APK when they are not, so day-to-day builds never break.

Credentials are read in this order:

1. Environment variables (used by CI):

   | Variable | Meaning |
   | --- | --- |
   | `SPORTAGE_KEYSTORE_FILE` | path to the `.jks`, relative to `android/` |
   | `SPORTAGE_KEYSTORE_PASSWORD` | keystore password |
   | `SPORTAGE_KEY_ALIAS` | key alias |
   | `SPORTAGE_KEY_PASSWORD` | password for that alias |

2. `android/keystore.properties` (local machines, git-ignored):

   ```properties
   storeFile=release.jks
   storePassword=…
   keyAlias=sportage
   keyPassword=…
   ```

If neither source is complete, `assembleRelease` produces `app-release-unsigned.apk` and the
Gradle log says `Release signing: no credentials`.

## The keystore in this workspace (already created)

| File | What it is | Committed? |
| --- | --- | --- |
| `android/release.jks` | the signing key (PKCS12, RSA 2048, alias `sportage`, valid for 10,000 days) | no — git-ignored |
| `android/keystore.properties` | local credentials, read automatically by Gradle | no — git-ignored |
| `android/release-key.b64` | the same keystore as a single-line base64 blob, for the GitHub secret | no — git-ignored |

Certificate identity: `CN=Sportage OBD, OU=Android, O=Sportage OBD, L=Unknown, ST=Unknown, C=US`,
SHA-256 fingerprint
`7E:BF:87:F2:4C:4E:B9:EB:9E:B7:90:F6:0D:84:1E:9D:81:0D:E7:A0:14:ED:19:24:B4:E0:3D:74:06:7C:8D:6D`,
valid until 2054-03-23. Because a release build now signs successfully, `./gradlew
assembleRelease` in `android/` produces a ready-to-install `app-release.apk`.

**Back it up before anything else.** Copy `android/release.jks` and the passwords in
`android/keystore.properties` somewhere private and durable (password manager, encrypted backup),
ideally two places. Android identifies an app by its signing key: if this keystore is lost you can
no longer ship an update over an installed copy — users would have to uninstall and lose their
data — and anyone who copies it can sign updates that Android will accept as yours.

If you want the certificate to carry your real name/company/country instead of the placeholder
`L=Unknown, ST=Unknown`, regenerate it now, while the app has never been published (see
"Recreate the keystore from scratch" below). Once a build has shipped, the key must not change.

## Add the four GitHub secrets

1. Open `android/keystore.properties` in the editor. `storePassword` and `keyPassword` hold the
   same value (PKCS12 keystores use a single password, so both secrets get that same value);
   `keyAlias` is `sportage`.
2. Open `android/release-key.b64`: it is one long line — select all and copy it.
3. In GitHub, go to the repository → **Settings → Secrets and variables → Actions → New repository
   secret** and add:

   | Secret name | Value |
   | --- | --- |
   | `RELEASE_KEYSTORE_BASE64` | the entire contents of `android/release-key.b64` |
   | `RELEASE_KEYSTORE_PASSWORD` | `storePassword` from `keystore.properties` |
   | `RELEASE_KEY_ALIAS` | `sportage` |
   | `RELEASE_KEY_PASSWORD` | `keyPassword` from `keystore.properties` |

The base64 blob must stay on one line — the workflow decodes it with `base64 -d`, and a wrapped
value would fail to decode. To regenerate it after any change to the keystore:

```bash
base64 -w 0 android/release.jks > android/release-key.b64   # macOS: base64 -i android/release.jks -o android/release-key.b64
```

Never commit `release.jks`, `release-key.b64` or `keystore.properties`, and never paste the
passwords into an issue, a commit or a workflow file; `.gitignore` already excludes all three.

## Verify locally

```bash
cd android
./gradlew assembleRelease          # credentials come from keystore.properties

# macOS/Linux, build-tools 35.0.0
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
```

A signed build is named `app-release.apk`; an unsigned one is `app-release-unsigned.apk`.
`--print-certs` should report the fingerprint above. The APK is signed with APK Signature Scheme
v2 only, which is sufficient for the app's `minSdk` of 28 (JAR/v1 signing is only needed below
Android 7.0, and AGP does not emit it above `minSdk` 24).

## Publishing

Push a tag to create a GitHub Release with the signed APKs attached:

```bash
git tag v1.0.0
git push origin v1.0.0
```

The `Publish release` job fails if the release APK is unsigned, so a tag build can never publish
an uninstallable artifact. A tag build also requires all four secrets; without them it fails with
an error naming the missing ones.

## Recreate the keystore from scratch

Only for a fresh key, before anything has been published. Delete `release.jks`,
`release-key.b64` and the installed app, then:

```bash
cd android
keytool -genkeypair -v \
  -keystore release.jks \
  -alias sportage \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=<your name>, O=<your company>, C=<XX>"
```

Then update `keystore.properties`, regenerate `release-key.b64`, and replace all four GitHub
secrets.

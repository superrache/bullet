#!/usr/bin/env bash
# Prepares a release: bumps versionCode, sets versionName, builds and verifies
# the signed release APK, then prints the git commands to tag it.
#
# Usage: ./release.sh <version>      e.g. ./release.sh 0.4.0
set -euo pipefail

cd "$(dirname "$0")"

GRADLE_FILE=app/build.gradle.kts

die() { echo "❌ $*" >&2; exit 1; }

[[ $# -eq 1 ]] || die "Usage : ./release.sh <version>   (ex. ./release.sh 0.4.0)"
VERSION=$1
[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || die "Version invalide « $VERSION » : format attendu X.Y.Z (ex. 0.4.0)."

TAG="v$VERSION"
git rev-parse -q --verify "refs/tags/$TAG" >/dev/null 2>&1 && die "Le tag $TAG existe déjà."
[[ -f keystore.properties ]] || die "keystore.properties absent : l'APK release ne serait pas signé (voir README, « Publier une version »)."
STORE_FILE=$(sed -nE 's/^storeFile[[:space:]]*=[[:space:]]*(.*)/\1/p' keystore.properties)
[[ -n $STORE_FILE && -f $STORE_FILE ]] || die "Keystore « $STORE_FILE » introuvable (storeFile dans keystore.properties, relatif à la racine du projet)."

# SDK Android : local.properties est partagé avec le conteneur de dev (sdk.dir=/opt/android-sdk),
# donc sur le Mac on cherche un SDK valide ailleurs et on le passe à Gradle via ANDROID_HOME.
find_sdk() {
    local candidates=("${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}")
    [[ -f local.properties ]] && candidates+=("$(sed -nE 's/^sdk\.dir=(.*)/\1/p' local.properties)")
    candidates+=("$HOME/Library/Android/sdk" "$HOME/Android/Sdk" /opt/android-sdk)
    local dir
    for dir in "${candidates[@]}"; do
        [[ -n $dir && -d $dir/platforms && -d $dir/build-tools ]] && { echo "$dir"; return; }
    done
}
SDK=$(find_sdk)
[[ -n $SDK ]] || die "SDK Android introuvable. Installez-le (Android Studio → ~/Library/Android/sdk) ou définissez ANDROID_HOME,
   ou lancez la release dans le conteneur : docker compose run --rm life-control-dev ./release.sh $VERSION"
export ANDROID_HOME=$SDK
echo "▶ SDK Android : $SDK"

CURRENT_CODE=$(sed -nE 's/^[[:space:]]*versionCode = ([0-9]+).*/\1/p' "$GRADLE_FILE")
CURRENT_NAME=$(sed -nE 's/^[[:space:]]*versionName = "([^"]+)".*/\1/p' "$GRADLE_FILE")
[[ -n $CURRENT_CODE && -n $CURRENT_NAME ]] || die "versionCode / versionName introuvables dans $GRADLE_FILE."

# The new version must be strictly greater than the current one.
if [[ $(printf '%s\n%s\n' "$CURRENT_NAME" "$VERSION" | sort -V | tail -1) != "$VERSION" || $CURRENT_NAME == "$VERSION" ]]; then
    die "La version $VERSION doit être supérieure à la version actuelle $CURRENT_NAME."
fi

NEW_CODE=$((CURRENT_CODE + 1))
echo "▶ $CURRENT_NAME ($CURRENT_CODE) → $VERSION ($NEW_CODE)"

sed -i.bak -E \
    -e "s/^([[:space:]]*versionCode = )[0-9]+/\1$NEW_CODE/" \
    -e "s/^([[:space:]]*versionName = )\"[^\"]+\"/\1\"$VERSION\"/" \
    "$GRADLE_FILE"
rm -f "$GRADLE_FILE.bak"

# On failure, put the previous version back so the tree is left as it was.
restore() {
    sed -i.bak -E \
        -e "s/^([[:space:]]*versionCode = )[0-9]+/\1$CURRENT_CODE/" \
        -e "s/^([[:space:]]*versionName = )\"[^\"]+\"/\1\"$CURRENT_NAME\"/" \
        "$GRADLE_FILE"
    rm -f "$GRADLE_FILE.bak"
    echo "↩ Version restaurée à $CURRENT_NAME ($CURRENT_CODE)." >&2
}
trap 'restore' ERR

echo "▶ Tests, lint et build release (vérification du manifeste incluse)…"
./gradlew testDebugUnitTest lint assembleRelease

BUILT=app/build/outputs/apk/release/app-release.apk
[[ -f $BUILT ]] || { echo "❌ APK signé introuvable : $BUILT (signature non configurée ?)" >&2; false; }
APK=app/build/outputs/apk/release/bullet-$VERSION-release.apk
cp "$BUILT" "$APK"

echo "▶ Vérification de la signature…"
APKSIGNER=$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)/apksigner
CERTS=$("$APKSIGNER" verify --print-certs "$APK")
echo "$CERTS" | grep -E "certificate DN|SHA-256" || true
if echo "$CERTS" | grep -q "CN=Android Debug"; then
    echo "❌ L'APK est signé avec la clé de debug." >&2
    false
fi

trap - ERR

cat <<EOF

✅ Version $VERSION ($NEW_CODE) prête : $APK

Pour commiter et créer le tag :

  git commit -am "chore: release $VERSION" && git tag $TAG && git push origin main $TAG

Puis sur GitHub → Releases → Draft a new release → tag $TAG, joindre l'APK.
EOF

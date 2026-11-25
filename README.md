# Bullet — reprendre le contrôle, à la carte

Application Android pour arrêter, limiter ou commencer des habitudes, avec journal quotidien,
vue d'ensemble par période et badges à collectionner.
« Bullet » est le nom actuel (une seule chaîne à changer : `app_name` dans `strings.xml` ; le logotype
`BrandWordmark` et l'écran de verrouillage le reprennent automatiquement).

Spécifications : [`docs/SPECIFICATIONS.md`](docs/SPECIFICATIONS.md).

## Installer

[<img src="https://raw.githubusercontent.com/ImranR98/Obtainium/main/assets/graphics/badge_obtainium.png"
  alt="Obtenir avec Obtainium" height="54">](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/superrache/bullet)

Obtainium installe l'APK publié dans les [releases GitHub](https://github.com/superrache/bullet/releases)
et propose les mises à jour automatiquement.

## Démarrer

Dans le conteneur de dev (`docker compose run life-control-dev`) :

```bash
./gradlew assembleDebug                 # APK de debug : app/build/outputs/apk/debug/
./gradlew test                          # tous les tests (domaine + Robolectric)
./gradlew :app:recordRoborazziDebug     # tests + captures d'écran : app/build/outputs/roborazzi/
./gradlew lintDebug
```

Installer sur un téléphone ou un émulateur de l'hôte : `adb connect host.docker.internal:5555`
puis `./gradlew installDebug`.

## Architecture

```
core/domain/   Kotlin pur : modèle, règles RG-xx (statuts, séries, stats, économies), moteur de badges
app/           Android : Room, DataStore, Hilt, WorkManager, Compose (Material 3)
  data/db          entités, DAO, base Room (schéma exporté dans app/schemas/)
  data/repository  dépôts, journal partagé (Journal), service de badges, export/import
  data/content     catalogue embarqué (assets/catalog/fr/catalog.json)
  notifications    rappels (WorkManager), action « Fait » depuis la notification
  widget           widget d'écran d'accueil (avancement du jour, sans détail)
  ui/              écrans : today, calendar, badges, discover, habit, onboarding, settings
```

- Le statut d'un jour n'est jamais stocké : il est recalculé depuis les entrées et les objectifs versionnés.
- `JournalRepository` expose un instantané évalué partagé par tous les écrans.
- `BadgeService` évalue les badges à chaque changement, enregistre les déblocages (définitifs) et les annonce une seule fois.
- `CardService` attribue une carte à collectionner par jour (rareté selon le taux de réussite) ; `CardGenerator` la tire
  en direct sur Wikipédia (seul accès réseau de l'application). Les sujets sont dans `assets/cards/themes.json`,
  vérifiables avec `python3 tools/cards/check_themes.py`.
- Le contenu (24 habitudes documentées, badges, services d'aide, messages) est dans `catalog.json` ; une
  traduction se fait en ajoutant `assets/catalog/<langue>/catalog.json` et `values-<langue>/strings.xml`.

## Tests

| Suite | Contenu |
|---|---|
| `core/domain` (34 tests) | règles RG-03 à RG-13, séries jours/semaines, stats, économies, badges |
| `HabitRepositoryTest` | versionnement d'objectif (RG-08), unicité des entrées, cascade, tri |
| `ExportRepositoryTest` | export → suppression → import, fusion « le plus récent gagne », fichiers invalides |
| `ReminderSchedulerTest` | calcul du prochain rappel, jour logique (RG-02) |
| `HabitCardTest` | carte de saisie rapide (Compose) |
| `AppJourneyTest` | parcours réel (Hilt + Room + catalogue) : onboarding, saisie, badge, calendrier, badges, fiches — avec captures |

## Publier une version

### Clé de signature (une seule fois)

La clé release signe toutes les versions : **sans elle, plus aucune mise à jour possible** (Android refuse
d'installer une version signée autrement). À sauvegarder hors du projet (gestionnaire de mots de passe, clé USB).

1. `keystore.properties` à la racine (non versionné) :

   ```properties
   storeFile=release.jks
   storePassword=…
   keyAlias=bullet
   keyPassword=…        # identique à storePassword (format PKCS12)
   ```

2. Le keystore (non versionné, `*.jks`), avec les mêmes mots de passe :

   ```bash
   keytool -genkeypair -keystore release.jks -storetype PKCS12 -alias bullet \
     -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Bullet"
   ```

Sans `keystore.properties`, `assembleRelease` produit un APK non signé.

### Construire la version

```bash
./release.sh 0.2.0
```

Le script incrémente `versionCode`, fixe `versionName`, lance tests, lint et build release, vérifie que l'APK
n'est pas signé avec la clé de debug, puis l'écrit dans `app/build/outputs/apk/release/bullet-<version>-release.apk`.
En cas d'échec, la version est restaurée.

Il fonctionne sur le Mac (SDK cherché dans `ANDROID_HOME`, puis `~/Library/Android/sdk` — `local.properties`
pointe vers le SDK du conteneur) ou dans le conteneur :
`docker compose run --rm life-control-dev ./release.sh 0.2.0`.

> L'APK release n'a pas la même signature que l'APK debug : pour passer de l'un à l'autre sur un téléphone, il faut
> désinstaller l'application, **ce qui efface ses données**. Faire un export (Réglages → Exporter) avant.

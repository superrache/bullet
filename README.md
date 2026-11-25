# Bullet — reprendre le contrôle, à la carte

Application Android pour arrêter, limiter ou commencer des habitudes, avec journal quotidien,
vue d'ensemble par période et badges à collectionner.
« Bullet » est le nom actuel (une seule chaîne à changer : `app_name` dans `strings.xml` ; le logotype
`BrandWordmark` et l'écran de verrouillage le reprennent automatiquement).

Spécifications : [`docs/SPECIFICATIONS.md`](docs/SPECIFICATIONS.md).

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

# PDF Déverrouilleur (Android)

Application Android simple qui retire le mot de passe et les restrictions
(impression, copie, modification) d'un fichier PDF, afin de pouvoir le
partager librement avec des collaborateurs.

Tout le traitement se fait **sur l'appareil**. Aucun fichier n'est envoyé
sur un serveur ou sur Internet.

## Comment ça marche

1. L'utilisateur choisit un PDF.
2. Si le fichier a seulement des restrictions (impression ou copie bloquées),
   l'application les retire immédiatement.
3. Si le fichier demande un mot de passe à l'ouverture, l'application le
   réclame. L'utilisateur saisit le mot de passe **qu'il connaît**.
4. L'application enregistre une copie déverrouillée, puis propose de la partager.

L'application **ne tente jamais de deviner** un mot de passe. Elle déverrouille
uniquement des fichiers que l'utilisateur peut déjà ouvrir. Utilisez-la sur vos
propres documents ou sur ceux que vous êtes autorisé à diffuser.

## Pile technique

- **Kotlin** + **Jetpack Compose** (interface).
- **PDFBox-Android** (Tom Roush) pour lire le PDF avec mot de passe et le
  réenregistrer sans chiffrement ni restrictions.
- `minSdk 24` (Android 7.0), `targetSdk 34`.

## Compilation

Ouvrir le dossier `PdfDeverrouilleur` dans **Android Studio**, laisser Gradle
se synchroniser, puis lancer sur un appareil ou un émulateur.

En ligne de commande (depuis `PdfDeverrouilleur/`) :

```bash
./gradlew assembleDebug
```

Le fichier `.apk` est produit dans `app/build/outputs/apk/debug/`.

Le fichier `local.properties` doit pointer vers votre SDK Android, par exemple :

```
sdk.dir=/Users/VOUS/Library/Android/sdk
```

Ce fichier n'est pas versionné : chaque développeur crée le sien.

## Installation sur un appareil

Activez le débogage USB, branchez le téléphone, puis :

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Vous pouvez aussi copier l'`.apk` sur le téléphone et l'ouvrir, après avoir
autorisé l'installation depuis des sources inconnues.

## Fichiers de test

Le script `outils/generer_pdf_tests.py` (nécessite `pikepdf`) génère six PDF
couvrant les cas de restrictions courants :

| Cas | Protection | Mot de passe |
|-----|-----------|--------------|
| Aucune protection | — | — |
| Impression interdite | propriétaire | aucun |
| Copie interdite | propriétaire | aucun |
| Toutes restrictions | propriétaire | aucun |
| Mot de passe d'ouverture | ouverture | `secret123` |
| Ouverture + propriétaire (AES-256) | ouverture | `open2024` |

```bash
pip install pikepdf
python3 outils/generer_pdf_tests.py
```

## Structure

| Fichier | Rôle |
|---------|------|
| `app/src/main/java/.../Deverrouilleur.kt` | Logique : ouvre le PDF, retire chiffrement et restrictions, enregistre. |
| `app/src/main/java/.../MainActivity.kt` | Interface Compose : choix du fichier, mot de passe, partage. |
| `app/src/main/AndroidManifest.xml` | Déclaration de l'activité et du FileProvider de partage. |
| `outils/generer_pdf_tests.py` | Génère des PDF de test aux restrictions variées. |

## Usage légitime

Cet outil retire des protections que **vous êtes en droit de retirer** : vos
propres documents, ou ceux que vous êtes autorisé à diffuser. Il exige le mot
de passe d'ouverture pour les fichiers chiffrés et ne tente jamais de le
deviner. Ne l'utilisez pas pour contourner les protections de documents
appartenant à des tiers sans autorisation.

## Politique de confidentialité

L'application ne collecte aucune donnée et traite tout sur l'appareil. La
politique de confidentialité est disponible dans `docs/politique-confidentialite.md`
(version HTML : `docs/index.html`), prête à être hébergée via GitHub Pages pour
fournir l'URL publique exigée par le Play Store.

## Licence

MIT. Voir le fichier `LICENSE`.

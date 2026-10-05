SMS Forwarder est une application mobile permettant de transférer les SMS reçus d’un téléphone vers une autre destination.

## Flux de travail
- Le développement se fait sur `main`, par des PR. Chaque PR vers `main` compile l’APK de debug et le joint en artefact (GitHub Actions). Chaque push sur `main` produit aussi un APK d’intégration.
- Pour chaque livraison, une branche `release/x.y.z` est créée depuis `main` : le push sur `release/**` produit l’APK.
- Le workflow peut aussi être lancé à la main (onglet Actions).
- Pour les livraisons (`release/**`) et les lancements manuels, l’APK est aussi envoyé sur Telegram (secrets `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID`, à défaut `BOT_TOKEN` / `CHAT_ID`), avec la légende « SMS Forwarder <version> · <branche> · <commit> ».
- Version affichée selon l’origine du build (SemVer : suffixe de pré-version, puis `+commit` en métadonnées) :

  | Origine | `versionName` | APK |
  |---|---|---|
  | `release/1.3.2` | `1.3.2` | `sms-forwarder-1.3.2.apk` |
  | push sur `main` | `1.3.2-dev.<run>+<commit>` | `sms-forwarder-1.3.2-dev.<run>.apk` |
  | PR n° 11 | `1.3.2-pr.11.<run>+<commit>` | `sms-forwarder-1.3.2-pr.11.<run>.apk` |

  Le CI calcule le suffixe (variable `VERSION_SUFFIX`, lue par `versionNameSuffix` dans `app/build.gradle`) ; un build local n’en a pas. Le `versionCode` ne change pas : la livraison s’installe toujours par-dessus un APK de test. Après chaque livraison, monter `versionName` / `versionCode` sur `main` : `main` porte toujours la prochaine version.
- L’artefact porte le nom de l’APK sans `.apk`.

## Outil « SMS » de la console Miango (v1.2.0)
Chaque SMS relayé est aussi envoyé à `POST https://miango.netlify.app/sms/incoming`, avec la SIM qui l'a reçu, pour être consultable dans la console Miango (Outils → SMS) :
- en-tête `X-Sms-Token` : secret partagé, à fournir au build par la variable d'environnement `SMS_INGEST_TOKEN` (même valeur que dans Netlify) ;
- corps : `from`, `body`, `timestamp`, `sim` (`subscription_id`, `slot`, `carrier`, `number`, `name`) et `device` (`id` généré une fois, `name`).

L'écran principal permet de régler :
- le nom du téléphone ;
- le nom de chaque SIM active (envoyé par défaut ; un renommage dans la console Miango reste prioritaire) ;
- la liste des expéditeurs autorisés (un par ligne) ;
- les règles avancées (v1.3.0), décrites ci-dessous.

La permission « Téléphone » (`READ_PHONE_STATE`) sert à identifier la SIM (emplacement, opérateur, numéro).

## Règles de filtrage avancées (v1.3.0)
Une regex par ligne, en plus de la liste des expéditeurs autorisés :

| Règle | S'applique à |
|---|---|
| `from:<regex>` | l'expéditeur |
| `body:<regex>` | le corps du SMS |
| `<regex>` (sans préfixe) | l'expéditeur **ou** le corps |
| `!` en tête (`!body:<regex>`, `!from:<regex>`, `!<regex>`) | règle d'**exclusion** |

Un SMS est relayé si aucune règle d'exclusion ne correspond, **et** si son expéditeur est dans la liste ou si au moins une règle d'inclusion correspond. La regex est cherchée n'importe où dans le texte (`^` et `$` restent possibles). Règle par défaut : `body:(?i)paypal`, qui reprend le comportement de la v1.2.0. Une regex invalide est refusée à l'enregistrement.

## Destinations configurables (v1.3.0)
Écran « Destinations des SMS » :
- **URL** : liste éditable (ajouter, modifier, supprimer, activer ou désactiver). URL absolue `http(s)://…` validée à l'enregistrement, en-tête de sécurité optionnel (nom et valeur, valeur jamais réaffichée). Toutes les URL reçoivent le format complet `{ from, body, timestamp, sim, device }` ; `/smshandler` n'en lit que `from`, `body` et `timestamp`.
- **Telegram** : token du bot, identifiant de la conversation, activation. Message en texte brut : corps du SMS, ligne vide, expéditeur, puis SIM (nom donné dans l'app, sinon « SIM n · opérateur ») et téléphone, et enfin la date (v1.3.2).

Valeurs par défaut (installation ou mise à jour depuis la v1.2.0, sans action) : `https://miango.netlify.app/smshandler` sans en-tête, `https://miango.netlify.app/sms/incoming` avec `X-Sms-Token` = `SMS_INGEST_TOKEN` du build, et Telegram avec `BOT_TOKEN` / `CHAT_ID` du build.

Chaque SMS relayé part vers toutes les destinations actives, l'une après l'autre. Résultat d'un envoi :
- réponse 2xx : envoyé ;
- réponse d'erreur (4xx, 5xx) : en échec, **sans nouvel envoi automatique**. `/smshandler` répond 500 aux SMS qui ne viennent pas d'AirtelMoney, et peut avoir déjà mis à jour le solde avant une erreur ;
- pas de réponse (réseau absent, délai dépassé) : nouvelles tentatives.

Les secrets (valeurs d'en-tête, token du bot) ne sont jamais écrits dans les logs.

## Journal des SMS (v1.3.0)
L'écran « Journal des SMS » liste les SMS relayés ces **30 derniers jours** (SQLite local, purge automatique), du plus récent au plus ancien, avec un chargement progressif en fin de liste. Chaque entrée affiche la date de réception, l'expéditeur, la SIM et un extrait (texte complet en touchant l'entrée), puis, pour chaque destination : statut (`envoyé`, `en échec`, `nouvelle tentative`, `en attente`), nombre d'essais, date du dernier essai et erreur. Le journal ne contient aucun secret (URL sans paramètres, erreurs masquées).

## Synchronisation des SMS manqués (v1.3.0)
Une coupure réseau touche le téléphone entier, donc toutes ses SIM. L'application garde l'**horodatage de la dernière synchronisation réussie** et vérifie que tous les SMS reçus depuis ont été relayés :
1. **Point de départ** : la réception du premier SMS envoyé avec succès. Les SMS antérieurs ne sont jamais repris.
2. Les envois du journal encore `en attente` ou `nouvelle tentative` sont refaits, destination par destination. Un envoi `envoyé` n'est jamais refait.
3. La boîte de réception (`content://sms/inbox`, toutes SIM, colonne `sub_id`) est lue depuis la dernière synchronisation réussie, avec une marge de 2 minutes. Un SMS retenu par les filtres (expéditeurs et règles avancées) et absent du journal est relayé. Il est reconnu par une empreinte (expéditeur, horodatage du centre SMS, corps), complétée par une comparaison expéditeur + corps à 10 minutes près.
4. La dernière synchronisation réussie n'avance que jusqu'au plus ancien SMS encore non synchronisé. Un envoi sans réponse depuis plus de 7 jours est abandonné (marqué `en échec`).

Déclenchement (JobScheduler, toujours avec réseau) : toutes les 15 minutes, au retour du réseau quand des envois restent à faire, au démarrage du téléphone, et par le bouton « Synchroniser maintenant » de l'écran principal, qui affiche aussi la dernière synchronisation réussie. Le journal indique les SMS envoyés lors d'une synchronisation (« synchro »).

Aucun changement n'est nécessaire côté Miango : `/sms/incoming` ignore un SMS déjà enregistré (`dedup_hash`).

## Choix des SIM transférées (v1.4.0)
Sur l'écran principal, chaque SIM a une case « Transférer les SMS de cette SIM », appliquée dès qu'elle est touchée (règle : `filter/SimPolicy`, testée par `SimPolicyTest`) :
- une SIM jamais réglée, dont une SIM nouvellement insérée, est **active** ;
- une SIM désactivée ne transfère plus rien, ni à la réception ni à la synchronisation ;
- une SIM **réactivée** ne transfère que les SMS reçus **après** sa réactivation. La synchronisation peut relire une fenêtre plus ancienne (marge de 2 min, envoi en attente qui retient la dernière synchronisation) : les SMS reçus pendant la désactivation ne sont jamais repris ;
- les envois déjà au journal quand la SIM est désactivée (SMS accepté quand elle était active) sont **terminés** ;
- tant qu'une SIM est désactivée, un SMS dont la SIM n'est pas identifiée (colonne `sub_id` absente, permission « Téléphone » refusée) **n'est pas transféré** ;
- une SIM désactivée retirée du téléphone reste listée (« SIM absente du téléphone ») pour pouvoir la réactiver.

Le choix est enregistré par identifiant d'abonnement Android (`subscriptionId`), comme le nom de la SIM. Un SMS non transféré n'apparaît pas dans le journal (comme un SMS refusé par les filtres).

## Identifiant du téléphone et signature de l'APK (v1.3.1)
L'identifiant envoyé à Miango (`device.id`) est dérivé d'`ANDROID_ID`, haché, de la forme `a-<32 hex>`. Il reste le même après une désinstallation puis une réinstallation, **à condition que l'APK soit toujours signé avec la même clé**. Il ne change qu'après une réinitialisation du téléphone. Sans `ANDROID_ID` utilisable, un identifiant aléatoire est généré une fois.

Pour une clé de signature fixe, créer une seule fois un keystore et le déclarer dans les secrets GitHub Actions :

```bash
keytool -genkeypair -v -keystore smsforwarder.keystore -alias smsforwarder \
  -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 smsforwarder.keystore   # valeur du secret SIGNING_KEYSTORE_BASE64
```

Secrets : `SIGNING_KEYSTORE_BASE64`, `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` (`smsforwarder`) et `SIGNING_KEY_PASSWORD`. Conserver le keystore en lieu sûr : quiconque le possède peut signer un APK capable de remplacer l'application, avec ses permissions SMS. Sans ces secrets, chaque build du CI est signé avec une clé de debug temporaire. L'APK ne peut alors pas mettre à jour le précédent, et l'identifiant change à chaque installation.

SMS Forwarder est une application mobile permettant de transférer les SMS reçus d’un téléphone vers une autre destination.

## Flux de travail
- Le développement se fait sur `main`, par des PR. Chaque PR vers `main` compile l’APK de debug et le joint en artefact (GitHub Actions).
- Pour chaque livraison, une branche `release/x.y.z` est créée depuis `main` : le push sur `release/**` produit l’APK.
- Le workflow peut aussi être lancé à la main (onglet Actions).

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
- **Telegram** : token du bot, identifiant de la conversation, activation.

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

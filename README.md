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
- la liste des expéditeurs autorisés (un par ligne). Les SMS contenant « paypal » sont toujours relayés.

La permission « Téléphone » (`READ_PHONE_STATE`) sert à identifier la SIM (emplacement, opérateur, numéro).

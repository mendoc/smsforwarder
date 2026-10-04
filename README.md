
## Outil « SMS » de la console Miango (v1.2.0)
Chaque SMS relayé est aussi envoyé à `POST https://miango.netlify.app/sms/incoming`, avec la SIM qui l'a reçu, pour être consultable dans la console Miango (Outils → SMS) :
- en-tête `X-Sms-Token` : secret partagé, à fournir au build par la variable d'environnement `SMS_INGEST_TOKEN` (même valeur que dans Netlify) ;
- corps : `from`, `body`, `timestamp`, `sim` (`subscription_id`, `slot`, `carrier`, `number`, `name`) et `device` (`id` généré une fois, `name`).

L'écran principal permet de régler :
- le nom du téléphone ;
- le nom de chaque SIM active (envoyé par défaut ; un renommage dans la console Miango reste prioritaire) ;
- la liste des expéditeurs autorisés (un par ligne). Les SMS contenant « paypal » sont toujours relayés.

La permission « Téléphone » (`READ_PHONE_STATE`) sert à identifier la SIM (emplacement, opérateur, numéro).

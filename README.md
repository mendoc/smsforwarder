SMS Forwarder est une application mobile permettant de transférer les SMS reçus d’un téléphone vers une autre destination.

## Usages possibles
- Recevoir des SMS sur Telegram, par e-mail;
- Transférer des SMS sur une application Web existante;
- Transférer des SMS sur un autre téléphone pour tout centraliser.

## Comportement
- L’application se lance en arrière-plan au démarrage du téléphone;
- Une notification persistante est affiché dans le ruban supérieure pour signaler que l’application tourne en arrière-plan;
- [x] L’application tourne en arrière-plan pour surveiller l’arrivée de SMS. A la réception d’un SMS, elle le transfère directement vers une destination paramétrée par l’utilisateur.

## Fonctionnalités
- [x] Transfert les SMS entrants;
- Définir la destination des SMS;
- Définir une liste d’exceptions d'émetteurs pour soit ne transférer que certains SMS ou ignorer certains;
- Définir un nom pour le téléphone afin de l’identifier;
- Démarrer/Arrêter la surveillance des SMS;
- Consulter la liste de tous les SMS transférés;
- Transférer les potentiels SMS non transférés dû à une coupure d’Internet;
- Créer des règles de routage pour des transferts plus personnalisés;

## Paramètres de l’application
Une interface permet de configurer l’application. Ci-dessous, la liste des paramètres disponibles.
- Nom du téléphone : définir un nom pour identifier le téléphone sur lequel l’application tourne (Ex: téléphone du bureau, téléphone pour les PayPal)
- Mode de transfert : définir le comportement à adopter lors de la réception d’un SMS. Deux modes sont possibles : 
  - Transférer tout sauf : transférer tous les SMS entrants sauf ceux des émetteurs de la liste d'exceptions;
  - Ignorer tout sauf : ne transférer que les SMS des émetteurs de la liste d'exceptions.
- Liste des exceptions : définir une liste d’émetteurs pour le mode de transfert;
- Destination des SMS : configurer une ou plusieurs destinations pour le transfert des SMS. 
  - URL : faire une requête HTTP POST à la réception d’un SMS en envoyant les informations suivantes au format JSON : 
    - from : le numéro de l'émetteur;
    - body : le contenu du message;
    - timestamp : la date de réception du SMS en millisecondes;
    - device : le nom du téléphone sur lequel tourne l’application.
  - bot Telegram : définir les information d’un bot Telegram vers lequel les SMS seront transférés
    - Token du bot
    - Identifiant de la conversation
  - Numéro : un ou plusieurs numéros de téléphone vers lesquels le SMS sera transféré. Cette fonctionnalité utilise la carte SIM du téléphone pour transférer le SMS.
  - E-mail : une ou plusieurs adresses e-mail vers lesquels transférer les SMS.
- Lien de la console d'administration : renseigner un lien pour lier l’application à une interface d’administration Web.

## Risques
Si quelqu’un l’installe dans votre téléphone sans votre consentement, il pourra recevoir tous vos SMS.

# Rapport de Projet : Système de Messagerie et Partage de Fichiers via UDP

## 1. Description Générale
Ce projet est une application de communication en temps réel basée sur le protocole **UDP** (User Datagram Protocol). Il permet à plusieurs clients de s'échanger des messages texte et des fichiers (images, PDF, etc.) via un serveur central.

---

## 2. Utilisation de l'UDP
Le projet utilise exclusivement les sockets UDP de Java (`DatagramSocket` et `DatagramPacket`). Contrairement au TCP, l'UDP est un protocole "sans connexion", ce qui signifie que :
- Les données sont envoyées sous forme de **Datagrammes** indépendants.
- Il n'y a pas de garantie native de livraison ou d'ordre (une couche de fiabilité personnalisée a été ajoutée pour le transfert de fichiers).

### Détails Techniques de l'UDP :
- **Classe `PacketProtocol`** : C'est le cœur de la communication. Elle définit la structure des paquets (Type, ID Client, Destinataire, Séquence, Données) et gère la **sérialisation** (conversion d'objets en tableaux d'octets `byte[]`) pour l'envoi via UDP.
- **Port du Serveur** : Le serveur écoute sur le port fixe `9876`.
- **Ports Clients** : Les clients utilisent des ports aléatoires (port `0` passé au constructeur `DatagramSocket`).

---

## 3. Emplacement de l'Implémentation UDP
Voici où l'UDP a été intégré dans le code :

- **`src/PacketProtocol.java`** : Définit comment les paquets sont formés et transformés en octets pour être transportés par UDP.
- **`src/Server.java`** : Contient la boucle de réception principale (`startPacketReceiver`) qui utilise `socket.receive(datagramPacket)`.
- **`src/Client.java`** & **`src/ClientGUI.java`** : Contiennent également des boucles de réception et des méthodes d'envoi (`sendPacket`) utilisant `socket.send(datagramPacket)`.

---

## 4. Mécanisme de Diffusion (Broadcast)
La diffusion (envoyer un message à tout le monde) est gérée par le serveur.

### Comment ça marche :
1.  **Réception** : Un client envoie un paquet au serveur avec le destinataire marqué comme `"ALL"`.
2.  **Relais** : Le serveur reçoit ce paquet dans la méthode `processPacket`.
3.  **Iteration** : La méthode `broadcastPacket` dans `Server.java` parcourt la liste des clients connectés (`Map<String, ClientInfo> clients`).
4.  **Envoi** : Pour chaque client (sauf l'expéditeur), le serveur crée un nouveau `DatagramPacket` et l'envoie sur le socket UDP.

```java
// Extrait de Server.java (Méthode broadcastPacket)
for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
    if (!entry.getKey().equals(excludeClientId)) {
        ClientInfo client = entry.getValue();
        DatagramPacket datagramPacket = new DatagramPacket(
            data, data.length, client.address, client.port
        );
        socket.send(datagramPacket);
    }
}
```

---

## 5. Comment Lancer/Appeler les Messages de Diffusion

### Via la Ligne de Commande (Client.java) :
- **Messages Texte** : Il suffit de taper votre message directement dans la console et d'appuyer sur **Entrée**. Le client crée automatiquement un paquet de type `MSG` avec le destinataire `"ALL"`.
- **Fichiers** : Utilisez la commande `/file <chemin_du_fichier>`. Exemple : `/file test.txt`.

### Via l'Interface Graphique (ClientGUI.java) :
1.  **Sélecteur de destinataire** : Assurez-vous que l'option `"ALL (Broadcast)"` est sélectionnée dans le menu déroulant (c'est l'option par défaut).
2.  **Envoi de message** : Tapez votre texte dans le champ en bas et cliquez sur le bouton **"Send Message"**.
3.  **Envoi de fichier** : Cliquez sur **"Send File"**, choisissez votre fichier, et il sera diffusé à tous les utilisateurs connectés.

---

## 6. Résumé des Commandes et Actions
| Action | Commande (Console) | Action (GUI) |
| :--- | :--- | :--- |
| **Envoyer un message à tous** | `Texte + Entrée` | Taper texte + bouton "Send Message" |
| **Envoyer un fichier à tous** | `/file chemin/fichier` | Bouton "Send File" |
| **Voir les utilisateurs** | `/users` | Liste à droite ou bouton "Refresh Users" |
| **Quitter** | `/quit` | Fermer la fenêtre |

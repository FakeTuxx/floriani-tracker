# Floriani Tracker - großer Umbau

## Neuer Entwicklungslogin

Beim ersten Start wird automatisch ein Testzugang angelegt:

- Benutzername: `wundschuh`
- Passwort: `floriani2026`

Wichtig: Vor echter Veröffentlichung muss dieser Zugang geändert bzw. durch ein Admin-System ersetzt werden.

## Was eingebaut wurde

- Login vor der Nutzung
- Benutzer ist einer Feuerwehr zugeordnet
- Feuerwehr ist einer Gemeinde zugeordnet
- Nach dem Login werden automatisch Feuerwehr, Gemeinde und Sammellisten geladen
- Häuser und Listen sind pro Feuerwehr getrennt
- bestehende Häuser ohne Feuerwehr-Zuordnung werden beim ersten Start automatisch der FF Wundschuh zugeordnet
- neue mobile Oberfläche
- Hausliste als große Handy-Karten statt Tabelle
- Filter nach Status, Geld, Betrag, Suche und Sortierung
- Schnellbuttons: Gemacht, Später, Offen
- optional einblendbare Karte
- OSM-Hausimport verwendet jetzt automatisch die Gemeinde der angemeldeten Feuerwehr
- Datenbank-Fallback für lokale Entwicklung wieder auf H2 gesetzt

## Nächste sinnvolle Schritte

1. Echtes Admin-Menü bauen, um Feuerwehren, Benutzer und Gemeinden anzulegen.
2. Offizielle vollständige Gemeindeliste der Statistik Austria importieren.
3. Passwort-Ändern-Funktion bauen.
4. Für Veröffentlichung PostgreSQL auf Render verwenden.
5. Datenschutz, Impressum und Backup-Konzept ergänzen.

## Mehrere Feuerwehr-/Gemeinde-Logins

Beim Start werden nun für alle vorbereiteten steirischen Testgemeinden automatisch Feuerwehr-Logins erstellt.

Passwort für alle Testzugänge:

```text
floriani2026
```

Beispiele:

```text
wundschuh
graz
feldkirchenbeigraz
seiersbergpirka
premstaetten
kalsdorfbeigraz
leibnitz
gleisdorf
weiz
bruckandermur
muerzzuschlag
gralla
heiligenkreuzamwaasen
deutschlandsberg
stainz
hartberg
fuerstenfeld
judenburg
knittelfeld
leoben
liezen
```

Jeder Login lädt automatisch die passende Feuerwehr und Gemeinde.


## PDF-Druck-Fix
- Beim Drucken werden die Umschalt- und Druckbuttons nicht mehr mitgedruckt.
- Die PDF-Tabelle zeigt jetzt sauber die aktive Filterbeschreibung, z. B. Status oder Mindestbetrag.
- Gedruckt wird nur die schlichte PDF-Ansicht mit Titel, Feuerwehr/Gemeinde, Filterinfo und Tabelle.

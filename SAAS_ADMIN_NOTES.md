# Floriani Tracker - SaaS/Admin Ausbau

## Neue Logins

Super-Admin:

- Benutzername: `admin`
- Passwort: `admin2026`

Demo-Feuerwehren bleiben vorhanden, z. B. `wundschuh`, `graz`, `leibnitz` mit Passwort `floriani2026`.

## Was neu ist

- Super-Admin-Bereich direkt in der Website
- Feuerwehren/Kunden anlegen
- Gemeinde/Bezirk/Bundesland zur Feuerwehr speichern
- erster Feuerwehr-Admin wird automatisch erstellt
- Abo-Status speichern: `TEST`, `ACTIVE`, `EXPIRED`, `BLOCKED`
- optionales Ablaufdatum für das Abo
- Login wird blockiert, wenn Feuerwehr deaktiviert/abgelaufen/gesperrt ist
- weitere Benutzer pro Feuerwehr anlegen: `ADMIN`, `COLLECTOR`, `READ_ONLY`
- Daten bleiben pro Feuerwehr getrennt
- PostgreSQL-Abhängigkeit ist bereits im Projekt vorhanden

## Für Veröffentlichung

Für echte Nutzung nicht H2 verwenden, sondern PostgreSQL. Lokal bleibt H2 als Entwicklungsdatenbank möglich.

Wichtige Umgebungsvariablen für Hosting:

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `SPRING_DATASOURCE_DRIVER=org.postgresql.Driver`
- `H2_CONSOLE_ENABLED=false`


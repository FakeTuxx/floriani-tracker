# Floriani Tracker - Render/PostgreSQL Deployment

## Lokal testen

```powershell
.\mvnw.cmd spring-boot:run
```

Lokal verwendet die App standardmäßig H2.

## Render / Produktion

Für öffentliche Nutzung soll PostgreSQL verwendet werden. Auf Render im Webservice diese Environment Variables setzen:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:<port>/<database>?sslmode=require
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>
SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
H2_CONSOLE_ENABLED=false
APP_DEMO_DATA_ENABLED=false
SESSION_COOKIE_SECURE=true
JPA_DDL_AUTO=update
APP_SUPER_ADMIN_USERNAME=<dein-admin-user>
APP_SUPER_ADMIN_PASSWORD=<sicheres-admin-passwort>
APP_SUPER_ADMIN_DISPLAY_NAME=System Admin
```

Wichtig: In Produktion `APP_DEMO_DATA_ENABLED=false` lassen, damit keine Test-Feuerwehren wie `graz/floriani2026` erstellt werden.

## Abo / Kundenbetrieb

1. Mit Super-Admin einloggen.
2. Feuerwehr anlegen.
3. Gemeinde, Kontakt, Abo-Status und Ablaufdatum setzen.
4. Ersten Feuerwehr-Admin-Zugang erstellen.
5. Zugangsdaten an Feuerwehr geben.

## Datenbank

Aktuell nutzt das Projekt noch `spring.jpa.hibernate.ddl-auto=update`. Das ist für die erste produktionsnahe Testphase praktisch. Für einen stabilen Echtbetrieb mit vielen Kunden sollte später auf Flyway/Liquibase-Migrationen und `validate` umgestellt werden.

## Backups

PostgreSQL-Backups beim Hosting-Anbieter aktivieren. Vor größeren Updates immer Backup erstellen.

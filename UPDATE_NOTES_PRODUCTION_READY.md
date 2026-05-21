# Update: Produktions-/SaaS-Ausbau

Neu in dieser Version:

- PostgreSQL-/Render-ready Konfiguration über Environment Variables
- Demo-Daten können per `APP_DEMO_DATA_ENABLED=false` deaktiviert werden
- Super-Admin-Zugang kann per Environment Variables gesetzt werden
- Passwort ändern für eingeloggte Benutzer
- Listen duplizieren für neues Jahr
- Excel-Export mit denselben Filtern wie die Liste
- PDF-Ansicht öffnet auf eigener Seite, damit es am Handy besser scrollbar ist
- Render Blueprint `render.yaml`
- Dockerfile korrigiert (`COPY . .`)

Nicht eingebaut:

- CSV/Excel-Import für Familiennamen, weil aktuell nicht gewünscht.

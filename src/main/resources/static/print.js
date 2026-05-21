document.addEventListener("DOMContentLoaded", () => {
    document.getElementById("printNowButton").addEventListener("click", () => window.print());
    document.getElementById("closePrintButton").addEventListener("click", () => window.close());
    renderPrintView();
});

function renderPrintView() {
    const container = document.getElementById("printContent");
    let payload;
    try {
        payload = JSON.parse(sessionStorage.getItem("florianiPrintPayload") || localStorage.getItem("florianiPrintPayload") || "null");
    } catch {
        payload = null;
    }

    if (!payload) {
        container.innerHTML = `<div class="empty-state">Keine PDF-Daten gefunden. Bitte die PDF-Ansicht aus der App heraus öffnen.</div>`;
        return;
    }

    const rows = (payload.houses || []).map(house => `
        <tr>
            <td>${escapeHtml(house.address)}</td>
            <td>${escapeHtml(house.residentName || "")}</td>
            <td>${statusLabel(house.status)}</td>
            <td>${house.donationAmount ? formatEuro(house.donationAmount) : ""}</td>
            <td>${escapeHtml(house.note || "")}</td>
        </tr>
    `).join("");

    container.innerHTML = `
        <div class="pdf-title standalone-print-title">
            <h1>${escapeHtml(payload.listName || "Sammelliste")}</h1>
            <p>${escapeHtml(payload.departmentName || "")} · ${escapeHtml(payload.municipalityName || "")}</p>
            <p><strong>${Number(payload.shownCount || 0)} von ${Number(payload.totalCount || 0)} Häusern</strong> · erstellt am ${escapeHtml(payload.generatedAt || "")}</p>
            <p class="pdf-filter-note">${escapeHtml(payload.filters || "Aktive Filter: keine")}</p>
        </div>
        <table class="pdf-table standalone-print-table">
            <thead>
                <tr>
                    <th>Adresse</th>
                    <th>Name</th>
                    <th>Status</th>
                    <th>Spende</th>
                    <th>Notiz</th>
                </tr>
            </thead>
            <tbody>${rows || `<tr><td colspan="5">Keine Häuser für diese Filter.</td></tr>`}</tbody>
        </table>
    `;
}

function statusLabel(status) {
    const labels = {
        OFFEN: "Offen",
        SPAETER_NOCHMAL: "Später nochmal",
        ERLEDIGT: "Erledigt",
        UEBERSPRINGEN: "Überspringen"
    };
    return labels[status] || "Offen";
}

function formatEuro(value) {
    return new Intl.NumberFormat("de-AT", {style: "currency", currency: "EUR"}).format(Number(value || 0));
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

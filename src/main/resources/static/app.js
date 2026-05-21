let map;
let mapInitialized = false;
let houseMarkers = [];
let userLocationMarker = null;
let userAccuracyCircle = null;
let municipalityBoundaryLayer = null;
let currentUser = null;
let currentDepartment = null;
let currentHouses = [];
let currentListName = "";
let overlayMode = "LIST";
let locateControlAdded = false;
let houseBeingEdited = null;
let isLoadingHouses = false;

const STATUS_LABELS = {
    OFFEN: "Offen",
    SPAETER_NOCHMAL: "Später nochmal",
    ERLEDIGT: "Erledigt",
    UEBERSPRINGEN: "Überspringen"
};

const STATUS_ORDER = {
    OFFEN: 1,
    SPAETER_NOCHMAL: 2,
    ERLEDIGT: 3,
    UEBERSPRINGEN: 4
};

document.addEventListener("DOMContentLoaded", async () => {
    initEvents();
    await checkLogin();

    if ("serviceWorker" in navigator) {
        navigator.serviceWorker.register("/service-worker.js")
            .catch(error => console.log("Service Worker Fehler:", error));
    }
});

function initEvents() {
    document.getElementById("loginForm").addEventListener("submit", login);
    document.getElementById("logoutButton").addEventListener("click", logout);
    document.getElementById("adminLogoutButton")?.addEventListener("click", logout);
    document.getElementById("adminCreateDepartmentButton")?.addEventListener("click", adminCreateDepartment);
    document.getElementById("adminReloadButton")?.addEventListener("click", loadAdminDashboard);
    document.getElementById("adminSearch")?.addEventListener("input", debounce(loadAdminDepartments, 250));

    document.getElementById("menuButton").addEventListener("click", openMenu);
    document.getElementById("closeMenuButton").addEventListener("click", closeMenu);
    document.getElementById("sideMenu").addEventListener("click", event => {
        if (event.target.id === "sideMenu") closeMenu();
    });

    document.getElementById("openCreateListButton").addEventListener("click", () => {
        closeMenu();
        openCreateListModal();
    });
    document.getElementById("openDuplicateListButton")?.addEventListener("click", () => {
        closeMenu();
        openDuplicateListModal();
    });
    document.getElementById("openListButton").addEventListener("click", () => {
        closeMenu();
        openListOverlay("LIST");
    });
    document.getElementById("openPdfButton").addEventListener("click", () => {
        closeMenu();
        openPrintablePage();
    });
    document.getElementById("excelExportButton")?.addEventListener("click", () => {
        closeMenu();
        exportExcel();
    });
    document.getElementById("openPasswordButton")?.addEventListener("click", () => {
        closeMenu();
        openPasswordModal();
    });
    document.getElementById("importButton").addEventListener("click", async () => {
        closeMenu();
        await importMunicipalityOsmAddresses({askBeforeImport: true});
    });

    document.getElementById("closeListOverlayButton").addEventListener("click", closeListOverlay);
    document.getElementById("listOverlay").addEventListener("click", event => {
        if (event.target.id === "listOverlay") closeListOverlay();
    });

    document.getElementById("listViewButton").addEventListener("click", () => setOverlayMode("LIST"));
    document.getElementById("pdfViewButton").addEventListener("click", openPrintablePage);
    document.getElementById("printPdfButton").addEventListener("click", () => {
        setOverlayMode("PDF");
        openPrintablePage();
    });
    document.getElementById("excelOverlayButton")?.addEventListener("click", exportExcel);

    document.getElementById("closeCreateListButton").addEventListener("click", closeCreateListModal);
    document.getElementById("cancelCreateListButton").addEventListener("click", closeCreateListModal);
    document.getElementById("createListModal").addEventListener("click", event => {
        if (event.target.id === "createListModal") closeCreateListModal();
    });
    document.getElementById("createListButton").addEventListener("click", createNewList);
    document.getElementById("newListName").addEventListener("keydown", event => {
        if (event.key === "Enter") createNewList();
    });

    document.getElementById("closeDuplicateListButton")?.addEventListener("click", closeDuplicateListModal);
    document.getElementById("cancelDuplicateListButton")?.addEventListener("click", closeDuplicateListModal);
    document.getElementById("duplicateListModal")?.addEventListener("click", event => {
        if (event.target.id === "duplicateListModal") closeDuplicateListModal();
    });
    document.getElementById("duplicateListButton")?.addEventListener("click", duplicateCurrentList);
    document.getElementById("duplicateTargetName")?.addEventListener("keydown", event => {
        if (event.key === "Enter") duplicateCurrentList();
    });

    document.getElementById("closePasswordButton")?.addEventListener("click", closePasswordModal);
    document.getElementById("cancelPasswordButton")?.addEventListener("click", closePasswordModal);
    document.getElementById("passwordModal")?.addEventListener("click", event => {
        if (event.target.id === "passwordModal") closePasswordModal();
    });
    document.getElementById("savePasswordButton")?.addEventListener("click", changePassword);

    document.getElementById("closeEditHouseButton").addEventListener("click", closeEditHouseModal);
    document.getElementById("cancelEditHouseButton").addEventListener("click", closeEditHouseModal);
    document.getElementById("editHouseModal").addEventListener("click", event => {
        if (event.target.id === "editHouseModal") closeEditHouseModal();
    });
    document.getElementById("saveEditHouseButton").addEventListener("click", saveEditHouseModal);
    document.getElementById("editMapButton").addEventListener("click", () => {
        if (!houseBeingEdited) return;
        const house = houseBeingEdited;
        closeEditHouseModal();
        focusHouseOnMap(house);
    });

    document.getElementById("listSelect").addEventListener("change", async () => {
        currentListName = document.getElementById("listSelect").value;
        if (currentListName) {
            await loadHouses({showLoading: true, loadingText: "Liste wird geladen ..."});
        } else {
            currentHouses = [];
            clearHouseMarkers();
            updateEmptyStats();
            renderHouseList();
        }
        updateActiveListTitle();
    });

    ["searchInput", "statusFilter", "moneyFilter", "moneyValue", "sortSelect"].forEach(id => {
        document.getElementById(id).addEventListener("input", renderHouseList);
        document.getElementById(id).addEventListener("change", renderHouseList);
    });

    document.addEventListener("keydown", event => {
        if (event.key !== "Escape") return;
        closeMenu();
        closeCreateListModal();
        closeDuplicateListModal();
        closePasswordModal();
        closeEditHouseModal();
        closeListOverlay();
    });
}

async function checkLogin() {
    try {
        const response = await fetch("/api/auth/me");
        if (!response.ok) {
            showLogin();
            return;
        }
        currentUser = await response.json();
        if (currentUser.superAdmin) {
            await bootAdmin();
        } else {
            await bootApp();
        }
    } catch (error) {
        console.error(error);
        showLogin();
    }
}

async function login(event) {
    event.preventDefault();
    const username = document.getElementById("usernameInput").value.trim();
    const password = document.getElementById("passwordInput").value;
    const message = document.getElementById("loginMessage");

    message.textContent = "";

    try {
        const response = await fetch("/api/auth/login", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({username, password})
        });

        if (!response.ok) {
            const error = await safeJson(response);
            message.textContent = error.message || "Login fehlgeschlagen.";
            return;
        }

        currentUser = await response.json();
        document.getElementById("passwordInput").value = "";
        if (currentUser.superAdmin) {
            await bootAdmin();
        } else {
            await bootApp();
        }
    } catch (error) {
        console.error(error);
        message.textContent = "Server nicht erreichbar.";
    }
}

async function logout() {
    await fetch("/api/auth/logout", {method: "POST"}).catch(() => {});
    currentUser = null;
    currentDepartment = null;
    currentHouses = [];
    currentListName = "";
    closeMenu();
    closeListOverlay();
    closeEditHouseModal();
    showLogin();
}

async function bootAdmin() {
    showAdmin();
    await loadAdminDashboard();
}

async function bootApp() {
    showApp();
    await loadDepartment();
    await loadLists();
    currentHouses = [];
    updateEmptyStats();
    updateActiveListTitle();
    initMapIfNeeded();
    setTimeout(() => map?.invalidateSize(), 150);
}

function showLogin() {
    document.getElementById("loginView").classList.remove("hidden");
    document.getElementById("appView").classList.add("hidden");
    document.getElementById("adminView")?.classList.add("hidden");
}

function showApp() {
    document.getElementById("loginView").classList.add("hidden");
    document.getElementById("adminView")?.classList.add("hidden");
    document.getElementById("appView").classList.remove("hidden");
}

function showAdmin() {
    document.getElementById("loginView").classList.add("hidden");
    document.getElementById("appView").classList.add("hidden");
    document.getElementById("adminView")?.classList.remove("hidden");
}

async function loadDepartment() {
    const response = await fetch("/api/department/current");
    if (!response.ok) {
        showLogin();
        return;
    }

    currentDepartment = await response.json();
    const municipality = currentDepartment.municipality;

    document.getElementById("departmentName").textContent = currentDepartment.name;
    document.getElementById("municipalityInfo").textContent = `${municipality.name} · ${municipality.district} · ${municipality.state}`;
    document.getElementById("listHint").textContent = `Nur Listen und Häuser von ${currentDepartment.name} werden geladen.`;
}

async function loadLists(preferredListName = null) {
    try {
        const response = await fetch("/api/houses/lists");
        if (!response.ok) throw new Error("Listen konnten nicht geladen werden");

        const lists = await response.json();
        const listSelect = document.getElementById("listSelect");
        const previousList = preferredListName || currentListName;
        listSelect.innerHTML = "";

        addOption(listSelect, "", "Bitte Sammelliste auswählen");
        lists.forEach(listName => addOption(listSelect, listName, listName));

        if (previousList && lists.some(listName => listName.toLowerCase() === previousList.toLowerCase())) {
            currentListName = previousList;
            listSelect.value = previousList;
        } else {
            currentListName = "";
            listSelect.value = "";
            updateEmptyStats();
        }
        updateActiveListTitle();
    } catch (error) {
        console.error(error);
        showToast("Listen konnten nicht geladen werden");
    }
}

function addOption(select, value, label) {
    const option = document.createElement("option");
    option.value = value;
    option.textContent = label;
    select.appendChild(option);
}

function openMenu() {
    document.getElementById("sideMenu").classList.remove("hidden");
    document.getElementById("menuButton").setAttribute("aria-expanded", "true");
}

function closeMenu() {
    document.getElementById("sideMenu").classList.add("hidden");
    document.getElementById("menuButton").setAttribute("aria-expanded", "false");
}

function openCreateListModal() {
    document.getElementById("createListModal").classList.remove("hidden");
    setTimeout(() => document.getElementById("newListName").focus(), 80);
}

function closeCreateListModal() {
    document.getElementById("createListModal").classList.add("hidden");
}


function openDuplicateListModal() {
    if (!currentListName) {
        showToast("Bitte zuerst eine Liste auswählen");
        return;
    }
    const target = document.getElementById("duplicateTargetName");
    const nextYearName = suggestNextYearListName(currentListName);
    target.value = nextYearName;
    document.getElementById("duplicateListModal").classList.remove("hidden");
    setTimeout(() => target.focus(), 80);
}

function closeDuplicateListModal() {
    document.getElementById("duplicateListModal")?.classList.add("hidden");
}

function suggestNextYearListName(name) {
    const currentYear = new Date().getFullYear();
    const nextYear = currentYear + 1;
    const match = String(name || "").match(/(20\d{2})/);
    if (match) {
        return name.replace(match[1], String(Number(match[1]) + 1));
    }
    return `Florianisammlung ${nextYear}`;
}

async function duplicateCurrentList() {
    if (!currentListName) {
        showToast("Bitte zuerst eine Liste auswählen");
        return;
    }
    const button = document.getElementById("duplicateListButton");
    const targetName = document.getElementById("duplicateTargetName").value.trim();
    const resetValues = document.getElementById("duplicateResetValues").checked;

    if (!targetName) {
        showToast("Bitte einen Namen für die neue Liste eingeben");
        return;
    }

    button.disabled = true;
    button.textContent = "Dupliziere ...";
    try {
        const response = await fetch("/api/houses/lists/duplicate", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({sourceListName: currentListName, targetListName: targetName, resetValues})
        });
        const body = await safeJson(response);
        if (!response.ok) {
            showToast(body.message || "Liste konnte nicht dupliziert werden");
            return;
        }
        closeDuplicateListModal();
        await loadLists(targetName);
        currentListName = targetName;
        document.getElementById("listSelect").value = targetName;
        await loadHouses({showLoading: true, loadingText: "Neue Liste wird geladen ..."});
        openListOverlay("LIST");
        showToast(`${body.copied} Häuser übernommen`);
    } catch (error) {
        console.error(error);
        showToast("Fehler beim Duplizieren");
    } finally {
        button.disabled = false;
        button.textContent = "Duplizieren";
    }
}

function openPasswordModal() {
    document.getElementById("currentPasswordInput").value = "";
    document.getElementById("newPasswordInput").value = "";
    document.getElementById("passwordMessage").textContent = "";
    document.getElementById("passwordModal").classList.remove("hidden");
    setTimeout(() => document.getElementById("currentPasswordInput").focus(), 80);
}

function closePasswordModal() {
    document.getElementById("passwordModal")?.classList.add("hidden");
}

async function changePassword() {
    const currentPassword = document.getElementById("currentPasswordInput").value;
    const newPassword = document.getElementById("newPasswordInput").value;
    const message = document.getElementById("passwordMessage");
    const button = document.getElementById("savePasswordButton");

    message.textContent = "";
    if (!currentPassword || !newPassword) {
        message.textContent = "Bitte beide Felder ausfüllen.";
        return;
    }
    if (newPassword.length < 8) {
        message.textContent = "Das neue Passwort muss mindestens 8 Zeichen haben.";
        return;
    }

    button.disabled = true;
    button.textContent = "Speichere ...";
    try {
        const response = await fetch("/api/auth/change-password", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({currentPassword, newPassword})
        });
        const body = await safeJson(response);
        if (!response.ok) {
            message.textContent = body.message || "Passwort konnte nicht geändert werden.";
            return;
        }
        closePasswordModal();
        showToast("Passwort wurde geändert");
    } catch (error) {
        console.error(error);
        message.textContent = "Server nicht erreichbar.";
    } finally {
        button.disabled = false;
        button.textContent = "Speichern";
    }
}

async function createNewList() {
    const input = document.getElementById("newListName");
    const button = document.getElementById("createListButton");
    const listName = input.value.trim();

    if (!listName) {
        showToast("Bitte einen Listennamen eingeben");
        return;
    }

    button.disabled = true;
    button.textContent = "Erstelle ...";

    currentListName = listName;
    const listSelect = document.getElementById("listSelect");
    const exists = Array.from(listSelect.options)
        .some(option => option.value.toLowerCase() === listName.toLowerCase());
    if (!exists) addOption(listSelect, listName, listName);
    listSelect.value = listName;

    input.value = "";
    currentHouses = [];
    clearHouseMarkers();
    updateEmptyStats();
    updateActiveListTitle();
    closeCreateListModal();

    setLoading(true, `Häuser für ${currentDepartment?.municipality?.name || "die Gemeinde"} werden geladen ...`);
    showToast(`Liste "${listName}" wurde erstellt`);

    try {
        await importMunicipalityOsmAddresses({askBeforeImport: false});
        await loadLists();
        currentListName = listName;
        document.getElementById("listSelect").value = listName;
        await loadHouses({showLoading: false});
        openListOverlay("LIST");
    } finally {
        setLoading(false);
        button.disabled = false;
        button.textContent = "Erstellen";
    }
}

function updateActiveListTitle() {
    const title = document.getElementById("activeListTitle");
    const hint = document.getElementById("listHint");

    if (!currentListName) {
        title.textContent = "Keine Liste ausgewählt";
        hint.textContent = "Erstelle im Menü eine neue Liste oder wähle eine vorhandene Sammelliste aus.";
        return;
    }

    title.textContent = currentListName;
    const count = currentHouses.length;
    hint.textContent = count === 0
        ? "Die Liste ist ausgewählt. Falls noch keine Häuser geladen sind, nutze im Menü „Häuser neu laden / ergänzen“."
        : `${count} Häuser in dieser Liste geladen.`;
}

function setLoading(active, text = "Häuser laden ...") {
    isLoadingHouses = active;
    const banner = document.getElementById("loadingBanner");
    const textEl = document.getElementById("loadingText");
    if (textEl) textEl.textContent = text;
    banner.classList.toggle("hidden", !active);
}

async function loadHouses(options = {}) {
    const {showLoading = false, loadingText = "Häuser laden ..."} = options;

    if (!currentListName) {
        currentHouses = [];
        clearHouseMarkers();
        updateEmptyStats();
        updateActiveListTitle();
        renderHouseList();
        return;
    }

    if (showLoading) setLoading(true, loadingText);

    try {
        const response = await fetch("/api/houses?listName=" + encodeURIComponent(currentListName));
        if (!response.ok) throw new Error("Häuser konnten nicht geladen werden");

        currentHouses = await response.json();
        await loadStats();
        updateActiveListTitle();
        renderHouseList();
        refreshMapMarkers();
    } catch (error) {
        console.error(error);
        showToast("Fehler beim Laden der Häuser");
    } finally {
        if (showLoading) setLoading(false);
    }
}

async function loadStats() {
    if (!currentListName) {
        updateEmptyStats();
        return;
    }

    try {
        const response = await fetch("/api/houses/stats?listName=" + encodeURIComponent(currentListName));
        if (!response.ok) throw new Error("Statistik konnte nicht geladen werden");

        const stats = await response.json();
        document.getElementById("statMoney").textContent = formatEuro(stats.donationSum ?? 0);
        document.getElementById("statProgress").textContent = `${stats.progressPercent ?? 0} %`;
        document.getElementById("statOpen").textContent = stats.open ?? 0;
        document.getElementById("statDone").textContent = stats.done ?? 0;
    } catch (error) {
        console.error(error);
        showToast("Statistik konnte nicht geladen werden");
    }
}

function updateEmptyStats() {
    document.getElementById("statMoney").textContent = formatEuro(0);
    document.getElementById("statProgress").textContent = "0 %";
    document.getElementById("statOpen").textContent = "0";
    document.getElementById("statDone").textContent = "0";
}

function openListOverlay(mode = "LIST") {
    document.getElementById("listOverlay").classList.remove("hidden");
    setOverlayMode(mode);
}

function closeListOverlay() {
    document.getElementById("listOverlay").classList.add("hidden");
}

function setOverlayMode(mode) {
    overlayMode = mode;
    document.getElementById("listViewButton").classList.toggle("active", mode === "LIST");
    document.getElementById("pdfViewButton").classList.toggle("active", mode === "PDF");
    document.getElementById("printPdfButton").classList.toggle("hidden", mode !== "PDF");
    renderHouseList();
}

function renderHouseList() {
    const container = document.getElementById("houseList");
    if (!container) return;

    const filteredHouses = getFilteredHouses();
    const isPdf = overlayMode === "PDF";

    container.innerHTML = "";
    document.getElementById("overlayModeLabel").textContent = isPdf ? "PDF-Format" : "Hausliste";
    document.getElementById("overlayTitle").textContent = currentListName || "Keine Liste ausgewählt";

    if (!currentListName) {
        document.getElementById("houseCountText").textContent = "Wähle zuerst eine Sammelliste aus.";
        container.innerHTML = `<div class="empty-state">Bitte oben eine Sammelliste auswählen oder im Menü eine neue Liste erstellen.</div>`;
        return;
    }

    if (isLoadingHouses) {
        document.getElementById("houseCountText").textContent = "Häuser werden geladen ...";
        container.innerHTML = `<div class="empty-state loading-state"><div class="spinner"></div><strong>Häuser laden ...</strong><br>Bitte kurz warten.</div>`;
        return;
    }

    document.getElementById("houseCountText").textContent = `${filteredHouses.length} von ${currentHouses.length} Häusern angezeigt`;

    if (currentHouses.length === 0) {
        container.innerHTML = `
            <div class="empty-state">
                <strong>Noch keine Häuser in dieser Liste.</strong><br>
                Öffne das Menü und klicke auf „Häuser neu laden / ergänzen“.
            </div>
        `;
        return;
    }

    if (filteredHouses.length === 0) {
        container.innerHTML = `<div class="empty-state">Keine Häuser passen zu diesem Filter.</div>`;
        return;
    }

    container.appendChild(isPdf ? createPdfTable(filteredHouses) : createOverviewList(filteredHouses));
}

function createOverviewList(houses) {
    const wrapper = document.createElement("div");
    wrapper.className = "overview-list";

    wrapper.innerHTML = `
        <div class="overview-row overview-head">
            <span>Adresse</span>
            <span>Name</span>
            <span>Status</span>
            <span>Spende</span>
            <span>Notiz</span>
            <span>Aktion</span>
        </div>
    `;

    houses.forEach(house => {
        const row = document.createElement("article");
        row.className = `overview-row ${getStatusClass(house.status)}`;
        row.id = `house-card-${house.id}`;
        row.innerHTML = `
            <button class="overview-address row-map-button" type="button" title="Auf Karte anzeigen">
                <strong>${escapeHtml(house.address)}</strong>
                <small>${escapeHtml(house.district || currentDepartment?.municipality?.name || "")}</small>
            </button>
            <div class="overview-resident">${escapeHtml(house.residentName || "—")}</div>
            <div><span class="status-pill ${getStatusClass(house.status)}">${STATUS_LABELS[house.status] || "Offen"}</span></div>
            <div class="overview-money">${house.donationAmount ? formatEuro(house.donationAmount) : "—"}</div>
            <div class="overview-note">${escapeHtml(house.note || "—")}</div>
            <div class="overview-actions">
                <button class="light-button small" type="button" data-action="edit">Bearbeiten</button>
            </div>
        `;
        row.querySelector(".row-map-button").addEventListener("click", () => focusHouseOnMap(house));
        row.querySelector("[data-action='edit']").addEventListener("click", () => openEditHouseModal(house));
        wrapper.appendChild(row);
    });

    return wrapper;
}

function createPdfTable(houses) {
    const wrapper = document.createElement("div");
    wrapper.className = "pdf-table-wrap";

    const rows = houses.map(house => `
        <tr>
            <td>${escapeHtml(house.address)}</td>
            <td>${escapeHtml(house.residentName || "")}</td>
            <td>${STATUS_LABELS[house.status] || "Offen"}</td>
            <td>${house.donationAmount ? formatEuro(house.donationAmount) : ""}</td>
            <td>${escapeHtml(house.note || "")}</td>
        </tr>
    `).join("");

    wrapper.innerHTML = `
        <div class="pdf-title">
            <h1>${escapeHtml(currentListName)}</h1>
            <p>${escapeHtml(currentDepartment?.name || "")} · ${escapeHtml(currentDepartment?.municipality?.name || "")}</p>
            <p class="pdf-filter-note">Gefilterte Ansicht: ${houses.length} von ${currentHouses.length} Häusern</p>
            <p class="pdf-filter-note">${escapeHtml(describeActiveFilters())}</p>
        </div>
        <table class="pdf-table">
            <thead>
                <tr>
                    <th>Adresse</th>
                    <th>Name</th>
                    <th>Status</th>
                    <th>Spende</th>
                    <th>Notiz</th>
                </tr>
            </thead>
            <tbody>${rows}</tbody>
        </table>
    `;

    return wrapper;
}

function describeActiveFilters() {
    const parts = [];
    const rawQuery = document.getElementById("searchInput")?.value?.trim() || "";
    const statusFilter = document.getElementById("statusFilter")?.value || "ALL";
    const moneyFilter = document.getElementById("moneyFilter")?.value || "ALL";
    const moneyValue = document.getElementById("moneyValue")?.value || "";
    const sort = document.getElementById("sortSelect")?.value || "ADDRESS_ASC";

    if (rawQuery) parts.push(`Suche: ${rawQuery}`);
    if (statusFilter !== "ALL") parts.push(`Status: ${STATUS_LABELS[statusFilter] || statusFilter}`);

    if (moneyFilter === "WITH_MONEY") parts.push("Geld: nur mit Betrag");
    if (moneyFilter === "WITHOUT_MONEY") parts.push("Geld: nur ohne Betrag");
    if (moneyFilter === "MIN") parts.push(`Geld: mindestens ${moneyValue || 0} €`);
    if (moneyFilter === "MAX") parts.push(`Geld: höchstens ${moneyValue || 0} €`);

    const sortLabels = {
        ADDRESS_ASC: "Adresse A-Z",
        STATUS_ASC: "Status",
        MONEY_DESC: "Geld absteigend",
        MONEY_ASC: "Geld aufsteigend",
        UPDATED_DESC: "zuletzt bearbeitet"
    };
    if (sort !== "ADDRESS_ASC") parts.push(`Sortierung: ${sortLabels[sort] || sort}`);

    return parts.length ? `Aktive Filter: ${parts.join(" · ")}` : "Aktive Filter: keine";
}


function getFilterQueryParams() {
    return {
        search: document.getElementById("searchInput")?.value?.trim() || "",
        statusFilter: document.getElementById("statusFilter")?.value || "ALL",
        moneyFilter: document.getElementById("moneyFilter")?.value || "ALL",
        moneyValue: document.getElementById("moneyValue")?.value || "",
        sort: document.getElementById("sortSelect")?.value || "ADDRESS_ASC"
    };
}

function openPrintablePage() {
    if (!currentListName) {
        showToast("Bitte zuerst eine Liste auswählen");
        return;
    }
    const filteredHouses = getFilteredHouses();
    const printPayload = {
        listName: currentListName,
        departmentName: currentDepartment?.name || "",
        municipalityName: currentDepartment?.municipality?.name || "",
        filters: describeActiveFilters(),
        totalCount: currentHouses.length,
        shownCount: filteredHouses.length,
        generatedAt: new Date().toLocaleString("de-AT"),
        houses: filteredHouses
    };
    sessionStorage.setItem("florianiPrintPayload", JSON.stringify(printPayload));
    localStorage.setItem("florianiPrintPayload", JSON.stringify(printPayload));
    window.open("/print.html", "_blank");
}

function exportExcel() {
    if (!currentListName) {
        showToast("Bitte zuerst eine Liste auswählen");
        return;
    }
    const params = new URLSearchParams({listName: currentListName});
    const filters = getFilterQueryParams();
    Object.entries(filters).forEach(([key, value]) => {
        if (value !== null && value !== undefined && String(value).length > 0) params.set(key, value);
    });
    window.location.href = "/api/houses/export/excel?" + params.toString();
}

function getFilteredHouses() {
    const query = normalizeText(document.getElementById("searchInput")?.value || "");
    const statusFilter = document.getElementById("statusFilter")?.value || "ALL";
    const moneyFilter = document.getElementById("moneyFilter")?.value || "ALL";
    const moneyValue = Number(document.getElementById("moneyValue")?.value || 0);
    const sort = document.getElementById("sortSelect")?.value || "ADDRESS_ASC";

    let houses = currentHouses.filter(house => {
        const haystack = normalizeText([house.address, house.street, house.houseNumber, house.residentName, house.note, house.updatedBy]
            .filter(Boolean)
            .join(" "));

        if (query && !haystack.includes(query)) return false;
        if (statusFilter !== "ALL" && house.status !== statusFilter) return false;

        const amount = Number(house.donationAmount || 0);
        if (moneyFilter === "WITH_MONEY" && amount <= 0) return false;
        if (moneyFilter === "WITHOUT_MONEY" && amount > 0) return false;
        if (moneyFilter === "MIN" && amount < moneyValue) return false;
        if (moneyFilter === "MAX" && amount > moneyValue) return false;

        return true;
    });

    houses.sort((a, b) => {
        if (sort === "STATUS_ASC") return (STATUS_ORDER[a.status] || 99) - (STATUS_ORDER[b.status] || 99) || compareAddress(a, b);
        if (sort === "MONEY_DESC") return Number(b.donationAmount || 0) - Number(a.donationAmount || 0) || compareAddress(a, b);
        if (sort === "MONEY_ASC") return Number(a.donationAmount || 0) - Number(b.donationAmount || 0) || compareAddress(a, b);
        if (sort === "UPDATED_DESC") return new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0) || compareAddress(a, b);
        return compareAddress(a, b);
    });

    return houses;
}

function normalizeText(value) {
    return String(value || "")
        .toLowerCase()
        .normalize("NFD")
        .replace(/[\u0300-\u036f]/g, "")
        .replace(/ß/g, "ss")
        .replace(/straße/g, "strasse")
        .replace(/str\./g, "strasse")
        .replace(/\s+/g, " ")
        .trim();
}

function compareAddress(a, b) {
    const streetCompare = String(a.street || a.address || "").localeCompare(String(b.street || b.address || ""), "de", {
        numeric: true,
        sensitivity: "base"
    });
    if (streetCompare !== 0) return streetCompare;
    return String(a.houseNumber || "").localeCompare(String(b.houseNumber || ""), "de", {
        numeric: true,
        sensitivity: "base"
    });
}

function openEditHouseModal(house) {
    houseBeingEdited = house;
    document.getElementById("editHouseTitle").textContent = house.address || "Haus";
    document.getElementById("editResidentName").value = house.residentName || "";
    document.getElementById("editStatus").value = house.status || "OFFEN";
    document.getElementById("editDonation").value = house.donationAmount ?? "";
    document.getElementById("editNote").value = house.note || "";
    document.getElementById("editHouseModal").classList.remove("hidden");
    setTimeout(() => document.getElementById("editDonation").focus(), 80);
}

function closeEditHouseModal() {
    document.getElementById("editHouseModal").classList.add("hidden");
    houseBeingEdited = null;
}

async function saveEditHouseModal() {
    if (!houseBeingEdited) return;
    const house = houseBeingEdited;
    const donationInput = document.getElementById("editDonation").value;

    await saveHousePayload(house.id, {
        status: document.getElementById("editStatus").value,
        donationAmount: donationInput === "" ? null : Number(donationInput),
        note: document.getElementById("editNote").value,
        residentName: document.getElementById("editResidentName").value.trim() || null,
        latitude: house.latitude,
        longitude: house.longitude,
        street: house.street,
        houseNumber: house.houseNumber,
        district: house.district
    });
    closeEditHouseModal();
}

async function saveHousePayload(id, payload) {
    try {
        const response = await fetch(`/api/houses/${id}`, {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });

        if (!response.ok) throw new Error("Speichern fehlgeschlagen");

        showToast("Gespeichert");
        await loadHouses();
    } catch (error) {
        console.error(error);
        showToast("Fehler beim Speichern");
    }
}

async function importMunicipalityOsmAddresses(options = {}) {
    const {askBeforeImport = true} = options;

    if (!currentListName) {
        showToast("Bitte zuerst eine Liste auswählen oder erstellen");
        return;
    }

    if (!currentDepartment?.municipality?.name) {
        showToast("Keine Gemeinde bei dieser Feuerwehr hinterlegt");
        return;
    }

    const municipalityName = currentDepartment.municipality.name;
    if (askBeforeImport) {
        const confirmed = confirm(`Häuser für ${municipalityName} in Liste "${currentListName}" laden? Vorhandene Häuser werden nicht doppelt angelegt.`);
        if (!confirmed) return;
    }

    const overpassQuery = `
        [out:json][timeout:120];
        area["boundary"="administrative"]["name"="${municipalityName}"]["admin_level"~"^(6|8)$"]->.searchArea;
        (
          node["addr:housenumber"](area.searchArea);
          way["addr:housenumber"](area.searchArea);
          relation["addr:housenumber"](area.searchArea);
        );
        out center tags;
    `;

    setLoading(true, `Häuser für ${municipalityName} werden geladen ...`);
    renderHouseList();

    try {
        const response = await fetch("https://overpass-api.de/api/interpreter", {
            method: "POST",
            headers: {"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"},
            body: "data=" + encodeURIComponent(overpassQuery)
        });

        if (!response.ok) throw new Error("OpenStreetMap-Abfrage fehlgeschlagen");

        const data = await response.json();
        const importedHouses = convertOsmElementsToHouses(data.elements);

        if (importedHouses.length === 0) {
            showToast("Keine Häuser gefunden. Eventuell später erneut probieren.");
            return;
        }

        setLoading(true, `${importedHouses.length} Häuser gefunden. Speichern läuft ...`);
        const saveResponse = await fetch("/api/houses/import?listName=" + encodeURIComponent(currentListName), {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(importedHouses)
        });

        if (!saveResponse.ok) throw new Error("Import speichern fehlgeschlagen");

        const savedHouses = await saveResponse.json();
        showToast(`${savedHouses.length} Häuser geladen`);
        await loadHouses({showLoading: false});
    } catch (error) {
        console.error(error);
        showToast("Fehler beim Laden aus OpenStreetMap");
    } finally {
        setLoading(false);
        renderHouseList();
    }
}

function convertOsmElementsToHouses(elements) {
    const houses = [];
    const seen = new Set();
    const municipalityName = currentDepartment.municipality.name;

    elements.forEach(element => {
        if (!element.tags) return;

        const houseNumber = element.tags["addr:housenumber"];
        const street = element.tags["addr:street"] || element.tags["addr:place"] || municipalityName;
        if (!houseNumber || !street) return;

        let latitude = element.lat;
        let longitude = element.lon;
        if ((!latitude || !longitude) && element.center) {
            latitude = element.center.lat;
            longitude = element.center.lon;
        }
        if (!latitude || !longitude) return;

        const address = `${street} ${houseNumber}`;
        const key = normalizeText(address);
        if (seen.has(key)) return;
        seen.add(key);

        houses.push({
            listName: currentListName,
            address,
            street,
            houseNumber,
            district: municipalityName,
            latitude,
            longitude,
            status: "OFFEN",
            donationAmount: null,
            residentName: null,
            note: null
        });
    });

    houses.sort((a, b) => a.address.localeCompare(b.address, "de", {numeric: true, sensitivity: "base"}));
    return houses;
}

function initMapIfNeeded() {
    if (mapInitialized) return;

    const lat = currentDepartment?.municipality?.latitude || currentUser?.latitude || 46.9278;
    const lon = currentDepartment?.municipality?.longitude || currentUser?.longitude || 15.4534;
    const municipalityName = currentDepartment?.municipality?.name || "Wundschuh";

    map = L.map("map", {zoomControl: true}).setView([lat, lon], 15);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
        maxZoom: 20,
        attribution: "&copy; OpenStreetMap contributors"
    }).addTo(map);

    addLocateControl();
    addLegend();
    mapInitialized = true;
    loadMunicipalityBoundary(municipalityName);
}

function addLocateControl() {
    if (locateControlAdded) return;
    const locateControl = L.control({position: "topleft"});
    locateControl.onAdd = function () {
        const button = L.DomUtil.create("button", "leaflet-control locate-map-button");
        button.type = "button";
        button.title = "Mein Standort";
        button.innerHTML = "⌖";
        L.DomEvent.disableClickPropagation(button);
        L.DomEvent.on(button, "click", locateUser);
        return button;
    };
    locateControl.addTo(map);
    locateControlAdded = true;
}

async function loadMunicipalityBoundary(municipalityName) {
    const query = `
        [out:json][timeout:25];
        relation["boundary"="administrative"]["name"="${municipalityName}"]["admin_level"="8"];
        out geom;
    `;

    try {
        const response = await fetch("https://overpass-api.de/api/interpreter", {
            method: "POST",
            headers: {"Content-Type": "application/x-www-form-urlencoded; charset=UTF-8"},
            body: "data=" + encodeURIComponent(query)
        });

        if (!response.ok) return;
        const data = await response.json();
        const relation = data.elements.find(element => element.type === "relation");
        if (!relation?.members) return;

        const boundaryLines = relation.members
            .filter(member => member.geometry?.length)
            .map(member => member.geometry.map(point => [point.lat, point.lon]));

        if (boundaryLines.length === 0) return;
        if (municipalityBoundaryLayer) municipalityBoundaryLayer.remove();

        municipalityBoundaryLayer = L.polyline(boundaryLines, {
            color: "#b40012",
            weight: 3,
            opacity: 0.9
        }).addTo(map);

        const bounds = municipalityBoundaryLayer.getBounds();
        if (bounds.isValid()) {
            map.fitBounds(bounds, {padding: [25, 25], maxZoom: 15});
        }
    } catch (error) {
        console.log("Gemeindegrenze konnte nicht geladen werden", error);
    }
}

function refreshMapMarkers() {
    if (!mapInitialized) return;
    clearHouseMarkers();
    currentHouses.forEach(house => addHouseMarker(house));
}

function clearHouseMarkers() {
    houseMarkers.forEach(marker => marker.remove());
    houseMarkers = [];
}

function addHouseMarker(house) {
    if (!house.latitude || !house.longitude) return;

    const marker = L.marker([house.latitude, house.longitude], {
        icon: createStatusDotIcon(house.status)
    });

    marker.bindPopup(`
        <div class="house-popup">
            <h3>${escapeHtml(house.address)}</h3>
            <p><strong>Name:</strong> ${escapeHtml(house.residentName || "-")}</p>
            <p><strong>Status:</strong> ${STATUS_LABELS[house.status] || "Offen"}</p>
            <p><strong>Spende:</strong> ${house.donationAmount ? formatEuro(house.donationAmount) : "-"}</p>
            <button onclick="openEditHouseById(${house.id})">Bearbeiten</button>
        </div>
    `);

    marker.addTo(map);
    houseMarkers.push(marker);
}

function openEditHouseById(id) {
    const house = currentHouses.find(item => Number(item.id) === Number(id));
    if (house) openEditHouseModal(house);
}
window.openEditHouseById = openEditHouseById;

function focusHouseOnMap(house) {
    if (!house.latitude || !house.longitude) {
        showToast("Für dieses Haus gibt es keine Kartenposition");
        return;
    }
    initMapIfNeeded();
    closeListOverlay();
    setTimeout(() => {
        map.invalidateSize();
        map.setView([house.latitude, house.longitude], 19);
    }, 150);
}

function createStatusDotIcon(status) {
    return L.divIcon({
        className: "",
        html: `<div class="status-dot-marker ${getStatusClass(status)}"></div>`,
        iconSize: [16, 16],
        iconAnchor: [8, 8],
        popupAnchor: [0, -10]
    });
}

function addLegend() {
    const legend = L.control({position: "bottomright"});
    legend.onAdd = function () {
        const div = L.DomUtil.create("div", "legend");
        div.innerHTML = `
            <strong>Status</strong><br>
            <span class="legend-dot offen"></span> Offen<br>
            <span class="legend-dot spaeter"></span> Später<br>
            <span class="legend-dot erledigt"></span> Erledigt<br>
            <span class="legend-dot ueberspringen"></span> Überspringen
        `;
        return div;
    };
    legend.addTo(map);
}

function locateUser() {
    if (!navigator.geolocation) {
        showToast("Standort wird nicht unterstützt");
        return;
    }

    initMapIfNeeded();

    navigator.geolocation.getCurrentPosition(position => {
        const lat = position.coords.latitude;
        const lon = position.coords.longitude;
        const accuracy = position.coords.accuracy;

        if (userLocationMarker) userLocationMarker.remove();
        if (userAccuracyCircle) userAccuracyCircle.remove();

        userLocationMarker = L.marker([lat, lon]).addTo(map).bindPopup("Du bist ungefähr hier");
        userAccuracyCircle = L.circle([lat, lon], {radius: accuracy}).addTo(map);
        map.setView([lat, lon], 18);
    }, () => {
        showToast("Standort konnte nicht ermittelt werden");
    }, {
        enableHighAccuracy: true,
        timeout: 10000,
        maximumAge: 10000
    });
}

function getStatusClass(status) {
    switch (status) {
        case "OFFEN": return "offen";
        case "SPAETER_NOCHMAL": return "spaeter";
        case "ERLEDIGT": return "erledigt";
        case "UEBERSPRINGEN": return "ueberspringen";
        default: return "offen";
    }
}

function formatEuro(value) {
    return new Intl.NumberFormat("de-AT", {
        style: "currency",
        currency: "EUR"
    }).format(Number(value || 0));
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

function showToast(message) {
    const toast = document.getElementById("toast");
    toast.textContent = message;
    toast.classList.remove("hidden");
    clearTimeout(showToast.timeout);
    showToast.timeout = setTimeout(() => toast.classList.add("hidden"), 2600);
}

async function safeJson(response) {
    try {
        return await response.json();
    } catch {
        return {};
    }
}

function debounce(fn, delay) {
    let timer;
    return (...args) => {
        clearTimeout(timer);
        timer = setTimeout(() => fn(...args), delay);
    };
}

async function loadAdminDashboard() {
    await Promise.all([loadAdminOverview(), loadAdminDepartments()]);
}

async function loadAdminOverview() {
    try {
        const response = await fetch("/api/admin/overview");
        if (!response.ok) throw new Error("Admin-Übersicht konnte nicht geladen werden");
        const data = await response.json();
        document.getElementById("adminStatDepartments").textContent = data.fireDepartments ?? 0;
        document.getElementById("adminStatActive").textContent = data.active ?? 0;
        document.getElementById("adminStatBlocked").textContent = data.blockedOrExpired ?? 0;
        document.getElementById("adminStatHouses").textContent = data.houses ?? 0;
    } catch (error) {
        console.error(error);
        showToast("Admin-Übersicht konnte nicht geladen werden");
    }
}

async function loadAdminDepartments() {
    const container = document.getElementById("adminDepartmentList");
    if (!container) return;
    const search = document.getElementById("adminSearch")?.value?.trim() || "";
    container.innerHTML = `<div class="empty-state">Feuerwehren werden geladen ...</div>`;

    try {
        const response = await fetch("/api/admin/fire-departments" + (search ? `?search=${encodeURIComponent(search)}` : ""));
        if (!response.ok) throw new Error("Feuerwehren konnten nicht geladen werden");
        const departments = await response.json();
        if (departments.length === 0) {
            container.innerHTML = `<div class="empty-state">Keine Feuerwehren gefunden.</div>`;
            return;
        }
        container.innerHTML = "";
        departments.forEach(department => container.appendChild(createAdminDepartmentCard(department)));
    } catch (error) {
        console.error(error);
        container.innerHTML = `<div class="empty-state">Fehler beim Laden der Feuerwehren.</div>`;
    }
}

function createAdminDepartmentCard(department) {
    const card = document.createElement("article");
    card.className = `admin-department-card ${department.canUse ? "ok" : "blocked"}`;
    const municipality = department.municipality || {};
    const validUntil = department.subscriptionValidUntil || "ohne Ablaufdatum";
    card.innerHTML = `
        <div class="admin-department-main">
            <div>
                <p class="eyebrow">${escapeHtml(String(department.subscriptionStatus || "TEST"))}</p>
                <h3>${escapeHtml(department.name)}</h3>
                <p>${escapeHtml(municipality.name || "-")} · ${escapeHtml(municipality.district || "-")} · ${escapeHtml(municipality.state || "-")}</p>
                <p class="muted">Kontakt: ${escapeHtml(department.contactName || "-")} ${department.contactEmail ? " · " + escapeHtml(department.contactEmail) : ""}</p>
            </div>
            <div class="admin-badges">
                <span class="status-pill ${department.canUse ? "erledigt" : "offen"}">${department.canUse ? "aktiv" : "gesperrt"}</span>
                <span class="admin-mini-badge">gültig bis: ${escapeHtml(validUntil)}</span>
                <span class="admin-mini-badge">User: ${department.userCount ?? 0}</span>
                <span class="admin-mini-badge">Listen: ${department.listCount ?? 0}</span>
            </div>
        </div>
        <details class="admin-details">
            <summary>Bearbeiten / Benutzer anlegen</summary>
            <div class="admin-form-grid compact">
                <label>Name<input data-field="name" value="${escapeAttribute(department.name)}"></label>
                <label>Status
                    <select data-field="subscriptionStatus">
                        ${["TEST","ACTIVE","EXPIRED","BLOCKED"].map(status => `<option value="${status}" ${status === department.subscriptionStatus ? "selected" : ""}>${status}</option>`).join("")}
                    </select>
                </label>
                <label>Aktiv
                    <select data-field="active">
                        <option value="true" ${department.active ? "selected" : ""}>Ja</option>
                        <option value="false" ${!department.active ? "selected" : ""}>Nein</option>
                    </select>
                </label>
                <label>Gültig bis<input data-field="subscriptionValidUntil" type="date" value="${escapeAttribute(department.subscriptionValidUntil || "")}"></label>
                <label>Standard-Liste<input data-field="defaultListName" value="${escapeAttribute(department.defaultListName || "")}"></label>
                <label>Ansprechpartner<input data-field="contactName" value="${escapeAttribute(department.contactName || "")}"></label>
                <label>E-Mail<input data-field="contactEmail" value="${escapeAttribute(department.contactEmail || "")}"></label>
                <label>Telefon<input data-field="contactPhone" value="${escapeAttribute(department.contactPhone || "")}"></label>
                <label class="wide">Interne Notiz<input data-field="internalNote" value="${escapeAttribute(department.internalNote || "")}"></label>
            </div>
            <div class="admin-actions">
                <button class="red-button small" data-action="save-department">Feuerwehr speichern</button>
                <button class="light-button small" data-action="load-users">Benutzer anzeigen</button>
            </div>
            <div class="admin-user-box hidden"></div>
        </details>
    `;

    card.querySelector("[data-action='save-department']").addEventListener("click", () => adminSaveDepartment(department.id, card));
    card.querySelector("[data-action='load-users']").addEventListener("click", () => adminLoadUsers(department.id, card));
    return card;
}

async function adminCreateDepartment() {
    const message = document.getElementById("adminCreateMessage");
    message.textContent = "";
    const payload = {
        fireDepartmentName: valueOf("adminFdName"),
        municipalityName: valueOf("adminMunicipality"),
        district: valueOf("adminDistrict"),
        state: valueOf("adminState") || "Steiermark",
        gkz: valueOf("adminGkz"),
        latitude: numberOrNull("adminLat"),
        longitude: numberOrNull("adminLon"),
        defaultListName: valueOf("adminDefaultList") || "Florianisammlung 2026",
        username: valueOf("adminUsername"),
        password: valueOf("adminPassword"),
        displayName: valueOf("adminFdName"),
        subscriptionStatus: valueOf("adminSubscriptionStatus") || "TEST",
        subscriptionValidUntil: valueOf("adminValidUntil"),
        contactName: valueOf("adminContactName"),
        contactEmail: valueOf("adminContactEmail"),
        internalNote: valueOf("adminInternalNote")
    };

    try {
        const response = await fetch("/api/admin/fire-departments", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });
        const body = await safeJson(response);
        if (!response.ok) {
            message.textContent = body.message || "Feuerwehr konnte nicht erstellt werden.";
            return;
        }
        message.textContent = `Erstellt: ${body.name}. Zugang: ${payload.username} / ${payload.password}`;
        ["adminFdName","adminMunicipality","adminDistrict","adminGkz","adminLat","adminLon","adminUsername","adminContactName","adminContactEmail","adminInternalNote"].forEach(id => document.getElementById(id).value = "");
        await loadAdminDashboard();
    } catch (error) {
        console.error(error);
        message.textContent = "Server nicht erreichbar.";
    }
}

async function adminSaveDepartment(id, card) {
    const payload = {};
    card.querySelectorAll("[data-field]").forEach(input => {
        const field = input.getAttribute("data-field");
        payload[field] = field === "active" ? input.value === "true" : input.value;
    });

    try {
        const response = await fetch(`/api/admin/fire-departments/${id}`, {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });
        if (!response.ok) throw new Error("Speichern fehlgeschlagen");
        showToast("Feuerwehr gespeichert");
        await loadAdminDashboard();
    } catch (error) {
        console.error(error);
        showToast("Fehler beim Speichern");
    }
}

async function adminLoadUsers(departmentId, card) {
    const box = card.querySelector(".admin-user-box");
    box.classList.remove("hidden");
    box.innerHTML = `<div class="empty-state">Benutzer werden geladen ...</div>`;

    try {
        const response = await fetch(`/api/admin/fire-departments/${departmentId}/users`);
        if (!response.ok) throw new Error("Benutzer konnten nicht geladen werden");
        const users = await response.json();
        box.innerHTML = `
            <h4>Benutzer</h4>
            <div class="admin-users-list">
                ${users.map(user => `<div><strong>${escapeHtml(user.username)}</strong><span>${escapeHtml(user.displayName)} · ${escapeHtml(String(user.role))} · ${user.active ? "aktiv" : "inaktiv"}</span></div>`).join("") || "<p>Keine Benutzer.</p>"}
            </div>
            <div class="admin-form-grid compact user-create-grid">
                <label>Benutzername<input data-new-user="username" placeholder="z.B. max.mustermann"></label>
                <label>Passwort<input data-new-user="password" value="floriani2026"></label>
                <label>Anzeigename<input data-new-user="displayName" placeholder="Name"></label>
                <label>Rolle
                    <select data-new-user="role">
                        <option value="COLLECTOR">Sammler</option>
                        <option value="ADMIN">Feuerwehr-Admin</option>
                        <option value="READ_ONLY">Nur Lesen</option>
                    </select>
                </label>
            </div>
            <button class="red-button small" data-action="create-user">Benutzer erstellen</button>
        `;
        box.querySelector("[data-action='create-user']").addEventListener("click", () => adminCreateUser(departmentId, card));
    } catch (error) {
        console.error(error);
        box.innerHTML = `<div class="empty-state">Benutzer konnten nicht geladen werden.</div>`;
    }
}

async function adminCreateUser(departmentId, card) {
    const box = card.querySelector(".admin-user-box");
    const payload = {};
    box.querySelectorAll("[data-new-user]").forEach(input => payload[input.getAttribute("data-new-user")] = input.value);

    try {
        const response = await fetch(`/api/admin/fire-departments/${departmentId}/users`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });
        const body = await safeJson(response);
        if (!response.ok) {
            showToast(body.message || "Benutzer konnte nicht erstellt werden");
            return;
        }
        showToast(`Benutzer ${body.username} erstellt`);
        await adminLoadUsers(departmentId, card);
        await loadAdminOverview();
    } catch (error) {
        console.error(error);
        showToast("Fehler beim Benutzer-Erstellen");
    }
}

function valueOf(id) {
    return document.getElementById(id)?.value?.trim() || "";
}

function numberOrNull(id) {
    const value = valueOf(id);
    return value === "" ? null : Number(value);
}

function escapeAttribute(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll('"', "&quot;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;");
}

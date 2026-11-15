// Presentation layer: browser behavior. [Part B - teammate]
// Talks to the Occupan API (/api/...). Text is always inserted with textContent, never as HTML.

const STATUSES = ["Available", "Occupied", "Reserved", "Cleaning", "Maintenance", "Unavailable"];
const $ = (selector) => document.querySelector(selector);

let token = "";
let me = null;
try { token = sessionStorage.getItem("occupan_token") || ""; } catch (_) { /* storage blocked */ }

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (key === "class") node.className = value;
    else if (key === "onclick") node.addEventListener("click", value);
    else node.setAttribute(key, value);
  }
  for (const child of children) node.append(child);
  return node;
}

async function api(path, { method = "GET", body } = {}) {
  const headers = { "Content-Type": "application/json" };
  if (token) headers["Authorization"] = "Bearer " + token;
  const response = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined });
  let data = {};
  try { data = await response.json(); } catch (_) { /* empty body */ }
  if (response.status === 401 && path !== "/api/login") { signOutLocally(); }
  if (!response.ok) throw new Error(data.detail || "Something went wrong.");
  return data;
}

function showNotice(message, bad = false) {
  const box = $("#notice");
  box.textContent = message;
  box.className = "notice" + (bad ? " bad" : "");
  box.hidden = false;
  clearTimeout(showNotice.timer);
  showNotice.timer = setTimeout(() => { box.hidden = true; }, 4000);
}

// ---------- sign in / out (FR-1) ----------
function signOutLocally() {
  token = ""; me = null;
  try { sessionStorage.removeItem("occupan_token"); } catch (_) {}
  $("#app-view").hidden = true;
  $("#userbox").hidden = true;
  $("#login-view").hidden = false;
  document.body.classList.remove("is-authed"); // styling hook: hides the top bar on the sign-in screen
  $("#rows").replaceChildren(); $("#summary").replaceChildren(); // don't leave the last user's rows in the page
}

function showApp() {
  $("#login-view").hidden = true;
  $("#app-view").hidden = false;
  $("#userbox").hidden = false;
  document.body.classList.add("is-authed");
  $("#whoami").textContent = `${me.name} (${me.role === "administrator" ? "Administrator" : "Clinical staff"})`;
  $("#add-resource").hidden = me.role !== "administrator";
  loadResources();
}

$("#login-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  const form = new FormData(event.target);
  $("#login-error").textContent = "";
  try {
    const result = await api("/api/login", { method: "POST", body: { username: form.get("username"), password: form.get("password") } });
    token = result.token; me = result.user;
    try { sessionStorage.setItem("occupan_token", token); } catch (_) {}
    event.target.reset();
    showApp();
  } catch (error) {
    $("#login-error").textContent = error.message;
  }
});

$("#logout").addEventListener("click", async () => {
  try { await api("/api/logout", { method: "POST" }); } catch (_) {}
  signOutLocally();
});

// ---------- dashboard (FR-2, FR-7) ----------
function currentFilters() {
  const form = new FormData($("#filters"));
  const params = new URLSearchParams();
  for (const [key, value] of form.entries()) if (String(value).trim()) params.set(key, String(value).trim());
  return params.toString();
}

async function loadResources() {
  try {
    const data = await api("/api/resources?" + currentFilters());
    renderSummary(data.status_counts);
    renderRows(data.items);
    $("#count").textContent = `${data.total} ${data.total === 1 ? "resource" : "resources"}`;
    $("#empty").hidden = data.items.length > 0;
  } catch (error) {
    showNotice(error.message, true);
  }
}

function renderSummary(counts) {
  const box = $("#summary");
  box.replaceChildren();
  for (const status of STATUSES) {
    box.append(el("div", { class: "chip c-" + status }, el("b", {}, String(counts[status] ?? 0)), el("span", { class: "chip-label" }, status)));
  }
}

function renderRows(items) {
  const body = $("#rows");
  body.replaceChildren();
  for (const item of items) {
    const actions = el("td", { class: "actions-cell" });
    if (item.status === "Available") actions.append(button("Assign", () => openAssign(item)));
    if (item.status === "Occupied" && item.assignment_id) actions.append(button("Release", () => openRelease(item)));
    if (item.status !== "Occupied") actions.append(button("Status", () => openStatus(item)));
    actions.append(button("History", () => openHistory(item)));
    if (me.role === "administrator") {
      actions.append(button("Edit", () => openEdit(item)));
      if (item.status !== "Occupied") actions.append(button("Retire", () => retire(item), "danger"));
    }
    body.append(el("tr", {},
      el("td", { "data-label": "ID" }, item.resource_id), el("td", { "data-label": "Type" }, item.type),
      el("td", { "data-label": "Location" }, item.location),
      el("td", { "data-label": "Status" }, el("span", { class: "badge s-" + item.status }, item.status)),
      el("td", { "data-label": "Assigned to" }, item.assigned_to || "—"), actions));
  }
}

function button(label, handler, extra = "") {
  return el("button", { class: ("small " + extra).trim(), onclick: handler }, label);
}

let filterTimer;
$("#filters").addEventListener("input", () => { clearTimeout(filterTimer); filterTimer = setTimeout(loadResources, 200); });
$("#filters").addEventListener("change", loadResources);
$("#clear-filters").addEventListener("click", () => { $("#filters").reset(); loadResources(); });

// ---------- dialogs: wire a form to an API call ----------
function wireDialog(dialogId, formId, errorId, submitFn) {
  const dialog = $(dialogId), form = $(formId), error = $(errorId);
  form.addEventListener("submit", async (event) => {
    if (event.submitter && event.submitter.value === "cancel") return; // lets the dialog close
    event.preventDefault();
    error.textContent = "";
    try {
      const message = await submitFn(new FormData(form));
      dialog.close();
      showNotice(message);
      loadResources();
    } catch (err) {
      error.textContent = err.message;
    }
  });
  return dialog;
}

// assign (FR-5)
let assignTarget = null;
const assignDialog = wireDialog("#dlg-assign", "#assign-form", "#assign-error", async (form) => {
  const value = String(form.get("value")).trim();
  const body = { resource_id: assignTarget.resource_id };
  if (form.get("kind") === "patient") body.patient_identifier = value; else body.operational_need = value;
  await api("/api/assignments", { method: "POST", body });
  return `${assignTarget.resource_id} assigned.`;
});
document.querySelectorAll('#assign-form input[name="kind"]').forEach((radio) => radio.addEventListener("change", () => {
  $("#assign-form input[name=value]").placeholder = radio.value === "patient" ? "SIM-12345" : "e.g. Staff training session";
}));
function openAssign(item) {
  assignTarget = item;
  $("#assign-title").textContent = item.resource_id;
  $("#assign-form").reset(); $("#assign-error").textContent = "";
  assignDialog.showModal();
}

// release (FR-6)
let releaseTarget = null;
const releaseDialog = wireDialog("#dlg-release", "#release-form", "#release-error", async (form) => {
  await api(`/api/assignments/${releaseTarget.assignment_id}/release`, { method: "POST", body: { next_status: form.get("next") } });
  return `${releaseTarget.resource_id} released.`;
});
function openRelease(item) {
  releaseTarget = item;
  $("#release-title").textContent = item.resource_id;
  $("#release-error").textContent = "";
  releaseDialog.showModal();
}

// change status (FR-4)
let statusTarget = null;
const statusDialog = wireDialog("#dlg-status", "#status-form", "#status-error", async (form) => {
  await api(`/api/resources/${statusTarget.resource_id}/status`, { method: "POST", body: { status: form.get("status") } });
  return `${statusTarget.resource_id} is now ${form.get("status")}.`;
});
function openStatus(item) {
  statusTarget = item;
  $("#status-title").textContent = item.resource_id;
  $("#status-form select[name=status]").value = item.status === "Available" ? "Cleaning" : "Available";
  $("#status-error").textContent = "";
  statusDialog.showModal();
}

// add / edit / retire (FR-3, administrators only)
const addDialog = wireDialog("#dlg-add", "#add-form", "#add-error", async (form) => {
  await api("/api/resources", { method: "POST", body: {
    resource_id: String(form.get("resource_id")), type: form.get("type"), location: String(form.get("location")) } });
  return `${String(form.get("resource_id")).toUpperCase()} added.`;
});
$("#add-resource").addEventListener("click", () => { $("#add-form").reset(); $("#add-error").textContent = ""; addDialog.showModal(); });

let editTarget = null;
const editDialog = wireDialog("#dlg-edit", "#edit-form", "#edit-error", async (form) => {
  await api(`/api/resources/${editTarget.resource_id}`, { method: "PATCH", body: { location: String(form.get("location")) } });
  return `${editTarget.resource_id} updated.`;
});
function openEdit(item) {
  editTarget = item;
  $("#edit-title").textContent = item.resource_id;
  $("#edit-form input[name=location]").value = item.location;
  $("#edit-error").textContent = "";
  editDialog.showModal();
}

async function retire(item) {
  if (!confirm(`Retire ${item.resource_id}? It will no longer appear on the dashboard.`)) return;
  try {
    await api(`/api/resources/${item.resource_id}`, { method: "DELETE" });
    showNotice(`${item.resource_id} retired.`);
    loadResources();
  } catch (error) {
    showNotice(error.message, true);
  }
}

// ---------- history (FR-9) ----------
function simpleTable(headers, rows) {
  const table = el("table", {}, el("thead", {}, el("tr", {}, ...headers.map((h) => el("th", {}, h)))));
  const body = el("tbody");
  for (const row of rows) body.append(el("tr", {}, ...row.map((cell) => el("td", {}, cell == null ? "—" : String(cell)))));
  table.append(body);
  return table;
}

function fmt(iso) { return iso ? new Date(iso).toLocaleString() : null; }

async function openHistory(item) {
  try {
    const data = await api(`/api/resources/${item.resource_id}/history`);
    $("#history-title").textContent = item.resource_id;
    const box = $("#history-body");
    box.replaceChildren(
      el("div", { class: "hist-section" }, el("h4", {}, "Assignments"),
        data.assignments.length
          ? simpleTable(["For", "Assigned", "By", "Released", "By"],
              data.assignments.map((a) => [a.patient_id || a.operational_need, fmt(a.assigned_at), a.assigned_by, fmt(a.released_at), a.released_by]))
          : el("p", { class: "muted" }, "No assignments yet.")),
      el("div", { class: "hist-section" }, el("h4", {}, "Status changes"),
        simpleTable(["From", "To", "When", "By"],
          data.status_changes.map((s) => [s.previous_status, s.new_status, fmt(s.changed_at), s.changed_by]))));
    $("#dlg-history").showModal();
  } catch (error) {
    showNotice(error.message, true);
  }
}
$("#close-history").addEventListener("click", () => $("#dlg-history").close());

// ---------- utilization report (FR-10) ----------
$("#show-report").addEventListener("click", async () => {
  try {
    const data = await api("/api/reports/utilization");
    const rows = Object.entries(data.by_type).map(([type, v]) => [type, v.total, ...STATUSES.map((s) => v[s]), v.utilization_pct + "%"]);
    rows.push(["All", data.overall.total, ...STATUSES.map((s) => data.overall[s]), data.overall.utilization_pct + "%"]);
    $("#report-body").replaceChildren(
      simpleTable(["Type", "Total", ...STATUSES, "Utilization"], rows),
      el("p", { class: "muted" }, "Utilization = Occupied resources divided by all active resources of that type."));
    $("#dlg-report").showModal();
  } catch (error) {
    showNotice(error.message, true);
  }
});
$("#close-report").addEventListener("click", () => $("#dlg-report").close());

// ---------- start ----------
(async function start() {
  if (!token) return;
  try { me = await api("/api/me"); showApp(); } catch (_) { signOutLocally(); }
})();

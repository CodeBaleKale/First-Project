/* SnapCal frontend — capture, review, calendar, tasks. No build step, no deps. */

const $ = (sel) => document.querySelector(sel);
const $$ = (sel) => [...document.querySelectorAll(sel)];

// ---------- view switching ----------
$$(".tab").forEach((tab) =>
  tab.addEventListener("click", () => {
    $$(".tab").forEach((t) => t.classList.toggle("active", t === tab));
    $$(".view").forEach((v) => v.classList.add("hidden"));
    $(`#view-${tab.dataset.view}`).classList.remove("hidden");
    if (tab.dataset.view === "calendar") renderCalendar();
    if (tab.dataset.view === "tasks") renderTasks();
  })
);

function toast(msg) {
  const el = $("#toast");
  el.textContent = msg;
  el.classList.remove("hidden");
  clearTimeout(toast._t);
  toast._t = setTimeout(() => el.classList.add("hidden"), 2600);
}

async function api(path, options = {}) {
  const res = await fetch(path, options);
  if (!res.ok) {
    let detail = res.statusText;
    try { detail = (await res.json()).detail || detail; } catch {}
    throw new Error(detail);
  }
  return res.status === 204 ? null : res.json();
}

// ---------- health banner ----------
api("/api/health")
  .then((h) => { if (!h.api_key_configured) $("#key-banner").classList.remove("hidden"); })
  .catch(() => {});

// ---------- capture: screenshots ----------
const files = [];
const dropzone = $("#dropzone");
const fileInput = $("#file-input");

dropzone.addEventListener("click", () => fileInput.click());
dropzone.addEventListener("dragover", (e) => { e.preventDefault(); dropzone.classList.add("dragover"); });
dropzone.addEventListener("dragleave", () => dropzone.classList.remove("dragover"));
dropzone.addEventListener("drop", (e) => {
  e.preventDefault();
  dropzone.classList.remove("dragover");
  addFiles(e.dataTransfer.files);
});
fileInput.addEventListener("change", () => { addFiles(fileInput.files); fileInput.value = ""; });
document.addEventListener("paste", (e) => {
  if ($("#view-capture").classList.contains("hidden")) return;
  const imgs = [...(e.clipboardData?.items || [])]
    .filter((i) => i.type.startsWith("image/"))
    .map((i) => i.getAsFile());
  if (imgs.length) addFiles(imgs);
});

function addFiles(list) {
  for (const f of list) {
    if (!f || !f.type.startsWith("image/")) continue;
    if (files.length >= 6) { toast("Up to 6 screenshots per extraction"); break; }
    files.push(f);
  }
  renderPreviews();
}

function renderPreviews() {
  const wrap = $("#previews");
  wrap.innerHTML = "";
  files.forEach((f, i) => {
    const div = document.createElement("div");
    div.className = "preview";
    const img = document.createElement("img");
    img.src = URL.createObjectURL(f);
    const rm = document.createElement("button");
    rm.textContent = "✕";
    rm.title = "Remove";
    rm.addEventListener("click", () => { files.splice(i, 1); renderPreviews(); });
    div.append(img, rm);
    wrap.append(div);
  });
}

// ---------- capture: extraction ----------
let reviewItems = [];

$("#extract-btn").addEventListener("click", async () => {
  const text = $("#capture-text").value.trim();
  if (!text && files.length === 0) { toast("Paste some text or add a screenshot first"); return; }

  const status = $("#extract-status");
  const btn = $("#extract-btn");
  btn.disabled = true;
  status.classList.remove("hidden", "error");
  status.textContent = "Reading with Claude…";

  const form = new FormData();
  if (text) form.append("text", text);
  files.forEach((f) => form.append("images", f));

  try {
    const result = await api("/api/extract", { method: "POST", body: form });
    reviewItems = result.items.map((item) => ({ ...item, _include: item.confidence !== "low" }));
    $("#review-summary").textContent = result.summary;
    renderReview();
    $("#review").classList.toggle("hidden", false);
    status.textContent = result.items.length
      ? `Found ${result.items.length} item(s) — review and confirm below.`
      : "Nothing actionable found in that input.";
  } catch (err) {
    status.classList.add("error");
    status.textContent = `Extraction failed: ${err.message}`;
  } finally {
    btn.disabled = false;
  }
});

function splitDateTime(value) {
  if (!value) return { date: "", time: "" };
  const [date, time] = value.split("T");
  return { date, time: (time || "").slice(0, 5) };
}

function renderReview() {
  const list = $("#review-list");
  list.innerHTML = "";
  if (!reviewItems.length) { $("#save-btn").classList.add("hidden"); return; }
  $("#save-btn").classList.remove("hidden");

  reviewItems.forEach((item, i) => {
    const card = document.createElement("div");
    card.className = "card" + (item._include ? "" : " excluded");

    const include = document.createElement("input");
    include.type = "checkbox";
    include.checked = item._include;
    include.title = "Include this item";
    include.addEventListener("change", () => { item._include = include.checked; renderReview(); });

    const fields = document.createElement("div");
    fields.className = "fields";

    const kindSel = document.createElement("select");
    for (const k of ["event", "task"]) {
      const opt = new Option(k === "event" ? "📅 Event" : "✅ Task", k, false, item.kind === k);
      kindSel.add(opt);
    }
    kindSel.addEventListener("change", () => { item.kind = kindSel.value; renderReview(); });

    const title = document.createElement("input");
    title.type = "text";
    title.className = "title-input";
    title.value = item.title;
    title.addEventListener("input", () => (item.title = title.value));

    fields.append(kindSel, title);

    if (item.kind === "event") {
      const { date, time } = splitDateTime(item.start);
      const dateIn = document.createElement("input");
      dateIn.type = "date";
      dateIn.value = date;
      const timeIn = document.createElement("input");
      timeIn.type = "time";
      timeIn.value = time;
      timeIn.disabled = item.all_day;
      const sync = () => {
        item.start = dateIn.value ? (item.all_day || !timeIn.value ? dateIn.value : `${dateIn.value}T${timeIn.value}`) : null;
      };
      dateIn.addEventListener("change", sync);
      timeIn.addEventListener("change", sync);

      const allDayLabel = document.createElement("label");
      const allDay = document.createElement("input");
      allDay.type = "checkbox";
      allDay.checked = item.all_day;
      allDay.addEventListener("change", () => { item.all_day = allDay.checked; sync(); renderReview(); });
      allDayLabel.append(allDay, " all-day");

      const loc = document.createElement("input");
      loc.type = "text";
      loc.placeholder = "location";
      loc.value = item.location || "";
      loc.addEventListener("input", () => (item.location = loc.value || null));

      fields.append(dateIn, timeIn, allDayLabel, loc);
    } else {
      const due = document.createElement("input");
      due.type = "date";
      due.value = (item.due || "").split("T")[0];
      due.addEventListener("change", () => (item.due = due.value || null));
      const dueLabel = document.createElement("label");
      dueLabel.append("due ", due);
      fields.append(dueLabel);
    }

    const meta = document.createElement("div");
    meta.className = "meta-row";
    meta.innerHTML = `<span class="badge ${item.kind}">${item.kind}</span>
      <span class="badge ${item.confidence}">${item.confidence} confidence</span>`;
    if (item.with_people?.length) {
      const ppl = document.createElement("span");
      ppl.textContent = "with " + item.with_people.join(", ");
      meta.append(ppl);
    }
    if (item.notes) {
      const notes = document.createElement("span");
      notes.textContent = item.notes;
      meta.append(notes);
    }

    card.append(include, fields, meta);
    if (item.source_quote) {
      const q = document.createElement("div");
      q.className = "quote";
      q.textContent = item.source_quote;
      card.append(q);
    }
    list.append(card);
  });
}

$("#save-btn").addEventListener("click", async () => {
  const selected = reviewItems.filter((i) => i._include && i.title.trim());
  if (!selected.length) { toast("Nothing selected"); return; }
  const payload = { items: selected.map(({ _include, ...item }) => item) };
  try {
    const saved = await api("/api/items", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    toast(`Added ${saved.length} item(s)`);
    reviewItems = [];
    files.length = 0;
    $("#capture-text").value = "";
    renderPreviews();
    $("#review").classList.add("hidden");
    $("#extract-status").classList.add("hidden");
    refreshTaskCount();
    const hasEvents = saved.some((s) => s.kind === "event");
    $(`.tab[data-view="${hasEvents ? "calendar" : "tasks"}"]`).click();
  } catch (err) {
    toast(`Save failed: ${err.message}`);
  }
});

// ---------- calendar ----------
let calCursor = new Date();
let selectedDay = null;

$("#cal-prev").addEventListener("click", () => { calCursor.setMonth(calCursor.getMonth() - 1); renderCalendar(); });
$("#cal-next").addEventListener("click", () => { calCursor.setMonth(calCursor.getMonth() + 1); renderCalendar(); });
$("#cal-today").addEventListener("click", () => { calCursor = new Date(); selectedDay = isoDate(new Date()); renderCalendar(); });

function isoDate(d) {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

async function renderCalendar() {
  const events = await api("/api/items?kind=event").catch(() => []);
  const byDay = {};
  for (const ev of events) {
    if (!ev.start) continue;
    const day = ev.start.split("T")[0];
    (byDay[day] ||= []).push(ev);
  }

  const year = calCursor.getFullYear();
  const month = calCursor.getMonth();
  $("#cal-title").textContent = calCursor.toLocaleDateString(undefined, { month: "long", year: "numeric" });

  const first = new Date(year, month, 1);
  const gridStart = new Date(first);
  gridStart.setDate(1 - first.getDay());

  const grid = $("#cal-grid");
  grid.innerHTML = "";
  const todayIso = isoDate(new Date());

  for (let i = 0; i < 42; i++) {
    const day = new Date(gridStart);
    day.setDate(gridStart.getDate() + i);
    const dayIso = isoDate(day);
    const cell = document.createElement("div");
    cell.className = "cal-cell";
    if (day.getMonth() !== month) cell.classList.add("other");
    if (dayIso === todayIso) cell.classList.add("today");
    cell.innerHTML = `<span class="daynum">${day.getDate()}</span>`;
    for (const ev of (byDay[dayIso] || []).slice(0, 3)) {
      const chip = document.createElement("span");
      chip.className = "chip";
      const { time } = splitDateTime(ev.start);
      chip.textContent = (ev.all_day || !time ? "" : time + " ") + ev.title;
      cell.append(chip);
    }
    if ((byDay[dayIso] || []).length > 3) {
      const more = document.createElement("span");
      more.className = "chip";
      more.textContent = `+${byDay[dayIso].length - 3} more`;
      cell.append(more);
    }
    cell.addEventListener("click", () => { selectedDay = dayIso; renderDayPanel(byDay); });
    grid.append(cell);
  }
  renderDayPanel(byDay);
}

function renderDayPanel(byDay) {
  const panel = $("#day-panel");
  if (!selectedDay) { panel.classList.add("hidden"); return; }
  panel.classList.remove("hidden");
  const date = new Date(selectedDay + "T00:00");
  $("#day-panel-title").textContent = date.toLocaleDateString(undefined, {
    weekday: "long", month: "long", day: "numeric",
  });
  const list = $("#day-panel-list");
  list.innerHTML = "";
  const events = byDay[selectedDay] || [];
  if (!events.length) { list.innerHTML = '<div class="empty">Nothing scheduled.</div>'; return; }
  for (const ev of events.sort((a, b) => (a.start > b.start ? 1 : -1))) {
    const row = document.createElement("div");
    row.className = "day-item";
    const { time } = splitDateTime(ev.start);
    row.innerHTML = `<span class="time">${ev.all_day || !time ? "all day" : time}</span>
      <span>${escapeHtml(ev.title)}</span>
      <span class="where">${escapeHtml(ev.location || "")}</span>
      <span class="spacer"></span>`;
    const dl = document.createElement("a");
    dl.className = "icon-btn";
    dl.href = `/api/items/${ev.id}/export.ics`;
    dl.title = "Download .ics";
    dl.textContent = "⤓";
    const del = document.createElement("button");
    del.className = "icon-btn";
    del.title = "Delete";
    del.textContent = "🗑";
    del.addEventListener("click", async () => {
      await api(`/api/items/${ev.id}`, { method: "DELETE" });
      toast("Event deleted");
      renderCalendar();
    });
    row.append(dl, del);
    list.append(row);
  }
}

function escapeHtml(s) {
  return s.replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
}

// ---------- tasks ----------
$("#show-done").addEventListener("change", renderTasks);

async function renderTasks() {
  const tasks = await api("/api/items?kind=task").catch(() => []);
  const showDone = $("#show-done").checked;
  const list = $("#task-list");
  list.innerHTML = "";
  const visible = tasks.filter((t) => showDone || !t.done);
  if (!visible.length) {
    list.innerHTML = '<div class="empty">No to-dos yet — capture a text to get started.</div>';
  }
  const todayIso = isoDate(new Date());
  for (const task of visible) {
    const row = document.createElement("div");
    row.className = "task-row" + (task.done ? " done" : "");
    const check = document.createElement("input");
    check.type = "checkbox";
    check.checked = task.done;
    check.addEventListener("change", async () => {
      await api(`/api/items/${task.id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ done: check.checked }),
      });
      renderTasks();
      refreshTaskCount();
    });
    const title = document.createElement("span");
    title.className = "task-title";
    title.textContent = task.title;
    row.append(check, title);
    if (task.due) {
      const due = document.createElement("span");
      const dueDay = task.due.split("T")[0];
      due.className = "task-due" + (!task.done && dueDay < todayIso ? " overdue" : "");
      due.textContent = "due " + new Date(dueDay + "T00:00").toLocaleDateString(undefined, { month: "short", day: "numeric" });
      row.append(due);
    }
    const del = document.createElement("button");
    del.className = "icon-btn";
    del.title = "Delete";
    del.textContent = "🗑";
    del.addEventListener("click", async () => {
      await api(`/api/items/${task.id}`, { method: "DELETE" });
      renderTasks();
      refreshTaskCount();
    });
    row.append(del);
    list.append(row);
  }
}

async function refreshTaskCount() {
  const tasks = await api("/api/items?kind=task").catch(() => []);
  const open = tasks.filter((t) => !t.done).length;
  $("#task-count").textContent = open ? `(${open})` : "";
}

refreshTaskCount();

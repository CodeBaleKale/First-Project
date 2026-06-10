"use strict";

const STORAGE_KEY = "panpals-state-v2";
const TOKEN_RE = /[#@][A-Za-z0-9_]+/g;
const HASHTAG_RE = /#[A-Za-z0-9_]+/g;
const TOP_PALS_SLOTS = 8;

const usersById = Object.fromEntries(SEED.users.map((u) => [u.id, u]));

// ---------- State ----------

function seedState() {
  const now = Date.now();
  return {
    activeUserId: "you",
    // MySpace rules: Vexx is everyone's first pal. No exceptions. 🌕
    followedIds: ["vexx"],
    welcomeDismissed: false,
    posts: SEED.posts.map((p) => ({
      id: p.id,
      userId: p.userId,
      text: p.text,
      ts: now - p.minutesAgo * 60000,
      pinned: p.pinned ?? false,
      sears: p.sears,
      searedByMe: false,
      comments: p.comments.map((c) => ({
        userId: c.userId,
        text: c.text,
        ts: now - c.minutesAgo * 60000,
      })),
    })),
  };
}

function loadState() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY));
    if (parsed && Array.isArray(parsed.posts) && usersById[parsed.activeUserId]) {
      parsed.followedIds = Array.isArray(parsed.followedIds) ? parsed.followedIds : ["vexx"];
      if (!parsed.followedIds.includes("vexx")) parsed.followedIds.unshift("vexx");
      return parsed;
    }
  } catch {
    // Corrupt or legacy state: fall through and reseed.
  }
  return seedState();
}

let state = loadState();
let hashtagFilter = null;
let searchQuery = "";
const expandedPosts = new Set();
let searPulseId = null; // post whose sear button should pop on next render
let freshPostId = null; // post that should slide in on next render

function save() {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
}

function activeUser() {
  return usersById[state.activeUserId];
}

// ---------- DOM helpers ----------

function el(tag, attrs = {}, ...children) {
  const node = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs)) {
    if (value == null || value === false) continue;
    if (key === "class") node.className = value;
    else if (key.startsWith("on")) node.addEventListener(key.slice(2), value);
    else if (value === true) node.setAttribute(key, "");
    else node.setAttribute(key, value);
  }
  for (const child of children.flat(Infinity)) {
    if (child == null) continue;
    node.append(child.nodeType ? child : document.createTextNode(child));
  }
  return node;
}

function avatar(user, size = "md") {
  return el(
    "span",
    { class: `avatar avatar-${size}`, style: `background:${user.color}`, title: user.name },
    user.emoji
  );
}

// Name plus any badge ("🌕 Founder") and the "(you)" tag.
function nameBits(user) {
  const bits = [user.name];
  if (user.badge) bits.push(" ", el("span", { class: "badge" }, user.badge));
  if (user.isYou) bits.push(" ", el("span", { class: "you-tag" }, "(you)"));
  return bits;
}

// Renders post text as DOM nodes (never innerHTML) with clickable
// #hashtags and @mentions.
function richText(text) {
  const frag = document.createDocumentFragment();
  let last = 0;
  for (const match of text.matchAll(TOKEN_RE)) {
    frag.append(text.slice(last, match.index));
    const token = match[0];
    if (token.startsWith("#")) {
      frag.append(el("button", { class: "hashtag", type: "button", onclick: () => setHashtagFilter(token) }, token));
    } else {
      frag.append(el("button", { class: "mention", type: "button", onclick: () => searchFor(token) }, token));
    }
    last = match.index + token.length;
  }
  frag.append(text.slice(last));
  return frag;
}

function timeAgo(ts) {
  const mins = Math.max(1, Math.round((Date.now() - ts) / 60000));
  if (mins < 60) return `${mins}m`;
  const hours = Math.round(mins / 60);
  if (hours < 24) return `${hours}h`;
  return `${Math.round(hours / 24)}d`;
}

function formatCount(n) {
  if (n >= 1e6) return `${Math.round(n / 1e5) / 10}M`;
  if (n >= 1000) return `${Math.round(n / 100) / 10}k`;
  return `${n}`;
}

function firstName(user) {
  return user.firstName ?? user.name.split(" ").at(-1);
}

// ---------- Filtering ----------

function postTags(text) {
  return (text.match(HASHTAG_RE) ?? []).map((t) => t.toLowerCase());
}

function visiblePosts() {
  const list = state.posts.filter((post) => {
    if (hashtagFilter && !postTags(post.text).includes(hashtagFilter.toLowerCase())) return false;
    if (searchQuery) {
      const user = usersById[post.userId];
      const haystack = `${post.text} ${user.name} @${user.handle}`.toLowerCase();
      if (!haystack.includes(searchQuery)) return false;
    }
    return true;
  });
  // Founder's welcome stays pinned to the top, MySpace-Tom style.
  return list.sort((a, b) => (b.pinned ? 1 : 0) - (a.pinned ? 1 : 0));
}

function setHashtagFilter(tag) {
  hashtagFilter = tag;
  renderFilterBanner();
  renderFeed();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function searchFor(query) {
  document.getElementById("search").value = query;
  searchQuery = query.toLowerCase();
  renderFeed();
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function clearFilters() {
  hashtagFilter = null;
  searchQuery = "";
  document.getElementById("search").value = "";
  renderFilterBanner();
  renderFeed();
}

// ---------- Actions ----------

function switchPersona(userId) {
  state.activeUserId = userId;
  save();
  renderAll();
}

function submitPost() {
  const input = document.getElementById("composerInput");
  const text = input.value.trim();
  if (!text) return;
  const post = {
    id: `post-${Date.now()}`,
    userId: state.activeUserId,
    text,
    ts: Date.now(),
    pinned: false,
    sears: 0,
    searedByMe: false,
    comments: [],
  };
  state.posts.unshift(post);
  freshPostId = post.id;
  input.value = "";
  document.getElementById("postBtn").disabled = true;
  save();
  renderAll();
}

function toggleSear(post) {
  post.searedByMe = !post.searedByMe;
  post.sears += post.searedByMe ? 1 : -1;
  if (post.searedByMe) searPulseId = post.id;
  save();
  renderFeed();
}

function addComment(post, inputEl) {
  const text = inputEl.value.trim();
  if (!text) return;
  post.comments.push({ userId: state.activeUserId, text, ts: Date.now() });
  expandedPosts.add(post.id);
  save();
  renderFeed();
}

function toggleFollow(userId) {
  const idx = state.followedIds.indexOf(userId);
  if (idx === -1) state.followedIds.push(userId);
  else state.followedIds.splice(idx, 1);
  save();
  renderSuggestions();
  renderProfile(); // Top Pals grid lives on the profile card
}

function dismissWelcome() {
  state.welcomeDismissed = true;
  save();
  renderFeed();
}

// ---------- Rendering ----------

function renderTopbar() {
  const user = activeUser();
  document.getElementById("topbarUser").replaceChildren(
    avatar(user, "sm"),
    el("span", { class: "who-text" }, el("b", {}, nameBits(user)), " ", el("span", { class: "handle" }, `@${user.handle}`))
  );
}

function topPals() {
  const ids = ["vexx", ...state.followedIds.filter((id) => id !== "vexx")];
  return ids.slice(0, TOP_PALS_SLOTS).map((id) => usersById[id]).filter(Boolean);
}

function palsGrid() {
  const pals = topPals();
  const slots = pals.map((pal, i) =>
    el(
      "div",
      { class: "pal-slot", title: pal.isFounder ? "Vexx is everyone's first pal" : pal.name },
      avatar(pal, "sm"),
      el("span", { class: "pal-name" }, firstName(pal)),
      i === 0 ? el("span", { class: "pal-first" }, "1st 💘") : null
    )
  );
  for (let i = pals.length; i < TOP_PALS_SLOTS; i++) {
    slots.push(
      el(
        "div",
        { class: "pal-slot empty", title: "Follow pals to fill your Top 8" },
        el("span", { class: "circ" }, "+"),
        el("span", { class: "pal-name" }, "open")
      )
    );
  }
  return el(
    "div",
    { class: "pals-block" },
    el("h3", { class: "pals-title" }, "Top Pals"),
    el("div", { class: "pals-grid" }, slots),
    el("p", { class: "pals-note" }, "Vexx is everyone's first pal. House rules. 🌕")
  );
}

function renderProfile() {
  const user = activeUser();
  const sizzleCount = state.posts.filter((p) => p.userId === user.id).length;
  const stats = user.isYou
    ? [
        [String(sizzleCount), "Sizzles"],
        [String(state.followedIds.length), "Pals"],
        [formatCount(user.followers), "Followers"],
      ]
    : [
        [String(sizzleCount), "Sizzles"],
        [formatCount(user.followers), "Followers"],
        [formatCount(user.following), "Following"],
      ];
  document.getElementById("profileCard").replaceChildren(
    el("div", { class: "profile-cover", style: `background:linear-gradient(120deg, ${user.color}, #c2571f)` }),
    avatar(user, "lg"),
    el("div", { class: "profile-name" }, nameBits(user)),
    el("div", { class: "profile-handle" }, `@${user.handle}`),
    el("div", {}, el("span", { class: "profile-chip" }, user.type)),
    el("p", { class: "profile-bio" }, user.bio),
    el(
      "div",
      { class: "profile-stats" },
      stats.map(([value, label]) =>
        el("span", { class: "stat" }, el("b", {}, value), el("span", {}, label))
      )
    ),
    user.isYou ? palsGrid() : null
  );
}

function renderPersonas() {
  const items = SEED.users
    .filter((user) => !user.isFounder) // nobody gets to BE Vexx
    .map((user) =>
      el(
        "li",
        {},
        el(
          "button",
          {
            class: `persona-item${user.id === state.activeUserId ? " persona-active" : ""}`,
            type: "button",
            onclick: () => switchPersona(user.id),
          },
          avatar(user, "sm"),
          el("span", { class: "who" }, el("b", {}, nameBits(user)), el("span", {}, user.type))
        )
      )
    );
  document.getElementById("personaList").replaceChildren(...items);
}

function renderComposer() {
  const user = activeUser();
  document.getElementById("composerAvatar").replaceChildren(avatar(user, "md"));
  document.getElementById("composerInput").placeholder = `What's sizzling, ${firstName(user)}?`;
}

function renderFilterBanner() {
  const banner = document.getElementById("filterBanner");
  if (!hashtagFilter) {
    banner.hidden = true;
    banner.replaceChildren();
    return;
  }
  banner.hidden = false;
  banner.replaceChildren(
    el("span", {}, `Showing sizzles tagged ${hashtagFilter}`),
    el("button", { type: "button", onclick: clearFilters }, "Clear")
  );
}

function welcomeCard() {
  const vexx = usersById.vexx;
  return el(
    "div",
    { class: "card welcome-card" },
    avatar(vexx, "md"),
    el(
      "div",
      { class: "welcome-text" },
      el("b", {}, "Vexx WereWolf is now your pal! "),
      "Everyone on PanPals starts with one friend — the founder. Welcome aboard. 🌕"
    ),
    el("button", { class: "welcome-btn", type: "button", onclick: dismissWelcome }, "Thanks, Vexx 💘")
  );
}

function commentRow(comment) {
  const user = usersById[comment.userId];
  return el(
    "div",
    { class: "comment" },
    avatar(user, "sm"),
    el(
      "div",
      { class: "comment-body" },
      el("b", {}, nameBits(user), " ", el("span", { class: "when" }, `· ${timeAgo(comment.ts)}`)),
      el("p", {}, comment.text)
    )
  );
}

function commentsBlock(post) {
  const input = el("input", {
    class: "comment-input",
    type: "text",
    maxlength: "280",
    placeholder: `Reply as ${firstName(activeUser())}…`,
    onkeydown: (event) => {
      if (event.key === "Enter") addComment(post, input);
    },
  });
  return el(
    "div",
    { class: "comments" },
    post.comments.map(commentRow),
    el(
      "div",
      { class: "comment-composer" },
      avatar(activeUser(), "sm"),
      input,
      el("button", { class: "reply-btn", type: "button", onclick: () => addComment(post, input) }, "Reply")
    )
  );
}

function postCard(post) {
  const user = usersById[post.userId];
  const expanded = expandedPosts.has(post.id);
  return el(
    "article",
    { class: `card post${post.id === freshPostId ? " fresh" : ""}` },
    el(
      "header",
      { class: "post-header" },
      avatar(user, "md"),
      el(
        "div",
        { class: "post-meta" },
        el("span", { class: "post-name" }, nameBits(user)),
        el("span", { class: "post-sub" }, `@${user.handle} · ${timeAgo(post.ts)}`)
      ),
      post.pinned ? el("span", { class: "pin-chip" }, "📌 Pinned") : null
    ),
    el("div", { class: "post-body" }, richText(post.text)),
    el(
      "div",
      { class: "post-actions" },
      el(
        "button",
        {
          class: `action-btn${post.searedByMe ? " seared" : ""}${post.id === searPulseId ? " pop" : ""}`,
          type: "button",
          title: "Sear this sizzle",
          onclick: () => toggleSear(post),
        },
        `🔥 ${post.sears}`
      ),
      el(
        "button",
        {
          class: "action-btn",
          type: "button",
          title: "Show replies",
          onclick: () => {
            if (expandedPosts.has(post.id)) expandedPosts.delete(post.id);
            else expandedPosts.add(post.id);
            renderFeed();
          },
        },
        `💬 ${post.comments.length}`
      )
    ),
    expanded ? commentsBlock(post) : null
  );
}

function renderFeed() {
  const feed = document.getElementById("feed");
  const nodes = [];
  if (!state.welcomeDismissed) nodes.push(welcomeCard());
  const posts = visiblePosts();
  if (posts.length === 0) {
    nodes.push(
      el(
        "div",
        { class: "card empty-state" },
        el("div", { class: "big" }, "🍽️"),
        el("div", {}, "Nothing on the menu. Try clearing your search or filter.")
      )
    );
  } else {
    nodes.push(...posts.map(postCard));
  }
  feed.replaceChildren(...nodes);
  searPulseId = null;
  freshPostId = null;
}

function renderTrending() {
  const counts = new Map();
  for (const post of state.posts) {
    for (const tag of post.text.match(HASHTAG_RE) ?? []) {
      const key = tag.toLowerCase();
      const entry = counts.get(key) ?? { display: tag, count: 0 };
      entry.count += 1;
      counts.set(key, entry);
    }
  }
  const top = [...counts.values()].sort((a, b) => b.count - a.count || a.display.localeCompare(b.display)).slice(0, 5);
  const items = top.map(({ display, count }, i) =>
    el(
      "li",
      { class: "trend-item" },
      el("span", { class: "trend-rank" }, `${i + 1}`),
      el("button", { class: "tag", type: "button", onclick: () => setHashtagFilter(display) }, display),
      el("span", { class: "trend-count" }, `${count} ${count === 1 ? "sizzle" : "sizzles"}`)
    )
  );
  document.getElementById("trendingList").replaceChildren(...items);
}

function renderSuggestions() {
  const suggestions = SEED.users
    .filter((user) => user.id !== state.activeUserId && !user.isFounder && !user.isYou)
    .sort((a, b) => b.followers - a.followers)
    .slice(0, 3);
  const items = suggestions.map((user) => {
    const following = state.followedIds.includes(user.id);
    return el(
      "li",
      { class: "suggest-item" },
      avatar(user, "sm"),
      el("span", { class: "who" }, el("b", {}, user.name), el("span", {}, `${formatCount(user.followers)} followers`)),
      el(
        "button",
        {
          class: `follow-btn${following ? " following" : ""}`,
          type: "button",
          onclick: () => toggleFollow(user.id),
        },
        following ? "Pals ✓" : "Add pal"
      )
    );
  });
  document.getElementById("suggestList").replaceChildren(...items);
}

function renderAll() {
  renderTopbar();
  renderProfile();
  renderPersonas();
  renderComposer();
  renderFilterBanner();
  renderTrending();
  renderSuggestions();
  renderFeed();
}

// ---------- Init ----------

function init() {
  document.getElementById("logo").addEventListener("click", clearFilters);

  document.getElementById("search").addEventListener("input", (event) => {
    searchQuery = event.target.value.trim().toLowerCase();
    renderFeed();
  });

  const composerInput = document.getElementById("composerInput");
  const postBtn = document.getElementById("postBtn");
  composerInput.addEventListener("input", () => {
    postBtn.disabled = !composerInput.value.trim();
  });
  composerInput.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && (event.metaKey || event.ctrlKey)) submitPost();
  });
  postBtn.addEventListener("click", submitPost);

  document.getElementById("resetBtn").addEventListener("click", () => {
    if (!confirm("Reset PanPals demo data? Your posts and sears will be lost.")) return;
    localStorage.removeItem(STORAGE_KEY);
    location.reload();
  });

  renderAll();
}

init();

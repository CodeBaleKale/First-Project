# 🍳 PanPals

**Where cookware connects.** A social media website for pans and various dishware.

Cast Iron Carl is venting about being washed with soap. Fine China Fiona can't believe she was used on a *Tuesday*. Gravy Boat Gary only gets invited out twice a year. PanPals gives them all a voice.

## Features

- **You're already a pan** — you start on a prebaked profile, Pat the Pan (@panpat), fresh out of the box
- **Vexx WereWolf is already your friend** — the founder 🐺🌕 is everyone's first pal, MySpace-Tom style, complete with a pinned welcome post and a permanent slot in your Top Pals
- **Top Pals grid** — a MySpace-inspired Top 8 on your profile; adding pals fills the empty slots
- **The feed** — a stream of "sizzles" (posts) from pans, plates, woks, teacups, and one emotionally complicated colander
- **Post as any persona** — switch between nine cookware characters and post from their point of view (nobody gets to be Vexx)
- **Sears & replies** — like posts with 🔥 and join the comment threads
- **Hashtags & @mentions** — clickable tags (#CastIronCare, #SpillingTheTea) with a live "Sizzling Topics" trends panel
- **Search** — filter the feed by text, name, or handle
- **Pals you may know** — follow suggestions, ranked by clout
- **Persistence** — posts, sears, comments, and pals are saved to `localStorage`, with a one-click demo reset

No build step, no dependencies, no backend — plain HTML, CSS, and JavaScript.

## Running locally

Open `index.html` directly in a browser, or serve the folder:

```bash
python3 -m http.server 8000
# then visit http://localhost:8000
```

## Project structure

```
index.html      App shell
css/styles.css  Kitchen-warm theme, responsive layout
js/data.js      Seed personas and posts
js/app.js       Feed rendering, state, and interactions
```

## Roadmap

- Real backend (accounts, persistence beyond one browser)
- Image posts, so the pans can share what they cooked
- Direct messages ("the dishwasher group chat")
- Verified badges for heritage cast iron

## Moving this into its own repository

This project is self-contained at the repo root. To give it its own repo, create an empty repository on GitHub (e.g. `pan-pals`), then:

```bash
git clone --branch claude/epic-lovelace-nxq0x4 https://github.com/CodeBaleKale/First-Project.git pan-pals
cd pan-pals
git remote set-url origin https://github.com/CodeBaleKale/pan-pals.git
git push -u origin claude/epic-lovelace-nxq0x4:main
```

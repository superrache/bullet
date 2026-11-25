#!/usr/bin/env python3
"""
Vérifie les sujets des cartes à collectionner (app/src/main/assets/cards/themes.json).

Les cartes sont générées en direct par l'application (CardGenerator) ; ce fichier ne contient que des titres
d'articles de Wikipédia (fr) par thème. Ce script signale les titres introuvables, les pages d'homonymie
et les articles sans image libre (ils ne pourraient jamais devenir une carte), et peut les retirer.

Usage :
  python3 tools/cards/check_themes.py            # rapport
  python3 tools/cards/check_themes.py --fix      # retire les titres inutilisables et corrige les redirections
  python3 tools/cards/check_themes.py --add thème "Titre 1" "Titre 2"   # ajoute puis vérifie
"""

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

UA = "BulletCardsCheck/1.0 (theme list checker)"
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
THEMES = os.path.join(ROOT, "app", "src", "main", "assets", "cards", "themes.json")


def get_json(url: str) -> dict:
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/json"})
    for attempt in range(6):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code != 429 and e.code < 500:
                raise
            time.sleep(int(e.headers.get("Retry-After") or 0) or 10 * (attempt + 1))
    raise RuntimeError(url)


def check(titles: list[str]) -> dict[str, tuple[str | None, str]]:
    """titre → (titre final ou None, raison)."""
    result = {}
    for i in range(0, len(titles), 50):
        batch = titles[i:i + 50]
        q = urllib.parse.urlencode({
            "action": "query", "format": "json", "formatversion": 2, "redirects": 1,
            "prop": "pageimages|pageprops", "piprop": "name", "pilicense": "free", "pilimit": 50,
            "ppprop": "disambiguation|wikibase_item", "titles": "|".join(batch),
        })
        data = get_json(f"https://fr.wikipedia.org/w/api.php?{q}")["query"]
        alias = {n["from"]: n["to"] for n in data.get("normalized", [])}
        redirects = {r["from"]: r["to"] for r in data.get("redirects", [])}
        pages = {p["title"]: p for p in data.get("pages", [])}
        for t in batch:
            final = redirects.get(alias.get(t, t), alias.get(t, t))
            p = pages.get(final, {"missing": True})
            if p.get("missing"):
                result[t] = (None, "introuvable")
            elif "disambiguation" in p.get("pageprops", {}):
                result[t] = (None, "homonymie")
            elif not p.get("pageimage"):
                result[t] = (None, "pas d'image libre")
            else:
                result[t] = (final, "ok")
        time.sleep(1)
    return result


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--fix", action="store_true")
    ap.add_argument("--add", nargs="+", metavar=("THEME", "TITRE"))
    args = ap.parse_args()

    with open(THEMES, encoding="utf-8") as f:
        data = json.load(f)
    if args.add:
        theme = next(t for t in data["themes"] if t["id"] == args.add[0])
        theme["titles"] += [t for t in args.add[1:] if t not in theme["titles"]]
        args.fix = True

    bad = 0
    for theme in data["themes"]:
        status = check(theme["titles"])
        kept = []
        for t in theme["titles"]:
            final, why = status[t]
            if final is None:
                bad += 1
                print(f"  ✗ [{theme['id']}] {t} — {why}", file=sys.stderr)
            else:
                if final != t:
                    print(f"  ↪ [{theme['id']}] {t} → {final}", file=sys.stderr)
                if final not in kept:
                    kept.append(final)
        if args.fix:
            theme["titles"] = kept
        print(f"{theme['emoji']} {theme['label']} : {len(kept)} sujets utilisables", file=sys.stderr)

    if args.fix:
        with open(THEMES, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=1)
            f.write("\n")
    print(f"\n{bad} titre(s) inutilisable(s)" + (" retiré(s)" if args.fix and bad else ""), file=sys.stderr)


if __name__ == "__main__":
    main()

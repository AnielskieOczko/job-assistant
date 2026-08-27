import html, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from fixture import CV

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = "/Volumes/my-data/Developer/Projects/job-assistant/target/cv-prototypes"
FONTS = open(os.path.join(HERE, "fonts.css")).read()
e = html.escape

BASE = """
*{box-sizing:border-box;margin:0;padding:0}
section:last-of-type{margin-bottom:0}
html{-webkit-print-color-adjust:exact;print-color-adjust:exact}
@page{size:A4;margin:0}
body{font-family:PlexSans,'Segoe UI',sans-serif;font-feature-settings:'kern' 1;
  text-rendering:geometricPrecision;color:var(--ink);background:#fff}
a{color:inherit;text-decoration:none}
li{list-style:none}
.role,.edu-row{break-inside:avoid}
h1,h2,h3{font-weight:inherit}
p{orphans:2;widows:2}
"""

def page(title, css, body):
    return (f"<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n<meta charset=\"UTF-8\">\n"
            f"<title>{e(title)}</title>\n<style>{FONTS}</style>\n<style>{BASE}{css}</style>\n"
            f"</head>\n<body>\n{body}\n</body>\n</html>\n")

def contacts_line(sep=" · "):
    parts = [e(c) for c in CV["contacts"]] + [f'<a href="https://{e(u)}">{e(u)}</a>' for _, u in CV["links"]]
    return sep.join(parts)

# ─────────────────────────────────────────────────────────────── A · REGISTER
def register():
    css = """
:root{--ink:#16181d;--muted:#6b7178;--rule:#c9ced6;--pad:13mm}
body{font-size:9.5pt;line-height:1.45;padding:var(--pad) var(--pad) 10mm}
.name{font-family:PlexCond;font-weight:600;font-size:30pt;line-height:.95;letter-spacing:-.012em}
.headline{font-size:11pt;margin-top:5px;color:var(--ink)}
.meta{font-family:PlexMono;font-size:7.6pt;color:var(--muted);margin-top:9px;letter-spacing:-.01em}
.hr{height:2.4px;background:var(--ink);margin:9px 0 11px}
.summary{font-size:9.9pt;margin-bottom:11px}
h2{font-family:PlexMono;font-size:7.4pt;font-weight:500;letter-spacing:.19em;
   text-transform:uppercase;color:var(--muted);padding-bottom:4px;border-bottom:1px solid var(--rule);
   margin-bottom:9px}
section{margin-bottom:9px}
/* The category rail: the catalog's own taxonomy doing the sorting a recruiter would otherwise do. */
.cat{display:grid;grid-template-columns:22mm 1fr;gap:2px 6mm;margin-bottom:2.5px}
.cat dt{font-family:PlexMono;font-size:7.2pt;letter-spacing:.09em;color:var(--muted);padding-top:1.6px}
.cat dd{font-size:9.8pt}
.role{display:grid;grid-template-columns:33mm 1fr;gap:0 5mm;margin-bottom:8px}
.when{font-family:PlexMono;font-size:7.4pt;color:var(--muted);padding-top:2.6px;line-height:1.6;white-space:nowrap}
.where{display:block;margin-top:2px}
.role-title{font-weight:600;font-size:10.6pt}
.company{font-size:10.6pt}
.bullets{margin-top:4px}
.bullets li{padding-left:11px;text-indent:-11px;margin-bottom:1.5px}
.bullets li::before{content:"–  ";color:var(--muted)}
.edu-row{display:grid;grid-template-columns:33mm 1fr;gap:0 5mm;margin-bottom:2px}
.edu-row span:first-child{font-family:PlexMono;font-size:7.6pt;color:var(--muted)}
.langs{font-size:9.5pt}
"""
    b = [f'<h1 class="name">{e(CV["fullName"])}</h1>',
         f'<p class="headline">{e(CV["headline"])}</p>',
         f'<p class="meta">{contacts_line("   ·   ")}</p>',
         '<div class="hr"></div>',
         f'<p class="summary">{e(CV["summary"])}</p>',
         '<section><h2>Skills</h2><dl class="cat">']
    for cat, items in CV["skills"]:
        b.append(f'<dt>{e(cat)}</dt><dd>{e(" · ".join(items))}</dd>')
    b.append('</dl></section><section><h2>Experience</h2>')
    for r in CV["experiences"]:
        b.append(f'<div class="role"><div class="when">{e(r["period"])}'
                 f'<span class="where">{e(r["location"])}</span></div><div>'
                 f'<div><span class="role-title">{e(r["role"])}</span></div>'
                 f'<div class="company">{e(r["company"])}</div><ul class="bullets">')
        for text, _ in r["bullets"]:
            b.append(f'<li>{e(text)}</li>')
        b.append('</ul></div></div>')
    b.append('</section><section><h2>Education</h2>')
    for s, p in CV["education"]:
        b.append(f'<div class="edu-row"><span>{e(p)}</span><span>{e(s)}</span></div>')
    b.append('</section><section><h2>Languages</h2><p class="langs">')
    b.append(" · ".join(f"{e(n)} ({e(lv)})" for n, lv in CV["languages"]))
    b.append('</p></section>')
    return page(f'{CV["fullName"]} — CV (Register)', css, "\n".join(b))

# ──────────────────────────────────────────────────────────────── B · DOSSIER
def dossier():
    css = """
:root{--ink:#16181d;--muted:#6b7178;--rule:#d9dde2;--panel:#14171c;--on-panel:#e8ecf1;
      --panel-muted:#8fa0b4;--signal:#9fc2e8}
body{font-size:9.8pt;line-height:1.5}
.sheet{display:grid;grid-template-columns:60mm 1fr;min-height:297mm}
aside{background:var(--panel);color:var(--on-panel);padding:15mm 9mm 15mm 12mm}
main{padding:15mm 14mm 15mm 10mm}
.name{font-family:PlexCond;font-weight:600;font-size:26pt;line-height:.98;letter-spacing:-.01em;color:#fff}
.headline{font-size:9.6pt;color:var(--signal);margin-top:6px;line-height:1.4}
aside h2,main h2{font-family:PlexMono;font-size:7.2pt;font-weight:500;letter-spacing:.19em;
  text-transform:uppercase}
aside h2{color:var(--panel-muted);margin:15px 0 6px;padding-top:11px;border-top:1px solid #2a3038}
main h2{color:var(--muted);padding-bottom:4px;border-bottom:1px solid var(--rule);margin-bottom:9px}
aside .meta{font-family:PlexMono;font-size:6.9pt;letter-spacing:-.02em;line-height:1.9;
  color:var(--on-panel);margin-top:14px;overflow-wrap:normal;word-break:keep-all}
.cat-name{font-family:PlexMono;font-size:6.9pt;letter-spacing:.09em;color:var(--panel-muted);
  margin:7px 0 1px}
.cat-items{font-size:9pt;line-height:1.45}
.aside-row{margin-bottom:7px}
.aside-row .t{font-size:8.9pt;line-height:1.35}
.aside-row .s{font-family:PlexMono;font-size:7.2pt;color:var(--panel-muted)}
main section{margin-bottom:15px}
.summary{font-size:10.1pt;margin-bottom:15px}
.role{margin-bottom:12px}
.role-head{display:flex;justify-content:space-between;align-items:baseline;gap:10px}
.role-title{font-weight:600;font-size:10.5pt}
.company{font-size:10.5pt;color:var(--muted)}
.when{font-family:PlexMono;font-size:7.6pt;color:var(--muted);white-space:nowrap}
.bullets{margin-top:4px}
.bullets li{padding-left:11px;text-indent:-11px;margin-bottom:2.5px}
.bullets li::before{content:"–  ";color:var(--muted)}
"""
    aside = [f'<h1 class="name">{e(CV["fullName"])}</h1>',
             f'<p class="headline">{e(CV["headline"])}</p>',
             '<div class="meta">' + "<br>".join(
                 [e(c) for c in CV["contacts"]] +
                 [f'<a href="https://{e(u)}">{e(u)}</a>' for _, u in CV["links"]]) + '</div>',
             '<h2>Skills</h2>']
    for cat, items in CV["skills"]:
        aside.append(f'<div class="cat-name">{e(cat)}</div>'
                     f'<div class="cat-items">{e(" · ".join(items))}</div>')
    aside.append('<h2>Education</h2>')
    for s, p in CV["education"]:
        aside.append(f'<div class="aside-row"><div class="t">{e(s)}</div><div class="s">{e(p)}</div></div>')
    aside.append('<h2>Languages</h2>')
    for n, lv in CV["languages"]:
        aside.append(f'<div class="aside-row"><div class="t">{e(n)}</div><div class="s">{e(lv)}</div></div>')

    main = [f'<p class="summary">{e(CV["summary"])}</p>', '<section><h2>Experience</h2>']
    for r in CV["experiences"]:
        main.append(f'<div class="role"><div class="role-head"><div>'
                    f'<span class="role-title">{e(r["role"])}</span></div>'
                    f'<span class="when">{e(r["period"])}</span></div>'
                    f'<div class="company">{e(r["company"])} · {e(r["location"])}</div>'
                    f'<ul class="bullets">')
        for text, _ in r["bullets"]:
            main.append(f'<li>{e(text)}</li>')
        main.append('</ul></div>')
    main.append('</section>')
    body = ('<div class="sheet"><aside>' + "\n".join(aside) + '</aside><main>' +
            "\n".join(main) + '</main></div>')
    return page(f'{CV["fullName"]} — CV (Dossier)', css, body)

# ─────────────────────────────────────────────────────────────── C · EVIDENCE
def evidence():
    css = """
:root{--ink:#16181d;--muted:#6b7178;--rule:#c9ced6;--tag:#2f5d50;--tag-bg:#eaf1ee;--pad:13mm}
body{font-size:9.4pt;line-height:1.42;padding:var(--pad) var(--pad) 10mm}
.name{font-family:PlexCond;font-weight:600;font-size:31pt;line-height:.96;letter-spacing:-.012em}
.headline{font-size:10.8pt;margin-top:5px}
.meta{font-family:PlexMono;font-size:7.6pt;color:var(--muted);margin-top:9px}
.hr{height:2.4px;background:var(--ink);margin:11px 0 13px}
.summary{font-size:9.8pt;margin-bottom:11px}
h2{font-family:PlexMono;font-size:7.4pt;font-weight:500;letter-spacing:.19em;text-transform:uppercase;
   color:var(--muted);padding-bottom:4px;border-bottom:1px solid var(--rule);margin-bottom:9px}
section{margin-bottom:11px}
.stack{font-size:9.4pt;line-height:1.6}
.stack b{font-family:PlexMono;font-size:7.2pt;font-weight:500;letter-spacing:.09em;color:var(--muted)}
.role{margin-bottom:10px}
.role-head{display:flex;justify-content:space-between;align-items:baseline;gap:10px}
.role-title{font-weight:600;font-size:10.6pt}
.company{font-size:10.6pt;color:var(--muted)}
.when{font-family:PlexMono;font-size:7.8pt;color:var(--muted);white-space:nowrap}
.bullets{margin-top:5px}
.bullets li{margin-bottom:3.5px;padding-left:11px;text-indent:-11px}
.bullets li::before{content:"–  ";color:var(--muted)}
/* The signature: each claim carries the skills that back it, straight from experience_bullet_skill.
   Text nodes, never ::before content — CvInvariant scans visibleText and must still see these. */
.ev{display:block;text-indent:0;margin-top:1.8px}
.ev span{font-family:PlexMono;font-size:6.9pt;letter-spacing:.03em;color:var(--tag);
  background:var(--tag-bg);padding:1.4px 5px;border-radius:2px;margin-right:3px;white-space:nowrap}
.edu-row{display:flex;justify-content:space-between;gap:10px;margin-bottom:3px}
.edu-row .p{font-family:PlexMono;font-size:7.8pt;color:var(--muted);white-space:nowrap}
"""
    flat = []
    for _, items in CV["skills"]:
        flat += items
    b = [f'<h1 class="name">{e(CV["fullName"])}</h1>',
         f'<p class="headline">{e(CV["headline"])}</p>',
         f'<p class="meta">{contacts_line("   ·   ")}</p>',
         '<div class="hr"></div>',
         f'<p class="summary">{e(CV["summary"])}</p>',
         '<section><h2>Core stack</h2><p class="stack">']
    b.append(" · ".join(e(s) for s in flat[:14]))
    b.append('</p></section><section><h2>Experience</h2>')
    for r in CV["experiences"]:
        b.append(f'<div class="role"><div class="role-head"><div>'
                 f'<span class="role-title">{e(r["role"])}</span> '
                 f'<span class="company">{e(r["company"])}</span></div>'
                 f'<span class="when">{e(r["period"])}</span></div><ul class="bullets">')
        for text, skills in r["bullets"]:
            tags = "".join(f'<span>{e(s)}</span>' for s in skills)
            b.append(f'<li>{e(text)}<span class="ev">{tags}</span></li>')
        b.append('</ul></div>')
    b.append('</section><section><h2>Education</h2>')
    for s, p in CV["education"]:
        b.append(f'<div class="edu-row"><span>{e(s)}</span><span class="p">{e(p)}</span></div>')
    b.append('</section><section><h2>Languages</h2><p class="stack">')
    b.append(" &nbsp;·&nbsp; ".join(f"{e(n)} ({e(lv)})" for n, lv in CV["languages"]))
    b.append('</p></section>')
    return page(f'{CV["fullName"]} — CV (Evidence)', css, "\n".join(b))

os.makedirs(OUT, exist_ok=True)
for name, fn in (("a-register", register), ("b-dossier", dossier), ("c-evidence", evidence)):
    p = os.path.join(OUT, f"{name}.html")
    open(p, "w").write(fn())
    print(f"{name:12} {os.path.getsize(p)/1024:6.0f}KB")

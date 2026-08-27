import base64, os
S = os.path.dirname(os.path.abspath(__file__))
OUT = "/Volumes/my-data/Developer/Projects/job-assistant/target/cv-prototypes"
FONTS = open(os.path.join(S, "fonts.css")).read()

def img(name):
    b = open(os.path.join(OUT, f"{name}.png"), "rb").read()
    return "data:image/png;base64," + base64.b64encode(b).decode()


CHOSEN = [
    ("a-register", "Register", "no photo",
     "The fallback, and not a lesser version of anything. With no portrait the header is one "
     "column and nothing is left behind — no gap, no placeholder, no reserved box."),
    ("a-register-photo", "Register", "with photo",
     "The portrait sits beside the name, headline and summary so it shares their height rather "
     "than setting it. Contacts span the full width underneath, or the photo squeezes them onto "
     "two lines."),
]
REJECTED = [
    ("b-dossier", "Dossier", "Two column, dark sidebar. Looks the most designed and parses the worst."),
    ("c-evidence", "Evidence", "Per-bullet provenance. Its badge idea survives, moved up to per-job."),
]

QUESTIONS = [
    ("Photo",
     "Optional by construction. <code>.head</code> is one column; adding a portrait adds the second "
     "column and nothing else. No image means no column and no reserved space."),
    ("Skill badges",
     "One row per job, not per task — the union of the skills on that role's bullets, in "
     "first-appearance order, so the strongest bullet's stack leads."),
    ("Badges and dropped bullets",
     "The union is taken over the bullets that actually <em>render</em>. A skill whose only "
     "evidence was dropped during tailoring must not survive into the badge row, or the CV claims "
     "something nothing backs."),
    ("Fits one page",
     "All four do, with four roles and ten bullets. The photo costs about 14px — roughly one "
     "bullet — which the tailoring step can pay by dropping one."),
    ("Still guarded",
     "Badges are real <code>&lt;span&gt;</code> text nodes, never <code>::before</code> content, so "
     "<code>HtmlText.visibleText</code> still sees every skill name and <code>CvInvariant</code> "
     "keeps working."),
]

def chip(label, value, tone):
    return f'<span class="chip {tone}"><span class="k">{label}</span><span class="v">{value}</span></span>'

chosen_cards = []
for slug, name, variant, blurb in CHOSEN:
    chosen_cards.append(f"""
<article class="card">
  <a class="shot" href="#{slug}" aria-label="Enlarge {name}, {variant}">
    <img src="{img(slug)}" alt="The {name} layout, {variant}, rendered to A4" loading="lazy">
  </a>
  <div class="card-body">
    <h3>{name} <span class="variant">{variant}</span></h3>
    <p class="blurb">{blurb}</p>
  </div>
</article>""")

rejected_cards = []
for slug, name, blurb in REJECTED:
    rejected_cards.append(f"""
<article class="card small">
  <a class="shot" href="#{slug}" aria-label="Enlarge {name}">
    <img src="{img(slug)}" alt="The {name} layout rendered to A4" loading="lazy">
  </a>
  <div class="card-body">
    <h3>{name}</h3>
    <p class="blurb">{blurb}</p>
  </div>
</article>""")

ALL = [(s, n) for s, n, *_ in CHOSEN] + [(s, n) for s, n, *_ in REJECTED]
lightboxes = "".join(
    f'<div class="lightbox" id="{slug}"><a class="close" href="#top" aria-label="Close">Close</a>'
    f'<img src="{img(slug)}" alt="{name} at full size"></div>'
    for slug, name in ALL)

rows = "".join(f"<tr><th scope='row'>{q}</th><td>{a}</td></tr>" for q, a in QUESTIONS)

HTML = f"""<title>Three CVs, One Page Each</title>
<style>
{FONTS}
:root {{
  --ground:#eef0f3; --panel:#ffffff; --ink:#181b20; --muted:#646c77;
  --rule:#d6dae0; --accent:#2f5d50; --accent-soft:#e7efeb; --warn:#8a5a2b; --warn-soft:#f6ecdf;
  --shadow:0 1px 2px rgba(20,28,36,.09), 0 8px 24px -12px rgba(20,28,36,.22);
}}
@media (prefers-color-scheme: dark) {{
  :root:not([data-theme="light"]) {{
    --ground:#101317; --panel:#181c22; --ink:#e6eaef; --muted:#98a2ad;
    --rule:#2b323b; --accent:#84bda9; --accent-soft:#1b2a26; --warn:#d0a06a; --warn-soft:#2a2115;
    --shadow:0 1px 2px rgba(0,0,0,.5), 0 10px 30px -14px rgba(0,0,0,.7);
  }}
}}
:root[data-theme="dark"] {{
  --ground:#101317; --panel:#181c22; --ink:#e6eaef; --muted:#98a2ad;
  --rule:#2b323b; --accent:#84bda9; --accent-soft:#1b2a26; --warn:#d0a06a; --warn-soft:#2a2115;
  --shadow:0 1px 2px rgba(0,0,0,.5), 0 10px 30px -14px rgba(0,0,0,.7);
}}
*{{box-sizing:border-box}}
body{{margin:0;background:var(--ground);color:var(--ink);
  font-family:PlexSans,system-ui,sans-serif;font-size:16px;line-height:1.6;
  padding:clamp(28px,5vw,64px) clamp(18px,4vw,40px) 96px}}
.wrap{{max-width:1180px;margin:0 auto;display:flex;flex-direction:column;gap:clamp(32px,4vw,56px)}}
.eyebrow{{font-family:PlexMono;font-size:11px;letter-spacing:.18em;text-transform:uppercase;
  color:var(--muted);margin:0 0 10px}}
h1{{font-family:PlexCond;font-weight:600;font-size:clamp(34px,6vw,56px);line-height:1.02;
  letter-spacing:-.015em;margin:0;text-wrap:balance}}
.lede{{max-width:64ch;margin:16px 0 0;color:var(--muted);font-size:17px}}
.lede strong{{color:var(--ink);font-weight:600}}
h2{{font-family:PlexMono;font-size:11px;font-weight:500;letter-spacing:.18em;text-transform:uppercase;
  color:var(--muted);margin:0 0 16px;padding-bottom:8px;border-bottom:1px solid var(--rule)}}
.grid{{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:clamp(16px,2vw,26px)}}
.grid.tight{{grid-template-columns:repeat(auto-fit,minmax(240px,1fr));max-width:720px}}
.card{{background:var(--panel);border:1px solid var(--rule);border-radius:4px;overflow:hidden;
  box-shadow:var(--shadow);display:flex;flex-direction:column}}
.card.small{{opacity:.78}}
.shot{{display:block;background:#fff;border-bottom:1px solid var(--rule);line-height:0}}
.shot img{{width:100%;height:auto;display:block}}
.shot:focus-visible{{outline:2px solid var(--accent);outline-offset:-2px}}
.card-body{{padding:16px 20px 20px;display:flex;flex-direction:column;gap:6px}}
.card h3{{font-family:PlexCond;font-weight:600;font-size:24px;letter-spacing:-.01em;margin:0;
  line-height:1;display:flex;align-items:baseline;gap:8px;flex-wrap:wrap}}
.variant{{font-family:PlexMono;font-size:11px;letter-spacing:.06em;color:var(--accent);font-weight:400}}
.blurb{{margin:0;font-size:14.5px;color:var(--muted)}}
.tablewrap{{overflow-x:auto;border:1px solid var(--rule);border-radius:4px;background:var(--panel)}}
table{{border-collapse:collapse;width:100%;min-width:560px;font-size:14.5px}}
th,td{{text-align:left;vertical-align:top;padding:13px 18px;border-bottom:1px solid var(--rule)}}
tbody th{{font-weight:600;width:26%;color:var(--ink)}}
tbody td{{color:var(--muted)}}
tbody tr:last-child th,tbody tr:last-child td{{border-bottom:0}}
code{{font-family:PlexMono;font-size:12.5px;background:var(--accent-soft);color:var(--accent);
  padding:1px 5px;border-radius:2px}}
.notes{{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:clamp(16px,2vw,26px)}}
.note{{background:var(--panel);border:1px solid var(--rule);border-left:3px solid var(--accent);
  border-radius:4px;padding:18px 20px}}
.note.flag{{border-left-color:var(--warn)}}
.note h3{{margin:0 0 8px;font-size:16px;font-weight:600;letter-spacing:-.005em}}
.note p{{margin:0 0 10px;font-size:14.5px;color:var(--muted)}}
.note p:last-child{{margin-bottom:0}}
.lightbox{{display:none}}
.lightbox:target{{display:flex;position:fixed;inset:0;z-index:50;background:rgba(10,13,17,.93);
  padding:24px;overflow:auto;justify-content:center;align-items:flex-start}}
.lightbox img{{max-width:min(900px,100%);height:auto;background:#fff;box-shadow:0 20px 60px rgba(0,0,0,.5)}}
.close{{position:fixed;top:18px;right:22px;color:#fff;font-family:PlexMono;font-size:12px;
  letter-spacing:.12em;text-transform:uppercase;text-decoration:none;background:rgba(255,255,255,.14);
  padding:8px 14px;border-radius:3px}}
.close:hover{{background:rgba(255,255,255,.24)}}
a{{color:var(--accent)}}
@media (max-width:640px){{tbody th{{width:auto}}}}
</style>

<div class="wrap" id="top">
  <header>
    <p class="eyebrow">Issue 14 · chosen direction</p>
    <h1>Three CVs, one page each</h1>
    <p class="lede"><strong>Register wins</strong>, with two changes: an optional portrait, and the
    skill badges borrowed from Evidence — moved up from per-task to <strong>per job</strong>. Both
    variants below fit a single A4 page with four roles and ten bullets. Click any preview to
    enlarge.</p>
  </header>

  <section>
    <h2>Register, with and without a photo</h2>
    <div class="grid">{''.join(chosen_cards)}</div>
  </section>

  <section>
    <h2>How it answers the two changes</h2>
    <div class="tablewrap"><table><tbody>{rows}</tbody></table></div>
  </section>

  <section>
    <h2>A photo is personal data, so it has a rule</h2>
    <div class="notes">
      <div class="note flag">
        <h3>The portrait must never reach a model</h3>
        <p>A face is a direct identifier — more identifying than the name and email the outbound
        guard already refuses. The rule that keeps it safe is the one the name already follows:
        <strong>the prompt never carries it, and the renderer adds it afterwards from the
        database.</strong></p>
        <p>So the photo belongs on the profile and in the template's model, never in
        <code>ProfileBriefing</code>, <code>TailoredCv</code> or <code>CoverLetter</code>. Tailoring
        chooses words; it has no business seeing a face.</p>
      </div>
      <div class="note flag">
        <h3>Storage and erasure</h3>
        <p>It needs somewhere to live that a profile delete removes, the way
        <code>V11</code> made <code>llm_call</code> cascade from <code>profile</code>. A portrait
        that outlives the profile it belongs to is the same bug that migration fixed.</p>
        <p>Worth knowing rather than deciding here: a photo is normal on a Polish CV and unusual on a
        UK or US one. Optional is the right shape regardless.</p>
      </div>
    </div>
  </section>

  <section>
    <h2>Not chosen</h2>
    <div class="grid tight">{''.join(rejected_cards)}</div>
  </section>

  <section>
    <h2>Still missing, deliberately</h2>
    <div class="notes">
      <div class="note">
        <h3>Projects and courses</h3>
        <p>Neither exists in the profile yet. Both are additional sections in the same rhythm as
        Education — a label rail on the left, content on the right — so neither changes this layout
        when it arrives. <code>Project</code> is what issue 19 decides; credentials are still fog on
        the map.</p>
      </div>
      <div class="note">
        <h3>What the template needs from the view model</h3>
        <p><code>CvView</code> carries <code>skills: List&lt;String&gt;</code> today. This layout
        needs two additions: the catalogue <strong>category</strong> per skill for the rail, and the
        <strong>skills per role</strong> for the badges. Both are already in the database —
        <code>canonical_skill.category</code> and <code>experience_bullet_skill</code>.</p>
      </div>
    </div>
  </section>
</div>
{lightboxes}
"""
p_out = os.path.join(OUT, "comparison.html")
open(p_out, "w").write(HTML)
print(f"{p_out}  {os.path.getsize(p_out)/1024/1024:.2f}MB")

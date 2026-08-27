# CV layout prototypes

Throwaway prototypes built to resolve
[What should a tailored CV look like?](https://github.com/AnielskieOczko/job-assistant/issues/14).
They exist to be *reacted to*, not to be merged — the winning layout gets folded into
`src/main/resources/templates/cv.html`, and this directory can then go.

## Regenerating

```bash
python3 docs/prototypes/cv/build.py              # writes HTML into target/cv-prototypes/
./mvnw test -Ppdf -Dtest=CvPrototypeRenderTest   # renders PDFs and preview PNGs beside them
python3 docs/prototypes/cv/page.py               # builds the side-by-side comparison page
```

`CvPrototypeRenderTest` is deliberately **not** an `@IntegrationTest`: a layout prototype needs
Chromium, not Postgres, and requiring a container would make it un-runnable for the one thing it is
for. It skips silently when `target/cv-prototypes` holds no HTML.

## What is in here

- `fixture.py` — sample data, deliberately fuller than `docs/sample-profile.json` so page breaks are
  actually exercised. A layout that looks right on half a page has not been tested.
- `build.py` — emits the four renders from that one fixture: **Register** with and without a
  portrait (the chosen direction), plus Dossier and Evidence, which were not chosen.
- `page.py` — assembles the previews into the comparison page.
- `fonts.css` — IBM Plex Sans, Sans Condensed and Mono, base64-embedded, **latin + latin-ext**.

## Two findings that outlive the prototypes

**Fonts have to be embedded, and the subset has to include latin-ext.** Chromium embeds into the PDF
whatever font it resolved *at render time*, so the risk is not the reader lacking the font — it is the
rendering machine lacking it and silently falling back. Base64 in the stylesheet removes that. And the
`latin` subset does not carry ł, ą, ę, ś or ż, so a Polish CV rendered from it falls back mid-word.
`docs/sample-profile.json` alone would never have caught this; the fixture uses Poznań, Gdańsk,
Wrocław and Uniwersytet Adama Mickiewicza on purpose.

**Preview at the print width or the preview is a different document.** A4 is 794 × 1123 CSS px. An
early version of the render test screenshotted at 1240px wide, which wrapped the text differently from
the PDF and showed one page where the PDF had two.

## The chosen direction

**Register**, with two changes Rafal asked for on the issue.

**An optional portrait.** `.head` is a one-column grid; a photo adds the second column and nothing
else, so the no-photo case leaves no gap and reserves no space. The summary sits *inside* the header
grid so the portrait shares its height rather than setting it alone, and the contact line spans the
full width underneath — without that, the photo squeezes it onto two lines and the page overflows.
A photo costs roughly 14px, about one bullet.

**Skill badges per job, not per task.** Evidence tagged every bullet; Register takes the union of a
role's bullet skills in first-appearance order and shows one row per job. The union is deliberately
taken over the bullets that actually *render*: a skill whose only evidence was dropped during
tailoring must not survive into the badge row, or the CV claims something nothing backs.

Badges are real `<span>` text nodes, never `::before` content, so `HtmlText.visibleText` still sees
every skill name and `CvInvariant` keeps working. `CvPrototypeRenderTest` asserts that.

## What this needs from the view model

`CvView` carries `skills: List<String>` today. This layout needs the catalogue **category** per skill
for the rail, and the **skills per role** for the badges. Both already exist in the database, as
`canonical_skill.category` and `experience_bullet_skill`.

## Not yet in the template, on purpose

Projects and courses. Neither exists in the profile, and both are additional sections in the same
rhythm as Education — a label rail left, content right — so neither disturbs this layout when it
lands. `Project` is what issue #19 decides; credentials are still fog on the map.

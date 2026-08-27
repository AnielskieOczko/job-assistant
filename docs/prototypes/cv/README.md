# CV layout prototypes

Throwaway prototypes built to resolve
[What should a tailored CV look like?](https://github.com/AnielskieOczko/job-assistant/issues/14).
They exist to be *reacted to*, not to be merged — the winning layout gets folded into
`src/main/resources/templates/cv.html`, and this directory can then go.

## Regenerating

```bash
python3 docs/prototypes/cv/build.py          # writes HTML into target/cv-prototypes/
./mvnw test -Ppdf -Dtest=CvPrototypeRenderTest   # renders PDFs and preview PNGs beside them
```

`CvPrototypeRenderTest` is deliberately **not** an `@IntegrationTest`: a layout prototype needs
Chromium, not Postgres, and requiring a container would make it un-runnable for the one thing it is
for. It skips silently when `target/cv-prototypes` holds no HTML.

## What is in here

- `fixture.py` — sample data, deliberately fuller than `docs/sample-profile.json` so page breaks are
  actually exercised. A layout that looks right on half a page has not been tested.
- `build.py` — emits the three layouts from that one fixture.
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

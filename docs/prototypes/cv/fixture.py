# Throwaway fixture for CV layout prototypes. Deliberately fuller than docs/sample-profile.json
# so page breaks are exercised: a layout that looks right on half a page is not yet tested.
CV = {
    "fullName": "Alex Novak",
    "headline": "Senior Backend Engineer — Kotlin / Spring Boot",
    "summary": "Backend engineer with eight years on the JVM, building payment and reconciliation "
               "services that have to be right rather than merely fast. Works in explicit module "
               "boundaries, deterministic tests and Postgres.",
    "contacts": ["alex.novak@example.com", "+48 601 234 567", "Poznań, Poland"],
    "links": [("GitHub", "github.com/alexnovak"), ("LinkedIn", "linkedin.com/in/alexnovak")],
    "skills": [
        ("LANGUAGE",  ["Kotlin", "Java", "SQL", "Python"]),
        ("FRAMEWORK", ["Spring Boot", "Spring Data", "Quarkus", "Hibernate"]),
        ("DATABASE",  ["PostgreSQL", "Redis", "Flyway"]),
        ("MESSAGING", ["Kafka", "RabbitMQ"]),
        ("CLOUD",     ["AWS", "Docker", "Kubernetes"]),
        ("TESTING",   ["JUnit 5", "Testcontainers", "MockK"]),
        ("PRACTICE",  ["Domain-driven design", "Event sourcing", "Trunk-based development"]),
    ],
    "experiences": [
        {"company": "Nordkraft Payments", "role": "Senior Backend Engineer",
         "period": "Mar 2021 — present", "location": "Poznań · hybrid",
         "bullets": [
            ("Designed and shipped a payment reconciliation service settling 2M transactions a day, "
             "cutting the manual finance close from three days to four hours.",
             ["Kotlin", "Spring Boot", "PostgreSQL"]),
            ("Split a 400k-line monolith into six deployable services behind an event backbone, "
             "with no scheduled downtime during the migration.",
             ["Kafka", "Docker", "Domain-driven design"]),
            ("Cut p99 settlement latency from 4.2s to 380ms by replacing per-row lookups with a "
             "batched projection, measured against production traffic replay.",
             ["PostgreSQL", "Redis"]),
            ("Introduced Testcontainers across the estate, which ended a class of failures that "
             "only ever appeared in staging.",
             ["Testcontainers", "JUnit 5"]),
         ]},
        {"company": "Baltic Logistics Group", "role": "Backend Engineer",
         "period": "Sep 2018 — Feb 2021", "location": "Gdańsk · on-site",
         "bullets": [
            ("Built the carrier-integration layer that onboarded 40 freight partners onto one API, "
             "replacing a per-partner CSV pipeline.",
             ["Java", "Spring Boot", "RabbitMQ"]),
            ("Owned the migration from Oracle to PostgreSQL for the tracking domain — 1.4TB moved "
             "with a dual-write cutover and no lost events.",
             ["PostgreSQL", "Flyway"]),
            ("Wrote the on-call runbook and led the rotation for a team of nine.",
             ["Trunk-based development"]),
         ]},
        {"company": "Sygnal Software House", "role": "Java Developer",
         "period": "Jul 2016 — Aug 2018", "location": "Wrocław · on-site",
         "bullets": [
            ("Delivered six client projects on Spring Boot, two of which are still in production.",
             ["Java", "Spring Boot", "Hibernate"]),
            ("Automated the release pipeline, taking deployments from a manual evening job to a "
             "fifteen-minute pipeline anyone on the team could run.",
             ["Docker"]),
         ]},
        {"company": "Uniwersytet Adama Mickiewicza", "role": "Research Assistant (part-time)",
         "period": "Oct 2015 — Jun 2016", "location": "Poznań",
         "bullets": [
            ("Maintained the simulation toolchain used by the numerical methods group.",
             ["Python"]),
         ]},
    ],
    "education": [
        ("MSc in Computer Science, Poznań University of Technology", "2014 — 2016"),
        ("BSc in Computer Science, Poznań University of Technology", "2011 — 2014"),
    ],
    "languages": [("Polish", "native"), ("English", "C1"), ("German", "B1")],
}

# A neutral stand-in for an uploaded portrait. Deliberately not a real face: the prototype has to
# show the layout with an image in it, and no real person belongs in a fixture.
PHOTO_PLACEHOLDER = (
    "data:image/svg+xml;utf8,"
    "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 200 260'>"
    "<rect width='200' height='260' fill='%23dde2e8'/>"
    "<circle cx='100' cy='96' r='42' fill='%23b3bcc7'/>"
    "<path d='M28 260c0-44 32-74 72-74s72 30 72 74z' fill='%23b3bcc7'/>"
    "</svg>"
)


def role_stack(role):
    """Skills for a whole job, not a single task: the union of what its bullets evidence.

    Order is first-appearance, so the technology the strongest bullet rests on leads. Taking the
    union of the *rendered* bullets matters — a skill whose only evidence was dropped during
    tailoring must not survive into the badge row, or the CV claims something nothing backs.
    """
    seen = []
    for _, skills in role["bullets"]:
        for s in skills:
            if s not in seen:
                seen.append(s)
    return seen

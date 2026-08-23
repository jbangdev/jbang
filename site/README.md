# Puppy website

Roq-based static site for `https://puppy.xam.dk`.

## Local development

```bash
cd site
mvn quarkus:dev
```

## Static export

```bash
cd site
QUARKUS_ROQ_GENERATOR_BATCH=true mvn -B package quarkus:run -DskipTests
```

The generated site is written to `site/target/roq` and deployed by `.github/workflows/website.yml` when the `website` branch is pushed.

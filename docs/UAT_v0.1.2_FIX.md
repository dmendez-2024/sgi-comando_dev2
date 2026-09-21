# UAT v0.1.2 fix

Backend Docker packaging fix:
- binds `quarkus:build`, `generate-code`, and `generate-code-tests` to the Maven lifecycle;
- explicitly packages Quarkus as `fast-jar` for the runtime Docker image;
- fixes missing `target/quarkus-app/{lib,app,quarkus}` during Docker multi-stage COPY.

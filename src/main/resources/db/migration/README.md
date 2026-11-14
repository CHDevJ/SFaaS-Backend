# Flyway migration rules

Database schema changes must be committed as a new migration file.

- Start with `V1__init_schema.sql` after the ERD is approved.
- Never edit a migration that has already been merged or executed by another developer.
- Use the next unused version, for example `V2__create_equipment.sql`.
- One pull request should contain only the migrations required by that feature.
- Do not create tables manually in the shared database.

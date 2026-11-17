# STF backend collaboration rules

## Branches

- `main`: final and demo-ready code only
- `backend`: integration branch
- `feature/SF5-번호-기능명`: new work
- `fix/SF5-번호-기능명`: bug fixes

Examples:

```text
feature/SF5-21-auth
feature/SF5-22-measurement-ingestion
fix/SF5-35-alarm-state
```

## Pull requests

1. Create a branch from the latest `backend`.
2. Keep one Jira issue or one feature in one branch.
3. Run `./gradlew test` before pushing.
4. Open a pull request into `backend`.
5. Merge only after one teammate reviews it and CI succeeds.
6. Do not force-push or commit directly to `main`.

## API changes

Update `docs/openapi.yaml` first whenever a method, URL, field, Enum or error response changes. Frontend and backend developers review the same contract before implementation.

## Database changes

Create a new Flyway file under `src/main/resources/db/migration`. Never edit a migration already shared with the team.

## Secrets

Never commit `.env`, `application-local.properties`, passwords, API keys or tokens. Commit only safe examples such as `.env.example`.

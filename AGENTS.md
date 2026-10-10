# Agent guidelines

## Branches and commits

Use [Conventional Commits](https://www.conventionalcommits.org/) style for both branch names and commit messages.

Types:

- `feat` — a new user-facing feature
- `fix` — a bug fix
- `chore` — maintenance: version bumps, dependencies, build config, cleanup
- `refactor` — code change that neither fixes a bug nor adds a feature
- `docs` — documentation only
- `test` — adding or updating tests

### Branch names

`<type>/<short-kebab-description>`, for example:

- `feat/bottom-navigation`
- `fix/background-app-detection`
- `chore/bump-0.5.0`

### Commit messages

`<type>(<optional scope>): <imperative summary>`, lowercase, no trailing period, summary under ~72 characters. Add a body after a blank line when the why isn't obvious.

```
feat(settings): add dark theme
fix: detect apps brought back from the background
chore: bump version to 0.5.0
```

Mark breaking changes with `!` after the type (e.g. `feat!: ...`) and explain them in the body.

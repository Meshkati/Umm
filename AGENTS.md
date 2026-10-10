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

## Tests

### Suites

There are two suites:

- **Local** (`app/src/test/`, `./gradlew test`): runs on the JVM and should pass on every change. Nearly all tests belong here. Use plain JUnit 4 for pure Kotlin code, and Robolectric when the code needs Android (`Context`, `SharedPreferences`, files) or a Compose screen.
- **Device** (`app/src/androidTest/`, `./gradlew connectedAndroidTest`): a handful of UI Automator smoke tests on an emulator, run before releases. Only for what the JVM can't check, such as real accessibility events from other apps.

### Scenarios need approval

Don't write a test scenario the user hasn't approved. Before writing tests, propose the scenarios as a list, one plain-language line each, and wait for the user to approve, change or drop them. Write only the approved ones. If a new scenario comes up while writing, fixing a bug or refactoring, propose it instead of adding it.

### Writing tests

- Test behaviour: what a caller or the user can observe, not how the code gets there. No BDD framework; mark the parts with `// given`, `// when` and `// then` comments.
- One scenario per test, so a failing test names exactly what broke. Name the test after its scenario.
- Assert only what the scenario is about. Don't assert incidental details (exact copy, call order, internal state) that would break on changes that aren't bugs.

```kotlin
@Test
fun weekEndsOnToday() {
    // given
    val today = LocalDate.of(2026, 10, 11)

    // when
    val summary = summarize(emptyList(), Period.WEEK, ZoneOffset.UTC, today)

    // then
    assertEquals(today, summary.buckets.last().start)
}
```

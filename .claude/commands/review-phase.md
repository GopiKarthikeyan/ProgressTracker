Review Phase $ARGUMENTS of this Android project. Do NOT modify any files. This is a review only.

Context: read docs/SPEC.md, .cursor/rules/project.mdc, and the "Phase $ARGUMENTS" section of docs/REVIEW_CHECKLISTS.md. The code under review is everything changed since the last commit (git status, git diff HEAD) plus any new files.

Steps:
1. Run ./gradlew assembleDebug testDebugUnitTest lintDebug and report the results.
2. Build a requirements table from the Phase $ARGUMENTS prompt in SPEC.md and the checklist: requirement | Done / Partial / Missing | file reference.
3. Check every rule in .cursor/rules/project.mdc and report each violation.
4. Look for: correctness bugs, coroutine/lifecycle leaks, Room or disk I/O on the main thread, swallowed exceptions, deprecated APIs, hardcoded exercise names or user-facing strings, exported components that shouldn't be, network use outside WeatherRepository, and any device-manufacturer-specific code.
5. Judge the tests: meaningful assertions, edge cases covered, no tests that just mirror the implementation.

Output:
- Findings grouped as Blocker / Major / Minor, each with file:line, what's wrong, why it matters, and the fix.
- Verdict: READY TO COMMIT or FIX FIRST.
- If FIX FIRST: a numbered fix list written as a single prompt I can paste straight into Cursor.

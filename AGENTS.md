# Agent Contract

1. Read `docs/AGENT_USER_README.html` by indexed intent, not as a giant prompt.
2. Never place provider secrets in projects, prompts, reports, image metadata, logs or docs.
3. Validate provider/model capabilities before constructing a request.
4. Treat originals and upstream prompts as immutable. Create a derived asset/node with a new ID.
5. Every automated generation requires an explicit finite `JobBudget`.
6. Do not resume interrupted paid jobs automatically after restart.
7. New capability = implementation + tests + agent index + user documentation.
8. ORML/local-model output is derived metadata or a child asset; it never overwrites the source.
9. Preserve prompt provenance fields through import/copy/export.
10. Offworld visual regression rule: no SaaS-blue, neon/purple AI gradients, rounded-card language, or decorative provider colors.

11. Agent Build must display/confirm a finite Director plan before execution, retrieve only indexed local documentation context, and persist the plan ID plus consulted capability IDs with generated work.
12. Cancellation is terminal for the active job result: a provider response arriving after cancellation must not be admitted to lineage.

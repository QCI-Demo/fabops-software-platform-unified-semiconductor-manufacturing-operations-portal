# GitLab hosting & branch protection

This content is the `telemetry-schema-registry` schema registry. Deploy it as a GitLab project and protect `main` as follows.

## Create the GitLab project

1. In GitLab, create a new project named **`telemetry-schema-registry`**.
2. Push this repository (or mirror from the FabOps GitHub remote) to that project.
3. Confirm `.gitlab-ci.yml` is present at the repo root so pipelines run on push.

## Enable branch protection for `main`

In **Settings → Repository → Protected branches**:

| Setting | Value |
|---------|-------|
| Branch | `main` |
| Allowed to merge | Maintainers (or merge via MR only) |
| Allowed to push | No one (or Maintainers only) |
| Require approval | Recommended: ≥1 |
| Pipeline must succeed | Required (`avro-tools-lint` job) |

## Initial version tag

After the initial schemas and docs land on `main`, create annotated tag **`v1.0`**:

```bash
git tag -a v1.0 -m "Initial telemetry event schema registry release"
git push origin v1.0
```

The CI pipeline also runs on tags (`avro-tools-lint`).

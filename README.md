# connectCG_BE

## Production deployment

The VPS never builds application source. GitHub Actions verifies the backend and publishes `latest`, `sha-<commit>`, and `v*` release tags to GHCR.

On the backend VPS:

```bash
chmod +x deploy.sh
./deploy.sh
```

The terminal menu creates `.env.production` with permission `600`, then pulls and starts every required image. Choose either PostgreSQL Docker or a managed PostgreSQL provider. Local PostgreSQL, Redis, and MinIO data live in named Docker volumes; deploy and rollback never remove those volumes.

For a private GHCR package, log in once before deploying:

```bash
echo "$GHCR_TOKEN" | docker login ghcr.io -u YOUR_GITHUB_USER --password-stdin
```

Put Caddy or Nginx on the host in front of the ports bound to `127.0.0.1`:

```caddyfile
api.example.com {
    reverse_proxy 127.0.0.1:8080
}

media.example.com {
    reverse_proxy 127.0.0.1:9000
}
```

### Managed PostgreSQL and Supabase

For Supabase, paste the JDBC Session Pooler URL on port `5432` and append `sslmode=require`. Use the project-specific pooler username shown in the Supabase Connect dialog. Do not use the transaction pooler on port `6543` as the Hibernate datasource. The TUI defaults managed databases to a private `connectcg` schema so application tables are not placed in Supabase's API-exposed `public` schema.

Before a migration deploy, create a provider snapshot. If supported, restrict database ingress to the backend VPS IP. This application continues to use its own Spring Security authentication; Supabase is only the PostgreSQL host.

See [Supabase database connections](https://supabase.com/docs/guides/database/connecting-to-postgres) and [Supabase with Spring Boot](https://supabase.com/docs/guides/getting-started/quickstarts/spring-boot).

## Local development data

Docker Compose runs the PostgreSQL schema migrations from `db/postgresql` and the development-only sample migration from `db/postgresql-dev`.

The demo password is `password123` for every account:

- `admin`
- `john_doe`
- `jane_smith`
- `bob_wilson`
- `alice_brown`
- `charlie_davis`

The V100 migration creates profiles, hobbies, avatars, friendships, groups, posts, comments, reactions, and chat rooms. Tests and the default non-Docker application configuration only load the core PostgreSQL migrations, so test databases remain isolated from demo data.

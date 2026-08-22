# connectCG_BE

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

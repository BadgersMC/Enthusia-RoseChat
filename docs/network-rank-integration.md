# Network-owned Discord rank integration

## Spec

### REQ-NET-001 - Linked identity
WHEN DiscordSRV resolves a Minecraft account link THE SYSTEM SHALL render the existing Minecraft prefix and nickname after a gold [D] marker.

### REQ-NET-002 - Unlinked identity
IF no Minecraft account link exists THEN THE SYSTEM SHALL retain a neutral Discord nickname without granting a Minecraft rank identity.

### REQ-NET-003 - Delivery boundary
THE SYSTEM SHALL leave production files, configuration and processes unchanged during source reconciliation.

## Task and evidence

- Integration starts from fetched canonical BadgersMC/Enthusia-RoseChat master 0f88bbd, the repository owned by the network's RoseChat gitlink. Existing wsg138 PR #21 source at c272610f is not automatically included in that pin.
- Preserve the two reviewed candidate commits via cherry-pick; no unrelated changes from the dirty development checkout are copied. This is brownfield source integration, not newly invented historical test-first evidence.
- Prove: retain the candidate's linked/unlinked/builder-reuse behavioral tests and its default-resource contract. Existing account-link lookup is the identity authority, not Discord roles or message text. No project-local SPEAR state/EARS helpers exist; use this scoped requirement/task record and the existing external EARS validator.
- Engine/arch: the DiscordSRV adapter supplies only identity placeholders. Existing rank resolver, channel routing, permission gates, provider API and persistent data remain unchanged. Default formatting changes do not overwrite installed configurations; live migration requires separate authorization.
- Refine: canonical clean test/shadowJar passes 133 tests with zero failures/errors/skips. Packaging contains exactly 20 staff API class entries and no server/consumer/LumaGuilds-owned provider classes. External EARS and diff checks pass. Hosted checks and linked online/offline/unlinked client acceptance are independent gates. Keep the monorepo pin unchanged until canonical merge and verified combined build.

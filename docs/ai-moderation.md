# OpenAI chat moderation

RoseChat can optionally classify player-originated public chat with OpenAI's moderation endpoint while preserving chat availability when the remote service is slow or unavailable.

## Goals

- Use `omni-moderation-latest` for public player chat.
- Never block the Minecraft chat pipeline on an external request for more than 300 ms.
- Fail open on timeout, network failure, rate limiting, authentication failure, malformed responses, or circuit-breaker open state.
- Preserve enough recent channel context to distinguish ordinary Minecraft combat language from targeted abuse.
- Treat OpenAI category scores as signals. RoseChat's Minecraft-specific policy decides whether to allow, review, or delete a message.
- Notify the sender when an enforcement-level flag blocks or deletes a message.
- Count only enforcement-level flags as strikes. Two strikes in a rolling hour request a 30-day public mute from EnthusiaStaff.
- Warn staff on every login while moderation is configured/enabled but unhealthy.

## Context model

Each initial moderation request carries two independent text inputs:

1. the target message by itself;
2. a bounded channel transcript containing recent messages before the target and the target itself.

The transcript uses explicit target markers and metadata, including the target index and whether the target is currently at the start, middle, or end of the available window. Keeping the target-only input separate prevents harmful text in neighboring messages from being attributed directly to the target.

A short follow-up context window may be used for ambiguous messages after later chat arrives. The target remains explicitly marked and can therefore move from `END` to `MIDDLE`/`START`. Follow-up context is corroborating evidence only; neighboring content alone must not create a strike for an otherwise clean target.

## Minecraft-specific policy

The endpoint's top-level `flagged` value is not the deletion switch. In particular, generic `violence` is heavily discounted because normal gameplay includes language such as `I killed him`, `die`, `fight me`, and `I'm going to kill you` in an in-game context.

More weight is given to targeted harassment, threatening harassment, hate, threatening hate, self-harm instructions, and graphic violence. Thresholds are configuration values and should be tuned in shadow mode before production enforcement.

A player strike is created only when RoseChat's policy returns `DELETE`, not merely because OpenAI reports `flagged=true` or a high generic-violence score.

## Timing

For a player-originated public message:

1. RoseChat's local checks and message rules run first.
2. The moderation request starts asynchronously.
3. If an enforcement decision arrives before 300 ms, RoseChat applies it before broadcast.
4. If no decision is available at 300 ms, RoseChat broadcasts normally.
5. A later enforcement decision deletes the already-published message where supported and notifies the sender.

An atomic message lifecycle prevents a response racing the timeout from broadcasting or enforcing twice.

## Failure behavior

Moderation is a soft subsystem. It has no authority to take chat down.

Repeated remote failures open a circuit breaker. While open, messages are sent without AI moderation and periodic probes are allowed to determine when service has recovered. Staff with the AI moderation status permission are warned on login while the subsystem is degraded/down.

## EnthusiaStaff integration

RoseChat does not dispatch punishment command strings. An optional integration contract is used so EnthusiaStaff remains the punishment authority. RoseChat reports enforcement-level strikes with an idempotency key and moderation metadata; EnthusiaStaff owns the durable strike history and the resulting 30-day public mute when the second strike occurs inside the rolling one-hour window.

If EnthusiaStaff is absent or the integration is unavailable, chat moderation still works. RoseChat records/alerts that automatic sanction escalation is unavailable rather than substituting another punishment implementation.

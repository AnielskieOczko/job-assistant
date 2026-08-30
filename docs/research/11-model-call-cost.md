# Can this app know the actual cost of each model call?

Research for [issue #11](https://github.com/AnielskieOczko/job-assistant/issues/11), part of the
roadmap map (#9). Blocks #15.

## Status: implemented, with two corrections

Built on 2026-08-30. The verdict below held: cost arrives inline, the raw body already reaches
`AuditingChatModelListener`, and capturing it was a cast and a permissive JSON read. Two findings
did **not** survive a re-check of OpenRouter's docs on the day of implementation, and are corrected
here rather than edited away, because the reasoning that produced them is still worth reading.

1. **`cost_details.upstream_inference_cost` is BYOK-only.** The API reference now states it "is only
   available for BYOK requests. For all other requests it will be 0 or null." The
   `upstream_cost_usd` column proposed under *Recommended column shape* would therefore be null on
   every row this application writes, so **it was not added**. The other three columns were, plus
   `finish_reason`, `provider_call_id` and a `subject_kind`/`subject_id` pair — see `V24`.
2. **`GET /api/v1/credits` needs a *management* key** and answers 403 to an inference key, so the
   account-reconciliation idea this research gestured at could not have used it.
   **`GET /api/v1/key`** is the inference-key equivalent and is the better fit anyway: it describes
   the key doing the calling, and reports `usage`, `usage_daily`, `usage_monthly`, `limit` and
   `limit_remaining`. That is what `/api/llm/spend/account` calls.

A third point the research flagged as unverifiable is now routed around rather than resolved.
Whether `usage.cost` is denominated in dollars or in OpenRouter "credits" is still not stated
anywhere in their docs — but `/api/v1/key` reports in the *same* unit, so the comparison the
dashboard draws is valid either way, and nothing in the application ever converts the number.

One thing this research did not anticipate, and which shaped the design more than anything in it:
`llm_call` is purged after thirty days and cascade-deleted with its profile, so it cannot hold an
accumulated total at all. That is why `V25` adds `llm_spend_daily`.

## Verdict

Yes. OpenRouter returns the actual charged cost, in USD, inside the ordinary synchronous chat
completion response — `usage.cost` — with no request flag needed; Requesty does the same with
`usage.cost`. **Recommended route: read `usage.cost` (and its cache/reasoning breakdown) out of the
raw HTTP response body that LangChain4j already receives but currently discards**, because
LangChain4j's parsed `ChatCompletionResponse`/`Usage` types have no `cost` field at all and silently
drop it. The data reaches `AuditingChatModelListener` today — `ChatModelResponseContext.chatResponse()
.metadata()` is, at runtime, an `OpenAiChatResponseMetadata` carrying an unconditionally-populated
`rawHttpResponse()` with the full provider JSON — so no new seam is required, only a cast and a
second, permissive JSON parse inside the existing listener.

## OpenRouter

Primary sources: <https://openrouter.ai/docs/use-cases/usage-accounting>,
<https://openrouter.ai/docs/api-reference/get-a-generation>, and the live unauthenticated
`GET https://openrouter.ai/api/v1/models` response (fetched 2026-08-26).

**Mechanism 1 - cost on the completion response itself (recommended).** The usage-accounting docs
state plainly: "Full usage details are now always included automatically in every response" and
that the older `usage: { include: true }` and `stream_options: { include_usage: true }` request
parameters "are deprecated and have no effect." No special request flag is needed for a
non-streaming call, which is all this app makes. The response's `usage` object carries, verbatim per
the same page:

- `usage.cost` - total amount charged to the account for this generation, in the same unit the
  dashboard uses (OpenRouter calls it "credits"; in practice this is USD-denominated for API
  billing, but the docs page itself never states a currency symbol - see "what I could not verify").
- `usage.cost_details.upstream_inference_cost` - what the upstream provider actually charged
  OpenRouter, when different from what OpenRouter charged the caller (e.g. a discounted or
  promotional rate on top).
- `usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens` - the OpenAI-shaped counts
  LangChain4j already captures.
- `usage.prompt_tokens_details.cached_tokens` / `.cache_write_tokens` - see the cached-tokens
  section below.
- `usage.completion_tokens_details.reasoning_tokens` - see the reasoning-tokens section below.

For streaming, the same page says the usage object arrives "in the last SSE message"; irrelevant
here since `OpenAiChatModel` (non-streaming) is what `OpenAiCompatibleChatModelRegistry` builds
(`src/main/kotlin/com/jankowski/rafal/jobassistant/llm/internal/OpenAiCompatibleChatModelRegistry.kt:41`).

**Mechanism 2 - `GET https://openrouter.ai/api/v1/generation?id=<id>` follow-up lookup.** Documented
at <https://openrouter.ai/docs/api-reference/get-a-generation>. Response fields include `total_cost`,
`upstream_inference_cost`, `tokens_prompt`, `tokens_completion`, `native_tokens_cached`,
`native_tokens_reasoning`, `provider_name`, `cache_discount`, and timing fields (`latency`,
`generation_time`, `moderation_latency`). This is strictly a superset check on the same data the
completion response already carries, plus `provider_name` (which provider actually served the
request) that is not reliably present on the completion response itself (see below). **No documented
propagation delay was found** on this page or via search of OpenRouter's own docs and support
articles - I could not confirm or rule out a lag before a generation id becomes queryable. Given
mechanism 1 already delivers cost synchronously, this app has no reason to depend on mechanism 2 for
the guardrail; it would only be worth adding later, opportunistically, to backfill `provider_name`
for display.

**Mechanism 3 - `GET https://openrouter.ai/api/v1/models` pricing table.** Confirmed live (curl,
2026-08-26, no auth required): each entry has a `pricing` object with string-encoded USD-per-single-
-token rates, e.g. `"pricing":{"prompt":"0.000000075","completion":"0.00000025","input_cache_read":
"0.000000015"}` for one model, and a `"web_search":"0.0025"` per-search charge on another. The
OpenAPI schema (via docs fetch) additionally documents `pricing.request`, `pricing.image`,
`pricing.audio`, `pricing.image_output`, `pricing.input_cache_write`, `pricing.input_cache_write_1h`,
and `pricing.internal_reasoning`. Units are USD per single token, not per million - confirmed by the
example value against the model's known list price - so any local computation must not divide by
1,000,000 a second time. This table would let the app estimate cost locally from token counts alone
without a network round-trip per call, but it is strictly worse than mechanism 1 for this app: it
requires keeping a cached copy of pricing per model, doesn't capture provider-side discounts or
promotional pricing (`cost_details.upstream_inference_cost` can differ from list price), and adds a
second source of truth to keep in sync. It is useful only as a fallback if a provider profile is ever
pointed at a raw OpenAI-compatible endpoint that doesn't echo cost at all (Ollama, LM Studio - see
"what else is worth recording").

**Top-level `provider`.** The chat-completion response schema (fetched from OpenRouter's docs,
2026-08-26) lists `id`, `choices`, `created`, `model`, `object`, `system_fingerprint`, `usage`,
`service_tier`, and an `openrouter_metadata` object, but *no* top-level `provider` string - the docs
note "does not include a separate top-level `provider` field in the ChatResult schema" and that
provider routing detail lives in `openrouter_metadata.endpoints`, gated behind an opt-in header this
research did not chase down. `provider_name` is confirmed only on the `/generation` follow-up
lookup. Treat "which provider served this call" as a nice-to-have deferred to that lookup, not as
something guaranteed on the synchronous path.

## Requesty

Primary sources: <https://docs.requesty.ai/api-reference/endpoint/chat-completions-create>,
<https://docs.requesty.ai/features/cost-tracking>, <https://docs.requesty.ai/api-reference/endpoint/models-list>
(all fetched 2026-08-26).

Requesty's mechanism is the same shape as OpenRouter's mechanism 1, and is simpler: the response
schema documents `usage.cost` directly as "Requesty's USD cost for this request. Returned by default
on non-streaming responses" - explicitly a Requesty extension bolted onto the standard OpenAI `usage`
object, present with **no opt-in parameter** for the non-streaming calls this app makes. Streaming
would require `stream_options: {"include_usage": true}`, irrelevant here. The same `usage` object
carries `prompt_tokens_details.cached_tokens` and `completion_tokens_details.reasoning_tokens`,
matching OpenAI's own shape.

The cost-tracking page adds that this per-request figure "is the same number that's aggregated in
the dashboard," i.e. it is Requesty's own billed figure, not a client-side estimate - but the page
does not say whether that figure is itself sourced from the upstream provider's real invoice or from
Requesty's own price table, and does not mention any propagation delay. That distinction did not
surface in any fetched page, so mark it **UNVERIFIED**.

Requesty also documents a `GET /v1/models` (`models-list`) endpoint with per-model pricing fields
`input_price`, `output_price`, `caching_price`, `caching_5m_price`, `caching_1h_price`,
`cached_price`, all USD per single token - the same local-computation fallback role OpenRouter's
`/models` plays, with the same caveats.

## The LangChain4j seam - does the data reach `AuditingChatModelListener` today?

This is the crux, and it was verified against the actual 1.19.0 jars and sources (not docs of
another version): `~/.m2/repository/dev/langchain4j/langchain4j-open-ai/1.19.0/langchain4j-open-ai-1.19.0.jar`
and its matching `-sources.jar`, plus `langchain4j-core-1.19.0.jar`, inspected with `javap` and by
extracting the sources jar.

**Yes, the raw provider JSON reaches the listener today - the fix is a cast, not a new seam.**

`AuditingChatModelListener.onResponse` (`src/main/kotlin/com/jankowski/rafal/jobassistant/llm/internal/AuditingChatModelListener.kt:34`)
currently does:

```kotlin
val usage = responseContext.chatResponse().metadata().tokenUsage()
```

`ChatResponse.metadata()` is declared to return the base `dev.langchain4j.model.chat.response
.ChatResponseMetadata`, and that class exposes only `id()`, `modelName()`, `tokenUsage()` (declared
return type `dev.langchain4j.model.output.TokenUsage`, with only `inputTokenCount()`,
`outputTokenCount()`, `totalTokenCount()`) and `finishReason()`. Nothing about cost or cache/reasoning
breakdown lives on the base type. But `OpenAiChatModel.doChat` (module `langchain4j-open-ai`,
`OpenAiChatModel.java`) always builds and returns an `OpenAiChatResponseMetadata` instance, not the
base class - confirmed in the sources:

```java
OpenAiChatResponseMetadata responseMetadata = OpenAiChatResponseMetadata.builder()
        .id(openAiResponse.id())
        .modelName(openAiResponse.model())
        .tokenUsage(tokenUsageFrom(openAiResponse.usage()))
        .finishReason(finishReasonFrom(openAiResponse.choices().get(0).finishReason()))
        .created(openAiResponse.created())
        .serviceTier(openAiResponse.serviceTier())
        .systemFingerprint(openAiResponse.systemFingerprint())
        .rawHttpResponse(parsedAndRawResponse.rawHttpResponse())   // <-- always set, unconditionally
        .logProbs(logProbsFrom(openAiResponse.choices().get(0).logprobs()))
        .build();
```

`OpenAiChatResponseMetadata` (subclass) adds `created()`, `serviceTier()`, `systemFingerprint()`,
`logProbs()`, and critically **`rawHttpResponse()`, returning `dev.langchain4j.http.client
.SuccessfulHttpResponse`**, which exposes `statusCode()`, `headers()`, and `body(): String` - the
exact bytes the provider sent back, untouched by LangChain4j's Jackson mapping. This field is
populated on every call, not behind any builder flag: `OpenAiChatModel.Builder` has no toggle to
disable it (checked via `javap` on `OpenAiChatModel$Builder` - no `raw`/`http`/`response` setters).

Why doesn't `cost` already show up in the typed `usage`? Because it was never going to: LangChain4j's
internal `dev.langchain4j.model.openai.internal.shared.Usage` class (what `openAiResponse.usage()`
deserializes into) only declares `totalTokens`, `promptTokens`, `promptTokensDetails.cachedTokens`,
`completionTokens`, `completionTokensDetails.reasoningTokens` - no `cost` field exists on the class at
all, so Jackson has nowhere to put it. And unknown JSON properties don't even error out: both
`ChatCompletionResponse` and `Usage` carry `@JsonIgnoreProperties(ignoreUnknown = true)` (confirmed in
their sources), and the shared `ObjectMapper` (`internal/Json.java`) only disables
`FAIL_ON_IGNORED_PROPERTIES`, not `FAIL_ON_UNKNOWN_PROPERTIES` - the class-level annotation is what
keeps parsing from throwing on `cost`, `cost_details`, or a top-level `provider` string, and those
properties are then dropped on the floor. This is a deliberate design in LangChain4j (a provider-
-neutral type), not a bug: it costs OpenRouter/Requesty-specific fields, on purpose, so that the same
`Usage` class works against a plain OpenAI endpoint that has none of them.

**What changing `AuditingChatModelListener` would take:** cast `responseContext.chatResponse()
.metadata()` to `OpenAiChatResponseMetadata`, call `.rawHttpResponse()?.body()`, and parse that
string with the listener's own `JsonMapper` (`json`, already a constructor dependency) as a loose
tree read (`json.readTree(body)`) rather than into a fixed data class, then pull `usage.cost`,
`usage.cost_details.upstream_inference_cost`, `usage.prompt_tokens_details.cached_tokens`, and
`usage.completion_tokens_details.reasoning_tokens` by path, defaulting to null when absent (Requesty
uses the same key names as OpenRouter for the base fields; `cost_details` is OpenRouter-specific,
`caching_price`-style provider fields are Requesty's own and would need their own lookup if Requesty
ever needs the same treatment as an inline cost rather than `usage.cost`, which it already returns
directly). This is a same-file change, guarded the same way the rest of the listener already is (the
`runCatching` in `audit()`), because a provider that returns no `cost` key (Ollama, LM Studio, or an
OpenAI-compatible endpoint with no such extension) must not break auditing - it should simply persist
a null cost, exactly like a call with no token usage today. No decorator, no HTTP interceptor, and no
change to `OpenAiCompatibleChatModelRegistry`'s model construction are required; `InspectingChatModel`
(`src/main/kotlin/com/jankowski/rafal/jobassistant/llm/internal/InspectingChatModel.kt`) is unrelated
to this - it inspects *outgoing* requests before the listener pipeline runs and has no visibility into
the response, so it is the wrong layer for this even though it is the repo's other example of a
`ChatModel`-wrapping seam.

One thing worth flagging for the guardrail specifically: `customParameters()` on
`OpenAiChatRequestParameters` (`dev.langchain4j.model.openai`, confirmed via `javap` and via its use
in `OpenAiUtils.toOpenAiChatRequest`, which does `.customParameters(parameters.customParameters())`)
is a `Map<String, Object>` merged into the outgoing JSON body via Jackson's `@JsonAnyGetter` on
`ChatCompletionRequest.customParameters` (confirmed in source). That is the mechanism available if a
future provider profile ever needs the deprecated-but-still-accepted `usage: {"include": true}` flag
explicitly, or any other provider-specific request extension - it requires no LangChain4j fork,
just setting `defaultRequestParameters(OpenAiChatRequestParameters.builder().customParameters(...)
.build())` on the model builder in `OpenAiCompatibleChatModelRegistry`. Not needed for OpenRouter or
Requesty today since both already include cost by default on non-streaming calls.

## Cached-prompt and reasoning tokens

Both providers expose the same OpenAI-shaped breakdown, and LangChain4j *does* partially model this
one - `dev.langchain4j.model.openai.OpenAiTokenUsage` (a `TokenUsage` subclass, also confirmed
present in 1.19.0 via `javap`) adds `inputTokensDetails().cachedTokens()` and
`outputTokensDetails().reasoningTokens()`, sourced from the same `Usage.promptTokensDetails
.cachedTokens` / `Usage.completionTokensDetails.reasoningTokens` fields the raw JSON carries. So
unlike cost, **cached and reasoning token counts already reach `AuditingChatModelListener` today**,
just not read: `responseContext.chatResponse().metadata().tokenUsage()` returns (at runtime) an
`OpenAiTokenUsage` instance even though the listener currently only calls the base `TokenUsage`
methods (`inputTokenCount()`/`outputTokenCount()`). Casting to `OpenAiTokenUsage` there is a smaller,
free addition alongside the cost change.

Why they matter for a cost sum: cached input tokens are typically billed at a steep discount (often
far below the plain input rate - OpenRouter's `pricing.input_cache_read` on the live `/models`
response was roughly 5x cheaper than `pricing.prompt` on the two entries inspected), so a naive
`inputTokens * pricing.prompt` overstates cost whenever a cached prefix was reused. Reasoning tokens
are the opposite risk: they are billed as *output* tokens (part of `completion_tokens`/
`outputTokenCount`) but are invisible in the visible completion text, so a naive `outputTokens ==
len(response text)` assumption undercounts them - `outputTokenCount()` already includes them,
`reasoningTokens()` is a breakdown, not an addition. Any cost estimate computed locally from the
`/models` pricing table (mechanism 3, either provider) needs to apply the cache-read rate to
`cachedTokens` and subtract that from the plain input rate applied to the remainder, rather than
pricing all input tokens uniformly - one more reason `usage.cost` from the response itself is the
right primary route: it already reflects this arithmetic server-side.

## Recommended column shape for `llm_call`

Following the house style in `V4__llm_call.sql` and `V11__llm_call_privacy.sql` - heavy explanatory
comments, nullable where a call can legitimately lack the value (an error response, a provider that
doesn't return it, or a pre-migration row):

```sql
-- Cost and token-breakdown columns, added once it became clear the provider's own completion
-- response already carries them - see docs/research/11-model-call-cost.md. LangChain4j's parsed
-- response types drop these fields entirely (they are OpenRouter/Requesty extensions on the OpenAI
-- schema, not modelled by a provider-neutral library), so AuditingChatModelListener now also reads
-- the raw HTTP response body it already receives and was previously discarding.

alter table llm_call
    -- What was actually charged for this call, in USD. Null when the provider doesn't report cost
    -- (a local model, a raw OpenAI endpoint) or when the call errored before a response arrived.
    -- numeric rather than a float: this value is summed for the spend guardrail, and float summation
    -- drift is not acceptable for something a budget check compares against.
    add column cost_usd numeric(12, 8),

    -- The subset of input_tokens that were served from a prompt cache, billed at a discount. Needed
    -- separately from input_tokens because a uniform per-token price applied to the total overstates
    -- cost whenever caching is in play.
    add column cached_input_tokens integer,

    -- The subset of output_tokens spent on reasoning rather than the visible response text. Already
    -- included in output_tokens (billed as ordinary output), broken out here because it is otherwise
    -- invisible against response_text and worth surfacing as its own signal in the UI.
    add column reasoning_output_tokens integer,

    -- What the upstream provider actually charged the router, when the router discloses it and it
    -- differs from cost_usd (a promotional or discounted rate the app itself did not receive). Purely
    -- informational; the guardrail and UI both use cost_usd.
    add column upstream_cost_usd numeric(12, 8);
```

No new table ownership question: these columns extend the existing `llm_call` row, which already
carries `profile_id` and its cascade-delete from `profile` (`V11__llm_call_privacy.sql`). None of the
new columns are prompt or response text, so none of them are subject to the same "delete on profile
erasure" reasoning beyond what already applies to the row as a whole - a cost figure with no
attached identifier is not personal data, but it lives in a row that already is one for other
reasons, so it is erased along with it for free.

No new outbound identifier risk: every value here is read from a response the app already receives,
not sent. The only outbound addition this research surfaces - `customParameters` carrying an explicit
`usage: {"include": true}` - is not needed for either configured provider today (see above), and if
it were ever added it carries no candidate data, only a boolean flag.

## Which feature each route unblocks

- **Cost display in the LLM-calls UI**: unblocked by `usage.cost` on the synchronous response alone.
  Read it in `AuditingChatModelListener.onResponse`, persist it with the call, show it in
  `JdbcLlmCallLog`/the UI exactly like `latencyMs` today. No follow-up lookup needed.
- **Spend guardrail (refuse a call once a budget is exceeded)**: also unblocked by the same
  synchronous route, and *only* by it. The guardrail needs a cost figure available at the moment a
  call is being considered or immediately after it completes, to decide whether the *next* call should
  be allowed - a `/generation` follow-up lookup (OpenRouter) that arrives with any unknown delay
  cannot serve this: by the time it resolves, an arbitrary number of further calls may already have
  been let through. Because `usage.cost` returns inline with the completion itself (both providers,
  confirmed above, no propagation delay to wait out), the guardrail is exactly as timely as the audit
  write it already piggybacks on - it can accumulate `cost_usd` per period from `llm_call` and check
  the running total before starting a new job, with no new latency in the analysis pipeline.

The models/pricing-table route (mechanism 3, either provider) would technically unblock the
guardrail too - it needs no per-call response field, just tokens times a cached rate - but only at
the cost of maintaining a second, provider-specific source of truth for pricing (subject to change
without notice, and blind to any discount `cost_details.upstream_inference_cost` would have revealed).
It is not recommended as the primary route while `usage.cost` is available for free on every call this
app already makes.

## What I could not verify

- Whether OpenRouter's `usage.cost` unit is explicitly documented as USD anywhere, versus the vaguer
  "credits" language used elsewhere on the same docs page. The live `/models` pricing values are
  unambiguously USD-per-token (cross-checked against a model's known list price), but the
  usage-accounting page itself never states a currency for `usage.cost`. Treat it as USD pending a
  direct confirmation against a real billed call (this app has no live OpenRouter key exercised during
  this research).
- Any documented propagation delay for OpenRouter's `GET /api/v1/generation?id=` lookup - searched
  OpenRouter's own docs and a linked support article, found none. Since the app does not need this
  lookup for either dependent feature, this gap does not block a recommendation, but it means "how
  stale is `provider_name`" is unanswered if that field is added later.
- Whether Requesty's `usage.cost` reflects the literal upstream provider invoice or Requesty's own
  price table applied after the fact - the cost-tracking page asserts consistency with its own
  dashboard but does not say which of those two it is sourced from.
- The exact header/mechanism to obtain `openrouter_metadata.endpoints` (which would carry the serving
  provider on the synchronous response, avoiding the `/generation` lookup for that one field) - the
  fetched docs mention the object exists but not what opts a request into it.
- Whether LM Studio or Ollama (both configured as possible profiles per `LlmProperties`/
  `job-assistant.llm.profiles`) return any `usage.cost`-shaped field at all. Neither was checked
  against a live instance; the reasonable assumption is that neither does, since both serve local
  models with no metered billing, which is exactly why `cost_usd` above is nullable.

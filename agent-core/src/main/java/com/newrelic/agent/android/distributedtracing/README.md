# Distributed tracing support in New Relic Android Agent

## Feature Flags
Distributed Tracing is enabled in the agent through the [DistributedTracing](https://source.datanerd.us/mobile/android_agent/blob/develop/agent-core/src/main/java/com/newrelic/agent/android/FeatureFlag.java#L18). 
In Agent version 6.0, it will be [enabled](https://source.datanerd.us/mobile/android_agent/blob/develop/agent-core/src/main/java/com/newrelic/agent/android/FeatureFlag.java#L28) by default.  

## Class Hierarchy

## TraceContext
The Android agent follows [W3C trace context](https://w3c.github.io/trace-context/) guidance.

Trace parent and state header values will be [W3C-compliant](https://w3c.github.io/trace-context/#trace-context-http-request-headers-format).

### TraceState
[Trace state format](https://source.datanerd.us/agents/agent-specs/blob/master/distributed_tracing/Trace-Context-Payload.md#tracestate)

### TraceParent
[Trace parent format](https://w3c.github.io/trace-context/#traceparent-header)

### TracePayload (legacy)
[New Relic Trace Payload](https://source.datanerd.us/agents/agent-specs/blob/master/distributed_tracing/New-Relic-Payload.md)

## Spans
[Span Data Model](https://source.datanerd.us/distributed-tracing/distributed_tracing_spec/blob/565f7a4c879ec0d600094989e850bcadc86f3435/span_data_model.md#restricted-fields)

## References
[Mobile data payload](https://source.datanerd.us/agents/agent-specs/blob/master/distributed_tracing/New-Relic-Payload.md#mobile-agent)
[Release notes](https://docs.google.com/document/d/1IXbIQ8wy3REyHilHPWUMNvOFkyhk9QDNgmfm51q0ZNc/edit#heading=h.vfcj0lld82a1)
[Engineering Plan](https://docs.google.com/document/d/18iDDvGB5kLESo-gv3qFOnbHyxvbDSZUsEk_aLN0Fgs8/edit#heading=h.bi286s9naipu)


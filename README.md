# rd-ldes-client

## Tests

Run the current regression suite with:

```sh
mvn test
```

This includes the LDES 1.0 RDF serialization, discovery, extraction, relation,
and HTTP retry regressions. The external black-box suite in
`../ldes-client-conformance-test-suite` remains the acceptance gate.

See [SPEC_COMPLIANCE_PLAN.md](SPEC_COMPLIANCE_PLAN.md) for the implementation
record, unsupported optional capabilities, and verification workflow.

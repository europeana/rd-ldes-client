# rd-ldes-client

## Tests

Run the current regression suite with:

```sh
mvn test
```

Known-red LDES 1.0 contract tests are tagged separately so individual
compliance milestones can be developed test-first:

```sh
mvn test -Pspec-compliance
```

The external black-box suite in `../ldes-client-conformance-test-suite` remains
the acceptance gate. See [SPEC_COMPLIANCE_PLAN.md](SPEC_COMPLIANCE_PLAN.md) for
the failure analysis, implementation order, and definition of done.

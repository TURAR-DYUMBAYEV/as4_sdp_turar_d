# Executed verification

Verified on 14 October 2026 using Java 17, Maven Wrapper 3.9.9 and JaCoCo 0.8.14.

JUnit: **25 tests, 0 failures, 0 errors, 0 skipped**.

| Package | Covered lines | Total lines | Line coverage |
| --- | ---: | ---: | ---: |
| ecommerce | 11 | 21 | 52.38% |
| ecommerce.cli | 54 | 54 | 100.00% |
| ecommerce.exception | 10 | 16 | 62.50% |
| ecommerce.facade | 75 | 75 | 100.00% |
| ecommerce.persistence | 114 | 114 | 100.00% |
| ecommerce.subsystema | 73 | 73 | 100.00% |
| ecommerce.subsystemb | 63 | 63 | 100.00% |
| ecommerce.subsystemc | 81 | 81 | 100.00% |

The required subsystem and facade packages each have 100% line coverage. The gate requires at least 80% separately in each of those four packages. No subsystem or facade classes are excluded. This is line coverage, not a claim of complete branch coverage or correctness for every possible input.

Commands executed from the project using a local Maven dependency cache:

```powershell
.\mvnw.cmd -B -ntp -Dmaven.repo.local=<local-cache> clean verify
java -jar target/Assignment4-SDP-Turar-1.0.0.jar <temporary-demo-database>
```

The full build passed: compilation with release 17, JUnit, JaCoCo report, executable JAR packaging, and coverage check. The local cache option only chooses where dependencies are stored; normal commands in README work without it.

A controlled staging build ran only SubsystemTest. JaCoCo correctly rejected ecommerce.facade with 0.00 line coverage against the 0.80 minimum. The final project build ran the entire suite and passed.

The packaged CLI was exercised in three separate JVM processes with real stdin, then checked directly through SQLite:

- Successful order persisted all three committed stages.
- B failure persisted a RESERVED/ABSENT failure snapshot and released A.
- C failure persisted A/B commits; B_CANCELLED preceded A_RELEASED.
- Carrier failure recorded a refund.
- Restart after A and after B resumed successfully.
- Repeated manual compensation restored inventory exactly once.
- Final BOOK stock was 95: only the successful 3-unit and resumed 2-unit orders remained fulfilled.

See [the actual console transcript](demo-transcript.txt). The demo used a separate temporary database, leaving the normal fulfillment.db unseeded until its first application run.

All 12 subsystem classes passed an import check: no subsystem imported another subsystem. No TODO placeholders remain. PlantUML rendered the matching SVG and PNG successfully, and the rendered PNG was visually inspected.

Limits: no real carrier/payment API, no concurrent workflow writers, no power-loss/disk-corruption test, and no manual IntelliJ GUI run. The Maven project and CLI were executed directly. Git commits are local; nothing was pushed or submitted.

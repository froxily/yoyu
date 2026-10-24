# Third-party notices

Yoyū is released under the [MIT License](LICENSE). It builds on the open-source components and data below. Each remains under its own license; the licenses named here are those the projects publish, and each project's own license text is the authoritative statement. To list the exact versions you build with, run `cd backend && ./mvnw dependency:list` and `cd frontend && npm ls --all`.

## Shipped in the backend jar

About 100 libraries are bundled, resolved through Spring Boot 4.1.1's dependency management. By family:

| Component | License |
| --- | --- |
| Spring Framework, Spring Boot, Spring Security, Spring Data, Spring Session | Apache-2.0 |
| Apache Tomcat (embedded), Jackson, HikariCP, Flyway Community Edition, Micrometer, jboss-logging, ClassMate, ByteBuddy, SnakeYAML, Hibernate ORM and Hibernate Validator, Log4j API (SLF4J bridge), Nimbus JOSE+JWT and OAuth 2.0 / OIDC SDK, json-smart and accessors-smart | Apache-2.0 |
| PostgreSQL JDBC driver | BSD-2-Clause |
| ASM, ANTLR 4 runtime, JAXB runtime and core, istack-commons | BSD-3-Clause / Eclipse Distribution License 1.0 |
| SLF4J, Checker Framework annotations | MIT |
| Jakarta EE API jars, Angus Mail and Angus Activation | EPL-2.0, Apache-2.0 or GPL-2.0 with Classpath Exception, depending on the artifact |
| Logback | EPL-1.0 or LGPL-2.1 (dual) |
| AspectJ weaver | EPL-2.0 |

## Shipped in the frontend bundle

| Component | Version | License |
| --- | --- | --- |
| React, React DOM | 19.3.1 | MIT |

The Google "G" and LINE marks drawn on the sign-in buttons follow those providers' published sign-in branding guidelines.

## Build and test tooling (not distributed with the application)

| Component | License |
| --- | --- |
| Vite, Vitest, vite-plugin-pwa, `@vitejs/plugin-react`, oxlint, `@vite-pwa/assets-generator`, `@types/*` | MIT |
| TypeScript | Apache-2.0 |
| `sharp` and its prebuilt libvips binaries (used only by the icon generator) | Apache-2.0, with LGPL-3.0-or-later for libvips |
| lightningcss, ico-endec | MPL-2.0 |
| caniuse-lite (browser-support data) | CC-BY-4.0 (data from caniuse.com) |
| JUnit 5 | EPL-2.0 |
| AssertJ | Apache-2.0 |
| Testcontainers | MIT |
| Maven Wrapper | Apache-2.0 |

None of these are included in the shipped jar, container image or frontend bundle.

## Container images used by the Compose setup and Dockerfile

Pulled at build or run time, not redistributed by this repository: `postgres` (PostgreSQL License), `axllent/mailpit` (MIT), `eclipse-temurin` (GPL-2.0 with Classpath Exception), `node` (MIT).

## Bundled data

**`backend/src/main/resources/common-passwords.txt`**: a list of about 9,100 commonly used passwords of 10 or more characters, used to reject weak passwords at sign-up. It is derived from the UK National Cyber Security Centre's "top 100,000 most common passwords" list as distributed in [SecLists](https://github.com/danielmiessler/SecLists), which is released under the MIT License, Copyright (c) 2018 Daniel Miessler. The list consists of passwords that people have published or that appear in public breach corpora; some entries are therefore strings people chose as passwords, including text that resembles other words, names or addresses. It does not identify any user of Yoyū.

## Brand names

Google, LINE, Microsoft Azure, Neon, GitHub, Docker, PostgreSQL and other product names are trademarks of their respective owners. Yoyū is an independent project and is not affiliated with or endorsed by any of them, by the Immigration Services Agency of Japan, or by any school or employer. Sources for the work-hour rules are linked in the README and in `docs/SPEC.md`.

## Project assets

The app icon, favicon and splash artwork were created for this project and are covered by the project license. The screenshots in `docs/screenshots/` show the app with made-up demo data.

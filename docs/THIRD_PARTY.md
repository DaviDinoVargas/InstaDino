# Componentes de terceiros

Inventário das dependências **de execução** (escopo `runtime`) do aplicativo desktop e do backend, gerado em 2026-10-26 a partir dos POMs das próprias dependências (campo `licenses`, herdado do POM pai quando necessário). Dependências de teste e de build não são distribuídas e não constam aqui.

> Este inventário é **informativo**. A revisão jurídica das licenças (especialmente MPL/EPL/LGPL e a redistribuição junto ao instalador) é responsabilidade do responsável pelo produto antes do primeiro release. Regenere a lista a cada mudança de dependência.

O JDK/JRE embutido no instalador (OpenJDK, GPLv2 com *Classpath Exception*) e o OpenJFX (GPLv2 com *Classpath Exception*) também são redistribuídos pelo pacote do desktop.

| Componente | Versão | Licença declarada | Usado em |
|---|---|---|---|
| `ch.qos.logback:logback-classic` | 1.5.34 | EPL-2.0; LGPL-2.1-only | backend |
| `ch.qos.logback:logback-core` | 1.5.34 | EPL-2.0; LGPL-2.1-only | backend |
| `com.fasterxml:classmate` | 1.7.3 | Apache License, Version 2.0 | backend |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.21 | The Apache Software License, Version 2.0 | backend |
| `com.fasterxml.jackson.core:jackson-annotations` | 2.22 | The Apache Software License, Version 2.0 | desktop |
| `com.fasterxml.jackson.core:jackson-core` | 2.21.4 | The Apache Software License, Version 2.0 | backend |
| `com.fasterxml.jackson.core:jackson-core` | 2.22.4 | The Apache Software License, Version 2.0 | desktop |
| `com.fasterxml.jackson.core:jackson-databind` | 2.21.4 | The Apache Software License, Version 2.0 | backend |
| `com.fasterxml.jackson.core:jackson-databind` | 2.22.4 | The Apache Software License, Version 2.0 | desktop |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-toml` | 2.21.4 | The Apache Software License, Version 2.0 | backend |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | 2.21.4 | The Apache Software License, Version 2.0 | backend |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8` | 2.21.4 | The Apache Software License, Version 2.0 | backend |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.21.4 | The Apache Software License, Version 2.0 | backend |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | 2.22.4 | The Apache Software License, Version 2.0 | desktop |
| `com.fasterxml.jackson.module:jackson-module-parameter-names` | 2.21.4 | The Apache Software License, Version 2.0 | backend |
| `com.h2database:h2` | 2.3.232 | MPL 2.0; EPL 1.0 | backend |
| `com.sun.istack:istack-commons-runtime` | 4.1.2 | Eclipse Distribution License - v 1.0 | backend |
| `com.zaxxer:HikariCP` | 6.3.3 | The Apache Software License, Version 2.0 | backend |
| `io.github.willena:sqlite-jdbc` | 3.53.4.0 | The Apache Software License, Version 2.0 | desktop |
| `io.micrometer:micrometer-commons` | 1.15.12 | The Apache Software License, Version 2.0 | backend |
| `io.micrometer:micrometer-observation` | 1.15.12 | The Apache Software License, Version 2.0 | backend |
| `io.smallrye:jandex` | 3.2.0 | Apache License, Version 2.0 | backend |
| `io.swagger.core.v3:swagger-annotations-jakarta` | 2.2.47 | Apache License 2.0 | backend |
| `io.swagger.core.v3:swagger-core-jakarta` | 2.2.47 | Apache License 2.0 | backend |
| `io.swagger.core.v3:swagger-models-jakarta` | 2.2.47 | Apache License 2.0 | backend |
| `jakarta.activation:jakarta.activation-api` | 2.1.4 | EDL 1.0 | backend |
| `jakarta.annotation:jakarta.annotation-api` | 2.1.1 | EPL 2.0; GPL2 w/ CPE | backend |
| `jakarta.inject:jakarta.inject-api` | 2.0.1 | The Apache Software License, Version 2.0 | backend |
| `jakarta.persistence:jakarta.persistence-api` | 3.1.0 | Eclipse Public License v. 2.0; Eclipse Distribution License v. 1.0 | backend |
| `jakarta.transaction:jakarta.transaction-api` | 2.0.1 | EPL 2.0; GPL2 w/ CPE | backend |
| `jakarta.validation:jakarta.validation-api` | 3.0.2 | Apache License 2.0 | backend |
| `jakarta.xml.bind:jakarta.xml.bind-api` | 4.0.5 | Eclipse Distribution License - v 1.0 | backend |
| `net.bytebuddy:byte-buddy` | 1.17.8 | Apache License, Version 2.0 | backend |
| `net.java.dev.jna:jna` | 5.19.1 | LGPL-2.1-or-later; Apache-2.0 | desktop |
| `net.java.dev.jna:jna-platform` | 5.19.1 | LGPL-2.1-or-later; Apache-2.0 | desktop |
| `org.antlr:antlr4-runtime` | 4.13.2 | BSD-3-Clause | backend |
| `org.apache.commons:commons-lang3` | 3.17.0 | Apache-2.0 | backend |
| `org.apache.logging.log4j:log4j-api` | 2.24.3 | Apache-2.0 | backend |
| `org.apache.logging.log4j:log4j-to-slf4j` | 2.24.3 | Apache-2.0 | backend |
| `org.apache.tomcat.embed:tomcat-embed-core` | 10.1.55 | Apache License, Version 2.0 | backend |
| `org.apache.tomcat.embed:tomcat-embed-el` | 10.1.55 | Apache License, Version 2.0 | backend |
| `org.apache.tomcat.embed:tomcat-embed-websocket` | 10.1.55 | Apache License, Version 2.0 | backend |
| `org.aspectj:aspectjweaver` | 1.9.25.1 | Eclipse Public License - v 2.0 | backend |
| `org.bouncycastle:bcprov-jdk18on` | 1.86 | Bouncy Castle Licence | backend |
| `org.eclipse.angus:angus-activation` | 2.0.3 | EDL 1.0 | backend |
| `org.flywaydb:flyway-core` | 11.7.2 | Apache License, Version 2.0 | backend |
| `org.flywaydb:flyway-database-postgresql` | 11.7.2 | Apache License, Version 2.0 | backend |
| `org.glassfish.jaxb:jaxb-core` | 4.0.9 | Eclipse Distribution License - v 1.0 | backend |
| `org.glassfish.jaxb:jaxb-runtime` | 4.0.9 | Eclipse Distribution License - v 1.0 | backend |
| `org.glassfish.jaxb:txw2` | 4.0.9 | Eclipse Distribution License - v 1.0 | backend |
| `org.hibernate.common:hibernate-commons-annotations` | 7.0.3.Final | Apache License Version 2.0 | backend |
| `org.hibernate.orm:hibernate-core` | 6.6.53.Final | GNU Library General Public License v2.1 or later | backend |
| `org.hibernate.validator:hibernate-validator` | 8.0.3.Final | Apache License 2.0 | backend |
| `org.jboss.logging:jboss-logging` | 3.6.3.Final | Apache License 2.0 | backend |
| `org.jsoup:jsoup` | 1.23.2 | The MIT License | desktop |
| `org.jspecify:jspecify` | 1.0.0 | The Apache License, Version 2.0 | backend |
| `org.openjfx:javafx-base` | 21.0.13 | GPLv2+CE | desktop |
| `org.openjfx:javafx-controls` | 21.0.13 | GPLv2+CE | desktop |
| `org.openjfx:javafx-graphics` | 21.0.13 | GPLv2+CE | desktop |
| `org.postgresql:postgresql` | 42.7.11 | BSD-2-Clause | backend |
| `org.slf4j:jul-to-slf4j` | 2.0.18 | MIT | backend |
| `org.slf4j:slf4j-api` | 2.0.18 | MIT | backend |
| `org.springdoc:springdoc-openapi-starter-common` | 2.8.17 | The Apache License, Version 2.0 | backend |
| `org.springdoc:springdoc-openapi-starter-webmvc-api` | 2.8.17 | The Apache License, Version 2.0 | backend |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.8.17 | The Apache License, Version 2.0 | backend |
| `org.springframework:spring-aop` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-aspects` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-beans` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-context` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-core` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-expression` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-jcl` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-jdbc` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-orm` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-tx` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-web` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework:spring-webmvc` | 6.2.19 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-autoconfigure` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-starter` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-starter-data-jpa` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-starter-jdbc` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-starter-json` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-starter-logging` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-starter-security` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-starter-tomcat` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-starter-validation` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.boot:spring-boot-starter-web` | 3.5.16 | Apache License, Version 2.0 | backend |
| `org.springframework.data:spring-data-commons` | 3.5.13 | Apache License, Version 2.0 | backend |
| `org.springframework.data:spring-data-jpa` | 3.5.13 | Apache License, Version 2.0 | backend |
| `org.springframework.security:spring-security-config` | 6.5.11 | Apache License, Version 2.0 | backend |
| `org.springframework.security:spring-security-core` | 6.5.11 | Apache License, Version 2.0 | backend |
| `org.springframework.security:spring-security-crypto` | 6.5.11 | Apache License, Version 2.0 | backend |
| `org.springframework.security:spring-security-web` | 6.5.11 | Apache License, Version 2.0 | backend |
| `org.webjars:swagger-ui` | 5.32.2 | Apache-2.0 | backend |
| `org.webjars:webjars-locator-lite` | 1.1.3 | MIT | backend |
| `org.yaml:snakeyaml` | 2.4 | Apache License, Version 2.0 | backend |

`desktop` = aplicativo Windows; `backend` = servidor comercial (não é distribuído aos usuários).

## Pontos de atenção para a revisão jurídica

- **JNA** (`net.java.dev.jna`) é licenciada em dupla (LGPL-2.1-or-later ou Apache-2.0); a escolha da licença aplicável (Apache-2.0 é a opção mais simples) deve ser confirmada na revisão jurídica.
- **Logback** (backend) é dupla EPL-2.0 / LGPL-2.1; o backend não é distribuído aos usuários.
- **H2** (somente desenvolvimento e testes do backend) é MPL-2.0 / EPL-1.0; produção usa PostgreSQL.
- **OpenJFX e OpenJDK** são GPLv2 com *Classpath Exception*; o instalador precisa incluir os avisos de licença correspondentes.
- O driver SQLite (`io.github.willena:sqlite-jdbc`) embute o SQLite3 Multiple Ciphers; confirmar os avisos de licença do componente nativo antes do release.

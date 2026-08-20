package com.elicatari.dteissuer;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.Repository;

/**
 * Frontera hexagonal. No prueba aislamiento de tenant: eso es {@code TenantIsolationIT}.
 */
@AnalyzeClasses(packages = "com.elicatari.dteissuer", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchTest {

    @ArchTest
    static final ArchRule domainDoesNotDependOnFrameworksOrAdapters = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "org.springframework.amqp..",
                    "com.rabbitmq..",
                    "org.keycloak..",
                    "lombok..",
                    "org.springdoc..",
                    "io.swagger..",
                    "io.micrometer..",
                    "io.prometheus..",
                    "..adapter..",
                    "..application..",
                    "..shared..")
            .because("el dominio es Java puro: sin Spring, JPA, Rabbit, Keycloak, OpenAPI ni adapters");

    @ArchTest
    static final ArchRule applicationDoesNotDependOnAdaptersOrPersistence = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..adapter..",
                    "org.springframework.data..",
                    "jakarta.persistence..",
                    "org.hibernate..",
                    "org.springframework.amqp..",
                    "com.rabbitmq..",
                    "org.keycloak..")
            .because("la aplicación define puertos; no conoce JPA, Spring Data ni Rabbit");

    @ArchTest
    static final ArchRule outboundPortsAreInterfacesNotSpringDataRepositories = classes()
            .that()
            .resideInAPackage("..application.port.out..")
            .and()
            .areInterfaces()
            .should()
            .notBeAssignableTo(JpaRepository.class)
            .andShould()
            .notBeAssignableTo(Repository.class)
            .because("el puerto de salida es una interfaz propia, no un JpaRepository expuesto");

    @ArchTest
    static final ArchRule domainAndApplicationDoNotExtendJpaRepository = noClasses()
            .that()
            .resideInAnyPackage("..domain..", "..application..")
            .should()
            .beAssignableTo(JpaRepository.class)
            .orShould()
            .beAssignableTo(Repository.class)
            .because("Spring Data no entra al hexágono interno");
}
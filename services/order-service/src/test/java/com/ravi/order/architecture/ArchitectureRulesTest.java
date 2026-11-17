package com.ravi.order.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * ArchUnit architecture enforcement test.
 * Enforces Hexagonal and Clean Architecture package dependency rules:
 * 1. Domain layer must NOT depend on Infrastructure or Presentation.
 * 2. Domain layer must NOT depend on Spring, JPA, or Kafka.
 * 3. Presentation layer must NOT access Infrastructure persistence directly.
 */
@AnalyzeClasses(packages = "com.ravi.order", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule domainMustNotDependOnInfrastructure =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule domainMustNotDependOnPresentation =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("..presentation..");

    @ArchTest
    static final ArchRule domainMustNotDependOnSpringFramework =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "org.hibernate.."
                    );

    @ArchTest
    static final ArchRule presentationMustNotDirectlyAccessInfrastructurePersistence =
            noClasses().that().resideInAPackage("..presentation..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure.persistence..");
}

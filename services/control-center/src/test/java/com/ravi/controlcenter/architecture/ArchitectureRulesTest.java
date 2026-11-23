package com.ravi.controlcenter.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

/**
 * ArchUnit architecture enforcement test for Control Center.
 */
@AnalyzeClasses(packages = "com.ravi.controlcenter", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule presentation_controllers_should_only_reside_in_web =
            classes()
                    .that().haveSimpleNameEndingWith("Controller")
                    .should().resideInAnyPackage("..web..", "..controller..")
                    .as("REST and Admin Controllers in Control Center must reside in the web or controller package");

    @ArchTest
    static final ArchRule services_should_reside_in_service_package =
            classes()
                    .that().haveSimpleNameEndingWith("Service")
                    .should().resideInAPackage("..service..")
                    .as("Application and operational services must reside in the service package");
}

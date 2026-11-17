package com.ravi.gateway.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.ravi.gateway", importOptions = ImportOption.DoNotIncludeTests.class)
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule presentation_controllers_should_only_reside_in_presentation =
            classes()
                    .that().haveSimpleNameEndingWith("Controller")
                    .should().resideInAPackage("..presentation..")
                    .as("REST Controllers in API Gateway must reside in the presentation layer");

    @ArchTest
    static final ArchRule security_and_filters_should_reside_in_infrastructure =
            classes()
                    .that().haveSimpleNameEndingWith("Filter").or().haveSimpleNameEndingWith("Config")
                    .should().resideInAPackage("..infrastructure..")
                    .as("Filters and Security Config must reside in the infrastructure layer");
}

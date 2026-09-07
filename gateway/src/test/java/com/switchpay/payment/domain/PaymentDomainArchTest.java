package com.switchpay.payment.domain;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.switchpay.payment.domain")
public class PaymentDomainArchTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_spring_or_jpa =
            noClasses()
                    .that().resideInAPackage("com.switchpay.payment.domain..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "jakarta.persistence..",
                            "javax.persistence..",
                            "jakarta.servlet..",
                            "javax.servlet.."
                    );
}

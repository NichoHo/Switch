package com.switchpay.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

class ArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.switchpay");

    @Test
    void ledgerRepositoriesShouldBeAppendOnly() {
        noMethods().that().areDeclaredInClassesThat().resideInAPackage("..ledger.store..")
            .should().beAnnotatedWith(Modifying.class)
            .orShould().haveNameStartingWith("delete")
            .orShould().haveNameStartingWith("update")
            .check(classes);
    }

    /**
     * NR-14: a controller assembling its own response by reaching into a repository is a
     * decision about how two tables join, made in the layer that's supposed to only know about
     * HTTP shape. Written after the fact, once {@code PaymentController}, {@code SettlementController}
     * and {@code ThreedsCallbackController} already had the offending dependencies removed:
     * this is what stops a fourth controller from reintroducing the pattern during Phase 9.
     */
    @Test
    void controllersShouldNotDependOnRepositoriesDirectly() {
        noClasses().that().areAnnotatedWith(RestController.class)
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .check(classes);
    }
}

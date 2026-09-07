package com.switchpay.vault;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class VaultArchitectureTest {

    @Test
    void panNeverLeavesTheVault() {
        JavaClasses importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.switchpay");

        ArchRule pan_never_leaves_the_vault =
                noClasses().that().resideOutsideOfPackage("com.switchpay.vault..")
                        .should().dependOnClassesThat().haveSimpleNameEndingWith("PanCipher")
                        .orShould().dependOnClassesThat().haveSimpleName("Pan");

        pan_never_leaves_the_vault.check(importedClasses);
    }
}

package com.kiri.server;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.kiri.server.global.entity.BaseEntity;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaEnumConstant;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ArchitectureTest {

    // 테스트 코드를 제외한 main 클래스만 한 번 읽어서 모든 규칙에 재사용
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.kiri.server");

    @Test
    @DisplayName("Controller는 Service만 호출하고, Repository는 Service에서만 접근한다.")
    void layerDependencies() {
        // global 패키지 등 레이어 밖 의존은 검사하지 않음
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Controller")
                .definedBy("..domain..controller..")
                .layer("Service")
                .definedBy("..domain..service..")
                .layer("Repository")
                .definedBy("..domain..repository..")
                .whereLayer("Controller")
                .mayNotBeAccessedByAnyLayer()
                .whereLayer("Service")
                .mayOnlyBeAccessedByLayers("Controller", "Service")
                .whereLayer("Repository")
                .mayOnlyBeAccessedByLayers("Service")
                .check(CLASSES);
    }

    @Test
    @DisplayName("엔티티는 BaseEntity를 상속한다.")
    void entitiesExtendBaseEntity() {
        classes()
                .that()
                .areAnnotatedWith(Entity.class)
                .should()
                .beAssignableTo(BaseEntity.class)
                .check(CLASSES);
    }

    @Test
    @DisplayName("엔티티에 public setter 메서드가 없다.")
    void entitiesHaveNoSetters() {
        // Lombok @Setter는 바이트코드에 어노테이션이 남지 않으므로, 생성된 setXxx 메서드를 검사
        noMethods()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(Entity.class)
                .and()
                .arePublic()
                .should()
                .haveNameMatching("set[A-Z].*")
                .check(CLASSES);
    }

    @Test
    @DisplayName("@ManyToOne, @OneToOne 연관관계는 fetch = LAZY를 명시한다.")
    void toOneRelationsAreLazy() {
        fields().that()
                .areAnnotatedWith(ManyToOne.class)
                .or()
                .areAnnotatedWith(OneToOne.class)
                .should()
                .beAnnotatedWith(lazyToOneRelation())
                .check(CLASSES);
    }

    @Test
    @DisplayName("엔티티의 enum 필드는 @Enumerated(EnumType.STRING)으로 저장한다.")
    void enumFieldsAreStoredAsString() {
        fields().that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(Entity.class)
                .and()
                .haveRawType(describe("enum", JavaClass::isEnum))
                .should()
                .beAnnotatedWith(enumeratedAsString())
                .check(CLASSES);
    }

    @Test
    @DisplayName("Controller와 Service에서 DTO, 엔티티의 builder()를 직접 호출하지 않고 Converter를 사용한다.")
    void builderIsCalledOnlyInConverter() {
        noClasses()
                .that()
                .resideInAnyPackage("..controller..", "..service..")
                .should()
                .callMethodWhere(
                        target(name("builder")).and(target(owner(resideInAnyPackage("..dto..", "..entity..")))))
                .check(CLASSES);
    }

    private static DescribedPredicate<JavaAnnotation<?>> lazyToOneRelation() {
        return describe(
                "@ManyToOne/@OneToOne(fetch = FetchType.LAZY)",
                annotation -> (annotation.getRawType().isEquivalentTo(ManyToOne.class)
                                || annotation.getRawType().isEquivalentTo(OneToOne.class))
                        && hasEnumValue(annotation, "fetch", "LAZY"));
    }

    private static DescribedPredicate<JavaAnnotation<?>> enumeratedAsString() {
        return describe(
                "@Enumerated(EnumType.STRING)",
                annotation -> annotation.getRawType().isEquivalentTo(Enumerated.class)
                        && hasEnumValue(annotation, "value", EnumType.STRING.name()));
    }

    // 속성값을 명시하지 않았으면 기본값(EAGER, ORDINAL)이 쓰이므로 false
    private static boolean hasEnumValue(JavaAnnotation<?> annotation, String property, String constantName) {
        return annotation
                .get(property)
                .map(value -> value instanceof JavaEnumConstant constant
                        && constant.name().equals(constantName))
                .orElse(false);
    }
}

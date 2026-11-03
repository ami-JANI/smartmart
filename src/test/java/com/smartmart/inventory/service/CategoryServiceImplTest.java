package com.smartmart.inventory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.smartmart.inventory.domain.Category;
import com.smartmart.inventory.repository.CategoryRepository;
import com.smartmart.inventory.repository.ProductRepository;

class CategoryServiceImplTest {

    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryServiceImpl service = new CategoryServiceImpl(repository, products);

    private Category category(long id, String name, Category parent) {
        Category category = new Category(name, parent);
        ReflectionTestUtils.setField(category, "id", id);
        when(repository.findById(id)).thenReturn(Optional.of(category));
        return category;
    }

    @Test
    void createStripsTheNameAndSaves() {
        Category saved = category(7L, "Rice", null);
        when(repository.saveAndFlush(any())).thenReturn(saved);

        assertThat(service.create("  Rice  ", null)).isEqualTo(7L);

        verify(repository).existsByParent_IdAndName(null, "Rice");
    }

    @Test
    void createRefusesBlankName() {
        assertThatThrownBy(() -> service.create("   ", null))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessage("Category name is required");
    }

    @Test
    void createAcceptsNameAtTheLimitAndRefusesOneOver() {
        Category saved = category(1L, "x", null);
        when(repository.saveAndFlush(any())).thenReturn(saved);

        service.create("a".repeat(100), null);

        assertThatThrownBy(() -> service.create("a".repeat(101), null))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessageContaining("at most 100");
    }

    @Test
    void createRefusesDuplicateAmongSiblings() {
        Category grocery = category(1L, "Grocery", null);
        when(repository.existsByParent_IdAndName(grocery.getId(), "Rice")).thenReturn(true);

        assertThatThrownBy(() -> service.create("Rice", 1L))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessageContaining("already exists");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void createRefusesMissingParent() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("Rice", 99L))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessage("Category not found");
    }

    @Test
    void duplicateCaughtByTheDatabaseIsRefusedToo() {
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique"));

        assertThatThrownBy(() -> service.create("Rice", null))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void renameToTheSameNameChangesNothing() {
        category(1L, "Rice", null);

        service.rename(1L, " Rice ");

        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void renameChecksSiblingsOfTheSameParent() {
        Category grocery = category(1L, "Grocery", null);
        category(2L, "Rice", grocery);
        when(repository.existsByParent_IdAndName(1L, "Dal")).thenReturn(true);

        assertThatThrownBy(() -> service.rename(2L, "Dal"))
                .isInstanceOf(CategoryRuleException.class);
    }

    @Test
    void moveUnderItselfIsRefused() {
        category(1L, "Grocery", null);

        assertThatThrownBy(() -> service.move(1L, 1L))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessageContaining("cannot be moved");
    }

    @Test
    void moveUnderItsOwnGrandchildIsRefused() {
        Category grocery = category(1L, "Grocery", null);
        Category rice = category(2L, "Rice", grocery);
        category(3L, "Miniket", rice);

        assertThatThrownBy(() -> service.move(1L, 3L))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessageContaining("cannot be moved");
    }

    @Test
    void moveUnderAnotherBranchWorks() {
        Category grocery = category(1L, "Grocery", null);
        Category rice = category(2L, "Rice", grocery);
        Category food = category(3L, "Food", null);

        service.move(2L, 3L);

        assertThat(rice.getParent()).isSameAs(food);
        verify(repository).saveAndFlush(rice);
    }

    @Test
    void moveToTopLevelWorks() {
        Category grocery = category(1L, "Grocery", null);
        Category rice = category(2L, "Rice", grocery);

        service.move(2L, null);

        assertThat(rice.getParent()).isNull();
    }

    @Test
    void moveRefusesWhenTheNewParentHasThatName() {
        Category grocery = category(1L, "Grocery", null);
        category(2L, "Rice", grocery);
        category(3L, "Food", null);
        when(repository.existsByParent_IdAndName(3L, "Rice")).thenReturn(true);

        assertThatThrownBy(() -> service.move(2L, 3L))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void deleteWithChildrenIsRefused() {
        Category grocery = category(1L, "Grocery", null);
        when(repository.existsByParent_Id(1L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessageContaining("Cannot delete");
        verify(repository, never()).delete(grocery);
    }

    @Test
    void deleteWithProductsIsRefused() {
        Category rice = category(2L, "Rice", null);
        when(products.existsByCategory_Id(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(CategoryRuleException.class)
                .hasMessageContaining("Cannot delete");
        verify(repository, never()).delete(rice);
    }

    @Test
    void deleteWithoutChildrenWorks() {
        Category rice = category(2L, "Rice", null);

        service.delete(2L);

        verify(repository).delete(rice);
    }

    @Test
    void treeNestsChildrenAndKeepsTheOrder() {
        Category food = category(1L, "Food", null);
        Category dal = category(2L, "Dal", food);
        Category rice = category(3L, "Rice", food);
        Category toys = category(4L, "Toys", null);
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(dal, food, rice, toys));

        List<CategoryNode> tree = service.tree();

        assertThat(tree).extracting(CategoryNode::name).containsExactly("Food", "Toys");
        assertThat(tree.get(0).children()).extracting(CategoryNode::name).containsExactly("Dal", "Rice");
        assertThat(tree.get(0).children().get(0).parentId()).isEqualTo(1L);
        assertThat(tree.get(1).children()).isEmpty();
    }

    @Test
    void optionsShowThePathInTreeOrder() {
        Category food = category(1L, "Food", null);
        Category rice = category(2L, "Rice", food);
        Category miniket = category(3L, "Miniket", rice);
        when(repository.findAllByOrderByNameAsc()).thenReturn(List.of(food, miniket, rice));

        assertThat(service.options()).containsExactly(
                new CategoryOption(1L, "Food"),
                new CategoryOption(2L, "Food > Rice"),
                new CategoryOption(3L, "Food > Rice > Miniket"));
    }
}

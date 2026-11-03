package com.smartmart.inventory.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.smartmart.inventory.service.CategoryService;

/**
 * Runs the real page, service and tables against the two databases from
 * compose.yaml. The page reads from the replica, so checks of what it shows
 * wait for replication, polling on the test thread to keep its mock user.
 * Each test removes the categories it created.
 */
@SpringBootTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureMockMvc
@WithMockUser
class CategoryPageTests {

    private final MockMvc mvc;
    private final CategoryService service;
    private final List<Long> created = new ArrayList<>();

    @Autowired
    CategoryPageTests(MockMvc mvc, CategoryService service) {
        this.mvc = mvc;
        this.service = service;
    }

    @AfterEach
    void removeCreatedCategories() {
        // Children were created after their parents, so delete newest first.
        for (int i = created.size() - 1; i >= 0; i--) {
            service.delete(created.get(i));
        }
    }

    private String unique(String name) {
        return name + "-" + UUID.randomUUID();
    }

    private Long create(String name, Long parentId) {
        Long id = service.create(name, parentId);
        created.add(id);
        return id;
    }

    private void awaitPageShows(String text) {
        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                mvc.perform(get("/categories"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString(text))));
    }

    @Test
    void pageRendersTheTreeWithPathsAndAddForm() throws Exception {
        String parent = unique("Grocery");
        String child = unique("Rice");
        create(child, create(parent, null));

        awaitPageShows(parent + " &gt; " + child);

        mvc.perform(get("/categories"))
                .andExpect(content().string(containsString("Add a category")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
    }

    @Test
    void addingACategoryRedirectsWithAMessage() throws Exception {
        String name = unique("Dal");

        mvc.perform(post("/categories").with(csrf()).param("name", name).param("parentId", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("message", "Category added"));

        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(service.options()).anySatisfy(option -> {
                    assertThat(option.path()).isEqualTo(name);
                    if (!created.contains(option.id())) {
                        created.add(option.id());
                    }
                }));
        awaitPageShows(name);
    }

    @Test
    void blankNameShowsTheFieldError() throws Exception {
        mvc.perform(post("/categories").with(csrf()).param("name", "  ").param("parentId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Category name is required")));
    }

    @Test
    void duplicateAmongSiblingsIsRefusedWithAMessage() throws Exception {
        String name = unique("Oil");
        create(name, null);

        mvc.perform(post("/categories").with(csrf()).param("name", name).param("parentId", ""))
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("error", "A category named \"" + name + "\" already exists here"));
    }

    @Test
    void sameNameUnderDifferentParentsIsAllowed() {
        String name = unique("Other");
        create(name, create(unique("Food"), null));
        create(name, create(unique("Toys"), null));
    }

    @Test
    void movingUnderOwnChildIsRefused() throws Exception {
        Long parent = create(unique("Grocery"), null);
        Long child = create(unique("Rice"), parent);

        mvc.perform(post("/categories/" + parent + "/move").with(csrf()).param("parentId", child.toString()))
                .andExpect(redirectedUrl("/categories"))
                .andExpect(flash().attribute("error",
                        "A category cannot be moved under itself or one of its own child categories"));
    }

    @Test
    void renameAndMoveToTopLevelWork() throws Exception {
        Long parent = create(unique("Grocery"), null);
        Long child = create(unique("Rice"), parent);
        String newName = unique("Miniket");

        mvc.perform(post("/categories/" + child + "/rename").with(csrf()).param("name", newName))
                .andExpect(flash().attribute("message", "Category renamed"));
        mvc.perform(post("/categories/" + child + "/move").with(csrf()).param("parentId", ""))
                .andExpect(flash().attribute("message", "Category moved"));

        await().pollInSameThread().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(service.tree()).anySatisfy(node -> {
                    assertThat(node.id()).isEqualTo(child);
                    assertThat(node.name()).isEqualTo(newName);
                    assertThat(node.parentId()).isNull();
                }));
    }

    @Test
    void deleteIsRefusedWithChildrenAndWorksWithout() throws Exception {
        Long parent = create(unique("Grocery"), null);
        Long child = create(unique("Rice"), parent);

        mvc.perform(post("/categories/" + parent + "/delete").with(csrf()))
                .andExpect(flash().attribute("error", "Cannot delete: this category has products or child categories"));

        mvc.perform(post("/categories/" + child + "/delete").with(csrf()))
                .andExpect(flash().attribute("message", "Category deleted"));
        created.remove(child);
    }
}
